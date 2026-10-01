package com.ppp62.livetracking.util

import kotlin.math.*

object LocationUtils {
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return radius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
    fun isStale(recordedAt: Long, now: Long = System.currentTimeMillis()) = now - recordedAt > 90_000L
}
