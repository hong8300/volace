package com.hong.volace.audio

import android.media.AudioManager
import com.hong.volace.data.Profile
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pixel 9 / Android 17 ranges (DESIGN.md 8.5): call and alarm start at 1, media goes to 25. */
class StreamRangesTest {

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

    private fun profile(ringerMode: Int, ring: Int, alarm: Int, voice: Int, media: Int = 10) = Profile(
        name = "p",
        orderIndex = 0,
        ringerMode = ringerMode,
        ringVolume = ring,
        notificationVolume = 0,
        mediaVolume = media,
        alarmVolume = alarm,
        voiceCallVolume = voice,
        systemVolume = 0,
    )

    @Test
    fun streamsWithAFloorOfOne_neverNormaliseToZero() {
        val p = ranges.normalize(profile(AudioManager.RINGER_MODE_VIBRATE, ring = 0, alarm = 0, voice = 0))
        assertEquals(1, p.alarmVolume)
        assertEquals(1, p.voiceCallVolume)
    }

    @Test
    fun audibleRinger_startsAtOne_otherModesMayBeZero() {
        assertEquals(1..7, ranges.of(VolumeStream.RINGER, AudioManager.RINGER_MODE_NORMAL))
        assertEquals(0..7, ranges.of(VolumeStream.RINGER, AudioManager.RINGER_MODE_VIBRATE))
        assertEquals(0..7, ranges.of(VolumeStream.RINGER, AudioManager.RINGER_MODE_SILENT))
        // Only the ringer: a silent notification stream is fine in "着信音あり".
        assertEquals(0..7, ranges.of(VolumeStream.NOTIFICATION, AudioManager.RINGER_MODE_NORMAL))

        val normal = ranges.normalize(profile(AudioManager.RINGER_MODE_NORMAL, ring = 0, alarm = 5, voice = 5))
        assertEquals(1, normal.ringVolume)
        val vibrate = ranges.normalize(profile(AudioManager.RINGER_MODE_VIBRATE, ring = 0, alarm = 5, voice = 5))
        assertEquals(0, vibrate.ringVolume)
    }

    @Test
    fun levelsAboveTheMaximum_areCapped() {
        // e.g. copied from a phone whose media goes to 30
        val p = ranges.normalize(profile(AudioManager.RINGER_MODE_NORMAL, ring = 9, alarm = 9, voice = 20, media = 30))
        assertEquals(7, p.ringVolume)
        assertEquals(7, p.alarmVolume)
        assertEquals(15, p.voiceCallVolume)
        assertEquals(25, p.mediaVolume)
    }

    @Test
    fun valuesInRange_areLeftAlone() {
        val p = profile(AudioManager.RINGER_MODE_NORMAL, ring = 5, alarm = 6, voice = 11, media = 15)
        assertEquals(p, ranges.normalize(p))
    }
}
