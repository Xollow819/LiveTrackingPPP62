package com.ppp62.livetracking.data.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.backendDataStore by preferencesDataStore("backend_config")

/**
 * Stores the Supabase project URL + anon key entered by the lecturer.
 * Nothing is hard-coded: the APK ships unconfigured and works fully offline
 * until a backend is entered in Settings.
 */
class BackendConfig(private val context: Context) {
    companion object {
        private val KEY_URL = stringPreferencesKey("supabase_url")
        private val KEY_ANON = stringPreferencesKey("supabase_anon_key")
        private val KEY_SESSION_CODE = stringPreferencesKey("online_session_code")
        private val KEY_SESSION_ID = stringPreferencesKey("online_session_id")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
    }

    val url: Flow<String> = context.backendDataStore.data.map { it[KEY_URL].orEmpty() }
    val anonKey: Flow<String> = context.backendDataStore.data.map { it[KEY_ANON].orEmpty() }

    suspend fun isConfigured(): Boolean =
        context.backendDataStore.data.first().let { it[KEY_URL].orEmpty().isNotBlank() && it[KEY_ANON].orEmpty().isNotBlank() }

    suspend fun save(url: String, anonKey: String) {
        context.backendDataStore.edit {
            it[KEY_URL] = url.trim().trimEnd('/')
            it[KEY_ANON] = anonKey.trim()
        }
    }

    suspend fun clear() {
        context.backendDataStore.edit { it.remove(KEY_URL); it.remove(KEY_ANON) }
    }

    suspend fun currentUrl(): String = context.backendDataStore.data.first()[KEY_URL].orEmpty()
    suspend fun currentAnonKey(): String = context.backendDataStore.data.first()[KEY_ANON].orEmpty()

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
}
