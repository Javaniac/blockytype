package com.javaniac.blockytype.menu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LookupEditTest {
    @Test
    fun `a lookup edit takes Minecraft names`() {
        val e = FieldEdit.lookup()
        assertTrue(e.isLookup)
        assertFalse(FieldEdit("server-ip", "x").isLookup)
        assertEquals("Alice_1", e.commit(" Alice_1 "))
        assertNull(e.error)
    }

    @Test
    fun `other text is not a name`() {
        val e = FieldEdit.lookup()
        for (bad in listOf("", "ab", "a b", "x".repeat(17), "../x", "名前abc")) {
            assertNull(e.commit(bad), bad)
            assertEquals("not a valid name", e.error, bad)
        }
    }

    @Test
    fun `fail sets the error and typing clears it`() {
        val e = FieldEdit.lookup()
        e.fail("never joined")
        assertEquals("never joined", e.error)
        e.type("Bob")
        assertNull(e.error)
        assertEquals("Bob", e.draft)
    }

    @Test
    fun `the draft starts empty and is cut`() {
        val e = FieldEdit.lookup()
        assertEquals("", e.draft)
        e.type("a".repeat(500))
        assertEquals(FieldEdit.MAX_DRAFT, e.draft.length)
    }
}
