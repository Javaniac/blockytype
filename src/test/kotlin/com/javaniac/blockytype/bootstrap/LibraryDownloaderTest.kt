package com.javaniac.blockytype.bootstrap

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryDownloaderTest {
    private val files = ConcurrentHashMap<String, ByteArray>()
    private val hits = ConcurrentHashMap<String, AtomicInteger>()
    private val failFirst = ConcurrentHashMap.newKeySet<String>()
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            val path = ex.requestURI.path.removePrefix("/")
            val n = hits.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            val body = files[path]
            if (body == null) ex.sendResponseHeaders(404, -1)
            else if (path in failFirst && n == 1) ex.sendResponseHeaders(503, -1)
            else {
                ex.sendResponseHeaders(200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            }
            ex.close()
        }
        start()
    }
    private val base = "http://127.0.0.1:${server.address.port}/"
    private val dir: Path = Files.createTempDirectory("libs")

    @AfterTest
    fun stop() = server.stop(0)

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun publish(coords: String, content: String): Library {
        val bytes = content.toByteArray()
        val lib = Library.parse("$coords ${sha(bytes)}")
        files[lib.path()] = bytes
        return lib
    }

    private fun downloader() = LibraryDownloader(base, dir) { }

    @Test
    fun `a library line parses and maps to the maven layout`() {
        val l = Library.parse("org.jetbrains.kotlin:kotlin-stdlib:2.4.20 ABC123")
        assertEquals("org/jetbrains/kotlin/kotlin-stdlib/2.4.20/kotlin-stdlib-2.4.20.jar", l.path())
        assertEquals("abc123", l.sha256())
        val c = Library.parse("a.b:c:1.0:natives-linux 00")
        assertEquals("a/b/c/1.0/c-1.0-natives-linux.jar", c.path())
    }

    @Test
    fun `parse skips comments and blank lines and rejects broken ones`() {
        val libs = Library.parseAll(listOf("# libs", "", "a:b:1 00", "  ", "c:d:2 11"))
        assertEquals(listOf("a:b:1", "c:d:2"), libs.map { "${it.group()}:${it.artifact()}:${it.version()}" })
        assertFailsWith<IllegalArgumentException> { Library.parse("a:b 00") }
        assertFailsWith<IllegalArgumentException> { Library.parse("a:b:1") }
    }

    @Test
    fun `missing libraries are downloaded to the maven layout and returned`() {
        val a = publish("x.y:a:1.0", "alpha")
        val b = publish("x.y:b:2.0", "beta")
        val paths = downloader().ensure(listOf(a, b))
        assertEquals(listOf(dir.resolve(a.path()), dir.resolve(b.path())), paths)
        assertEquals("alpha", String(paths[0].readBytes()))
        assertFalse(Files.list(dir.resolve("x/y/a/1.0")).use { s -> s.anyMatch { it.fileName.toString().endsWith(".part") } })
    }

    @Test
    fun `libraries that are already there and valid are not downloaded again`() {
        val a = publish("x.y:a:1.0", "alpha")
        downloader().ensure(listOf(a))
        downloader().ensure(listOf(a))
        assertEquals(1, hits.getValue(a.path()).get())
    }

    @Test
    fun `a corrupt file on disk is replaced`() {
        val a = publish("x.y:a:1.0", "alpha")
        val target = dir.resolve(a.path())
        Files.createDirectories(target.parent)
        target.writeBytes("broken".toByteArray())
        downloader().ensure(listOf(a))
        assertEquals("alpha", String(target.readBytes()))
    }

    @Test
    fun `a wrong hash is rejected and nothing is kept`() {
        val good = publish("x.y:a:1.0", "alpha")
        val bad = Library.parse("x.y:a:1.0 ${"0".repeat(64)}")
        files[bad.path()] = "tampered".toByteArray()
        assertEquals(good.path(), bad.path())
        val e = assertFailsWith<IOException> { downloader().ensure(listOf(bad)) }
        assertTrue("sha-256" in e.message!!.lowercase() || "hash" in e.message!!.lowercase(), e.message)
        assertFalse(dir.resolve(bad.path()).exists())
    }

    @Test
    fun `a server error is retried`() {
        val a = publish("x.y:a:1.0", "alpha")
        failFirst += a.path()
        downloader().ensure(listOf(a))
        assertEquals(2, hits.getValue(a.path()).get())
    }

    @Test
    fun `a missing library fails with its name`() {
        val ghost = Library.parse("x.y:ghost:1.0 ${"0".repeat(64)}")
        val e = assertFailsWith<IOException> { downloader().ensure(listOf(ghost)) }
        assertTrue("ghost" in e.message!!)
    }
}
