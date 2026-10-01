package com.ppp62.livetracking.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

/**
 * Local notifications for Find-My-style arrival/departure alerts.
 * POST_NOTIFICATIONS is requested at launch in MainActivity; posting without
 * the grant is a silent no-op.
 */
object Notifier {
    const val CHANNEL_ALERTS = "field_alerts"
    private var nextId = 2000

    private fun manager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = manager(context)
        if (mgr.getNotificationChannel(CHANNEL_ALERTS) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERTS,
                    "Arrival & departure alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    fun post(context: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        runCatching {
            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(context.applicationInfo.icon)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
            manager(context).notify(nextId++, notification)
        }
    }
}
