package com.ppp62.livetracking.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import org.osmdroid.util.GeoPoint

/**
 * Device's current location via the platform LocationManager (no extra SDK).
 * Returns the best last-known fix, or null when permission is missing or no
 * fix is available yet.
 */
object DeviceLocation {
    fun lastKnown(context: Context): GeoPoint? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
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
