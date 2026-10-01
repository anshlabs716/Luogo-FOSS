package app.luogo.app.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import app.luogo.app.LuogoApplication

/**
 * Android Quick Settings Tile allowing one-tap toggle of E2EE Live Location Sharing.
 */
@RequiresApi(Build.VERSION_CODES.N)
class LuogoQuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        val repo = runCatching { LuogoApplication.getRepository(this) }.getOrNull() ?: return
        val current = repo.userProfile.value
        val nextSharing = !current.sharingEnabled
        repo.updateUserProfile(current.displayName, current.colorArgb, sharingEnabled = nextSharing)
        if (nextSharing) {
            repo.hardwareLocationManager.startLiveTracking()
        } else {
            repo.hardwareLocationManager.stopLiveTracking()
        }
        syncTileState()
    }

    private fun syncTileState() {
        val tile = qsTile ?: return
        val repo = runCatching { LuogoApplication.getRepository(this) }.getOrNull()
        val active = repo?.userProfile?.value?.sharingEnabled ?: true
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (active) "Luogo Sharing ON" else "Luogo Paused"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (active) "E2EE Live (~2s)" else "Tap to resume"
        }
        tile.updateTile()
    }
}
