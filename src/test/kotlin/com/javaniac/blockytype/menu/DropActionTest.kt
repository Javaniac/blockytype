package com.javaniac.blockytype.menu

import kotlin.test.Test
import kotlin.test.assertEquals

class DropActionTest {
    @Test
    fun `Q closes an open menu, Ctrl+Q too`() {
        assertEquals(DropAction.CLOSE, MenuInput.dropAction(ctrl = false, menuOpen = true, isAdmin = false))
        assertEquals(DropAction.CLOSE, MenuInput.dropAction(ctrl = true, menuOpen = true, isAdmin = true))
    }

    @Test
    fun `Q opens the settings`() {
        assertEquals(DropAction.SETTINGS,MenuInput.dropAction(ctrl = false, menuOpen = false, isAdmin = true))
    }

    @Test
    fun `Ctrl+Q opens the admin panel for admins only`() {
        assertEquals(DropAction.ADMIN, MenuInput.dropAction(ctrl = true, menuOpen = false, isAdmin = true))
        assertEquals(DropAction.NONE, MenuInput.dropAction(ctrl = true, menuOpen = false, isAdmin = false))
    }
}
