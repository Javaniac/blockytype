package com.javaniac.blockytype.profile

import java.awt.image.BufferedImage
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SkinFaceTest {
    private fun textures(url: String) =
        Base64.getEncoder().encodeToString("""{"timestamp":1,"profileId":"abc","textures":{"SKIN":{"url":"$url"}}}""".toByteArray())

    @Test
    fun `the skin url comes out of the textures value`() {
        assertEquals(
            "https://textures.minecraft.net/texture/abc123",
            SkinFace.skinUrl(textures("http://textures.minecraft.net/texture/abc123")),
        )
        assertEquals(
            "https://textures.minecraft.net/texture/abc123",
            SkinFace.skinUrl(textures("https://textures.minecraft.net/texture/abc123")),
        )
    }

    @Test
    fun `only Mojang's texture server is ever fetched`() {
        assertNull(SkinFace.skinUrl(textures("http://evil.example.com/texture/abc")))
        assertNull(SkinFace.skinUrl(textures("http://textures.minecraft.net.evil.com/texture/abc")))
        assertNull(SkinFace.skinUrl(textures("http://127.0.0.1/texture/abc")))
        assertNull(SkinFace.skinUrl(textures("file:///etc/passwd")))
        assertNull(SkinFace.skinUrl(textures("http://user@textures.minecraft.net@evil.com/x")))
        assertNull(SkinFace.skinUrl("not base64 !!"))
        assertNull(SkinFace.skinUrl(Base64.getEncoder().encodeToString("{}".toByteArray())))
    }

    @Test
    fun `the value is read from a session server profile`() {
        val json = """{"id":"abc","name":"Alice","properties":[{"name":"textures","value":"VALUE123","signature":"x"}]}"""
        assertEquals("VALUE123", SkinFace.textureValue(json))
        assertNull(SkinFace.textureValue("""{"id":"abc","properties":[]}"""))
        assertNull(SkinFace.textureValue("garbage"))
    }

    private fun skin(height: Int = 64, fill: (BufferedImage) -> Unit): BufferedImage {
        val img = BufferedImage(64, height, BufferedImage.TYPE_INT_ARGB)
        fill(img)
        return img
    }

    @Test
    fun `the face is the 8 by 8 square at 8,8`() {
        val img = skin {
            for (y in 0 until 8) for (x in 0 until 8) it.setRGB(8 + x, 8 + y, (0xFF shl 24) or ((x * 30) shl 16) or ((y * 30) shl 8) or 0x40)
        }
        val face = SkinFace.face(img)
        assertEquals(64, face.size)
        assertEquals(0x000040, face[0])
        assertEquals((30 shl 16) or 0x40, face[1])
        assertEquals((7 * 30 shl 16) or (7 * 30 shl 8) or 0x40, face[63])
    }

    @Test
    fun `the hat layer covers the face where it is not transparent`() {
        val img = skin {
            for (y in 0 until 8) for (x in 0 until 8) it.setRGB(8 + x, 8 + y, 0xFF102030.toInt())
            it.setRGB(40 + 2, 8 + 3, 0xFFFF0000.toInt()) // an opaque hat pixel
            it.setRGB(40 + 5, 8 + 5, 0x00FF0000) // fully transparent: the face shows
        }
        val face = SkinFace.face(img)
        assertEquals(0xFF0000, face[3 * 8 + 2])
        assertEquals(0x102030, face[5 * 8 + 5])
        assertEquals(0x102030, face[0])
    }

    @Test
    fun `old 64 by 32 skins work and tiny images fall back`() {
        val old = skin(32) { for (y in 0 until 8) for (x in 0 until 8) it.setRGB(8 + x, 8 + y, 0xFF00FF00.toInt()) }
        assertEquals(0x00FF00, SkinFace.face(old)[10])
        val tiny = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        assertContentEquals(SkinFace.PLACEHOLDER, SkinFace.face(tiny))
    }

    @Test
    fun `the placeholder is a full 8 by 8 head`() {
        assertEquals(64, SkinFace.PLACEHOLDER.size)
        assertNotNull(SkinFace.PLACEHOLDER.firstOrNull())
        for (c in SkinFace.PLACEHOLDER) assertEquals(0, c ushr 24, "colors are plain 0xRRGGBB")
    }

    @Test
    fun `png bytes are decoded to a face`() {
        val img = skin { for (y in 0 until 8) for (x in 0 until 8) it.setRGB(8 + x, 8 + y, 0xFF336699.toInt()) }
        val out = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "png", out)
        assertEquals(0x336699, SkinFace.fromPng(out.toByteArray())!![20])
        assertNull(SkinFace.fromPng(byteArrayOf(1, 2, 3)))
    }
}
