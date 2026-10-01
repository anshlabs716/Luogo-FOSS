package app.luogo.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.luogo.app.LuogoApplication

/**
 * Recovers location tracking and BLE finding state after device reboot when user has enabled sharing.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val repo = runCatching { LuogoApplication.getRepository(context) }.getOrNull() ?: return
            if (repo.userProfile.value.sharingEnabled && repo.hardwareLocationManager.hasCoarseLocationPermission()) {
                repo.hardwareLocationManager.startLiveTracking()
            }
        }
    }
}
