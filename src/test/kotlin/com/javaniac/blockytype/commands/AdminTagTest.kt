package com.javaniac.blockytype.commands

import com.javaniac.blockytype.themes.DefaultTheme
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AdminTagTest {
    private fun plain(c: net.kyori.adventure.text.Component) = PlainTextComponentSerializer.plainText().serialize(c)

    @Test
    fun `the tag text`() {
        assertEquals("[Server Admin]", AdminTag.TEXT)
    }

    @Test
    fun `the tab list name has the tag for admins only`() {
        assertEquals("[Server Admin] Alice", plain(AdminTag.displayName(DefaultTheme, "Alice", admin = true)))
        assertEquals("Alice", plain(AdminTag.displayName(DefaultTheme, "Alice", admin = false)))
    }

    @Test
    fun `the tag is drawn in the accent color`() {
        val c = AdminTag.displayName(DefaultTheme, "Alice", admin = true)
        val tag = c.children().firstOrNull { plain(it).contains("Server Admin") }
        assertNotNull(tag)
        assertEquals(DefaultTheme.mainColor, tag.color())
    }
}
