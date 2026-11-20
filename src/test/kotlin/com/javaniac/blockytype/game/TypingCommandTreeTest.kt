package com.javaniac.blockytype.game

import net.minestom.server.network.packet.server.play.DeclareCommandsPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypingCommandTreeTest {
    private fun DeclareCommandsPacket.children(of: Int = 0) =
        nodes().let { n -> n[of].children.map { n[it] } }

    private fun DeclareCommandsPacket.rootNames() = children().map { it.name }

    @Test
    fun `typing argument stays at the root`() {
        val t = TypingCommandTree.of(false).children().single { it.name == "t" }
        assertEquals("minecraft:ask_server", t.suggestionsType)
    }

    @Test
    fun `chat completion offers settings and profile, for players and admins`() {
        for (admin in listOf(false, true)) {
            val p = TypingCommandTree.of(admin)
            val literals = p.children().filter { it.parser == null }.map { it.name }
            assertEquals(listOf("profile", "settings"), literals.sorted(), "admin=$admin")
            assertEquals(listOf("profile", "settings", "t"), p.rootNames().sorted(), "admin=$admin")
        }
    }

    @Test
    fun `settings takes a setting then a value both asked from the server`() {
        val p = TypingCommandTree.of(false)
        val settings = p.nodes().indexOf(p.children().single { it.name == "settings" })
        val setting = p.children(settings).single()
        assertEquals("minecraft:ask_server", setting.suggestionsType)
        val idx = p.nodes().indexOf(setting)
        assertEquals("minecraft:ask_server", p.children(idx).single().suggestionsType)
    }

    @Test
    fun `profile takes one player name asked from the server`() {
        val p = TypingCommandTree.of(false)
        val profile = p.nodes().indexOf(p.children().single { it.name == "profile" })
        val player = p.children(profile).single()
        assertEquals("minecraft:ask_server", player.suggestionsType)
    }

    @Test
    fun `recognizes its own packets`() {
        assertTrue(TypingCommandTree.isOurs(TypingCommandTree.of(true)))
        assertTrue(TypingCommandTree.isOurs(TypingCommandTree.of(false)))
    }
}
