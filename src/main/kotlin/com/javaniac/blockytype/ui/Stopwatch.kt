package com.javaniac.blockytype.ui

import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.play.SetTimePacket

/**
 * Per-player animation clock. The client feeds `Globals.GameTime` (smooth, per frame) from its world game time,
 * which only changes when a [SetTimePacket] arrives. Resetting it to 0 on an event makes the shader see
 * "ticks since the event". The instance never broadcasts periodic time packets, so nothing else moves it.
 */
object Stopwatch {
    /**
     * Game time of the keybind probe (see `KeybindProbe`): negative, so `Globals.GameTime` is negative too, which
     * a real world never is. `text.vsh` hides every unmarked text while it is below `Shaders.PROBE_GAME_TIME`,
     * which makes the probe's anvil screen invisible. The client counts up from it (100 ticks in 5 s), far below 0.
     */
    const val PROBE_TICKS = -12000L

    fun probePacket() = SetTimePacket(PROBE_TICKS, emptyMap())

    fun resetPacket() = SetTimePacket(0L, emptyMap())

    fun reset(player: Player) = player.sendPacket(resetPacket())
}
