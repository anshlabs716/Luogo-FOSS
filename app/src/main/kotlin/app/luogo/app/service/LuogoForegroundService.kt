package app.luogo.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import app.luogo.app.LuogoApplication

/**
 * Foreground service keeping live ~2-second moving location fusion and BLE crowdsourced finding active
 * while displaying a transparent, user-controllable persistent notification.
 */
class LuogoForegroundService : Service() {

    companion object {
        const val ACTION_START_SHARING = "app.luogo.app.action.START_SHARING"
        const val ACTION_STOP_SHARING = "app.luogo.app.action.STOP_SHARING"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = runCatching { LuogoApplication.getRepository(this) }.getOrNull()
        val notifHelper = LuogoNotificationHelper(this)

        if (intent?.action == ACTION_STOP_SHARING) {
            repo?.let {
                val p = it.userProfile.value
                it.updateUserProfile(p.displayName, p.colorArgb, sharingEnabled = false)
                it.hardwareLocationManager.stopLiveTracking()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = notifHelper.buildForegroundServiceNotification(
            "E2EE Live Location (~2s moving) & BLE Finding Network active"
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    LuogoNotificationHelper.NOTIF_ID_FOREGROUND_SERVICE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(LuogoNotificationHelper.NOTIF_ID_FOREGROUND_SERVICE, notification)
            }
            repo?.hardwareLocationManager?.startLiveTracking()
        } catch (_: Exception) {
            // If started without location permission granted yet, do not crash
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
