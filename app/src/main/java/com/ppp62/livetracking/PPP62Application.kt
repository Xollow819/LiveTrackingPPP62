package com.ppp62.livetracking

import android.app.Application
import androidx.room.Room
import androidx.work.Configuration
import com.ppp62.livetracking.data.AppDatabase
import com.ppp62.livetracking.data.PPPRepository
import com.ppp62.livetracking.data.remote.BackendConfig
import com.ppp62.livetracking.data.remote.SupabaseBackend
import org.osmdroid.config.Configuration as OsmConfiguration

class PPP62Application : Application(), Configuration.Provider {
    lateinit var database: AppDatabase
        private set
    lateinit var repository: PPPRepository
        private set
    lateinit var backendConfig: BackendConfig
        private set
    lateinit var backend: SupabaseBackend
        private set

    override fun onCreate() {
        super.onCreate()
        OsmConfiguration.getInstance().apply {
            load(this@PPP62Application, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = "$packageName/${BuildConfig.VERSION_NAME}"
            tileDownloadThreads = 4 // Each source still enforces its own concurrency limit (OSM: 2).
            tileDownloadMaxQueueSize = 64
            cacheMapTileCount = if (getSystemService(android.app.ActivityManager::class.java).isLowRamDevice) 64 else 128
            tileFileSystemCacheMaxBytes = 256L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 192L * 1024 * 1024
        }
        database = Room.databaseBuilder(this, AppDatabase::class.java, "ppp62.db")
            .addMigrations(com.ppp62.livetracking.data.DatabaseMigrations.FROM_1_TO_2, com.ppp62.livetracking.data.DatabaseMigrations.FROM_2_TO_3)
            .build()
        repository = PPPRepository(database.dao())
        backendConfig = BackendConfig(this)
        backend = SupabaseBackend(backendConfig)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()
}
