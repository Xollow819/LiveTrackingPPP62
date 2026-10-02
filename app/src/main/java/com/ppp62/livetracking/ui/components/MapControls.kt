package com.ppp62.livetracking.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.delay

enum class MapStyle { Standard, Satellite }

private fun mapPreferences(context: Context) = context.getSharedPreferences("map_preferences", Context.MODE_PRIVATE)

@Composable
fun rememberMapStyle(): MapStyle {
    val context = LocalContext.current
    val preferences = remember(context) { mapPreferences(context) }
    fun read() = if (preferences.getString("style", null) == MapStyle.Satellite.name) MapStyle.Satellite else MapStyle.Standard
    var style by remember { mutableStateOf(read()) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "style") style = read() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return style
}

fun saveMapStyle(context: Context, style: MapStyle) {
    mapPreferences(context).edit().putString("style", style.name).apply()
}

@Composable
fun MapControlButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, busy: Boolean = false) {
    GlassCard(onClick = onClick, modifier = modifier.size(48.dp).semantics { contentDescription = description }, shape = androidx.compose.foundation.shape.RoundedCornerShape(50), translucent = true) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Icon(icon, description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLayersControl(style: MapStyle, onSelect: (MapStyle) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    MapControlButton(Icons.Default.Layers, "Choose map type", { open = true }, modifier)
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Map type", style = MaterialTheme.typography.titleLarge)
                MapStyle.entries.forEach { option ->
                    val available = true
                    OutlinedCard(onClick = { onSelect(option); open = false }, enabled = available, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(if (option == MapStyle.Standard) Icons.Default.Map else Icons.Default.SatelliteAlt, null)
                            Column(Modifier.weight(1f)) {
                                Text(option.name, style = MaterialTheme.typography.titleMedium)
                                Text(if (!available) "Satellite imagery is not available" else if (option == MapStyle.Standard) "Streets and places" else "Sentinel-2 · landscape detail · educational use", style = MaterialTheme.typography.bodySmall)
                            }
                            if (style == option) Icon(Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CheckpointMapHint(modifier: Modifier = Modifier, visible: Boolean = true) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(expanded) { if (expanded) { delay(4_000); expanded = false } }
    var drag by remember { mutableFloatStateOf(0f) }
    AnimatedVisibility(visible, modifier = modifier) {
        GlassCard(modifier = Modifier.animateContentSize().pointerInput(expanded) {
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onDragEnd = { if (drag < -32.dp.toPx()) expanded = false },
                onHorizontalDrag = { change, amount -> drag += amount; change.consume() }
            )
        }, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), translucent = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Info, if (expanded) "Collapse checkpoint instructions" else "Show checkpoint instructions", tint = MaterialTheme.colorScheme.primary)
                }
                AnimatedVisibility(expanded,
                    enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                    exit = slideOutHorizontally { -it } + shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()) {
                    Text("Tap the map to add a checkpoint", modifier = Modifier.widthIn(max = 205.dp).padding(end = 16.dp, top = 10.dp, bottom = 10.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
