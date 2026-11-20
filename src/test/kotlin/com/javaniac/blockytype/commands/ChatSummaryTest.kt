package com.javaniac.blockytype.commands

import com.javaniac.blockytype.game.CharCounts
import com.javaniac.blockytype.game.SecondStat
import com.javaniac.blockytype.game.SpeedUnit
import com.javaniac.blockytype.game.TestResult
import com.javaniac.blockytype.game.TestRules
import com.javaniac.blockytype.themes.DefaultTheme
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChatSummaryTest {
    private val result = TestResult(
        wpm = 112.4, raw = 118.2, accuracy = 96.7,
        counts = CharCounts(correct = 263, incorrect = 4, extra = 1, missed = 0, correctWordChars = 250, rawChars = 270),
        consistency = 74.3, seconds = 30.24,
        perSecond = listOf(SecondStat(100.0, 110.0, 1), SecondStat(112.0, 130.0, 3)),
    )

    private fun plain(c: Component) = PlainTextComponentSerializer.plainText().serialize(c)

    private fun lines(level: String, best: Double? = 120.0, avg: Double? = 104.4, unit: SpeedUnit = SpeedUnit.WPM, decimals: Boolean = false) =
        ChatSummary.build(result, "words 25", "english", unit, decimals, level, best, avg, DefaultTheme).map(::plain)

    @Test
    fun `off sends nothing`() {
        assertEquals(emptyList(), lines("off"))
    }

    @Test
    fun `short is one line with the headline numbers`() {
        assertEquals(listOf("» 112 wpm  97% acc  raw 118  74% cons"), lines("short"))
    }

    @Test
    fun `full adds mode, time, personal best and average`() {
        assertEquals(
            listOf(
                "» 112 wpm  97% acc  raw 118  74% cons",
                "  words 25 english  ·  30.2s  ·  pb 120 (-8)  ·  avg10 104",
            ),
            lines("full"),
        )
    }

    @Test
    fun `a new personal best is marked and shows the gain`() {
        assertEquals("  words 25 english  ·  30.2s  ·  new pb ★ (+4)  ·  avg10 104", lines("full", best = 108.0)[1])
        assertEquals("  words 25 english  ·  30.2s  ·  new pb ★  ·  avg10 104", lines("full", best = null)[1])
        assertEquals("» 112 wpm  97% acc  raw 118  74% cons ★", lines("short", best = 108.0)[0])
    }

    @Test
    fun `missing average is left out`() {
        assertEquals("  words 25 english  ·  30.2s  ·  pb 120 (-8)", lines("full", avg = null)[1])
    }

    @Test
    fun `the speed unit and decimals apply`() {
        assertEquals("» 562 cpm  97% acc  raw 591  74% cons", lines("short", unit = SpeedUnit.CPM)[0])
        assertEquals("» 112.40 wpm  96.70% acc  raw 118.20  74.30% cons", lines("short", decimals = true)[0])
    }

    @Test
    fun `hover has the details and a click restarts`() {
        val first = ChatSummary.build(result, "words 25", "english", SpeedUnit.WPM, false, "full", 120.0, 104.4, DefaultTheme).first()
        val hover = plain(first.hoverEvent()!!.value() as Component)
        assertTrue("263/4/1/0" in hover, hover)
        assertTrue("errors 4" in hover, hover)
        assertTrue("peak raw 130" in hover, hover)
        assertEquals(ClickEvent.Action.RUN_COMMAND, first.clickEvent()!!.action())
        assertEquals("/restart", (first.clickEvent()!!.payload() as ClickEvent.Payload.Text).value())
    }

    @Test
    fun `colors come from the theme`() {
        val first = ChatSummary.build(result, "words 25", "english", SpeedUnit.WPM, false, "short", 120.0, null, DefaultTheme).first()
        assertEquals(DefaultTheme.caretColor, first.children().first().color())
        assertNotNull(first.children().firstOrNull { it.color() == DefaultTheme.mainColor })
    }

    @Test
    fun `a failed test says why in one line`() {
        fun failed(reason: String, rules: TestRules = TestRules()) = plain(ChatSummary.failed(reason, rules, DefaultTheme))
        assertEquals("» failed: expert, a wrong word was submitted", failed("expert"))
        assertEquals("» failed: master, a key was wrong", failed("master"))
        assertEquals("» failed: speed fell below 60 wpm", failed("min speed", TestRules(minSpeed = 60)))
        assertEquals("» failed: accuracy fell below 95%", failed("min accuracy", TestRules(minAccuracy = 95)))
        assertEquals("» failed: a word was slower than 30 wpm", failed("min word burst"))
        assertEquals("» failed: something else", failed("something else"))
    }

    @Test
    fun `player chat shows the best wpm before the name`() {
        assertEquals("[112] Alice » hello", plain(ChatSummary.playerChat(DefaultTheme, "Alice", 112.4, SpeedUnit.WPM, Component.text("hello"))))
        assertEquals("[-] Bob » hi", plain(ChatSummary.playerChat(DefaultTheme, "Bob", null, SpeedUnit.WPM, Component.text("hi"))))
        assertEquals("[562] Alice » hello", plain(ChatSummary.playerChat(DefaultTheme, "Alice", 112.4, SpeedUnit.CPM, Component.text("hello"))))
    }

    @Test
    fun `admins get the server admin prefix in chat`() {
        assertEquals(
            "[Server Admin] [112] Alice » hello",
            plain(ChatSummary.playerChat(DefaultTheme, "Alice", 112.4, SpeedUnit.WPM, Component.text("hello"), admin = true)),
        )
        assertEquals(
            "[Server Admin] [-] Bob » hi",
            plain(ChatSummary.playerChat(DefaultTheme, "Bob", null, SpeedUnit.WPM, Component.text("hi"), admin = true)),
        )
        assertEquals("[112] Alice » hello", plain(ChatSummary.playerChat(DefaultTheme, "Alice", 112.4, SpeedUnit.WPM, Component.text("hello"), admin = false)))
    }
}
