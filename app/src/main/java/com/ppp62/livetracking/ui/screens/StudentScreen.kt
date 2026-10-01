package com.ppp62.livetracking.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ppp62.livetracking.data.TrackingState
import com.ppp62.livetracking.service.LocationTrackingService
import com.ppp62.livetracking.ui.AppViewModel
import com.ppp62.livetracking.ui.BackendViewModel
import com.ppp62.livetracking.ui.components.OsmMap
import com.ppp62.livetracking.ui.toEntity
import com.ppp62.livetracking.util.DeviceLocation
import com.ppp62.livetracking.util.LocationUtils
import com.ppp62.livetracking.util.Notifier
import org.osmdroid.util.GeoPoint

/**
 * Student field view: join with the lecturer's code, then see the lecturer's
 * map (their checkpoints, your live position) and share real-time movement.
 * Arrival at a checkpoint raises a local alert, Find-My style.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentScreen(vm: AppViewModel, bvm: BackendViewModel, onBack: () -> Unit, onCheckIn: (String) -> Unit) {
    val locations by vm.locations.collectAsState()
    val profile by vm.profile
    val onlineSession by bvm.onlineSession
    val onlineCheckpoints by bvm.onlineCheckpoints.collectAsState()
    val myRole by bvm.myRole
    val sessionCheckpoints by vm.sessionCheckpoints
    val context = LocalContext.current

    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var team by rememberSaveable { mutableStateOf("") }
    var joinError by rememberSaveable { mutableStateOf<String?>(null) }
    var joining by remember { mutableStateOf(false) }

    // The map the lecturer configured: keep the in-memory entities in sync.
    LaunchedEffect(onlineCheckpoints) {
        if (onlineCheckpoints.isNotEmpty()) {
            vm.setSessionCheckpoints(onlineCheckpoints.mapIndexed { i, r -> r.toEntity(i) })
        }
    }
    // Restore a previously joined student session after app restart.
    LaunchedEffect(onlineSession, myRole) {
        val session = onlineSession
        if (session != null && myRole == "student" && !profile.joined) {
            val (savedName, savedTeam) = bvm.savedIdentity()
            if (savedName.isNotBlank()) {
                vm.joinField(savedName, savedTeam.ifBlank { "Field" })
                name = savedName; team = savedTeam
            }
        }
    }

    val own = locations.firstOrNull { it.participantId == "this-device" }
    val ownPoint = own?.let { GeoPoint(it.latitude, it.longitude) }
    var devicePoint by remember { mutableStateOf<GeoPoint?>(null) }
    LaunchedEffect(Unit) { devicePoint = DeviceLocation.lastKnown(context) }

    // Student-side arrival alerts (Find-My style geofence).
    val notified = remember(onlineSession?.code) { mutableStateSetOf<String>() }
    var arrivalBanner by remember { mutableStateOf<com.ppp62.livetracking.data.CheckpointEntity?>(null) }
    LaunchedEffect(own, sessionCheckpoints) {
        val o = own ?: return@LaunchedEffect
        sessionCheckpoints.forEach { cp ->
            if (cp.id !in notified &&
                LocationUtils.distanceMeters(o.latitude, o.longitude, cp.latitude, cp.longitude) <= cp.radiusMeters
            ) {
                notified.add(cp.id)
                arrivalBanner = cp
                Notifier.post(context, "PPPVenza", "You arrived at ${cp.name}")
            }
        }
    }

    var tracking by rememberSaveable(own?.trackingState) {
        mutableStateOf(own?.trackingState == TrackingState.LIVE || own?.trackingState == TrackingState.PAUSED)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Field session", fontWeight = FontWeight.Bold)
                        Text(
                            if (profile.joined) "${profile.name} • ${profile.team}" else "Join with your lecturer's code",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { pad ->
        if (!profile.joined) {
            JoinForm(
                code, { code = it.uppercase() }, name, { name = it }, team, { team = it },
                joinError, joining,
                onJoin = {
                    joinError = when {
                        code.isBlank() -> "Enter the join code from your lecturer"
                        name.isBlank() -> "Enter your name"
                        team.isBlank() -> "Enter your team"
                        else -> null
                    }
                    if (joinError != null) return@JoinForm
                    joining = true
                    bvm.joinOnline(
                        code.trim(), name.trim(), "student", team = team.trim(),
                        onError = { err -> joining = false; joinError = err },
                        onJoined = { joining = false; vm.joinField(name.trim(), team.trim()) }
                    )
                },
                modifier = Modifier.fillMaxSize().padding(pad)
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                arrivalBanner?.let { cp ->
                    item {
                        ElevatedCard(Modifier.fillMaxWidth().padding(12.dp, 12.dp, 12.dp, 0.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Column(Modifier.weight(1f)) {
                                    Text("You arrived at ${cp.name}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text("Ready to check in", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                TextButton(onClick = { arrivalBanner = null }) { Text("Dismiss") }
                                FilledTonalButton(onClick = { arrivalBanner = null; onCheckIn(cp.id) }) { Text("Check in") }
                            }
                        }
                    }
                }
                item {
                    OsmMap(
                        Modifier.fillMaxWidth().height(320.dp),
                        sessionCheckpoints,
                        listOfNotNull(own),
                        myLocation = ownPoint ?: devicePoint
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!tracking) {
                            Button(
                                onClick = {
                                    tracking = true
                                    val intent = Intent(context, LocationTrackingService::class.java)
                                        .putExtra(LocationTrackingService.EXTRA_NAME, profile.name)
                                        .putExtra(LocationTrackingService.EXTRA_TEAM, profile.team)
                                    ContextCompat.startForegroundService(context, intent)
                                },
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) { Icon(Icons.Default.PlayArrow, null); Text(" Start sharing location") }
                        } else {
                            OutlinedButton(
                                onClick = { context.startService(Intent(context, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_PAUSE)) },
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) { Icon(Icons.Default.Pause, null); Text(" Pause") }
                            Button(
                                onClick = {
                                    tracking = false
                                    context.startService(Intent(context, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_FINISH))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) { Icon(Icons.Default.StopCircle, null); Text(" Finish") }
                        }
                    }
                }
                item {
                    AssistChip(
                        onClick = {},
                        label = { Text(if (onlineSession != null) "Online ✓ ${onlineSession!!.code}" else "Offline — waiting for connection") },
                        leadingIcon = { Icon(if (onlineSession != null) Icons.Default.CloudDone else Icons.Default.CloudOff, null) },
                        modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp)
                    )
                }
                item {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (own == null) "Waiting for GPS…" else "GPS ±${own.accuracyMeters.toInt()} m • ${own.trackingState.name.lowercase().replaceFirstChar { it.uppercase() }}"
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.GpsFixed, null) },
                        modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp)
                    )
                }
                item { Text("Checkpoints", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp)) }
                if (sessionCheckpoints.isEmpty()) {
                    item { Text("No checkpoints on this session yet.", modifier = Modifier.padding(16.dp, 0.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(sessionCheckpoints, key = { it.id }) { cp ->
                    ListItem(
                        headlineContent = { Text("${cp.orderIndex}. ${cp.name}") },
                        supportingContent = { Text("Radius ${cp.radiusMeters.toInt()} m") },
                        leadingContent = { Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { FilledTonalIconButton(onClick = { onCheckIn(cp.id) }) { Icon(Icons.Default.AddAPhoto, "Check in") } }
                    )
                    HorizontalDivider()
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun JoinForm(
    code: String, onCode: (String) -> Unit,
    name: String, onName: (String) -> Unit,
    team: String, onTeam: (String) -> Unit,
    error: String?, joining: Boolean,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Join a session", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Ask your lecturer for the 6-character join code.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(code, onCode, label = { Text("Join code") }, placeholder = { Text("e.g. KX7Q2P") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Key, null) })
        OutlinedTextField(name, onName, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Person, null) })
        OutlinedTextField(team, onTeam, label = { Text("Team") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Group, null) })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.weight(1f))
        Button(onClick = onJoin, enabled = !joining, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            if (joining) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else { Icon(Icons.Default.Login, null); Spacer(Modifier.width(8.dp)); Text("Join session") }
        }
    }
}
