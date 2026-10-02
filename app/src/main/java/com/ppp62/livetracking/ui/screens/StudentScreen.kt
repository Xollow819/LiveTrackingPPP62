package com.ppp62.livetracking.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ppp62.livetracking.data.*
import com.ppp62.livetracking.ui.*
import com.ppp62.livetracking.ui.components.*
import com.ppp62.livetracking.service.LocationTrackingService
import com.ppp62.livetracking.service.SyncWorker
import com.ppp62.livetracking.util.DeviceLocation
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentScreen(vm:AppViewModel,bvm:BackendViewModel,onBack:()->Unit,onCheckIn:(String)->Unit) {
    val context=LocalContext.current
    val profile by vm.profile; val session by bvm.onlineSession; val role by bvm.myRole
    val cps by vm.checkpoints.collectAsState(); val locations by vm.locations.collectAsState(); val allRecords by vm.checkIns.collectAsState()
    val records=allRecords.filter{it.userId==bvm.myUserId.value}
    val serviceState by LocationTrackingService.state.collectAsState()
    var code by rememberSaveable { mutableStateOf("") }; var name by rememberSaveable { mutableStateOf("") }; var team by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }; var joining by remember { mutableStateOf(false) }; var tab by rememberSaveable { mutableIntStateOf(0) }
    var target by remember { mutableStateOf<GeoPoint?>(null, referentialEqualityPolicy()) }; var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while(true) {now=System.currentTimeMillis();delay(5000)} }
    LaunchedEffect(cps) { vm.setSessionCheckpoints(cps) }
    LaunchedEffect(session?.id,role) {
        if(session!=null && role=="student") { val identity=bvm.savedIdentity();vm.joinField(identity.first,identity.second) }
        else vm.resetSession()
    }
    val own=locations.firstOrNull {it.participantId==bvm.myUserId.value}
    val sharing=serviceState==TrackingState.LIVE || serviceState==TrackingState.PAUSED
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun start() {
        if(session?.isActive!=true || bvm.myUserId.value==null) return
        try {
            ContextCompat.startForegroundService(context,Intent(context,LocationTrackingService::class.java)
                .putExtra(LocationTrackingService.EXTRA_SESSION,session!!.id).putExtra(LocationTrackingService.EXTRA_USER,bvm.myUserId.value)
                .putExtra(LocationTrackingService.EXTRA_NAME,profile.name).putExtra(LocationTrackingService.EXTRA_TEAM,profile.team))
            if(Build.VERSION.SDK_INT>=33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } catch(e:Exception) {error="Unable to start sharing. Check location permissions."}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants -> if(grants.values.any{it}) start() else error="Allow location access before sharing" }
    Scaffold(topBar={TopAppBar(title={Column {Text(session?.title?.ifBlank{"Field session"} ?: "Field session",style=MaterialTheme.typography.titleLarge);Text(if(profile.joined) "${profile.name} · ${profile.team}" else "Your route starts here",style=MaterialTheme.typography.labelMedium)}},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")}},actions={if(profile.joined) IconButton(onClick={bvm.leaveSession();vm.resetSession()}){Icon(Icons.Default.Logout,"Leave session")}})},bottomBar={
        if(profile.joined && session!=null && role=="student") GlassNavigation(tab,listOf("Map","Stops","Records"),listOf(Icons.Default.Map,Icons.Default.Route,Icons.Default.ReceiptLong)){tab=it}
    }) { pad ->
        if(!profile.joined || session==null || role!="student") JoinForm(code,{code=it.uppercase()},name,{name=it},team,{team=it},error,joining,{
            if(code.isBlank()||name.isBlank()||team.isBlank()) error="Enter code, name and team"
            else {joining=true;bvm.joinOnline(code,name,"student",team=team,onError={joining=false;error=it},onJoined={joining=false;vm.joinField(name,team)})}
        },Modifier.fillMaxSize().padding(pad))
        else when(tab) {
            0 -> Box(Modifier.fillMaxSize().padding(pad)) {
                OsmMap(Modifier.fillMaxSize(),cps,listOfNotNull(own),myLocation=own?.let{GeoPoint(it.latitude,it.longitude)},target=target,viewportKey="student-${session!!.id}",layersTopPadding=124.dp,attributionBottomPadding=160.dp)
                MapExploreControls({target=it},Modifier.align(Alignment.TopEnd).padding(12.dp))
                GlassCard(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Icon(if(sharing) Icons.Default.GpsFixed else Icons.Default.GpsOff,null,tint=MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
                                Text(if(!session!!.isActive) "Session completed" else if(serviceState==TrackingState.PAUSED) "Sharing paused" else if(sharing) "Sharing location" else "Ready when you are",style=MaterialTheme.typography.titleMedium)
                                Text(own?.let{"±${it.accuracyMeters.toInt()} m · ${((now-it.recordedAt).coerceAtLeast(0)/1000)}s ago"} ?: "Waiting for a location fix",style=MaterialTheme.typography.bodySmall)
                            }
                            Text(if(bvm.connectionState.value==ConnectionState.CONNECTED) "Connected" else "Reconnecting",style=MaterialTheme.typography.labelSmall)
                        }
                        if(session!!.isActive) Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            if(!sharing) Button(onClick={if(DeviceLocation.hasPermission(context)) start() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},modifier=Modifier.fillMaxWidth()){Text("Start sharing")}
                            else {
                                FilledTonalButton(onClick={context.startService(Intent(context,LocationTrackingService::class.java).setAction(if(serviceState==TrackingState.PAUSED) LocationTrackingService.ACTION_RESUME else LocationTrackingService.ACTION_PAUSE))},modifier=Modifier.weight(1f)){Text(if(serviceState==TrackingState.PAUSED) "Resume" else "Pause")}
                                OutlinedButton(onClick={context.startService(Intent(context,LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_FINISH))},modifier=Modifier.weight(1f)){Text("Finish")}
                            }
                        }
                        error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                    }
                }
            }
            1 -> LazyColumn(Modifier.fillMaxSize().padding(pad),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item{Text("The route",style=MaterialTheme.typography.headlineLarge);Text("${cps.size} checkpoints",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                if(cps.isEmpty()) item{Text("Your lecturer has not added checkpoints yet.")}
                items(cps,key={it.id}) {cp -> GlassCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("${cp.orderIndex}. ${cp.name}",style=MaterialTheme.typography.titleLarge)
                    Text(cp.instructions.ifBlank{"Record conditions when you arrive."},color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Arrival radius ${cp.radiusMeters.toInt()} m",style=MaterialTheme.typography.labelMedium)
                    Button(onClick={onCheckIn(cp.id)},enabled=session!!.isActive){Icon(Icons.Default.AddAPhoto,null);Text("  Check in")}
                }} }
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(pad),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {Text("Your records",style=MaterialTheme.typography.headlineLarge);TextButton(onClick={vm.retryUploads()}){Text("Retry uploads")}}
                if(records.isEmpty()) item{Text("Your check-ins will appear here.")}
                items(records,key={it.id}) {record -> GlassCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(cps.firstOrNull{it.id==record.checkpointId}?.name ?: "Checkpoint",style=MaterialTheme.typography.titleMedium)
                    Text("${record.temperatureC ?: "—"} °C · ${record.weightKg ?: "—"} kg · ${record.condition.name.lowercase()}")
                    Text(if(record.syncState==SyncState.SYNCED) "Uploaded" else if(record.syncState==SyncState.FAILED) "Upload needs attention" else "Upload queued",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
                    record.uploadError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                    record.exceptionReason?.let{Text("Flagged: $it",color=MaterialTheme.colorScheme.tertiary)}
                    if(record.notes.isNotBlank()) Text(record.notes)
                }} }
            }
        }
    }
}

@Composable
private fun JoinForm(
    code: String, onCode: (String) -> Unit,
    name: String, onName: (String) -> Unit,
    team: String, onTeam: (String) -> Unit,
    error: String?, joining: Boolean,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.imePadding().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("Join your field session", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Enter the details shared by your lecturer to open the route and start recording your practical.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        GlassCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(code, onCode, label = { Text("Session code") }, placeholder = { Text("e.g. KX7Q2P") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Key, null) })
        OutlinedTextField(name, onName, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Person, null) })
        OutlinedTextField(team, onTeam, label = { Text("Team") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Group, null) })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onJoin, enabled = !joining, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
            if (joining) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else { Icon(Icons.Default.Login, null); Spacer(Modifier.width(8.dp)); Text("Join session") }
        }
    }
}
