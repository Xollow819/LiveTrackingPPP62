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
import com.ppp62.livetracking.ui.AppViewModel
import com.ppp62.livetracking.ui.components.OsmMap
import com.ppp62.livetracking.util.CsvExporter
import com.ppp62.livetracking.util.LocationUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LecturerScreen(vm: AppViewModel, onBack: () -> Unit, onEditor: () -> Unit, onSubmissions: () -> Unit) {
    val checkpoints by vm.checkpoints.collectAsState(); val people by vm.locations.collectAsState(); val submissions by vm.checkIns.collectAsState(); val context = LocalContext.current
    var teamFilter by rememberSaveable { mutableStateOf("All") }; val teams = listOf("All") + people.map { it.team }.distinct(); val visiblePeople = if (teamFilter == "All") people else people.filter { it.team == teamFilter }
    Scaffold(topBar = { TopAppBar(title = { Column { Text("Lecturer dashboard", fontWeight = FontWeight.Bold); Text("PPP62-01 • Live monitoring", style = MaterialTheme.typography.labelSmall) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }, actions = { IconButton(onClick = { CsvExporter.share(context, submissions) }) { Icon(Icons.Default.FileDownload, "Export CSV") } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item { OsmMap(Modifier.fillMaxWidth().height(310.dp), checkpoints, visiblePeople) }
            item { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Summary("${people.count { !LocationUtils.isStale(it.recordedAt) && it.trackingState == TrackingState.LIVE }}", "Live", Modifier.weight(1f)); Summary("${submissions.size}/${checkpoints.size}", "Check-ins", Modifier.weight(1f)); Summary("${submissions.count { it.syncState == SyncState.FLAGGED }}", "Flagged", Modifier.weight(1f)) } }
            item { Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilledTonalButton(onClick = onEditor, modifier = Modifier.weight(1f)) { Icon(Icons.Default.AddLocationAlt, null); Text(" Checkpoints") }; FilledTonalButton(onClick = onSubmissions, modifier = Modifier.weight(1f)) { Icon(Icons.Default.AssignmentTurnedIn, null); Text(" Submissions") } } }
            item { Text("Team filter", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 4.dp)); Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { teams.take(4).forEach { FilterChip(teamFilter == it, { teamFilter = it }, { Text(it) }) } } }
            item { Text("Participants", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
            items(visiblePeople, key = { it.participantId }) { p ->
                val stale = LocationUtils.isStale(p.recordedAt); val label = when { p.trackingState != TrackingState.LIVE -> p.trackingState.name; stale -> "STALE"; else -> "LIVE" }
                ListItem(headlineContent = { Text(p.participantName) }, supportingContent = { Text("${p.team} • ±${p.accuracyMeters.toInt()} m • Battery ${p.batteryPercent}%") }, leadingContent = { Icon(if (label == "LIVE") Icons.Default.RadioButtonChecked else Icons.Default.Warning, null, tint = if (label == "LIVE") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary) }, trailingContent = { Text(label, style = MaterialTheme.typography.labelMedium) }); HorizontalDivider()
            }
        }
    }
}
@Composable private fun Summary(value: String, label: String, modifier: Modifier = Modifier) { ElevatedCard(modifier) { Column(Modifier.padding(12.dp)) { Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelMedium) } } }
