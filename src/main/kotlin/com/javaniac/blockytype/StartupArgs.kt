package com.javaniac.blockytype

/** Where an effective setting value came from. The settings file wins over defaults. */
enum class Source(val label: String) {
    FILE("file"),
    DEFAULT("default"),
}

data class Resolved<T>(val value: T, val source: Source)

/** [file] beats [default]. */
fun <T> resolve(file: T?, default: T): Resolved<T> =
    if (file != null) Resolved(file, Source.FILE) else Resolved(default, Source.DEFAULT)

/** Effective network settings (game server and resource pack host). */
data class NetworkConfig(
    val serverIp: Resolved<String>,
    val serverPort: Resolved<Int>,
    val resourcePackIp: Resolved<String>,
    val resourcePackPort: Resolved<Int>,
)

/** Network defaults and the startup banner. The server takes no command line arguments: everything is in `blockytype.properties`. */
object StartupArgs {
    const val DEFAULT_SERVER_IP = "0.0.0.0"
    const val DEFAULT_SERVER_PORT = 25565
    const val DEFAULT_RESOURCE_PACK_IP = "localhost"
    const val DEFAULT_RESOURCE_PACK_PORT = 25500

    /** Startup message: effective values with their source. */
    fun banner(config: NetworkConfig): String = buildString {
        appendLine("Network configuration (source in brackets, change it in blockytype.properties):")
        appendLine("  game server    ${config.serverIp.value}:${config.serverPort.value}  [${config.serverIp.source.label} / ${config.serverPort.source.label}]")
        append("  resource pack  ${config.resourcePackIp.value}:${config.resourcePackPort.value}  [${config.resourcePackIp.source.label} / ${config.resourcePackPort.source.label}]")
    }
}
