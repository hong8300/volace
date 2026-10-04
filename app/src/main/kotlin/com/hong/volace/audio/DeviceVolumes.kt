package com.hong.volace.audio

import android.media.AudioManager

/**
 * What the device is actually set to right now — which drifts from the applied profile as soon as
 * anything else (volume keys, the system panel, another app) touches the volume.
 */
data class DeviceVolumes(
    val ringerMode: Int,
    val levels: Map<VolumeStream, Int>,
) {
    fun levelOf(stream: VolumeStream): Int = levels[stream] ?: 0
}

fun ringerModeLabel(mode: Int): String = when (mode) {
    AudioManager.RINGER_MODE_SILENT -> "サイレント"
    AudioManager.RINGER_MODE_VIBRATE -> "バイブ"
    else -> "着信音あり"
}
