package com.hong.volace.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hong.volace.audio.ApplyResult
import com.hong.volace.audio.ProfileSwitcher
import com.hong.volace.shortcut.ProfileShortcuts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /**
     * One redraw at a time. Each pass reads the state once it holds the lock, so passes finish in
     * order and the last one on screen is never older than an earlier one.
     */
    private val drawLock = Mutex()

    suspend fun refreshAll(context: Context) = drawLock.withLock {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val placed = WidgetStyle.entries
            .map { style -> style to manager.getAppWidgetIds(ComponentName(app, style.provider)) }
            .filter { (_, ids) -> ids.isNotEmpty() }
        try {
            if (placed.isNotEmpty()) {
                val state = WidgetState.load(app)
                placed.forEach { (style, ids) ->
                    val views = WidgetRenderer.build(app, style, state)
                    ids.forEach { id -> manager.updateAppWidget(id, views) }
                }
            }
            // Every profile change comes through here; the shortcuts only update when they differ.
            ProfileShortcuts.sync(app)
        } finally {
            // Last, so a volume change that lands while we were drawing still triggers another
            // pass; and in finally, so one failed redraw does not end the watch for good (the job
            // fires once and nothing else would re-arm it until the next tap).
            VolumeWatchJob.sync(app, armed = placed.isNotEmpty())
        }
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
            val outcome = when (action) {
                ACTION_APPLY -> ProfileSwitcher.apply(app, profileId)
                else -> ProfileSwitcher.cycle(app)
            }
            if (outcome != null && outcome.result != ApplyResult.Applied) {
                // No toast: Android drops toasts from a background app that has no notification
                // permission. The redraw below is what tells the user (see WidgetRenderer).
                Log.w(TAG, "${outcome.profile.name} not applied: ${outcome.result}")
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
