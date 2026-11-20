package com.javaniac.blockytype.game

import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileSuggestTest {
    private val online = listOf("Zed", "alice", "Alfred", "Bob", "albert", "Alice")

    @Test
    fun `matches the typed prefix without case, sorted`() {
        assertEquals(listOf("albert", "alice", "Alice", "Alfred").sortedBy { it.lowercase() }, ProfileSuggest.names("al", online))
        assertEquals(listOf("Bob"), ProfileSuggest.names("BO", online))
        assertEquals(emptyList(), ProfileSuggest.names("x", online))
    }

    @Test
    fun `nothing typed lists everybody`() {
        assertEquals(online.sortedBy { it.lowercase() }, ProfileSuggest.names("", online))
    }

    @Test
    fun `the list is capped and has no duplicates`() {
        val many = (1..100).map { "Player$it" } + "Player1"
        assertEquals(ProfileSuggest.MAX, ProfileSuggest.names("pl", many).size)
        assertEquals(1, ProfileSuggest.names("Player1", listOf("Player1", "Player1")).size)
    }

    @Test
    fun `the answer replaces what was typed after the prefix`() {
        val r = ProfileSuggest.answer("/t Al", "/t ", online)
        assertEquals(3, r.start)
        assertEquals(2, r.length)
        assertEquals(ProfileSuggest.names("Al", online), r.matches)
        val none = ProfileSuggest.answer("/t ", "/t ", online)
        assertEquals(3, none.start)
        assertEquals(0, none.length)
    }

    @Test
    fun `a chat profile command completes the same way`() {
        val r = ProfileSuggest.answer("/profile bo", "/profile ", online)
        assertEquals(9, r.start)
        assertEquals(listOf("Bob"), r.matches)
    }
}
