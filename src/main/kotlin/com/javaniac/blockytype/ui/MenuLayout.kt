package com.javaniac.blockytype.ui

/**
 * Geometry of menus. Slot 0 is the header, 1..[BODY_ROWS] the body rows, [FOOTER] the footer. Everything is in
 * GUI units from the screen center, so the server needs no knowledge of the window size. `text.vsh` places the element ids with the same constants (see `UiFont`).
 */
object MenuLayout {
    const val HEADER = 0
    const val BODY_ROWS = UiFont.MENU_ROWS
    const val FOOTER = BODY_ROWS + 1

    /** y of the top of a slot's text, relative to the screen center. */
    fun slotDy(slot: Int): Int = when (slot) {
        HEADER -> UiFont.MENU_HEADER_DY
        FOOTER -> UiFont.MENU_FOOTER_DY
        in 1..BODY_ROWS -> UiFont.MENU_ROW0_DY + UiFont.MENU_ROW_PITCH * (slot - 1)
        else -> throw IllegalArgumentException("menu slot out of range: $slot")
    }

    /** HUD element id of a slot. */
    fun elementId(slot: Int): Int = when (slot) {
        HEADER -> UiFont.MENU_HEADER_ID
        FOOTER -> UiFont.MENU_FOOTER_ID
        in 1..BODY_ROWS -> UiFont.MENU_ROW_FIRST_ID + slot - 1
        else -> throw IllegalArgumentException("menu slot out of range: $slot")
    }
}
