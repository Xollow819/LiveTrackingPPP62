package com.ppp62.livetracking.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ppp62.livetracking.data.*
import com.ppp62.livetracking.util.LocationUtils
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun OsmMap(modifier: Modifier = Modifier, checkpoints: List<CheckpointEntity>, participants: List<LocationEntity> = emptyList()) {
    val context = LocalContext.current
    val map = remember { MapView(context).apply { setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); controller.setZoom(13.5); controller.setCenter(GeoPoint(-6.927, 107.619)) } }
    DisposableEffect(map) { map.onResume(); onDispose { map.onPause(); map.onDetach() } }
    AndroidView(factory = { map }, modifier = modifier, update = {
        it.overlays.clear()
        checkpoints.forEach { cp -> it.overlays.add(Marker(it).apply { position = GeoPoint(cp.latitude, cp.longitude); title = "${cp.orderIndex}. ${cp.name}"; snippet = "Radius ${cp.radiusMeters.toInt()} m • ${cp.instructions}"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) }) }
        participants.forEach { p ->
            val status = when { p.trackingState == TrackingState.PAUSED -> "PAUSED"; p.trackingState == TrackingState.FINISHED -> "FINISHED"; LocationUtils.isStale(p.recordedAt) -> "STALE"; else -> "LIVE" }
            it.overlays.add(Marker(it).apply { position = GeoPoint(p.latitude, p.longitude); title = p.participantName; snippet = "${p.team} • $status • Battery ${p.batteryPercent}%"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) })
        }
        it.invalidate()
    })
}
