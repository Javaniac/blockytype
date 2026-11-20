package com.javaniac.blockytype.ui

import com.javaniac.blockytype.game.TargetText
import com.javaniac.blockytype.game.TypingTest
import com.javaniac.blockytype.themes.DefaultTheme
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogoTest {
    @Test
    fun `the logo is an 8 by 8 grid of known cells`() {
        assertEquals(8, Logo.GRID.size)
        for (row in Logo.GRID) {
            assertEquals(8, row.length)
            assertTrue(row.all { it in ".#b" }, row)
        }
    }

    @Test
    fun `the corners are cut and the letter is drawn`() {
        for ((r, c) in listOf(0 to 0, 0 to 7, 7 to 0, 7 to 7)) assertEquals('.', Logo.GRID[r][c])
        assertTrue(Logo.GRID.sumOf { row -> row.count { it == 'b' } } > 10, "letter and caret")
        assertEquals(Logo.GRID.map { it.reversed() }.size, 8)
    }

    @Test
    fun `in game pixels use the theme colors`() {
        val p = Logo.pixels(DefaultTheme)
        assertEquals(64, p.size)
        assertEquals(setOf(DefaultTheme.mainColor.value(), DefaultTheme.bgColor.value()), p.toSet())
        assertEquals(DefaultTheme.bgColor.value(), p[0], "a cut corner shows the background")
        assertEquals(DefaultTheme.mainColor.value(), p[1])
    }

    @Test
    fun `the svg has one rect per run and no corner`() {
        val svg = Logo.svg(DefaultTheme)
        assertTrue(svg.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 8 8\""))
        assertTrue("<title>BlockyType</title>" in svg)
        assertTrue("fill=\"#E2B714\"" in svg && "fill=\"#323437\"" in svg)
        assertTrue(svg.endsWith("</svg>\n"))
        val rects = Regex("<rect ").findAll(svg).count()
        val runs = Logo.GRID.sumOf { row -> Regex("#+|b+").findAll(row).count() }
        assertEquals(runs, rects)
    }

    @Test
    fun `the png has the requested size and transparent corners`() {
        val img = ImageIO.read(ByteArrayInputStream(Logo.png(DefaultTheme, 64)))
        assertEquals(64, img.width)
        assertEquals(64, img.height)
        assertEquals(0, img.getRGB(0, 0) ushr 24, "corner is transparent")
        assertEquals(0xFF, img.getRGB(32, 4) ushr 24)
        assertEquals(DefaultTheme.mainColor.value(), img.getRGB(32, 4) and 0xFFFFFF)
    }

    @Test
    fun `the checked in logo files match the generator`() {
        val svg = Path.of("assets/logo.svg")
        assertEquals(Logo.svg(DefaultTheme), Files.readString(svg), "run the generator again: assets/logo.svg is out of date")
        val png = ImageIO.read(Path.of("assets/logo.png").toFile())
        assertEquals(512, png.width)
        val expected = ImageIO.read(ByteArrayInputStream(Logo.png(DefaultTheme, 512)))
        for (y in 0 until 512 step 37) for (x in 0 until 512 step 41) assertEquals(expected.getRGB(x, y), png.getRGB(x, y), "$x,$y")
    }

    @Test
    fun `the banner svg names the project`() {
        val banner = Logo.banner(DefaultTheme)
        assertTrue("BlockyType" in banner && "<svg" in banner && banner.endsWith("</svg>\n"))
        assertEquals(banner, Files.readString(Path.of("assets/banner.svg")), "assets/banner.svg is out of date")
    }

    @Test
    fun `the typing screen shows the logo and stays zero width`() {
        val title = PlainTextComponentSerializer.plainText().serialize(
            HudRenderer.render(TypingTest(TargetText.sample()).toHudState(0), DefaultTheme),
        )
        for (row in 0 until 8) assertTrue(title.any { it.code == UiFont.FACE_BASE + row }, "row $row")
    }
}
