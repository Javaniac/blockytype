package com.javaniac.blockytype.sound

import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoundDownloaderTest {
    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, text) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private val good = zip("index.txt" to "click1/1.ogg\ntime_warning.ogg\n", "click1/1.ogg" to "OggS-1", "time_warning.ogg" to "OggS-w")
    private var served = good
    private var status = 200
    private var requests = 0
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            requests++
            val bytes = served
            ex.sendResponseHeaders(status, if (status == 200) bytes.size.toLong() else -1)
            if (status == 200) ex.responseBody.use { it.write(bytes) }
            ex.close()
        }
        start()
    }
    private val originalDir = Sounds.dir
    private val originalUrl = SoundDownloader.url
    private val originalHash = SoundDownloader.sha256
    private lateinit var target: Path

    @BeforeTest
    fun setUp() {
        target = Files.createTempDirectory("snd").resolve("sounds")
        Sounds.dir = target
        SoundDownloader.url = "http://127.0.0.1:${server.address.port}/sounds.zip"
        SoundDownloader.sha256 = sha(good)
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
        Sounds.dir = originalDir
        SoundDownloader.url = originalUrl
        SoundDownloader.sha256 = originalHash
    }

    @Test
    fun `the sounds are downloaded, verified and unpacked`() {
        assertTrue(SoundDownloader.ensure())
        assertEquals(listOf("click1/1.ogg", "time_warning.ogg"), Sounds.files())
        assertEquals("OggS-1", String(Sounds.bytes("click1/1.ogg")))
        assertFalse(target.resolveSibling("sounds.tmp").exists())
    }

    @Test
    fun `nothing is downloaded when the sounds are already there`() {
        assertTrue(SoundDownloader.ensure())
        val before = requests
        status = 500
        assertTrue(SoundDownloader.ensure())
        assertEquals(before, requests)
    }

    @Test
    fun `a wrong hash is rejected and nothing is kept`() {
        SoundDownloader.sha256 = "0".repeat(64)
        assertFalse(SoundDownloader.ensure())
        assertFalse(target.exists())
        assertEquals(emptyList(), Sounds.files())
    }

    @Test
    fun `a failed download leaves nothing behind`() {
        status = 503
        assertFalse(SoundDownloader.ensure())
        assertFalse(target.exists())
    }

    @Test
    fun `entries that climb out of the folder are rejected`() {
        served = zip("index.txt" to "a.ogg\n", "../evil.ogg" to "x")
        SoundDownloader.sha256 = sha(served)
        assertFalse(SoundDownloader.ensure())
        assertFalse(target.exists())
        assertFalse(target.resolveSibling("evil.ogg").exists())
    }

    @Test
    fun `a missing sound is an empty list, not a crash`() {
        assertEquals(emptyList(), Sounds.files())
        assertTrue(Sounds.soundsJson().isNotEmpty())
        assertTrue(target.readBytesOrNull() == null)
    }

    private fun Path.readBytesOrNull() = if (exists()) readBytes() else null
}
