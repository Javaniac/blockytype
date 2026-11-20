package com.javaniac.blockytype.game

import net.minestom.server.command.ArgumentParserType
import net.minestom.server.network.packet.server.play.DeclareCommandsPacket
import net.minestom.server.network.packet.server.play.DeclareCommandsPacket.NodeType

/**
 * The only command tree players get: one root-level greedy string argument whose suggestions are asked
 * from the server. With a normal `/t <text>` command the client would never ask the server about `/` or
 * `/t` (literal nodes are local), so deleting into the prefix would be invisible. With a root argument every
 * edit of the command box sends a request with the full text (up to the cursor).
 *
 * Next to it sits the `settings` literal, so the client colors and completes it. It is the only command
 * chat completion offers when a player types `/` (the greedy argument is not a command name, and `restart`
 * and the admin `pack` command still work when typed, they are just not advertised). A matching literal
 * wins over the greedy argument when parsing.
 */
object TypingCommandTree {
    private class N(val name: String, val type: NodeType, val exec: Boolean, val ask: Boolean = false, val greedy: Boolean = false) {
        val kids = mutableListOf<N>()
        fun add(n: N) = n.also { kids += it }
    }

    private fun lit(name: String, exec: Boolean = true) = N(name, NodeType.LITERAL, exec)
    private fun arg(name: String, greedy: Boolean = false, ask: Boolean = true) =
        N(name, NodeType.ARGUMENT, true, ask, greedy)

    private fun build(): DeclareCommandsPacket {
        val root = N("", NodeType.ROOT, false)
        root.add(arg("t", greedy = true))
        root.add(lit("settings")).add(arg("setting")).add(arg("value", greedy = true))
        root.add(lit("profile")).add(arg("player"))
        val out = mutableListOf<DeclareCommandsPacket.Node>()
        fun emit(n: N): Int {
            val node = DeclareCommandsPacket.Node()
            val index = out.size
            out += node
            node.flags = DeclareCommandsPacket.getFlag(n.type, n.exec, false, n.ask)
            if (n.type != NodeType.ROOT) node.name = n.name
            if (n.type == NodeType.ARGUMENT) {
                node.parser = ArgumentParserType.STRING
                node.properties = byteArrayOf(if (n.greedy) 2 else 0)
                if (n.ask) node.suggestionsType = "minecraft:ask_server"
            }
            node.children = n.kids.map { emit(it) }.toIntArray()
            return index
        }
        emit(root)
        return DeclareCommandsPacket(out, 0)
    }

    val packet: DeclareCommandsPacket = build()

    /** The same tree for everybody; [admin] is kept so callers do not change. */
    @Suppress("UNUSED_PARAMETER")
    fun of(admin: Boolean) = packet

    fun isOurs(p: DeclareCommandsPacket) = p === packet
}
