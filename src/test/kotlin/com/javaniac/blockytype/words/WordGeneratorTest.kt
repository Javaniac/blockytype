package com.javaniac.blockytype.words

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WordGeneratorTest {
    private fun list(vararg w: String) = WordList("test", false, w.toList())

    @Test
    fun `never repeats the previous word or the one before`() {
        val gen = WordGenerator(list("a", "b", "c", "d", "e"), zipf = false, random = Random(1))
        val out = gen.take(3000)
        for (i in 1 until out.size) assertFalse(out[i] == out[i - 1], "repeat at $i")
        for (i in 2 until out.size) assertFalse(out[i] == out[i - 2], "repeat-2 at $i")
    }

    @Test
    fun `skips I, symbol words and digit words`() {
        val gen = WordGenerator(
            list("I", "a-b", "x1", "it's", "good", "fine", "word", "text"), zipf = false, random = Random(2),
        )
        val out = gen.take(500).toSet()
        assertTrue(out.all { it in setOf("it's", "good", "fine", "word", "text") }, "$out")
    }

    @Test
    fun `ignores words with spaces and words that are too long`() {
        val gen = WordGenerator(list("two words", "x".repeat(51), "ok", "fine", "good"), false, Random(3))
        assertTrue(gen.take(200).all { it in setOf("ok", "fine", "good") })
    }

    @Test
    fun `tiny lists still produce words`() {
        assertEquals(List(5) { "same" }, WordGenerator(list("same"), false, Random(4)).take(5))
    }

    @Test
    fun `same seed gives same words`() {
        val l = list("a", "b", "c", "d", "e", "f", "g")
        assertEquals(WordGenerator(l, false, Random(9)).take(50), WordGenerator(l, false, Random(9)).take(50))
    }

    @Test
    fun `zipf index follows monkeytype inverse cdf`() {
        // r = 0 is always the first (most frequent) word
        assertEquals(0, WordGenerator.zipfIndex(200, 0.0))
        for (i in 0..1000) {
            val idx = WordGenerator.zipfIndex(200, i / 1001.0)
            assertTrue(idx in 0 until 200, "index $idx")
        }
        // exp(r*H - gamma) - 0.5 with n = 200 at r = 0.5
        val h = Math.log(200.5) + 0.5772156649015329
        assertEquals(Math.floor(Math.exp(0.5 * h - 0.5772156649015329) - 0.5).toInt(), WordGenerator.zipfIndex(200, 0.5))
    }

    @Test
    fun `zipf prefers early words`() {
        val words = (0 until 200).map { "w$it".replace(Regex("[0-9]")) { d -> ('a' + d.value.toInt()).toString() } }
        val gen = WordGenerator(WordList("z", true, words), zipf = true, random = Random(5))
        val early = gen.take(4000).count { words.indexOf(it) < 20 }
        assertTrue(early > 4000 * 0.35, "early picks $early") // uniform would give ~10%
    }
}
