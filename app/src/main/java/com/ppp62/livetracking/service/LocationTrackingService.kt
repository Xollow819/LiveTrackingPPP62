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

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Live location", NotificationManager.IMPORTANCE_LOW))
        manager = getSystemService(LocationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        participantName = intent?.getStringExtra(EXTRA_NAME) ?: participantName
        team = intent?.getStringExtra(EXTRA_TEAM) ?: team
        when (intent?.action) {
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> startTracking()
            ACTION_FINISH -> finishTracking()
            else -> startTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        paused = false
        startForeground(NOTIFICATION_ID, notification("Sharing live location", ACTION_PAUSE, "Pause"))
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            manager.removeUpdates(this)
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10_000L, 10f, this)
            // Network provider gives a faster first fix indoors / under tree cover where GPS struggles.
            if (manager.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                try { manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10_000L, 10f, this) }
                catch (_: Exception) { /* provider unavailable on this device */ }
            }
        }
        scope.launch { repository().setTrackingState(PPPRepository.DEVICE_PARTICIPANT_ID, TrackingState.LIVE) }
    }

    private fun pauseTracking() {
        paused = true
        manager.removeUpdates(this)
        startForeground(NOTIFICATION_ID, notification("Location sharing paused", ACTION_RESUME, "Resume"))
        scope.launch { repository().setTrackingState(PPPRepository.DEVICE_PARTICIPANT_ID, TrackingState.PAUSED) }
    }

    private fun finishTracking() {
        manager.removeUpdates(this)
        scope.launch { repository().setTrackingState(PPPRepository.DEVICE_PARTICIPANT_ID, TrackingState.FINISHED) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private var lastAccuracy = Float.MAX_VALUE
    private var lastAcceptedAt = 0L

    override fun onLocationChanged(location: Location) {
        if (paused || location.accuracy > 100f) return
        // With GPS + network providers active, keep the most accurate fix and
        // guarantee progress with at least one accepted fix per minute.
        val now = SystemClock.elapsedRealtime()
        if (location.accuracy > lastAccuracy && now - lastAcceptedAt <= 60_000) return
        lastAccuracy = location.accuracy
        lastAcceptedAt = now
        val battery = getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        scope.launch {
            (application as PPP62Application).database.dao().upsertLocation(
                LocationEntity(PPPRepository.DEVICE_PARTICIPANT_ID, PPPRepository.DEMO_SESSION_ID, participantName, team,
                    location.latitude, location.longitude, location.accuracy, location.speed, location.bearing,
                    location.time, TrackingState.LIVE, battery)
            )
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
    override fun onDestroy() { if (::manager.isInitialized) manager.removeUpdates(this); scope.cancel(); super.onDestroy() }

    companion object {
        const val CHANNEL = "ppp62_tracking"; const val NOTIFICATION_ID = 62
        const val ACTION_PAUSE = "ppp62.PAUSE"; const val ACTION_RESUME = "ppp62.RESUME"; const val ACTION_FINISH = "ppp62.FINISH"
        const val EXTRA_NAME = "participant_name"; const val EXTRA_TEAM = "team"
    }
}
