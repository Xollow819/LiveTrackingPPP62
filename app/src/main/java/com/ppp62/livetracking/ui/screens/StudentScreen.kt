package com.ppp62.livetracking.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ppp62.livetracking.data.*
import com.ppp62.livetracking.ui.*
import com.ppp62.livetracking.ui.components.*
import com.ppp62.livetracking.service.LocationTrackingService
import com.ppp62.livetracking.util.DeviceLocation
import com.ppp62.livetracking.data.remote.LivePositionRow
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentScreen(vm: AppViewModel, bvm: BackendViewModel, onBack: () -> Unit, onCheckIn: (String) -> Unit,
    onTransportRecord: (String, TransportPhase) -> Unit = { id, _ -> onCheckIn(id) }) {
    val context = LocalContext.current
    val profile by vm.profile; val session by bvm.onlineSession; val role by bvm.myRole
    val checkpoints by vm.checkpoints.collectAsState(); val locations by vm.locations.collectAsState()
    val allRecords by vm.checkIns.collectAsState(); val onlineRecords by bvm.onlineSubmissions.collectAsState()
    val savedTransportRecords by vm.savedTransportRecords.collectAsState()
    val userId by bvm.myUserId
    val cps = remember(checkpoints, session?.id) { TransportJourney.ordered(checkpoints.filter { it.sessionId == session?.id }) }
    val records = remember(allRecords, userId, session?.id) { allRecords.filter { it.userId == userId && it.sessionId == session?.id } }
    val remoteRecords = remember(onlineRecords, userId, session?.id) { onlineRecords.filter { it.userId == userId && it.sessionId == session?.id } }
    val serviceState by LocationTrackingService.state.collectAsState()
    val serviceIdentity by LocationTrackingService.identity.collectAsState()
    val onlinePositions by bvm.positions.collectAsState()
    val store = remember(context) { JourneyStore(context) }
    val timingFlow = remember(session?.id, userId) { store.observe(session?.id.orEmpty(), userId.orEmpty()) }
    val timing by timingFlow.collectAsState(initial = store.load(session?.id.orEmpty(), userId.orEmpty()))
    var code by rememberSaveable { mutableStateOf("") }; var name by rememberSaveable { mutableStateOf("") }; var team by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }; var joining by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var markerPickerOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(cps) { vm.setSessionCheckpoints(cps) }
    LaunchedEffect(session?.id, role) {
        if (session != null && role == "student") { val identity = bvm.savedIdentity(); vm.joinField(identity.first, identity.second) }
        else vm.resetSession()
    }
    val own = locations.firstOrNull { it.participantId == userId && it.sessionId == session?.id }
    val serviceMatches = serviceIdentity == (session?.id to userId)
    val sharing = serviceMatches && serviceState != TrackingState.FINISHED
    val markerPrefs = remember(context) { context.getSharedPreferences("participant_markers", android.content.Context.MODE_PRIVATE) }
    var markerType by remember(session?.id, userId) {
        mutableStateOf(if (session != null && userId != null) markerPrefs.getString(ParticipantMarkers.preferenceKey(session!!.id, userId!!), ParticipantMarkers.default) ?: ParticipantMarkers.default else ParticipantMarkers.default)
    }
    val mapParticipants = remember(onlinePositions, own, session?.id, userId) {
        val remote = onlinePositions.filter { it.sessionId == session?.id }.map { it.toLocationEntity() }
        if (userId != null && remote.none { it.participantId == userId } && own != null) remote + own else remote
    }
    val markerTypes = remember(onlinePositions, markerType, userId) {
        onlinePositions.associate { it.userId to it.markerType } + listOfNotNull(userId?.let { it to markerType })
    }
    fun hasRecord(phase: TransportPhase): Boolean {
        val id = TransportJourney.recordId(session?.id.orEmpty(), userId.orEmpty(), phase)
        return id in savedTransportRecords || records.any { it.id == id } || remoteRecords.any { it.id == id }
    }
    val hasStart = hasRecord(TransportPhase.START); val hasEnd = hasRecord(TransportPhase.END)
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun start() {
        val current = session ?: return
        val uid = userId ?: return
        if (!current.isActive || hasEnd || timing.finishedAt > 0) return
        error = null
        if (!DeviceLocation.locationEnabled(context)) { error = "Enable device location before sharing."; return }
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (hasStart) vm.startSharing(current.id, uid)
        else TransportJourney.endpoint(cps, TransportPhase.START)?.let { onTransportRecord(it.id, TransportPhase.START) }
            ?: run { error = "Wait for your lecturer to add the route." }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) start() else error = "Allow location access before sharing."
    }
    fun finish() {
        val current = session ?: return
        val uid = userId ?: return
        vm.finishSharing(current.id, uid)
        val endpoint = TransportJourney.endpoint(cps, TransportPhase.END)
        if (endpoint != null) onTransportRecord(endpoint.id, TransportPhase.END)
        else error = "Sharing stopped. Wait for the lecturer’s route to record ending conditions."
    }
    val joinedMapSession = profile.joined && session != null && role == "student"
    val density = LocalDensity.current
    var navigationHeight by remember { mutableStateOf(80.dp) }
    Scaffold(topBar = { if (!profile.joined || session == null || role != "student") TopAppBar(title = { Column {
        Text(session?.title?.ifBlank { "Field session" } ?: "Field session", style = MaterialTheme.typography.titleLarge)
        Text("Your route starts here", style = MaterialTheme.typography.labelMedium)
    } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }) },
        contentWindowInsets = if (joinedMapSession) WindowInsets.systemBars.exclude(WindowInsets.statusBars) else WindowInsets.systemBars,
    ) { pad ->
        if (!profile.joined || session == null || role != "student") JoinForm(code, { code = it.uppercase() }, name, { name = it }, team, { team = it }, error, joining, {
            if (code.isBlank() || name.isBlank() || team.isBlank()) error = "Enter code, name and team"
            else { joining = true; bvm.joinOnline(code, name, "student", team = team, onError = { joining = false; error = it }, onJoined = { joining = false; vm.joinField(name, team) }) }
        }, Modifier.fillMaxSize().padding(pad))
        else Box(Modifier.fillMaxSize().padding(pad)) {
            // Keep the native map and its tiles alive while visiting Stops/Records.
            StudentRouteMap(cps, mapParticipants, markerTypes, "student-${session!!.id}", tab == 0, navigationHeight + 4.dp)
            when (tab) {
                0 -> GlassCard(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = navigationHeight + 56.dp),
                    shape = RoundedCornerShape(32.dp), translucent = true) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (sharing) Icons.Default.GpsFixed else Icons.Default.GpsOff, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(when {
                                    hasEnd -> "Transport completed"
                                    timing.finishedAt > 0 -> "Ending conditions needed"
                                    sharing && serviceState == TrackingState.PAUSED -> "Sharing paused"
                                    sharing -> "Sharing location"
                                    !session!!.isActive -> "Session completed"
                                    else -> "Ready when you are"
                                }, style = MaterialTheme.typography.titleSmall)
                                LocationFreshness(own)
                            }
                            Text(if (bvm.connectionState.value == ConnectionState.CONNECTED) "Online" else "Reconnecting", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            JourneyTimer(timing, store.bootCount(), compact = true)
                            TextButton(onClick = { markerPickerOpen = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                                val selectedMarker = ParticipantMarkers.get(markerType)
                                Text("${selectedMarker.emoji} Marker", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Text("${mapParticipants.count { it.participantId != userId && it.trackingState == TrackingState.LIVE && System.currentTimeMillis() - it.recordedAt < 90_000 }} teammates nearby", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        when {
                            hasEnd -> Text("Starting and ending conditions recorded.", style = MaterialTheme.typography.bodySmall)
                            timing.finishedAt > 0 && hasStart -> Button(onClick = ::finish, enabled = cps.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Record ending conditions") }
                            session!!.isActive && !sharing -> Button(onClick = {
                                if (DeviceLocation.hasPermission(context)) start() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            }, enabled = cps.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(if (hasStart) "Resume sharing" else "Start sharing") }
                            sharing -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(onClick = { context.startService(Intent(context, LocationTrackingService::class.java).setAction(
                                    if (serviceState == TrackingState.PAUSED) LocationTrackingService.ACTION_RESUME else LocationTrackingService.ACTION_PAUSE)) }, modifier = Modifier.weight(1f)) { Text(if (serviceState == TrackingState.PAUSED) "Resume" else "Pause") }
                                OutlinedButton(onClick = ::finish, modifier = Modifier.weight(1f)) { Text("Finish") }
                            }
                        }
                        if (!hasStart && cps.isNotEmpty()) Text("Record transport conditions at departure and arrival only.", style = MaterialTheme.typography.bodySmall)
                        if (cps.isEmpty()) Text("Your lecturer has not added the route yet.", style = MaterialTheme.typography.bodySmall)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                1 -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface.copy(alpha = .78f)) {
                    LazyColumn(Modifier.fillMaxSize().padding(top = 72.dp, bottom = navigationHeight), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text("The route", style = MaterialTheme.typography.headlineLarge); Text("${cps.size} checkpoints · conditions at start and end", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (cps.isEmpty()) item { Text("Your lecturer has not added checkpoints yet.") }
                        items(cps, key = { it.id }) { cp -> GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("${cp.orderIndex}. ${cp.name}", style = MaterialTheme.typography.titleLarge)
                                Text(cp.instructions.ifBlank { "Follow this stop on the lecturer’s route." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(when (cp.id) {
                                    cps.first().id -> if (cps.size == 1) "Departure and arrival · transport conditions" else "Departure · starting conditions"
                                    cps.last().id -> "Arrival · ending conditions"
                                    else -> "Route stop · no transport form needed"
                                }, style = MaterialTheme.typography.labelMedium)
                                Text("Arrival radius ${cp.radiusMeters.toInt()} m", style = MaterialTheme.typography.labelMedium)
                            }
                        } }
                    }
                }
                else -> Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface.copy(alpha = .78f)) {
                    LazyColumn(Modifier.fillMaxSize().padding(top = 72.dp, bottom = navigationHeight), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text("Your records", style = MaterialTheme.typography.headlineLarge); TextButton(onClick = { vm.retryUploads() }) { Text("Retry uploads") } }
                        if (records.isEmpty() && remoteRecords.isEmpty()) item { Text("Starting and ending transport records will appear here.") }
                        items(records, key = { it.id }) { record -> GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(TransportJourney.phase(record.id, record.sessionId, record.userId)?.label
                                    ?: cps.firstOrNull { it.id == record.checkpointId }?.name ?: "Checkpoint", style = MaterialTheme.typography.titleMedium)
                                Text(listOfNotNull(record.temperatureC?.let { "Water $it °C" },record.totalFish?.let { "$it fish" },record.ph?.let { "pH $it" },record.dissolvedOxygen?.let { "DO $it mg/L" },record.condition.name.lowercase()).joinToString(" · "))
                                Text(if (record.syncState == SyncState.SYNCED) "Uploaded" else if (record.syncState == SyncState.FAILED) "Upload needs attention" else "Upload queued", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                record.uploadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                record.exceptionReason?.let { Text("Flagged: $it", color = MaterialTheme.colorScheme.tertiary) }
                                if (record.notes.isNotBlank()) Text(record.notes)
                            }
                        } }
                        items(remoteRecords.filter { remote -> records.none { it.id == remote.id } }, key = { it.id.orEmpty() }) { record -> GlassCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(TransportJourney.phase(record.id, record.sessionId, record.userId)?.label ?: record.checkpointName, style = MaterialTheme.typography.titleMedium)
                                Text(listOfNotNull(record.temperatureC?.let { "Water $it °C" },record.totalFish?.let { "$it fish" },record.ph?.let { "pH $it" },record.dissolvedOxygen?.let { "DO $it mg/L" },record.condition?.lowercase()).joinToString(" · "))
                                Text("Uploaded", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                record.exceptionReason?.let { Text("Flagged: $it", color = MaterialTheme.colorScheme.tertiary) }
                            }
                        } }
                    }
                }
            }
            GlassPageHeader(
                title = session?.title?.ifBlank { "Field session" } ?: "Field session",
                subtitle = "${profile.name} · ${profile.team}",
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp)
            ) {
                IconButton(onClick = { bvm.leaveSession(); vm.resetSession() }) { Icon(Icons.Default.Logout, "Leave session") }
            }
            GlassNavigation(
                tab,
                listOf("Map", "Stops", "Records"),
                listOf(Icons.Default.Map, Icons.Default.Route, Icons.Default.ReceiptLong),
                modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { navigationHeight = with(density) { it.height.toDp() } }
            ) { tab = it }
            if (markerPickerOpen) ModalBottomSheet(onDismissRequest = { markerPickerOpen = false }) {
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose your map marker", style = MaterialTheme.typography.titleLarge)
                    Text("Your team will see this icon on the live map.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ParticipantMarkers.options.forEach { option ->
                        ListItem(
                            headlineContent = { Text(option.label) },
                            leadingContent = { Text(option.emoji, style = MaterialTheme.typography.headlineSmall) },
                            trailingContent = { if (markerType == option.id) Icon(Icons.Default.Check, contentDescription = "Selected") },
                            modifier = Modifier.fillMaxWidth().clickable {
                                markerType = option.id
                                val currentSession = session
                                val currentUser = userId
                                if (currentSession != null && currentUser != null) {
                                    markerPrefs.edit().putString(ParticipantMarkers.preferenceKey(currentSession.id, currentUser), option.id).apply()
                                    if (serviceMatches) context.startService(Intent(context, LocationTrackingService::class.java)
                                        .setAction(LocationTrackingService.ACTION_SET_MARKER)
                                        .putExtra(LocationTrackingService.EXTRA_SESSION, currentSession.id)
                                        .putExtra(LocationTrackingService.EXTRA_USER, currentUser)
                                        .putExtra(LocationTrackingService.EXTRA_NAME, profile.name)
                                        .putExtra(LocationTrackingService.EXTRA_TEAM, profile.team)
                                        .putExtra(LocationTrackingService.EXTRA_MARKER_TYPE, option.id))
                                }
                                markerPickerOpen = false
                            },
                            supportingContent = null
                        )
                    }
                }
            }
        }
    }
}

/** Only this leaf recomposes each second; map overlays and route requests stay unchanged. */
@Composable
private fun JourneyTimer(timing: JourneyTiming, bootCount: Int, compact: Boolean = false) {
    var elapsed by remember(timing) { mutableLongStateOf(timing.elapsed(System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime(), bootCount)) }
    LaunchedEffect(timing) {
        while (timing.running) {
            elapsed = timing.elapsed(System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime(), bootCount)
            delay(1000)
        }
    }
    Row(Modifier.then(if (compact) Modifier else Modifier.fillMaxWidth()), horizontalArrangement = if (compact) Arrangement.spacedBy(6.dp) else Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(if (compact) "Time" else "Journey time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(JourneyTiming.format(elapsed), style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun LivePositionRow.toLocationEntity(): LocationEntity {
    val timestamp = runCatching { java.time.OffsetDateTime.parse(recordedAt).toInstant().toEpochMilli() }.getOrDefault(0L)
    return LocationEntity(
        participantId = userId,
        sessionId = sessionId,
        participantName = displayName.ifBlank { "Student" },
        team = team,
        latitude = lat,
        longitude = lng,
        accuracyMeters = (accuracy ?: 0.0).toFloat(),
        speedMps = 0f,
        heading = 0f,
        recordedAt = timestamp,
        trackingState = runCatching { TrackingState.valueOf(trackingState) }.getOrDefault(TrackingState.STALE),
        batteryPercent = 0
    )
}

@Composable
private fun LocationFreshness(own: LocationEntity?) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(own?.recordedAt) { while (own != null) { now = System.currentTimeMillis(); delay(5000) } }
    Text(own?.let { "±${it.accuracyMeters.toInt()} m · ${((now - it.recordedAt).coerceAtLeast(0) / 1000)}s ago" }
        ?: "Waiting for a location fix", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun StudentRouteMap(checkpoints: List<CheckpointEntity>, participants: List<LocationEntity>, markerTypes: Map<String, String>, viewportKey: String, active: Boolean, attributionBottomPadding: androidx.compose.ui.unit.Dp) {
    if (checkpoints.isEmpty()) {
        if (active) Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(bottom = 240.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Route, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Waiting for the lecturer’s route", style = MaterialTheme.typography.titleMedium)
                    Text("Pins appear automatically when the route is ready.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        return
    }
    val context = LocalContext.current
    val points = remember(checkpoints) { com.ppp62.livetracking.util.RoutePlanner.coordinates(checkpoints) }
    var retry by remember { mutableIntStateOf(0) }; var overview by remember { mutableIntStateOf(0) }
    var routing by remember(points, retry) { mutableStateOf(points.size > 1) }
    var target by remember { mutableStateOf<GeoPoint?>(null, referentialEqualityPolicy()) }
    val route by produceState(com.ppp62.livetracking.util.RoutePlanner.direct(points), points, retry) {
        value = com.ppp62.livetracking.util.RoutePlanner.direct(points)
        try { value = com.ppp62.livetracking.util.RoutePlanner.load(context, points, retry > 0) }
        finally { routing = false }
    }
    Box(Modifier.fillMaxSize()) {
        OsmMap(Modifier.fillMaxSize(), checkpoints, participants = participants, markerTypes = markerTypes, target = target, viewportKey = viewportKey,
            showRadius = false, routePoints = route.points, roadRoute = route.road, fitCheckpoints = true,
            routeOverviewRequest = overview, active = active, layersTopPadding = 184.dp, attributionBottomPadding = attributionBottomPadding)
        if (active) {
            MapExploreControls({ target = it }, Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 12.dp))
            if (points.size > 1) GlassCard(
                Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 72.dp, end = 72.dp).widthIn(max = 270.dp),
                shape = RoundedCornerShape(18.dp), translucent = true
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (route.road) "Road route" else "Pin-to-pin path", style = MaterialTheme.typography.labelLarge)
                        Text(String.format(java.util.Locale.ROOT, "%.1f km · %d stops", route.distanceMeters / 1000, checkpoints.size), style = MaterialTheme.typography.bodySmall)
                        if (routing) Text("Finding road route…", style = MaterialTheme.typography.labelSmall)
                        else if (!route.road) TextButton(onClick = { retry++ }, contentPadding = PaddingValues(0.dp)) { Text("Retry road route") }
                    }
                    IconButton(onClick = { overview++ }) { Icon(Icons.Default.Route, "Show lecturer route") }
                }
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
        Text("Enter the details shared by your lecturer to open the route and start recording your practical. When you start sharing, your live location and map marker are visible to your lecturer and other students in this session.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
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
