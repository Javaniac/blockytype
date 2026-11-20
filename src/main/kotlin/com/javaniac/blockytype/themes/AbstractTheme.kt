package com.javaniac.blockytype.themes

import com.javaniac.blockytype.toColor
import net.kyori.adventure.text.format.TextColor

/**
 * An interface for defining a theme.
 * The default values represent the default theme.
 */
interface AbstractTheme {
    val name: String
    val bgColor: TextColor get() = 0x323437.toColor()
    val mainColor: TextColor get() = 0xE2B714.toColor()
    val caretColor: TextColor get() = 0xE2B714.toColor()
    val subColor: TextColor get() = 0x646669.toColor()
    val subAltColor: TextColor get() = 0x2C2E31.toColor()
    val textColor: TextColor get() = 0xD1D0C5.toColor()
    val errorColor: TextColor get() = 0xCA4754.toColor()
}