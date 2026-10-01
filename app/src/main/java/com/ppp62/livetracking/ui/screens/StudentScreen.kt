package com.ppp62.livetracking.ui.screens

import android.content.Intent
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
import androidx.core.content.ContextCompat
import com.ppp62.livetracking.data.TrackingState
import com.ppp62.livetracking.service.LocationTrackingService
import com.ppp62.livetracking.ui.AppViewModel
import com.ppp62.livetracking.ui.components.OsmMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentScreen(vm: AppViewModel, onBack: () -> Unit, onCheckIn: (String) -> Unit) {
    val checkpoints by vm.checkpoints.collectAsState()
    val locations by vm.locations.collectAsState()
    val profile by vm.profile
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("PPP6201") }
    var name by rememberSaveable { mutableStateOf(profile.name) }
    var team by rememberSaveable { mutableStateOf(profile.team) }
    var tracking by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Column { Text("Student field session", fontWeight = FontWeight.Bold); Text(if (profile.joined) "${profile.name} • ${profile.team}" else "Join before tracking", style = MaterialTheme.typography.labelSmall) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            if (!profile.joined) item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Join session", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Join code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(name, { name = it }, label = { Text("Student name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(team, { team = it }, label = { Text("Team / vehicle") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { vm.join(code, name, team) {} }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Login, null); Text(" Join active session") }
                }
            } else {
                item { OsmMap(Modifier.fillMaxWidth().height(310.dp), checkpoints, locations) }
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!tracking) Button(onClick = {
                            tracking = true
                            val i = Intent(context, LocationTrackingService::class.java).putExtra(LocationTrackingService.EXTRA_NAME, profile.name).putExtra(LocationTrackingService.EXTRA_TEAM, profile.team)
                            ContextCompat.startForegroundService(context, i)
                        }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PlayArrow, null); Text(" Start tracking") }
                        else {
                            OutlinedButton(onClick = { context.startService(Intent(context, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_PAUSE)) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Pause, null); Text(" Pause") }
                            Button(onClick = { tracking = false; context.startService(Intent(context, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_FINISH)) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.weight(1f)) { Icon(Icons.Default.StopCircle, null); Text(" Finish") }
                        }
                    }
                }
                item { Text("Route checkpoints", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp)) }
                items(checkpoints, key = { it.id }) { cp ->
                    ListItem(headlineContent = { Text("${cp.orderIndex}. ${cp.name}") }, supportingContent = { Text(cp.instructions) }, leadingContent = { Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { FilledTonalIconButton(onClick = { onCheckIn(cp.id) }) { Icon(Icons.Default.AddAPhoto, "Check in") } })
                    HorizontalDivider()
                }
                item { val own = locations.firstOrNull { it.participantId == "this-device" }; AssistChip(onClick = {}, label = { Text(if (own == null) "Waiting for GPS" else "GPS ±${own.accuracyMeters.toInt()} m • ${own.trackingState}") }, leadingIcon = { Icon(Icons.Default.GpsFixed, null) }, modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}
