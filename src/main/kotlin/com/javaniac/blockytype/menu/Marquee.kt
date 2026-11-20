package com.javaniac.blockytype.menu

/** Scrolls a text that is wider than its window: a pause, then one char per step to the end, a pause, again. */
object Marquee {
    /** Semi fast: about seven chars a second. */
    const val STEP_NANOS = 140_000_000L

    /** Steps the text rests at the start and at the end. */
    const val HOLD_STEPS = 8

    /** First char shown, [elapsedNanos] after the text appeared. */
    fun offset(length: Int, window: Int, elapsedNanos: Long): Int {
        val span = length - window
        if (span <= 0) return 0
        val steps = (elapsedNanos / STEP_NANOS).coerceAtLeast(0)
        val cycle = span + 2L * HOLD_STEPS
        return ((steps % cycle) - HOLD_STEPS).coerceIn(0L, span.toLong()).toInt()
    }
}
