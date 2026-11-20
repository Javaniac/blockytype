package com.javaniac.blockytype.sound

import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.zip.ZipInputStream

private val log = LoggerFactory.getLogger("SoundDownloader")

/**
 * Downloads the sound pack (`sounds.zip`: Ogg files plus `index.txt`, built by the Gradle task `soundsZip` from `assets/sounds`)
 * into [Sounds.dir] on the first start. The zip is pinned by its SHA-256, so it cannot change unnoticed.
 * Blocking: call at startup, not on the tick thread.
 */
object SoundDownloader {
    /** Release asset of the BlockyType repository that holds `sounds.zip` (upload the output of `./gradlew soundsZip`). */
    const val DEFAULT_URL = "https://github.com/Javaniac/blockytype/releases/download/assets-1/sounds.zip"

    /** SHA-256 of the released `sounds.zip` (printed by `./gradlew soundsZip`). */
    const val DEFAULT_SHA256 = "3d9d11058e751a068d89f0b39e272005089ba7c3d9173afe28f488b5b4532478"

    private const val MAX_BYTES = 64L * 1024 * 1024

    @Volatile
    var url: String = DEFAULT_URL

    @Volatile
    var sha256: String = DEFAULT_SHA256

    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    /** True when the sounds are on disk afterwards. A failure is logged, and the server runs without sounds. */
    @Synchronized
    fun ensure(): Boolean {
        if (Files.isRegularFile(Sounds.dir.resolve("index.txt"))) return true
        log.info("Downloading the sounds (first start only)...")
        return try {
            install(download())
            log.info("Sounds installed: ${Sounds.files().size} files")
            true
        } catch (e: Exception) {
            log.warn("Could not download the sounds, the server runs without sound: ${e.message}")
            false
        }
    }

    private fun download(): ByteArray {
        val response = client.send(
            HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(120)).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream(),
        )
        response.body().use { body ->
            require(response.statusCode() == 200) { "HTTP ${response.statusCode()} for $url" }
            val bytes = body.readNBytes(MAX_BYTES.toInt() + 1)
            require(bytes.size <= MAX_BYTES) { "sounds.zip is too big" }
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            require(actual == sha256) { "sounds.zip does not match the pinned SHA-256 (got $actual)" }
            return bytes
        }
    }

    /** Unpacks [zip] next to [Sounds.dir] and renames it into place, so a half-written folder is never used. */
    private fun install(zip: ByteArray) {
        val target = Sounds.dir.toAbsolutePath().normalize()
        val tmp = target.resolveSibling(target.fileName.toString() + ".tmp")
        deleteTree(tmp)
        Files.createDirectories(tmp)
        try {
            ZipInputStream(ByteArrayInputStream(zip)).use { z ->
                while (true) {
                    val entry = z.nextEntry ?: break
                    val out = tmp.resolve(entry.name).normalize()
                    require(out.startsWith(tmp)) { "unsafe path in sounds.zip: ${entry.name}" }
                    if (entry.isDirectory) {
                        Files.createDirectories(out)
                    } else {
                        Files.createDirectories(out.parent)
                        Files.copy(z, out, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
            require(Files.isRegularFile(tmp.resolve("index.txt"))) { "sounds.zip has no index.txt" }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        } catch (t: Throwable) {
            deleteTree(tmp)
            throw t
        }
    }

    private fun deleteTree(dir: Path) {
        if (!Files.exists(dir)) return
        Files.walk(dir).use { s -> s.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }
}
