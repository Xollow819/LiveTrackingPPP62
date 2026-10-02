package com.ppp62.livetracking.ui.screens

import com.ppp62.livetracking.ui.components.GlassCard
import kotlinx.serialization.encodeToString

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ppp62.livetracking.data.CheckpointEntity
import com.ppp62.livetracking.ui.AppViewModel
import com.ppp62.livetracking.ui.BackendViewModel
import com.ppp62.livetracking.ui.DraftCheckpoint
import com.ppp62.livetracking.ui.components.OsmMap
import com.ppp62.livetracking.ui.components.MapControlButton
import com.ppp62.livetracking.ui.components.CheckpointMapHint
import com.ppp62.livetracking.util.DeviceLocation
import com.ppp62.livetracking.util.PlaceResult
import com.ppp62.livetracking.util.PlaceSearch
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

private fun DraftCheckpoint.toEntity(order: Int) = CheckpointEntity(
    id = "draft-$order", sessionId = "", name = name, latitude = lat, longitude = lng,
    radiusMeters = radius, orderIndex = order + 1, instructions = instructions
)

/**
 * Lecturer session setup: 1) session details, 2) pin checkpoints on the map,
 * 3) generate the join code students use. No example content — everything
 * starts blank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LecturerSetupScreen(vm: AppViewModel, bvm: BackendViewModel, onBack: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(0) }
    var title by rememberSaveable { mutableStateOf("") }
    var detailsError by remember { mutableStateOf<String?>(null) }
    val drafts = rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<DraftCheckpoint>,String>(
        save = { list -> list.map { kotlinx.serialization.json.Json.encodeToString(it) } },
        restore = { list -> list.map { kotlinx.serialization.json.Json.decodeFromString<DraftCheckpoint>(it) }.toMutableStateList() }
    )) { mutableStateListOf<DraftCheckpoint>() }
    var code by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }

    val titles = listOf("Session details", "Checkpoint map", "Join code")
    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 52.dp).padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (step > 0 && code == null) step-- else onBack() }, modifier = Modifier.padding(start = 4.dp)) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("New field session", fontWeight = FontWeight.Bold)
                        Text("Step ${step + 1} of 3 • ${titles[step]}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (step) {
                0 -> DetailsStep(title, { title = it }, detailsError, onContinue = {
                    detailsError = when {
                        title.isBlank() -> "Give the session a title"
                        else -> null
                    }
                    if (detailsError == null) step = 1
                })
                1 -> MapStep(drafts, onContinue = { step = 2 }, onBack = { step = 0 })
                2 -> CodeStep(title, drafts.toList(), code, creating, createError,
                    onGenerate = {
                        creating = true; createError = null
                        bvm.createSessionWithCheckpoints(title.trim(), drafts.toList()) { c, err ->
                            creating = false
                            if (c != null) code = c else createError = err
                        }
                    },
                    onStartMonitoring = { bvm.startMonitoring() })
            }
        }
    }
}

@Composable
private fun DetailsStep(
    title: String, onTitle: (String) -> Unit,
    error: String?,
    onContinue: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        LinearProgressIndicator(progress = { .34f }, Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.surfaceVariant)
        Text("Start with the basics", style = MaterialTheme.typography.headlineMedium)
        Text("Name the practical. Your lecturer account protects the route and records.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GlassCard(shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(title, onTitle, label = { Text("Session title") }, placeholder = { Text("Morning distribution run") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.weight(1f))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Text("Continue to map"); Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ArrowForward, null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapStep(
    drafts: MutableList<DraftCheckpoint>,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var myLoc by remember { mutableStateOf<GeoPoint?>(null, referentialEqualityPolicy()) }
    var target by remember { mutableStateOf<GeoPoint?>(null, referentialEqualityPolicy()) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var pendingPin by remember { mutableStateOf<GeoPoint?>(null, referentialEqualityPolicy()) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val allowed = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        permissionDenied = !allowed
        if (allowed) scope.launch {
            locating = true
            locationMessage = null
            val fix = DeviceLocation.currentOrLastKnown(context)
            locating = false
            if (fix != null) { myLoc = fix; target = fix } else locationMessage = if (!DeviceLocation.locationEnabled(context)) "Turn on device location, then try again." else "No location fix yet. Move outdoors and retry."
        }
    }

    fun locate() {
        if (locating) return
        if (!DeviceLocation.hasPermission(context)) {
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        if (!DeviceLocation.locationEnabled(context)) {
            locationMessage = "Turn on device location, then try again."
            return
        }
        scope.launch {
            locating = true
            locationMessage = null
            val fix = DeviceLocation.currentOrLastKnown(context)
            locating = false
            if (fix != null) { myLoc = fix; target = fix } else locationMessage = "No location fix yet. Move outdoors and retry."
        }
    }

    LaunchedEffect(Unit) { myLoc = DeviceLocation.lastKnown(context) }

    fun doSearch() {
        if (query.isBlank()) return
        searching = true; searchError = null
        scope.launch {
            val found = PlaceSearch.search(query)
            searching = false
            results = found
            if (found.isEmpty()) searchError = "No places found — try another search"
        }
    }

    val entities = drafts.mapIndexed { i, d -> d.toEntity(i) }
    val density = LocalDensity.current
    var searchPanelHeight by remember { mutableIntStateOf(0) }
    var checkpointPanelHeight by remember { mutableIntStateOf(0) }
    val gpsTop = if (searchOpen) with(density) { searchPanelHeight.toDp() } + 8.dp else 72.dp

    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val showCheckpointPanel = !searchOpen || maxHeight >= 440.dp
        val panelSpace = if (showCheckpointPanel) with(density) { checkpointPanelHeight.toDp() } else 0.dp
        val maxResultsHeight = (maxHeight - panelSpace - 228.dp).coerceAtLeast(48.dp)
        OsmMap(
            modifier = Modifier.fillMaxSize(),
            checkpoints = entities,
            myLocation = myLoc,
            target = target,
            onMapTap = { pendingPin = it },
            layersTopPadding = gpsTop + 60.dp,
            attributionBottomPadding = panelSpace
        )

        // Search toggle + bar
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { searchPanelHeight = it.height }.padding(12.dp)) {
            if (!searchOpen) {
                MapControlButton(Icons.Default.Search, "Search places", { searchOpen = true }, Modifier.align(Alignment.End))
            } else {
                ElevatedCard {
                    Column(Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                query, { query = it }, label = { Text("Search a place") }, singleLine = true,
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { doSearch() })
                            )
                            IconButton(onClick = { doSearch() }, enabled = !searching) { Icon(Icons.Default.Search, null) }
                            IconButton(onClick = { searchOpen = false; results = emptyList(); query = "" }) { Icon(Icons.Default.Close, null) }
                        }
                        if (searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                        searchError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(4.dp)) }
                        Column(Modifier.heightIn(max = maxResultsHeight).verticalScroll(rememberScrollState())) {
                            results.forEach { r ->
                                ListItem(
                                    headlineContent = { Text(r.name, maxLines = 1) },
                                    supportingContent = { Text(r.detail, maxLines = 1, style = MaterialTheme.typography.bodySmall) },
                                    leadingContent = { Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary) },
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        target = GeoPoint(r.lat, r.lon)
                                        results = emptyList(); searchOpen = false; query = ""
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        MapControlButton(
            Icons.Default.MyLocation, "Find my location", { locate() },
            Modifier.align(Alignment.TopEnd).padding(top = gpsTop, end = 12.dp), busy = locating
        )

        if (locationMessage != null || permissionDenied) {
            GlassCard(Modifier.align(Alignment.CenterEnd).padding(end = 64.dp, top = 12.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(locationMessage ?: "Location permission is needed to find your position.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        if (permissionDenied) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        else if (!DeviceLocation.locationEnabled(context)) context.startActivity(Intent(ACTION_LOCATION_SOURCE_SETTINGS))
                        else locate()
                    }) { Text(if (permissionDenied) "Settings" else if (!DeviceLocation.locationEnabled(context)) "Turn on" else "Retry") }
                }
            }
        }

        CheckpointMapHint(
            Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 12.dp, end = 72.dp),
            visible = !searchOpen && pendingPin == null
        )

        // Keep search controls accessible when the keyboard leaves little map space.
        if (showCheckpointPanel) {
            GlassCard(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { checkpointPanelHeight = it.height }.padding(12.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (drafts.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(drafts) { i, d ->
                                InputChip(
                                    selected = false,
                                    onClick = { target = GeoPoint(d.lat, d.lng) },
                                    label = { Text("${i + 1}. ${d.name}") },
                                    trailingIcon = { IconButton(onClick = { drafts.removeAt(i) }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)) } }
                                )
                            }
                        }
                    } else {
                        Text("No checkpoints yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    mapError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                        Button(
                            onClick = {
                                if (drafts.isEmpty()) mapError = "Pin at least one checkpoint on the map"
                                else onContinue()
                            },
                            modifier = Modifier.weight(2f)
                        ) { Text("Continue"); Icon(Icons.Default.ArrowForward, null) }
                    }
                }
            }
        }
    }

    pendingPin?.let { geo ->
        PinDialog(
            lat = geo.latitude, lng = geo.longitude,
            order = drafts.size + 1,
            onDismiss = { pendingPin = null },
            onSave = { name, radius, instructions, photo, temperature, weight ->
                drafts.add(DraftCheckpoint(name, geo.latitude, geo.longitude, radius, instructions, photo, temperature, weight))
                pendingPin = null
            }
        )
    }
}

@Composable
private fun PinDialog(lat: Double, lng: Double, order: Int, onDismiss: () -> Unit, onSave: (String, Double, String, Boolean, Boolean, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("75") }
    var instructions by remember { mutableStateOf("") }
    var photo by remember {mutableStateOf(true)}; var temperature by remember {mutableStateOf(true)}; var weight by remember {mutableStateOf(true)}
    var attempted by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Checkpoint $order") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("%.5f, %.5f".format(lat, lng), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it }, label = { Text("Checkpoint name *") }, singleLine = true)
                OutlinedTextField(radius, { radius = it }, label = { Text("Arrival radius (m)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(instructions, { instructions = it }, label = { Text("Instructions for students") }, minLines = 2)
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(photo,{photo=it});Text("Require photo")}
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(temperature,{temperature=it});Text("Require temperature")}
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(weight,{weight=it});Text("Require weight")}
                if (attempted) Text("Name the checkpoint and use a radius of 20–500 m.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                attempted = true
                val r = radius.toDoubleOrNull()
                if (name.isNotBlank() && r != null && r in 20.0..500.0) onSave(name.trim(), r, instructions.trim(), photo, temperature, weight)
            }) { Text("Pin checkpoint") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CodeStep(
    title: String,
    drafts: List<DraftCheckpoint>,
    code: String?,
    creating: Boolean,
    error: String?,
    onGenerate: () -> Unit,
    onStartMonitoring: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (code == null) {
            LinearProgressIndicator(progress = { 1f }, Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.surfaceVariant)
            Text("Ready to invite your group", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("“$title” with ${drafts.size} checkpoint${if (drafts.size == 1) "" else "s"}. Generating the join code creates the online session and uploads the checkpoints — students join with the code and see your map.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
            Button(onClick = onGenerate, enabled = !creating, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                if (creating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Key, null)
                Spacer(Modifier.width(8.dp)); Text(if (creating) "Creating session…" else "Generate join code")
            }
        } else {
            Spacer(Modifier.weight(1f))
            Text("Share this code with your students", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(code, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, letterSpacing = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                FilledTonalButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Icon(Icons.Default.ContentCopy, null); Text(" Copy") }
                FilledTonalButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Join my PPPVenza field session \"$title\" with code: $code")
                    }
                    context.startActivity(Intent.createChooser(intent, "Share join code"))
                }) { Icon(Icons.Default.Share, null); Text(" Share") }
            }
            Spacer(Modifier.weight(1f))
            Text("Session is live — students joining now appear on your monitoring map.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onStartMonitoring, modifier = Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Default.Dashboard, null); Spacer(Modifier.width(8.dp)); Text("Start monitoring") }
        }
    }
}
