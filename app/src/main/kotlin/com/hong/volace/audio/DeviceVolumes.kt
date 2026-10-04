package com.hong.volace.audio

import android.media.AudioManager
import androidx.annotation.StringRes
import com.hong.volace.R

/**
 * What the device is actually set to right now — which drifts from the applied profile as soon as
 * anything else (volume keys, the system panel, another app) touches the volume.
 */
data class DeviceVolumes(
    val ringerMode: Int,
    val levels: Map<VolumeStream, Int>,
    /**
     * "Do Not Disturb" is on (any mode). Android then reports the ringer as silent and may mute
     * the ringer and notification streams, whatever the ringer is underneath.
     */
    val dndActive: Boolean = false,
    /** Which of Volace's own DND modes is on ([DndMode.value]). */
    val volaceDnd: Int = DndMode.OFF.value,
) {
    fun levelOf(stream: VolumeStream): Int = levels[stream] ?: 0
}

@StringRes
fun ringerModeLabel(mode: Int): Int = when (mode) {
    AudioManager.RINGER_MODE_SILENT -> R.string.ringer_silent
    AudioManager.RINGER_MODE_VIBRATE -> R.string.ringer_vibrate
    else -> R.string.ringer_normal
}
