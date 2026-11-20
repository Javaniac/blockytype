package com.javaniac.blockytype.words

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val log = LoggerFactory.getLogger("LanguageDownloader")

/**
 * Downloads a Monkeytype word list into `languages/`, pinned to a commit.
 * Blocking: call off the tick thread. Only one download runs at a time.
 */
object LanguageDownloader {
    const val COMMIT = "78a22fdbb94d3a48b879e49e38e6c2d80cd5a60f"
    private const val MAX_BYTES = 30L * 1024 * 1024

    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    private val lock = ReentrantLock()

    const val DEFAULT_BASE_URL = "https://raw.githubusercontent.com/monkeytypegame/monkeytype/$COMMIT/frontend/static/languages/"

    /** Where the lists come from (tests use a local server). */
    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL

    fun url(name: String): URI = URI.create("$baseUrl$name.json")

    /** Makes sure the default list ([LanguageRegistry.DEFAULT]) is on disk. False (and a log line) when the download failed. */
    fun ensureDefault(): Boolean {
        if (LanguageRegistry.isAvailable(LanguageRegistry.DEFAULT)) return true
        log.info("Downloading the default word list (${LanguageRegistry.DEFAULT}, first start only)...")
        return try {
            download(LanguageRegistry.DEFAULT)
            true
        } catch (e: Exception) {
            log.error("Could not download the default word list: ${e.message}. Typing tests need it; the server retries when a test starts.")
            false
        }
    }

    fun download(name: String): WordList {
        val entry = requireNotNull(LanguageRegistry.entry(name)) { "unknown language '$name'" }
        return lock.withLock {
            if (LanguageRegistry.isAvailable(name)) return@withLock LanguageRegistry.load(name)
            require(entry.bytes <= MAX_BYTES) { "language '$name' is too big" }
            val response = client.send(
                HttpRequest.newBuilder(url(name)).timeout(Duration.ofSeconds(60)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream(),
            )
            response.body().use { body ->
                require(response.statusCode() == 200) { "download failed: HTTP ${response.statusCode()}" }
                val bytes = body.readNBytes(MAX_BYTES.toInt() + 1)
                require(bytes.size <= MAX_BYTES) { "language '$name' is too big" }
                val list = LanguageRegistry.parseList(name, bytes.decodeToString(), entry.orderedByFrequency) // validates
                Files.createDirectories(LanguageRegistry.dir)
                val target = LanguageRegistry.file(name)
                val tmp = target.resolveSibling("$name.json.part")
                Files.write(tmp, bytes)
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
                list
            }
        }
    }
}
