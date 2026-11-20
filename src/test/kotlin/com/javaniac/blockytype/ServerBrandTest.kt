package com.javaniac.blockytype

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerBrandTest {
    @Test
    fun `the brand name and list entry carry the project name`() {
        assertEquals("BlockyType", ServerBrand.NAME)
        assertTrue("BlockyType" in PlainTextComponentSerializer.plainText().serialize(ServerBrand.motd()))
    }

    @Test
    fun `the server list icon is a 64 by 64 png`() {
        val img = ImageIO.read(ByteArrayInputStream(ServerBrand.icon()))
        assertEquals(64, img.width)
        assertEquals(64, img.height)
    }
}
