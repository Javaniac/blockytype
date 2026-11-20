package com.javaniac.blockytype.words

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanguageDownloaderTest {
    private val body = LanguageDownloaderTest::class.java.getResource("/languages/german.json")!!.readText()
    private var status = 200
    private var payload = body
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            val bytes = payload.toByteArray()
            ex.sendResponseHeaders(status, if (status == 200) bytes.size.toLong() else -1)
            if (status == 200) ex.responseBody.use { it.write(bytes) }
            ex.close()
        }
        start()
    }
    private val originalDir = LanguageRegistry.dir
    private val originalBase = LanguageDownloader.baseUrl
    private lateinit var dir: Path

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("lang")
        LanguageRegistry.dir = dir
        LanguageDownloader.baseUrl = "http://127.0.0.1:${server.address.port}/"
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
        LanguageRegistry.dir = originalDir
        LanguageDownloader.baseUrl = originalBase
    }

    @Test
    fun `a missing list is downloaded, validated and stored`() {
        assertFalse(LanguageRegistry.isAvailable("german"))
        val list = LanguageDownloader.download("german")
        assertEquals("german", list.name)
        assertTrue(LanguageRegistry.isAvailable("german"))
        assertEquals(body, dir.resolve("german.json").readText())
        assertFalse(dir.resolve("german.json.part").exists())
    }

    @Test
    fun `a failed download leaves nothing behind`() {
        status = 500
        assertFailsWith<IllegalArgumentException> { LanguageDownloader.download("german") }
        assertFalse(dir.resolve("german.json").exists())
        assertFalse(dir.resolve("german.json.part").exists())
    }

    @Test
    fun `a file that is not a word list is rejected`() {
        payload = "<html>not json</html>"
        assertFailsWith<Exception> { LanguageDownloader.download("german") }
        assertFalse(dir.resolve("german.json").exists())
    }

    @Test
    fun `the url is pinned to a commit`() {
        LanguageDownloader.baseUrl = LanguageDownloader.DEFAULT_BASE_URL
        assertEquals(
            "https://raw.githubusercontent.com/monkeytypegame/monkeytype/${LanguageDownloader.COMMIT}/frontend/static/languages/german.json",
            LanguageDownloader.url("german").toString(),
        )
        assertEquals(40, LanguageDownloader.COMMIT.length)
    }

    @Test
    fun `ensureDefault downloads english once`() {
        payload = LanguageDownloaderTest::class.java.getResource("/languages/english.json")!!.readText()
        assertTrue(LanguageDownloader.ensureDefault())
        assertTrue(LanguageRegistry.isAvailable("english"))
        status = 500 // already there: no second request
        assertTrue(LanguageDownloader.ensureDefault())
    }

    @Test
    fun `ensureDefault reports a failure instead of throwing`() {
        status = 503
        assertFalse(LanguageDownloader.ensureDefault())
    }
}
