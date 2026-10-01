package com.ppp62.livetracking.ui

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

    /** Field join for online sessions: no local demo session lookup, just the profile. */
    fun joinField(name: String, team: String) {
        profile.value = StudentProfile(name.trim(), team.trim(), true)
        message.value = "Joined the field session"
    }

    fun join(code: String, name: String, team: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val found = repository.joinSession(code)
        val valid = found != null && name.isNotBlank() && team.isNotBlank()
        if (valid) profile.value = StudentProfile(name.trim(), team.trim(), true)
        message.value = if (valid) "Joined ${found!!.name}" else "Check the join code, name, and team"
        onResult(valid)
    }

    fun submit(checkpoint: CheckpointEntity, temperature: Double, weight: Double, condition: FishCondition, notes: String, photoUri: String?, latitude: Double?, longitude: Double?, onDone: () -> Unit) = viewModelScope.launch {
        repository.submitCheckIn(checkpoint, profile.value.name, profile.value.team, temperature, weight, condition, notes, photoUri, latitude, longitude)
        // Best-effort online upload: photo to Storage + row to Supabase. Local Room stays the truth.
        val uploaded = runCatching {
            val app = getApplication<PPP62Application>()
            val session = app.backendConfig.onlineSession() ?: return@runCatching false
            val uid = app.backend.ensureSignedIn() ?: return@runCatching false
            val bytes = photoUri?.let { uri ->
                app.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { it.readBytes() }
            }
            val path = bytes?.let { app.backend.uploadEvidence(session.second, it) }
            app.backend.submitEvidence(
                SubmissionRow(
                    sessionId = session.second, userId = uid, displayName = profile.value.name,
                    checkpointName = checkpoint.name, note = notes, photoPath = path,
                    lat = latitude, lng = longitude,
                    temperatureC = temperature, weightKg = weight, condition = condition.name
                )
            )
            true
        }.getOrDefault(false)
        message.value = if (uploaded) "Check-in saved + uploaded ✓" else "Check-in saved locally"
        onDone()
    }

    fun addCheckpoint(name: String, lat: Double, lng: Double, radius: Double, instructions: String, onDone: () -> Unit) = viewModelScope.launch {
        repository.addCheckpoint(name, lat, lng, radius, instructions, checkpoints.value.size + 1)
        message.value = "Checkpoint created"
        onDone()
    }

    fun clearMessage() { message.value = null }
}
