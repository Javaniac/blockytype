package com.javaniac.blockytype.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsCatalogTest {
    private val fixedSettings = SettingsCatalog.all.filter { it.options.isFixed }

    @Test
    fun `ids are unique kebab case`() {
        val ids = SettingsCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate ids")
        for (id in ids) assertTrue(Regex("[a-z0-9]+(-[a-z0-9]+)*").matches(id), "bad id $id")
    }

    @Test
    fun `fixed options contain the default and have no duplicates`() {
        for (s in fixedSettings) {
            val options = s.options.values()
            assertTrue(s.default in options, "${s.id}: default '${s.default}' not in $options")
            assertEquals(options.size, options.toSet().size, "${s.id}: duplicate options")
        }
    }

    @Test
    fun `every section from monkeytype is present and used`() {
        for (section in Section.entries) {
            assertTrue(SettingsCatalog.all.any { it.section == section }, "empty section ${section.label}")
        }
        assertEquals(
            listOf("behavior", "input", "sound", "caret", "appearance", "theme", "hide elements", "danger zone"),
            Section.entries.map { it.label },
        )
    }

    @Test
    fun `the settings that have server defaults are exactly the ones that work now`() {
        val serverDefaults = SettingsCatalog.all.filter { it.serverDefault }.map { it.id }.toSet()
        assertEquals(setOf("theme", "font", "animation", "anti-aliasing", "input-box"), serverDefaults)
        for (id in serverDefaults) assertTrue(SettingsCatalog.byId(id)!!.implemented, "$id must be implemented")
    }

    @Test
    fun `monkeytype settings are listed`() {
        for (id in listOf("difficulty", "stop-on-error", "confidence-mode", "caret-style", "pace-caret", "highlight-mode", "typing-speed-unit", "show-key-tips")) {
            assertNotNull(SettingsCatalog.byId(id), id)
        }
        assertNull(SettingsCatalog.byId("nope"))
    }

    @Test
    fun `effective value is user then server then catalog default`() {
        val animation = SettingsCatalog.byId("animation")!!
        assertEquals(animation.default, SettingsCatalog.effective("animation", user = emptyMap(), server = emptyMap()))
        assertEquals("off", SettingsCatalog.effective("animation", user = emptyMap(), server = mapOf("animation" to "off")))
        assertEquals("on", SettingsCatalog.effective("animation", user = mapOf("animation" to "on"), server = mapOf("animation" to "off")))
    }

    @Test
    fun `invalid stored values are ignored`() {
        val default = SettingsCatalog.byId("animation")!!.default
        assertEquals(default, SettingsCatalog.effective("animation", user = mapOf("animation" to "bogus"), server = emptyMap()))
        assertEquals("off", SettingsCatalog.effective("animation", user = mapOf("animation" to "bogus"), server = mapOf("animation" to "off")))
    }

    @Test
    fun `user cannot override a setting nobody may change`() {
        assertEquals(false, SettingsCatalog.isValid("nope", "x"))
        assertEquals(true, SettingsCatalog.isValid("animation", "off"))
        assertEquals(false, SettingsCatalog.isValid("animation", "sideways"))
    }

    @Test
    fun `test mode settings exist with monkeytype presets`() {
        assertEquals(listOf("words", "time"), SettingsCatalog.byId("test-mode")!!.options.values())
        assertEquals(listOf("10", "25", "50", "100"), SettingsCatalog.byId("word-count")!!.options.values())
        assertEquals(listOf("15", "30", "60", "120"), SettingsCatalog.byId("time-limit")!!.options.values())
        assertEquals("words", SettingsCatalog.byId("test-mode")!!.default)
        assertEquals("25", SettingsCatalog.byId("word-count")!!.default)
        assertEquals("30", SettingsCatalog.byId("time-limit")!!.default)
        for (id in listOf("test-mode", "word-count", "time-limit", "language")) {
            assertTrue(SettingsCatalog.byId(id)!!.implemented, id)
        }
    }

    @Test
    fun `settings that cannot work in blockytype are gone`() {
        val removed = listOf(
            "opposite-shift-mode", "composition-display", "layout", "code-unindent-on-backspace",
            "show-out-of-focus-warning", "caps-lock-warning", "repeat-quotes", "always-show-words-history",
            "single-list-command-line", "keymap-mode", "smooth-line-scroll", "show-all-lines", "repeated-pace",
        )
        for (id in removed) assertNull(SettingsCatalog.byId(id), id)
    }

    @Test
    fun `removed option values are gone`() {
        fun values(id: String) = SettingsCatalog.byId(id)!!.options.values()
        assertEquals(listOf("off", "enter"), values("quick-restart"))
        assertEquals(listOf("off", "replace"), values("indicate-typos"))
        assertEquals(listOf("off", "bar", "text", "mini"), values("live-progress-style"))
        assertEquals(listOf("1", "1.5", "2"), values("font-size"))
        assertEquals(listOf("30", "40", "50"), values("max-line-width"))
        assertEquals(listOf("off", "average", "pb", "last", "custom"), values("pace-caret"))
        assertEquals(listOf("off", "on", "light", "dark"), values("random-theme"))
        val funbox = values("funbox")
        for (gone in listOf("mirror", "upside down", "nausea", "tts", "arrows", "earthquake", "wikipedia", "no quit")) {
            assertTrue(gone !in funbox, gone)
        }
        for (kept in listOf("off", "zipf", "rot13", "ALL CAPS", "memory", "binary")) assertTrue(kept in funbox, kept)
    }

    @Test
    fun `sound options cover every monkeytype set`() {
        assertEquals(27, SettingsCatalog.byId("play-sound-on-click")!!.options.values().size)
        assertEquals(5, SettingsCatalog.byId("play-sound-on-error")!!.options.values().size)
        assertTrue(SettingsCatalog.isValid("play-sound-on-click", "razer green"))
        assertTrue(SettingsCatalog.byId("chat-summary") != null)
    }

    @Test
    fun `every remaining setting does something`() {
        val inert = SettingsCatalog.all.filter { !it.implemented }.map { it.id }
        assertEquals(emptyList(), inert)
    }

    @Test
    fun `language is a paged list of all word lists`() {
        val language = SettingsCatalog.byId("language")!!
        assertTrue(language.options.paged)
        assertEquals(com.javaniac.blockytype.words.LanguageRegistry.names(), language.options.values())
        assertEquals("english", language.default)
        assertTrue(SettingsCatalog.isValid("language", "german"))
        assertEquals(false, SettingsCatalog.isValid("language", "klingon"))
    }
}
