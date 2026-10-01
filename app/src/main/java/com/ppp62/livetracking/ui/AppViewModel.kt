package com.ppp62.livetracking.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ppp62.livetracking.PPP62Application
import com.ppp62.livetracking.data.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StudentProfile(val name: String = "Student", val team: String = "Team A", val joined: Boolean = false)

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

    fun join(code: String, name: String, team: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val found = repository.joinSession(code)
        val valid = found != null && name.isNotBlank() && team.isNotBlank()
        if (valid) profile.value = StudentProfile(name.trim(), team.trim(), true)
        message.value = if (valid) "Joined ${found!!.name}" else "Check the join code, name, and team"
        onResult(valid)
    }

    fun submit(checkpoint: CheckpointEntity, temperature: Double, weight: Double, condition: FishCondition, notes: String, photoUri: String?, latitude: Double?, longitude: Double?, onDone: () -> Unit) = viewModelScope.launch {
        repository.submitCheckIn(checkpoint, profile.value.name, profile.value.team, temperature, weight, condition, notes, photoUri, latitude, longitude)
        message.value = "Check-in saved locally"
        onDone()
    }

    fun addCheckpoint(name: String, lat: Double, lng: Double, radius: Double, instructions: String, onDone: () -> Unit) = viewModelScope.launch {
        repository.addCheckpoint(name, lat, lng, radius, instructions, checkpoints.value.size + 1)
        message.value = "Checkpoint created"
        onDone()
    }

    fun clearMessage() { message.value = null }
}
