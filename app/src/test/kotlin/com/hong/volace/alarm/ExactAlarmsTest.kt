package com.hong.volace.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.hong.volace.audio.DeviceVolumes
import com.hong.volace.audio.VolumeStream
import com.hong.volace.timer.ProfileTimer
import com.hong.volace.timer.ProfileTimers
import com.hong.volace.timer.TimerStore
import com.hong.volace.timer.TimerReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/** Exact when "アラームとリマインダー" is allowed, a late-tolerant alarm otherwise; never none. */
@RunWith(RobolectricTestRunner::class)
class ExactAlarmsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    private fun operation() = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_EXPIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @Before
    fun clear() {
        alarms.cancel(operation())
    }

    @Test
    fun allowed_sets_an_exact_alarm() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        assertTrue(ExactAlarms.isAllowed(context))
        assertTrue(ExactAlarms.set(context, 1_000_000L, operation()))

        val alarm = shadowOf(alarms).peekNextScheduledAlarm()!!
        assertEquals(1_000_000L, alarm.triggerAtMs)
        assertEquals(0L, alarm.windowLengthMs)
    }

    @Test
    fun not_allowed_still_sets_an_alarm_that_is_not_exact() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        assertFalse(ExactAlarms.isAllowed(context))
        assertFalse(ExactAlarms.set(context, 1_000_000L, operation()))

        // The alarm exists (the timer ends late, not never), but is not an exact one.
        val alarm = shadowOf(alarms).peekNextScheduledAlarm()!!
        assertEquals(1_000_000L, alarm.triggerAtMs)
        assertTrue(alarm.windowLengthMs != 0L)
    }

    @Test
    fun setting_again_replaces_the_alarm() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        ExactAlarms.set(context, 1_000_000L, operation())
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        ExactAlarms.set(context, 2_000_000L, operation())

        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        assertEquals(2_000_000L, shadowOf(alarms).peekNextScheduledAlarm()!!.triggerAtMs)
    }

    @Test
    fun settings_page_is_this_apps() {
        val intent = ExactAlarms.settingsIntent(context)

        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
    }

    @Test
    fun without_a_timer_nothing_is_armed() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        runBlocking { ProfileTimers.resume(context) }

        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test
    fun a_running_timer_gets_an_exact_alarm_once_allowed() {
        val endAt = System.currentTimeMillis() + 60 * 60_000L
        TimerStore.save(
            context,
            ProfileTimer(
                profileId = 1,
                endAt = endAt,
                restoreId = null,
                previous = DeviceVolumes(AudioManager.RINGER_MODE_NORMAL, mapOf(VolumeStream.MEDIA to 8)),
                previousActiveId = null,
                profileName = "サイレント",
                restoreName = "適用前の状態",
            ),
        )

        // The alarm was set without the permission; the user then allows it (the receiver's job).
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        runBlocking { ProfileTimers.resume(context) }
        assertTrue(shadowOf(alarms).peekNextScheduledAlarm()!!.windowLengthMs != 0L)

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        runBlocking { ProfileTimers.resume(context) }
        val alarm = shadowOf(alarms).peekNextScheduledAlarm()!!
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        assertEquals(endAt, alarm.triggerAtMs)
        assertEquals(0L, alarm.windowLengthMs)

        TimerStore.clear(context)
    }
}
