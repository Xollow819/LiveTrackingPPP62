package com.ppp62.livetracking.util

import kotlinx.coroutines.Dispatchers
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
    suspend fun search(query: String): List<PlaceResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
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
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching emptyList<PlaceResult>()
            val body = conn.inputStream.bufferedReader().readText()
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
        }.getOrDefault(emptyList())
    }
}
