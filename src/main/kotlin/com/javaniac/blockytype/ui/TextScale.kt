package com.javaniac.blockytype.ui

import net.kyori.adventure.key.Key

/**
 * The `font-size` setting: how big the target text is. Each scale is its own font whose advance is a whole number of
 * GUI units, so a line of [maxChars] chars always fits the 300 units of the smallest GUI.
 */
enum class TextScale(val id: String, val adv: Int, val font: Key) {
    X1("1", UiFont.MONO_ADV, UiFont.MONO),
    X1_5("1.5", UiFont.MONO_ADV * 3 / 2, Key.key("blockytype", "mono_15")),
    X2("2", UiFont.MONO_ADV * 2, Key.key("blockytype", "mono_2"));

    /** Chars of one line of target text. */
    val maxChars: Int get() = UiFont.MAX_LINE_CHARS * UiFont.MONO_ADV / adv

    /** `size` of the font relative to `mono`. */
    val factor: Double get() = adv.toDouble() / UiFont.MONO_ADV

    companion object {
        fun of(id: String): TextScale = entries.firstOrNull { it.id == id } ?: X1
    }
}
