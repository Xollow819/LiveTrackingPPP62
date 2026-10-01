package com.ppp62.livetracking.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ppp62.livetracking.PPP62Application
import com.ppp62.livetracking.data.remote.LivePositionRow
import com.ppp62.livetracking.data.remote.SessionRow
import com.ppp62.livetracking.data.remote.SubmissionRow
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/**
 * Owns everything Supabase: configuration, connectivity, online session join,
 * and the lecturer's live position stream. The local Room database remains the
 * source of truth; this layer is best-effort and degrades silently offline.
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
            onlineSession.value = config.onlineSession()?.let { (_, id) ->
                runCatching { backend.findSessionById(id) }.getOrNull()
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
        backendEnabled.value = false
        connectionMessage.value = "Backend disconnected — app works offline"
    }

    /**
     * Joins (or creates, for lecturers) the online session with [code].
     * Returns the session, or null with [onError] called.
     */
    fun joinOnline(code: String, displayName: String, role: String, pin: String = "", onError: (String) -> Unit = {}) =
        viewModelScope.launch {
            try {
                val uid = backend.ensureSignedIn() ?: throw IllegalStateException("Could not sign in (check connection)")
                myUserId.value = uid
                myRole.value = role
                var session = backend.findSession(code)
                if (session == null) {
                    if (role != "lecturer") throw IllegalStateException("No online session with code ${code.uppercase()}")
                    session = backend.createSession(code, "PPP62 field session", pin.ifBlank { "1234" })
                } else if (role == "lecturer" && pin.isNotBlank() && session.lecturerPin.isNotBlank() && session.lecturerPin != pin) {
                    throw IllegalStateException("Wrong lecturer PIN")
                }
                backend.joinSession(session.id, uid, displayName, role)
                config.saveOnlineSession(session.code, session.id)
                config.saveDisplayName(displayName)
                onlineSession.value = session
                if (role == "lecturer") startObserving(session.id)
            } catch (e: Exception) {
                onError(e.message ?: "Join failed")
            }
        }

    fun startObserving(sessionId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            backend.observePositions(sessionId)
                .retryWhen { _, _ -> delay(5_000); true }
                .catch { }
                .collect { _positions.value = it }
        }
    }

    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
        _positions.value = emptyList()
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
}
