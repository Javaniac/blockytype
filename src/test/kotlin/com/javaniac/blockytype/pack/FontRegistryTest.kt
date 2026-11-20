package com.javaniac.blockytype.pack

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FontRegistryTest {
    private fun fixture(name: String) = FontRegistryTest::class.java.getResourceAsStream("/fonts/$name")!!.use { it.readBytes() }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private val originalRoot = FontRegistry.root
    private val originalBuiltins = FontRegistry.builtins
    private lateinit var root: Path

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("fonts")
        FontRegistry.root = root
        FontRegistry.builtins = listOf(builtin("test-mono"))
    }

    @AfterTest
    fun tearDown() {
        FontRegistry.root = originalRoot
        FontRegistry.builtins = originalBuiltins
    }

    private fun builtin(name: String) = BuiltinFont(name, "http://unused/$name.ttf", sha(fixture("test-mono.ttf")), "http://unused/OFL.txt", "0".repeat(64))

    private val builtinDir get() = root.resolve("builtin")
    private val customDir get() = root.resolve("custom")

    @Test
    fun `init creates the folders, the readme and the bundled font with its license`() {
        FontRegistry.init()
        assertTrue(builtinDir.exists() && customDir.exists())
        val readme = root.resolve("README.txt").readText().lowercase()
        assertTrue("custom" in readme && "monospace" in readme, readme)
        assertTrue(builtinDir.resolve("jetbrains-mono.ttf").exists())
        assertTrue(builtinDir.resolve("jetbrains-mono.license.txt").readText().contains("OFL", ignoreCase = true))
    }

    @Test
    fun `init keeps an existing readme`() {
        Files.createDirectories(root)
        root.resolve("README.txt").toFile().writeText("mine")
        FontRegistry.init()
        assertEquals("mine", root.resolve("README.txt").readText())
    }

    @Test
    fun `the default font comes first and works without any download`() {
        FontRegistry.init()
        assertEquals(listOf("jetbrains-mono"), FontRegistry.names())
        assertEquals("jetbrains-mono", FontRegistry.load("jetbrains-mono").name)
    }

    @Test
    fun `a downloaded builtin font is listed after the default`() {
        FontRegistry.init()
        builtinDir.resolve("test-mono.ttf").writeBytes(fixture("test-mono.ttf"))
        assertEquals(listOf("jetbrains-mono", "test-mono"), FontRegistry.names())
        assertEquals("test-mono", FontRegistry.load("test-mono").name)
    }

    @Test
    fun `a file that is not a builtin font is moved from builtin to custom`() {
        FontRegistry.init()
        builtinDir.resolve("my-font.ttf").writeBytes(fixture("test-mono.ttf"))
        builtinDir.resolve("my-font.license.txt").toFile().writeText("license")
        val moved = FontRegistry.init().movedToCustom
        assertEquals(setOf("my-font.ttf", "my-font.license.txt"), moved.toSet())
        assertFalse(builtinDir.resolve("my-font.ttf").exists())
        assertTrue(customDir.resolve("my-font.ttf").exists() && customDir.resolve("my-font.license.txt").exists())
        assertTrue("my-font" in FontRegistry.names())
    }

    @Test
    fun `a custom monospace font is listed`() {
        FontRegistry.init()
        customDir.resolve("mine.ttf").writeBytes(fixture("test-mono.ttf"))
        assertTrue("mine" in FontRegistry.names())
        assertEquals("mine", FontRegistry.load("mine").name)
    }

    @Test
    fun `invalid custom fonts are neither listed nor loadable`() {
        FontRegistry.init()
        customDir.resolve("proportional.ttf").writeBytes(fixture("proportional.ttf"))
        customDir.resolve("thin.ttf").writeBytes(fixture("thin-mono.ttf"))
        customDir.resolve("garbage.ttf").writeBytes("not a font".toByteArray())
        assertEquals(listOf("jetbrains-mono"), FontRegistry.names())
        for (n in listOf("proportional", "thin", "garbage")) assertFailsWith<IllegalArgumentException> { FontRegistry.load(n) }
        assertEquals("jetbrains-mono", FontRegistry.loadOrBundled("proportional").name)
    }

    @Test
    fun `a font that changes on disk is checked again`() {
        FontRegistry.init()
        val f = customDir.resolve("swap.ttf")
        f.writeBytes(fixture("proportional.ttf"))
        assertFalse("swap" in FontRegistry.names())
        f.writeBytes(fixture("test-mono.ttf"))
        Files.setLastModifiedTime(f, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000))
        assertTrue("swap" in FontRegistry.names())
    }

    @Test
    fun `a custom font cannot take the name of a builtin one`() {
        FontRegistry.init()
        builtinDir.resolve("test-mono.ttf").writeBytes(fixture("test-mono.ttf"))
        customDir.resolve("test-mono.ttf").writeBytes(fixture("test-mono.ttf"))
        assertEquals(1, FontRegistry.names().count { it == "test-mono" })
    }

    @Test
    fun `save stores a valid font in custom and refuses builtin names and bad fonts`() {
        FontRegistry.init()
        FontRegistry.save("fresh", fixture("test-mono.ttf"), "ttf")
        assertTrue(customDir.resolve("fresh.ttf").exists())
        assertFailsWith<IllegalArgumentException> { FontRegistry.save("test-mono", fixture("test-mono.ttf"), "ttf") }
        assertFailsWith<IllegalArgumentException> { FontRegistry.save("jetbrains-mono", fixture("test-mono.ttf"), "ttf") }
        assertFailsWith<IllegalArgumentException> { FontRegistry.save("prop", fixture("proportional.ttf"), "ttf") }
        assertFailsWith<IllegalArgumentException> { FontRegistry.save("odd", fixture("test-mono.ttf"), "exe") }
        assertFalse(customDir.resolve("prop.ttf").exists())
    }

    @Test
    fun `licenses come from the jar, the builtin folder or next to a custom font`() {
        FontRegistry.init()
        assertTrue(FontRegistry.licenseOf("jetbrains-mono")!!.decodeToString().contains("OFL", ignoreCase = true))
        builtinDir.resolve("test-mono.license.txt").toFile().writeText("builtin license")
        assertEquals("builtin license", FontRegistry.licenseOf("test-mono")!!.decodeToString())
        customDir.resolve("mine.ttf").writeBytes(fixture("test-mono.ttf"))
        assertNull(FontRegistry.licenseOf("mine"))
        customDir.resolve("mine.license.txt").toFile().writeText("custom license")
        assertEquals("custom license", FontRegistry.licenseOf("mine")!!.decodeToString())
    }

    @Test
    fun `the pack carries the license of its font`() {
        FontRegistry.init()
        val files = PackBuilder.build(font = FontRegistry.load("jetbrains-mono"))
        assertNotNull(files["licenses/jetbrains-mono.txt"])
    }

    @Test
    fun `fonts are checked like a real font loader would`() {
        assertEquals("test-mono", FontInfo.load("test-mono", fixture("test-mono.ttf"), "ttf").name)
        val thin = assertFailsWith<IllegalArgumentException> { FontInfo.load("t", fixture("thin-mono.ttf"), "ttf") }
        assertTrue("thin" in thin.message!!, thin.message)
        val prop = assertFailsWith<IllegalArgumentException> { FontInfo.load("p", fixture("proportional.ttf"), "ttf") }
        assertTrue("monospace" in prop.message!!, prop.message)
    }
}
