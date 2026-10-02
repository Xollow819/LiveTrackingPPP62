package com.ppp62.livetracking.service

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.ppp62.livetracking.MainActivity
import com.ppp62.livetracking.PPP62Application
import com.ppp62.livetracking.data.*
import kotlinx.coroutines.*

class LocationTrackingService : Service(), LocationListener {
    private lateinit var manager: LocationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var paused = false
    private var participantName = "Student"
    private var team = "Team"
    private var markerType = ParticipantMarkers.default
    private var sessionId = ""
    private var userId = ""
    private var lastFix: Location? = null
    private var heartbeat: Job? = null
    private val prefs by lazy { getSharedPreferences("tracking_service", MODE_PRIVATE) }
    private val journey by lazy { JourneyStore(this) }
    private val arrived = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Live location", NotificationManager.IMPORTANCE_LOW))
        manager = getSystemService(LocationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        participantName = intent?.getStringExtra(EXTRA_NAME) ?: prefs.getString("name", "Student").orEmpty()
        team = intent?.getStringExtra(EXTRA_TEAM) ?: prefs.getString("team", "").orEmpty()
        markerType = ParticipantMarkers.get(intent?.getStringExtra(EXTRA_MARKER_TYPE) ?: prefs.getString("marker_type", ParticipantMarkers.default).orEmpty()).id
        sessionId = intent?.getStringExtra(EXTRA_SESSION) ?: prefs.getString("session", "").orEmpty()
        userId = intent?.getStringExtra(EXTRA_USER) ?: prefs.getString("user", "").orEmpty()
        if (sessionId.isBlank() || userId.isBlank() || (intent == null && !prefs.getBoolean("sharing", false))) { stopSelf(); return START_NOT_STICKY }
        if ((intent?.action == ACTION_PAUSE || intent?.action == ACTION_RESUME) && !prefs.getBoolean("sharing", false)) { stopSelf(); return START_NOT_STICKY }
        prefs.edit().putString("name",participantName).putString("team",team).putString("session",sessionId).putString("user",userId).putString("marker_type",markerType).apply()
        identity.value = sessionId to userId
        when (intent?.action) {
            ACTION_PAUSE -> pauseTracking()
            ACTION_FINISH -> finishTracking()
            ACTION_SET_MARKER -> scope.launch { lastFix?.let { publish(it, if (paused) TrackingState.PAUSED else TrackingState.LIVE) } }
            else -> if(intent==null && prefs.getBoolean("paused",false)) pauseTracking() else startTracking()
        }
        return START_STICKY
    }
    private fun startTracking() {
        if (!com.ppp62.livetracking.util.DeviceLocation.hasPermission(this)) { prefs.edit().putBoolean("sharing",false).apply();state.value=TrackingState.FINISHED;stopSelf();return }
        try {
            startForeground(NOTIFICATION_ID, notification("Sharing live location", ACTION_PAUSE, "Pause"))
            if (journey.load(sessionId, userId).finishedAt > 0) { finishTracking(); return }
            journey.resume(sessionId, userId)
            paused = false; state.value = TrackingState.LIVE
            prefs.edit().putBoolean("sharing",true).putBoolean("paused",false).apply()
            manager.removeUpdates(this)
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10_000L, 0f, this)
            if(manager.allProviders.contains(LocationManager.NETWORK_PROVIDER) && manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10_000L, 0f, this)
            heartbeat?.cancel()
            heartbeat=scope.launch {
                while(isActive) {
                    val app=application as PPP62Application
                    try {
                        val session=app.backend.findSessionById(sessionId)
                        if(session!=null && !session.isActive) { withContext(Dispatchers.Main) { finishTracking() }; return@launch }
                        lastFix?.let { publish(it,TrackingState.LIVE) }
                    } catch (_: Exception) { }
                    delay(30_000)
                }
            }
        } catch (_: SecurityException) { finishTracking() }
        catch (_: IllegalArgumentException) { state.value=TrackingState.STALE }
    }
    private fun pauseTracking() {
        journey.pause(sessionId, userId)
        paused=true; state.value=TrackingState.PAUSED
        prefs.edit().putBoolean("paused",true).apply()
        manager.removeUpdates(this); heartbeat?.cancel()
        startForeground(NOTIFICATION_ID, notification("Location sharing paused",ACTION_RESUME,"Resume"))
        scope.launch { persistState(TrackingState.PAUSED) }
    }
    private fun finishTracking() {
        journey.finish(sessionId, userId)
        paused=true; state.value=TrackingState.FINISHED
        prefs.edit().putBoolean("sharing",false).putBoolean("paused",false).apply()
        manager.removeUpdates(this); heartbeat?.cancel()
        scope.launch {
            try { persistState(TrackingState.FINISHED) }
            finally { withContext(Dispatchers.Main) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } }
        }
    }
    private suspend fun persistState(value:TrackingState) {
        val app=application as PPP62Application
        app.database.dao().setTrackingState(userId,value,sessionId)
        val fix=lastFix ?: app.database.dao().ownLocation(sessionId,userId)?.let { row ->
            Location("cached").apply{latitude=row.latitude;longitude=row.longitude;accuracy=row.accuracyMeters;time=row.recordedAt}
        }
        fix?.let { runCatching {withTimeout(5_000){publish(it,value)}} }
    }
    private suspend fun publish(location:Location,value:TrackingState) {
        val app=application as PPP62Application
        val row=PositionOutboxEntity(sessionId,userId,participantName,team,location.latitude,location.longitude,location.accuracy.toDouble(),location.time,value.name,System.currentTimeMillis(),markerType)
        app.database.dao().queuePosition(row)
        try {
            app.backend.publishPosition(row.sessionId,row.userId,row.displayName,row.latitude,row.longitude,row.accuracy,row.recordedAt,row.team,row.trackingState,row.eventAt,row.markerType)
            app.database.dao().acknowledgePosition(row.sessionId,row.userId,row.eventAt)
        } catch(e:Exception) {SyncWorker.enqueue(app);throw e}
    }
    private var lastPublishAt=0L
    override fun onLocationChanged(location:Location) {
        if(paused) return
        lastFix=Location(location)
        state.value=TrackingState.LIVE
        scope.launch {
            val app=application as PPP62Application
            val battery=getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            app.database.dao().upsertLocation(LocationEntity(userId,sessionId,participantName,team,
                location.latitude,location.longitude,location.accuracy,location.speed,location.bearing,location.time,TrackingState.LIVE,battery))
            if(location.accuracy<=100 && System.currentTimeMillis()-location.time<90_000) {
                app.database.dao().checkpointsForSession(sessionId).forEach { cp ->
                    val distance=com.ppp62.livetracking.util.LocationUtils.distanceMeters(location.latitude,location.longitude,cp.latitude,cp.longitude)
                    val key="$sessionId:${cp.id}"
                    if(distance<=cp.radiusMeters && !prefs.getBoolean(key,false)) {
                        prefs.edit().putBoolean(key,true).apply()
                        com.ppp62.livetracking.util.Notifier.post(this@LocationTrackingService,"Checkpoint arrival","You arrived at ${cp.name}")
                    } else if(distance>cp.radiusMeters+20) prefs.edit().putBoolean(key,false).apply()
                }
            }
            val now=SystemClock.elapsedRealtime()
            if(now-lastPublishAt>=15_000) { lastPublishAt=now; runCatching { publish(location,TrackingState.LIVE) } }
        }
    }
    private fun notification(text: String, action: String, actionLabel: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val actionIntent = Intent(this, LocationTrackingService::class.java).setAction(action)
        val actionPending = PendingIntent.getService(this, action.hashCode(), actionIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val finishIntent = Intent(this, LocationTrackingService::class.java).setAction(ACTION_FINISH)
        val finishPending = PendingIntent.getService(this, 62, finishIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("PPP62 practical session")
            .setContentText(text).setOngoing(true).setContentIntent(open)
            .addAction(0, actionLabel, actionPending).addAction(0, "Finish", finishPending).build()
    }

    private fun repository() = (application as PPP62Application).repository
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        if (sessionId.isNotBlank() && userId.isNotBlank()) journey.finish(sessionId, userId)
        if (::manager.isInitialized) manager.removeUpdates(this)
        prefs.edit().putBoolean("sharing",false).apply(); state.value=TrackingState.FINISHED; identity.value=null
        scope.cancel(); super.onDestroy()
    }

    companion object {
        val state = kotlinx.coroutines.flow.MutableStateFlow(TrackingState.FINISHED)
        val identity = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, String>?>(null)
        const val EXTRA_SESSION = "session_id"; const val EXTRA_USER = "user_id"
        const val CHANNEL = "ppp62_tracking"; const val NOTIFICATION_ID = 62
        const val ACTION_PAUSE = "ppp62.PAUSE"; const val ACTION_RESUME = "ppp62.RESUME"; const val ACTION_FINISH = "ppp62.FINISH"
        const val EXTRA_NAME = "participant_name"; const val EXTRA_TEAM = "team"; const val EXTRA_MARKER_TYPE = "marker_type"
        const val ACTION_SET_MARKER = "ppp62.SET_MARKER"
    }
}
