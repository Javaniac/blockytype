package com.javaniac.blockytype.pack

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FontInstallerTest {
    private val files = ConcurrentHashMap<String, ByteArray>()
    private val hits = AtomicInteger()
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            hits.incrementAndGet()
            val body = files[ex.requestURI.path.removePrefix("/")]
            if (body == null) ex.sendResponseHeaders(404, -1) else {
                ex.sendResponseHeaders(200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            }
            ex.close()
        }
        start()
    }
    private val base = "http://127.0.0.1:${server.address.port}/"
    private val dir: Path = Files.createTempDirectory("builtin")
    private val fontBytes = FontInstallerTest::class.java.getResourceAsStream("/fonts/test-mono.ttf")!!.use { it.readBytes() }
    private val licenseBytes = "OFL license text".toByteArray()

    @AfterTest
    fun stop() = server.stop(0)

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun font(name: String, fontHash: String = sha(fontBytes)): BuiltinFont {
        files["$name.ttf"] = fontBytes
        files["$name-OFL.txt"] = licenseBytes
        return BuiltinFont(name, "$base$name.ttf", fontHash, "$base$name-OFL.txt", sha(licenseBytes))
    }

    @Test
    fun `missing fonts and their licenses are downloaded and verified`() {
        val result = FontInstaller.install(listOf(font("a"), font("b")), dir)
        assertEquals(listOf("a", "b"), result.installed.sorted())
        assertTrue(result.failed.isEmpty())
        assertEquals(sha(fontBytes), sha(dir.resolve("a.ttf").readBytes()))
        assertEquals("OFL license text", dir.resolve("a.license.txt").readText())
        assertFalse(Files.list(dir).use { s -> s.anyMatch { it.fileName.toString().endsWith(".part") } })
    }

    @Test
    fun `fonts that are already valid are not downloaded again`() {
        val a = font("a")
        FontInstaller.install(listOf(a), dir)
        val before = hits.get()
        val result = FontInstaller.install(listOf(a), dir)
        assertEquals(before, hits.get())
        assertTrue(result.installed.isEmpty())
    }

    @Test
    fun `a modified builtin file is replaced`() {
        val a = font("a")
        dir.resolve("a.ttf").writeBytes("changed".toByteArray())
        dir.resolve("a.license.txt").toFile().writeText("OFL license text")
        FontInstaller.install(listOf(a), dir)
        assertEquals(sha(fontBytes), sha(dir.resolve("a.ttf").readBytes()))
    }

    @Test
    fun `a wrong hash is rejected, nothing is kept, and the others still install`() {
        val bad = font("bad", fontHash = "0".repeat(64))
        val ok = font("ok")
        val result = FontInstaller.install(listOf(bad, ok), dir)
        assertEquals(listOf("ok"), result.installed)
        assertEquals(listOf("bad"), result.failed.keys.toList())
        assertFalse(dir.resolve("bad.ttf").exists() || dir.resolve("bad.ttf.part").exists())
    }

    @Test
    fun `an unreachable font is reported, not thrown`() {
        val gone = BuiltinFont("gone", "${base}nope.ttf", "0".repeat(64), "${base}nope.txt", "0".repeat(64))
        val result = FontInstaller.install(listOf(gone), dir)
        assertTrue("gone" in result.failed)
    }
}

class FontManifestTest {
    @Test
    fun `every manifest entry is pinned and complete`() {
        val all = FontManifest.ALL
        assertEquals(21, all.size)
        assertEquals(all.size, all.map { it.name }.toSet().size)
        assertFalse(all.any { it.name == FontRegistry.BUNDLED })
        for (f in all) {
            assertTrue(Regex("[a-z0-9-]+").matches(f.name), f.name)
            for (url in listOf(f.fontUrl, f.licenseUrl)) {
                assertTrue(Regex("https://raw\\.githubusercontent\\.com/[^/]+/[^/]+/[0-9a-f]{40}/.+").matches(url), "${f.name}: $url is not pinned to a commit")
            }
            assertTrue(Regex("[0-9a-f]{64}").matches(f.fontSha256) && Regex("[0-9a-f]{64}").matches(f.licenseSha256), f.name)
        }
    }
}
