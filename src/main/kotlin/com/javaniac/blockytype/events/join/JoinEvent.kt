package com.javaniac.blockytype.events.join

import com.javaniac.blockytype.BlockyType
import kotlinx.coroutines.launch
import com.javaniac.blockytype.commands.AdminTag
import com.javaniac.blockytype.ServerConfig
import com.javaniac.blockytype.db.PlayerSettings
import com.javaniac.blockytype.db.Storage
import com.javaniac.blockytype.game.HiddenHudNotice
import com.javaniac.blockytype.game.Sessions
import com.javaniac.blockytype.game.TestSession
import com.javaniac.blockytype.game.TypingCommandTree
import com.javaniac.blockytype.net.ArrivalPlayer
import com.javaniac.blockytype.net.PingNotice
import net.minestom.server.MinecraftServer
import net.minestom.server.timer.TaskSchedule
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.other.ArmorStandMeta
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.network.packet.server.play.CameraPacket
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("JoinEvent")

object JoinEvent: EventListener<PlayerSpawnEvent> {
    override fun eventType(): Class<PlayerSpawnEvent> = PlayerSpawnEvent::class.java

    override fun run(event: PlayerSpawnEvent): EventListener.Result {
        if (!event.isFirstSpawn) return EventListener.Result.SUCCESS
        val player = event.player

        // Camera: an invisible marker stand in front of the command block.
        val stand = Entity(EntityType.ARMOR_STAND)
        stand.editEntityMeta(ArmorStandMeta::class.java) {
            it.isMarker = true
            it.isHasNoBasePlate = true
            it.isHasNoGravity = true
            it.isInvisible = true
        }
        stand.setInstance(event.instance, ConnectEvent.STAND)
        player.sendPacket(CameraPacket(stand.entityId))
        HiddenHudNotice.spawn(event.instance, player)

        // Hover in place: a falling body would be out of interaction range of the command block.
        player.isAllowFlying = true
        player.isFlying = true
        player.setNoGravity(true)

        player.setHeldItemSlot(4)
        player.sendPacket(TypingCommandTree.of(ServerConfig.isAdmin(player.uuid)))

        val settings = BlockyType.pendingSettings.remove(player.uuid) ?: PlayerSettings()
        val session = TestSession(player, event.instance, ConnectEvent.BLOCK_POS, stand.entityId, settings, BlockyType.pendingTheme.remove(player.uuid))
        Sessions.put(session)
        session.render()
        session.refreshStats()

        // Let the pack and HUD settle, then read the player's keybinds.
        MinecraftServer.getSchedulerManager().buildTask { if (player.isOnline) session.startKeybindProbe() }
            .delay(TaskSchedule.seconds(3)).schedule()

        // Warn about a high ping once the keepalive answers have measured it.
        MinecraftServer.getSchedulerManager().buildTask {
            val rtt = (player as? ArrivalPlayer)?.rtt?.snapshot()
            if (player.isOnline && PingNotice.warns(rtt)) player.sendMessage(PingNotice.message(session.theme, rtt!!.median))
        }.delay(TaskSchedule.seconds(PingNotice.DELAY_SECONDS)).schedule()

        session.startKeybindPoll()
        BlockyType.launch {
            try {
                Storage.touchPlayer(player.uuid, player.username)
            } catch (t: Throwable) {
                log.error("Could not record ${player.username}", t)
            }
        }
        AdminTag.refresh(player)

        return EventListener.Result.SUCCESS
    }
}
