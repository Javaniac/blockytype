package com.javaniac.blockytype.themes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MonkeytypeThemesTest {
    @Test
    fun `all monkeytype themes are present`() {
        assertEquals(187, MONKEYTYPE_THEMES.size)
    }

    @Test
    fun `names are unique lowercase ids`() {
        val names = MONKEYTYPE_THEMES.map { it.name }
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.all { it == it.lowercase() && it.none(Char::isWhitespace) })
    }

    @Test
    fun `colors match monkeytype`() {
        val t = assertNotNull(MONKEYTYPE_THEMES.firstOrNull { it.name == "8008" })
        assertEquals(0x333a45, t.bgColor.value())
        assertEquals(0xf44c7f, t.mainColor.value())
        assertEquals(0xf44c7f, t.caretColor.value())
        assertEquals(0x939eae, t.subColor.value())
        assertEquals(0x2e343d, t.subAltColor.value())
        assertEquals(0xe9ecf0, t.textColor.value())
        assertEquals(0xda3333, t.errorColor.value())
    }

    @Test
    fun `short hex colors expand`() {
        assertEquals(0xaabbcc, MonkeytypeTheme.parse("#abc"))
        assertEquals(0x112233, MonkeytypeTheme.parse("#11223344"))
    }
}
