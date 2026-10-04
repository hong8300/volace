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
        ProfilePalette.COLORS.take(ProfilePalette.ORIGINAL_COUNT).filter { it != amber }.forEach { color ->
            assertEquals("0x${Integer.toHexString(color)}", white, contentColorOn(color))
        }
    }

    @Test
    fun theColoursAddedLater_areReadable() {
        // Chosen so text on them reaches 4.5:1 (pastels take dark text).
        ProfilePalette.COLORS.drop(ProfilePalette.ORIGINAL_COUNT).forEach { color ->
            val contrast = ColorUtils.calculateContrast(contentColorOn(color), color)
            assertTrue("0x${Integer.toHexString(color)}: $contrast", contrast >= 4.5)
        }
    }

    @Test
    fun everyColourAndIcon_hasAName() {
        assertEquals(ProfilePalette.COLORS.size, ProfilePalette.NAMES.size)
        assertEquals(ProfilePalette.COLORS.size, ProfilePalette.COLORS.toSet().size)
        assertEquals(ProfileIcon.entries.size, ProfileIcon.entries.map { it.key }.toSet().size)
        assertTrue(ProfileIcon.entries.all { it.emoji.isNotBlank() })
    }
}
