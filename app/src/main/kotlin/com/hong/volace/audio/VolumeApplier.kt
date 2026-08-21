package com.hong.volace.audio

import android.content.Context
import android.media.AudioManager
import com.hong.volace.data.Profile

class VolumeApplier(context: Context) {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun maxVolume(stream: VolumeStream): Int = audioManager.getStreamMaxVolume(stream.streamType)

    fun currentVolume(stream: VolumeStream): Int = audioManager.getStreamVolume(stream.streamType)

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
    }
}
