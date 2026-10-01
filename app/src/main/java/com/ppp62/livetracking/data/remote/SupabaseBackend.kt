package com.ppp62.livetracking.data.remote

import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.minutes
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.engine.android.Android
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import java.util.UUID

/**
 * Supabase backend for multi-device live tracking.
 *
 * - Anonymous auth (no email/password; enable "Allow anonymous sign-ins" in the
 *   Supabase dashboard under Authentication -> Providers).
 * - Students upsert their latest position; lecturers subscribe via Realtime.
 * - Evidence photos go to the `evidence` storage bucket.
 * - Supabase owns shared data; Room caches session data and queues evidence retries.
 */
class SupabaseBackend(private val config: BackendConfig) {
    val recoveryRequested=kotlinx.coroutines.flow.MutableStateFlow(false)
    private val wireJson = Json {encodeDefaults=true}

    @Volatile private var client: io.github.jan.supabase.SupabaseClient? = null
    @Volatile private var clientKey: String? = null

    /** Returns a client, or null when the backend is not configured. */
    suspend fun client(): io.github.jan.supabase.SupabaseClient? {
        val url = config.currentUrl()
        val key = config.currentAnonKey()
        if (url.isBlank() || key.isBlank()) return null
        val cacheKey = "$url|$key"
        if (client == null || clientKey != cacheKey) {
            client = createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
                httpEngine = Android.create()
                install(Auth) { scheme="pppvenza";host="auth";flowType=FlowType.PKCE }
                install(Postgrest)
                install(Realtime)
                install(Storage)
            }
            clientKey = cacheKey
        }
        return client
    }

    suspend fun isConfigured(): Boolean = config.isConfigured()

    /** Signs in anonymously if needed; returns the auth user id or null. */
    suspend fun ensureSignedIn(): String? {
        val c = client() ?: return null
        c.auth.awaitInitialization()
        return try {
            c.auth.currentUserOrNull()?.id ?: run {
                c.auth.signInAnonymously()
                c.auth.currentUserOrNull()?.id
            }
        } catch (_: Exception) { null }
    }

    /** Lightweight connectivity + credentials check for the Settings screen. */
    suspend fun testConnection(): Result<String> = runCatching {
        val c = client() ?: throw IllegalStateException("Supabase URL / anon key not set")
        val uid = ensureSignedIn() ?: throw IllegalStateException("Anonymous sign-in failed — enable it under Authentication → Providers in the Supabase dashboard")
        // A cheap authenticated read that also validates RLS.
        c.from("tracking_sessions").select { filter { eq("code", "__ping__") } }.decodeList<SessionRow>()
        "Connected as $uid"
    }

    // ------------------------------------------------------------------ sessions

    suspend fun findSession(code: String): SessionRow? {
        val c = client() ?: return null
        ensureSignedIn() ?: return null
        return c.from("tracking_sessions").select {
            filter { eq("code", code.trim().uppercase()) }
        }.decodeSingleOrNull<SessionRow>()
    }

    suspend fun findSessionById(id: String): SessionRow? {
        val c = client() ?: return null
        ensureSignedIn() ?: return null
        return c.from("tracking_sessions").select {
            filter { eq("id", id) }
        }.decodeSingleOrNull<SessionRow>()
    }

    suspend fun signIn(email: String, password: String) {
        val c = client() ?: error("Backend not configured")
        c.auth.signInWith(Email) { this.email = email.trim(); this.password = password }
    }
    suspend fun signUp(email: String, password: String) {
        val c = client() ?: error("Backend not configured")
        c.auth.signUpWith(Email) { this.email = email.trim(); this.password = password }
    }
    suspend fun resetPassword(email: String) {
        val c = client() ?: error("Backend not configured")
        c.auth.resetPasswordForEmail(email.trim(),redirectUrl="pppvenza://auth?recovery=1")
    }
    suspend fun handleAuthIntent(intent:android.content.Intent) {
        val c=client() ?: return
        c.handleDeeplinks(intent,onSessionSuccess={
            if(intent.data?.getQueryParameter("recovery")=="1") recoveryRequested.value=true
        })
    }
    suspend fun changePassword(password:String) {
        require(password.length>=8) {"Use at least eight characters"}
        requireNotNull(client()).auth.updateUser { this.password=password }
        recoveryRequested.value=false
    }
    suspend fun signOut() {client()?.auth?.signOut()}
    suspend fun isLecturerSignedIn(): Boolean = client()?.auth?.currentUserOrNull()?.email?.isNotBlank() == true
    suspend fun createSession(code: String, title: String, checkpoints: List<CheckpointRow>): SessionRow {
        val c = client() ?: error("Backend not configured")
        return c.postgrest.rpc("create_field_session", buildJsonObject {
            put("p_code", code); put("p_title", title)
            put("p_checkpoints", wireJson.encodeToJsonElement(checkpoints))
        }).decodeSingle<SessionRow>()
    }
    suspend fun joinByCode(code: String, displayName: String, team: String): SessionRow {
        val c = client() ?: error("Backend not configured")
        return c.postgrest.rpc("join_field_session", buildJsonObject {
            put("p_code", code.trim().uppercase()); put("p_name", displayName.trim()); put("p_team", team.trim())
        }).decodeSingle<SessionRow>()
    }
    suspend fun membership(sessionId: String, userId: String): ParticipantRow? = client()?.from("session_participants")?.select {
        filter { eq("session_id", sessionId); eq("user_id", userId) }
    }?.decodeSingleOrNull<ParticipantRow>()
    suspend fun roster(sessionId: String): List<ParticipantRow> = requireNotNull(client()).from("session_participants").select {
        filter { eq("session_id", sessionId) }
    }.decodeList()
    suspend fun history(): List<SessionRow> = requireNotNull(client()).from("tracking_sessions").select().decodeList()
    suspend fun finishSession(sessionId: String) {
        requireNotNull(client()).postgrest.rpc("close_field_session", buildJsonObject { put("p_session", sessionId) })
    }

    // ------------------------------------------------------------------ positions

    /** Publishes the student's latest position (upsert keyed on session+user). */
    suspend fun publishPosition(sessionId: String, userId: String, displayName: String, lat: Double, lng: Double, accuracy: Double?, recordedAt: Long = System.currentTimeMillis(), team: String = "", state: String = "LIVE", eventAt:Long=System.currentTimeMillis()) {
        val c = client() ?: error("Backend not configured")
        c.from("live_positions").upsert(LivePositionRow(sessionId, userId, displayName, lat, lng, accuracy,
            team, state, java.time.Instant.ofEpochMilli(recordedAt).toString(),eventAt=java.time.Instant.ofEpochMilli(eventAt).toString())) { onConflict = "session_id,user_id" }
    }
    suspend fun loadPositions(sessionId: String): List<LivePositionRow> = requireNotNull(client()).from("live_positions").select {
        filter { eq("session_id", sessionId) }
    }.decodeList()

    /**
     * Emits the full position list for a session, refreshing on every Realtime
     * change. Re-collect to reconnect; the flow ends if the backend is gone.
     */
    fun observePositions(sessionId: String): Flow<List<LivePositionRow>> = flow {
        val c = client() ?: return@flow
        emit(loadPositions(sessionId))
        val channel = c.realtime.channel("positions-$sessionId")
        try {
            val changes = listOf("live_positions","checkpoints","submissions","session_participants","tracking_sessions").map { tableName ->
                channel.postgresChangeFlow<PostgresAction>("public") {
                    table = tableName
                    filter = if(tableName=="tracking_sessions") "id=eq.$sessionId" else "session_id=eq.$sessionId"
                }
            }
            channel.subscribe()
            merge(*changes.toTypedArray()).collect { emit(loadPositions(sessionId)) }
        } finally {
            runCatching { c.realtime.removeChannel(channel) }
        }
    }

    // ------------------------------------------------------------------ submissions

    /** Uploads JPEG bytes; returns the storage path to store in the submission row. */
    suspend fun uploadEvidence(sessionId: String, userId: String, submissionId: String, bytes: ByteArray): String {
        val c = client() ?: throw IllegalStateException("Backend not configured")
        val path = "$sessionId/$userId/$submissionId.jpg"
        c.storage.from("evidence").upload(path, bytes) { upsert = true;contentType=ContentType.Image.JPEG }
        return path
    }

    /** Short-lived authorised URL for the private evidence bucket. */
    suspend fun evidenceUrl(path: String): String? {
        val c = client() ?: return null
        return runCatching { c.storage.from("evidence").createSignedUrl(path, 10.minutes) }.getOrNull()
    }

    suspend fun submitEvidence(row: SubmissionRow) {
        val c = client() ?: throw IllegalStateException("Backend not configured")
        c.postgrest.rpc("submit_field_evidence", buildJsonObject { put("p_record", wireJson.encodeToJsonElement(row)) })
    }

    suspend fun submissionById(id:String):SubmissionRow? = requireNotNull(client()).from("submissions").select {filter{eq("id",id)}}.decodeSingleOrNull<SubmissionRow>()
    suspend fun listSubmissions(sessionId: String): List<SubmissionRow> {
        val c = client() ?: error("Backend not configured")
        return run {
            c.from("submissions").select { filter { eq("session_id", sessionId) } }.decodeList()
        }
    }

    // ------------------------------------------------------------------ checkpoints

    suspend fun listCheckpoints(sessionId: String): List<CheckpointRow> {
        val c = client() ?: error("Backend not configured")
        return run {
            c.from("checkpoints").select { filter { eq("session_id", sessionId) } }.decodeList()
        }
    }

    suspend fun saveCheckpoint(row:CheckpointRow) { requireNotNull(client()).postgrest.rpc("save_field_checkpoint",buildJsonObject{put("p_checkpoint",wireJson.encodeToJsonElement(row))}) }
    suspend fun removeCheckpoint(id:String) {requireNotNull(client()).from("checkpoints").delete{filter{eq("id",id)}}}
    suspend fun addCheckpoint(row: CheckpointRow) {
        val c = client() ?: throw IllegalStateException("Backend not configured")
        c.from("checkpoints").insert(row.copy(id = row.id ?: UUID.randomUUID().toString()))
    }
}
