package com.javaniac.blockytype.pack

import com.javaniac.blockytype.BlockyType
import com.javaniac.blockytype.withMinestomMain
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.launch
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.minestom.server.entity.Player
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("PackServer")

/**
 * Where the pack is hosted. [host] is the address clients download from (what goes into the
 * resource pack request), [bindHost]/[port] is where the HTTP server listens.
 */
data class PackServerConfig(
    val host: String = "localhost",
    val port: Int = DEFAULT_PORT,
    val bindHost: String = "0.0.0.0",
) {
    fun url(file: PackFile) = url(file.sha1, null)

    /**
     * Download URL of the pack with [sha1] for a client connecting from [remote]. A client on this machine
     * gets the loopback address: the public [host] is often unreachable from inside the same network (no hairpin NAT).
     */
    fun url(sha1: String, remote: SocketAddress?): String {
        val local = (remote as? InetSocketAddress)?.address?.isLoopbackAddress == true
        return "http://${if (local) "127.0.0.1" else host}:$port/pack/$sha1.zip"
    }

    companion object {
        const val DEFAULT_PORT = 25500
    }
}

/**
 * Generates the resource packs in memory (one per [PackVariant], see [PackCache]), serves them over HTTP
 * (Minestom does not host files) and sends each player the pack of their settings.
 */
class PackServer(private val config: PackServerConfig = PackServerConfig()) {
    private val cache = PackCache(build = { PackBuilder.zip(PackBuilder.build(it)) })

    /** The pack each online player currently has. Also keeps their file alive while it downloads. */
    private val current = ConcurrentHashMap<UUID, PackFile>()

    private var server: HttpServer? = null

    fun start() {
        val http = HttpServer.create(InetSocketAddress(config.bindHost, config.port), 0)
        http.createContext("/pack/") { ex ->
            ex.use {
                val hash = it.requestURI.path.removePrefix("/pack/").removeSuffix(".zip")
                val file = cache.byHash(hash) ?: current.values.firstOrNull { f -> f.sha1 == hash }
                if (file == null) {
                    it.sendResponseHeaders(404, -1)
                    return@use
                }
                it.responseHeaders.add("Content-Type", "application/zip")
                if (it.requestMethod == "HEAD") {
                    it.sendResponseHeaders(200, -1)
                } else {
                    it.sendResponseHeaders(200, file.zip.size.toLong())
                    it.responseBody.use { body -> body.write(file.zip) }
                }
            }
        }
        http.start()
        server = http
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun request(file: PackFile, remote: SocketAddress?): ResourcePackRequest = ResourcePackRequest.resourcePackRequest()
        .packs(ResourcePackInfo.resourcePackInfo(file.id, URI.create(config.url(file.sha1, remote)), file.sha1))
        .required(true)
        .build()

    /**
     * Sends [player] the pack of [variant] right away (use during configuration, where the thread may block).
     * Nothing is sent when they already have that exact pack.
     */
    fun send(player: Player, variant: PackVariant) {
        val file = cache.get(variant)
        val old = current.put(player.uuid, file)
        if (old != null) {
            if (old.id == file.id) return
            player.removeResourcePacks(old.id)
        }
        player.sendResourcePacks(request(file, player.playerConnection.remoteAddress))
    }

    /** Like [send] for a player in the game: builds the pack off the tick thread. */
    fun sendAsync(player: Player, variant: PackVariant) {
        BlockyType.launch {
            try {
                cache.get(variant) // build off the tick thread, send() then finds it cached
                withMinestomMain { send(player, variant) }
            } catch (t: Throwable) {
                log.error("Could not send the pack to ${player.username}", t)
            }
        }
    }

    fun forget(player: Player) {
        current.remove(player.uuid)
    }

    /**
     * Forgets all built packs (fonts changed on disk) and builds them again for the players online, resending
     * where the content changed. Returns the SHA-1 of [defaultVariant]'s pack. Blocking.
     */
    fun reload(defaultVariant: PackVariant, variantOf: (Player) -> PackVariant?): String {
        cache.clear()
        val fresh = cache.get(defaultVariant) // fails early if the default pack cannot be built
        for ((uuid, _) in current.entries.toList()) {
            val player = net.minestom.server.MinecraftServer.getConnectionManager().onlinePlayers
                .firstOrNull { it.uuid == uuid } ?: continue
            send(player, variantOf(player) ?: defaultVariant)
        }
        return fresh.sha1
    }
}
