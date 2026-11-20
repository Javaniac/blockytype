package com.javaniac.blockytype.pack

/**
 * Tiny JSON writer matching Python's `json.dumps(obj, indent=2)` (ASCII-escaped, 2-space indent),
 * so generated files stay stable and private-use font characters survive as `\uXXXX`.
 * Supports Map, List, String, Number and Boolean.
 */
object Json {
    fun write(value: Any?): String = StringBuilder().also { append(it, value, 0) }.append('\n').toString()

    private fun append(sb: StringBuilder, value: Any?, level: Int) {
        when (value) {
            null -> sb.append("null")
            is String -> string(sb, value)
            is Boolean -> sb.append(value)
            is Number -> sb.append(value)
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    sb.append("{}")
                    return
                }
                sb.append("{\n")
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(",\n")
                    first = false
                    indent(sb, level + 1)
                    string(sb, k.toString())
                    sb.append(": ")
                    append(sb, v, level + 1)
                }
                sb.append('\n')
                indent(sb, level)
                sb.append('}')
            }
            is List<*> -> {
                if (value.isEmpty()) {
                    sb.append("[]")
                    return
                }
                sb.append("[\n")
                value.forEachIndexed { i, v ->
                    if (i > 0) sb.append(",\n")
                    indent(sb, level + 1)
                    append(sb, v, level + 1)
                }
                sb.append('\n')
                indent(sb, level)
                sb.append(']')
            }
            else -> error("Unsupported JSON value: ${value::class}")
        }
    }

    private fun indent(sb: StringBuilder, level: Int) {
        repeat(level) { sb.append("  ") }
    }

    private fun string(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 || c.code > 0x7E -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }
}
