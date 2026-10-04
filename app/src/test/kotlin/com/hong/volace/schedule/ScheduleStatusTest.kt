package com.hong.volace.schedule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric for the real org.json (android.jar only has stubs). */
@RunWith(RobolectricTestRunner::class)
class ScheduleStatusTest {

    @Test
    fun json_roundTrip() {
        ScheduleStatus.Outcome.entries.forEach { outcome ->
            val status = ScheduleStatus(at = 1_791_084_000_000, profileId = 4, profileName = "マナー", outcome = outcome)
            assertEquals(status, ScheduleStatus.fromJson(status.toJson()))
        }
    }

    @Test
    fun json_brokenIsNull() {
        assertNull(ScheduleStatus.fromJson("{"))
        assertNull(ScheduleStatus.fromJson("""{"at":1,"profileId":2,"profileName":"x","outcome":"NO_SUCH"}"""))
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun failed(outcome: ScheduleStatus.Outcome = ScheduleStatus.Outcome.FAILED) =
        ScheduleStatus(at = 1_000, profileId = 4, profileName = "マナー", outcome = outcome)

    @Test
    fun manualChoice_marksBoundariesUpToNowAsDone() {
        ScheduleStore.setHandledUntil(context, 1_000)
        val before = System.currentTimeMillis()
        Schedules.noteManualChoice(context, profileId = 9)
        assertTrue(ScheduleStore.handledUntil(context)!! >= before)
    }

    @Test
    fun manualChoice_ofTheFailedProfileCountsAsSwitchedByHand() {
        ScheduleStore.saveStatus(context, failed())
        Schedules.noteManualChoice(context, profileId = 4)
        assertEquals(ScheduleStatus.Outcome.BY_HAND, ScheduleStore.status(context)?.outcome)
    }

    @Test
    fun manualChoice_ofAnotherProfileTakesPriority() {
        ScheduleStore.saveStatus(context, failed(ScheduleStatus.Outcome.NEEDS_ACCESS))
        Schedules.noteManualChoice(context, profileId = 9)
        assertEquals(ScheduleStatus.Outcome.OVERRIDDEN, ScheduleStore.status(context)?.outcome)
    }

    @Test
    fun manualChoice_leavesOtherOutcomesAlone() {
        ScheduleStore.saveStatus(context, failed(ScheduleStatus.Outcome.PROFILE_GONE))
        Schedules.noteManualChoice(context, profileId = 4)
        assertEquals(ScheduleStatus.Outcome.PROFILE_GONE, ScheduleStore.status(context)?.outcome)
    }
}
