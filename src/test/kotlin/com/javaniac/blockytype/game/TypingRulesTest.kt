package com.javaniac.blockytype.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TypingRulesTest {
    private val ms = 1_000_000L

    private fun test(rules: TestRules, vararg words: String = arrayOf("the", "quick", "fox")) =
        TypingTest(TargetText(words.toList()), rules = rules)

    /** Types [text] one char at a time, [stepMs] apart; each char is applied to the current box text. */
    private fun TypingTest.type(text: String, startMs: Long = 0, stepMs: Long = 100): List<TypingTest.Outcome> {
        var t = startMs
        return text.map { c ->
            apply(typed + c, t * ms).also { t += stepMs }
        }
    }

    @Test
    fun `rules are read from the settings`() {
        val rules = TestRules.from(
            mapOf(
                "difficulty" to "expert", "freedom-mode" to "on", "strict-space" to "on", "stop-on-error" to "word",
                "delete-on-error" to "letter hard", "confidence-mode" to "max", "quick-end" to "off", "lazy-mode" to "on",
                "min-speed" to "60", "min-accuracy" to "95", "min-word-burst" to "flex",
            )::getValue,
        )
        assertEquals(TestRules.Difficulty.EXPERT, rules.difficulty)
        assertTrue(rules.freedom && rules.strictSpace && rules.lazy && !rules.quickEnd)
        assertEquals(TestRules.StopOnError.WORD, rules.stopOnError)
        assertEquals(TestRules.DeleteOnError.LETTER_HARD, rules.deleteOnError)
        assertEquals(TestRules.Confidence.MAX, rules.confidence)
        assertEquals(60, rules.minSpeed)
        assertEquals(95, rules.minAccuracy)
        assertEquals(TestRules.MinBurst.FLEX, rules.minBurst)
    }

    @Test
    fun `default rules change nothing`() {
        val t = test(TestRules())
        t.type("thx quick fox")
        assertNull(t.failure)
        assertEquals(TypingTest.State.FINISHED, t.state)
    }

    @Test
    fun `expert fails when a wrong word is submitted`() {
        val t = test(TestRules(difficulty = TestRules.Difficulty.EXPERT))
        t.type("thx")
        assertEquals(TypingTest.State.RUNNING, t.state)
        t.type(" ", startMs = 300)
        assertEquals(TypingTest.State.FINISHED, t.state)
        assertEquals("expert", t.failure)
    }

    @Test
    fun `master fails on the first wrong key`() {
        val t = test(TestRules(difficulty = TestRules.Difficulty.MASTER))
        t.type("thx")
        assertEquals(TypingTest.State.FINISHED, t.state)
        assertEquals("master", t.failure)
    }

    @Test
    fun `stop on error letter blocks a wrong letter and counts it`() {
        val t = test(TestRules(stopOnError = TestRules.StopOnError.LETTER))
        val outcomes = t.type("thx")
        assertEquals(TypingTest.Outcome.PENALIZED, outcomes.last())
        assertEquals("th", t.typed)
        assertTrue(t.keys.any { !it.correct })
        assertEquals(TypingTest.Outcome.ACCEPTED, t.apply("the", 400 * ms))
    }

    @Test
    fun `stop on error word blocks space until the word is right`() {
        val t = test(TestRules(stopOnError = TestRules.StopOnError.WORD))
        t.type("thx")
        assertEquals("thx", t.typed) // wrong letters are allowed
        assertEquals(TypingTest.Outcome.PENALIZED, t.apply("thx ", 400 * ms))
        assertEquals("thx", t.typed)
        t.apply("th", 500 * ms)
        t.apply("the", 600 * ms)
        assertEquals(TypingTest.Outcome.ACCEPTED, t.apply("the ", 700 * ms))
    }

    @Test
    fun `delete on error letter removes the wrong char and the one before`() {
        val t = test(TestRules(deleteOnError = TestRules.DeleteOnError.LETTER))
        t.type("thx")
        assertEquals("t", t.typed)
    }

    @Test
    fun `delete on error word clears the current word`() {
        val t = test(TestRules(deleteOnError = TestRules.DeleteOnError.WORD))
        t.type("the quix")
        assertEquals("the ", t.typed)
    }

    @Test
    fun `hard delete on error jumps back to the previous word on a wrong first char`() {
        val soft = test(TestRules(deleteOnError = TestRules.DeleteOnError.LETTER))
        soft.type("the x")
        assertEquals("the ", soft.typed)

        val hard = test(TestRules(deleteOnError = TestRules.DeleteOnError.LETTER_HARD))
        hard.type("the x")
        assertEquals("the", hard.typed)
    }

    @Test
    fun `confidence on keeps previous words locked, max forbids backspace`() {
        val on = test(TestRules(confidence = TestRules.Confidence.ON))
        on.type("thx ")
        assertEquals(TypingTest.Outcome.REJECTED, on.apply("thx", 500 * ms)) // back into the old word
        on.type("qu", startMs = 600)
        assertEquals(TypingTest.Outcome.ACCEPTED, on.apply("thx q", 800 * ms)) // within the word

        val max = test(TestRules(confidence = TestRules.Confidence.MAX))
        max.type("th")
        assertEquals(TypingTest.Outcome.REJECTED, max.apply("t", 500 * ms))
    }

    @Test
    fun `freedom mode lets correct words be erased`() {
        val t = test(TestRules(freedom = true))
        t.type("the ")
        assertEquals(TypingTest.Outcome.ACCEPTED, t.apply("the", 500 * ms))
        assertEquals(TypingTest.Outcome.ACCEPTED, t.apply("th", 600 * ms))
    }

    @Test
    fun `strict space turns a space on an empty word into an error`() {
        val t = test(TestRules(strictSpace = true))
        t.type("the")
        assertEquals(TypingTest.Outcome.ACCEPTED, t.apply("the ", 400 * ms))
        assertEquals(TypingTest.Outcome.PENALIZED, t.apply("the  ", 500 * ms))
        assertTrue(!t.keys.last().correct)
        assertEquals("the ", t.typed)
    }

    @Test
    fun `quick end off needs the last word right or a space`() {
        val wrongEnd = test(TestRules(quickEnd = false))
        wrongEnd.type("the quick fax")
        assertEquals(TypingTest.State.RUNNING, wrongEnd.state)
        wrongEnd.apply("the quick fax ", 2000 * ms)
        assertEquals(TypingTest.State.FINISHED, wrongEnd.state)

        val rightEnd = test(TestRules(quickEnd = false))
        rightEnd.type("the quick fox")
        assertEquals(TypingTest.State.FINISHED, rightEnd.state)

        val quick = test(TestRules(quickEnd = true))
        quick.type("the quick fax")
        assertEquals(TypingTest.State.FINISHED, quick.state)
    }

    @Test
    fun `lazy mode accepts plain letters for accented ones`() {
        val t = test(TestRules(lazy = true), "café", "naïve")
        t.type("cafe naive")
        assertEquals(TypingTest.State.FINISHED, t.state)
        val r = TestStats.result(t)
        assertEquals(0, r.counts.incorrect)
        assertEquals(100.0, r.accuracy)

        val strict = test(TestRules(), "café", "naïve")
        strict.type("cafe naive")
        assertTrue(TestStats.result(strict).counts.incorrect > 0)
    }

    @Test
    fun `min speed fails a slow test`() {
        val t = test(TestRules(minSpeed = 60))
        t.type("the q", stepMs = 1000)
        assertTrue(!t.checkMinimums(6_000 * ms))
        assertEquals("min speed", t.failure)
        assertEquals(TypingTest.State.FINISHED, t.state)
    }

    @Test
    fun `min speed waits for a few seconds and ignores a fast test`() {
        val t = test(TestRules(minSpeed = 20))
        t.type("the quic", stepMs = 50)
        assertTrue(t.checkMinimums(1_000 * ms)) // too early to judge
        assertNull(t.failure)
    }

    @Test
    fun `min accuracy fails once enough keys were typed`() {
        val t = test(TestRules(minAccuracy = 90))
        t.type("txxx", stepMs = 100)
        assertTrue(t.checkMinimums(500 * ms)) // 4 keys: too few to judge
        t.type("xx", startMs = 400)
        assertTrue(!t.checkMinimums(700 * ms))
        assertEquals("min accuracy", t.failure)
    }

    @Test
    fun `min word burst fails a slow word`() {
        val t = test(TestRules(minBurst = TestRules.MinBurst.FIXED))
        t.type("the", stepMs = 1000)
        t.type(" ", startMs = 3000)
        assertEquals("min word burst", t.failure)
    }

    @Test
    fun `flex burst is more forgiving for long words`() {
        val fixed = test(TestRules(minBurst = TestRules.MinBurst.FIXED), "extraordinary", "fox")
        fixed.type("extraordinary", stepMs = 650) // about 20 wpm
        fixed.type(" ", startMs = 13 * 650)
        assertEquals("min word burst", fixed.failure)

        val flex = test(TestRules(minBurst = TestRules.MinBurst.FLEX), "extraordinary", "fox")
        flex.type("extraordinary", stepMs = 650)
        flex.type(" ", startMs = 13 * 650)
        assertNull(flex.failure)
    }
}
