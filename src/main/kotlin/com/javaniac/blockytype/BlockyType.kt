package com.javaniac.blockytype

import com.javaniac.blockytype.net.ArrivalPlayer
import com.javaniac.blockytype.net.KeepAlive
import com.javaniac.blockytype.commands.GrantAdminCommand
import com.javaniac.blockytype.commands.PackCommand
import com.javaniac.blockytype.commands.RevokeAdminCommand
import com.javaniac.blockytype.commands.ProfileCommand
import com.javaniac.blockytype.commands.MojangProfiles
import com.javaniac.blockytype.profile.PlayerBackfill
import com.javaniac.blockytype.commands.RestartCommand
import com.javaniac.blockytype.commands.SettingsCommand
import com.javaniac.blockytype.db.PlayerSettings
import com.javaniac.blockytype.db.Storage
import com.javaniac.blockytype.events.game.GameEvents
import com.javaniac.blockytype.events.join.ConnectEvent
import com.javaniac.blockytype.events.join.JoinEvent
import com.javaniac.blockytype.game.Sessions
import com.javaniac.blockytype.pack.PackServer
import com.javaniac.blockytype.pack.FontInstaller
import com.javaniac.blockytype.pack.FontRegistry
import com.javaniac.blockytype.pack.PackServerConfig
import com.javaniac.blockytype.settings.ServerDefaults
import com.javaniac.blockytype.sound.SoundDownloader
import com.javaniac.blockytype.words.LanguageDownloader
import com.javaniac.blockytype.settings.ServerSettings
import com.javaniac.blockytype.themes.AbstractTheme
import com.javaniac.blockytype.themes.DefaultTheme
import com.javaniac.blockytype.themes.MONKEYTYPE_THEMES
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import net.minestom.server.Auth
import net.minestom.server.MinecraftServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The main class of BlockyType, a typing game made in Minecraft.
 */
object BlockyType: CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext = job + Dispatchers.Default

    val logger: Logger = LoggerFactory.getLogger("BlockyType")
    val themes = mutableListOf<AbstractTheme>()

    /** Settings loaded during configuration, picked up when the player spawns. */
    val pendingSettings = ConcurrentHashMap<UUID, PlayerSettings>()

    /** Theme picked by `random-theme` while a player connects (not stored in their settings). */
    val pendingTheme = ConcurrentHashMap<UUID, String>()

    /** Effective network settings, resolved in [main] before [packServer] is first used. */
    lateinit var network: NetworkConfig
        private set

    val packServer by lazy {
        PackServer(PackServerConfig(host = network.resourcePackIp.value, port = network.resourcePackPort.value))
    }

    /** Rebuilds every pack (fonts changed on disk or a default changed) and resends where needed. Returns the default pack's SHA-1. */
    fun reloadPacks(): String = packServer.reload(ServerDefaults.packVariant(emptyMap())) { Sessions[it]?.packVariant() }

    /** Network values of the settings file (keys `server-ip`, `server-port`, ...). */
    private fun resolveNetwork(panel: Map<String, String>): NetworkConfig {
        fun port(key: String) = panel[key]?.toIntOrNull()?.takeIf { it in 1..65535 }
        return NetworkConfig(
            serverIp = resolve(panel["server-ip"]?.takeIf { it.isNotBlank() }, StartupArgs.DEFAULT_SERVER_IP),
            serverPort = resolve(port("server-port"), StartupArgs.DEFAULT_SERVER_PORT),
            resourcePackIp = resolve(panel["resource-pack-ip"]?.takeIf { it.isNotBlank() }, StartupArgs.DEFAULT_RESOURCE_PACK_IP),
            resourcePackPort = resolve(port("resource-pack-port"), StartupArgs.DEFAULT_RESOURCE_PACK_PORT),
        )
    }

    @JvmStatic
    fun main(args: Array<String>) {
        KeepAlive.configure() // before anything reads Minestom's ServerProperties
        FontRegistry.init()
        Storage.init()
        ServerConfig.loadAdmins(runBlocking { Storage.admins() }.map { it.uuid })
        // The settings file replaces the old server_settings table: its rows are copied in once, then removed.
        val legacy = runBlocking { Storage.serverSettings() }
        val settings = ServerSettings.load(legacy)
        if (legacy.isNotEmpty()) {
            runBlocking { legacy.keys.forEach { Storage.clearServerSetting(it) } }
            logger.info("Moved ${legacy.size} settings from the database into blockytype.properties")
        }
        network = resolveNetwork(settings)
        LanguageDownloader.ensureDefault()
        SoundDownloader.ensure()
        StartupArgs.banner(network).lines().forEach { logger.info(it) }

        val server = MinecraftServer.init(Auth.Online())
        ServerBrand.install()
        MinecraftServer.getConnectionManager().setPlayerProvider { connection, profile -> ArrivalPlayer(connection, profile) }
        packServer.start()
        MinecraftServer.getCommandManager().register(PackCommand)
        MinecraftServer.getCommandManager().register(SettingsCommand)
        MinecraftServer.getCommandManager().register(RestartCommand)
        MinecraftServer.getCommandManager().register(ProfileCommand)
        MinecraftServer.getCommandManager().register(GrantAdminCommand)
        MinecraftServer.getCommandManager().register(RevokeAdminCommand)
        Console.start()

        MinecraftServer.getGlobalEventHandler()
            .addListener(ConnectEvent)
            .addListener(JoinEvent)
        GameEvents.register()

        MinecraftServer.getSchedulerManager()
            .buildShutdownTask {
                job.cancel()
                packServer.stop()
            }

        themes.apply {
            add(DefaultTheme)
            addAll(MONKEYTYPE_THEMES.sortedBy { it.name })
        }

        // Players from before the players table existed become known (and so can be looked up) in the background.
        BlockyType.launch {
            try {
                val added = PlayerBackfill.run { id ->
                    try {
                        MojangProfiles.byUuid(id).also { Thread.sleep(1500) } // stay far below Mojang's rate limit
                    } catch (e: Exception) {
                        logger.warn("Could not look up $id: ${e.message}")
                        null
                    }
                }
                if (added > 0) logger.info("Added $added existing players to the player list")
            } catch (t: Throwable) {
                logger.error("Player backfill failed", t)
            }
        }

        server.start(network.serverIp.value, network.serverPort.value)
        logger.info("Successfully started!")

        // Built-in fonts other than JetBrains Mono are downloaded in the background; players get them once they are there.
        BlockyType.launch {
            try {
                val result = FontInstaller.install()
                if (result.installed.isNotEmpty()) {
                    logger.info("Installed ${result.installed.size} built-in fonts")
                    withMinestomMain { reloadPacks() }
                }
            } catch (t: Throwable) {
                logger.error("Font download failed", t)
            }
        }
    }
}
