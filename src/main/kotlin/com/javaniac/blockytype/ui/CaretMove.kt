package com.javaniac.blockytype.ui

/** How the caret reached its current column; drives the slide animation in `text.vsh`. */
enum class CaretMove {
    NONE, FROM_LEFT, FROM_RIGHT;

    companion object {
        /** Slide only for a one column step on the same line; everything else (wrap, jump, first render) snaps. */
        fun between(old: Pair<Int, Int>?, new: Pair<Int, Int>): CaretMove {
            if (old == null || old.first < 0 || new.first < 0 || old.first != new.first) return NONE
            return when (new.second - old.second) {
                1 -> FROM_LEFT
                -1 -> FROM_RIGHT
                else -> NONE
            }
        }
    }
}
