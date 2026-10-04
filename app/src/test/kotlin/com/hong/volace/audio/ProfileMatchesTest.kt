package com.hong.volace.audio

import android.media.AudioManager.RINGER_MODE_NORMAL
import android.media.AudioManager.RINGER_MODE_SILENT
import android.media.AudioManager.RINGER_MODE_VIBRATE
import com.hong.volace.data.Profile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "変更あり": a profile right after it was applied must match, and only a real change may not.
 * The device values here are what Pixel 9a / Android 17 reported after applying (DESIGN.md 8).
 */
class ProfileMatchesTest {

    private val ranges = StreamRanges(
        mapOf(
            VolumeStream.RINGER to 0..7,
            VolumeStream.NOTIFICATION to 0..7,
            VolumeStream.MEDIA to 0..25,
            VolumeStream.ALARM to 1..7,
            VolumeStream.VOICE_CALL to 1..15,
            VolumeStream.SYSTEM to 0..7,
        ),
    )

    private val music = Profile(
        name = "音楽", orderIndex = 0, ringerMode = RINGER_MODE_NORMAL, ringVolume = 4,
        notificationVolume = 4, mediaVolume = 25, alarmVolume = 6, voiceCallVolume = 11, systemVolume = 2,
    )

    private fun device(mode: Int, ring: Int, notification: Int, media: Int, alarm: Int, voice: Int, system: Int) =
        DeviceVolumes(
            mode,
            mapOf(
                VolumeStream.RINGER to ring, VolumeStream.NOTIFICATION to notification,
                VolumeStream.MEDIA to media, VolumeStream.ALARM to alarm,
                VolumeStream.VOICE_CALL to voice, VolumeStream.SYSTEM to system,
            ),
        )

    @Test
    fun justApplied_matches_evenThoughSystemFollowsTheRinger() {
        // SYSTEM reports the ringer's 4, not the profile's 2 (aliased).
        assertTrue(profileMatches(music, device(RINGER_MODE_NORMAL, 4, 4, 25, 6, 11, 4), ranges))
    }

    @Test
    fun volumeKeysChangingMedia_isAChange() {
        assertFalse(profileMatches(music, device(RINGER_MODE_NORMAL, 4, 4, 20, 6, 11, 4), ranges))
    }

    @Test
    fun ringerModeChangedElsewhere_isAChange() {
        assertFalse(profileMatches(music, device(RINGER_MODE_VIBRATE, 0, 0, 25, 6, 11, 0), ranges))
    }

    @Test
    fun vibrateAndSilent_ignoreTheMutedRingerStreams() {
        val manner = music.copy(ringerMode = RINGER_MODE_VIBRATE, ringVolume = 5, notificationVolume = 5)
        // Muted: the device reports 0 for ring / notification whatever the profile says.
        assertTrue(profileMatches(manner, device(RINGER_MODE_VIBRATE, 0, 0, 25, 6, 11, 0), ranges))
        val silent = manner.copy(ringerMode = RINGER_MODE_SILENT)
        assertTrue(profileMatches(silent, device(RINGER_MODE_SILENT, 0, 0, 25, 6, 11, 0), ranges))
    }

    @Test
    fun storedLevelsOutsideTheRange_compareAsWritten() {
        // An alarm of 0 is written as 1 (the device minimum), so a device at 1 matches.
        val quietAlarm = music.copy(alarmVolume = 0)
        assertTrue(profileMatches(quietAlarm, device(RINGER_MODE_NORMAL, 4, 4, 25, 1, 11, 4), ranges))
    }

    @Test
    fun keptStreams_matchWhateverTheDeviceHas() {
        val keepMedia = VolumeStream.MEDIA.keepIn(music, keep = true)
        assertTrue(profileMatches(keepMedia, device(RINGER_MODE_NORMAL, 4, 4, 3, 6, 11, 4), ranges))
        assertFalse(profileMatches(keepMedia, device(RINGER_MODE_NORMAL, 4, 4, 3, 2, 11, 4), ranges))
    }

    private val applied = device(RINGER_MODE_NORMAL, 4, 4, 25, 6, 11, 4)

    @Test
    fun dnd_volaceModeMustBeTheProfiles() {
        val priority = music.copy(dndMode = DndMode.PRIORITY.value)
        assertTrue(profileMatches(priority, applied.copy(dndActive = true, volaceDnd = DndMode.PRIORITY.value), ranges))
        // Turned off from the system's Modes, or another of Volace's modes on instead.
        assertFalse(profileMatches(priority, applied, ranges))
        assertFalse(profileMatches(priority, applied.copy(dndActive = true, volaceDnd = DndMode.ALARMS.value), ranges))
        assertFalse(profileMatches(music, applied.copy(dndActive = true, volaceDnd = DndMode.PRIORITY.value), ranges))
    }

    @Test
    fun dnd_anotherModeHidesTheRingerAndItsStreams() {
        // Bedtime on: the ringer reads silent and the ringer streams muted; Volace left them alone.
        val underBedtime = device(RINGER_MODE_SILENT, 0, 0, 25, 6, 11, 0).copy(dndActive = true)
        assertTrue(profileMatches(music, underBedtime, ranges))
        // What Volace does write is still compared.
        assertFalse(profileMatches(music, underBedtime.copy(levels = underBedtime.levels + (VolumeStream.MEDIA to 3)), ranges))
    }

    @Test
    fun dnd_off_theRingerCountsAgain() {
        assertFalse(profileMatches(music, applied.copy(ringerMode = RINGER_MODE_VIBRATE), ranges))
    }
}
