package com.javaniac.blockytype.pack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadersTest {
    private fun fsh(v: ClientVersion): String {
        val files = Shaders.files(v)
        val key = files.keys.singleOrNull { it.endsWith("core/text.fsh") }
        assertTrue(key != null, "text.fsh override missing for ${v.label}")
        return files.getValue(key)
    }

    @Test
    fun `text fsh override exists for every client version`() {
        for (v in ClientVersion.entries) fsh(v)
    }

    @Test
    fun `text fsh filters marked HUD glyphs by hand because the atlas sampler is NEAREST`() {
        for (v in ClientVersion.entries) {
            val src = fsh(v)
            assertTrue("texelFetch" in src, "${v.label}: no manual filtering")
            assertTrue("dFdx" in src && "dFdy" in src, "${v.label}: footprint must come from derivatives")
            // vanilla behaviour stays for everything that is not a HUD glyph
            assertTrue("discard" in src, "${v.label}: vanilla alpha discard lost")
        }
    }

    @Test
    fun `26_3 text fsh uses layout locations that match the vertex shader outputs`() {
        val vsh = Shaders.files(ClientVersion.V26_3).entries.single { it.key.endsWith("core/text.vsh") }.value
        val src = fsh(ClientVersion.V26_3)
        for (decl in listOf("vec4 vertexColor", "vec2 texCoord0")) {
            val vshLoc = Regex("layout\\(location = (\\d+)\\) out $decl").find(vsh)?.groupValues?.get(1)
            val fshLoc = Regex("layout\\(location = (\\d+)\\) in $decl").find(src)?.groupValues?.get(1)
            assertEquals(vshLoc, fshLoc, "location of $decl")
        }
    }

    @Test
    fun `26_2 text fsh has no layout qualifiers`() {
        assertTrue("layout(location" !in fsh(ClientVersion.V26_2))
    }
}

class CaretShaderTest {
    private fun vsh(v: ClientVersion) =
        Shaders.files(v).entries.single { it.key.endsWith("core/text.vsh") }.value

    @Test
    fun `text vsh animates flagged carets from GameTime on every client version`() {
        for (v in ClientVersion.entries) {
            val src = vsh(v)
            assertTrue("GameTime" in src, "${v.label}: GameTime unused")
            assertTrue("globals.glsl" in src, "${v.label}: globals not imported")
            assertTrue("hudCaretOffset" in src, "${v.label}: caret slide missing")
        }
    }
}

class FilterRulesTest {
    private fun fsh(v: ClientVersion) =
        Shaders.files(v).entries.single { it.key.endsWith("core/text.fsh") }.value

    @Test
    fun `glyphs magnified by more than one texel per pixel keep nearest sampling`() {
        // The stretched background square, the caret and other bitmaps must stay crisp; only minified
        // (footprint >= 1 texel) font glyphs are area filtered.
        for (v in ClientVersion.entries) {
            val src = fsh(v)
            assertTrue("MIN_FILTER_FOOTPRINT" in src, "${v.label}: no minimum footprint rule")
            assertTrue("maxFootprint" in src, "${v.label}: footprint is not compared")
        }
    }
}
