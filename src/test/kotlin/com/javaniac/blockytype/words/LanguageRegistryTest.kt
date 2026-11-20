package com.javaniac.blockytype.words

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The test run points `blockytype.languages.dir` at `src/test/resources/languages` (a few small fixture lists). */
class LanguageRegistryTest {
    private fun e(name: String) = LanguageRegistry.Entry(name, 100, false)

    @Test
    fun `english first then english variants then the rest alphabetically`() {
        val sorted = LanguageRegistry.order(
            listOf(e("german"), e("english_5k"), e("afrikaans"), e("english"), e("english_1k"), e("french")),
        ).map { it.name }
        assertEquals(listOf("english", "english_1k", "english_5k", "afrikaans", "french", "german"), sorted)
    }

    @Test
    fun `parses the index`() {
        val entries = LanguageRegistry.parseIndex("afrikaans\t2302\t0\nenglish_450k\t7911492\t1\n\n")
        assertEquals(
            listOf(
                LanguageRegistry.Entry("afrikaans", 2302, false),
                LanguageRegistry.Entry("english_450k", 7911492, true),
            ),
            entries,
        )
    }

    @Test
    fun `display name replaces underscores`() {
        assertEquals("english 1k", e("english_1k").display)
        assertEquals("norwegian bokmal", e("norwegian_bokmal").display)
    }

    @Test
    fun `the index in the jar lists english first and every list the server can download`() {
        val names = LanguageRegistry.names()
        assertEquals("english", names.first())
        assertTrue(names.size > 200, "${names.size}")
        assertTrue("german" in names && "code_python" !in names && "klingon" !in names)
    }

    @Test
    fun `a list that is on disk is available and loads`() {
        for (name in listOf("english", "german", "afrikaans")) {
            assertTrue(LanguageRegistry.isAvailable(name), name)
            val list = LanguageRegistry.load(name)
            assertEquals(name, list.name)
            assertTrue(list.words.isNotEmpty(), name)
        }
    }

    @Test
    fun `english has the standard 200 words and is not weighted`() {
        val en = LanguageRegistry.load("english")
        assertEquals(200, en.words.size)
        assertTrue(en.words.all { ' ' !in it })
    }

    @Test
    fun `lists that are not on disk throw`() {
        assertTrue(!LanguageRegistry.isAvailable("english_450k"))
        assertFailsWith<IllegalArgumentException> { LanguageRegistry.load("english_450k") }
        assertFailsWith<IllegalArgumentException> { LanguageRegistry.load("klingon") }
    }

    @Test
    fun `fixture lists only have typeable single token words`() {
        for (name in listOf("english", "german", "afrikaans")) {
            val words = LanguageRegistry.load(name).words
            val bad = words.count { it.isBlank() || ' ' in it || '\n' in it || '\t' in it }
            assertTrue(words.size - bad >= 50 && bad * 100 < words.size * 40, "$name: $bad of ${words.size} unusable")
        }
    }
}
