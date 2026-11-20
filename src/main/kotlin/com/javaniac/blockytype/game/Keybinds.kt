package com.javaniac.blockytype.game

import com.javaniac.blockytype.ui.Role
import com.javaniac.blockytype.ui.Run
import net.kyori.adventure.text.Component
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.component.DataComponents
import net.minestom.server.item.component.TooltipDisplay

/** How the player's client names the keys BlockyType uses (read with [KeybindProbe]). */
data class Keybinds(
    val menu: String = "Q",
    val restart: String = "F",
    val use: String = "right click",
    val left: String = "A",
    val right: String = "D",
) {
    /** Dropping a whole stack (the drop key with Ctrl held) opens the admin panel. */
    val admin: String get() = "Ctrl+$menu"

    /** The hint that starts a test: the key that opens the typing box. */
    fun typeHint(): List<Run> = listOf(Run(use.replace(Regex("^(Left|Right|Middle) Button$"), "$1-Mouse"), Role.MAIN), Run(" to type", Role.SUB))

    /** Hint lines for the side of the typing screen. */
    fun side(isAdmin: Boolean): List<List<Run>> = buildList {
        add(listOf(Run(menu, Role.MAIN), Run(" settings", Role.SUB)))
        if (isAdmin) add(listOf(Run(admin, Role.MAIN), Run(" admin", Role.SUB)))
    }

    companion object {
        val DEFAULT = Keybinds()

        /** Between the names in the probe item. */
        const val SEP = "~"

        /** Between the hints of a footer. */
        const val DOT = " · "

        /** The hints in one row, separated by a centered dot. */
        fun join(hints: List<List<Run>>): List<Run> =
            hints.flatMapIndexed { i, h -> if (i == 0) h else listOf(Run(DOT, Role.SUB)) + h }

        private const val MAX_NAME = 24

        /**
         * The keys from the text of the rename box (the client's resolved [KeybindProbe.name]), or null when it
         * is not the expected shape. The text comes from the client, so it is validated hard: it ends up on a HUD.
         */
        fun parse(raw: String): Keybinds? {
            val parts = raw.split(SEP)
            if (parts.size != KeybindProbe.KEYS.size) return null
            val names = parts.map { it.trim() }
            for (n in names) {
                if (n.isEmpty() || n.length > MAX_NAME) return null
                if (n.startsWith("key.")) return null // the client did not resolve it
                if (n.any { it.isISOControl() || it in ''..'' }) return null
            }
            return Keybinds(menu = names[0], restart = names[1], use = names[2], left = names[3], right = names[4])
        }
    }
}

/**
 * Reads the player's keybinds: an anvil with an item named by keybind components is opened for a moment. The
 * client resolves the components with its own bindings and fills its rename box with the result, then sends it
 * back (rename packet).
 */
object KeybindProbe {
    /** Options keys of the bindings, in the order of the probe text. */
    val KEYS = listOf("key.drop", "key.swapOffhand", "key.use", "key.left", "key.right")

    fun name(): Component {
        val b = Component.text()
        KEYS.forEachIndexed { i, key ->
            if (i > 0) b.append(Component.text(Keybinds.SEP))
            b.append(Component.keybind(key))
        }
        return b.build()
    }

    /** The item has an empty model and no tooltip: the pack hides the rest of the anvil screen. */
    fun item(): ItemStack = ItemStack.builder(Material.PAPER)
        .set(DataComponents.CUSTOM_NAME, name())
        .set(DataComponents.ITEM_MODEL, "blockytype:blank")
        .set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay(true, emptySet()))
        .build()
}
