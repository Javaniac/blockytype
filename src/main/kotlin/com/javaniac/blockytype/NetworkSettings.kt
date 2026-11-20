package com.javaniac.blockytype

/** One row of the admin panel's network section. */
data class NetworkField(
    val key: String,
    val label: String,
    val value: String,
    val source: Source,
    /** Saved in the panel but not yet running (needs a restart). */
    val pendingRestart: Boolean,
)

/** Validation and display of the four network settings (admin panel). Pure, so it is unit-tested. */
object NetworkSettings {
    val KEYS = listOf("server-ip", "server-port", "resource-pack-ip", "resource-pack-port")

    private val LABELS = mapOf(
        "server-ip" to "server ip",
        "server-port" to "server port",
        "resource-pack-ip" to "pack host",
        "resource-pack-port" to "pack port",
    )

    private const val MAX_HOST = 253

    /** The cleaned value, or null when [raw] is not valid for [key]. */
    fun validate(key: String, raw: String): String? {
        val v = raw.trim()
        return when (key) {
            "server-port", "resource-pack-port" -> v.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }
                ?.toIntOrNull()?.takeIf { it in 1..65535 }?.toString()
            "server-ip", "resource-pack-ip" -> v.takeIf {
                it.isNotEmpty() && it.length <= MAX_HOST && it.none { c -> c.isWhitespace() || c.isISOControl() }
            }
            else -> null
        }
    }

    /** The rows for the panel: [running] values, with valid saved [panel] (settings file) values that are not running yet. */
    fun fields(running: NetworkConfig, panel: Map<String, String>): List<NetworkField> = KEYS.map { key ->
        val (current, source) = when (key) {
            "server-ip" -> running.serverIp.let { it.value to it.source }
            "server-port" -> running.serverPort.let { it.value.toString() to it.source }
            "resource-pack-ip" -> running.resourcePackIp.let { it.value to it.source }
            else -> running.resourcePackPort.let { it.value.toString() to it.source }
        }
        val saved = panel[key]?.let { validate(key, it) }
        NetworkField(
            key = key,
            label = LABELS.getValue(key),
            value = saved ?: current,
            source = if (saved != null) Source.FILE else source,
            pendingRestart = saved != null && saved != current,
        )
    }
}

/** The network values of the settings file (they apply at the next start), cached in memory. */
object NetworkPanel {
    private val values = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Loads the stored settings, keeping only valid network keys and values. */
    fun load(stored: Map<String, String>) {
        values.clear()
        for ((key, value) in stored) {
            if (key in NetworkSettings.KEYS) NetworkSettings.validate(key, value)?.let { values[key] = it }
        }
    }

    fun snapshot(): Map<String, String> = HashMap(values)

    /** Remembers [value] for [key]. The caller stores it. */
    fun set(key: String, value: String) {
        require(key in NetworkSettings.KEYS) { "not a network setting: $key" }
        values[key] = requireNotNull(NetworkSettings.validate(key, value)) { "invalid value for $key" }
    }
}
