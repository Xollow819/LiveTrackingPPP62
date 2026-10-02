package com.ppp62.livetracking.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class JourneyStore(context: Context) {
    private val context = context.applicationContext
    private val prefs = context.getSharedPreferences("transport_journeys", Context.MODE_PRIVATE)
    fun bootCount() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun prefix(sessionId: String, userId: String) = "$sessionId:$userId:"
    fun load(sessionId: String, userId: String): JourneyTiming {
        val key = prefix(sessionId, userId)
        return JourneyTiming(prefs.getLong(key + "start", 0), prefs.getLong(key + "finish", 0),
            prefs.getLong(key + "total", 0), prefs.getLong(key + "wall", 0),
            prefs.getLong(key + "elapsed", 0), prefs.getInt(key + "boot", -1))
    }
    fun observe(sessionId: String, userId: String) = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith(prefix(sessionId, userId)) == true) trySend(load(sessionId, userId))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(load(sessionId, userId))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
    private fun update(sessionId: String, userId: String, transform: (JourneyTiming, Long, Long, Int) -> JourneyTiming) {
        val next = transform(load(sessionId, userId), System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount())
        val key = prefix(sessionId, userId)
        prefs.edit().putLong(key + "start", next.startedAt).putLong(key + "finish", next.finishedAt)
            .putLong(key + "total", next.accumulatedMillis).putLong(key + "wall", next.runningSinceWall)
            .putLong(key + "elapsed", next.runningSinceElapsed).putInt(key + "boot", next.bootCount).apply()
    }
    fun resume(sessionId: String, userId: String) = update(sessionId, userId) { timing, wall, elapsed, boot -> timing.resume(wall, elapsed, boot) }
    fun pause(sessionId: String, userId: String) = update(sessionId, userId) { timing, wall, elapsed, boot -> timing.pause(wall, elapsed, boot) }
    fun finish(sessionId: String, userId: String) = update(sessionId, userId) { timing, wall, elapsed, boot -> timing.finish(wall, elapsed, boot) }
}
