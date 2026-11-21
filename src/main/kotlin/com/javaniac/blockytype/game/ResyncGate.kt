package com.javaniac.blockytype.game

/**
 * Guards the typing box after the server rewrote it ([TestSession.prefill]). The client keeps typing on its old
 * text until the rewrite reaches it, so edits already in flight were made on text the server never accepted.
 * Judging them would reject each one and rewrite the box again and again; they are dropped instead until the
 * client echoes the rewritten text (or [TIMEOUT_NANOS] pass, in case no echo ever comes).
 */
class ResyncGate {
    enum class Verdict { PASS, DROP, ECHO }

    private var expected: String? = null
    private var since = 0L

    val waiting: Boolean get() = expected != null

    /** The box was rewritten to [text] at [nowNanos]. */
    fun expect(text: String, nowNanos: Long) {
        expected = text
        since = nowNanos
    }

    fun reset() {
        expected = null
    }

    /** What to do with the box content [text] that arrived at [nowNanos]. */
    fun check(text: String, nowNanos: Long): Verdict {
        val want = expected ?: return Verdict.PASS
        if (nowNanos - since > TIMEOUT_NANOS) {
            expected = null
            return Verdict.PASS
        }
        if (text == want) {
            expected = null
            return Verdict.ECHO
        }
        // One key on top of the rewritten text (append, or delete its last char): the client already has it.
        val oneKey = (text.length == want.length + 1 && text.startsWith(want)) ||
            (text.length == want.length - 1 && want.startsWith(text))
        if (oneKey) {
            expected = null
            return Verdict.PASS
        }
        return Verdict.DROP
    }

    companion object {
        const val TIMEOUT_NANOS = 1_000_000_000L
    }
}
