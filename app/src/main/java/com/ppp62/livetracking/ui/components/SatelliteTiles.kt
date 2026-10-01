package com.ppp62.livetracking.ui.components

import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

data class SatelliteMetadata(val template: String, val minZoom: Int, val maxZoom: Int, val attribution: String)

object SatelliteTiles {
    suspend fun metadata() = SatelliteMetadata(
        "https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2025_3857/default/g/{z}/{y}/{x}.jpg", 0, 21,
        "<a href=\"https://cloudless.eox.at\">EOxCloudless</a> by EOX IT Services GmbH · Contains modified Copernicus Sentinel data 2025 · <a href=\"https://creativecommons.org/licenses/by-nc-sa/4.0/\">CC BY-NC-SA 4.0</a>"
    )
    fun source(metadata: SatelliteMetadata) = object : XYTileSource("EOXSentinel2025", metadata.minZoom, metadata.maxZoom, 256, ".jpg", arrayOf("https://tiles.maps.eox.at/"), metadata.attribution) {
        override fun getTileURLString(mapTileIndex: Long) = metadata.template
            .replace("{z}", MapTileIndex.getZoom(mapTileIndex).toString())
            .replace("{x}", MapTileIndex.getX(mapTileIndex).toString())
            .replace("{y}", MapTileIndex.getY(mapTileIndex).toString())
    }
}
