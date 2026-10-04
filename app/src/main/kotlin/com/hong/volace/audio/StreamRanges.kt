package com.hong.volace.audio

import android.media.AudioManager
import com.hong.volace.data.Profile

/**
 * The levels this device accepts for each stream. Call and alarm cannot go below 1, and in
 * "着信音あり" the ringer cannot be 0 either: Android answers a ringer of 0 by switching to
 * vibrate, so such a profile would never stay as applied.
 *
 * One table for input, saving, applying and comparing, so the edit screen cannot show a 0 that
 * the device then plays at 1.
 */
class StreamRanges(private val byStream: Map<VolumeStream, IntRange>) {

    fun of(stream: VolumeStream, ringerMode: Int): IntRange {
        val range = byStream[stream] ?: 0..0
        val ringerAudible = stream == VolumeStream.RINGER && ringerMode == AudioManager.RINGER_MODE_NORMAL
        return if (ringerAudible && range.first < 1 && range.last >= 1) 1..range.last else range
    }

    fun max(stream: VolumeStream): Int = byStream[stream]?.last ?: 0

    /** [profile] with every level moved inside what the device accepts for its ringer mode. */
    fun normalize(profile: Profile): Profile =
        VolumeStream.entries.fold(profile) { p, stream ->
            stream.copyWith(p, stream.valueOf(p).coerceIn(of(stream, p.ringerMode)))
        }
}

fun VolumeApplier.ranges(): StreamRanges =
    StreamRanges(VolumeStream.entries.associateWith { minVolume(it)..maxVolume(it) })

/**
 * Whether the device still sits where [profile] left it ([VolumeApplier.matches]; pure, for tests).
 * Streams Android rewrites on its own are skipped, otherwise a freshly applied profile would
 * immediately look changed:
 * - SYSTEM is aliased to RING (see [VolumeApplier.apply]), so it always reports the ringer's level.
 * - In vibrate/silent, RING and NOTIFICATION are muted and report 0.
 * - Streams the profile leaves alone ("変更しない") match whatever the device has.
 * Expected values are what apply actually writes: moved into the device's range.
 */
internal fun profileMatches(profile: Profile, device: DeviceVolumes, ranges: StreamRanges): Boolean {
    val expected = ranges.normalize(profile)
    if (device.ringerMode != expected.ringerMode) return false
    val ringerMuted = expected.ringerMode != AudioManager.RINGER_MODE_NORMAL
    return VolumeStream.entries.all { stream ->
        when {
            stream.isKeptBy(expected) -> true
            stream == VolumeStream.SYSTEM -> true
            ringerMuted && (stream == VolumeStream.RINGER || stream == VolumeStream.NOTIFICATION) -> true
            else -> device.levelOf(stream) == stream.valueOf(expected)
        }
    }
}
