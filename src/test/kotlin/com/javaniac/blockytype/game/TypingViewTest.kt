package com.javaniac.blockytype.game

import com.javaniac.blockytype.ui.CharState
import com.javaniac.blockytype.ui.Palette
import com.javaniac.blockytype.ui.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TypingViewTest {
    private val ms = 1_000_000L

    private fun test(vararg words: String = arrayOf("the", "quick", "fox"), rules: TestRules = TestRules(), mode: TestMode? = null) =
        TypingTest(TargetText(words.toList()), mode ?: TestMode.Words(words.size), rules)

    private fun TypingTest.type(text: String, stepMs: Long = 100) {
        var t = 0L
        for (c in text) {
            apply(typed + c, t * ms)
            t += stepMs
        }
    }

    private fun view(t: TypingTest, o: ViewOptions = ViewOptions(highlight = ViewOptions.Highlight.OFF), now: Long = 10_000 * ms) =
        TypingView.build(t, now, "english", o)

    private fun states(s: com.javaniac.blockytype.ui.HudState.Typing) = s.lines.first().states

    @Test
    fun `default view marks typed text correct or wrong and the rest untyped`() {
        val t = test()
        t.type("the qx")
        val s = states(view(t))
        assertEquals(List(4) { CharState.CORRECT }, s.take(4)) // "the" and its committed space
        assertEquals(CharState.CORRECT, s[4])
        assertEquals(CharState.ERROR, s[5])
        assertEquals(CharState.UNTYPED, s[6])
        assertEquals("the quick fox", view(t).lines.first().text)
    }

    @Test
    fun `missing letters of a committed word are errors, extra letters flag the word`() {
        val t = test()
        t.type("th quickk")
        val s = states(view(t))
        assertEquals(CharState.ERROR, s[2]) // missing e
        val extra = test()
        extra.type("thex")
        assertEquals(CharState.ERROR, states(view(extra))[2])
        val hidden = view(extra, ViewOptions(hideExtra = true, highlight = ViewOptions.Highlight.OFF))
        assertEquals(CharState.CORRECT, states(hidden)[2])
    }

    @Test
    fun `blind mode shows no mistakes`() {
        val t = test()
        t.type("thx qu")
        val s = view(t, ViewOptions(blind = true, highlight = ViewOptions.Highlight.OFF))
        assertTrue(s.lines.all { l -> l.states.none { it == CharState.ERROR } })
    }

    @Test
    fun `indicate typos replaces the letter with the typed one`() {
        val t = test()
        t.type("thx")
        assertEquals("the quick fox", view(t).lines.first().text)
        val s = view(t, ViewOptions(showTypos = true, highlight = ViewOptions.Highlight.OFF))
        assertEquals("thx quick fox", s.lines.first().text)
        assertEquals(CharState.ERROR, s.lines.first().states[2])
    }

    @Test
    fun `highlight is off by default and with unset settings`() {
        assertEquals(ViewOptions.Highlight.OFF, ViewOptions().highlight)
        assertEquals(ViewOptions.Highlight.OFF, ViewOptions.from { id -> com.javaniac.blockytype.settings.SettingsCatalog.byId(id)!!.default }.highlight)
        assertEquals(ViewOptions.Highlight.LETTER, ViewOptions.from { id -> if (id == "highlight-mode") "letter" else com.javaniac.blockytype.settings.SettingsCatalog.byId(id)!!.default }.highlight)
    }

    @Test
    fun `highlight modes mark the untyped letters they cover`() {
        val t = test()
        t.type("th")
        fun marked(h: ViewOptions.Highlight) =
            view(t, ViewOptions(highlight = h)).lines.first().states.withIndex().filter { it.value == CharState.HIGHLIGHT }.map { it.index }
        assertEquals(emptyList(), marked(ViewOptions.Highlight.OFF))
        assertEquals(listOf(2), marked(ViewOptions.Highlight.LETTER))
        assertEquals(listOf(2), marked(ViewOptions.Highlight.WORD))
        assertEquals((4..8).toList(), marked(ViewOptions.Highlight.NEXT_WORD))
        assertEquals((4..8).toList() + (10..12).toList(), marked(ViewOptions.Highlight.NEXT_TWO))
        assertEquals((4..8).toList() + (10..12).toList(), marked(ViewOptions.Highlight.NEXT_THREE)) // only two words left
    }

    @Test
    fun `typed effects`() {
        val t = test()
        t.type("the q")
        fun line(e: ViewOptions.TypedEffect) = view(t, ViewOptions(effect = e, highlight = ViewOptions.Highlight.OFF)).lines.first()
        assertEquals("the quick fox", line(ViewOptions.TypedEffect.KEEP).text)
        assertEquals("     uick fox", line(ViewOptions.TypedEffect.HIDE).text)
        assertEquals("··· ·uick fox", line(ViewOptions.TypedEffect.DOTS).text)
        val fade = line(ViewOptions.TypedEffect.FADE).states
        assertEquals(List(5) { CharState.FADED }, fade.take(5))
        assertEquals(CharState.UNTYPED, fade[5])
    }

    @Test
    fun `memory hides the words once the test runs`() {
        val t = test()
        val before = view(t, ViewOptions(memory = true, highlight = ViewOptions.Highlight.OFF))
        assertEquals("the quick fox", before.lines.first().text)
        t.type("t")
        val after = view(t, ViewOptions(memory = true, highlight = ViewOptions.Highlight.OFF))
        assertEquals("t" + " ".repeat(12), after.lines.first().text)
    }

    @Test
    fun `palette flips and drops colors`() {
        val flip = Palette(flip = true)
        assertEquals(Role.SUB, flip.charRole(CharState.CORRECT))
        assertEquals(Role.TEXT, flip.charRole(CharState.UNTYPED))
        assertEquals(Role.ERROR, flip.charRole(CharState.ERROR))
        val mono = Palette(colorful = false)
        assertEquals(Role.TEXT, mono.charRole(CharState.ERROR))
        assertEquals(Role.TEXT, Palette().charRole(CharState.CORRECT))
        val s = view(test(), ViewOptions(flip = true, colorful = false))
        assertEquals(Palette(flip = true, colorful = false, liveRole = Role.MAIN, liveOpacity = 0.5), s.palette)
    }

    private val long = arrayOf("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta")

    @Test
    fun `tape letter mode keeps the caret still and scrolls the text`() {
        val t = test(*long)
        t.type("alpha b")
        val s = view(t, ViewOptions(tape = ViewOptions.Tape.LETTER, tapeMargin = 50, width = 30, highlight = ViewOptions.Highlight.OFF))
        assertEquals(1, s.lines.size)
        assertEquals(30, s.lines.first().text.length)
        assertEquals(0, s.caretLine)
        assertEquals(15, s.caretColumn)
        val text = t.target.text
        assertEquals(text[7], s.lines.first().text[15]) // the next letter is under the caret
        assertEquals(text[6], s.lines.first().text[14])
        assertEquals(' ', s.lines.first().text[0]) // before the start of the text
    }

    @Test
    fun `tape word mode moves the caret within the word and jumps per word`() {
        val t = test(*long)
        t.type("alpha b")
        val s = view(t, ViewOptions(tape = ViewOptions.Tape.WORD, tapeMargin = 25, width = 40, highlight = ViewOptions.Highlight.OFF))
        val text = t.target.text
        assertEquals(10 + 1, s.caretColumn) // margin 25% of 40 = 10, one letter into the word
        assertEquals(text[7], s.lines.first().text[s.caretColumn])
        assertEquals(text[6], s.lines.first().text[10]) // the word starts at the margin
    }

    @Test
    fun `live stats follow the chosen styles`() {
        val t = test()
        t.type("the q") // 1 word done, one wrong later
        val now = 2_000 * ms
        fun live(o: ViewOptions) = view(t, o.copy(highlight = ViewOptions.Highlight.OFF), now).live.joinToString("") { it.text }
        assertEquals("1/3", live(ViewOptions(progress = ViewOptions().progress, speed = LiveStyle.OFF)).trim())
        assertTrue("wpm" in live(ViewOptions(progress = LiveStyle.OFF, speed = LiveStyle.TEXT)))
        assertTrue("wpm" !in live(ViewOptions(progress = LiveStyle.OFF, speed = LiveStyle.MINI)))
        assertEquals("", live(ViewOptions(progress = LiveStyle.OFF, speed = LiveStyle.OFF)))
        assertTrue(live(ViewOptions(progress = LiveStyle.OFF, speed = LiveStyle.OFF, accuracy = LiveStyle.TEXT)).contains("100%"))
        for (r in view(t, ViewOptions(), now).live) assertEquals(Role.LIVE, r.role)
    }

    @Test
    fun `live accuracy and burst`() {
        val t = test()
        t.type("thx qui")
        val a = view(t, ViewOptions(progress = LiveStyle.OFF, speed = LiveStyle.OFF, accuracy = LiveStyle.MINI)).live.joinToString("") { it.text }
        assertEquals("71%", a) // 5 of 7 keys were right
        val b = view(t, ViewOptions(progress = LiveStyle.OFF, speed = LiveStyle.OFF, burst = LiveStyle.TEXT)).live.joinToString("") { it.text }
        assertTrue(b.endsWith("wpm burst"), b)
    }

    @Test
    fun `live speed uses the chosen unit`() {
        val t = test()
        t.type("the quick ", stepMs = 200)
        val wpm = view(t, ViewOptions(progress = LiveStyle.OFF, speed = LiveStyle.TEXT), now = 2_000 * ms).live.joinToString("") { it.text }
        val cpm = view(t, ViewOptions(progress = LiveStyle.OFF, speed = LiveStyle.TEXT, unit = SpeedUnit.CPM), now = 2_000 * ms).live.joinToString("") { it.text }
        assertTrue(wpm.endsWith(" wpm"))
        assertTrue(cpm.endsWith(" cpm"))
        assertEquals(wpm.substringBefore(' ').toInt() * 5, cpm.substringBefore(' ').toInt())
    }

    @Test
    fun `progress bar and time left`() {
        val t = test()
        t.type("the ")
        val bar = view(t, ViewOptions(progress = LiveStyle.BAR, speed = LiveStyle.OFF)).bar
        assertNotNull(bar)
        assertEquals(1.0 / 3, bar!!, 0.001)
        assertNull(view(t, ViewOptions(progress = LiveStyle.MINI)).bar)

        val timed = test(*long, mode = TestMode.Time(30))
        timed.type("alpha ")
        val left = view(timed, ViewOptions(progress = LiveStyle.TEXT, speed = LiveStyle.OFF), now = 5_000 * ms).live.joinToString("") { it.text }
        assertEquals("25s", left)
        assertEquals(0.0, view(test(*long, mode = TestMode.Time(30)), ViewOptions(progress = LiveStyle.BAR)).bar!!, 0.0001)
    }

    @Test
    fun `view options are read from the settings`() {
        val map = mapOf(
            "blind-mode" to "on", "highlight-mode" to "next two words", "typed-effect" to "dots", "tape-mode" to "word",
            "tape-margin" to "25", "flip-test-colors" to "on", "colorful-mode" to "off", "funbox" to "memory",
            "live-progress-style" to "bar", "live-speed-style" to "text", "live-accuracy-style" to "mini",
            "live-word-burst-style" to "text", "live-progress-color" to "black", "live-progress-opacity" to "0.75",
            "typing-speed-unit" to "cps", "always-show-decimal-places" to "on", "start-graphs-at-zero" to "off",
            "show-key-tips" to "hide", "max-line-width" to "30", "hide-extra-letters" to "on", "indicate-typos" to "replace",
        )
        val o = ViewOptions.from { map[it] ?: "off" }
        assertTrue(o.blind && o.flip && !o.colorful && o.memory && o.decimals && !o.startGraphsAtZero && !o.keyTips && o.hideExtra && o.showTypos)
        assertEquals(ViewOptions.Highlight.NEXT_TWO, o.highlight)
        assertEquals(ViewOptions.TypedEffect.DOTS, o.effect)
        assertEquals(ViewOptions.Tape.WORD, o.tape)
        assertEquals(25, o.tapeMargin)
        assertEquals(LiveStyle.BAR, o.progress)
        assertEquals(LiveStyle.TEXT, o.speed)
        assertEquals(LiveStyle.MINI, o.accuracy)
        assertEquals(Role.BG, o.liveColor)
        assertEquals(0.75, o.liveOpacity)
        assertEquals(SpeedUnit.CPS, o.unit)
        assertEquals(30, o.width)
    }

    @Test
    fun `speed units convert and format`() {
        assertEquals(100.0, SpeedUnit.WPM.convert(100.0))
        assertEquals(500.0, SpeedUnit.CPM.convert(100.0))
        assertEquals(8.33, SpeedUnit.CPS.convert(100.0), 0.01)
        assertEquals(1.67, SpeedUnit.WPS.convert(100.0), 0.01)
        assertEquals(6000.0, SpeedUnit.WPH.convert(100.0))
        assertEquals("112", SpeedUnit.WPM.format(112.35))
        assertEquals("112.35", SpeedUnit.WPM.format(112.35, decimals = true))
        assertEquals("1.67", SpeedUnit.WPS.format(100.0)) // small units always show decimals
        assertEquals(SpeedUnit.WPM, SpeedUnit.of("bogus"))
    }

    @Test
    fun `show average adds the last ten tests to the config bar`() {
        val stats = com.javaniac.blockytype.db.PlayerStats(best = 120.0, last = 100.0, average = 104.4, accuracy = 96.2)
        fun config(a: ViewOptions.AverageShown, st: com.javaniac.blockytype.db.PlayerStats? = stats) =
            TypingView.build(test(), 0, "english", ViewOptions(average = a), st).config.joinToString("") { it.text }
        assertEquals("words 3 | english", config(ViewOptions.AverageShown.OFF))
        assertEquals("words 3 | english | avg 104 wpm", config(ViewOptions.AverageShown.SPEED))
        assertEquals("words 3 | english | avg 96%", config(ViewOptions.AverageShown.ACC))
        assertEquals("words 3 | english | avg 104 wpm 96%", config(ViewOptions.AverageShown.BOTH))
        assertEquals("words 3 | english", config(ViewOptions.AverageShown.BOTH, null)) // no results yet
    }
}
