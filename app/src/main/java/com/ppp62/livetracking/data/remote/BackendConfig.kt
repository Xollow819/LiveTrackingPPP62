package com.ppp62.livetracking.data.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ppp62.livetracking.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.backendDataStore by preferencesDataStore("backend_config")

/**
 * Supabase project URL + anon key.
 *
 * Resolution order: values saved on-device (DataStore) win; otherwise the
 * keys bundled into the APK at build time (BuildConfig, from the gitignored
 * local.properties) are used, so the app is online out of the box with no
 * per-device setup. Nothing is hard-coded in source control.
 */
class BackendConfig(private val context: Context) {
    companion object {
        private val KEY_URL = stringPreferencesKey("supabase_url")
        private val KEY_ANON = stringPreferencesKey("supabase_anon_key")
        private val KEY_SESSION_CODE = stringPreferencesKey("online_session_code")
        private val KEY_SESSION_ID = stringPreferencesKey("online_session_id")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_ROLE = stringPreferencesKey("role")
        private val KEY_TEAM = stringPreferencesKey("team")
    }

    val url: Flow<String> = context.backendDataStore.data.map { it[KEY_URL].orEmpty() }
    val anonKey: Flow<String> = context.backendDataStore.data.map { it[KEY_ANON].orEmpty() }

    /** True when the backend was bundled into this APK at build time. */
    fun isBundled(): Boolean =
        BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    suspend fun isConfigured(): Boolean =
        currentUrl().isNotBlank() && currentAnonKey().isNotBlank()

    suspend fun save(url: String, anonKey: String) {
        context.backendDataStore.edit {
            it[KEY_URL] = url.trim().trimEnd('/')
            it[KEY_ANON] = anonKey.trim()
        }
    }

    /** Clears on-device overrides; bundled build-time keys (if any) still apply. */
    suspend fun clear() {
        context.backendDataStore.edit { it.remove(KEY_URL); it.remove(KEY_ANON) }
    }

    suspend fun currentUrl(): String =
        context.backendDataStore.data.first()[KEY_URL].orEmpty().ifBlank { BuildConfig.SUPABASE_URL }
    suspend fun currentAnonKey(): String =
        context.backendDataStore.data.first()[KEY_ANON].orEmpty().ifBlank { BuildConfig.SUPABASE_ANON_KEY }

    /** Session the device joined online (if any). */
    suspend fun saveOnlineSession(code: String, sessionId: String) {
        context.backendDataStore.edit { it[KEY_SESSION_CODE] = code; it[KEY_SESSION_ID] = sessionId }
    }

    suspend fun onlineSession(): Pair<String, String>? =
        context.backendDataStore.data.first().let {
            val code = it[KEY_SESSION_CODE].orEmpty(); val id = it[KEY_SESSION_ID].orEmpty()
            if (code.isNotBlank() && id.isNotBlank()) code to id else null
        }

    suspend fun clearOnlineSession() {
        context.backendDataStore.edit { it.remove(KEY_SESSION_CODE); it.remove(KEY_SESSION_ID) }
    }

    suspend fun saveDisplayName(name: String) { context.backendDataStore.edit { it[KEY_DISPLAY_NAME] = name } }
    suspend fun displayName(): String = context.backendDataStore.data.first()[KEY_DISPLAY_NAME].orEmpty()

    suspend fun saveRole(role: String) { context.backendDataStore.edit { it[KEY_ROLE] = role } }
    suspend fun role(): String = context.backendDataStore.data.first()[KEY_ROLE].orEmpty().ifBlank { "student" }

    suspend fun saveTeam(team: String) { context.backendDataStore.edit { it[KEY_TEAM] = team } }
    suspend fun team(): String = context.backendDataStore.data.first()[KEY_TEAM].orEmpty()
}
