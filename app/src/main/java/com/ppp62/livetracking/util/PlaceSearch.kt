package com.ppp62.livetracking.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PlaceResult(val name: String, val detail: String, val lat: Double, val lon: Double)

/**
 * Free OpenStreetMap place search (Nominatim). No API key, no account, no cost.
 * Only called on explicit user submit (never per-keystroke) to respect the
 * Nominatim usage policy.
 */
object PlaceSearch {
    private val lock = Mutex()
    private var lastRequest = 0L
    suspend fun search(query: String): List<PlaceResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        lock.withLock {
            delay((1000-(android.os.SystemClock.elapsedRealtime()-lastRequest)).coerceAtLeast(0))
            lastRequest=android.os.SystemClock.elapsedRealtime()
            val url = URL(
                "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&addressdetails=0&q=" +
                    URLEncoder.encode(query.trim(), "UTF-8")
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "PPPVenza/1.1 (Android field-tracking app)")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 12_000
                readTimeout = 12_000
            }
            val body = try {
                check(conn.responseCode == HttpURLConnection.HTTP_OK) { "Search service unavailable" }
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally { conn.disconnect() }
            val arr = JSONArray(body)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val display = o.optString("display_name", "")
                PlaceResult(
                    name = display.substringBefore(",").ifBlank { display },
                    detail = display,
                    lat = o.optDouble("lat", Double.NaN),
                    lon = o.optDouble("lon", Double.NaN)
                )
            }.filter { it.lat.isFinite() && it.lon.isFinite() }
        }
    }
}
