package com.javaniac.blockytype.pack

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val log = LoggerFactory.getLogger("FontInstaller")

/** Downloads the built-in fonts ([FontManifest]) and their licenses into `fonts/builtin/`, verifying every file's SHA-256. */
object FontInstaller {
    private const val MAX_BYTES = 8L * 1024 * 1024
    private const val THREADS = 4

    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    /** [installed] are the names that were downloaded now, [failed] maps names to the reason. Fonts already in place are in neither. */
    data class Result(val installed: List<String>, val failed: Map<String, String>)

    /** Installs the missing or modified fonts of [fonts] into [dir]. Blocking. A failure of one font does not stop the others. */
    fun install(fonts: List<BuiltinFont> = FontRegistry.builtins, dir: Path = FontRegistry.builtinFolder()): Result {
        Files.createDirectories(dir)
        val todo = fonts.filter { !isCurrent(it, dir) }
        if (todo.isEmpty()) return Result(emptyList(), emptyMap())
        log.info("Downloading ${todo.size} built-in fonts (first start only)...")
        val installed = java.util.Collections.synchronizedList(mutableListOf<String>())
        val failed = java.util.Collections.synchronizedMap(linkedMapOf<String, String>())
        val pool = Executors.newFixedThreadPool(minOf(THREADS, todo.size))
        try {
            for (font in todo) {
                pool.execute {
                    try {
                        fetch(font.fontUrl, font.fontSha256, dir.resolve("${font.name}.ttf"))
                        fetch(font.licenseUrl, font.licenseSha256, dir.resolve("${font.name}.license.txt"))
                        installed += font.name
                    } catch (e: Exception) {
                        failed[font.name] = e.message ?: e.toString()
                        log.warn("Could not download font ${font.name}: ${e.message}")
                    }
                }
            }
        } finally {
            pool.shutdown()
            pool.awaitTermination(10, TimeUnit.MINUTES)
        }
        return Result(installed.sorted(), failed.toMap())
    }

    private fun isCurrent(font: BuiltinFont, dir: Path) =
        hashOf(dir.resolve("${font.name}.ttf")) == font.fontSha256 && hashOf(dir.resolve("${font.name}.license.txt")) == font.licenseSha256

    private fun hashOf(file: Path): String? = if (Files.isRegularFile(file)) sha256(Files.readAllBytes(file)) else null

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun fetch(url: String, sha256: String, target: Path) {
        if (hashOf(target) == sha256) return
        val response = client.send(
            HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream(),
        )
        response.body().use { body ->
            require(response.statusCode() == 200) { "HTTP ${response.statusCode()} for $url" }
            val bytes = body.readNBytes(MAX_BYTES.toInt() + 1)
            require(bytes.size <= MAX_BYTES) { "file is larger than ${MAX_BYTES / 1024 / 1024} MB: $url" }
            val actual = sha256(bytes)
            require(actual == sha256) { "SHA-256 mismatch for $url (expected $sha256, got $actual)" }
            val part = target.resolveSibling(target.fileName.toString() + ".part")
            Files.write(part, bytes)
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
