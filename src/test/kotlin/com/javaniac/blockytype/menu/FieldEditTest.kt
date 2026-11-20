package com.javaniac.blockytype.menu

import com.javaniac.blockytype.NetworkPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FieldEditTest {
    @Test
    fun `typing replaces the draft and clears the error`() {
        val e = FieldEdit("server-port", "25565")
        assertEquals("25565", e.draft)
        assertNull(e.commit("abc"))
        assertNotNull(e.error)
        e.type("25570")
        assertEquals("25570", e.draft)
        assertNull(e.error)
    }

    @Test
    fun `commit validates the typed text for the key`() {
        assertEquals("25570", FieldEdit("server-port", "1").commit(" 25570 "))
        assertEquals("10.0.0.5", FieldEdit("server-ip", "x").commit("10.0.0.5"))
        assertNull(FieldEdit("server-port", "1").commit("70000"))
        assertNull(FieldEdit("server-ip", "x").commit("a b"))
    }

    @Test
    fun `a failed commit keeps the draft the player typed`() {
        val e = FieldEdit("server-port", "1")
        e.commit("999999")
        assertEquals("999999", e.draft)
    }

    @Test
    fun `the draft is cut to a sane length`() {
        val e = FieldEdit("server-ip", "x")
        e.type("a".repeat(1000))
        assertEquals(FieldEdit.MAX_DRAFT, e.draft.length)
    }

    @Test
    fun `only network keys can be edited`() {
        assertFailsWith<IllegalArgumentException> { FieldEdit("font", "x") }
    }
}

class NetworkPanelTest {
    @Test
    fun `load keeps only valid network keys and values`() {
        NetworkPanel.load(mapOf("server-port" to "25570", "server-ip" to "bad ip", "font" to "x", "resource-pack-ip" to "example.com"))
        assertEquals(mapOf("server-port" to "25570", "resource-pack-ip" to "example.com"), NetworkPanel.snapshot())
    }

    @Test
    fun `set validates`() {
        NetworkPanel.load(emptyMap())
        NetworkPanel.set("server-port", "25571")
        assertEquals("25571", NetworkPanel.snapshot()["server-port"])
        assertFailsWith<IllegalArgumentException> { NetworkPanel.set("server-port", "0") }
        assertFailsWith<IllegalArgumentException> { NetworkPanel.set("nope", "1") }
    }
}
