package com.hong.volace.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.hong.volace.schedule.Schedules
import com.hong.volace.timer.ProfileTimers
import com.hong.volace.widget.WidgetScope

private const val TAG = "VolaceAlarm"

/**
 * The alarms behind timed profiles, the schedule and Bluetooth (DESIGN.md 5.14, 5.15, 5.18).
 *
 * Android 17 lets an app change volumes from the background only inside a foreground service, and
 * only an exact alarm may start one from there (DESIGN.md 8.7). The exact alarm needs
 * SCHEDULE_EXACT_ALARM, which Android 14+ does not grant at install: the user allows
 * "アラームとリマインダー" in Settings, and may take it away again (the app is then stopped and its
 * alarms are gone).
 *
 * Without it an alarm is still set, but not exact: it may come late, and the service may not be
 * allowed to start, so the switch falls back to the "tap to switch" notification. The screens say so.
 */
object ExactAlarms {

    /** Whether an exact alarm may be set right now. */
    fun isAllowed(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    /** Opens the "アラームとリマインダー" page of this app in Settings. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Sets [operation] for [at]: exact when allowed, else an alarm that may come late. Returns
     * whether it is exact. The permission can go between the check and the call.
     */
    fun set(context: Context, at: Long, operation: PendingIntent): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        if (alarms.canScheduleExactAlarms()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
                return true
            } catch (e: SecurityException) {
                Log.w(TAG, "exact alarm refused, setting an inexact one", e)
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        return false
    }
}

/**
 * The user allowed "アラームとリマインダー" again: the alarms set without it are not exact. Sets them
 * again. (Taking it away stops the app and removes its alarms without a broadcast; the app
 * does the same when it opens, and after a reboot.)
 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        val app = context.applicationContext
        Log.i(TAG, "alarms and reminders: allowed=${ExactAlarms.isAllowed(app)}, setting the alarms again")
        WidgetScope.run(goAsync()) {
            ProfileTimers.resume(app)
            Schedules.reschedule(app)
        }
    }
}
