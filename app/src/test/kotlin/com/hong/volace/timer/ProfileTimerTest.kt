package com.hong.volace.timer

import android.media.AudioManager
import com.hong.volace.audio.DeviceVolumes
import com.hong.volace.audio.SoundKind
import com.hong.volace.audio.VolumeStream
import com.hong.volace.audio.isKeptBy
import com.hong.volace.audio.valueOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

/** Robolectric for the real org.json (android.jar only has stubs). */
@RunWith(RobolectricTestRunner::class)
class ProfileTimerTest {

    private val levels = mapOf(
        VolumeStream.RINGER to 0,
        VolumeStream.NOTIFICATION to 0,
        VolumeStream.MEDIA to 8,
        VolumeStream.ALARM to 4,
        VolumeStream.VOICE_CALL to 9,
        VolumeStream.SYSTEM to 0,
    )

    private val timer = ProfileTimer(
        profileId = 11,
        endAt = 1_791_084_074_655,
        restoreId = null,
        previous = DeviceVolumes(AudioManager.RINGER_MODE_SILENT, levels),
        previousActiveId = 9,
        profileName = "通常",
        restoreName = "適用前の状態",
    )

    @Test
    fun json_roundTrip() {
        assertEquals(timer, ProfileTimer.fromJson(timer.toJson()))
        val toProfile = timer.copy(restoreId = 10, previousActiveId = null)
        assertEquals(toProfile, ProfileTimer.fromJson(toProfile.toJson()))
    }

    @Test
    fun json_keepsTheSoundsFromBefore() {
        val withSounds = timer.copy(
            previousSounds = mapOf(SoundKind.RINGTONE to "content://media/internal/audio/media/42", SoundKind.ALARM to SoundKind.SILENT),
        )
        assertEquals(withSounds, ProfileTimer.fromJson(withSounds.toJson()))
    }

    @Test
    fun json_savedBeforeSoundsExistedHasNone() {
        val old = JSONObject(timer.toJson()).apply { remove("sounds") }.toString()
        assertEquals(emptyMap<SoundKind, String>(), ProfileTimer.fromJson(old)?.previousSounds)
    }

    @Test
    fun previousProfile_writesBackOnlyTheSoundsTheTimerChanged() {
        val restore = timer.copy(previousSounds = mapOf(SoundKind.NOTIFICATION to "content://x/1")).previousProfile("前")
        assertEquals("content://x/1", restore.notificationSoundUri)
        assertNull(restore.ringtoneUri)
        assertNull(restore.alarmSoundUri)
    }

    @Test
    fun json_brokenRecordIsNoTimer() {
        assertNull(ProfileTimer.fromJson("{"))
        assertNull(ProfileTimer.fromJson("""{"profileId": 1}"""))
    }

    @Test
    fun isDue_fromTheEndOn() {
        assertFalse(timer.isDue(timer.endAt - 1))
        assertTrue(timer.isDue(timer.endAt))
    }

    @Test
    fun asProfile_silentLeavesMutedStreamsAlone() {
        val profile = DeviceVolumes(AudioManager.RINGER_MODE_SILENT, levels).asProfile("前")

        assertEquals(AudioManager.RINGER_MODE_SILENT, profile.ringerMode)
        // Muted streams read 0; writing that would lose the level the ringer returns to.
        assertTrue(VolumeStream.RINGER.isKeptBy(profile))
        assertTrue(VolumeStream.NOTIFICATION.isKeptBy(profile))
        assertTrue(VolumeStream.SYSTEM.isKeptBy(profile))
        assertFalse(VolumeStream.MEDIA.isKeptBy(profile))
        assertEquals(8, VolumeStream.MEDIA.valueOf(profile))
        assertEquals(4, VolumeStream.ALARM.valueOf(profile))
        assertEquals(9, VolumeStream.VOICE_CALL.valueOf(profile))
    }

    @Test
    fun asProfile_normalWritesEverything() {
        val normal = levels + mapOf(VolumeStream.RINGER to 5, VolumeStream.NOTIFICATION to 3, VolumeStream.SYSTEM to 5)
        val profile = DeviceVolumes(AudioManager.RINGER_MODE_NORMAL, normal).asProfile("前")

        assertEquals(0, profile.keepMask)
        VolumeStream.entries.forEach { assertEquals(normal[it], it.valueOf(profile)) }
    }

    @Test
    fun nextOccurrence_todayIfAheadElseTomorrow() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 4, 12, 20, 30)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val later = Calendar.getInstance().apply { timeInMillis = nextOccurrence(now, 15, 0) }
        assertEquals(4, later.get(Calendar.DAY_OF_MONTH))
        assertEquals(15, later.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, later.get(Calendar.SECOND))

        val earlier = Calendar.getInstance().apply { timeInMillis = nextOccurrence(now, 7, 0) }
        assertEquals(5, earlier.get(Calendar.DAY_OF_MONTH))
        assertEquals(7, earlier.get(Calendar.HOUR_OF_DAY))

        // The current minute has already begun: it means tomorrow, not "now".
        val sameMinute = Calendar.getInstance().apply { timeInMillis = nextOccurrence(now, 12, 20) }
        assertEquals(5, sameMinute.get(Calendar.DAY_OF_MONTH))
    }
}
