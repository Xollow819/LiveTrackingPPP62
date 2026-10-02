package com.ppp62.livetracking

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.russhwolf.settings.Settings
import io.github.jan.supabase.auth.SettingsCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Supabase must retain its AndroidX initializer when WorkManager initialization is customised. */
@RunWith(AndroidJUnit4::class)
class StartupInitializationTest {
    @Test fun defaultAuthStorageIsAvailableAtStartup() {
        val settings = Settings()
        assertNotNull(SettingsSessionManager(settings))
        assertNotNull(SettingsCodeVerifierCache(settings))
    }
}
