package com.javaniac.blockytype.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordBurstTest {
    private val ms = 1_000_000L

    private fun typed(words: List<String>, text: String, stepMs: Long = 400): TypingTest {
        val test = TypingTest(TargetText(words))
        var t = 0L
        for (c in text) {
            test.apply(test.typed + c, t * ms)
            t += stepMs
        }
        return test
    }

    private val fox = listOf("the", "quick", "fox")

    @Test
    fun `every key knows its word`() {
        val test = typed(fox, "the qu")
        assertEquals(listOf(0, 0, 0, 0, 1, 1), test.keys.map { it.word })
    }

    @Test
    fun `burst is typed length plus one as wpm from the first to the last key of the word`() {
        val b = WordBurst.bursts(typed(fox, "the quick fox"))
        // the: keys 0..1200 (incl. space) 1.2 s, 4 chars; quick: 1600..3600 2 s, 6 chars; fox: 4000..4800 0.8 s, 4 chars
        assertEquals(3, b.size)
        assertEquals(40.0, b[0]!!, 0.01)
        assertEquals(36.0, b[1]!!, 0.01)
        assertEquals(60.0, b[2]!!, 0.01)
    }

    @Test
    fun `untyped words have no burst and a single key word is infinite`() {
        val b = WordBurst.bursts(typed(fox, "the q"))
        assertEquals(40.0, b[0]!!, 0.01)
        assertEquals(Double.POSITIVE_INFINITY, b[1]!!) // one key: no duration
        assertNull(b[2])
    }

    @Test
    fun `typed word count follows the text`() {
        assertEquals(0, WordBurst.typedWords(typed(fox, "")))
        assertEquals(1, WordBurst.typedWords(typed(fox, "th")))
        assertEquals(1, WordBurst.typedWords(typed(fox, "the "))) // the empty word after the space is not typed yet
        assertEquals(2, WordBurst.typedWords(typed(fox, "the q")))
        assertEquals(3, WordBurst.typedWords(typed(fox, "the quick fox")))
    }

    @Test
    fun `levels follow the 15 35 65 85 percentiles of the bursts`() {
        val values = (1..10).map { it * 10.0 }
        assertEquals(listOf(0, 1, 1, 2, 2, 2, 3, 3, 4, 4), WordBurst.levels(values))
        assertEquals(listOf(3, 1, 4), WordBurst.levels(listOf(40.0, 36.0, 60.0)))
    }

    @Test
    fun `infinite and untyped words get special levels`() {
        val levels = WordBurst.levels(listOf(10.0, Double.POSITIVE_INFINITY, null, 50.0))
        assertEquals(WordBurst.NO_HEAT, levels[1])
        assertEquals(WordBurst.UNTYPED, levels[2])
        assertTrue(levels[0] in 0..4 && levels[3] in 0..4)
        assertEquals(WordBurst.NO_HEAT, WordBurst.levels(listOf(1200.0))[0]) // 1000 and more count as infinite
    }

    @Test
    fun `a second covers the words typed in it`() {
        val test = typed(fox, "the quick fox")
        val seconds = 5
        assertEquals(0..0, WordBurst.wordsInSecond(test, 1, seconds)) // keys at 0, 400, 800
        assertEquals(0..1, WordBurst.wordsInSecond(test, 2, seconds)) // 1200 (space of "the"), 1600 (q)
        assertEquals(1..1, WordBurst.wordsInSecond(test, 3, seconds)) // 2000, 2400, 2800
        assertNull(WordBurst.wordsInSecond(test, 9, seconds))
    }

    @Test
    fun `the last second includes the end of the test`() {
        val test = typed(listOf("ab", "cd"), "ab cd", stepMs = 500) // keys at 0, 500, 1000, 1500, 2000
        assertEquals(0..0, WordBurst.wordsInSecond(test, 1, seconds = 3)) // 500 and 1000: word "ab"
        assertEquals(1..1, WordBurst.wordsInSecond(test, 2, seconds = 3)) // 1500 and 2000
        assertNull(WordBurst.wordsInSecond(test, 3, seconds = 3))
        // the key at 2000 ends the test: with 2 seconds it belongs to the last one
        assertEquals(1..1, WordBurst.wordsInSecond(test, 2, seconds = 2))
    }
}
