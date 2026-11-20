package com.javaniac.blockytype.commands

import com.javaniac.blockytype.menu.FakeHost
import com.javaniac.blockytype.themes.AbstractTheme
import com.javaniac.blockytype.toColor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SettingsChatTest {
    private val host = FakeHost()

    private object Theme : AbstractTheme {
        override val name = "test"
        override val mainColor = 0x112233.toColor()
        override val errorColor = 0x445566.toColor()
        override val subColor = 0x778899.toColor()
        override val textColor = 0xAABBCC.toColor()
    }

    private fun run(vararg args: String): List<Component> = SettingsChat.run(host, args.toList()) { Theme }

    private fun Component.plain(): String = PlainTextComponentSerializer.plainText().serialize(this)

    /** The first part whose text is [text], searching all nested parts. */
    private fun Component.part(text: String): Component? {
        if (this is net.kyori.adventure.text.TextComponent && content() == text) return this
        for (c in children()) c.part(text)?.let { return it }
        return null
    }

    @Test
    fun `setting a valid value stores it and confirms`() {
        val out = run("animation", "off").single().plain()
        assertEquals("off", host.user["animation"])
        assertTrue("animation" in out && "off" in out, out)
    }

    @Test
    fun `values match ignoring case`() {
        run("theme", "NORD")
        assertEquals("nord", host.user["theme"])
    }

    @Test
    fun `an invalid value changes nothing and lists the choices`() {
        val out = run("animation", "sideways").single().plain()
        assertEquals(emptyList(), host.log)
        assertTrue("sideways" in out && "on" in out && "off" in out, out)
    }

    @Test
    fun `an unknown setting changes nothing`() {
        val out = run("nope", "x").single().plain()
        assertEquals(emptyList(), host.log)
        assertTrue("nope" in out)
    }

    @Test
    fun `only the setting shows its value and choices`() {
        host.user["animation"] = "off"
        val out = run("animation").single().plain()
        assertTrue("off" in out && "on" in out, out)
        assertEquals(emptyList(), host.log)
    }

    @Test
    fun `default clears the override of a server default setting`() {
        host.user["animation"] = "off"
        run("animation", "default")
        assertEquals(null, host.user["animation"])
    }

    @Test
    fun `default does nothing for a setting without a server default`() {
        host.user["difficulty"] = "expert"
        run("difficulty", "default")
        assertEquals("expert", host.user["difficulty"])
    }

    @Test
    fun `reset clears every override`() {
        host.user["theme"] = "nord"
        run("reset")
        assertEquals(emptyMap(), host.user)
    }

    @Test
    fun `the danger zone reset setting is not a value setting`() {
        assertTrue("reset-settings" !in SettingsChat.ids())
        run("reset-settings", "x")
        assertEquals(emptyList(), host.log)
    }

    @Test
    fun `no arguments list the settings grouped by section`() {
        val out = run().single().plain()
        assertTrue("animation" in out && "theme" in out && "font" in out, out)
        assertTrue("behavior" in out && "appearance" in out, out)
    }

    @Test
    fun `every message fits the chat`() {
        for (id in SettingsChat.ids()) {
            for (line in run(id)) assertTrue(line.plain().length < 1000, id)
        }
    }

    // ---- colors follow the theme

    @Test
    fun `the new value is drawn in the main color`() {
        val out = run("animation", "off").single()
        assertEquals(Theme.mainColor, out.part("off")?.color())
    }

    @Test
    fun `errors use the error color`() {
        assertEquals(Theme.errorColor, run("animation", "sideways").single().part("sideways")?.color())
        assertEquals(Theme.errorColor, run("nope").single().part("nope")?.color())
    }

    @Test
    fun `the current choice is main, the others sub, and each choice is clickable`() {
        host.user["animation"] = "off"
        val out = run("animation").single()
        assertEquals(Theme.mainColor, out.part("off")?.color())
        val on = assertNotNull(out.part("on"))
        assertEquals(Theme.subColor, on.color())
        assertTrue(on.clickEvent().toString().contains("/settings animation on"), on.clickEvent().toString())
    }

    @Test
    fun `the theme is read after the change so a theme switch colors its own message`() {
        var current: AbstractTheme = com.javaniac.blockytype.themes.DefaultTheme
        val out = SettingsChat.run(host, listOf("animation", "off")) { current }
        assertEquals(current.mainColor, out.single().part("off")?.color())
        current = Theme
        val out2 = SettingsChat.run(host, listOf("animation", "on")) { current }
        assertEquals(Theme.mainColor, out2.single().part("on")?.color())
    }

    @Test
    fun `a long choice list is shortened to fit the chat`() {
        host.themeChoices = (1..187).map { "theme_$it" }
        val shown = run("theme").single().plain()
        assertTrue(shown.length < 1000, "${shown.length}")
        assertTrue("theme_1" in shown && "187" in shown, shown)
        val bad = run("theme", "zzz").single().plain()
        assertTrue(bad.length < 1000, "${bad.length}")
    }
}
