package com.javaniac.blockytype.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CommandSuggestTest {
    private val choices = { id: String -> if (id == "theme") listOf("default", "dark", "nord") else listOf("on", "off") }
    private fun s(text: String, admin: Boolean = false) =
        CommandSuggest.suggest(text, admin, choices, listOf("jetbrains-mono", "fira-code"))

    @Test
    fun `profile completes online player names`() {
        val r = assertNotNull(CommandSuggest.suggest("/profile Al", false, choices, emptyList()) { listOf("Alice", "Bob", "alfred") })
        assertEquals(9, r.start)
        assertEquals(2, r.length)
        assertEquals(listOf("alfred", "Alice"), r.matches)
        assertEquals(listOf("Bob"), CommandSuggest.suggest("/profile ", false, choices, emptyList()) { listOf("Bob") }!!.matches)
        assertNull(CommandSuggest.suggest("/profileX", false, choices, emptyList()) { listOf("Bob") })
    }

    @Test
    fun `other text is ignored`() {
        assertNull(s("/t hello"))
        assertNull(s(""))
    }

    @Test
    fun `setting ids complete by prefix`() {
        val r = assertNotNull(s("/settings th"))
        assertEquals(10, r.start)
        assertEquals(2, r.length)
        assertEquals(listOf("theme"), r.matches.filter { it.startsWith("th") })
    }

    @Test
    fun `values complete by prefix`() {
        val r = assertNotNull(s("/settings theme d"))
        assertEquals(16, r.start)
        assertEquals(1, r.length)
        assertEquals(listOf("default", "dark"), r.matches)
    }

    @Test
    fun `all values with empty prefix`() {
        val r = assertNotNull(s("/settings theme "))
        assertEquals(listOf("default", "dark", "nord"), r.matches.take(3))
    }

    @Test
    fun `unknown setting gives nothing`() {
        assertNull(s("/settings nope "))
    }

    @Test
    fun `value list is capped`() {
        val r = assertNotNull(CommandSuggest.suggest("/settings theme ", false, { (1..500).map { "t$it" } }, emptyList()))
        assertEquals(CommandSuggest.MAX, r.matches.size)
    }

    @Test
    fun `pack is admin only`() {
        assertNull(s("/pack f", admin = false))
        assertEquals(listOf("font"), assertNotNull(s("/pack f", admin = true)).matches)
    }

    @Test
    fun `pack font use lists fonts`() {
        val r = assertNotNull(s("/pack font use j", admin = true))
        assertEquals(listOf("jetbrains-mono"), r.matches)
    }

    @Test
    fun `chat text without own completions still gets an empty answer`() {
        // An unanswered ask_server request keeps the client from showing the local literals (`settings`).
        val r = CommandSuggest.answer("/", isAdmin = false, choices = { emptyList() }, fonts = emptyList())
        assertEquals(emptyList(), r.matches)
        assertEquals(1, r.start)
        assertEquals(0, r.length)
        val s = CommandSuggest.answer("/settings th", isAdmin = false, choices = { emptyList() }, fonts = emptyList())
        assertEquals(listOf("theme"), s.matches.filter { it == "theme" })
    }
}
