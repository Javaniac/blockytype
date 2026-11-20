package com.javaniac.blockytype.pack

import com.javaniac.blockytype.ui.UiFont
import kotlin.test.Test
import kotlin.test.assertTrue

class TBlockPackTest {
    private val files = PackBuilder.build()
    private val json = String(files.getValue("assets/blockytype/font/ui.json"), Charsets.UTF_8).replace(" ", "")

    @Test
    fun `pack has both block atlases`() {
        for (f in listOf("block.png", "block_tall.png")) {
            val png = files["assets/blockytype/textures/font/$f"]
            assertTrue(png != null && png.size > 8, "$f missing")
        }
    }

    @Test
    fun `ui font maps each block size to a glyph with the right height and ascent`() {
        val normal = json.substringAfter("blockytype:font/block.png").substringBefore("}")
        assertTrue("\"height\":${UiFont.BLOCK_HEIGHT}" in normal && "\"ascent\":${UiFont.BLOCK_ASCENT}" in normal, normal)
        val tall = json.substringAfter("blockytype:font/block_tall.png").substringBefore("}")
        assertTrue("\"height\":${UiFont.TALL_HEIGHT}" in tall && "\"ascent\":${UiFont.TALL_ASCENT}" in tall, tall)
    }

    @Test
    fun `the cursor texture is gone`() {
        assertTrue(files["assets/blockytype/textures/font/cursor.png"] == null)
        assertTrue("cursor.png" !in json)
    }
}
