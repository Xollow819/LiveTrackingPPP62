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
import org.osmdroid.util.MapTileIndex
import kotlin.math.*
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Overlay
import kotlinx.coroutines.delay

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
    viewportKey: String = "explore",
) {
    val context = LocalContext.current
    val tapHandler = rememberUpdatedState(onMapTap)
    val metadata = remember { SatelliteTiles.defaultMetadata }
    val satelliteSource = remember { SatelliteTiles.source(metadata) }
    var tileError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val currentStyle = rememberUpdatedState(mapStyle)
    val viewportPrefs = remember(context) { context.getSharedPreferences("map_viewports", Context.MODE_PRIVATE) }
    val savedViewport = remember(viewportKey) {
        if (viewportPrefs.contains("$viewportKey.lat")) GeoPoint(
            Double.fromBits(viewportPrefs.getLong("$viewportKey.lat", 0)),
            Double.fromBits(viewportPrefs.getLong("$viewportKey.lon", 0))) else null
    }
    val blueDot = remember(context) {
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(AndroidColor.parseColor("#0A84FF"))
            setStroke((3 * context.resources.displayMetrics.density).toInt(), AndroidColor.WHITE)
            setSize((20 * context.resources.displayMetrics.density).toInt(), (20 * context.resources.displayMetrics.density).toInt())
        }
    }
    val checkpointIcons = remember(checkpoints.map { it.id to it.orderIndex }, context) {
        checkpoints.associate { it.id to mapPin(context, AndroidColor.parseColor("#0B756F"), it.orderIndex.toString()) }
    }
    val liveIcon = remember(context) { mapPin(context, AndroidColor.parseColor("#0A84FF"), "") }
    val staleIcon = remember(context) { mapPin(context, AndroidColor.parseColor("#A66600"), "") }
    val map = remember(context, viewportKey) {
        MapView(context).apply {
            // Compose owns final cleanup; temporary View detaches must not destroy the tile provider.
            setDestroyMode(false)
            setTileSource(if (mapStyle == MapStyle.Satellite) satelliteSource else TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            val initial = savedViewport ?: myLocation ?: checkpoints.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
                ?: participants.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
            controller.setZoom(if (savedViewport != null) Double.fromBits(viewportPrefs.getLong("$viewportKey.zoom", 15.0.toBits())) else if (initial != null) 15.0 else 13.5)
            controller.setCenter(initial ?: GeoPoint(-6.1751, 106.8650))
            // Queue the visible center before the first raster scan queues the surrounding tiles.
            addOnFirstLayoutListener { _, _, _, _, _ -> requestCenterTile(this) }
        }
    }
    LaunchedEffect(map, mapStyle, retry) {
        tileError = null
        val source = if (mapStyle == MapStyle.Satellite) satelliteSource else TileSourceFactory.MAPNIK
        // Setting the same source clears the in-memory tile cache, so avoid doing it on entry.
        if (map.tileProvider.tileSource !== source) {
            map.setTileSource(source)
            if (map.width > 0 && map.height > 0) requestCenterTile(map)
        }
        if (retry > 0) { map.tileProvider.clearTileCache(); requestCenterTile(map); map.invalidate() }
    }
    DisposableEffect(map) {
        val tileHandler = Handler(Looper.getMainLooper()) { message ->
            if (currentStyle.value == MapStyle.Satellite && message.what == MapTileProviderBase.MAPTILE_FAIL_ID) {
                tileError = "Some satellite tiles could not load. Retry or switch to Standard."
            } else if (message.what == MapTileProviderBase.MAPTILE_SUCCESS_ID) {
                tileError = null
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
        // Keep the tap overlay separate from independently updated marker groups.
        map.overlays.add(0, events)
        map.onResume()
        onDispose {
            viewportPrefs.edit().putLong("$viewportKey.lat", map.mapCenter.latitude.toBits())
                .putLong("$viewportKey.lon", map.mapCenter.longitude.toBits())
                .putLong("$viewportKey.zoom", map.zoomLevelDouble.toBits()).apply()
            map.tileProvider.tileRequestCompleteHandlers.remove(tileHandler); tileHandler.removeCallbacksAndMessages(null); map.onPause(); map.onDetach() }
    }

    var centeredOnDevice by remember(map) { mutableStateOf(savedViewport != null || myLocation != null) }
    LaunchedEffect(myLocation) {
        if (!centeredOnDevice && myLocation != null) {
            centeredOnDevice = true
            map.controller.setZoom(15.0)
            map.controller.setCenter(myLocation)
        }
    }
    var routeFocused by remember(map) { mutableStateOf(savedViewport != null || myLocation != null || checkpoints.isNotEmpty()) }
    LaunchedEffect(map, checkpoints.firstOrNull()?.id) {
        if (!routeFocused && checkpoints.isNotEmpty() && viewportKey != "explore") {
            routeFocused = true
            val first = checkpoints.first()
            map.controller.setZoom(15.0)
            map.controller.setCenter(GeoPoint(first.latitude, first.longitude))
        }
    }
    val routeOverlays = remember(map) { mutableListOf<Overlay>() }
    LaunchedEffect(map, checkpoints, showRadius, checkpointIcons) {
        map.overlays.removeAll(routeOverlays.toSet())
        val markerOverlays = routeOverlays
        markerOverlays.clear()
        if (showRadius) {
            checkpoints.forEach { cp ->
                markerOverlays.add(Polygon().apply {
                    points = circlePoints(GeoPoint(cp.latitude, cp.longitude), cp.radiusMeters)
                    fillColor = 0x3310B981
                    strokeColor = 0xFF10B981.toInt()
                    strokeWidth = 2f
                })
            }
        }
        checkpoints.forEach { cp ->
            markerOverlays.add(Marker(map).apply {
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
        map.overlays.addAll(minOf(1, map.overlays.size), routeOverlays)
        map.invalidate()
    }
    var freshnessTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(map, participants.isNotEmpty()) {
        if (participants.isNotEmpty()) while (true) { delay(5000); freshnessTick = System.currentTimeMillis() }
    }
    val staleStates = participants.map { freshnessTick - it.recordedAt > 90_000 }
    val participantOverlays = remember(map) { mutableListOf<Overlay>() }
    LaunchedEffect(map, participants, staleStates) {
        map.overlays.removeAll(participantOverlays.toSet())
        val markerOverlays = participantOverlays
        markerOverlays.clear()
        participants.forEach { p ->
            val status = when {
                p.trackingState == TrackingState.PAUSED -> "PAUSED"
                p.trackingState == TrackingState.FINISHED -> "FINISHED"
                LocationUtils.isStale(p.recordedAt) -> "STALE"
                else -> "LIVE"
            }
            markerOverlays.add(Marker(map).apply {
                position = GeoPoint(p.latitude, p.longitude)
                title = p.participantName
                icon = if (status == "LIVE") liveIcon else staleIcon
                snippet = "${p.team} • $status • Battery ${p.batteryPercent}%"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            })
        }
        map.overlays.addAll(participantOverlays)
        map.invalidate()
    }
    val locationMarker = remember(map) { Marker(map).apply {
        icon = blueDot; title = "My location"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    } }
    LaunchedEffect(map, myLocation?.latitude, myLocation?.longitude) {
        if (myLocation == null) map.overlays.remove(locationMarker)
        else { locationMarker.position = myLocation; if (!map.overlays.contains(locationMarker)) map.overlays.add(locationMarker) }
        map.invalidate()
    }
    val lastCameraRequest = remember(map) { arrayOf<GeoPoint?>(if (savedViewport != null) target else null) }
    SideEffect {
        // Each button press is a request, even if its coordinates match the previous fix.
        if (target != null && target !== lastCameraRequest[0]) {
            lastCameraRequest[0] = target
            map.controller.animateTo(target)
            if (map.zoomLevelDouble < targetZoom) map.controller.setZoom(targetZoom)
        }
    }

    Box(modifier) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize().glassSource(LocalGlassState.current))
        MapLayersControl(mapStyle, { saveMapStyle(context, it) }, Modifier.align(Alignment.TopEnd).padding(top = layersTopPadding, end = 12.dp))
        if (tileError != null) {
            Surface(Modifier.align(Alignment.CenterStart).padding(12.dp).widthIn(max = 230.dp), shape = MaterialTheme.shapes.medium, shadowElevation = 3.dp) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tileError.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { retry++ }) { Text("Retry") }
                            TextButton(onClick = { saveMapStyle(context, MapStyle.Standard) }) { Text("Standard") }
                        }
                }
            }
        }
        val attribution = if (mapStyle == MapStyle.Satellite) metadata.attribution else "© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap contributors</a>"
        val attributionText = remember(attribution) { Html.fromHtml(attribution, Html.FROM_HTML_MODE_LEGACY) }
        Surface(Modifier.align(Alignment.BottomStart).padding(bottom = attributionBottomPadding).fillMaxWidth(.82f), color = MaterialTheme.colorScheme.surface.copy(alpha = .92f)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            AndroidView(factory = { TextView(it).apply { textSize = 10f; setPadding(8, 2, 8, 2); movementMethod = LinkMovementMethod.getInstance() } }, update = {
                if (it.text.toString() != attributionText.toString()) it.text = attributionText
                it.setTextColor(android.graphics.Color.argb(255, (textColor.red * 255).toInt(), (textColor.green * 255).toInt(), (textColor.blue * 255).toInt()))
                it.setLinkTextColor(it.currentTextColor)
            })
        }
    }

}

/** One tile in the requested viewport; no speculative or bulk prefetch. */
private fun requestCenterTile(map: MapView) {
    val zoom = floor(map.zoomLevelDouble).toInt().coerceIn(0, 21)
    val count = 1 shl zoom
    val point = map.mapCenter
    val x = floor((point.longitude + 180.0) / 360.0 * count).toInt().coerceIn(0, count - 1)
    val latitude = point.latitude.coerceIn(-85.05112878, 85.05112878)
    val y = floor((1.0 - asinh(tan(Math.toRadians(latitude))) / PI) / 2.0 * count).toInt().coerceIn(0, count - 1)
    map.tileProvider.getMapTile(MapTileIndex.getTileIndex(zoom, x, y))
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
