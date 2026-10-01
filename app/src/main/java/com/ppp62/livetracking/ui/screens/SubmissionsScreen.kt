package com.ppp62.livetracking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ppp62.livetracking.data.SyncState
import com.ppp62.livetracking.ui.AppViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val submissions by vm.checkIns.collectAsState(); val checkpoints by vm.checkpoints.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Check-in review") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { pad ->
        if (submissions.isEmpty()) Box(Modifier.fillMaxSize().padding(pad).padding(32.dp)) { Text("No submissions yet. Student check-ins will appear here.") }
        else LazyColumn(Modifier.fillMaxSize().padding(pad)) { items(submissions, key = { it.id }) { item ->
            val cp = checkpoints.firstOrNull { it.id == item.checkpointId }?.name ?: item.checkpointId
            ElevatedCard(Modifier.fillMaxWidth().padding(12.dp, 6.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(cp, fontWeight = FontWeight.Bold); AssistChip(onClick = {}, label = { Text(item.syncState.name) }, leadingIcon = { Icon(if (item.syncState == SyncState.FLAGGED) Icons.Default.Warning else Icons.Default.CloudUpload, null) }) }
                Text("${item.studentName} • ${item.team}"); Text("${item.temperatureC} °C • ${item.weightKg} kg • ${item.condition.name}")
                item.distanceMeters?.let { Text("Distance ${it.toInt()} m") }; item.exceptionReason?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium) }
                if (item.notes.isNotBlank()) Text(item.notes); Text(DateFormat.getDateTimeInstance().format(Date(item.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        } }
    }
}
