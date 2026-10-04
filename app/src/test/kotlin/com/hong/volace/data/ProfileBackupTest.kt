package com.hong.volace.data

import com.hong.volace.audio.VolumeStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric for the real org.json (android.jar only has stubs). */
@RunWith(RobolectricTestRunner::class)
class ProfileBackupTest {

    private val profiles = listOf(
        Profile(
            id = 7, name = "会議", orderIndex = 3, ringerMode = 1, ringVolume = 0,
            notificationVolume = 0, mediaVolume = 12, alarmVolume = 5, voiceCallVolume = 9,
            systemVolume = 0, isActive = true, colorArgb = 0xFF7E57C2.toInt(), iconKey = "work",
        ),
        Profile(
            id = 9, name = "音楽", orderIndex = 5, ringerMode = 2, ringVolume = 4,
            notificationVolume = 4, mediaVolume = 25, alarmVolume = 6, voiceCallVolume = 11,
            systemVolume = 4, colorArgb = 0xFF26A69A.toInt(), iconKey = "music",
        ),
    )

    @Test
    fun roundTrip_keepsWhatDefinesAProfile_andDropsDeviceState() {
        val back = ProfileBackup.fromJson(ProfileBackup.toJson(profiles), firstOrder = 10)

        assertEquals(listOf("会議", "音楽"), back.map { it.name })
        assertEquals(listOf(10, 11), back.map { it.orderIndex }) // file order, after what exists
        assertTrue(back.all { it.id == 0L && !it.isActive }) // new rows, nothing applied
        val meeting = back[0]
        assertEquals(1, meeting.ringerMode)
        assertEquals(12, meeting.mediaVolume)
        assertEquals(9, meeting.voiceCallVolume)
        assertEquals(0xFF7E57C2.toInt(), meeting.colorArgb)
        assertEquals("work", meeting.iconKey)
    }

    @Test
    fun keptStreams_roundTrip_andOlderFilesKeepNothing() {
        val keeping = profiles[0].copy(keepMask = VolumeStream.MEDIA.keepBit or VolumeStream.ALARM.keepBit)
        val back = ProfileBackup.fromJson(ProfileBackup.toJson(listOf(keeping)))
        assertEquals(keeping.keepMask, back[0].keepMask)

        val older = """{"format":"volace-profiles","version":1,"profiles":[{"name":"x","ringerMode":2,"ring":1,"notification":1,"media":1,"alarm":1,"voiceCall":1,"system":1,"color":0}]}"""
        assertEquals(0, ProfileBackup.fromJson(older)[0].keepMask)
    }

    @Test
    fun foreignOrBrokenFiles_areRefusedWithAReason() {
        fun message(text: String) =
            assertThrows(ProfileBackup.FormatException::class.java) { ProfileBackup.fromJson(text) }.message

        assertEquals("JSON として読めません", message("not json"))
        assertEquals("Volace のバックアップではありません", message("""{"profiles": []}"""))
        assertEquals("対応していない形式です（version 2）", message("""{"format":"volace-profiles","version":2,"profiles":[]}"""))
        assertEquals(
            "1 件目に media がありません",
            message("""{"format":"volace-profiles","version":1,"profiles":[{"name":"x","ringerMode":2,"ring":1,"notification":1,"alarm":1,"voiceCall":1,"system":1,"color":0}]}"""),
        )
        assertEquals(
            "1 件目の着信モードが不正です",
            message("""{"format":"volace-profiles","version":1,"profiles":[{"name":"x","ringerMode":5,"ring":1,"notification":1,"media":1,"alarm":1,"voiceCall":1,"system":1,"color":0}]}"""),
        )
    }
}
