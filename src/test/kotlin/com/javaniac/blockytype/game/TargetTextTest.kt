package com.javaniac.blockytype.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TargetTextTest {
    @Test
    fun `append equals a fresh text of all words`() {
        val a = TargetText(listOf("alpha", "beta", "gamma"), width = 12)
        a.append(listOf("delta", "epsilon", "zeta", "eta"))
        val b = TargetText(listOf("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta"), width = 12)
        assertEquals(b.words, a.words)
        assertEquals(b.text, a.text)
        assertEquals(b.lines.map { it.start to it.text }, a.lines.map { it.start to it.text })
        assertEquals(b.wordStarts.toList(), a.wordStarts.toList())
        assertEquals(b.locate(20), a.locate(20))
    }

    @Test
    fun `append rejects unusable words`() {
        val a = TargetText(listOf("one"), width = 5)
        assertFailsWith<IllegalArgumentException> { a.append(listOf("two words")) }
        assertFailsWith<IllegalArgumentException> { a.append(listOf("toolongword")) }
        assertEquals(listOf("one"), a.words)
    }
}
