package com.javaniac.blockytype

import net.minestom.server.MinecraftServer
import kotlin.concurrent.thread

/** Minestom has no stdin console: this reads lines from stdin and runs them as console commands. */
object Console {
    fun start() {
        thread(name = "console", isDaemon = true) {
            val manager = MinecraftServer.getCommandManager()
            val input = System.`in`.bufferedReader()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isBlank()) continue
                manager.execute(manager.consoleSender, line.trim().removePrefix("/"))
            }
        }
    }
}
