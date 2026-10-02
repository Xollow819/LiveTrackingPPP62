package com.ppp62.livetracking.ui

import com.ppp62.livetracking.util.UserFacingErrors

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ppp62.livetracking.PPP62Application
import com.ppp62.livetracking.data.*
import com.ppp62.livetracking.data.remote.SubmissionRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ppp62.livetracking.service.LocationTrackingService

data class StudentProfile(val name: String = "", val team: String = "", val joined: Boolean = false)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as PPP62Application).repository
    val sessions = repository.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val checkpoints = repository.checkpoints.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val checkIns = repository.checkIns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val locations = repository.locations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _savedTransportRecords = MutableStateFlow<Set<String>>(emptySet())
    val savedTransportRecords = _savedTransportRecords.asStateFlow()

    var profile = androidx.compose.runtime.mutableStateOf(StudentProfile())
        private set
    var message = androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    /**
     * Checkpoints of the joined online session (the map the lecturer configured),
     * held in memory so maps and check-ins work without a local copy.
     */
    var sessionCheckpoints = androidx.compose.runtime.mutableStateOf<List<CheckpointEntity>>(emptyList())
        private set

    fun setSessionCheckpoints(list: List<CheckpointEntity>) { sessionCheckpoints.value = list }
    fun resetSession() { repository.selectSession(null); profile.value = StudentProfile(); sessionCheckpoints.value = emptyList() }

    /** Field join for online sessions: no local demo session lookup, just the profile. */
    fun joinField(name: String, team: String) {
        profile.value = StudentProfile(name.trim(), team.trim(), true)
        message.value = "Joined the field session"
    }

    fun startSharing(sessionId: String, userId: String) {
        val app = getApplication<PPP62Application>()
        val marker = app.getSharedPreferences("participant_markers", android.content.Context.MODE_PRIVATE)
            .getString(ParticipantMarkers.preferenceKey(sessionId, userId), ParticipantMarkers.default) ?: ParticipantMarkers.default
        try {
            ContextCompat.startForegroundService(app, Intent(app, LocationTrackingService::class.java)
                .putExtra(LocationTrackingService.EXTRA_SESSION, sessionId).putExtra(LocationTrackingService.EXTRA_USER, userId)
                .putExtra(LocationTrackingService.EXTRA_NAME, profile.value.name).putExtra(LocationTrackingService.EXTRA_TEAM, profile.value.team)
                .putExtra(LocationTrackingService.EXTRA_MARKER_TYPE, ParticipantMarkers.get(marker).id))
        } catch (e: Exception) { message.value = UserFacingErrors.message(e, "Unable to start sharing. Check location permissions.") }
    }
    fun finishSharing(sessionId: String, userId: String) {
        val app = getApplication<PPP62Application>()
        JourneyStore(app).finish(sessionId, userId)
        if (LocationTrackingService.identity.value == (sessionId to userId)) {
            app.startService(Intent(app, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_FINISH))
        }
    }

    fun submit(checkpoint: CheckpointEntity, temperature: Double?, weight: Double?, condition: FishCondition, notes: String, photoUri: String?, latitude: Double?, longitude: Double?, onDone: () -> Unit, phase: TransportPhase? = null) = viewModelScope.launch {
        val app = getApplication<PPP62Application>()
        try {
            val uid = app.backend.ensureSignedIn() ?: app.backendConfig.verifiedUser().takeIf {
                it.isNotBlank() && app.backendConfig.onlineSession()?.second==checkpoint.sessionId
            } ?: error("Sign in before recording evidence")
            require(app.backendConfig.onlineSession()?.second == checkpoint.sessionId) { "Rejoin the session before recording evidence" }
            if (phase != null) require(TransportJourney.endpoint(sessionCheckpoints.value, phase)?.id == checkpoint.id) {
                "The route changed. Reopen the transport record."
            }
            require((temperature == null && !checkpoint.requiresTemperature || temperature?.isFinite() == true) && (weight == null && !checkpoint.requiresWeight || weight?.let {it.isFinite() && it>=0} == true)) { "Enter valid measurements" }
            val timing = JourneyStore(app).load(checkpoint.sessionId, uid)
            val recordNotes = if (phase == TransportPhase.END && timing.startedAt > 0) {
                "Journey time: ${JourneyTiming.format(timing.accumulatedMillis)}\n${notes.trim()}".trim()
            } else notes
            repository.submitCheckIn(checkpoint, profile.value.name, profile.value.team, temperature, weight, condition, recordNotes, photoUri, latitude, longitude, uid, phase)
            phase?.let { _savedTransportRecords.update { records -> records + TransportJourney.recordId(checkpoint.sessionId, uid, it) } }
            com.ppp62.livetracking.service.SyncWorker.enqueue(app)
            message.value = "${phase?.label ?: "Check-in"} saved · upload queued"
            onDone()
        } catch (e: Exception) { message.value = UserFacingErrors.message(e, "Unable to save check-in") }
    }

    fun retryUploads() = viewModelScope.launch {
        val app=getApplication<PPP62Application>()
        app.backend.ensureSignedIn()?.let{app.database.dao().retryFailed(it)}
        com.ppp62.livetracking.service.SyncWorker.enqueue(app)
    }
    fun clearMessage() { message.value = null }
}
