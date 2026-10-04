package com.hong.volace.audio

import com.hong.volace.data.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The 1×1 widget's "next profile". */
class CycleTest {

    private fun list(vararg names: String, active: String? = null) = names.mapIndexed { i, n ->
        Profile(
            id = i + 1L, name = n, orderIndex = i, ringerMode = 2, ringVolume = 1, notificationVolume = 1,
            mediaVolume = 1, alarmVolume = 1, voiceCallVolume = 1, systemVolume = 1, isActive = n == active,
        )
    }

    @Test
    fun stepsToTheNext_andWrapsAround() {
        assertEquals("マナー", ProfileSwitcher.nextInCycle(list("サイレント", "マナー", "通常", active = "サイレント"))?.name)
        assertEquals("サイレント", ProfileSwitcher.nextInCycle(list("サイレント", "マナー", "通常", active = "通常"))?.name)
    }

    @Test
    fun nothingApplied_startsAtTheFirst() {
        assertEquals("サイレント", ProfileSwitcher.nextInCycle(list("サイレント", "マナー"))?.name)
    }

    @Test
    fun noProfiles_nothingToApply() {
        assertNull(ProfileSwitcher.nextInCycle(emptyList()))
    }
}
