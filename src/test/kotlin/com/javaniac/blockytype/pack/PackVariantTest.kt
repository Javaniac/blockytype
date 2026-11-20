package com.javaniac.blockytype.pack

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PackVariantTest {
    private val base = PackVariant("jetbrains-mono", antiAlias = true, showInput = true)

    private fun fsh(files: Map<String, ByteArray>, v: ClientVersion): String {
        val path = files.keys.single { it.startsWith(v.assetsDir) && it.endsWith("core/text.fsh") }
        return String(files.getValue(path), Charsets.UTF_8)
    }

    @Test
    fun `anti aliasing off disables the box filter on every client version`() {
        val on = PackBuilder.build(base)
        val off = PackBuilder.build(base.copy(antiAlias = false))
        for (v in ClientVersion.entries) {
            assertTrue("const float MIN_FILTER_FOOTPRINT = 0.9;" in fsh(on, v), v.label)
            assertTrue("const float MIN_FILTER_FOOTPRINT = 1000000.0;" in fsh(off, v), v.label)
        }
    }

    @Test
    fun `variants build different packs`() {
        val zips = listOf(
            base, base.copy(antiAlias = false), base.copy(showInput = false),
        ).map { PackBuilder.zip(PackBuilder.build(it)) }
        assertEquals(3, zips.map { it.toList() }.toSet().size)
    }

    @Test
    fun `the same variant builds the same bytes`() {
        assertContentEquals(PackBuilder.zip(PackBuilder.build(base)), PackBuilder.zip(PackBuilder.build(base)))
    }

    @Test
    fun `an unknown font falls back to the bundled one`() {
        val broken = PackBuilder.zip(PackBuilder.build(base.copy(font = "no-such-font")))
        assertContentEquals(PackBuilder.zip(PackBuilder.build(base)), broken)
    }

    @Test
    fun `variant comes from effective setting values`() {
        val v = PackVariant.of(font = "fira-code", antiAlias = "off", inputBox = "hide")
        assertEquals(PackVariant("fira-code", antiAlias = false, showInput = false), v)
        assertEquals(base, PackVariant.of("jetbrains-mono", "on", "show"))
        assertNotEquals(base, PackVariant.of("jetbrains-mono", "off", "show"))
        assertFalse(PackVariant.of("x", "off", "show").antiAlias)
    }
}
