package com.hong.volace.audio

import android.content.Context
import android.media.AudioManager
import com.hong.volace.data.Profile

class VolumeApplier(context: Context) {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun maxVolume(stream: VolumeStream): Int = audioManager.getStreamMaxVolume(stream.streamType)

    fun minVolume(stream: VolumeStream): Int = audioManager.getStreamMinVolume(stream.streamType)

    fun currentVolume(stream: VolumeStream): Int = audioManager.getStreamVolume(stream.streamType)

    fun snapshot(): DeviceVolumes = DeviceVolumes(
        ringerMode = audioManager.ringerMode,
        levels = VolumeStream.entries.associateWith { currentVolume(it) },
    )

    /**
     * Whether the device still sits where [profile] left it. Streams Android rewrites on its own
     * are skipped, otherwise a freshly applied profile would immediately look changed:
     * - SYSTEM is aliased to RING (see [apply]), so it always reports the ringer's level.
     * - In vibrate/silent, RING and NOTIFICATION are muted and report 0.
     * Expected values are clamped to the stream's range (call and alarm cannot go below 1).
     */
    fun matches(profile: Profile, device: DeviceVolumes): Boolean {
        if (device.ringerMode != profile.ringerMode) return false
        val ringerMuted = profile.ringerMode != AudioManager.RINGER_MODE_NORMAL
        return VolumeStream.entries.all { stream ->
            when {
                stream == VolumeStream.SYSTEM -> true
                ringerMuted && stream in RINGER_STREAMS -> true
                else -> device.levelOf(stream) ==
                    stream.valueOf(profile).coerceIn(minVolume(stream), maxVolume(stream))
            }
        }
    }

    fun apply(profile: Profile) {
        // Set the mode first so the ring/notification streams are unmuted and actually accept the
        // indices we are about to write.
        runCatching { audioManager.ringerMode = profile.ringerMode }

        // STREAM_SYSTEM is aliased to STREAM_RING on stock Android audio policy (confirmed via
        // dumpsys audio on Pixel 9 Pro XL / Android 17): whichever of the two is set last wins.
        // Apply SYSTEM first so the user-facing Ringer value is the one that actually sticks.
        APPLY_ORDER.forEach { stream ->
            runCatching {
                audioManager.setStreamVolume(stream.streamType, stream.valueOf(profile), 0)
            }
        }

        // Writing 0 to STREAM_RING makes the system drop into VIBRATE on its own, which silently
        // overrides an explicit "silent" profile. Re-assert the mode so the profile has the last
        // word, then restore the ring/notification indices the mode change may have bumped.
        runCatching { audioManager.ringerMode = profile.ringerMode }
        if (profile.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            listOf(VolumeStream.NOTIFICATION, VolumeStream.RINGER).forEach { stream ->
                runCatching {
                    audioManager.setStreamVolume(stream.streamType, stream.valueOf(profile), 0)
                }
            }
        }
    }

    private companion object {
        val APPLY_ORDER = listOf(
            VolumeStream.SYSTEM,
            VolumeStream.NOTIFICATION,
            VolumeStream.MEDIA,
            VolumeStream.ALARM,
            VolumeStream.VOICE_CALL,
            VolumeStream.RINGER,
        )

        val RINGER_STREAMS = setOf(VolumeStream.RINGER, VolumeStream.NOTIFICATION)
    }
}
