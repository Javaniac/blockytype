package com.javaniac.blockytype.game

import kotlin.math.roundToLong

/**
 * Repairs key times that a lag spike distorted. A hiccup on the connection holds back several keys and releases
 * them at once: they arrive a few ms apart after a long gap, which no human can type. Such a run is spread evenly
 * between the key before it and its last key. The first key, the last key of the run, the key count, the order and
 * so the test duration, wpm and accuracy stay as they were; only raw, per-second speed and bursts change.
 */
object KeyTimes {
    /** Keys closer than this to the previous one belong to a release burst. */
    const val BURST_GAP_MS = 8L

    /** A burst needs at least this many keys (including the first one after the gap). */
    const val MIN_RUN = 3

    /** The gap before a burst's first key must be at least this long, or it is just fast typing. */
    const val PRECEDING_GAP_MS = 40L

    fun deburst(keys: List<KeyPress>): List<KeyPress> {
        if (keys.size < MIN_RUN + 1) return keys
        val out = keys.toMutableList()
        var s = 1
        while (s < keys.size) {
            if (keys[s].ms - keys[s - 1].ms < PRECEDING_GAP_MS) {
                s++
                continue
            }
            var e = s
            while (e + 1 < keys.size && keys[e + 1].ms - keys[e].ms < BURST_GAP_MS) e++
            val n = e - s + 1
            if (n >= MIN_RUN) {
                val from = keys[s - 1].ms
                val step = (keys[e].ms - from).toDouble() / n
                for (k in s until e) out[k] = keys[k].copy(ms = from + (step * (k - s + 1)).roundToLong())
            }
            s = e + 1
        }
        return out
    }
}
