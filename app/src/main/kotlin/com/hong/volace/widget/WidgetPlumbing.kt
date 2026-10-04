package com.hong.volace.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hong.volace.audio.ApplyResult
import com.hong.volace.audio.VolumeApplier
import com.hong.volace.data.VolaceDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "VolaceWidget"

/**
 * Widgets are only ever driven from broadcasts, so a single process-wide scope is enough. Each
 * job holds the receiver's [BroadcastReceiver.PendingResult] until it is done.
 */
internal object WidgetScope {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun run(pending: BroadcastReceiver.PendingResult?, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (t: Throwable) {
                Log.e(TAG, "widget work failed", t)
            } finally {
                runCatching { pending?.finish() }
            }
        }
    }
}

/** Redraws every placed Volace widget of every style. */
object WidgetRefresher {

    suspend fun refreshAll(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val placed = WidgetStyle.entries
            .map { style -> style to manager.getAppWidgetIds(ComponentName(app, style.provider)) }
            .filter { (_, ids) -> ids.isNotEmpty() }
        if (placed.isNotEmpty()) {
            val state = WidgetState.load(app)
            placed.forEach { (style, ids) ->
                val views = WidgetRenderer.build(app, style, state)
                ids.forEach { id -> manager.updateAppWidget(id, views) }
            }
        }
        // Last, so a volume change that lands while we were drawing still triggers another pass.
        VolumeWatchJob.sync(app, armed = placed.isNotEmpty())
    }

    /** Fire-and-forget entry point for the in-app UI. */
    fun request(context: Context) {
        val app = context.applicationContext
        WidgetScope.run(null) { refreshAll(app) }
    }
}

/**
 * Asks the launcher to pin one of our widgets. The Pixel launcher's widget *search* does not index
 * side-loaded apps, so offering this from inside the app is the reliable way to place one.
 *
 * @return false when the launcher does not support pinning (the user must use the widget picker).
 */
fun requestPinWidget(context: Context, style: WidgetStyle): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return false
    return manager.requestPinAppWidget(ComponentName(context, style.provider), null, null)
}

/** Receives every widget tap. Applies the profile, then redraws all widgets. */
class VolumeApplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_APPLY && action != ACTION_CYCLE) return

        val app = context.applicationContext
        val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
        val pending = goAsync()

        WidgetScope.run(pending) {
            val dao = VolaceDatabase.get(app).profileDao()
            val target = when (action) {
                ACTION_APPLY -> dao.getById(profileId)
                else -> {
                    val all = dao.getAllOnce()
                    // indexOfFirst returns -1 when nothing is active, which lands on index 0.
                    all.getOrNull((all.indexOfFirst { it.isActive } + 1).mod(all.size.coerceAtLeast(1)))
                }
            }
            if (target != null) {
                val result = VolumeApplier(app).apply(target)
                if (result == ApplyResult.Applied) {
                    dao.applyActive(target.id)
                } else {
                    // No toast: Android drops toasts from a background app that has no notification
                    // permission. The redraw below is what tells the user (see WidgetRenderer).
                    Log.w(TAG, "${target.name} not applied: $result")
                }
            }
            // Also flips the widgets to "open the app" cells when access turned out to be missing.
            WidgetRefresher.refreshAll(app)
        }
    }

    companion object {
        const val ACTION_APPLY = "com.hong.volace.action.APPLY_PROFILE"
        const val ACTION_CYCLE = "com.hong.volace.action.CYCLE_PROFILE"
        const val EXTRA_PROFILE_ID = "com.hong.volace.extra.PROFILE_ID"
    }
}

abstract class VolaceWidgetProvider : AppWidgetProvider() {

    protected abstract val style: WidgetStyle

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val app = context.applicationContext
        val style = style
        val pending = goAsync()
        WidgetScope.run(pending) {
            val views = WidgetRenderer.build(app, style, WidgetState.load(app))
            appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
            // Also the path a widget takes after a reboot or an app update, when the
            // (non-persistable) volume watch is gone.
            VolumeWatchJob.sync(app, armed = true)
        }
    }

    override fun onDisabled(context: Context) {
        // The last widget of this size is gone; stop watching the volume if no other size is left.
        val app = context.applicationContext
        val pending = goAsync()
        WidgetScope.run(pending) { WidgetRefresher.refreshAll(app) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }
}

class Volace1x1Provider : VolaceWidgetProvider() {
    override val style = WidgetStyle.SINGLE
}

class Volace1x4Provider : VolaceWidgetProvider() {
    override val style = WidgetStyle.ROW4
}

class Volace2x4Provider : VolaceWidgetProvider() {
    override val style = WidgetStyle.GRID8
}
