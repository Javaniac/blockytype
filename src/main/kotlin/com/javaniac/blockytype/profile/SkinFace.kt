package com.javaniac.blockytype.profile

import com.javaniac.blockytype.words.JsonReader
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO

/**
 * The 8 x 8 face of a Minecraft skin (face plus hat layer) as 64 `0xRRGGBB` values, row by row. The skin comes from
 * the `textures` property of a profile; only Mojang's texture server is ever contacted for the image.
 */
object SkinFace {
    private const val TEXTURE_HOST = "textures.minecraft.net"
    private const val MAX_PNG_BYTES = 1_000_000

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    /** A neutral head for players without a usable skin. */
    val PLACEHOLDER: IntArray = IntArray(64) { i ->
        val x = i % 8
        val y = i / 8
        when {
            y < 2 || (y == 2 && (x == 0 || x == 7)) -> 0x4A3B2A // hair
            y == 4 && (x == 2 || x == 5) -> 0x3B5FD0 // eyes
            y == 6 && x in 3..4 -> 0x9C6B58 // mouth
            else -> 0xC29B7E // skin
        }
    }

    /** The https skin url in a base64 `textures` property value, or null (also for any host but Mojang's). */
    fun skinUrl(textureValue: String): String? = try {
        val json = JsonReader.parse(String(Base64.getDecoder().decode(textureValue.trim()), Charsets.UTF_8)) as? Map<*, *>
        val url = (((json?.get("textures") as? Map<*, *>)?.get("SKIN") as? Map<*, *>)?.get("url") as? String)
        val uri = url?.let { URI(it) }
        if (uri != null && uri.scheme in listOf("http", "https") && uri.host == TEXTURE_HOST && uri.userInfo == null && uri.port == -1) {
            "https://$TEXTURE_HOST${uri.rawPath}"
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    /** The `textures` value of a session server profile json, or null. */
    fun textureValue(profileJson: String): String? = try {
        val props = (JsonReader.parse(profileJson) as? Map<*, *>)?.get("properties") as? List<*>
        props?.firstNotNullOfOrNull { p ->
            (p as? Map<*, *>)?.takeIf { it["name"] == "textures" }?.get("value") as? String
        }
    } catch (_: Exception) {
        null
    }

    /** The face of a skin image: the head's front at (8, 8) with the hat layer (40, 8) over it. */
    fun face(skin: BufferedImage): IntArray {
        if (skin.width < 64 || skin.height < 16) return PLACEHOLDER.copyOf()
        return IntArray(64) { i ->
            val x = i % 8
            val y = i / 8
            val base = skin.getRGB(8 + x, 8 + y)
            val hat = skin.getRGB(40 + x, 8 + y)
            val alpha = hat ushr 24
            val c = if (alpha >= 128) hat else base
            c and 0xFFFFFF
        }
    }

    fun fromPng(bytes: ByteArray): IntArray? = try {
        ImageIO.read(ByteArrayInputStream(bytes))?.let(::face)
    } catch (_: Exception) {
        null
    }

    /** Downloads the face for a `textures` value. Blocking. Null when there is no skin or the download fails. */
    fun download(textureValue: String): IntArray? {
        val url = skinUrl(textureValue) ?: return null
        return try {
            val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() != 200 || response.body().size > MAX_PNG_BYTES) null else fromPng(response.body())
        } catch (_: Exception) {
            null
        }
    }

    /** The `textures` value of an offline player's profile from Mojang's session server. Blocking, null on any failure. */
    fun lookupTextureValue(uuid: UUID): String? = try {
        val request = HttpRequest.newBuilder(
            URI.create("https://sessionserver.mojang.com/session/minecraft/profile/${uuid.toString().replace("-", "")}")
        ).timeout(Duration.ofSeconds(8)).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 200) textureValue(response.body()) else null
    } catch (_: Exception) {
        null
    }
}
