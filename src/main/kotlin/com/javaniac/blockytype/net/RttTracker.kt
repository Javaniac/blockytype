package com.javaniac.blockytype.net

/**
 * Rolling window of a player's round trip times (ms) from the keepalive answers. Thread safe: keepalive answers
 * are handled on the network thread, readers are on the tick thread.
 */
class RttTracker(private val window: Int = DEFAULT_WINDOW) {
    /** Numbers of the current window: [jitter] is the mean absolute deviation from the mean. */
    data class Snapshot(val count: Int, val median: Double, val min: Double, val max: Double, val jitter: Double)

    private val samples = ArrayDeque<Double>()

    @Synchronized
    fun add(ms: Double) {
        if (ms.isNaN() || ms < 0) return
        samples.addLast(ms)
        while (samples.size > window) samples.removeFirst()
    }

    @Synchronized
    fun count(): Int = samples.size

    @Synchronized
    fun reset() = samples.clear()

    @Synchronized
    fun snapshot(): Snapshot? {
        if (samples.isEmpty()) return null
        val sorted = samples.sorted()
        val n = sorted.size
        val median = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
        val mean = sorted.average()
        return Snapshot(n, median, sorted.first(), sorted.last(), sorted.sumOf { kotlin.math.abs(it - mean) } / n)
    }

    companion object {
        /** About 5 s of samples at the keepalive rate of [KeepAlive.DELAY_MS]. */
        const val DEFAULT_WINDOW = 20
    }
}

/** How often keepalives (and so RTT samples) are sent. Minestom's default is 10 s. */
object KeepAlive {
    const val DELAY_MS = 250L

    /** Must run before `MinecraftServer.init`: Minestom reads the property when its connection manager starts. */
    fun configure() {
        System.setProperty("minestom.keep-alive-delay", DELAY_MS.toString())
    }
}
