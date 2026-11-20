package com.javaniac.blockytype.events.join

import com.javaniac.blockytype.BlockyType
import com.javaniac.blockytype.db.PlayerSettings
import com.javaniac.blockytype.db.Storage
import com.javaniac.blockytype.settings.ServerDefaults
import com.javaniac.blockytype.themes.RandomTheme
import kotlinx.coroutines.runBlocking
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.EventListener
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.instance.block.Block
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ConnectEvent")

object ConnectEvent: EventListener<AsyncPlayerConfigurationEvent> {
    /** Where the command block sits, directly in front of the camera stand. */
    val BLOCK_POS = BlockVec(0, 1, 2)

    /** The camera stand. The view and the crosshair ray start here. */
    val STAND = Pos(0.5, 1.5, 0.5, 0f, 0f) // marker stands have no eye height: this y is the eye y

    /**
     * The player's own body, behind the stand: the crosshair ray must not start inside it, but the block
     * has to stay in interaction range (measured from the body).
     */
    val SPAWN = Pos(0.5, 0.0, -1.5, 0f, 0f)

    override fun eventType(): Class<AsyncPlayerConfigurationEvent> = AsyncPlayerConfigurationEvent::class.java

    override fun run(event: AsyncPlayerConfigurationEvent): EventListener.Result {
        val instance = MinecraftServer.getInstanceManager()
            .createInstanceContainer().apply {
                setTime(6000)
                defaultClock()?.pause()
                loadChunk(0, 0).join()
                loadChunk(0, -1).join()
                setBlock(BLOCK_POS, Block.COMMAND_BLOCK)
            }
        event.spawningInstance = instance

        event.player.apply {
            respawnPoint = SPAWN
            gameMode = GameMode.CREATIVE // the client only opens command blocks in creative...
            permissionLevel = 2 // ...with op level 2
        }

        // Configuration is async: loading settings here does not block the tick thread.
        val settings = try {
            runBlocking { Storage.loadSettings(event.player.uuid) }
        } catch (t: Throwable) {
            log.error("Could not load settings of ${event.player.username}", t)
            PlayerSettings()
        }
        BlockyType.pendingSettings[event.player.uuid] = settings

        // `random-theme`: a theme for this visit only, the stored choice stays.
        val random = RandomTheme.pick(ServerDefaults.effective("random-theme", settings.overrides), BlockyType.themes)
        if (random != null) BlockyType.pendingTheme[event.player.uuid] = random
        val overrides = if (random != null) settings.overrides + ("theme" to random) else settings.overrides

        // The pack depends on the player's settings (font, anti-aliasing, input box), so it goes out after loading them.
        BlockyType.packServer.send(event.player, ServerDefaults.packVariant(overrides))

        return EventListener.Result.SUCCESS
    }
}
