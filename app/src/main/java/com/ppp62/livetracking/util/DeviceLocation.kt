package com.ppp62.livetracking.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.LocationListener
import android.os.Looper
import androidx.core.content.ContextCompat
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
    suspend fun currentOrLastKnown(context: Context, timeoutMillis: Long = 15_000L): GeoPoint? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        if (providers.isEmpty()) return lastKnown(context)
        val fresh = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: android.location.Location) {
                        if (continuation.isActive) continuation.resume(GeoPoint(location.latitude, location.longitude))
                        runCatching { manager.removeUpdates(this) }
                    }
                }
                continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
                providers.forEach { provider ->
                    runCatching { manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }
                }
            }
        }
        return fresh ?: lastKnown(context)
    }

    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun locationEnabled(context: Context): Boolean = runCatching {
        (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled
    }.getOrDefault(false)

    fun lastKnown(context: Context): GeoPoint? {
        if (!hasPermission(context)) return null
        return runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val enabled = lm.getProviders(true)
            val ordered = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            ).filter { enabled.contains(it) } + enabled
            val fix = ordered.distinct().firstNotNullOfOrNull { provider ->
                runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
            }
            fix?.let { GeoPoint(it.latitude, it.longitude) }
        }.getOrNull()
    }
}
