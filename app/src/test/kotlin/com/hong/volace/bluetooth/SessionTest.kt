package com.hong.volace.bluetooth

import android.media.AudioManager
import com.hong.volace.audio.DeviceVolumes
import com.hong.volace.audio.DndMode
import com.hong.volace.audio.SoundKind
import com.hong.volace.audio.VolumeStream
import com.hong.volace.audio.isKeptBy
import com.hong.volace.audio.valueOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric for the real org.json (android.jar only has stubs). */
@RunWith(RobolectricTestRunner::class)
class SessionTest {

    private val session = Session(
        profileId = 12,
        previous = DeviceVolumes(
            AudioManager.RINGER_MODE_NORMAL,
            mapOf(
                VolumeStream.RINGER to 5, VolumeStream.NOTIFICATION to 5, VolumeStream.MEDIA to 8,
                VolumeStream.ALARM to 4, VolumeStream.VOICE_CALL to 9, VolumeStream.SYSTEM to 5,
            ),
            volaceDnd = DndMode.PRIORITY.value,
        ),
        previousActiveId = 10,
        previousSounds = mapOf(SoundKind.RINGTONE to "content://media/internal/audio/media/10"),
    )

    @Test
    fun json_roundTrip() {
        assertEquals(session, Session.fromJson(session.toJson()))
        assertNull(Session.fromJson("{"))
    }

    @Test
    fun restore_leavesMediaAndCallToTheSpeakersOwnLevels() {
        val restore = session.restoreProfile("前")
        // Android keeps media and call per device: the earbuds' levels were the ones changed.
        assertTrue(VolumeStream.MEDIA.isKeptBy(restore))
        assertTrue(VolumeStream.VOICE_CALL.isKeptBy(restore))
        assertFalse(VolumeStream.ALARM.isKeptBy(restore))
        assertEquals(4, VolumeStream.ALARM.valueOf(restore))
        assertEquals(5, VolumeStream.RINGER.valueOf(restore))
        assertEquals(DndMode.PRIORITY.value, restore.dndMode)
        assertEquals("content://media/internal/audio/media/10", restore.ringtoneUri)
        assertNull(restore.alarmSoundUri)
    }

    @Test
    fun status_json_roundTrip() {
        BluetoothStatus.Outcome.entries.forEach { outcome ->
            val status = BluetoothStatus("Oladance", "音楽", connected = true, outcome = outcome, at = 1_000, profileId = 12)
            assertEquals(status, BluetoothStatus.fromJson(status.toJson()))
        }
    }

    @Test
    fun manualChoice_settlesAFailedSwitch() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val failed = BluetoothStatus("Oladance", "サイレント", connected = true, outcome = BluetoothStatus.Outcome.FAILED, profileId = 9)
        BluetoothStore.saveStatus(context, failed)
        BluetoothSwitch.noteManualChoice(context, profileId = 9)
        assertEquals(BluetoothStatus.Outcome.BY_HAND, BluetoothStore.status(context)?.outcome)
        BluetoothStore.saveStatus(context, failed)
        BluetoothSwitch.noteManualChoice(context, profileId = 10)
        assertEquals(BluetoothStatus.Outcome.OVERRIDDEN, BluetoothStore.status(context)?.outcome)
    }
}
