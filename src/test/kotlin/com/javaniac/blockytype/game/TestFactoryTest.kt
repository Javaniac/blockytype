package com.javaniac.blockytype.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestFactoryTest {
    private fun create(vararg settings: Pair<String, String>, random: Random = Random(1)): TestFactory.Created {
        val map = mapOf("test-mode" to "words", "word-count" to "25", "time-limit" to "30", "language" to "english", "funbox" to "off") + settings
        return TestFactory.create({ map[it] ?: com.javaniac.blockytype.settings.SettingsCatalog.byId(it)!!.default }, random)
    }

    @Test
    fun `words mode makes exactly the chosen number of words`() {
        val c = create("word-count" to "10")
        assertEquals(TestMode.Words(10), c.test.mode)
        assertEquals(10, c.test.target.words.size)
        assertEquals("english", c.language)
    }

    @Test
    fun `time mode starts with 100 words and grows`() {
        val c = create("test-mode" to "time", "time-limit" to "15")
        assertEquals(TestMode.Time(15), c.test.mode)
        assertEquals(TypingTest.AHEAD, c.test.target.words.size)
        c.test.apply("a", 0)
        assertTrue(c.test.target.words.size >= TypingTest.AHEAD)
    }

    @Test
    fun `words come from the chosen language`() {
        val german = com.javaniac.blockytype.words.LanguageRegistry.load("german").words.toSet()
        val c = create("language" to "german", "word-count" to "50")
        assertTrue(c.test.target.words.all { it in german })
        assertEquals("german", c.language)
    }

    @Test
    fun `an unknown or not downloaded language falls back to english`() {
        assertEquals("english", create("language" to "klingon").language)
        assertEquals("english", create("language" to "english_450k").language)
    }

    @Test
    fun `broken numbers fall back to the defaults`() {
        val c = create("word-count" to "abc")
        assertEquals(TestMode.Words(25), c.test.mode)
        assertEquals(TestMode.Time(30), create("test-mode" to "time", "time-limit" to "x").test.mode)
    }

    @Test
    fun `same seed gives the same text`() {
        assertEquals(create(random = Random(7)).test.target.text, create(random = Random(7)).test.target.text)
    }

    @Test
    fun `zipf funbox favours the first words of the list`() {
        val ordered = com.javaniac.blockytype.words.LanguageRegistry.load("english").words
        val c = create("funbox" to "zipf", "word-count" to "100")
        val early = c.test.target.words.count { ordered.indexOf(it) in 0 until 20 }
        assertTrue(early > 20, "early $early") // uniform would give ~10 of 100
    }

    @Test
    fun `display name has spaces`() {
        assertEquals("english 1k", create("language" to "english_1k").displayLanguage)
    }
}
