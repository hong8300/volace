package com.hong.volace.data

import com.hong.volace.audio.VolumeStream
import com.hong.volace.R
import androidx.test.core.app.ApplicationProvider
import org.robolectric.annotation.Config
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
        fun reason(text: String) =
            assertThrows(ProfileBackup.FormatException::class.java) { ProfileBackup.fromJson(text) }
                .let { it.reason to it.args.toList() }

        assertEquals(R.string.backup_err_not_json to emptyList<Any>(), reason("not json"))
        assertEquals(R.string.backup_err_not_volace to emptyList<Any>(), reason("""{"profiles": []}"""))
        assertEquals(
            R.string.backup_err_version to listOf<Any>(2),
            reason("""{"format":"volace-profiles","version":2,"profiles":[]}"""),
        )
        assertEquals(
            R.string.backup_err_missing to listOf<Any>(1, "media"),
            reason("""{"format":"volace-profiles","version":1,"profiles":[{"name":"x","ringerMode":2,"ring":1,"notification":1,"alarm":1,"voiceCall":1,"system":1,"color":0}]}"""),
        )
        assertEquals(
            R.string.backup_err_ringer to listOf<Any>(1),
            reason("""{"format":"volace-profiles","version":1,"profiles":[{"name":"x","ringerMode":5,"ring":1,"notification":1,"media":1,"alarm":1,"voiceCall":1,"system":1,"color":0}]}"""),
        )
    }

    /** The reasons read naturally in both languages (Japanese is the default resource). */
    @Test
    @Config(qualifiers = "ja")
    fun reasons_inJapanese() {
        val e = assertThrows(ProfileBackup.FormatException::class.java) {
            ProfileBackup.fromJson("""{"format":"volace-profiles","version":1,"profiles":[{"name":"x","ringerMode":2,"ring":1,"notification":1,"alarm":1,"voiceCall":1,"system":1,"color":0}]}""")
        }
        assertEquals("1 件目に media がありません", e.describe(ApplicationProvider.getApplicationContext()))
    }

    @Test
    @Config(qualifiers = "en")
    fun reasons_inEnglish() {
        val e = assertThrows(ProfileBackup.FormatException::class.java) { ProfileBackup.fromJson("not json") }
        assertEquals("Not readable as JSON", e.describe(ApplicationProvider.getApplicationContext()))
    }
}
