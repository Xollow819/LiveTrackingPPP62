package com.ppp62.livetracking.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.LocationListener
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.util.GeoPoint
import kotlin.coroutines.resume

/**
 * Device's current location via the platform LocationManager (no extra SDK).
 * Returns the best last-known fix, or null when permission is missing or no
 * fix is available yet.
 */
object DeviceLocation {
    /** Waits for a newly delivered provider fix, then falls back to a cached fix. */
    suspend fun currentOrLastKnown(context: Context, timeoutMillis: Long = 15_000L): GeoPoint? = currentFix(context, timeoutMillis)?.let { GeoPoint(it.latitude,it.longitude) }

    suspend fun currentFix(context: Context, timeoutMillis: Long = 15_000L): android.location.Location? {
        if (!hasPermission(context) || !locationEnabled(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        if (providers.isEmpty()) return lastKnownFix(context)
        val fresh = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine<android.location.Location?> { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: android.location.Location) {
                        if (continuation.isActive) continuation.resume(location)
                        runCatching { manager.removeUpdates(this) }
                    }
                }
                continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
                var registered = false
                providers.forEach { provider ->
                    try {
                        manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                        registered = true
                    } catch (_: SecurityException) {
                        // Permission can be revoked after the initial check.
                    } catch (_: IllegalArgumentException) {
                        // A provider can become unavailable during the request.
                    }
                }
                if (!registered && continuation.isActive) continuation.resume(null)
            }
        }
        return fresh ?: lastKnownFix(context)
    }

    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun locationEnabled(context: Context): Boolean = runCatching {
        LocationManagerCompat.isLocationEnabled(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
    }.getOrDefault(false)

    fun lastKnown(context: Context): GeoPoint? = lastKnownFix(context)?.let { GeoPoint(it.latitude,it.longitude) }

    fun lastKnownFix(context: Context): android.location.Location? {
        if (!hasPermission(context)) return null
        return runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val enabled = lm.getProviders(true)
            val ordered = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            ).filter { enabled.contains(it) } + enabled
            ordered.distinct().mapNotNull { provider ->
                try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null } catch (_: IllegalArgumentException) { null }
            }.filter { System.currentTimeMillis()-it.time in 0..120_000 }
                .maxByOrNull { it.time }

        }.getOrNull()
    }
}
