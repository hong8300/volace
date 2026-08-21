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
)
