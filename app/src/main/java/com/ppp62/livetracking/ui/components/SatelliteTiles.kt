package com.ppp62.livetracking.ui.components

import android.net.Uri
import com.ppp62.livetracking.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex
import java.net.HttpURLConnection
import java.net.URL

data class SatelliteMetadata(val template: String, val minZoom: Int, val maxZoom: Int, val attribution: String)

object SatelliteTiles {
    private var cached: SatelliteMetadata? = null

    suspend fun metadata(): SatelliteMetadata = withContext(Dispatchers.IO) {
        cached ?: run {
            check(BuildConfig.MAPTILER_API_KEY.isNotBlank()) { "Satellite imagery is unavailable in this build." }
            val endpoint = "https://api.maptiler.com/tiles/satellite-v4/tiles.json?key=" + Uri.encode(BuildConfig.MAPTILER_API_KEY)
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                check(connection.responseCode == 200) { "Satellite imagery could not be loaded." }
                val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                SatelliteMetadata(
                    json.getJSONArray("tiles").getString(0),
                    json.optInt("minzoom", 0), json.optInt("maxzoom", 20),
                    json.optString("attribution", "© MapTiler")
                ).also { cached = it }
            } finally { connection.disconnect() }
        }
    }

    fun source(metadata: SatelliteMetadata) = object : XYTileSource(
        "MapTilerSatelliteV4", metadata.minZoom, metadata.maxZoom, 256, ".jpg", arrayOf("https://api.maptiler.com/"), metadata.attribution
    ) {
        override fun getTileURLString(mapTileIndex: Long): String {
            val template = metadata.template
                .replace("{z}", MapTileIndex.getZoom(mapTileIndex).toString())
                .replace("{x}", MapTileIndex.getX(mapTileIndex).toString())
                .replace("{y}", MapTileIndex.getY(mapTileIndex).toString())
            return if (template.contains("key=")) template else template + (if (template.contains("?")) "&" else "?") + "key=" + Uri.encode(BuildConfig.MAPTILER_API_KEY)
        }
    }
}
