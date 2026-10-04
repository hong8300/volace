package com.hong.volace.schedule

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.text.format.DateFormat
import android.util.Log
import androidx.core.content.edit
import com.hong.volace.MainActivity
import com.hong.volace.R
import com.hong.volace.audio.ApplyResult
import com.hong.volace.audio.ProfileSwitcher
import com.hong.volace.audio.VolumeApplier
import com.hong.volace.data.ScheduleRule
import com.hong.volace.data.ScheduleSkip
import com.hong.volace.data.VolaceDatabase
import com.hong.volace.timer.ProfileTimers
import com.hong.volace.timer.TimerStore
import com.hong.volace.widget.WidgetRefresher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject
import java.util.Date

private const val TAG = "VolaceSchedule"

/**
 * The schedule: switches profiles at the times of the rules (DESIGN.md 5.15).
 *
 * Each boundary is applied once, when it comes; whatever is chosen by hand in between stays until
 * the next one. Android 17 ignores volume changes from an app in the background unless it runs a
 * foreground service (DESIGN.md 8.7), so the exact alarm starts [ScheduleService] (an exact alarm
 * may start one from the background) and the switch happens in there. Android does not say when
 * it ignores a change, so the result is read back; when it did not stick, a notification offers
 * to switch on a tap instead (a tap is a user action, which may always change the volume).
 */
object Schedules {

    fun observeStatus(context: Context): Flow<ScheduleStatus?> = ScheduleStore.observe(context)

    /** The alarm went off: applies the boundary that came, then arms the next one. */
    suspend fun run(context: Context) {
        val app = context.applicationContext
        val dao = VolaceDatabase.get(app).scheduleDao()
        val rules = dao.rulesOnce()
        val skips = dao.skipsOnce()
        val now = System.currentTimeMillis()
        val due = ScheduleCalc.latestDue(rules, skips, ScheduleStore.handledUntil(app) ?: now, now)
        if (due != null) switchTo(app, due)
        ScheduleStore.setHandledUntil(app, now)
        arm(app, rules, skips, now)
    }

    /**
     * After a reboot, an app update, a clock or time-zone change, or when the app opens: alarms may
     * be gone or set for the wrong moment. A boundary missed in the meantime (the phone was off) is
     * caught up through an alarm due right away, since only the alarm may start the service.
     */
    suspend fun reschedule(context: Context) {
        val app = context.applicationContext
        val dao = VolaceDatabase.get(app).scheduleDao()
        val rules = dao.rulesOnce()
        val skips = dao.skipsOnce()
        val now = System.currentTimeMillis()
        val handled = ScheduleStore.handledUntil(app)
        // The clock was set back: boundaries from now on come (again) and must not count as done.
        if (handled == null || handled > now) ScheduleStore.setHandledUntil(app, now)
        if (handled != null && ScheduleCalc.latestDue(rules, skips, handled, now) != null) {
            ScheduleAlarm.set(app, now)
        } else {
            arm(app, rules, skips, now)
        }
    }

    /** Rules or days off were edited: only boundaries from now on count. */
    suspend fun onRulesChanged(context: Context) {
        val app = context.applicationContext
        val dao = VolaceDatabase.get(app).scheduleDao()
        val now = System.currentTimeMillis()
        ScheduleStore.setHandledUntil(app, now)
        arm(app, dao.rulesOnce(), dao.skipsOnce(), now)
    }

    /**
     * A profile was chosen by hand (anywhere, the "tap to switch" notification included). It has
     * priority: boundaries up to now count as done, so a catch-up after the alarm was lost (a
     * force stop) does not undo it, and a failed switch is no longer pending.
     */
    internal fun noteManualChoice(context: Context, profileId: Long) {
        val now = System.currentTimeMillis()
        val handled = ScheduleStore.handledUntil(context)
        if (handled == null || handled < now) ScheduleStore.setHandledUntil(context, now)
        val status = ScheduleStore.status(context) ?: return
        if (status.outcome != ScheduleStatus.Outcome.FAILED && status.outcome != ScheduleStatus.Outcome.NEEDS_ACCESS) return
        val outcome = if (status.profileId == profileId) ScheduleStatus.Outcome.BY_HAND else ScheduleStatus.Outcome.OVERRIDDEN
        ScheduleStore.saveStatus(context, status.copy(outcome = outcome))
        ScheduleNotifications.cancelFailed(context)
    }

    private fun arm(app: Context, rules: List<ScheduleRule>, skips: List<ScheduleSkip>, now: Long) {
        val next = ScheduleCalc.next(rules, skips, now)
        if (next == null) ScheduleAlarm.cancel(app) else ScheduleAlarm.set(app, next.at)
    }

    private suspend fun switchTo(app: Context, due: Boundary) {
        val status = ProfileSwitcher.exclusive(app) { _, profiles ->
            val profile = profiles.getById(due.profileId)
                ?: return@exclusive ScheduleStatus(due.at, due.profileId, "", ScheduleStatus.Outcome.PROFILE_GONE)
            fun status(outcome: ScheduleStatus.Outcome) = ScheduleStatus(due.at, profile.id, profile.name, outcome)

            // A timed profile was chosen by hand: it runs to its end, and ends in the profile
            // the schedule now calls for instead of going back to what came before it.
            val timer = TimerStore.load(app)
            if (timer != null && !timer.isDue(System.currentTimeMillis())) {
                ProfileTimers.handOverLocked(app, profile)
                return@exclusive status(ScheduleStatus.Outcome.AFTER_TIMER)
            }

            val applier = VolumeApplier(app)
            val result = applier.apply(profile)
            // Android 17 ignores a change it does not allow without saying so: read it back.
            val stuck = result == ApplyResult.Applied && applier.matches(profile, applier.snapshot())
            if (stuck) {
                profiles.applyActive(profile.id)
                // A timer whose end is still waiting for a tap is overtaken by the schedule.
                ProfileTimers.dropLocked(app)
            }
            status(
                when {
                    stuck -> ScheduleStatus.Outcome.APPLIED
                    result == ApplyResult.NeedsAccess -> ScheduleStatus.Outcome.NEEDS_ACCESS
                    else -> ScheduleStatus.Outcome.FAILED
                },
            )
        }
        ScheduleStore.saveStatus(app, status)
        if (status.outcome.failed) {
            Log.w(TAG, "could not switch to ${status.profileName}: ${status.outcome}")
            ScheduleNotifications.showFailed(app, status)
        } else {
            ScheduleNotifications.cancelFailed(app)
        }
        WidgetRefresher.refreshAll(app)
    }
}

/** What the schedule did at its last boundary, for the screens. */
data class ScheduleStatus(
    /** The boundary's time. */
    val at: Long,
    val profileId: Long,
    /** As it was then (empty when the profile was gone). */
    val profileName: String,
    val outcome: Outcome,
) {
    enum class Outcome(val failed: Boolean) {
        APPLIED(false),

        /** Failed, then switched to by hand (e.g. from the "tap to switch" notification). */
        BY_HAND(false),

        /** Failed, then another profile was chosen by hand, which has priority. */
        OVERRIDDEN(false),

        /** A timed profile was running; it switches to this one when it ends. */
        AFTER_TIMER(false),

        /** Android ignored the change; the notification offers it on a tap. */
        FAILED(true),

        /** "Do Not Disturb" access was revoked. */
        NEEDS_ACCESS(true),

        /** The rule's profile was deleted. */
        PROFILE_GONE(true),
    }

    fun toJson(): String = JSONObject()
        .put("at", at)
        .put("profileId", profileId)
        .put("profileName", profileName)
        .put("outcome", outcome.name)
        .toString()

    companion object {
        fun fromJson(text: String): ScheduleStatus? = runCatching {
            val json = JSONObject(text)
            ScheduleStatus(
                at = json.getLong("at"),
                profileId = json.getLong("profileId"),
                profileName = json.optString("profileName"),
                outcome = Outcome.valueOf(json.getString("outcome")),
            )
        }.getOrNull()
    }
}

/** Device-local state of the schedule (volace_device.xml is excluded from backups). */
internal object ScheduleStore {

    private const val PREFS = "volace_device"
    private const val KEY_HANDLED = "schedule_handled_until"
    private const val KEY_STATUS = "schedule_status"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Boundaries up to this time (ms) have been dealt with; null before the schedule ever ran. */
    fun handledUntil(context: Context): Long? =
        prefs(context).getLong(KEY_HANDLED, -1L).takeIf { it >= 0 }

    // Committed at once (not apply()): the service stops right after, and the process with it.
    fun setHandledUntil(context: Context, at: Long) {
        prefs(context).edit(commit = true) { putLong(KEY_HANDLED, at) }
    }

    fun status(context: Context): ScheduleStatus? =
        prefs(context).getString(KEY_STATUS, null)?.let(ScheduleStatus::fromJson)

    fun saveStatus(context: Context, status: ScheduleStatus) {
        prefs(context).edit(commit = true) { putString(KEY_STATUS, status.toJson()) }
    }

    fun observe(context: Context): Flow<ScheduleStatus?> = callbackFlow {
        val prefs = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_STATUS) trySend(status(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(status(context))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

/** The one alarm, at the next boundary. */
internal object ScheduleAlarm {

    @SuppressLint("MissingPermission")
    fun set(context: Context, at: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        // USE_EXACT_ALARM is granted at install (lint only knows SCHEDULE_EXACT_ALARM). Without an
        // exact alarm the service may not start from the background, so the switch would fall
        // back to the notification.
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(context))
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ScheduleReceiver::class.java).setAction(ScheduleReceiver.ACTION_BOUNDARY),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

internal object ScheduleNotifications {

    const val RUNNING_ID = 1101
    private const val FAILED_ID = 1102
    private const val CHANNEL_RUNNING = "schedule"
    private const val CHANNEL_FAILED = "schedule_failed"

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun ensureChannels(context: Context) {
        manager(context)?.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_RUNNING,
                    context.getString(R.string.schedule_channel_running),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CHANNEL_FAILED,
                    context.getString(R.string.schedule_channel_failed),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            ),
        )
    }

    /** What the service shows for the second or so it runs (Android usually does not get to show it). */
    fun running(context: Context): Notification {
        ensureChannels(context)
        return Notification.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_schedule)
            .setContentTitle(context.getString(R.string.schedule_running))
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
    }

    fun showFailed(context: Context, status: ScheduleStatus) {
        ensureChannels(context)
        val time = DateFormat.getTimeFormat(context).format(Date(status.at))
        val (title, text, tap) = when (status.outcome) {
            ScheduleStatus.Outcome.NEEDS_ACCESS -> Triple(
                context.getString(R.string.schedule_failed_title, status.profileName),
                context.getString(R.string.schedule_failed_access),
                openApp(context),
            )
            ScheduleStatus.Outcome.PROFILE_GONE -> Triple(
                context.getString(R.string.schedule_gone_title),
                context.getString(R.string.schedule_gone_text, time),
                openApp(context),
            )
            else -> Triple(
                context.getString(R.string.schedule_failed_title, status.profileName),
                context.getString(R.string.schedule_failed_text, time),
                switchNow(context, status.profileId),
            )
        }
        val notification = Notification.Builder(context, CHANNEL_FAILED)
            .setSmallIcon(R.drawable.ic_schedule)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        // Without the notification permission this shows nothing; the list still shows the failure.
        runCatching { manager(context)?.notify(FAILED_ID, notification) }
    }

    fun cancelFailed(context: Context) {
        manager(context)?.cancel(FAILED_ID)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, MainActivity.launcherIntent(context), PendingIntent.FLAG_IMMUTABLE,
    )

    // An activity, not a broadcast: a visible activity may always change the volume.
    private fun switchNow(context: Context, profileId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        2,
        Intent(context, ScheduleActionActivity::class.java)
            .putExtra(ScheduleActionActivity.EXTRA_PROFILE_ID, profileId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
