package com.hong.volace.data

import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric for android.graphics.Color behind ColorUtils. */
@RunWith(RobolectricTestRunner::class)
class ContrastTest {

    private val white = 0xFFFFFFFF.toInt()
    private val dark = 0xFF1C1B1F.toInt()
    private val amber = 0xFFFFB300.toInt()

    @Test
    fun amber_getsDarkText_insteadOfUnreadableWhite() {
        assertTrue(ColorUtils.calculateContrast(white, amber) < 2.0) // the problem (≈1.8:1)
        assertEquals(dark, contentColorOn(amber))
        assertTrue(ColorUtils.calculateContrast(dark, amber) > 7.0)
    }

    @Test
    fun theOtherPaletteColours_keepWhiteText() {
        // Only the light colour flips; the rest keep the look they have always had.
        ProfilePalette.COLORS.filter { it != amber }.forEach { color ->
            assertEquals("0x${Integer.toHexString(color)}", white, contentColorOn(color))
        }
    }
}
