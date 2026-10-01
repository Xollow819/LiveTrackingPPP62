package com.ppp62.livetracking.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ppp62.livetracking.data.FishCondition
import com.ppp62.livetracking.ui.AppViewModel
import com.ppp62.livetracking.util.LocationUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(vm: AppViewModel, checkpointId: String, onDone: () -> Unit) {
    val checkpoints by vm.checkpoints.collectAsState(); val locations by vm.locations.collectAsState()
    val checkpoint = vm.sessionCheckpoints.value.firstOrNull { it.id == checkpointId }
        ?: checkpoints.firstOrNull { it.id == checkpointId }
    val context = LocalContext.current
    var temperature by rememberSaveable { mutableStateOf("") }; var weight by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }; var condition by rememberSaveable { mutableStateOf(FishCondition.GOOD) }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }; var pendingUri by remember { mutableStateOf<Uri?>(null) }; var attempted by remember { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) photoUri = pendingUri?.toString() }
    val own = locations.firstOrNull { it.participantId == "this-device" }
    val distance = if (checkpoint != null && own != null) LocationUtils.distanceMeters(own.latitude, own.longitude, checkpoint.latitude, checkpoint.longitude) else null

    Scaffold(topBar = { TopAppBar(title = { Text(checkpoint?.name ?: "Checkpoint check-in") }, navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.Close, null) } }) }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 18.dp, vertical = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (checkpoint == null) { Text("Checkpoint not found"); return@Column }
            Text("Field record", style = MaterialTheme.typography.headlineMedium)
            Text("Capture the conditions and evidence for this arrival.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ElevatedCard(shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = if (distance != null && distance <= checkpoint.radiusMeters) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(if (distance == null) Icons.Default.GpsOff else Icons.Default.GpsFixed, null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (distance == null) "GPS fix unavailable" else "${distance.toInt()} m from checkpoint", style = MaterialTheme.typography.titleMedium)
                        Text(if (distance == null) "Location will be recorded as unavailable" else "Allowed radius ${checkpoint.radiusMeters.toInt()} m", style = MaterialTheme.typography.bodySmall)
                    }
                    if (distance != null && distance <= checkpoint.radiusMeters) Icon(Icons.Default.CheckCircle, "Within checkpoint radius")
                }
            }
            Text("Transport conditions", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(temperature, { temperature = it }, label = { Text("Water temperature (°C) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = attempted && temperature.toDoubleOrNull() == null, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(weight, { weight = it }, label = { Text("Consignment weight (kg) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = attempted && weight.toDoubleOrNull() == null, modifier = Modifier.fillMaxWidth())
            Text("Fish condition", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FishCondition.entries.forEach { value -> FilterChip(selected = condition == value, onClick = { condition = value }, label = { Text(value.name.lowercase().replaceFirstChar { it.uppercase() }) }) } }
            Text("Supporting evidence", style = MaterialTheme.typography.titleLarge)
            OutlinedCard(onClick = {
                val dir = File(context.filesDir, "evidence").apply { mkdirs() }; val file = File(dir, "checkin-${System.currentTimeMillis()}.jpg")
                pendingUri = FileProvider.getUriForFile(context, "${context.packageName}.files", file); camera.launch(pendingUri!!)
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(18.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    val evidenceIcon = if (photoUri == null) Icons.Default.AddAPhoto else Icons.Default.CheckCircle
                    val evidenceTitle = if (photoUri == null) "Capture cargo/fish evidence *" else "Evidence photo captured"
                    Icon(evidenceIcon, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(evidenceTitle, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text("Stored privately in app storage", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (attempted && checkpoint.requiresPhoto && photoUri == null) Text("A photo is required", color = MaterialTheme.colorScheme.error)
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes / exception explanation") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                attempted = true; val temp = temperature.toDoubleOrNull(); val kg = weight.toDoubleOrNull()
                if (temp != null && kg != null && (!checkpoint.requiresPhoto || photoUri != null)) vm.submit(checkpoint, temp, kg, condition, notes, photoUri, own?.latitude, own?.longitude, onDone)
            }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Save check-in") }
            Text("Evidence is queued locally and remains marked Pending until an authenticated server confirms upload.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
