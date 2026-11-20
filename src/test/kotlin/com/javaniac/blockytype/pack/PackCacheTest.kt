package com.javaniac.blockytype.pack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PackCacheTest {
    private val a = PackVariant("jetbrains-mono", antiAlias = true, showInput = true)
    private val b = a.copy(antiAlias = false)
    private val c = a.copy(showInput = false)

    private class Counter {
        var builds = 0
        val build: (PackVariant) -> ByteArray = { v -> builds++; "pack:${v.font}:${v.antiAlias}:${v.showInput}".toByteArray() }
    }

    @Test
    fun `a variant is built once`() {
        val n = Counter()
        val cache = PackCache(n.build)
        val first = cache.get(a)
        assertSame(first, cache.get(a))
        assertEquals(1, n.builds)
    }

    @Test
    fun `different variants get different packs and ids`() {
        val cache = PackCache(Counter().build)
        val x = cache.get(a)
        val y = cache.get(b)
        assertNotEquals(x.sha1, y.sha1)
        assertNotEquals(x.id, y.id)
    }

    @Test
    fun `packs are found by their hash`() {
        val cache = PackCache(Counter().build)
        val x = cache.get(a)
        assertSame(x, cache.byHash(x.sha1))
        assertNull(cache.byHash("0".repeat(40)))
    }

    @Test
    fun `the cache is bounded but keeps recently used variants`() {
        val n = Counter()
        val cache = PackCache(n.build, max = 2)
        cache.get(a); cache.get(b)
        cache.get(a) // a is now the most recent
        cache.get(c) // evicts b
        assertEquals(3, n.builds)
        cache.get(a)
        assertEquals(3, n.builds, "a stayed")
        cache.get(b)
        assertEquals(4, n.builds, "b was rebuilt")
    }

    @Test
    fun `clear forces a rebuild`() {
        val n = Counter()
        val cache = PackCache(n.build)
        cache.get(a)
        cache.clear()
        cache.get(a)
        assertEquals(2, n.builds)
    }

    @Test
    fun `sha1 is 40 hex chars`() {
        val x = PackCache(Counter().build).get(a)
        assertTrue(Regex("[0-9a-f]{40}").matches(x.sha1))
    }
}
