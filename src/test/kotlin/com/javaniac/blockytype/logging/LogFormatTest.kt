package com.javaniac.blockytype.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.LoggingEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogFormatTest {
    private val esc = 27.toChar()
    private val ansi = Regex(esc + Regex.escape("[") + "[0-9;]*m")

    private fun pattern(): String {
        val xml = LogFormatTest::class.java.getResource("/logback.xml")!!.readText()
        return Regex("<pattern>(.*?)</pattern>").find(xml)!!.groupValues[1].replace("&#27;", "\u001B")
    }

    private fun render(level: Level, logger: String, message: String): String {
        val ctx = LoggerContext()
        val layout = PatternLayout().apply { context = ctx; pattern = pattern(); start() }
        val event = LoggingEvent("x", ctx.getLogger(logger), level, message, null, null)
        return layout.doLayout(event)
    }

    @Test
    fun `a line has date, time with seconds, level, class and message in that order`() {
        val line = ansi.replace(render(Level.INFO, "com.javaniac.blockytype.pack.PackServer", "pack ready"), "").trimEnd()
        assertTrue(
            Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} +INFO +PackServer +pack ready""").matches(line),
            "unexpected format: '$line'",
        )
    }

    @Test
    fun `the class column is the simple class name and levels line up`() {
        val warn = ansi.replace(render(Level.WARN, "a.b.Foo", "m"), "")
        val error = ansi.replace(render(Level.ERROR, "a.b.Foo", "m"), "")
        assertEquals(warn.indexOf("Foo"), error.indexOf("Foo"))
        assertTrue("a.b" !in warn)
    }

    @Test
    fun `levels are colored`() {
        assertTrue(ansi.containsMatchIn(render(Level.ERROR, "a.B", "m")))
    }
}
