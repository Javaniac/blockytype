package com.javaniac.blockytype.ui

/**
 * Remembers the last caret position and how it moved, so the HUD can flag a one column step for the shader.
 * The flag only lives [flagNanos] (longer than the slide, far shorter than the 20 minute game-time wrap), so
 * a later re-render does not replay the slide.
 */
class CaretTracker(private val flagNanos: Long = DEFAULT_FLAG_NANOS) {
    private var last: Pair<Int, Int> = START
    private var move = CaretMove.NONE
    private var movedAt = 0L

    /** Back to the start of a fresh test. */
    fun reset() {
        last = START
        move = CaretMove.NONE
    }

    /** Records the caret after an accepted edit. Returns the move, [CaretMove.NONE] when nothing slides. */
    fun update(caret: Pair<Int, Int>, now: Long): CaretMove {
        if (caret == last) return CaretMove.NONE
        move = CaretMove.between(last, caret)
        movedAt = now
        last = caret
        return move
    }

    /** The move to flag in a title rendered at [now]. */
    fun current(now: Long): CaretMove = if (move != CaretMove.NONE && now - movedAt < flagNanos) move else CaretMove.NONE

    companion object {
        /** Line 0, column 0: where every test starts. */
        val START = 0 to 0
        const val DEFAULT_FLAG_NANOS = 700_000_000L
    }
}
