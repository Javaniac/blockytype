package com.javaniac.blockytype.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BlockGlyphTest {
    @Test
    fun `block strings advance exactly their width`() {
        for (w in 1..400) {
            assertEquals(w, UiFont.blockAdvance(UiFont.block(w)), "width $w")
            assertEquals(w, UiFont.blockAdvance(UiFont.block(w, tall = true)), "tall width $w")
        }
    }

    @Test
    fun `block strings use few glyphs`() {
        assertTrue(UiFont.block(300).length <= 2 * 12, "300 wide = 4 x 64 + 32 + 8 + 4")
    }

    @Test
    fun `normal and tall blocks use different glyphs`() {
        val normal = UiFont.block(10).filter { it.code in UiFont.BLOCK_BASE..UiFont.BLOCK_BASE + UiFont.BLOCK_MAX_BIT }
        val tall = UiFont.block(10, tall = true).filter { it.code in UiFont.TALL_BASE..UiFont.TALL_BASE + UiFont.BLOCK_MAX_BIT }
        assertTrue(normal.isNotEmpty() && tall.isNotEmpty())
        assertEquals(UiFont.block(10).length, UiFont.block(10, tall = true).length)
    }

    @Test
    fun `width must be positive`() {
        assertFailsWith<IllegalArgumentException> { UiFont.block(0) }
        assertFailsWith<IllegalArgumentException> { UiFont.block(-3) }
    }
}
