package com.javaniac.blockytype.pack

import com.javaniac.blockytype.ui.UiFont
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MenuShaderTest {
    private fun vsh(v: ClientVersion) =
        Shaders.files(v).entries.single { it.key.endsWith("core/text.vsh") }.value

    @Test
    fun `text vsh reads the page bit for ids 16 to 31 on every client version`() {
        for (v in ClientVersion.entries) {
            val src = vsh(v)
            assertTrue("(c.g & 4) != 0" in src && "+ 16" in src, "page bit missing in ${v.label}")
        }
    }

    @Test
    fun `the cursor is gone from the shaders`() {
        for (v in ClientVersion.entries) {
            val src = vsh(v)
            assertTrue("hudIsCursor" !in src && "HUD_CURSOR" !in src, v.label)
        }
    }

    @Test
    fun `menu element anchors come from the shared layout constants`() {
        for (v in ClientVersion.entries) {
            val src = vsh(v)
            assertTrue("cy + (${UiFont.MENU_HEADER_DY}.0)" in src, "header ${v.label}")
            assertTrue("cy + (${UiFont.MENU_ROW0_DY}.0) + ${UiFont.MENU_ROW_PITCH}.0 * float(id - ${UiFont.MENU_ROW_FIRST_ID})" in src, "rows ${v.label}")
            assertTrue("cy + (${UiFont.MENU_FOOTER_DY}.0)" in src, "footer ${v.label}")
        }
    }

    @Test
    fun `menu rows fit the smallest GUI of 320x240`() {
        val cy = 120
        val first = cy + UiFont.MENU_ROW0_DY
        val last = first + UiFont.MENU_ROW_PITCH * (UiFont.MENU_ROWS - 1)
        assertTrue(cy + UiFont.MENU_HEADER_DY >= 0)
        assertTrue(first > cy + UiFont.MENU_HEADER_DY)
        assertTrue(last + 9 < cy + UiFont.MENU_FOOTER_DY, "last row must end above the footer")
        assertTrue(cy + UiFont.MENU_FOOTER_DY + 9 <= 240)
        assertEquals(UiFont.MENU_ROW_FIRST_ID + UiFont.MENU_ROWS - 1, UiFont.MENU_ROW_LAST_ID)
        assertTrue(UiFont.MENU_ROW_LAST_ID < UiFont.MENU_FOOTER_ID)
    }
}
