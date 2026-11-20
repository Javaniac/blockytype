package com.javaniac.blockytype.settings

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsFileTest {
    private val specs = listOf(
        SettingSpec("server-port", "Port of the game server.\nNeeds a restart.", "25565") { it.toIntOrNull() in 1..65535 },
        SettingSpec("font", "Font of the typing test.", "jetbrains-mono") { it.isNotBlank() },
        SettingSpec("input-box", "Show the input box.", "show") { it in listOf("show", "hide") },
    )

    private fun file(initial: String? = null): SettingsFile {
        val dir = Files.createTempDirectory("settings-test")
        val path: Path = dir.resolve("blockytype.properties")
        if (initial != null) path.writeText(initial)
        return SettingsFile(path, specs)
    }

    @Test
    fun `a missing file is created with every key, default and comment`() {
        val f = file()
        val values = f.load()
        assertEquals(mapOf("server-port" to "25565", "font" to "jetbrains-mono", "input-box" to "show"), values)
        val text = f.path.readText()
        assertTrue("# Port of the game server.\n# Needs a restart.\nserver-port=25565" in text, text)
        assertTrue("# Font of the typing test.\nfont=jetbrains-mono" in text)
        assertTrue(text.startsWith("# BlockyType"), text)
    }

    @Test
    fun `set changes only that value and keeps comments, unknown lines and order`() {
        val f = file()
        f.load()
        f.path.writeText(f.path.readText() + "\n# my own note\nextra=1\n")
        f.set("server-port", "25570")
        f.set("font", "fira-code")
        val text = f.path.readText()
        assertTrue("# Port of the game server.\n# Needs a restart.\nserver-port=25570" in text)
        assertTrue("# Font of the typing test.\nfont=fira-code" in text)
        assertTrue("# my own note\nextra=1" in text)
        assertTrue(text.indexOf("server-port") < text.indexOf("font=") && text.indexOf("font=") < text.indexOf("input-box="))
        assertEquals(1, Regex("(?m)^server-port=").findAll(text).count())
    }

    @Test
    fun `comments survive many writes`() {
        val f = file()
        f.load()
        val before = f.path.readText().lines().filter { it.startsWith("#") }
        repeat(5) { f.set("input-box", if (it % 2 == 0) "hide" else "show") }
        f.set("server-port", "1234")
        assertEquals(before, f.path.readText().lines().filter { it.startsWith("#") })
        assertEquals("1234", f.load()["server-port"])
    }

    @Test
    fun `a key missing from an older file is added with its comment`() {
        val f = file("# old file\nfont=hack\n")
        val values = f.load()
        assertEquals("hack", values["font"])
        val text = f.path.readText()
        assertTrue(text.startsWith("# old file\nfont=hack\n"))
        assertTrue("# Show the input box.\ninput-box=show" in text)
    }

    @Test
    fun `an invalid value is dropped from the result but left in the file`() {
        val f = file("server-port=99999\nfont=hack\ninput-box=maybe\n")
        val values = f.load()
        assertNull(values["server-port"])
        assertNull(values["input-box"])
        assertEquals("hack", values["font"])
        assertTrue("server-port=99999" in f.path.readText())
    }

    @Test
    fun `set rejects invalid values and unknown keys`() {
        val f = file()
        f.load()
        assertFailsWith<IllegalArgumentException> { f.set("server-port", "abc") }
        assertFailsWith<IllegalArgumentException> { f.set("nope", "x") }
        assertEquals("25565", f.load()["server-port"])
    }

    @Test
    fun `reset writes the default back`() {
        val f = file()
        f.load()
        f.set("font", "hack")
        f.reset("font")
        assertEquals("jetbrains-mono", f.load()["font"])
    }

    @Test
    fun `the old show-input key becomes the input box setting`() {
        assertEquals("hide", file("show-input=false\n").load()["input-box"])
        assertEquals("show", file("show-input=true\n").load()["input-box"])
        assertEquals("show", file("show-input=false\ninput-box=show\n").load()["input-box"])
    }

    @Test
    fun `database values are migrated once for keys the file does not have`() {
        val f = file("font=hack\n")
        val values = f.load(legacy = mapOf("server-port" to "25570", "font" to "fira-code", "input-box" to "bad"))
        assertEquals("25570", values["server-port"])
        assertEquals("hack", values["font"]) // the file wins
        assertEquals("show", values["input-box"]) // invalid database values are not migrated
        assertTrue("server-port=25570" in f.path.readText())
    }

    @Test
    fun `loading an unchanged file does not rewrite it`() {
        val f = file()
        f.load()
        val text = f.path.readText()
        val stamp = Files.getLastModifiedTime(f.path)
        Thread.sleep(20)
        f.load()
        assertEquals(text, f.path.readText())
        assertEquals(stamp, Files.getLastModifiedTime(f.path))
    }

    @Test
    fun `the real specs all have a comment and a valid default`() {
        val real = ServerSettingSpecs.all()
        assertEquals(
            listOf("server-ip", "server-port", "resource-pack-ip", "resource-pack-port", "font", "input-box", "animation", "anti-aliasing", "theme"),
            real.map { it.key },
        )
        for (s in real) {
            assertTrue(s.comment.length > 15, "${s.key} needs a comment")
            assertTrue(s.valid(s.default), "${s.key} default '${s.default}' is not valid")
        }
    }
}
