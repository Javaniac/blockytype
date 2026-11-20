package com.javaniac.blockytype.pack

import com.javaniac.blockytype.themes.AbstractTheme
import com.javaniac.blockytype.themes.DefaultTheme
import java.security.MessageDigest
import java.util.UUID

/** Colors (`0xRRGGBB`) of the visible command box: its fill, border, focused border and typed text. */
data class BoxColors(val fill: Int, val border: Int, val borderFocus: Int, val text: Int) {
    companion object {
        val DEFAULT = of(DefaultTheme)

        /** From the theme roles. The text red avoids the HUD marker bits (`R & 7 == 3`), which `text.fsh` would treat as HUD. */
        fun of(theme: AbstractTheme): BoxColors {
            var text = theme.textColor.value()
            if (((text shr 16) and 7) == 3) text += 1 shl 16
            return BoxColors(theme.subAltColor.value(), theme.subColor.value(), theme.mainColor.value(), text)
        }
    }
}

/** What differs between the packs players get: their font, anti-aliasing, input box setting and box colors. */
data class PackVariant(val font: String, val antiAlias: Boolean, val showInput: Boolean, val box: BoxColors = BoxColors.DEFAULT) {
    companion object {
        /** From the effective setting values (`anti-aliasing` on/off, `input-box` show/hide). A hidden box has no colors. */
        fun of(font: String, antiAlias: String, inputBox: String, box: BoxColors = BoxColors.DEFAULT): PackVariant {
            val show = inputBox == "show"
            return PackVariant(font, antiAlias == "on", show, if (show) box else BoxColors.DEFAULT)
        }
    }
}

/** A built pack zip. [id] changes with the content, so clients refetch after a change. */
class PackFile(val zip: ByteArray) {
    val sha1: String = MessageDigest.getInstance("SHA-1").digest(zip).joinToString("") { "%02x".format(it) }
    val id: UUID = UUID.nameUUIDFromBytes(zip)
}

/** Builds each [PackVariant] once and keeps the [max] most recently used. */
class PackCache(private val build: (PackVariant) -> ByteArray, private val max: Int = 32) {
    private val packs = object : LinkedHashMap<PackVariant, PackFile>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PackVariant, PackFile>?) = size > max
    }

    @Synchronized
    fun get(variant: PackVariant): PackFile = packs.getOrPut(variant) { PackFile(build(variant)) }

    @Synchronized
    fun byHash(sha1: String): PackFile? = packs.values.firstOrNull { it.sha1 == sha1 }

    /** Drops everything, for example after a font file changed. */
    @Synchronized
    fun clear() = packs.clear()
}
