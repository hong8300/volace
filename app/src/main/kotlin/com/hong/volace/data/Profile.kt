package com.hong.volace.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val orderIndex: Int,
    val ringerMode: Int,
    val ringVolume: Int,
    val notificationVolume: Int,
    val mediaVolume: Int,
    val alarmVolume: Int,
    val voiceCallVolume: Int,
    val systemVolume: Int,
    val isActive: Boolean = false,
    /** Accent colour (ARGB) used by the list rows and the widget cells. */
    val colorArgb: Int = ProfilePalette.DEFAULT,
    /** Key of a [ProfileIcon]; kept as a string so unknown values degrade gracefully. */
    val iconKey: String = ProfileIcon.DEFAULT.key,
    /**
     * Streams this profile leaves alone ("変更しない"), one bit each (VolumeStream.keepBit). Their
     * stored levels are kept so unticking brings them back.
     */
    val keepMask: Int = 0,
    /**
     * The device's default sounds this profile sets (SoundKind): null leaves it as it is
     * ("変更しない"), "" is "なし", otherwise a sound's URI. Device-specific: not in backups.
     */
    val ringtoneUri: String? = null,
    val notificationSoundUri: String? = null,
    val alarmSoundUri: String? = null,
    /** What it does with "Do Not Disturb" (DndMode.value): Volace's own modes only. */
    val dndMode: Int = 0,
)

/**
 * The columns the edit screen owns. Saving through this leaves [Profile.isActive] and
 * [Profile.orderIndex] alone: the screen holds a copy loaded when it opened, and writing those
 * back would undo an apply or a reorder done in the meantime (e.g. from a widget), leaving two
 * profiles marked as applied, or none.
 */
data class ProfileEdits(
    val id: Long,
    val name: String,
    val ringerMode: Int,
    val ringVolume: Int,
    val notificationVolume: Int,
    val mediaVolume: Int,
    val alarmVolume: Int,
    val voiceCallVolume: Int,
    val systemVolume: Int,
    val colorArgb: Int,
    val iconKey: String,
    val keepMask: Int,
    val ringtoneUri: String?,
    val notificationSoundUri: String?,
    val alarmSoundUri: String?,
    val dndMode: Int,
)

fun Profile.edits() = ProfileEdits(
    id = id,
    name = name,
    ringerMode = ringerMode,
    ringVolume = ringVolume,
    notificationVolume = notificationVolume,
    mediaVolume = mediaVolume,
    alarmVolume = alarmVolume,
    voiceCallVolume = voiceCallVolume,
    systemVolume = systemVolume,
    colorArgb = colorArgb,
    iconKey = iconKey,
    keepMask = keepMask,
    ringtoneUri = ringtoneUri,
    notificationSoundUri = notificationSoundUri,
    alarmSoundUri = alarmSoundUri,
    dndMode = dndMode,
)
