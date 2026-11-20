package com.javaniac.blockytype

import kotlinx.coroutines.suspendCancellableCoroutine
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

val mm = MiniMessage.miniMessage()

fun String.toComponent(): Component = mm.deserialize(this)

fun String.toBuilder(): StringBuilder = StringBuilder(this)

fun Component.toMiniMessage(): String = mm.serialize(this)

fun Audience.sendRichMessage(text: String) {
    this.sendMessage(text.toComponent())
}

val Int.ticks: Duration
    get() = (this * 50).milliseconds.toJavaDuration()

fun Int.toColor(): TextColor = TextColor.color(this)

/**
 * Runs a block of code on the main thread, and returns the result.
 * @see [withMinestomMain]
 */
suspend fun <T> withMinestomMainResult(block: () -> T): T = suspendCancellableCoroutine {
    MinecraftServer.getSchedulerManager().scheduleNextTick {
        try {
            val res = block()
            it.resume(res)
        } catch (t: Throwable) {
            it.resumeWithException(t)
        }
    }
}

/**
 * Runs a block of code on the main thread, without returning a result.
 * @see [withMinestomMainResult]
 */
suspend fun withMinestomMain(block: () -> Unit): Unit = suspendCancellableCoroutine {
    MinecraftServer.getSchedulerManager().scheduleNextTick {
        try {
            block()
            it.resume(Unit)
        } catch (t: Throwable) {
            it.resumeWithException(t)
        }
    }
}

fun TextColor.rawHex() = this.asHexString().removePrefix("#")