package com.hong.volace.ui.theme

import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric for android.graphics.Color behind ColorUtils. */
@RunWith(RobolectricTestRunner::class)
class SkinTest {

    @Test
    fun keys_areUniqueAndUnknownFallsBack() {
        assertEquals(Skin.entries.size, Skin.entries.map { it.key }.toSet().size)
        assertEquals(Skin.DEFAULT, Skin.fromKey("no-such-skin"))
        assertEquals(IconStyle.STANDARD, IconStyle.fromKey(null))
    }

    @Test
    fun skinsMadeFromATable_haveOne() {
        val handMade = setOf(Skin.DEFAULT, Skin.LIGHT, Skin.AUTO, Skin.DYNAMIC, Skin.HIGH_CONTRAST, Skin.MIDNIGHT)
        Skin.entries.filter { it !in handMade }.forEach { assertNotNull(it.name, it.colors) }
    }

    @Test
    fun tableSkins_areReadable() {
        Skin.entries.mapNotNull { skin -> skin.colors?.let { skin to it } }.forEach { (skin, c) ->
            fun check(what: String, fg: Int, bg: Int, min: Double) {
                val contrast = ColorUtils.calculateContrast(fg, bg)
                assertTrue("$skin $what: $contrast", contrast >= min)
            }
            check("text", c.text, c.background, 7.0)
            check("text on cards", c.text, c.surfaceHigh, 7.0)
            check("sub text", c.subText, c.surface, 4.5)
            check("buttons", c.onPrimary, c.primary, 3.0)
            check("links", c.primary, c.background, 3.0)
            check("containers", c.onPrimaryContainer, c.primaryContainer, 4.5)
        }
    }
}
