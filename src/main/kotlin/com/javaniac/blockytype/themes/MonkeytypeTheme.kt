package com.javaniac.blockytype.themes

import com.javaniac.blockytype.toColor
import net.kyori.adventure.text.format.TextColor

/** A theme ported from Monkeytype (`frontend/src/ts/constants/themes.ts`, GPL-3.0). Colors are `0xRRGGBB`. */
class MonkeytypeTheme(
    override val name: String,
    bg: Int,
    main: Int,
    caret: Int,
    sub: Int,
    subAlt: Int,
    text: Int,
    error: Int,
) : AbstractTheme {
    override val bgColor: TextColor = bg.toColor()
    override val mainColor: TextColor = main.toColor()
    override val caretColor: TextColor = caret.toColor()
    override val subColor: TextColor = sub.toColor()
    override val subAltColor: TextColor = subAlt.toColor()
    override val textColor: TextColor = text.toColor()
    override val errorColor: TextColor = error.toColor()

    companion object {
        /** Parses `#rgb`, `#rgba`, `#rrggbb` or `#rrggbbaa` to `0xRRGGBB` (alpha dropped). */
        fun parse(hex: String): Int {
            val h = hex.removePrefix("#")
            val full = if (h.length <= 4) h.map { "$it$it" }.joinToString("") else h
            return full.substring(0, 6).toInt(16)
        }
    }
}
