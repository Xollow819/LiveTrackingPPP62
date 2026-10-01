package com.ppp62.livetracking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ppp62.livetracking.data.*
import com.ppp62.livetracking.data.remote.LivePositionRow
import com.ppp62.livetracking.ui.AppViewModel
import com.ppp62.livetracking.ui.BackendViewModel
import com.ppp62.livetracking.ui.components.OsmMap
import com.ppp62.livetracking.util.CsvExporter
import com.ppp62.livetracking.util.LocationUtils
import java.time.OffsetDateTime

private fun LivePositionRow.toEntity(sessionId: String): LocationEntity {
    val recordedAt = try {
        updatedAt?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() } ?: System.currentTimeMillis()
    } catch (_: Exception) { System.currentTimeMillis() }
    return LocationEntity(
        participantId = userId, sessionId = sessionId, participantName = displayName.ifBlank { "Student" },
        team = "Online", latitude = lat, longitude = lng, accuracyMeters = (accuracy ?: 0.0).toFloat(),
        speedMps = 0f, heading = 0f, recordedAt = recordedAt, trackingState = TrackingState.LIVE, batteryPercent = 0
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LecturerScreen(vm: AppViewModel, bvm: BackendViewModel, onBack: () -> Unit, onEditor: () -> Unit, onSubmissions: () -> Unit) {
    val checkpoints by vm.checkpoints.collectAsState(); val people by vm.locations.collectAsState(); val submissions by vm.checkIns.collectAsState(); val context = LocalContext.current
    val backendEnabled by bvm.backendEnabled
    val onlineSession by bvm.onlineSession
    val onlinePositions by bvm.positions.collectAsState()
    var code by rememberSaveable { mutableStateOf("PPP6201") }
    var pin by rememberSaveable { mutableStateOf("") }
    var joinError by rememberSaveable { mutableStateOf<String?>(null) }

    // When an online session is active, the lecturer watches live student positions from Supabase.
    val mapPeople = if (onlineSession != null && onlinePositions.isNotEmpty()) onlinePositions.map { it.toEntity(onlineSession!!.id) } else people
    var teamFilter by rememberSaveable { mutableStateOf("All") }; val teams = listOf("All") + mapPeople.map { it.team }.distinct(); val visiblePeople = if (teamFilter == "All") mapPeople else mapPeople.filter { it.team == teamFilter }
    Scaffold(topBar = { TopAppBar(title = { Column { Text("Lecturer dashboard", fontWeight = FontWeight.Bold); Text(if (onlineSession != null) "Online • ${onlineSession!!.code}" else "PPP62-01 • Live monitoring", style = MaterialTheme.typography.labelSmall) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }, actions = { IconButton(onClick = { CsvExporter.share(context, submissions) }) { Icon(Icons.Default.FileDownload, "Export CSV") } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            if (backendEnabled && onlineSession == null) item {
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Online session", fontWeight = FontWeight.Bold)
                        Text("Create or join the shared session to watch students live on this map.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Code") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(pin, { pin = it }, label = { Text("Lecturer PIN") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Button(onClick = {
                            joinError = null
                            bvm.joinOnline(code, "Lecturer", "lecturer", pin) { err -> joinError = err }
                        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.CloudDone, null); Text(" Start / join online session") }
                        joinError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item { OsmMap(Modifier.fillMaxWidth().height(310.dp), checkpoints, visiblePeople) }
            item { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Summary("${visiblePeople.count { !LocationUtils.isStale(it.recordedAt) && it.trackingState == TrackingState.LIVE }}", "Live", Modifier.weight(1f)); Summary("${submissions.size}/${checkpoints.size}", "Check-ins", Modifier.weight(1f)); Summary("${submissions.count { it.syncState == SyncState.FLAGGED }}", "Flagged", Modifier.weight(1f)) } }
            item { Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilledTonalButton(onClick = onEditor, modifier = Modifier.weight(1f)) { Icon(Icons.Default.AddLocationAlt, null); Text(" Checkpoints") }; FilledTonalButton(onClick = onSubmissions, modifier = Modifier.weight(1f)) { Icon(Icons.Default.AssignmentTurnedIn, null); Text(" Submissions") } } }
            item { Text("Team filter", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 4.dp)); Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { teams.take(4).forEach { FilterChip(teamFilter == it, { teamFilter = it }, { Text(it) }) } } }
            item { Text("Participants", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
            items(visiblePeople, key = { it.participantId }) { participant ->
                val stale = LocationUtils.isStale(participant.recordedAt); val label = when { participant.trackingState != TrackingState.LIVE -> participant.trackingState.name; stale -> "STALE"; else -> "LIVE" }
                ListItem(headlineContent = { Text(participant.participantName) }, supportingContent = { Text("${participant.team} • ±${participant.accuracyMeters.toInt()} m • Battery ${participant.batteryPercent}%") }, leadingContent = { Icon(if (label == "LIVE") Icons.Default.RadioButtonChecked else Icons.Default.Warning, null, tint = if (label == "LIVE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary) }, trailingContent = { Text(label, style = MaterialTheme.typography.labelMedium) }); HorizontalDivider()
            }
        }
    }
}
@Composable private fun Summary(value: String, label: String, modifier: Modifier = Modifier) { ElevatedCard(modifier) { Column(Modifier.padding(12.dp)) { Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelMedium) } } }
