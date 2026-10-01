package com.ppp62.livetracking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ppp62.livetracking.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckpointEditorScreen(vm: AppViewModel, onBack: () -> Unit) {
    val checkpoints by vm.checkpoints.collectAsState(); var show by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Checkpoint editor") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }, actions = { IconButton(onClick = { show = true }) { Icon(Icons.Default.Add, "Add") } }) }, floatingActionButton = { FloatingActionButton(onClick = { show = true }) { Icon(Icons.Default.AddLocationAlt, null) } }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) { item { Text("Stops are completed in this order. Default geofence is 75 m.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }; items(checkpoints, key = { it.id }) { cp -> ListItem(headlineContent = { Text("${cp.orderIndex}. ${cp.name}") }, supportingContent = { Text("${cp.latitude}, ${cp.longitude} • ${cp.radiusMeters.toInt()} m\n${cp.instructions}") }, leadingContent = { Icon(Icons.Default.DragHandle, null) }); HorizontalDivider() } }
    }
    if (show) AddCheckpointDialog(onDismiss = { show = false }) { name, lat, lng, radius, instructions -> vm.addCheckpoint(name, lat, lng, radius, instructions) { show = false } }
}

@Composable private fun AddCheckpointDialog(onDismiss: () -> Unit, onSave: (String, Double, Double, Double, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var lat by remember { mutableStateOf("") }; var lng by remember { mutableStateOf("") }; var radius by remember { mutableStateOf("75") }; var instructions by remember { mutableStateOf("") }; var attempted by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New checkpoint") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
        OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        OutlinedTextField(lng, { lng = it }, label = { Text("Longitude") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        OutlinedTextField(radius, { radius = it }, label = { Text("Arrival radius (m)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        OutlinedTextField(instructions, { instructions = it }, label = { Text("Instructions") }, minLines = 2)
        if (attempted) Text("Complete all fields with valid coordinates.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
    } }, confirmButton = { TextButton(onClick = { attempted = true; val a=lat.toDoubleOrNull(); val b=lng.toDoubleOrNull(); val r=radius.toDoubleOrNull(); if (name.isNotBlank() && a != null && b != null && r != null && r in 20.0..500.0) onSave(name,a,b,r,instructions) }) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
