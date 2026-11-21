package com.javaniac.blockytype.net

import net.minestom.server.entity.Player
import net.minestom.server.network.packet.client.ClientPacket
import net.minestom.server.network.packet.client.play.ClientTabCompletePacket
import net.minestom.server.network.player.GameProfile
import net.minestom.server.network.player.PlayerConnection
import java.util.Collections
import java.util.IdentityHashMap

/**
 * A player that notes when each typing packet came in. Minestom parses packets on the network thread but only
 * handles them in the next tick, so reading the clock there would round every key to a 50 ms step and let a
 * lagging connection pile its keys onto one tick. [addPacketToQueue] runs on the network thread, so the stamp is
 * the real arrival time.
 */
class ArrivalPlayer(connection: PlayerConnection, profile: GameProfile) : Player(connection, profile) {
    private val stamps: MutableMap<ClientPacket, Long> = Collections.synchronizedMap(IdentityHashMap())

    override fun addPacketToQueue(packet: ClientPacket) {
        if (packet is ClientTabCompletePacket) {
            if (stamps.size > MAX_PENDING) stamps.clear() // stamps nobody read (not in a test): never grow
            stamps[packet] = System.nanoTime()
        }
        super.addPacketToQueue(packet)
    }

    /** Round trip times of the last seconds (keepalive answers, see [KeepAlive]). */
    val rtt = RttTracker()

    private var lastTabUpdate = 0L

    /**
     * Called on the network thread with each keepalive answer. Every sample feeds [rtt]; the tab list ping (which
     * Minestom broadcasts to everybody on each call) is only refreshed every 2 s, not at the faster keepalive rate.
     */
    override fun refreshLatency(latency: Int) {
        rtt.add(latency.toDouble())
        val now = System.nanoTime()
        if (now - lastTabUpdate < TAB_UPDATE_NANOS) return
        lastTabUpdate = now
        super.refreshLatency(latency)
    }

    /** Arrival time (nanoTime) of [packet], or null when it was not stamped. Each stamp is read once. */
    fun takeArrival(packet: ClientPacket): Long? = stamps.remove(packet)

    private companion object {
        const val MAX_PENDING = 512
        const val TAB_UPDATE_NANOS = 2_000_000_000L
    }
}
