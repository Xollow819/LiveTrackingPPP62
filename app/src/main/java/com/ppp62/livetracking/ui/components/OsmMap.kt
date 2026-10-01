package com.ppp62.livetracking.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.osmdroid.tileprovider.MapTileProviderBase
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
 * Street/satellite map with persistent layers selection and provider attribution:
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
    mapStyle: MapStyle = rememberMapStyle(),
    layersTopPadding: Dp = 72.dp,
    attributionBottomPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val tapHandler = rememberUpdatedState(onMapTap)
    var metadata by remember { mutableStateOf<SatelliteMetadata?>(null) }
    var loading by remember { mutableStateOf(false) }
    var tileError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val currentStyle = rememberUpdatedState(mapStyle)
    LaunchedEffect(mapStyle, retry) {
        tileError = null
        if (mapStyle == MapStyle.Satellite) {
            loading = true
            try { metadata = SatelliteTiles.metadata() }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { tileError = "Satellite imagery could not be loaded. Check your connection and retry." }
            finally { loading = false }
        }
    }
    val blueDot = remember(context) {
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(AndroidColor.parseColor("#2563EB"))
            setStroke((3 * context.resources.displayMetrics.density).toInt(), AndroidColor.WHITE)
            setSize((20 * context.resources.displayMetrics.density).toInt(), (20 * context.resources.displayMetrics.density).toInt())
        }
    }
    val checkpointIcons = remember(checkpoints.map { it.id to it.orderIndex }, context) {
        checkpoints.associate { it.id to mapPin(context, AndroidColor.parseColor("#0B6E5F"), it.orderIndex.toString()) }
    }
    val liveIcon = remember(context) { mapPin(context, AndroidColor.parseColor("#2563EB"), "") }
    val staleIcon = remember(context) { mapPin(context, AndroidColor.parseColor("#A66600"), "") }
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.5)
            // Neutral fallback; screens recenter on the device location when available.
            controller.setCenter(GeoPoint(-6.1751, 106.8650))
        }
    }
    LaunchedEffect(mapStyle, metadata, retry) {
        if (mapStyle == MapStyle.Standard || metadata != null) {
            val center = GeoPoint(map.mapCenter.latitude, map.mapCenter.longitude)
            val zoom = map.zoomLevelDouble
            map.setTileSource(if (mapStyle == MapStyle.Satellite) SatelliteTiles.source(metadata!!) else TileSourceFactory.MAPNIK)
            map.controller.setZoom(zoom.coerceIn(map.minZoomLevel, map.maxZoomLevel))
            map.controller.setCenter(center)
            map.invalidate()
        }
    }
    DisposableEffect(map) {
        val tileHandler = Handler(Looper.getMainLooper()) { message ->
            if (currentStyle.value == MapStyle.Satellite && message.what == MapTileProviderBase.MAPTILE_FAIL_ID) {
                tileError = "Some satellite tiles could not load. Retry or switch to Standard."
            }
            false
        }
        map.tileProvider.tileRequestCompleteHandlers.add(tileHandler)
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
        onDispose { map.tileProvider.tileRequestCompleteHandlers.remove(tileHandler); tileHandler.removeCallbacksAndMessages(null); map.onPause(); map.onDetach() }
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

    Box(modifier) {
        AndroidView(
            factory = { map },
            modifier = Modifier.fillMaxSize(),
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
                        icon = checkpointIcons[cp.id]
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
                        icon = if (status == "LIVE") liveIcon else staleIcon
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
        MapLayersControl(mapStyle, { saveMapStyle(context, it) }, Modifier.align(Alignment.TopEnd).padding(top = layersTopPadding, end = 12.dp))
        if (loading || tileError != null) {
            Surface(Modifier.align(Alignment.CenterStart).padding(12.dp).widthIn(max = 230.dp), shape = MaterialTheme.shapes.medium, shadowElevation = 3.dp) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading satellite imagery…", style = MaterialTheme.typography.bodySmall) }
                    else {
                        Text(tileError.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { retry++; map.tileProvider.clearTileCache() }) { Text("Retry") }
                            TextButton(onClick = { saveMapStyle(context, MapStyle.Standard) }) { Text("Standard") }
                        }
                    }
                }
            }
        }
        val attribution = if (mapStyle == MapStyle.Satellite && metadata != null) metadata!!.attribution else "© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap contributors</a>"
        Surface(Modifier.align(Alignment.BottomStart).padding(bottom = attributionBottomPadding).fillMaxWidth(.82f), color = MaterialTheme.colorScheme.surface.copy(alpha = .92f)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            AndroidView(factory = { TextView(it).apply { textSize = 10f; setPadding(8, 2, 8, 2); movementMethod = LinkMovementMethod.getInstance() } }, update = {
                it.text = Html.fromHtml(attribution, Html.FROM_HTML_MODE_LEGACY)
                it.setTextColor(android.graphics.Color.argb(255, (textColor.red * 255).toInt(), (textColor.green * 255).toInt(), (textColor.blue * 255).toInt()))
                it.setLinkTextColor(it.currentTextColor)
            })
        }
    }

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

/** White-rimmed numbered pins remain readable over both streets and imagery. */
private fun mapPin(context: Context, color: Int, label: String): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val bitmap = Bitmap.createBitmap((40 * density).toInt(), (48 * density).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(density, density)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val outline = Path().apply {
        moveTo(20f, 45f)
        cubicTo(16f, 37f, 4f, 28f, 4f, 19f)
        cubicTo(4f, 10f, 11f, 3f, 20f, 3f)
        cubicTo(29f, 3f, 36f, 10f, 36f, 19f)
        cubicTo(36f, 28f, 24f, 37f, 20f, 45f)
        close()
    }
    paint.color = color
    canvas.drawPath(outline, paint)
    paint.color = AndroidColor.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f
    canvas.drawPath(outline, paint)
    paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER; paint.textSize = 13f; paint.isFakeBoldText = true
    if (label.isBlank()) canvas.drawCircle(20f, 19f, 5f, paint)
    else canvas.drawText(label, 20f, 19f - (paint.ascent() + paint.descent()) / 2f, paint)
    return BitmapDrawable(context.resources, bitmap)
}
