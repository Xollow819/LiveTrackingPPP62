package com.ppp62.livetracking.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ppp62.livetracking.data.*
import com.ppp62.livetracking.util.LocationUtils
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/**
 * OpenStreetMap view (free, no API key) with:
 * - numbered checkpoint pins + translucent geofence radius circles,
 * - live participant pins with status in the snippet,
 * - an optional blue-dot "my location" marker,
 * - optional tap-to-pin ([onMapTap]) for the lecturer setup flow,
 * - programmatic [target] moves (search results, recenter button).
 */
@Composable
fun OsmMap(
    modifier: Modifier = Modifier,
    checkpoints: List<CheckpointEntity> = emptyList(),
    participants: List<LocationEntity> = emptyList(),
    myLocation: GeoPoint? = null,
    target: GeoPoint? = null,
    targetZoom: Double = 15.0,
    showRadius: Boolean = true,
    onMapTap: ((GeoPoint) -> Unit)? = null,
) {
    val context = LocalContext.current
    val tapHandler = rememberUpdatedState(onMapTap)
    val blueDot = remember {
        ShapeDrawable(OvalShape()).apply {
            paint.color = AndroidColor.parseColor("#2563EB")
            intrinsicWidth = 44
            intrinsicHeight = 44
        }
    }
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.5)
            // Neutral fallback; screens recenter on the device location when available.
            controller.setCenter(GeoPoint(-6.1751, 106.8650))
        }
    }
    DisposableEffect(map) {
        val events = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                tapHandler.value?.invoke(p)
                return tapHandler.value != null
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
        // Tap overlay stays at index 0; the update block rebuilds everything after it.
        map.overlays.add(0, events)
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }

    var centeredOnDevice by remember { mutableStateOf(false) }
    LaunchedEffect(myLocation) {
        if (!centeredOnDevice && myLocation != null) {
            centeredOnDevice = true
            map.controller.setZoom(15.0)
            map.controller.setCenter(myLocation)
        }
    }
    var lastTarget by remember { mutableStateOf<GeoPoint?>(null) }

    AndroidView(
        factory = { map },
        modifier = modifier,
        update = { mv ->
            val events = mv.overlays.filterIsInstance<MapEventsOverlay>()
            mv.overlays.clear()
            mv.overlays.addAll(events)

            if (showRadius) {
                checkpoints.forEach { cp ->
                    mv.overlays.add(Polygon().apply {
                        points = circlePoints(GeoPoint(cp.latitude, cp.longitude), cp.radiusMeters)
                        fillColor = 0x3310B981
                        strokeColor = 0xFF10B981.toInt()
                        strokeWidth = 2f
                    })
                }
            }
            checkpoints.forEach { cp ->
                mv.overlays.add(Marker(mv).apply {
                    position = GeoPoint(cp.latitude, cp.longitude)
                    title = "${cp.orderIndex}. ${cp.name}"
                    snippet = buildString {
                        append("Radius ${cp.radiusMeters.toInt()} m")
                        if (cp.instructions.isNotBlank()) append(" • ${cp.instructions}")
                    }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }
            participants.forEach { p ->
                val status = when {
                    p.trackingState == TrackingState.PAUSED -> "PAUSED"
                    p.trackingState == TrackingState.FINISHED -> "FINISHED"
                    LocationUtils.isStale(p.recordedAt) -> "STALE"
                    else -> "LIVE"
                }
                mv.overlays.add(Marker(mv).apply {
                    position = GeoPoint(p.latitude, p.longitude)
                    title = p.participantName
                    snippet = "${p.team} • $status • Battery ${p.batteryPercent}%"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }
            myLocation?.let { loc ->
                mv.overlays.add(Marker(mv).apply {
                    position = loc
                    icon = blueDot
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "My location"
                })
            }

            if (target != null && target != lastTarget) {
                lastTarget = target
                mv.controller.animateTo(target)
                if (mv.zoomLevelDouble < targetZoom) mv.controller.setZoom(targetZoom)
            }
            mv.invalidate()
        }
    )
}

/** Great-circle circle points (osmdroid-version independent). */
private fun circlePoints(center: GeoPoint, radiusMeters: Double, segments: Int = 48): List<GeoPoint> {
    val pts = ArrayList<GeoPoint>(segments + 1)
    val latRad = Math.toRadians(center.latitude)
    val lonRad = Math.toRadians(center.longitude)
    val angular = radiusMeters / 6_371_000.0
    for (i in 0..segments) {
        val bearing = 2.0 * Math.PI * i / segments
        val lat2 = Math.asin(
            Math.sin(latRad) * Math.cos(angular) + Math.cos(latRad) * Math.sin(angular) * Math.cos(bearing)
        )
        val lon2 = lonRad + Math.atan2(
            Math.sin(bearing) * Math.sin(angular) * Math.cos(latRad),
            Math.cos(angular) - Math.sin(latRad) * Math.sin(lat2)
        )
        pts.add(GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2)))
    }
    return pts
}
