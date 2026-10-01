package com.ppp62.livetracking.service

import android.content.Context
import androidx.work.*
import com.ppp62.livetracking.BuildConfig
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (BuildConfig.SYNC_ENDPOINT.isBlank()) return Result.success()
        // Authenticated institution/Firebase/Supabase transport plugs in here.
        // Pending records intentionally remain pending until the server acknowledges them.
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("ppp62-sync", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
