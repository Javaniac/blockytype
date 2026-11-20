package com.javaniac.blockytype.words

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FunboxTest {
    private val base = WordList("english", true, listOf("hello", "world", "typing", "speed", "quick", "brown", "fox", "jumps", "over", "lazy", "dog", "Please"))

    private fun words(mode: String, n: Int = 40, seed: Int = 1, lists: List<WordList> = listOf(base)): List<String> =
        Funbox.taker(mode, base, Random(seed)) { lists }(n)

    @Test
    fun `every funbox mode in the menu is implemented`() {
        val modes = com.javaniac.blockytype.settings.SettingsCatalog.byId("funbox")!!.options.values()
        for (mode in modes) assertTrue(Funbox.isKnown(mode), mode)
    }

    @Test
    fun `off and zipf keep the list words`() {
        for (mode in listOf("off", "zipf")) {
            val w = words(mode)
            assertEquals(40, w.size)
            assertTrue(w.all { it in base.words }, mode)
        }
    }

    @Test
    fun `case modes`() {
        assertTrue(words("ALL CAPS").all { it == it.uppercase() })
        assertTrue(words("capitals").all { it.first().isUpperCase() })
        assertTrue(words("sPoNgEcAsE").all { w -> w.withIndex().all { (i, c) -> if (i % 2 == 0) c.isLowerCase() else c.isUpperCase() } })
        val random = words("rAnDoMcAsE", 200)
        assertTrue(random.all { it.lowercase() in base.words.map(String::lowercase) })
        assertTrue(random.any { w -> w.any(Char::isUpperCase) && w.any(Char::isLowerCase) })
    }

    @Test
    fun `text transforms`() {
        assertTrue(words("backwards").all { it.reversed().lowercase() in base.words.map(String::lowercase) })
        assertTrue(words("ddoouubblleedd").all { w -> w.length % 2 == 0 && w.chunked(2).all { it[0] == it[1] } })
        assertTrue(words("rot13").all { w -> w.map { rot(it) }.joinToString("").lowercase() in base.words.map(String::lowercase) })
        assertTrue(words("instant messaging").all { w -> w == w.lowercase() && w.all(Char::isLetterOrDigit) })
    }

    private fun rot(c: Char): Char = when (c) {
        in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
        in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
        else -> c
    }

    @Test
    fun `morse`() {
        assertEquals("...", Funbox.morse("s"))
        assertEquals(".../---/...", Funbox.morse("sos"))
        assertTrue(words("morse").all { w -> w.all { it in ".-/" } })
    }

    @Test
    fun `words glued together`() {
        val nospace = words("nospace", 10)
        assertEquals(10, nospace.size)
        assertTrue(nospace.all { it.length > 6 && ' ' !in it })
        val underscores = words("underscore_spaces", 10)
        assertTrue(underscores.all { '_' in it && ' ' !in it })
        assertTrue(underscores.all { it.length <= 50 && it.split('_').all { p -> p.isNotEmpty() } })
    }

    @Test
    fun `number and symbol modes`() {
        assertTrue(words("binary").all { Regex("[01]{8}").matches(it) })
        assertTrue(words("hexadecimal").all { Regex("0x[0-9a-f]{2,6}").matches(it) })
        assertTrue(words("IPv4").all { Regex("(\\d{1,3}\\.){3}\\d{1,3}").matches(it) && it.split('.').all { p -> p.toInt() in 0..255 } })
        assertTrue(words("IPv6").all { Regex("([0-9a-f]{1,4}:){7}[0-9a-f]{1,4}").matches(it) })
        assertTrue(words("specials").all { w -> w.isNotEmpty() && w.none { it.isLetterOrDigit() || it == ' ' } })
        assertTrue(words("ascii").all { w -> w.isNotEmpty() && w.all { it.code in 33..126 } })
        assertTrue(words("gibberish").all { w -> w.length in 2..8 && w.all { it in 'a'..'z' } })
    }

    @Test
    fun `pseudolang makes new words from the letters of the list`() {
        val w = words("pseudolang", 200)
        val letters = base.words.joinToString("").lowercase().toSet()
        assertTrue(w.all { it.isNotEmpty() && it.all { c -> c.lowercaseChar() in letters } })
        assertTrue(w.any { it.lowercase() !in base.words.map(String::lowercase) })
    }

    @Test
    fun `polyglot mixes the words of several lists`() {
        val other = WordList("german", false, listOf("hallo", "welt", "schnell", "baum"))
        val w = words("polyglot", 200, lists = listOf(base, other))
        assertTrue(w.any { it in other.words })
        assertTrue(w.any { it in base.words })
    }

    @Test
    fun `same seed same words, other seed other words`() {
        assertEquals(words("gibberish", 20, seed = 3), words("gibberish", 20, seed = 3))
        assertNotEquals(words("gibberish", 20, seed = 3), words("gibberish", 20, seed = 4))
    }

    @Test
    fun `every word fits a line and has no space`() {
        for (mode in com.javaniac.blockytype.settings.SettingsCatalog.byId("funbox")!!.options.values()) {
            for (w in words(mode, 100)) assertTrue(w.isNotEmpty() && ' ' !in w && w.length <= 50, "$mode: '$w'")
        }
    }
}

class BritishEnglishTest {
    @Test
    fun `american words become british`() {
        assertEquals("colour", BritishEnglish.convert("color"))
        assertEquals("Colour", BritishEnglish.convert("Color"))
        assertEquals("COLOUR", BritishEnglish.convert("COLOR"))
        assertEquals("metres", BritishEnglish.convert("meters"))
        assertEquals("hello", BritishEnglish.convert("hello"))
    }

    @Test
    fun `hyphenated words and punctuation are handled`() {
        assertEquals("cream-coloured", BritishEnglish.convert("cream-colored"))
        assertEquals("'colour'", BritishEnglish.convert("\"color\""))
    }

    @Test
    fun `the table is loaded`() {
        assertTrue(BritishEnglish.size > 500)
    }
}
