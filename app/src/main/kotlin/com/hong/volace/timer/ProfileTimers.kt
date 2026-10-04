package com.hong.volace.timer

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hong.volace.R
import com.hong.volace.audio.ApplyResult
import com.hong.volace.audio.ProfileSwitcher
import com.hong.volace.audio.SoundKind
import com.hong.volace.audio.SwitchSource
import com.hong.volace.audio.Sounds
import com.hong.volace.audio.valueOf
import com.hong.volace.audio.VolumeApplier
import com.hong.volace.data.Profile
import com.hong.volace.data.ProfileDao
import com.hong.volace.schedule.Schedules
import kotlinx.coroutines.flow.Flow

private const val TAG = "VolaceTimer"

/**
 * Timed profiles: apply one until a given time, then go back (DESIGN.md 5.14).
 *
 * Android 17 ignores volume changes from an app in the background unless it runs a foreground
 * service (DESIGN.md 8.7). [start] is called from a visible activity (the list, the picker) and
 * starts [TimerService] there, which shows the timer until the end; the alarm at the end runs
 * [expire] in that service too (starting it again if it is gone, e.g. after a reboot). When going
 * back still fails, the timer stays stored as due and a notification asks for a tap.
 */
object ProfileTimers {

    const val DEFAULT_EXTENSION_MS = 30 * 60_000L

    fun current(context: Context): ProfileTimer? = TimerStore.load(context)

    /** [current] now and after every change. */
    fun observe(context: Context): Flow<ProfileTimer?> = TimerStore.observe(context)

    /**
     * Applies the profile with [profileId] until [endAt], then [restoreId] (null: the state from
     * before). Call from a visible activity only (see the class comment). Null when the profile is
     * gone; nothing is scheduled unless Android accepted the profile.
     */
    suspend fun start(context: Context, profileId: Long, endAt: Long, restoreId: Long?): ProfileSwitcher.Outcome? =
        ProfileSwitcher.exclusive(context) { app, dao ->
            val target = dao.getById(profileId) ?: return@exclusive null
            val applier = VolumeApplier(app)
            // A timer started on top of another keeps the state from before the first one:
            // "適用前の状態に戻す" should not land on the first timer's profile.
            val running = TimerStore.load(app)
            val previous = running?.previous ?: applier.snapshot()
            val previousActiveId = if (running != null) running.previousActiveId else dao.getAllOnce().firstOrNull { it.isActive }?.id
            // The sounds this profile changes, as they are before it (or before the first timer).
            val previousSounds = running?.previousSounds.orEmpty() + SoundKind.entries
                .filter { it.valueOf(target) != null && running?.previousSounds?.containsKey(it) != true }
                .associateWith { Sounds.current(app, it) }
            val restoreTo = restoreId?.takeIf { it != target.id }?.let { dao.getById(it) }
            val result = applier.apply(target)
            if (result == ApplyResult.Applied) {
                dao.applyActive(target.id)
                TimerStore.save(
                    app,
                    ProfileTimer(
                        profileId = target.id,
                        endAt = endAt,
                        restoreId = restoreTo?.id,
                        previous = previous,
                        previousActiveId = previousActiveId,
                        profileName = target.name,
                        restoreName = restoreTo?.name ?: app.getString(R.string.timer_previous_state),
                        previousSounds = previousSounds,
                    ),
                )
                TimerAlarm.schedule(app, endAt)
                TimerNotifications.cancelDue(app)
                TimerService.start(app)
                Schedules.noteManualChoice(app, target.id)
            }
            ProfileSwitcher.Outcome(target, result)
        }

    /** Moves the end [byMs] later (from now when it already passed). False when there is no timer. */
    suspend fun extend(context: Context, byMs: Long = DEFAULT_EXTENSION_MS): Boolean =
        ProfileSwitcher.exclusive(context) { app, _ ->
            val timer = TimerStore.load(app) ?: return@exclusive false
            val endAt = maxOf(timer.endAt, System.currentTimeMillis()) + byMs
            TimerStore.save(app, timer.copy(endAt = endAt))
            TimerAlarm.schedule(app, endAt)
            TimerService.refresh(app)
            true
        }

    /**
     * Goes back now (the "今すぐ戻す" button, or a tap on the "time is up" notification). Call from a
     * visible activity or while [TimerService] runs. Null when there was no timer.
     */
    suspend fun restore(context: Context): RestoreOutcome? =
        ProfileSwitcher.exclusive(context) { app, dao -> restoreLocked(app, dao, SwitchSource.USER) }

    /** The alarm went off. Goes back, or leaves the timer due and asks the user to. */
    suspend fun expire(context: Context) {
        val outcome = ProfileSwitcher.exclusive(context) { app, dao ->
            val timer = TimerStore.load(app) ?: return@exclusive null
            // A stale alarm (the timer was extended, or the clock moved): wait for the real end.
            if (!timer.isDue(System.currentTimeMillis())) {
                TimerAlarm.schedule(app, timer.endAt)
                return@exclusive null
            }
            restoreLocked(app, dao, SwitchSource.SCHEDULE)
        } ?: return
        if (!outcome.restored) {
            Log.w(TAG, "could not restore ${outcome.name}: ${outcome.result}")
            TimerNotifications.showDue(context, outcome.name)
        }
        // Either way the service has nothing more to wait for.
        TimerService.stop(context)
    }

    /**
     * After a reboot or an app update: alarms are gone and so is the service. Re-arms the alarm, or
     * handles an end that passed while the phone was off.
     */
    suspend fun resume(context: Context) {
        val timer = current(context) ?: return
        if (timer.isDue(System.currentTimeMillis())) {
            // Going back needs the service, which the alarm may start (a receiver may not change
            // the volume on Android 17): an alarm due right away.
            TimerAlarm.schedule(context, System.currentTimeMillis())
        } else {
            TimerAlarm.schedule(context, timer.endAt)
            TimerService.start(context)
        }
    }

    /**
     * The schedule reached a boundary while the timer runs (DESIGN.md 5.15): the timer was chosen by
     * hand, so it runs to its end, and then switches to [profile] instead of going back.
     */
    internal fun handOverLocked(context: Context, profile: Profile) {
        val timer = TimerStore.load(context) ?: return
        TimerStore.save(context, timer.copy(restoreId = profile.id, restoreName = profile.name))
        TimerService.refresh(context)
    }

    /** Forgets the timer and keeps the volumes as they are (a profile was chosen by hand). */
    internal fun dropLocked(context: Context) {
        if (TimerStore.load(context) == null) return
        TimerStore.clear(context)
        TimerAlarm.cancel(context)
        TimerService.stop(context)
        TimerNotifications.cancelDue(context)
    }

    private suspend fun restoreLocked(app: Context, dao: ProfileDao, source: SwitchSource): RestoreOutcome? {
        val timer = TimerStore.load(app) ?: return null
        val applier = VolumeApplier(app)
        val profile: Profile? = timer.restoreId?.let { dao.getById(it) }
        val target = profile ?: timer.previousProfile(app.getString(R.string.timer_previous_state))
        val result = applier.apply(target, source)
        // Android 17 ignores a change it does not allow without saying so: read it back.
        val restored = result == ApplyResult.Applied && applier.matches(target, applier.snapshot())
        if (restored) {
            when {
                profile != null -> dao.applyActive(profile.id)
                timer.previousActiveId != null -> dao.applyActive(timer.previousActiveId)
                else -> dao.clearActive()
            }
            TimerStore.clear(app)
            TimerAlarm.cancel(app)
            TimerNotifications.cancelDue(app)
            TimerService.stop(app)
        }
        return RestoreOutcome(target.name, restored, result)
    }

    data class RestoreOutcome(val name: String, val restored: Boolean, val result: ApplyResult) {
        fun message(context: Context): String = when {
            restored -> context.getString(R.string.timer_restored, name)
            result == ApplyResult.NeedsAccess -> context.getString(R.string.apply_needs_access)
            else -> context.getString(R.string.timer_restore_failed, name)
        }
    }
}

/** The one alarm that ends the timer. */
internal object TimerAlarm {

    @SuppressLint("MissingPermission")
    fun schedule(context: Context, at: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pendingIntent(context)
        // USE_EXACT_ALARM is granted at install (lint only knows SCHEDULE_EXACT_ALARM); the check
        // only matters if that ever changes, and then the timer ends a little late, not never.
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_EXPIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
