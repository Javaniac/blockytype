package com.javaniac.blockytype.pack

import com.javaniac.blockytype.ui.UiFont
import java.awt.Font
import java.awt.font.FontRenderContext
import java.io.ByteArrayInputStream

/**
 * A monospace font file and what the pack needs to know about it.
 * The HUD layout is a fixed grid of [UiFont.MONO_ADV] units per char, so any font is scaled until its advance is exactly that.
 */
class FontInfo(val name: String, val bytes: ByteArray, val extension: String, val advanceRatio: Double) {
    /** `size` of the `ttf` provider that gives an advance of exactly [UiFont.MONO_ADV] units. */
    val monoSize: Double get() = UiFont.MONO_ADV / advanceRatio

    /** Same for the big font (3x). */
    val monoBigSize: Double get() = monoSize * (UiFont.MONO_BIG_ADV / UiFont.MONO_ADV)

    /** File name of the font inside `assets/blockytype/font/` (resource location `blockytype:<packFile>`). */
    val packFile: String get() = "mono.$extension"

    companion object {
        private const val MEASURE_SIZE = 1000f
        private const val MAX_SPREAD = 0.01

        /**
         * Thinnest hyphen (in em) a font may have. A variable font can default to its Thin instance (azeret-mono did):
         * strokes of about one texel vanish in the anti-aliasing filter of `text.fsh` and the text turns to noise.
         */
        const val MIN_STROKE = 0.05

        /**
         * Parses [bytes] as a TrueType/OpenType font. Throws [IllegalArgumentException] if it is unusable, not
         * monospace or too thin.
         */
        fun load(name: String, bytes: ByteArray, extension: String): FontInfo {
            val font = try {
                Font.createFont(Font.TRUETYPE_FONT, ByteArrayInputStream(bytes))
            } catch (e: Exception) {
                throw IllegalArgumentException("not a valid font file: ${e.message}")
            }.deriveFont(MEASURE_SIZE)

            val context = FontRenderContext(null, true, true)
            val advances = "iMW.-0@ "
                .map { font.getStringBounds(it.toString(), context).width }
                .also { require(it.all { w -> w > 0.0 }) { "font has no usable glyph widths" } }
            val min = advances.min()
            val max = advances.max()
            require((max - min) / max <= MAX_SPREAD) {
                "font is not monospace (advances ${"%.0f".format(min)}..${"%.0f".format(max)} per $MEASURE_SIZE)"
            }
            val stroke = font.createGlyphVector(context, "-").getGlyphOutline(0).bounds2D.height / MEASURE_SIZE
            require(stroke >= MIN_STROKE) {
                "font is too thin (strokes ${"%.3f".format(stroke)} em, at least $MIN_STROKE needed): use a Regular weight, not Thin or Light"
            }
            return FontInfo(name, bytes, extension.lowercase(), advances.average() / MEASURE_SIZE)
        }
    }
}
