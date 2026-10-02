package com.ppp62.livetracking.ui.screens

import com.ppp62.livetracking.ui.components.GlassCard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ppp62.livetracking.data.SyncState
import com.ppp62.livetracking.data.TransportJourney
import com.ppp62.livetracking.data.remote.SubmissionRow
import com.ppp62.livetracking.ui.AppViewModel
import com.ppp62.livetracking.ui.BackendViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionsScreen(vm: AppViewModel, bvm: BackendViewModel, onBack: () -> Unit) {
    val submissions by vm.checkIns.collectAsState(); val checkpoints by vm.checkpoints.collectAsState()
    val onlineSession by bvm.onlineSession
    val onlineSubmissions by bvm.onlineSubmissions.collectAsState()
    val uniqueLocal = submissions.filter { local -> onlineSubmissions.none { it.id == local.id } }
    LaunchedEffect(onlineSession) { if (onlineSession != null) bvm.refreshOnlineSubmissions() }
    Scaffold(topBar = { TopAppBar(title = { Column { Text("Field records", fontWeight = FontWeight.Bold); Text("Review submitted evidence", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                GlassCard(Modifier.fillMaxWidth().padding(12.dp, 12.dp, 12.dp, 4.dp), shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Default.AssignmentTurnedIn, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                        Column {
                            Text("${onlineSubmissions.size + uniqueLocal.size} records", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(if (onlineSession != null) "Session ${onlineSession!!.code}" else "Saved on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            if (onlineSession != null) {
                item {
                    Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Online submissions (${onlineSubmissions.size})", fontWeight = FontWeight.Bold)
                        TextButton(onClick = { bvm.refreshOnlineSubmissions() }) { Icon(Icons.Default.Refresh, null); Text("Refresh") }
                    }
                }
                items(onlineSubmissions, key = { it.id ?: it.createdAt.orEmpty() + it.displayName }) { item ->
                    GlassCard(Modifier.fillMaxWidth().padding(12.dp, 6.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(TransportJourney.phase(item.id, item.sessionId, item.userId)?.let { "${it.label} · ${item.checkpointName}" }
                                    ?: item.checkpointName.ifBlank { "Checkpoint" }, fontWeight = FontWeight.Bold)
                                AssistChip(onClick = {}, label = { Text("ONLINE") }, leadingIcon = { Icon(Icons.Default.CloudDone, null) })
                            }
                            Text("${item.displayName} · ${item.team}")
                            item.exceptionReason?.let { Text("Flagged: $it",color=MaterialTheme.colorScheme.tertiary) }
                            val details = listOfNotNull(
                                item.temperatureC?.let { "$it °C" },
                                item.weightKg?.let { "$it kg" },
                                item.condition
                            ).joinToString(" • ")
                            if (details.isNotBlank()) Text(details)
                            item.photoPath?.let { OnlineEvidenceThumbnail(it, bvm) }
                            if (item.note.isNotBlank()) Text(item.note)
                            item.createdAt?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)); Text("On this device", fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp, 4.dp)) }
            }
            if (submissions.isEmpty() && onlineSubmissions.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(32.dp)) { Text("No submissions yet. Student check-ins will appear here.") } }
            else items(uniqueLocal, key = { it.id }) { item ->
            val cp = checkpoints.firstOrNull { it.id == item.checkpointId }?.name ?: item.checkpointId
            GlassCard(Modifier.fillMaxWidth().padding(12.dp, 6.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(TransportJourney.phase(item.id, item.sessionId, item.userId)?.let { "${it.label} · $cp" } ?: cp, fontWeight = FontWeight.Bold); AssistChip(onClick = {}, label = { Text(item.syncState.name) }, leadingIcon = { Icon(if (item.syncState == SyncState.FLAGGED) Icons.Default.Warning else Icons.Default.CloudUpload, null) }) }
                Text("${item.studentName} • ${item.team}"); Text("${item.temperatureC ?: "—"} °C • ${item.weightKg ?: "—"} kg • ${item.condition.name}")
                item.distanceMeters?.let { Text("Distance ${it.toInt()} m") }; item.exceptionReason?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium) }
                item.photoUri?.let { EvidenceThumbnail(it) }
                if (item.notes.isNotBlank()) Text(item.notes); Text(DateFormat.getDateTimeInstance().format(Date(item.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        } }
    }
}

/** Downloads an authenticated private evidence photo from Supabase Storage and shows a thumbnail. */
@Composable private fun OnlineEvidenceThumbnail(photoPath: String, bvm: BackendViewModel) {
    var bitmap by remember(photoPath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photoPath) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                val bytes = bvm.downloadEvidence(photoPath) ?: return@withContext null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val sample = max(1, min(bounds.outWidth, bounds.outHeight) / 240)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            } catch (_: Exception) { null }
        }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Evidence photo",
            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

/** Decodes a downsampled thumbnail of the evidence photo off the main thread. */
@Composable private fun EvidenceThumbnail(photoUri: String) {
    val context = LocalContext.current
    var bitmap by remember(photoUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photoUri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(photoUri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val sample = max(1, min(bounds.outWidth, bounds.outHeight) / 240)
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            } catch (_: Exception) { null }
        }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Evidence photo",
            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
    }
}
