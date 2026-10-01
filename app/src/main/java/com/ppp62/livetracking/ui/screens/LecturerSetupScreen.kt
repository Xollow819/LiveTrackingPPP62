package com.ppp62.livetracking.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ppp62.livetracking.data.CheckpointEntity
import com.ppp62.livetracking.ui.AppViewModel
import com.ppp62.livetracking.ui.BackendViewModel
import com.ppp62.livetracking.ui.DraftCheckpoint
import com.ppp62.livetracking.ui.components.OsmMap
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
    var pin by rememberSaveable { mutableStateOf("") }
    var detailsError by remember { mutableStateOf<String?>(null) }
    val drafts = remember { mutableStateListOf<DraftCheckpoint>() }
    var code by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }

    val titles = listOf("Session details", "Checkpoint map", "Join code")
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("New field session", fontWeight = FontWeight.Bold); Text("Step ${step + 1} of 3 • ${titles[step]}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                navigationIcon = { IconButton(onClick = { if (step > 0 && code == null) step-- else onBack() }) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (step) {
                0 -> DetailsStep(title, { title = it }, pin, { pin = it }, detailsError, onContinue = {
                    detailsError = when {
                        title.isBlank() -> "Give the session a title"
                        pin.length < 4 -> "Lecturer PIN needs at least 4 characters"
                        else -> null
                    }
                    if (detailsError == null) step = 1
                })
                1 -> MapStep(drafts, onContinue = { step = 2 }, onBack = { step = 0 })
                2 -> CodeStep(title, pin, drafts.toList(), code, creating, createError,
                    onGenerate = {
                        creating = true; createError = null
                        bvm.createSessionWithCheckpoints(title.trim(), pin, drafts.toList()) { c, err ->
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
    pin: String, onPin: (String) -> Unit,
    error: String?,
    onContinue: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Name the session and set a private PIN.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(title, onTitle, label = { Text("Session title") }, placeholder = { Text("Morning distribution run") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) })
        OutlinedTextField(pin, onPin, label = { Text("Lecturer PIN") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.VpnKey, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), supportingText = { Text("Only you use this to open the monitoring view. Students never see it.") })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.weight(1f))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Continue to map"); Icon(Icons.Default.ArrowForward, null) }
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
    var myLoc by remember { mutableStateOf<GeoPoint?>(null) }
    var target by remember { mutableStateOf<GeoPoint?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var pendingPin by remember { mutableStateOf<GeoPoint?>(null) }
    var mapError by remember { mutableStateOf<String?>(null) }

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

    Box(Modifier.fillMaxSize()) {
        OsmMap(
            modifier = Modifier.fillMaxSize(),
            checkpoints = entities,
            myLocation = myLoc,
            target = target,
            onMapTap = { pendingPin = it }
        )

        // Search toggle + bar
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp)) {
            if (!searchOpen) {
                FilledTonalIconButton(onClick = { searchOpen = true }, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Default.Search, "Search places") }
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

        // Recenter on device location
        FloatingActionButton(
            onClick = { DeviceLocation.lastKnown(context)?.let { myLoc = it; target = it } },
            modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) { Icon(Icons.Default.MyLocation, "My location", tint = MaterialTheme.colorScheme.primary) }

        // Tap hint
        if (drafts.isEmpty() && pendingPin == null) {
            ElevatedCard(Modifier.align(Alignment.TopCenter).padding(top = 68.dp)) {
                Text("Tap anywhere on the map to pin a checkpoint", modifier = Modifier.padding(12.dp, 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Draft list + continue
        ElevatedCard(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
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

    pendingPin?.let { geo ->
        PinDialog(
            lat = geo.latitude, lng = geo.longitude,
            order = drafts.size + 1,
            onDismiss = { pendingPin = null },
            onSave = { name, radius, instructions ->
                drafts.add(DraftCheckpoint(name, geo.latitude, geo.longitude, radius, instructions))
                pendingPin = null
            }
        )
    }
}

@Composable
private fun PinDialog(lat: Double, lng: Double, order: Int, onDismiss: () -> Unit, onSave: (String, Double, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("75") }
    var instructions by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Checkpoint $order") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("%.5f, %.5f".format(lat, lng), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it }, label = { Text("Checkpoint name *") }, singleLine = true)
                OutlinedTextField(radius, { radius = it }, label = { Text("Arrival radius (m)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(instructions, { instructions = it }, label = { Text("Instructions for students") }, minLines = 2)
                if (attempted) Text("Name the checkpoint and use a radius of 20–500 m.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                attempted = true
                val r = radius.toDoubleOrNull()
                if (name.isNotBlank() && r != null && r in 20.0..500.0) onSave(name.trim(), r, instructions.trim())
            }) { Text("Pin checkpoint") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CodeStep(
    title: String,
    pin: String,
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
            Text("Everything is set.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
