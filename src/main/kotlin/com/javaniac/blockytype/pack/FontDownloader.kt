package com.javaniac.blockytype.pack

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Downloads a font file from an http(s) URL into `fonts/custom/` (it must pass the font check). Blocking: call off the tick thread. */
object FontDownloader {
    private const val MAX_BYTES = 25L * 1024 * 1024
    private val NAME = Regex("[a-z0-9._-]{1,48}")

    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    /** Name used for the font: [requested], else the file name of [url] (lowercased, without extension). */
    fun fontName(url: URI, requested: String?): String {
        val name = (requested ?: url.path.substringAfterLast('/').substringBeforeLast('.')).lowercase()
        require(NAME.matches(name)) { "invalid font name '$name' (use a-z 0-9 . _ -)" }
        return name
    }

    fun download(url: String, requestedName: String?): FontInfo {
        val uri = try {
            URI.create(url)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("invalid URL")
        }
        require(uri.scheme == "http" || uri.scheme == "https") { "only http(s) URLs are supported" }
        val extension = uri.path.substringAfterLast('.', "").lowercase()
        require(extension == "ttf" || extension == "otf") { "the URL must point to a .ttf or .otf file" }
        val name = fontName(uri, requestedName)

        val response = client.send(
            HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream(),
        )
        response.body().use { body ->
            require(response.statusCode() == 200) { "server answered HTTP ${response.statusCode()}" }
            val declared = response.headers().firstValueAsLong("Content-Length").orElse(-1)
            require(declared <= MAX_BYTES) { "file is larger than ${MAX_BYTES / 1024 / 1024} MB" }
            val bytes = body.readNBytes((MAX_BYTES + 1).toInt())
            require(bytes.size <= MAX_BYTES) { "file is larger than ${MAX_BYTES / 1024 / 1024} MB" }
            return FontRegistry.save(name, bytes, extension)
        }
    }
}
