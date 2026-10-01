package com.ppp62.livetracking.data.remote

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * Supabase backend for multi-device live tracking.
 *
 * - Anonymous auth (no email/password; enable "Allow anonymous sign-ins" in the
 *   Supabase dashboard under Authentication -> Providers).
 * - Students upsert their latest position; lecturers subscribe via Realtime.
 * - Evidence photos go to the `evidence` storage bucket.
 * - Everything here is best-effort: callers must keep the local Room database
 *   as the source of truth when the backend is unconfigured or offline.
 */
class SupabaseBackend(private val config: BackendConfig) {

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
                install(Auth)
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

    suspend fun createSession(code: String, title: String, pin: String): SessionRow {
        val c = client() ?: throw IllegalStateException("Backend not configured")
        ensureSignedIn() ?: throw IllegalStateException("Sign-in failed")
        val row = SessionRow(id = UUID.randomUUID().toString(), code = code.trim().uppercase(), title = title.trim(), lecturerPin = pin.trim())
        // Upsert may return no representation depending on server settings;
        // fall back to a fresh read instead of crashing on decode.
        val inserted = runCatching {
            c.from("tracking_sessions").upsert(row) { onConflict = "code" }.decodeSingle<SessionRow>()
        }.getOrNull()
        return inserted ?: findSession(row.code) ?: row
    }

    suspend fun joinSession(sessionId: String, userId: String, displayName: String, role: String) {
        val c = client() ?: throw IllegalStateException("Backend not configured")
        c.from("session_participants").upsert(
            ParticipantRow(sessionId = sessionId, userId = userId, displayName = displayName, role = role)
        ) { onConflict = "session_id,user_id" }
    }

    // ------------------------------------------------------------------ positions

    /** Publishes the student's latest position (upsert keyed on session+user). */
    suspend fun publishPosition(sessionId: String, userId: String, displayName: String, lat: Double, lng: Double, accuracy: Double?) {
        val c = client() ?: return
        try {
            c.from("live_positions").upsert(
                LivePositionRow(sessionId = sessionId, userId = userId, displayName = displayName, lat = lat, lng = lng, accuracy = accuracy)
            ) { onConflict = "session_id,user_id" }
        } catch (_: Exception) { /* offline or backend hiccup: local Room keeps the truth */ }
    }

    suspend fun loadPositions(sessionId: String): List<LivePositionRow> {
        val c = client() ?: return emptyList()
        return try {
            c.from("live_positions").select { filter { eq("session_id", sessionId) } }.decodeList()
        } catch (_: Exception) { emptyList() }
    }

    /**
     * Emits the full position list for a session, refreshing on every Realtime
     * change. Re-collect to reconnect; the flow ends if the backend is gone.
     */
    fun observePositions(sessionId: String): Flow<List<LivePositionRow>> = flow {
        val c = client() ?: return@flow
        emit(loadPositions(sessionId))
        val channel = c.realtime.channel("positions-$sessionId")
        try {
            val changes = channel.postgresChangeFlow<PostgresAction>("public") {
                table = "live_positions"
                filter = "session_id=eq.$sessionId"
            }
            channel.subscribe()
            changes.collect { emit(loadPositions(sessionId)) }
        } finally {
            runCatching { c.realtime.removeChannel(channel) }
        }
    }

    // ------------------------------------------------------------------ submissions

    /** Uploads JPEG bytes; returns the storage path to store in the submission row. */
    suspend fun uploadEvidence(sessionId: String, bytes: ByteArray): String {
        val c = client() ?: throw IllegalStateException("Backend not configured")
        val path = "$sessionId/${UUID.randomUUID()}.jpg"
        c.storage.from("evidence").upload(path, bytes) { upsert = false }
        return path
    }

    /** Synchronous public URL (bucket is public, no auth needed). */
    suspend fun evidenceUrl(path: String): String? {
        val c = client() ?: return null
        return runCatching { c.storage.from("evidence").publicUrl(path) }.getOrNull()
    }

    suspend fun submitEvidence(row: SubmissionRow) {
        val c = client() ?: throw IllegalStateException("Backend not configured")
        c.from("submissions").insert(row)
    }

    suspend fun listSubmissions(sessionId: String): List<SubmissionRow> {
        val c = client() ?: return emptyList()
        return try {
            c.from("submissions").select { filter { eq("session_id", sessionId) } }.decodeList()
        } catch (_: Exception) { emptyList() }
    }

    // ------------------------------------------------------------------ checkpoints

    suspend fun listCheckpoints(sessionId: String): List<CheckpointRow> {
        val c = client() ?: return emptyList()
        return try {
            c.from("checkpoints").select { filter { eq("session_id", sessionId) } }.decodeList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun addCheckpoint(row: CheckpointRow) {
        val c = client() ?: throw IllegalStateException("Backend not configured")
        c.from("checkpoints").insert(row.copy(id = row.id ?: UUID.randomUUID().toString()))
    }
}
