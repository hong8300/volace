package com.hong.volace.tile

import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.hong.volace.R
import com.hong.volace.data.icon
import com.hong.volace.widget.WidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings tile: shows the profile in effect and, when tapped, opens [ProfilePickerActivity]
 * to choose one. Applying happens in that visible activity rather than in [onClick]: Android 17
 * only lets a visible activity (or a qualifying foreground service) change volumes, and it does not
 * say whether a tile click counts as a user interaction the way a widget tap does (DESIGN.md 8.7).
 */
class ProfileTileService : TileService() {

    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { scope = it }
        scope.launch {
            val state = withContext(Dispatchers.IO) { WidgetState.load(applicationContext) }
            render(state)
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
    }

    override fun onClick() {
        if (isLocked) unlockAndRun { openPicker() } else openPicker()
    }

    private fun render(state: WidgetState) {
        val tile = qsTile ?: return
        val active = state.active
        tile.label = active?.name ?: getString(R.string.app_name)
        tile.subtitle = when {
            !state.hasAccess -> "許可が必要"
            active == null -> "タップして選ぶ"
            state.drifted -> "変更あり"
            else -> getString(R.string.app_name)
        }
        tile.icon = Icon.createWithResource(this, active?.icon?.res ?: R.drawable.ic_profile_volume_up)
        // "On" means a profile is in effect as applied; drifted or nothing applied reads as off.
        tile.state = if (active != null && !state.drifted && state.hasAccess) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun openPicker() {
        val intent = Intent(this, ProfilePickerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            // API 33 only has the Intent overload (it throws from API 34 on).
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** Asks System UI to add the tile; it shows its own confirmation. */
        fun requestAdd(context: Context) {
            val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return
            statusBar.requestAddTileService(
                ComponentName(context, ProfileTileService::class.java),
                context.getString(R.string.app_name),
                Icon.createWithResource(context, R.drawable.ic_profile_volume_up),
                context.mainExecutor,
            ) { /* added, already there, or declined: nothing more to do */ }
        }
    }
}
