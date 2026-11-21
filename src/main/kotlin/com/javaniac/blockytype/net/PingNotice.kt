package com.javaniac.blockytype.net

import com.javaniac.blockytype.themes.AbstractTheme
import net.kyori.adventure.text.Component
import kotlin.math.roundToInt

/** The chat warning for players with a high ping, sent shortly after they join. */
object PingNotice {
    /** A median round trip above this many ms is "high". */
    const val HIGH_PING_MS = 100.0

    /** Seconds after the join: the keepalive window (see [KeepAlive]) holds a dozen or more samples by then. */
    const val DELAY_SECONDS = 6L

    /** Fewer samples than this say nothing about the connection. */
    const val MIN_SAMPLES = 8

    /** The median, not the mean or the max, so one spike on a good connection does not warn. */
    fun warns(snapshot: RttTracker.Snapshot?): Boolean =
        snapshot != null && snapshot.count >= MIN_SAMPLES && snapshot.median > HIGH_PING_MS

    fun message(theme: AbstractTheme, ms: Double): Component = Component.text()
        .append(Component.text("» ", theme.caretColor))
        .append(Component.text("High ping (", theme.subColor))
        .append(Component.text("${ms.roundToInt()} ms", theme.errorColor))
        .append(Component.text("): ", theme.subColor))
        .append(Component.text("your experience may be worse.", theme.textColor))
        .build()
}
