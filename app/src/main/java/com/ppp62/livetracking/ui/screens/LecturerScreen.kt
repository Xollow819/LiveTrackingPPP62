package com.ppp62.livetracking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ppp62.livetracking.data.*
import com.ppp62.livetracking.data.remote.LivePositionRow
import com.ppp62.livetracking.ui.AppViewModel
import com.ppp62.livetracking.ui.BackendViewModel
import com.ppp62.livetracking.ui.components.OsmMap
import com.ppp62.livetracking.ui.toEntity
import com.ppp62.livetracking.util.CsvExporter
import com.ppp62.livetracking.util.DeviceLocation
import com.ppp62.livetracking.util.LocationUtils
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun LivePositionRow.toEntity(sessionId: String): LocationEntity {
    val recordedAt = try {
        updatedAt?.let { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() } ?: System.currentTimeMillis()
    } catch (_: Exception) { System.currentTimeMillis() }
    return LocationEntity(
        participantId = userId, sessionId = sessionId, participantName = displayName.ifBlank { "Student" },
        team = "Field", latitude = lat, longitude = lng, accuracyMeters = (accuracy ?: 0.0).toFloat(),
        speedMps = 0f, heading = 0f, recordedAt = recordedAt, trackingState = TrackingState.LIVE, batteryPercent = 0
    )
}

/**
 * Lecturer entry: runs the setup wizard until a session exists, then shows
 * the live monitoring dashboard.
 */
@Composable
fun LecturerScreen(vm: AppViewModel, bvm: BackendViewModel, onBack: () -> Unit, onSubmissions: () -> Unit) {
    val onlineSession by bvm.onlineSession
    if (onlineSession == null) {
        LecturerSetupScreen(vm, bvm, onBack)
    } else {
        LecturerMonitorScreen(vm, bvm, onBack, onSubmissions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LecturerMonitorScreen(vm: AppViewModel, bvm: BackendViewModel, onBack: () -> Unit, onSubmissions: () -> Unit) {
    val context = LocalContext.current
    val onlineSession by bvm.onlineSession
    val onlinePositions by bvm.positions.collectAsState()
    val onlineCheckpoints by bvm.onlineCheckpoints.collectAsState()
    val alerts by bvm.alerts.collectAsState()
    val onlineSubmissions by bvm.onlineSubmissions.collectAsState()
    val session = onlineSession ?: return

    LaunchedEffect(session.id) { bvm.refreshOnlineSubmissions() }

    var myLoc by remember { mutableStateOf<GeoPoint?>(null) }
    LaunchedEffect(Unit) { myLoc = DeviceLocation.lastKnown(context) }

    val checkpoints = remember(onlineCheckpoints) { onlineCheckpoints.mapIndexed { i, r -> r.toEntity(i) } }
    val people = remember(onlinePositions) { onlinePositions.map { it.toEntity(session.id) } }
    val liveCount = people.count { !LocationUtils.isStale(it.recordedAt) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Monitoring", fontWeight = FontWeight.Bold); Text("Code ${session.code} • ${session.title}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { bvm.refreshOnlineSubmissions() }) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = { bvm.leaveSession() }) { Icon(Icons.Default.Logout, "Leave session") }
                }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item { OsmMap(Modifier.fillMaxWidth().height(320.dp), checkpoints, people, myLocation = myLoc) }
            item {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCard("$liveCount", "Live now", Modifier.weight(1f))
                    SummaryCard("${people.size}", "Students", Modifier.weight(1f))
                    SummaryCard("${onlineSubmissions.size}", "Check-ins", Modifier.weight(1f))
                }
            }
            item {
                FilledTonalButton(onClick = onSubmissions, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Icon(Icons.Default.AssignmentTurnedIn, null); Text(" Review submissions")
                }
            }
            if (alerts.isNotEmpty()) {
                item { Text("Arrivals & departures", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp)) }
                items(alerts.take(10), key = { it.id }) { alert ->
                    ListItem(
                        headlineContent = { Text(alert.text, style = MaterialTheme.typography.bodyMedium) },
                        supportingContent = { Text(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(alert.at)), style = MaterialTheme.typography.labelSmall) },
                        leadingContent = { Icon(if (alert.text.contains("arrived")) Icons.Default.Login else Icons.Default.Logout, null, tint = MaterialTheme.colorScheme.primary) }
                    )
                    HorizontalDivider()
                }
            }
            item { Text("Students on the map", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp)) }
            if (people.isEmpty()) {
                item { Text("No students sharing location yet — they appear here once they join with code ${session.code} and start tracking.", modifier = Modifier.padding(16.dp, 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(people, key = { it.participantId }) { p ->
                val stale = LocationUtils.isStale(p.recordedAt)
                ListItem(
                    headlineContent = { Text(p.participantName) },
                    supportingContent = { Text("±${p.accuracyMeters.toInt()} m accuracy") },
                    leadingContent = { Icon(Icons.Default.PersonPinCircle, null, tint = if (stale) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary) },
                    trailingContent = { Text(if (stale) "STALE" else "LIVE", style = MaterialTheme.typography.labelMedium, color = if (stale) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary) }
                )
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SummaryCard(value: String, label: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
