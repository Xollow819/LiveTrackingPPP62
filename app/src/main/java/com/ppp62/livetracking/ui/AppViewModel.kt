package com.ppp62.livetracking.ui

import com.ppp62.livetracking.util.UserFacingErrors

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ppp62.livetracking.PPP62Application
import com.ppp62.livetracking.data.*
import com.ppp62.livetracking.data.remote.SubmissionRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StudentProfile(val name: String = "", val team: String = "", val joined: Boolean = false)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as PPP62Application).repository
    val sessions = repository.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val checkpoints = repository.checkpoints.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val checkIns = repository.checkIns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val locations = repository.locations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun submit(checkpoint: CheckpointEntity, temperature: Double?, weight: Double?, condition: FishCondition, notes: String, photoUri: String?, latitude: Double?, longitude: Double?, onDone: () -> Unit) = viewModelScope.launch {
        val app = getApplication<PPP62Application>()
        try {
            val uid = app.backend.ensureSignedIn() ?: app.backendConfig.verifiedUser().takeIf {
                it.isNotBlank() && app.backendConfig.onlineSession()?.second==checkpoint.sessionId
            } ?: error("Sign in before recording evidence")
            require((temperature == null && !checkpoint.requiresTemperature || temperature?.isFinite() == true) && (weight == null && !checkpoint.requiresWeight || weight?.let {it.isFinite() && it>=0} == true)) { "Enter valid measurements" }
            repository.submitCheckIn(checkpoint, profile.value.name, profile.value.team, temperature, weight, condition, notes, photoUri, latitude, longitude, uid)
            com.ppp62.livetracking.service.SyncWorker.enqueue(app)
            message.value = "Check-in saved · upload queued"
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
