package com.javaniac.blockytype.pack

import com.javaniac.blockytype.ui.Element
import com.javaniac.blockytype.ui.ResultGraph
import com.javaniac.blockytype.ui.ResultLayout
import com.javaniac.blockytype.ui.UiFont
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultLayoutTest {
    private fun vsh(v: ClientVersion) =
        Shaders.files(v).entries.single { it.key.endsWith("core/text.vsh") }.value

    @Test
    fun `result anchors come from the shared layout constants on every client version`() {
        for (v in ClientVersion.entries) {
            val src = vsh(v)
            assertTrue("id == ${Element.RESULT_ERRORS}) return vec2(cx, cy + (${ResultLayout.ERRORS_DY}.0))" in src, "errors ${v.label}")
            assertTrue("id == ${Element.RESULT_STATS_LABEL}) return vec2(cx, cy + (${ResultLayout.STATS_LABEL_DY}.0))" in src, "stat labels ${v.label}")
            assertTrue("id == ${Element.RESULT_STATS}) return vec2(cx, cy + (${ResultLayout.STATS_VALUE_DY}.0))" in src, "stat values ${v.label}")
            assertTrue("id == ${Element.RESULT_X_LABELS}) return vec2(cx, cy + (${ResultLayout.X_LABELS_DY}.0))" in src, "x labels ${v.label}")
            assertTrue("id == ${Element.RESULT_Y_MID}) return vec2(cx, cy + (${ResultLayout.Y_MID_DY}.0))" in src, "y mid ${v.label}")
            assertTrue("id == ${Element.RESULT_Y_ZERO}) return vec2(cx, cy + (${ResultLayout.Y_ZERO_DY}.0))" in src, "y zero ${v.label}")
        }
    }

    @Test
    fun `the divider and the heatmap lines sit on the free menu rows`() {
        assertEquals(UiFont.MENU_ROW0_DY + UiFont.MENU_ROW_PITCH * (Element.RESULT_DIVIDER - UiFont.MENU_ROW_FIRST_ID), ResultLayout.DIVIDER_DY)
        for (i in 0 until ResultLayout.HEAT_LINES) {
            val id = Element.RESULT_HEAT_FIRST + i
            assertTrue(id in UiFont.MENU_ROW_FIRST_ID..UiFont.MENU_ROW_LAST_ID, "id $id")
            assertEquals(
                UiFont.MENU_ROW0_DY + UiFont.MENU_ROW_PITCH * (id - UiFont.MENU_ROW_FIRST_ID),
                ResultLayout.DIVIDER_DY + UiFont.MENU_ROW_PITCH * (i + 1),
            )
        }
    }

    @Test
    fun `everything fits the smallest GUI of 320x240 without overlapping`() {
        val line = 9 // height of a text line
        // graph, then its x labels, then the stats row, then the cursor line, the heatmap and the footer
        assertTrue(ResultLayout.BIG_DY < ResultGraph.TOP, "big numbers above the graph")
        assertTrue(ResultLayout.X_LABELS_DY >= ResultGraph.TOP + ResultGraph.H)
        assertTrue(ResultLayout.STATS_LABEL_DY >= ResultLayout.X_LABELS_DY + line)
        assertTrue(ResultLayout.STATS_VALUE_DY >= ResultLayout.STATS_LABEL_DY + line)
        assertTrue(ResultLayout.DIVIDER_DY >= ResultLayout.STATS_VALUE_DY + line)
        val lastHeat = ResultLayout.DIVIDER_DY + UiFont.MENU_ROW_PITCH * ResultLayout.HEAT_LINES
        assertTrue(lastHeat + line <= ResultLayout.FOOTER_DY, "heatmap above the footer")
        assertTrue(120 + ResultLayout.FOOTER_DY + line <= 240)
    }

    @Test
    fun `the y labels sit on the plot`() {
        assertTrue(ResultLayout.Y_MID_DY in ResultGraph.TOP..ResultGraph.TOP + ResultGraph.H)
        assertTrue(ResultLayout.Y_ZERO_DY in ResultGraph.TOP..ResultGraph.TOP + ResultGraph.H)
        assertTrue(ResultLayout.ERRORS_DY in ResultGraph.TOP..ResultGraph.TOP + ResultGraph.H)
    }
}
