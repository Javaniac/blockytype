package com.javaniac.blockytype.game

/** The second selected on the result graph; unset until the player first scrolls. */
class ResultCursor {
    var index: Int? = null
        private set

    /** Moves by [dir] (-1 or +1), clamped to 0 until [size]. The first move selects the first (+) or last (-) second. */
    fun step(dir: Int, size: Int) {
        if (size <= 0) {
            index = null
            return
        }
        val current = index
        index = if (current == null) {
            if (dir > 0) 0 else size - 1
        } else {
            (current + dir).coerceIn(0, size - 1)
        }
    }

    fun reset() {
        index = null
    }
}
