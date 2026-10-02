package com.ppp62.livetracking.ui.screens

import com.ppp62.livetracking.ui.components.GlassCard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ppp62.livetracking.data.*
import com.ppp62.livetracking.data.remote.LivePositionRow
import com.ppp62.livetracking.ui.AppViewModel
import com.ppp62.livetracking.ui.BackendViewModel
import com.ppp62.livetracking.ui.components.OsmMap
import com.ppp62.livetracking.ui.components.MapControlButton
import com.ppp62.livetracking.ui.toEntity
import com.ppp62.livetracking.util.CsvExporter
import com.ppp62.livetracking.util.DeviceLocation
import com.ppp62.livetracking.util.LocationUtils
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun LivePositionRow.toEntity(sessionId: String): LocationEntity {
    val recordedAt = try {
        recordedAt?.let { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() } ?: 0L
    } catch (_: Exception) { 0L }
    return LocationEntity(
        participantId = userId, sessionId = sessionId, participantName = displayName.ifBlank { "Student" },
        team = team, latitude = lat, longitude = lng, accuracyMeters = (accuracy ?: 0.0).toFloat(),
        speedMps = 0f, heading = 0f, recordedAt = recordedAt, trackingState = runCatching { TrackingState.valueOf(trackingState) }.getOrDefault(TrackingState.STALE), batteryPercent = 0
    )
}

/**
 * Lecturer entry: runs the setup wizard until a session exists, then shows
 * the live monitoring dashboard.
 */
@Composable
fun LecturerScreen(vm: AppViewModel, bvm: BackendViewModel, onBack: () -> Unit, onSubmissions: () -> Unit) {
    val onlineSession by bvm.onlineSession
    if (!bvm.lecturerAuthenticated.value) {
        LecturerAccountScreen(bvm,onBack)
    } else if (onlineSession == null || bvm.myRole.value != "lecturer") {
        var creating by remember { mutableStateOf(false) }
        if(creating) LecturerSetupScreen(vm,bvm) {creating=false} else WorkspaceScreen(bvm,onBack) {creating=true}
    } else {
        LecturerMonitorScreen(vm, bvm, onBack, onSubmissions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LecturerMonitorScreen(vm:AppViewModel,bvm:BackendViewModel,onBack:()->Unit,onSubmissions:()->Unit) {
    val session by bvm.onlineSession; val positions by bvm.positions.collectAsState(); val cps by bvm.onlineCheckpoints.collectAsState()
    val roster by bvm.roster.collectAsState(); val submissions by bvm.onlineSubmissions.collectAsState(); val alerts by bvm.alerts.collectAsState()
    var tab by androidx.compose.runtime.saveable.rememberSaveable {mutableIntStateOf(0)}
    var target by remember {mutableStateOf<GeoPoint?>(null, referentialEqualityPolicy())}
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    var routeOpen by remember{mutableStateOf(false)}
    var adding by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<com.ppp62.livetracking.data.remote.CheckpointRow?>(null)}
    var confirmClose by remember {mutableStateOf(false)}
    LaunchedEffect(Unit){while(true){now=System.currentTimeMillis();kotlinx.coroutines.delay(5000)}}
    val active=session ?: return
    val density=LocalDensity.current
    var mapFooterExtent by remember(active.id){mutableIntStateOf(with(density){128.dp.roundToPx()})}
    val attributionInset=with(density){mapFooterExtent.toDp()}+8.dp
    val people=positions.map{it.toEntity(active.id)}
    val checkpoints=cps.mapIndexed{i,cp->cp.toEntity(i)}
    val live=people.count{now-it.recordedAt<90_000&&it.trackingState==TrackingState.LIVE}
    Scaffold(topBar={TopAppBar(title={Column{Text(active.title.ifBlank{"Monitoring"},style=MaterialTheme.typography.titleLarge);Text("${active.code} · ${if(active.isActive) "Active session" else "Completed"}",style=MaterialTheme.typography.labelMedium)}},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")}},actions={
        if(active.isActive) IconButton(onClick={confirmClose=true}){Icon(Icons.Default.StopCircle,"Close session")}
        IconButton(onClick={bvm.leaveSession()}){Icon(Icons.Default.Logout,"Leave session")}
    })},bottomBar={com.ppp62.livetracking.ui.components.GlassNavigation(tab,listOf("Map","Students","Records"),listOf(Icons.Default.Map,Icons.Default.Groups,Icons.Default.Assignment)){tab=it}}) {pad->
        when(tab){
            0->Box(Modifier.fillMaxSize().padding(pad)){
                OsmMap(Modifier.fillMaxSize(),checkpoints,people,target=target,viewportKey="lecturer-${active.id}",layersTopPadding=124.dp,attributionBottomPadding=attributionInset,onMapTap=if(adding) { point ->
                    editing=com.ppp62.livetracking.data.remote.CheckpointRow(id=java.util.UUID.randomUUID().toString(),sessionId=active.id,name="",lat=point.latitude,lng=point.longitude,orderIndex=(cps.maxOfOrNull{it.orderIndex} ?: 0)+1);adding=false
                } else null)
                if(active.isActive) MapControlButton(Icons.Default.Route,"Edit route",{routeOpen=true},Modifier.align(Alignment.TopStart).padding(12.dp))
                if(adding) com.ppp62.livetracking.ui.components.CheckpointMapHint(Modifier.align(Alignment.TopStart).padding(start=12.dp,top=72.dp))
                com.ppp62.livetracking.ui.components.MapExploreControls({target=it},Modifier.align(Alignment.TopEnd).padding(12.dp))
                GlassCard(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged{mapFooterExtent=it.height}.padding(16.dp)){
                    Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.SpaceBetween){
                        Column{Text("$live",style=MaterialTheme.typography.headlineMedium);Text("Live now",style=MaterialTheme.typography.labelMedium)}
                        Column{Text("${roster.count{it.role=="student"}}",style=MaterialTheme.typography.headlineMedium);Text("Students",style=MaterialTheme.typography.labelMedium)}
                        Column{Text("${submissions.size}",style=MaterialTheme.typography.headlineMedium);Text("Records",style=MaterialTheme.typography.labelMedium)}
                    }
                }
            }
            1->LazyColumn(Modifier.fillMaxSize().padding(pad),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                item{Text("Your group",style=MaterialTheme.typography.headlineLarge);Text(if(bvm.connectionState.value==com.ppp62.livetracking.ui.ConnectionState.CONNECTED) "Connected" else "Reconnecting · showing last snapshot",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                if(roster.none{it.role=="student"}) item{Text("Share ${active.code} to invite your students.")}
                items(roster.filter{it.role=="student"},key={it.userId}){student->
                    val location=people.firstOrNull{it.participantId==student.userId}
                    val status=if(location==null) "Not sharing" else if(location.trackingState!=TrackingState.LIVE) location.trackingState.name.lowercase().replaceFirstChar{it.uppercase()} else if(now-location.recordedAt>=90_000) "Last location is stale" else "Live · ±${location.accuracyMeters.toInt()} m"
                    GlassCard(Modifier.fillMaxWidth(),onClick={location?.let{target=GeoPoint(it.latitude,it.longitude);tab=0}}){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(student.displayName,style=MaterialTheme.typography.titleLarge);Text(student.team);Text(status,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                    }}
                }
                if(alerts.isNotEmpty()) item{Text("Field activity",style=MaterialTheme.typography.titleLarge)}
                items(alerts.take(10),key={it.id}){Text(it.text,style=MaterialTheme.typography.bodyMedium)}
            }
            else->Column(Modifier.fillMaxSize().padding(pad).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
                Text("Field records",style=MaterialTheme.typography.headlineLarge)
                Text("${submissions.size} check-ins from this session. Review conditions, photos and exceptions.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick=onSubmissions,modifier=Modifier.fillMaxWidth().height(56.dp)){Text("Review evidence")}
                bvm.connectionMessage.value?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            }
        }
    }
    editing?.let {checkpoint->CheckpointEditor(checkpoint,{editing=null}){row,callback->bvm.saveCheckpoint(row,callback)}}
    if(routeOpen) ModalBottomSheet(onDismissRequest={routeOpen=false}) {
        Column(Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Route checkpoints",style=MaterialTheme.typography.titleLarge)
            cps.forEach{checkpoint->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                TextButton(onClick={editing=checkpoint;routeOpen=false},modifier=Modifier.weight(1f)){Text("${checkpoint.orderIndex}. ${checkpoint.name}")}
                if(active.isActive) IconButton(onClick={bvm.removeCheckpoint(checkpoint)}){Icon(Icons.Default.DeleteOutline,"Remove ${checkpoint.name}")}
            }}
            if(active.isActive) Button(onClick={routeOpen=false;adding=true;tab=0},modifier=Modifier.fillMaxWidth()){Text("Add checkpoint on map")}
            bvm.connectionMessage.value?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }
    if(confirmClose) AlertDialog(onDismissRequest={confirmClose=false},title={Text("Complete this session?")},text={Text("Location sharing stops and new check-ins are disabled. Existing records remain available.")},confirmButton={TextButton(onClick={confirmClose=false;bvm.closeSession()}){Text("Complete session")}},dismissButton={TextButton(onClick={confirmClose=false}){Text("Cancel")}})
}
