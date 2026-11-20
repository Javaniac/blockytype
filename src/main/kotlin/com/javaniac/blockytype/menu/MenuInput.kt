package com.javaniac.blockytype.menu

/** What a drop key press does (Q = settings, Ctrl+Q = admin panel). */
enum class DropAction { CLOSE, SETTINGS, ADMIN, NONE }

/** Small conversions from raw client input to menu input. */
object MenuInput {
    private const val HOTBAR = 9

    /** +1 / -1 / 0 from a hotbar slot change (scroll wheel or number key), taking the short way round. */
    fun scrollDirection(old: Int, new: Int): Int {
        var d = (new - old).mod(HOTBAR)
        if (d > HOTBAR / 2) d -= HOTBAR
        return d.coerceIn(-1, 1)
    }

    /** [ctrl]: the client sent DROP_ITEM_STACK (Ctrl+Q). Admin rights are checked here, never trusted from the client. */
    fun dropAction(ctrl: Boolean, menuOpen: Boolean, isAdmin: Boolean): DropAction = when {
        menuOpen -> DropAction.CLOSE
        !ctrl -> DropAction.SETTINGS
        isAdmin -> DropAction.ADMIN
        else -> DropAction.NONE
    }

    /** One navigation step of a menu. */
    fun navPresses(prev: InputKeys, now: InputKeys): List<Nav> = buildList {
        if (now.forward && !prev.forward) add(Nav.UP)
        if (now.backward && !prev.backward) add(Nav.DOWN)
        if (now.left && !prev.left) add(Nav.LEFT)
        if (now.right && !prev.right) add(Nav.RIGHT)
        if (now.jump && !prev.jump) add(Nav.SELECT)
    }
}

/** The movement keys of the client's input packet that menus use. */
data class InputKeys(
    val forward: Boolean = false,
    val backward: Boolean = false,
    val left: Boolean = false,
    val right: Boolean = false,
    val jump: Boolean = false,
)

/** W, S, A, D and space. */
enum class Nav { UP, DOWN, LEFT, RIGHT, SELECT }

/**
 * Turns the client's key states into navigation steps: one step per key press, and while a direction key stays held
 * it repeats every [intervalNanos] after [delayNanos]. The newest held direction wins. Space never repeats.
 * The client only sends its key state when it changes, so [update] must also be called on a timer.
 */
class KeyRepeater(
    private val delayNanos: Long = DEFAULT_DELAY_NANOS,
    private val intervalNanos: Long = DEFAULT_INTERVAL_NANOS,
) {
    private var keys = InputKeys()
    private var held: Nav? = null
    private var nextAt = 0L

    /** The steps due at [now] given the current [keys]. */
    fun update(keys: InputKeys, now: Long): List<Nav> {
        val presses = MenuInput.navPresses(this.keys, keys)
        this.keys = keys
        presses.lastOrNull { it != Nav.SELECT }?.let {
            held = it
            nextAt = now + delayNanos
        }
        val h = held
        if (h != null && !isDown(h, keys)) held = null
        if (presses.isNotEmpty()) return presses
        if (h != null && held != null && now >= nextAt) {
            nextAt = now + intervalNanos
            return listOf(h)
        }
        return emptyList()
    }

    /** Forgets the held keys (a menu closed or opened). */
    fun reset() {
        keys = InputKeys()
        held = null
    }

    private fun isDown(nav: Nav, k: InputKeys) = when (nav) {
        Nav.UP -> k.forward
        Nav.DOWN -> k.backward
        Nav.LEFT -> k.left
        Nav.RIGHT -> k.right
        Nav.SELECT -> k.jump
    }

    companion object {
        /** Semi fast: after a short pause about ten steps a second. */
        const val DEFAULT_DELAY_NANOS = 350_000_000L
        const val DEFAULT_INTERVAL_NANOS = 100_000_000L
    }
}
