package com.javaniac.blockytype.ui

import com.javaniac.blockytype.themes.AbstractTheme
import net.kyori.adventure.text.format.TextColor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * The BlockyType logo: a rounded tile with a block letter B, on an 8 x 8 grid so it is the same
 * picture in game (drawn like a player's face), in the server list icon, and in the files under `assets/`.
 */
object Logo {
    /** `.` is a cut corner, `#` the tile (main color) and `b` the letter (background color). */
    val GRID = listOf(
        ".######.",
        "#bbbb###",
        "#b###b##",
        "#bbbb###",
        "#b###b##",
        "#b###b##",
        "#bbbb###",
        ".######.",
    )

    private fun hex(c: TextColor) = "#%06X".format(c.value())

    private fun color(cell: Char, theme: AbstractTheme): TextColor = if (cell == 'b') theme.bgColor else theme.mainColor

    /** The 64 pixels as `0xRRGGBB`, row by row. A cut corner shows the background, as the HUD has no transparency. */
    fun pixels(theme: AbstractTheme): IntArray = IntArray(64) { i ->
        val cell = GRID[i / 8][i % 8]
        if (cell == '.') theme.bgColor.value() else color(cell, theme).value()
    }

    /** One `<rect>` per horizontal run of the same cell, shifted by ([dx], [dy]); corners are left out. */
    private fun rects(theme: AbstractTheme, indent: String): String = buildString {
        for ((y, row) in GRID.withIndex()) {
            for (run in Regex("#+|b+").findAll(row)) {
                append("$indent<rect x=\"${run.range.first}\" y=\"$y\" width=\"${run.value.length}\" height=\"1\" fill=\"${hex(color(run.value[0], theme))}\"/>\n")
            }
        }
    }

    /** The logo as a square SVG with a transparent background. */
    fun svg(theme: AbstractTheme): String =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 8 8\" width=\"256\" height=\"256\" shape-rendering=\"crispEdges\">\n" +
            "  <title>BlockyType</title>\n" + rects(theme, "  ") + "</svg>\n"

    /** The logo next to the name, for the top of the readme. */
    fun banner(theme: AbstractTheme): String =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 720 200\" width=\"720\" height=\"200\">\n" +
            "  <title>BlockyType</title>\n" +
            "  <rect width=\"720\" height=\"200\" rx=\"16\" fill=\"${hex(theme.bgColor)}\"/>\n" +
            "  <g transform=\"translate(40 40) scale(15)\" shape-rendering=\"crispEdges\">\n" + rects(theme, "    ") + "  </g>\n" +
            "  <text x=\"200\" y=\"108\" font-family=\"'JetBrains Mono', 'Fira Code', Consolas, monospace\" font-size=\"64\" font-weight=\"700\" fill=\"${hex(theme.textColor)}\">Blocky<tspan fill=\"${hex(theme.mainColor)}\">Type</tspan></text>\n" +
            "  <text x=\"202\" y=\"150\" font-family=\"'JetBrains Mono', 'Fira Code', Consolas, monospace\" font-size=\"22\" fill=\"${hex(theme.subColor)}\">a typing test inside Minecraft</text>\n" +
            "</svg>\n"

    /** The logo as a PNG of [size] x [size] pixels (a multiple of 8) with transparent corners. */
    fun png(theme: AbstractTheme, size: Int): ByteArray {
        require(size > 0 && size % 8 == 0) { "size must be a positive multiple of 8, got $size" }
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val cell = size / 8
        for (y in 0 until size) for (x in 0 until size) {
            val c = GRID[y / cell][x / cell]
            if (c != '.') image.setRGB(x, y, (0xFF shl 24) or color(c, theme).value())
        }
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
}
