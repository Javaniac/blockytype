package com.javaniac.blockytype.game

import com.javaniac.blockytype.ui.Role
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultViewTest {
    private fun finished(): Pair<TestResult, TypingTest> {
        val test = TypingTest(TargetText(listOf("the", "quick", "fox")))
        var t = 0L
        for (c in "the quick fox") {
            test.apply(test.typed + c, t * 1_000_000)
            t += 400
        }
        return TestStats.result(test) to test
    }

    private fun footer(keys: Keybinds): String {
        val (r, test) = finished()
        return ResultView.toHudState(r, test, keys).footer.joinToString("") { it.text }
    }

    @Test
    fun `the result footer names the player's own keys`() {
        val text = footer(Keybinds(menu = "G", restart = "Tab", use = "click", left = "Left", right = "Right"))
        assertTrue("Tab restart" in text, text)
        assertTrue("G settings" in text, text)
        assertTrue("Left/Right scroll" in text, text)
    }

    @Test
    fun `the default keys are F and Q`() {
        assertEquals("F restart · Q settings · A/D scroll", footer(Keybinds.DEFAULT))
    }

    @Test
    fun `mode name has mode and language`() {
        val words = TypingTest(TargetText(listOf("a", "b", "c")))
        assertEquals("words 3 english", ResultView.modeName(words, "english"))
        val time = TypingTest(TargetText(listOf("a")), TestMode.Time(30))
        assertEquals("time 30 german", ResultView.modeName(time, "german"))
    }

    // ---- heatmap and cursor

    @Test
    fun `the heatmap colors each word by its burst`() {
        val (r, test) = finished()
        val heat = ResultView.toHudState(r, test).heat
        val words = heat.flatten().map { it.word }
        assertEquals(listOf("the", "quick", "fox"), words.map { it.text })
        assertEquals(listOf(Role.HEAT3, Role.HEAT1, Role.HEAT4), words.map { it.role })
        assertTrue(words.none { it.highlighted })
    }

    @Test
    fun `words with a mistake are drawn in the error color`() {
        val test = TypingTest(TargetText(listOf("the", "quick", "fox")))
        var t = 0L
        for (c in "thx quick fox") {
            test.apply(test.typed + c, t * 1_000_000)
            t += 400
        }
        val heat = ResultView.toHudState(TestStats.result(test), test).heat.flatten().map { it.word }
        assertEquals(Role.ERROR, heat[0].role)
        assertTrue(heat[1].role != Role.ERROR)
    }

    @Test
    fun `the cursor highlights the words of its second and shows the numbers`() {
        val (r, test) = finished()
        val s = ResultView.toHudState(r, test, cursor = 1)
        val highlighted = s.heat.flatten().filter { it.word.highlighted }.map { it.word.text }
        assertEquals(listOf("the", "quick"), highlighted) // second 2: keys of "the " and "q"
        val c = s.cursor!!
        assertEquals(1, c.index)
        val sec = r.perSecond[1]
        assertEquals("2s  wpm ${sec.wpm.roundToInt()}  raw ${sec.raw.roundToInt()}  err ${sec.errors}", c.info)
        assertEquals(sec.wpm, c.wpm)
        assertEquals(sec.raw, c.raw)
    }

    @Test
    fun `no cursor means no marker and no info`() {
        val (r, test) = finished()
        assertEquals(null, ResultView.toHudState(r, test).cursor)
    }

    @Test
    fun `result uses the chosen speed unit and decimals`() {
        val (r, test) = finished()
        val plain = ResultView.toHudState(r, test)
        assertEquals("${r.wpm.roundToInt()}", plain.big[0].value)
        assertEquals("wpm", plain.big[0].label)

        val cpm = ResultView.toHudState(r, test, view = ViewOptions(unit = SpeedUnit.CPM))
        assertEquals("cpm", cpm.big[0].label)
        assertEquals("${(r.wpm * 5).roundToInt()}", cpm.big[0].value)
        assertEquals("${(r.raw * 5).roundToInt()}", cpm.stats.first { it.label == "raw" }.value)
        assertEquals(r.perSecond[0].wpm * 5, cpm.graph.wpm[0], 0.0001)
        assertEquals(r.perSecond[0].raw * 5, cpm.graph.raw[0], 0.0001)

        val dec = ResultView.toHudState(r, test, view = ViewOptions(decimals = true))
        assertEquals("%.2f".format(java.util.Locale.ROOT, r.wpm), dec.big[0].value)
        assertEquals("%.2f%%".format(java.util.Locale.ROOT, r.accuracy), dec.big[1].value)
        assertEquals("%.2fs".format(java.util.Locale.ROOT, r.seconds), dec.stats.first { it.label == "time" }.value)
        assertTrue(dec.stats.first { it.label == "consistency" }.value.contains('.'))
    }

    @Test
    fun `cursor info uses the unit`() {
        val (r, test) = finished()
        val s = ResultView.toHudState(r, test, cursor = 0, view = ViewOptions(unit = SpeedUnit.CPM))
        assertTrue(s.cursor!!.info.contains("cpm"), s.cursor!!.info)
    }

    @Test
    fun `graph starts at zero or at the lowest value`() {
        val (r, test) = finished()
        assertEquals(0, ResultView.toHudState(r, test).graph.floor)
        val free = ResultView.toHudState(r, test, view = ViewOptions(startGraphsAtZero = false)).graph
        val lowest = (free.wpm + free.raw).min()
        assertTrue(free.floor >= 0 && free.floor <= lowest, "floor ${free.floor} lowest $lowest")
        assertTrue(free.floor % 10 == 0 || free.floor == 0)
    }

    @Test
    fun `the heatmap and the footer are untouched by the options`() {
        val (r, test) = finished()
        val a = ResultView.toHudState(r, test)
        val b = ResultView.toHudState(r, test, view = ViewOptions(unit = SpeedUnit.CPS, decimals = true))
        assertEquals(a.heat, b.heat)
        assertEquals(a.footer, b.footer)
    }
}
