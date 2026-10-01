package com.ppp62.livetracking.ui.screens

import android.Manifest
import android.net.Uri
import android.provider.Settings
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    var trackingPermissionMessage by remember { mutableStateOf<String?>(null) }
    fun startSharing() {
        trackingPermissionMessage = null
        tracking = true
        val intent = Intent(context, LocationTrackingService::class.java)
            .putExtra(LocationTrackingService.EXTRA_NAME, profile.name)
            .putExtra(LocationTrackingService.EXTRA_TEAM, profile.team)
        ContextCompat.startForegroundService(context, intent)
    }
    val trackingLocationPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) startSharing()
        else { tracking = false; trackingPermissionMessage = "Allow location access to share your route. You can change this in app settings." }
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
            LazyColumn(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    OsmMap(
                        Modifier.fillMaxWidth().height(300.dp),
                        sessionCheckpoints,
                        listOfNotNull(own),
                        myLocation = ownPoint ?: devicePoint,
                        layersTopPadding = 12.dp
                    )
                    }
                }
                item {
                    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (tracking) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed, null, tint = if (tracking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                        Column(Modifier.weight(1f)) {
                            Text(if (own?.trackingState == TrackingState.PAUSED) "Location sharing paused" else if (tracking) "Location sharing active" else "Location sharing off", style = MaterialTheme.typography.titleMedium)
                            Text(if (own != null) "GPS accuracy ±${own.accuracyMeters.toInt()} m" else "Waiting for a GPS fix", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(shape = RoundedCornerShape(50), color = if (onlineSession != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(if (onlineSession != null) "ONLINE" else "OFFLINE", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!tracking) {
                            Button(
                                onClick = {
                                    if (DeviceLocation.hasPermission(context)) startSharing()
                                    else trackingLocationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                },
                                modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)
                            ) { Icon(Icons.Default.PlayArrow, null); Text(" Start sharing location") }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val action = if (own?.trackingState == TrackingState.PAUSED) LocationTrackingService.ACTION_RESUME else LocationTrackingService.ACTION_PAUSE
                                    context.startService(Intent(context, LocationTrackingService::class.java).setAction(action))
                                },
                                modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)
                            ) { Icon(if (own?.trackingState == TrackingState.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause, null); Text(if (own?.trackingState == TrackingState.PAUSED) " Resume" else " Pause") }
                            Button(
                                onClick = {
                                    tracking = false
                                    context.startService(Intent(context, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_FINISH))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)
                            ) { Icon(Icons.Default.StopCircle, null); Text(" Finish") }
                        }
                    }
                    trackingPermissionMessage?.let { message ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Settings") }
                        }
                    }
                    }
                }
                }
                item { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text("Route checkpoints", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); Text("${sessionCheckpoints.size} STOPS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                if (sessionCheckpoints.isEmpty()) {
                    item { Text("No checkpoints on this session yet.", modifier = Modifier.padding(16.dp, 0.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(sessionCheckpoints, key = { it.id }) { cp ->
                    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(18.dp)) {
                        ListItem(
                            headlineContent = { Text("${cp.orderIndex}. ${cp.name}", fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Arrival radius ${cp.radiusMeters.toInt()} m") },
                            leadingContent = { Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Default.LocationOn, null, Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) } },
                            trailingContent = { FilledTonalIconButton(onClick = { onCheckIn(cp.id) }) { Icon(Icons.Default.AddAPhoto, "Check in") } },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                    }
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
    Column(modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("Join your field session", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Enter the details shared by your lecturer to open the route and start recording your practical.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(code, onCode, label = { Text("Session code") }, placeholder = { Text("e.g. KX7Q2P") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Key, null) })
        OutlinedTextField(name, onName, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Person, null) })
        OutlinedTextField(team, onTeam, label = { Text("Team") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Group, null) })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.weight(1f))
        Button(onClick = onJoin, enabled = !joining, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
            if (joining) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else { Icon(Icons.Default.Login, null); Spacer(Modifier.width(8.dp)); Text("Join session") }
        }
    }
}
