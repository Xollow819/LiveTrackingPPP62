package com.ppp62.livetracking.ui

import com.ppp62.livetracking.util.UserFacingErrors

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ppp62.livetracking.PPP62Application
import com.ppp62.livetracking.data.*
import com.ppp62.livetracking.data.remote.*
import com.ppp62.livetracking.service.LocationTrackingService
import com.ppp62.livetracking.service.SyncWorker
import com.ppp62.livetracking.util.LocationUtils
import com.ppp62.livetracking.util.Notifier
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@kotlinx.serialization.Serializable
data class DraftCheckpoint(val name: String, val lat: Double, val lng: Double, val radius: Double, val instructions: String, val requiresPhoto:Boolean=true, val requiresTemperature:Boolean=true, val requiresWeight:Boolean=true)
data class FieldAlert(val id: Long, val text: String, val at: Long)
enum class ConnectionState { UNCONFIGURED, CONNECTING, CONNECTED, RECONNECTING }

class BackendViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<PPP62Application>()
    private val backend get() = app.backend
    private val config get() = app.backendConfig
    var backendEnabled = mutableStateOf(false); private set
    var connectionMessage = mutableStateOf<String?>(null); private set
    var connectionState = mutableStateOf(ConnectionState.UNCONFIGURED); private set
    var testingConnection = mutableStateOf(false); private set
    var lecturerAuthenticated = mutableStateOf(false); private set
    var onlineSession = mutableStateOf<SessionRow?>(null); private set
    var myUserId = mutableStateOf<String?>(null); private set
    var myRole = mutableStateOf("student"); private set
    private val _positions = MutableStateFlow<List<LivePositionRow>>(emptyList()); val positions = _positions.asStateFlow()
    private val _onlineSubmissions = MutableStateFlow<List<SubmissionRow>>(emptyList()); val onlineSubmissions = _onlineSubmissions.asStateFlow()
    private val _onlineCheckpoints = MutableStateFlow<List<CheckpointRow>>(emptyList()); val onlineCheckpoints = _onlineCheckpoints.asStateFlow()
    private val _roster = MutableStateFlow<List<ParticipantRow>>(emptyList()); val roster = _roster.asStateFlow()
    private val _history = MutableStateFlow<List<SessionRow>>(emptyList()); val history = _history.asStateFlow()
    private val _alerts = MutableStateFlow<List<FieldAlert>>(emptyList()); val alerts = _alerts.asStateFlow()
    private val snapshots=Mutex()
    private var observeJob: Job? = null
    private var pendingSession: SessionRow? = null
    private val insideState = mutableMapOf<Pair<String,String>,Boolean>()

    init { viewModelScope.launch {
        backendEnabled.value = backend.isConfigured()
        if (!backendEnabled.value) return@launch
        connectionState.value = ConnectionState.CONNECTING
        try {
            myUserId.value = backend.ensureSignedIn()
            lecturerAuthenticated.value = backend.isLecturerSignedIn()
            val restored = config.onlineSession()
            if(restored!=null && myUserId.value==config.verifiedUser()) {
                val cached=app.database.dao().sessionById(restored.second)
                if(cached!=null) {
                    onlineSession.value=SessionRow(cached.id,cached.joinCode,cached.name,isActive=cached.status==SessionStatus.ACTIVE)
                    myRole.value=config.role();app.repository.selectSession(cached.id)
                    _onlineCheckpoints.value=app.database.dao().checkpointsForSession(cached.id).map{CheckpointRow(it.id,it.sessionId,it.name,it.latitude,it.longitude,it.radiusMeters,it.orderIndex,it.instructions,it.requiresPhoto,it.requiresTemperature,it.requiresWeight)}
                    startObserving(cached.id)
                }
            }
            if (restored != null && myUserId.value != null) {
                val membership = backend.membership(restored.second, myUserId.value!!)
                val session = backend.findSessionById(restored.second)
                if (membership != null && session != null) activate(session, membership.role)
                else {config.clearOnlineSession();stopObserving();onlineSession.value=null;app.repository.selectSession(null)}
            }
        } catch (e: Exception) { failed(e) }
    } }
    private fun failed(e: Exception) {
        if (e is CancellationException) throw e
        connectionState.value = ConnectionState.RECONNECTING
        connectionMessage.value = UserFacingErrors.message(e, "Connection interrupted · retrying")
    }
    fun authenticate(email: String, password: String, register: Boolean = false, onResult: (String?) -> Unit) = viewModelScope.launch {
        try {
            if (register) backend.signUp(email,password) else backend.signIn(email,password)
            lecturerAuthenticated.value = backend.isLecturerSignedIn()
            myUserId.value = backend.ensureSignedIn()
            onResult(if (lecturerAuthenticated.value) null else "Check your email to confirm your account, then sign in")
        } catch (e: Exception) { onResult(UserFacingErrors.message(e, "Sign-in failed")) }
    }
    fun resetPassword(email: String, onResult: (String) -> Unit) = viewModelScope.launch {
        try { backend.resetPassword(email); onResult("Password reset email requested") }
        catch (e: Exception) { onResult(UserFacingErrors.message(e, "Unable to request reset")) }
    }
    private suspend fun activate(session: SessionRow, role: String) {
        config.saveOnlineSession(session.code, session.id); config.saveRole(role)
        myUserId.value?.let{config.saveVerifiedUser(it)}
        myRole.value = role; onlineSession.value = session
        app.repository.selectSession(session.id)
        app.database.dao().upsertSession(SessionEntity(session.id, session.title, session.code, "Lecturer", if(session.isActive) SessionStatus.ACTIVE else SessionStatus.COMPLETED, session.createdAt?.let { java.time.Instant.parse(it).toEpochMilli() } ?: System.currentTimeMillis(), null))
        startObserving(session.id)
        SyncWorker.enqueue(app)
    }
    fun joinOnline(code: String, displayName: String, role: String, team: String = "", onError: (String) -> Unit = {}, onJoined: (SessionRow) -> Unit = {}) = viewModelScope.launch {
        try {
            val uid = backend.ensureSignedIn() ?: error("Unable to sign in")
            val session = if(role=="lecturer") {
                val found = backend.findSession(code) ?: error("Session unavailable")
                require(found.ownerId == uid) { "Only the owner can monitor this session" }; found
            } else backend.joinByCode(code, displayName, team)
            myUserId.value=uid
            config.saveDisplayName(displayName); config.saveTeam(team)
            activate(session,role); onJoined(session)
        } catch(e: Exception) { onError(UserFacingErrors.message(e, "Join failed")) }
    }
    fun createSessionWithCheckpoints(title: String, drafts: List<DraftCheckpoint>, onResult: (String?,String?) -> Unit) = viewModelScope.launch {
        try {
            require(backend.isLecturerSignedIn()) { "Sign in with your lecturer account" }
            myUserId.value=backend.ensureSignedIn()
            val rows=drafts.mapIndexed { i,d -> CheckpointRow(sessionId="",name=d.name,lat=d.lat,lng=d.lng,radiusM=d.radius,orderIndex=i+1,instructions=d.instructions,requiresPhoto=d.requiresPhoto,requiresTemperature=d.requiresTemperature,requiresWeight=d.requiresWeight) }
            val session=backend.createSession(generateCode(),title,rows)
            config.saveDisplayName("Lecturer"); config.saveRole("lecturer")
            config.saveOnlineSession(session.code,session.id);myUserId.value?.let{config.saveVerifiedUser(it)}
            pendingSession=session; onResult(session.code,null)
        } catch(e:Exception) { onResult(null,UserFacingErrors.message(e, "Could not create session")) }
    }
    fun startMonitoring() = viewModelScope.launch { pendingSession?.let { pendingSession=null; activate(it,"lecturer") } }
    fun saveCheckpoint(row:CheckpointRow,onResult:(String?)->Unit)=viewModelScope.launch {
        try {backend.saveCheckpoint(row);refreshSnapshot(row.sessionId);onResult(null)}catch(e:Exception){onResult(UserFacingErrors.message(e, "Unable to save checkpoint"))}
    }
    fun removeCheckpoint(row:CheckpointRow)=viewModelScope.launch {
        try {requireNotNull(row.id);backend.removeCheckpoint(row.id);refreshSnapshot(row.sessionId)}catch(e:Exception){failed(e)}
    }
    fun refreshHistory() = viewModelScope.launch { try { _history.value=backend.history() } catch(e:Exception) { failed(e) } }
    fun openHistory(session: SessionRow) = viewModelScope.launch {
        try { val uid=backend.ensureSignedIn() ?: error("Sign in required"); require(session.ownerId==uid); activate(session,"lecturer") } catch(e:Exception) { failed(e) }
    }
    fun closeSession() = viewModelScope.launch {
        try { val session=onlineSession.value ?: return@launch; backend.finishSession(session.id); onlineSession.value=session.copy(isActive=false) }
        catch(e:Exception) { failed(e) }
    }
    fun leaveSession() = viewModelScope.launch {
        if(LocationTrackingService.identity.value!=null && LocationTrackingService.state.value!=TrackingState.FINISHED)
            app.startService(Intent(app,LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_FINISH))
        config.clearOnlineSession(); stopObserving(); app.repository.selectSession(null)
        onlineSession.value=null; myRole.value="student"
        _onlineCheckpoints.value=emptyList(); _onlineSubmissions.value=emptyList(); _roster.value=emptyList(); _alerts.value=emptyList(); insideState.clear()
    }
    fun refreshOnlineSubmissions() = viewModelScope.launch {
        try { onlineSession.value?.let { _onlineSubmissions.value=backend.listSubmissions(it.id) } } catch(e:Exception) { failed(e) }
    }
    suspend fun downloadEvidence(path: String): ByteArray? = backend.client()?.storage?.from("evidence")?.downloadAuthenticated(path)
    fun startObserving(sessionId: String) {
        observeJob?.cancel()
        observeJob=viewModelScope.launch {
            // Realtime delivers immediate movement; snapshots also recover missed changes/reconnects.
            launch {
                backend.observePositions(sessionId).retryWhen { error,_ -> failed(error as? Exception ?: Exception(error)); delay(5000); true }
                    .collect {
                        _positions.value=it; detectTransitions(it)
                        try { refreshSnapshot(sessionId) } catch(e:Exception) {failed(e)}
                    }
            }
            while(isActive) {
                try {
                    refreshSnapshot(sessionId)
                } catch(e:Exception) { failed(e) }
                delay(15000)
            }
        }
    }
    private suspend fun refreshSnapshot(sessionId:String) = snapshots.withLock {
        val session=backend.findSessionById(sessionId) ?: error("Session unavailable")
        onlineSession.value=session
        app.database.dao().upsertSession(SessionEntity(session.id,session.title,session.code,"Lecturer",if(session.isActive) SessionStatus.ACTIVE else SessionStatus.COMPLETED,runCatching{java.time.Instant.parse(session.createdAt).toEpochMilli()}.getOrDefault(0),null))
        val cps=backend.listCheckpoints(sessionId).sortedBy { it.orderIndex }
        _onlineCheckpoints.value=cps
        app.database.dao().replaceCheckpoints(sessionId,cps.mapIndexed { i,cp -> cp.toEntity(i) })
        _roster.value=backend.roster(sessionId)
        _onlineSubmissions.value=backend.listSubmissions(sessionId)
        _positions.value=backend.loadPositions(sessionId)
        if(!session.isActive && myRole.value=="student") app.stopService(Intent(app,LocationTrackingService::class.java))
        connectionState.value=ConnectionState.CONNECTED; connectionMessage.value=null
    }
    fun stopObserving() { observeJob?.cancel(); observeJob=null; _positions.value=emptyList() }
    private fun detectTransitions(list: List<LivePositionRow>) {
        list.forEach { p ->
            val at=runCatching { java.time.Instant.parse(p.recordedAt).toEpochMilli() }.getOrDefault(0)
            if(LocationUtils.isStale(at) || (p.accuracy ?: Double.MAX_VALUE)>100 || p.trackingState!="LIVE") return@forEach
            _onlineCheckpoints.value.forEach cpLoop@ { cp ->
                val key=p.userId to (cp.id ?: cp.name)
                val distance=LocationUtils.distanceMeters(p.lat,p.lng,cp.lat,cp.lng)
                val old=insideState[key]; val inside=distance<=cp.radiusM
                if(old==true && distance<cp.radiusM+20) return@cpLoop
                insideState[key]=inside
                if(old==null || old==inside) return@cpLoop
                val now=System.currentTimeMillis(); val text="${p.displayName} ${if(inside) "arrived at" else "left"} ${cp.name}"
                _alerts.value=(listOf(FieldAlert(now,text,now))+_alerts.value).take(50)
                if(myRole.value=="lecturer") Notifier.post(app,"Field activity",text)
            }
        }
    }
    fun refreshAuthentication()=viewModelScope.launch {lecturerAuthenticated.value=backend.isLecturerSignedIn();myUserId.value=backend.ensureSignedIn()}
    fun signOut()=viewModelScope.launch {leaveSession().join();backend.signOut();lecturerAuthenticated.value=false;myUserId.value=null;_history.value=emptyList()}
    fun refreshEnabled() = viewModelScope.launch { backendEnabled.value=backend.isConfigured() }
    fun saveConfig(url:String,key:String,onDone:(Boolean,String)->Unit) = viewModelScope.launch {
        testingConnection.value=true
        try { config.save(url,key); val result=backend.testConnection(); backendEnabled.value=result.isSuccess; onDone(result.isSuccess,result.exceptionOrNull()?.let { UserFacingErrors.message(it, "Unable to connect. Please try again.") } ?: "Connected") }
        finally { testingConnection.value=false }
    }
    fun clearConfig() = viewModelScope.launch { leaveSession(); config.clear(); backendEnabled.value=backend.isConfigured() }
    fun clearConnectionMessage() { connectionMessage.value=null }
    suspend fun savedIdentity()=config.displayName() to config.team()
    companion object {
        fun generateCode()=(1..6).map { "ABCDEFGHJKMNPQRSTUVWXYZ23456789".random() }.joinToString("")
    }
}
fun CheckpointRow.toEntity(order:Int)=CheckpointEntity(id ?: "$sessionId-$order",sessionId,name,lat,lng,radiusM,
    if(orderIndex>0) orderIndex else order+1,instructions,requiresPhoto,requiresTemperature,requiresWeight)
