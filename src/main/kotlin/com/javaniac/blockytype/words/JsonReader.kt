package com.javaniac.blockytype.words

/**
 * Minimal JSON reader (the pack's [com.javaniac.blockytype.pack.Json] only writes). Returns Map, List, String,
 * Long, Double, Boolean or null. Throws [IllegalArgumentException] on malformed input.
 */
object JsonReader {
    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value()
        p.skipWs()
        require(p.pos == text.length) { "trailing data at ${p.pos}" }
        return v
    }

    private class Parser(val s: String) {
        var pos = 0

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(): Any? {
            skipWs()
            require(pos < s.length) { "unexpected end" }
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("unexpected '$c' at $pos")
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            require(s.startsWith(word, pos)) { "bad literal at $pos" }
            pos += word.length
            return v
        }

        private fun num(): Any {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            val t = s.substring(start, pos)
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: throw IllegalArgumentException("bad number '$t'")
        }

        private fun str(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                require(pos < s.length) { "unterminated string" }
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        require(pos < s.length) { "unterminated escape" }
                        when (val e = s[pos++]) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000c')
                            'u' -> {
                                require(pos + 4 <= s.length) { "bad unicode escape" }
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> sb.append(e) // " \ /
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun arr(): List<Any?> {
            pos++
            val out = ArrayList<Any?>()
            skipWs()
            if (pos < s.length && s[pos] == ']') {
                pos++
                return out
            }
            while (true) {
                out += value()
                skipWs()
                require(pos < s.length) { "unterminated array" }
                when (s[pos++]) {
                    ',' -> {}
                    ']' -> return out
                    else -> throw IllegalArgumentException("expected , or ] at ${pos - 1}")
                }
            }
        }

        private fun obj(): Map<String, Any?> {
            pos++
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (pos < s.length && s[pos] == '}') {
                pos++
                return out
            }
            while (true) {
                skipWs()
                require(pos < s.length && s[pos] == '"') { "expected key at $pos" }
                val k = str()
                skipWs()
                require(pos < s.length && s[pos++] == ':') { "expected : at ${pos - 1}" }
                out[k] = value()
                skipWs()
                require(pos < s.length) { "unterminated object" }
                when (s[pos++]) {
                    ',' -> {}
                    '}' -> return out
                    else -> throw IllegalArgumentException("expected , or } at ${pos - 1}")
                }
            }
        }
    }
}
