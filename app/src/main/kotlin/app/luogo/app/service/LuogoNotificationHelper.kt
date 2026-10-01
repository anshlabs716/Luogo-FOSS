package app.luogo.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.luogo.app.MainActivity
import app.luogo.app.R

/**
 * Manages Android Notification Channels and dispatches notifications for:
 * - Persistent Live Location & Finding Foreground Service
 * - Saved Place Arrival / Departure Geofence Events
 * - Item Finding Sightings & Nearby Detection
 * - Unknown Tracker Safety Alerts
 */
class LuogoNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_LIVE_TRACKING = "luogo_live_tracking"
        const val CHANNEL_GEOFENCES = "luogo_geofences"
        const val CHANNEL_ITEMS_AND_SAFETY = "luogo_items_safety"

        const val NOTIF_ID_FOREGROUND_SERVICE = 1001
        private var dynamicNotifId = 2000
    }

    init {
        createNotificationChannels()
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val trackingChannel = NotificationChannel(
                CHANNEL_LIVE_TRACKING,
                context.getString(R.string.notification_channel_tracking),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent status when live location sharing or background BLE finding is active"
            }

            val geofenceChannel = NotificationChannel(
                CHANNEL_GEOFENCES,
                context.getString(R.string.notification_channel_geofence),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Arrival and departure notifications for saved places"
            }

            val itemsChannel = NotificationChannel(
                CHANNEL_ITEMS_AND_SAFETY,
                context.getString(R.string.notification_channel_items),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Crowdsourced item detections and unknown tracker safety alerts"
            }

            nm.createNotificationChannels(listOf(trackingChannel, geofenceChannel, itemsChannel))
        }
    }

    fun buildForegroundServiceNotification(statusLine: String): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(context, LuogoForegroundService::class.java).apply {
            action = LuogoForegroundService.ACTION_STOP_SHARING
        }
        val pendingPause = PendingIntent.getService(
            context,
            1,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_LIVE_TRACKING)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Luogo-FOSS Live Sharing Active")
            .setContentText(statusLine)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Pause Sharing",
                pendingPause
            )
            .build()
    }

    fun notifyGeofenceTransition(subjectName: String, placeName: String, arrived: Boolean) {
        if (!canPostNotifications()) return
        val title = if (arrived) "$subjectName arrived at $placeName" else "$subjectName departed $placeName"
        val body = if (arrived) {
            "Geofence arrival detected for saved place $placeName."
        } else {
            "Geofence departure detected from $placeName."
        }
        postNotification(CHANNEL_GEOFENCES, title, body)
    }

    fun notifyItemDetected(friendlyName: String, statusSummary: String) {
        if (!canPostNotifications()) return
        postNotification(
            CHANNEL_ITEMS_AND_SAFETY,
            "$friendlyName detected",
            statusSummary
        )
    }

    fun notifyUnknownTrackerAlert(riskLevel: String, distinctLocations: Int) {
        if (!canPostNotifications()) return
        postNotification(
            CHANNEL_ITEMS_AND_SAFETY,
            "Unknown Tracker Alert ($riskLevel Risk)",
            "An unrecognized BLE tag was observed traveling with you across $distinctLocations locations."
        )
    }

    private fun postNotification(channelId: String, title: String, body: String) {
        try {
            val openIntent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context,
                dynamicNotifId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            NotificationManagerCompat.from(context).notify(dynamicNotifId++, notif)
        } catch (_: SecurityException) {
        }
    }

    private fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
