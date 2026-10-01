package com.ppp62.livetracking.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ppp62.livetracking.PPP62Application
import com.ppp62.livetracking.data.CheckpointEntity
import com.ppp62.livetracking.data.remote.CheckpointRow
import com.ppp62.livetracking.data.remote.LivePositionRow
import com.ppp62.livetracking.data.remote.SessionRow
import com.ppp62.livetracking.data.remote.SubmissionRow
import com.ppp62.livetracking.util.LocationUtils
import com.ppp62.livetracking.util.Notifier
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/** A checkpoint drafted by the lecturer on the setup map, before the join code exists. */
data class DraftCheckpoint(
    val name: String,
    val lat: Double,
    val lng: Double,
    val radius: Double,
    val instructions: String
)

/** A Find-My-style arrival/departure event shown in the lecturer's activity feed. */
data class FieldAlert(val id: Long, val text: String, val at: Long)

/**
 * Owns everything Supabase: configuration, connectivity, online session join,
 * checkpoint sync, the lecturer's live position stream, and geofence
 * arrival/departure detection. The local Room database remains the source of
 * truth for on-device data; this layer is best-effort and degrades offline.
 */
class BackendViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<PPP62Application>()
    private val backend get() = app.backend
    private val config get() = app.backendConfig

    var backendEnabled = mutableStateOf(false)
        private set
    var connectionMessage = mutableStateOf<String?>(null)
        private set
    var testingConnection = mutableStateOf(false)
        private set
    var onlineSession = mutableStateOf<SessionRow?>(null)
        private set
    var myUserId = mutableStateOf<String?>(null)
        private set
    var myRole = mutableStateOf("student")
        private set

    private val _positions = MutableStateFlow<List<LivePositionRow>>(emptyList())
    val positions: StateFlow<List<LivePositionRow>> = _positions
    private var observeJob: Job? = null

    private val _onlineSubmissions = MutableStateFlow<List<SubmissionRow>>(emptyList())
    val onlineSubmissions: StateFlow<List<SubmissionRow>> = _onlineSubmissions

    /** Checkpoints of the active online session — the map the lecturer configured. */
    private val _onlineCheckpoints = MutableStateFlow<List<CheckpointRow>>(emptyList())
    val onlineCheckpoints: StateFlow<List<CheckpointRow>> = _onlineCheckpoints

    /** Arrival/departure activity feed (newest first, capped at 50). */
    private val _alerts = MutableStateFlow<List<FieldAlert>>(emptyList())
    val alerts: StateFlow<List<FieldAlert>> = _alerts

    /** Last known inside/outside state per (student, checkpoint); first sighting primes silently. */
    private val insideState = mutableMapOf<Pair<String, String>, Boolean>()

    fun refreshOnlineSubmissions() = viewModelScope.launch {
        val session = onlineSession.value ?: return@launch
        _onlineSubmissions.value = runCatching { backend.listSubmissions(session.id) }.getOrDefault(emptyList())
    }

    /** Downloads evidence photo bytes (bucket is public). */
    suspend fun downloadEvidence(path: String): ByteArray? {
        val c = backend.client() ?: return null
        return runCatching { c.storage.from("evidence").downloadPublic(path) }.getOrNull()
    }

    init {
        viewModelScope.launch {
            backendEnabled.value = backend.isConfigured()
            val restored = config.onlineSession()
            onlineSession.value = restored?.let { (_, id) ->
                runCatching { backend.findSessionById(id) }.getOrNull()
            }
            val session = onlineSession.value
            if (session != null) {
                myRole.value = config.role()
                _onlineCheckpoints.value = runCatching { backend.listCheckpoints(session.id) }.getOrDefault(emptyList())
                if (myRole.value == "lecturer") startObserving(session.id)
                refreshOnlineSubmissions()
            }
        }
    }

    fun refreshEnabled() = viewModelScope.launch { backendEnabled.value = backend.isConfigured() }

    fun saveConfig(url: String, key: String, onDone: (Boolean, String) -> Unit) = viewModelScope.launch {
        testingConnection.value = true
        connectionMessage.value = null
        try {
            config.save(url, key)
            val result = backend.testConnection()
            result.onSuccess {
                backendEnabled.value = true
                connectionMessage.value = "Connected ✓"
                onDone(true, "Connected ✓")
            }.onFailure {
                backendEnabled.value = false
                connectionMessage.value = "Failed: ${it.message}"
                onDone(false, "Failed: ${it.message}")
            }
        } finally {
            testingConnection.value = false
        }
    }

    fun clearConfig() = viewModelScope.launch {
        config.clear()
        stopObserving()
        onlineSession.value = null
        _onlineCheckpoints.value = emptyList()
        _alerts.value = emptyList()
        insideState.clear()
        backendEnabled.value = false
        connectionMessage.value = "Backend disconnected — app works offline"
    }

    /**
     * Joins (or creates, for lecturers) the online session with [code].
     * Loads the session's checkpoints so students see the lecturer's map.
     */
    fun joinOnline(
        code: String,
        displayName: String,
        role: String,
        pin: String = "",
        team: String = "",
        onError: (String) -> Unit = {},
        onJoined: (SessionRow) -> Unit = {}
    ) = viewModelScope.launch {
        try {
            val uid = backend.ensureSignedIn() ?: throw IllegalStateException("Could not sign in (check connection)")
            myUserId.value = uid
            myRole.value = role
            var session = backend.findSession(code)
            if (session == null) {
                if (role != "lecturer") throw IllegalStateException("No session found with code ${code.uppercase()}")
                session = backend.createSession(code, "PPPVenza field session", pin.ifBlank { "1234" })
            } else if (role == "lecturer" && pin.isNotBlank() && session.lecturerPin.isNotBlank() && session.lecturerPin != pin) {
                throw IllegalStateException("Wrong lecturer PIN")
            }
            backend.joinSession(session.id, uid, displayName, role)
            config.saveOnlineSession(session.code, session.id)
            config.saveDisplayName(displayName)
            config.saveRole(role)
            if (team.isNotBlank()) config.saveTeam(team)
            onlineSession.value = session
            _onlineCheckpoints.value = runCatching { backend.listCheckpoints(session.id) }.getOrDefault(emptyList())
            if (role == "lecturer") startObserving(session.id)
            onJoined(session)
        } catch (e: Exception) {
            onError(e.message ?: "Join failed")
        }
    }

    /**
     * Lecturer finishing setup: creates the online session with a fresh random
     * join code and uploads the drafted checkpoints. Returns the code, or null
     * with an error message. Monitoring starts only when [startMonitoring] is
     * called, so the lecturer can copy/share the code first.
     */
    fun createSessionWithCheckpoints(
        title: String,
        pin: String,
        drafts: List<DraftCheckpoint>,
        onResult: (code: String?, error: String?) -> Unit
    ) = viewModelScope.launch {
        try {
            val uid = backend.ensureSignedIn() ?: throw IllegalStateException("Could not sign in (check connection)")
            myUserId.value = uid
            myRole.value = "lecturer"
            var code = generateCode()
            var guard = 0
            while (backend.findSession(code) != null && guard++ < 10) code = generateCode()
            val session = backend.createSession(code, title.ifBlank { "PPPVenza field session" }, pin)
            drafts.forEach { d ->
                backend.addCheckpoint(
                    CheckpointRow(sessionId = session.id, name = d.name, lat = d.lat, lng = d.lng, radiusM = d.radius)
                )
            }
            backend.joinSession(session.id, uid, "Lecturer", "lecturer")
            config.saveOnlineSession(session.code, session.id)
            config.saveDisplayName("Lecturer")
            config.saveRole("lecturer")
            pendingSession = session
            _onlineCheckpoints.value = runCatching { backend.listCheckpoints(session.id) }.getOrDefault(emptyList())
            onResult(session.code, null)
        } catch (e: Exception) {
            onResult(null, e.message ?: "Could not create the session")
        }
    }

    private var pendingSession: SessionRow? = null

    /** Starts the monitoring view for a session created via [createSessionWithCheckpoints]. */
    fun startMonitoring() {
        val session = pendingSession ?: return
        pendingSession = null
        onlineSession.value = session
        startObserving(session.id)
        refreshOnlineSubmissions()
    }

    /** Leaves the current online session (keeps the backend configured). */
    fun leaveSession() = viewModelScope.launch {
        config.clearOnlineSession()
        stopObserving()
        onlineSession.value = null
        _onlineCheckpoints.value = emptyList()
        _alerts.value = emptyList()
        insideState.clear()
    }

    fun startObserving(sessionId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            backend.observePositions(sessionId)
                .retryWhen { _, _ -> delay(5_000); true }
                .catch { }
                .collect { list ->
                    _positions.value = list
                    detectTransitions(list)
                }
        }
    }

    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
        _positions.value = emptyList()
    }

    /**
     * Find-My-style geofence alerts: when a tracked student crosses a
     * checkpoint radius boundary, post a local notification and prepend to the
     * activity feed. First sighting of each (student, checkpoint) pair only
     * primes the state, so there is no burst of false arrivals on connect.
     */
    private fun detectTransitions(positions: List<LivePositionRow>) {
        val cps = _onlineCheckpoints.value
        if (cps.isEmpty()) return
        positions.forEach { p ->
            cps.forEach { cp ->
                val key = p.userId to (cp.id ?: cp.name)
                val inside = LocationUtils.distanceMeters(p.lat, p.lng, cp.lat, cp.lng) <= cp.radiusM
                val was = insideState[key]
                insideState[key] = inside
                if (was == null || was == inside) return@forEach
                val name = p.displayName.ifBlank { "A student" }
                val text = if (inside) "$name arrived at ${cp.name}" else "$name left ${cp.name}"
                _alerts.value = (listOf(FieldAlert(System.currentTimeMillis(), text, System.currentTimeMillis())) + _alerts.value).take(50)
                Notifier.post(app, "PPPVenza", text)
            }
        }
    }

    /** Publishes one position fix; safe to call on every GPS update (throttled by caller). */
    fun publishPosition(lat: Double, lng: Double, accuracy: Double?) {
        val session = onlineSession.value ?: return
        val uid = myUserId.value ?: return
        viewModelScope.launch {
            runCatching {
                backend.publishPosition(session.id, uid, config.displayName().ifBlank { "Student" }, lat, lng, accuracy)
            }
        }
    }

    fun clearConnectionMessage() { connectionMessage.value = null }

    /** Saved student identity for restoring a joined session after app restart. */
    suspend fun savedIdentity(): Pair<String, String> = config.displayName() to config.team()

    companion object {
        private val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray()
        fun generateCode(): String = (1..6).map { CODE_ALPHABET.random() }.joinToString("")
    }
}

/** Converts an online checkpoint row to a local entity for maps and check-ins. */
fun CheckpointRow.toEntity(order: Int): CheckpointEntity = CheckpointEntity(
    id = id ?: "$sessionId-$order",
    sessionId = sessionId,
    name = name,
    latitude = lat,
    longitude = lng,
    radiusMeters = radiusM,
    orderIndex = order + 1,
    instructions = ""
)
