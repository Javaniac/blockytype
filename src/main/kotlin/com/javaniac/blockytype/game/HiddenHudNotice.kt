package com.javaniac.blockytype.game

import com.javaniac.blockytype.events.join.ConnectEvent
import com.javaniac.blockytype.themes.DefaultTheme
import net.kyori.adventure.text.Component
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.TextDisplayMeta
import net.minestom.server.instance.Instance

/**
 * The HUD is the title of a boss bar, so F1 (hide GUI) shows the empty world behind it. A big text display
 * right in front of the camera fills the view with a note instead. The HUD background covers it otherwise.
 */
object HiddenHudNotice {
    const val SENTENCE = "You have hidden the HUD. Press F1 to show it again"
    const val REPEATS = 30

    /** Text scale and the height of one text line in blocks at scale 1 (9 px of 40 per block). */
    private const val SCALE = 0.27
    private const val LINE = 9.0 / 40.0

    /** Between the camera stand and the command block, so the crosshair ray still reaches the block. */
    val POS = Pos(
        ConnectEvent.STAND.x(),
        ConnectEvent.STAND.y() - REPEATS * LINE * SCALE / 2, // the text box grows up from its position: center it on the eye
        ConnectEvent.STAND.z() + 0.9,
    )

    fun text(): Component = Component.text((1..REPEATS).joinToString("\n") { SENTENCE }, DefaultTheme.mainColor)

    /** Spawns the notice for [player] only. */
    fun spawn(instance: Instance, player: Player): Entity {
        val entity = Entity(EntityType.TEXT_DISPLAY)
        entity.editEntityMeta(TextDisplayMeta::class.java) {
            it.text = text()
            it.lineWidth = 400
            it.brightnessOverride = (15 shl 4) or (15 shl 20) // full light, the empty world has none
            it.backgroundColor = 0xFF000000.toInt() or DefaultTheme.bgColor.value()
            it.isShadow = false
            it.billboardRenderConstraints = AbstractDisplayMeta.BillboardConstraints.CENTER
            it.scale = Vec(SCALE, SCALE, SCALE)
        }
        entity.updateViewableRule { it == player }
        entity.setNoGravity(true)
        entity.setInstance(instance, POS)
        return entity
    }
}
