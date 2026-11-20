package com.javaniac.blockytype.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypingTestTest {
    private val target = TargetText(listOf("the", "quick", "fox"))
    private val ms = 1_000_000L

    private fun TypingTest.type(text: String, startMs: Long = 0, stepMs: Long = 100) {
        var t = startMs
        for (c in text) {
            apply(typed + c, t * ms)
            t += stepMs
        }
    }

    @Test
    fun `typing the whole text finishes the test`() {
        val test = TypingTest(target)
        test.type("the quick fox")
        assertEquals(TypingTest.State.FINISHED, test.state)
        val r = TestStats.result(test)
        assertEquals(13, r.counts.correct) // 11 letters + 2 spaces
        assertEquals(0, r.counts.incorrect)
        assertEquals(100.0, r.accuracy)
        // 13 chars in 1.2 s
        assertEquals(13 / 5.0 / (1.2 / 60), r.wpm, 0.01)
    }

    @Test
    fun `wrong letters count against accuracy and the word`() {
        val test = TypingTest(target)
        test.type("thx quick fox")
        val r = TestStats.result(test)
        assertEquals(1, r.counts.incorrect)
        assertTrue(r.accuracy < 100.0)
        assertTrue(r.wpm < r.raw) // wrong word is not in wpm but in raw
    }

    @Test
    fun `paste is rejected`() {
        val test = TypingTest(target)
        assertEquals(TypingTest.Outcome.REJECTED, test.apply("the quick", 0))
        assertEquals("", test.typed)
        assertEquals(TypingTest.State.READY, test.state)
    }

    @Test
    fun `space on an empty word is rejected`() {
        val test = TypingTest(target)
        test.type("the")
        assertEquals(TypingTest.Outcome.ACCEPTED, test.apply("the ", 400 * ms))
        assertEquals(TypingTest.Outcome.REJECTED, test.apply("the  ", 500 * ms))
    }

    @Test
    fun `correct committed words are locked, wrong ones can be fixed`() {
        val test = TypingTest(target)
        test.type("the ")
        assertEquals(TypingTest.Outcome.REJECTED, test.apply("the", 500 * ms)) // locked
        assertEquals(TypingTest.Outcome.REJECTED, test.apply("", 500 * ms))

        val wrong = TypingTest(target)
        wrong.type("thx ")
        assertEquals(TypingTest.Outcome.ACCEPTED, wrong.apply("thx", 500 * ms)) // may go back
        assertEquals(TypingTest.Outcome.ACCEPTED, wrong.apply("th", 600 * ms))
    }

    @Test
    fun `extra letters are counted and the limit is enforced`() {
        val test = TypingTest(target)
        test.type("thee ")
        val counts = TestStats.countChars(test.typed, target.words)
        assertEquals(1, counts.extra)
        val long = TypingTest(target)
        long.type("t".repeat(3 + TypingTest.MAX_EXTRA))
        assertEquals(TypingTest.Outcome.REJECTED, long.apply(long.typed + "t", 5_000 * ms))
    }

    @Test
    fun `backspace is not a keypress`() {
        val test = TypingTest(target)
        test.type("thx")
        test.apply("th", 400 * ms)
        assertEquals(3, test.keys.size)
        assertEquals(listOf(true, true, false), test.keys.map { it.correct })
    }

    // ---- time mode ----

    private val sec = 1_000L * ms

    private fun timeTest(seconds: Int): TypingTest {
        var n = 0
        return TypingTest(TargetText(List(100) { "ab" }), TestMode.Time(seconds)) { count ->
            List(count) { "w${'a' + (n++ % 26)}" }
        }
    }

    @Test
    fun `time mode does not finish by completing words`() {
        val test = timeTest(30)
        test.type("ab ".repeat(100))
        assertEquals(TypingTest.State.RUNNING, test.state)
    }

    @Test
    fun `time mode keeps at least 100 words ahead`() {
        val test = timeTest(30)
        val before = test.target.words.size
        test.type("ab ".repeat(60))
        assertTrue(test.target.words.size > before)
        assertTrue(test.target.words.size - 60 >= 100)
    }

    @Test
    fun `expire ends a running time test at exactly the limit`() {
        val test = timeTest(15)
        test.type("ab ab", startMs = 0, stepMs = 100)
        assertEquals(TypingTest.State.RUNNING, test.state)
        test.expire(20 * sec)
        assertEquals(TypingTest.State.FINISHED, test.state)
        assertEquals(15_000L, test.durationMs())
        assertEquals(15.0, TestStats.result(test).seconds, 0.0001)
    }

    @Test
    fun `expire does nothing before the start or in words mode`() {
        val ready = timeTest(15)
        ready.expire(100 * sec)
        assertEquals(TypingTest.State.READY, ready.state)
        val words = TypingTest(target)
        words.type("the")
        words.expire(100 * sec)
        assertEquals(TypingTest.State.RUNNING, words.state)
    }

    @Test
    fun `a key after the deadline ends the test and is not counted`() {
        val test = timeTest(15)
        test.type("ab", startMs = 0, stepMs = 100)
        assertEquals(TypingTest.Outcome.REJECTED, test.apply("ab ", 16 * sec))
        assertEquals(TypingTest.State.FINISHED, test.state)
        assertEquals("ab", test.typed)
        assertEquals(15_000L, test.durationMs())
    }

    @Test
    fun `time mode credits the correct prefix of the last word`() {
        val test = TypingTest(TargetText(List(100) { "quick" }), TestMode.Time(15)) { List(100) { "quick" } }
        test.type("quick qui", startMs = 0, stepMs = 100)
        test.expire(20 * sec)
        val r = TestStats.result(test)
        // quick + space + qui
        assertEquals(TestStats.wpm(9, 15.0), r.wpm, 0.0001)
    }

    @Test
    fun `remaining seconds count down from the first key`() {
        val test = timeTest(30)
        assertEquals(30, test.remainingSeconds(5 * sec))
        test.type("a", startMs = 0)
        assertEquals(30, test.remainingSeconds(0))
        assertEquals(25, test.remainingSeconds(5 * sec))
        assertEquals(1, test.remainingSeconds(29 * sec + 500 * ms))
        assertEquals(0, test.remainingSeconds(40 * sec))
        assertEquals(30 * sec, test.deadlineNanos())
    }

    @Test
    fun `hud shows mode and language`() {
        val t = timeTest(30).toHudState(0, "german")
        assertEquals("time 30 | german", t.config.joinToString("") { it.text })
        val w = TypingTest(target).toHudState(0)
        assertEquals("words 3 | english", w.config.joinToString("") { it.text })
    }

    @Test
    fun `mode labels`() {
        assertEquals("words 25", TestMode.Words(25).label)
        assertEquals("time 30", TestMode.Time(30).label)
    }
}
