package com.javaniac.blockytype.events.game

import com.javaniac.blockytype.net.ArrivalPlayer
import com.javaniac.blockytype.BlockyType
import com.javaniac.blockytype.game.CommandSuggest
import com.javaniac.blockytype.game.PlayerMenuHost
import com.javaniac.blockytype.game.ProfileSuggest
import com.javaniac.blockytype.game.Sessions
import com.javaniac.blockytype.game.TestSession
import com.javaniac.blockytype.pack.FontRegistry
import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.play.TabCompletePacket
import com.javaniac.blockytype.ServerConfig
import com.javaniac.blockytype.menu.DropAction
import com.javaniac.blockytype.menu.InputKeys
import com.javaniac.blockytype.menu.MenuInput
import com.javaniac.blockytype.menu.Page
import com.javaniac.blockytype.settings.Section
import com.javaniac.blockytype.game.TypingCommandTree
import com.javaniac.blockytype.commands.ChatSummary
import com.javaniac.blockytype.game.SpeedUnit
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.PlayerHand
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent
import net.minestom.server.event.player.PlayerChatEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerHandAnimationEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerPacketEvent
import net.minestom.server.event.player.PlayerPacketOutEvent
import net.minestom.server.event.player.PlayerSwapItemEvent
import net.minestom.server.network.packet.client.play.ClientInputPacket
import net.minestom.server.network.packet.client.play.ClientNameItemPacket
import net.minestom.server.network.packet.client.play.ClientPlayerActionPacket
import net.minestom.server.network.packet.client.play.ClientTabCompletePacket
import net.minestom.server.network.packet.client.play.ClientUpdateCommandBlockPacket
import net.minestom.server.network.packet.server.play.DeclareCommandsPacket

/** Listeners of the command block typing flow. */
object GameEvents {
    const val PREFIX = "/t "

    /** Answers a chat command completion request (the typing flow never answers). */
    private fun suggestCommand(player: Player, s: TestSession, id: Int, text: String) {
        val host = PlayerMenuHost(s)
        val r = CommandSuggest.answer(text, host.isAdmin, host::choices, FontRegistry.names(), ::onlineNames)
        player.sendPacket(TabCompletePacket(id, r.start, r.length, r.matches.map { TabCompletePacket.Match(it, null) }))
    }

    /** Names of the players that are online (the completions of the profile look up). */
    private fun onlineNames(): List<String> = MinecraftServer.getConnectionManager().onlinePlayers.map { it.username }

    fun register() {
        val events = MinecraftServer.getGlobalEventHandler()

        // Right-click on the command block: the client opens its (invisible) edit screen by itself.
        events.addListener(PlayerBlockInteractEvent::class.java) { e ->
            val s = Sessions[e.player] ?: return@addListener
            if (e.blockPosition != s.blockPos) return@addListener
            e.isCancelled = true
            e.isBlockingItemUse = true
            if (e.hand != PlayerHand.MAIN) return@addListener
            if (s.edit != null) {
                s.onEditScreenOpened()
            } else if (s.result != null) {
                s.closeScreen()
            } else {
                s.onScreenOpened()
            }
        }

        // Every edit of the command box arrives as a suggestion request. Never answered: no suggestion box.
        events.addListener(PlayerPacketEvent::class.java) { e ->
            val packet = e.packet
            if (packet !is ClientTabCompletePacket) return@addListener
            e.isCancelled = true
            val arrived = (e.player as? ArrivalPlayer)?.takeArrival(packet) ?: System.nanoTime()
            val s = Sessions[e.player] ?: return@addListener
            val text = packet.text()
            if (s.edit != null) {
                if (text.startsWith(PREFIX)) {
                    s.onEditInput(text.substring(PREFIX.length))
                    // Typing a player's name: complete the names of the players that are online.
                    if (s.edit?.isLookup == true) {
                        val r = ProfileSuggest.answer(text, PREFIX, onlineNames())
                        e.player.sendPacket(TabCompletePacket(packet.transactionId(), r.start, r.length, r.matches.map { TabCompletePacket.Match(it, null) }))
                    }
                }
                return@addListener
            }
            if (!s.screenOpen) {
                if (s.menu == null) suggestCommand(e.player, s, packet.transactionId(), text)
                return@addListener
            }
            if (text.isEmpty()) {
                s.onEmptyText()
            } else if (!text.startsWith(PREFIX)) {
                s.cancel()
            } else {
                s.onInput(text.substring(PREFIX.length), arrived)
            }
        }

        // Enter / Done: ignored (same as Esc), except while an admin types a value: that saves it.
        MinecraftServer.getPacketListenerManager()
            .setPlayListener(ClientUpdateCommandBlockPacket::class.java) { packet, player ->
                Sessions[player]?.onEditCommit(packet.command())
            }

        // Only our command tree reaches players.
        events.addListener(PlayerPacketOutEvent::class.java) { e ->
            val p = e.packet
            if (p is DeclareCommandsPacket && !TypingCommandTree.isOurs(p)) {
                e.isCancelled = true
                e.player.sendPacket(TypingCommandTree.of(ServerConfig.isAdmin(e.player.uuid)))
            }
        }

        // Player chat: [best wpm] Name » message, in the sender's theme.
        events.addListener(PlayerChatEvent::class.java) { e ->
            val s = Sessions[e.player] ?: return@addListener
            val unit = SpeedUnit.of(s.effective("typing-speed-unit"))
            e.formattedMessage = ChatSummary.playerChat(s.theme, e.player.username, s.stats?.best, unit, Component.text(e.rawMessage), ServerConfig.isAdmin(e.player.uuid))
        }

        // F: restart (not while a menu is open).
        events.addListener(PlayerSwapItemEvent::class.java) { e ->
            e.isCancelled = true
            val s = Sessions[e.player] ?: return@addListener
            if (s.menu == null) s.reset()
        }

        // Q: opens the settings, closes any open menu. (With the typing screen open the key goes to the text box.)
        events.addListener(PlayerPacketEvent::class.java) { e ->
            val packet = e.packet
            if (packet !is ClientPlayerActionPacket) return@addListener
            if (packet.status() != ClientPlayerActionPacket.Status.DROP_ITEM &&
                packet.status() != ClientPlayerActionPacket.Status.DROP_ITEM_STACK
            ) return@addListener
            e.isCancelled = true
            val s = Sessions[e.player] ?: return@addListener
            val ctrl = packet.status() == ClientPlayerActionPacket.Status.DROP_ITEM_STACK
            when (MenuInput.dropAction(ctrl, menuOpen = s.menu != null, isAdmin = ServerConfig.isAdmin(e.player.uuid))) {
                DropAction.CLOSE -> if (s.edit != null) s.cancelEdit() else s.closeMenu()
                DropAction.SETTINGS -> s.openMenu(Page.Settings(Section.BEHAVIOR))
                DropAction.ADMIN -> s.openMenu(Page.Admin)
                DropAction.NONE -> {}
            }
        }

        // Keybind probe answer (the rename box of the probe anvil). Never reaches Minestom's own handling.
        events.addListener(PlayerPacketEvent::class.java) { e ->
            val packet = e.packet
            if (packet !is ClientNameItemPacket) return@addListener
            e.isCancelled = true
            Sessions[e.player]?.onProbeName(packet.itemName())
        }

        // The player body never moves (the cancelled event makes the server resync it).
        events.addListener(PlayerMoveEvent::class.java) { it.isCancelled = true }

        // Menu keys (the client only sends them with the camera on the player): W, S, A, D move, space selects.
        events.addListener(PlayerPacketEvent::class.java) { e ->
            val packet = e.packet
            if (packet !is ClientInputPacket) return@addListener
            Sessions[e.player]?.menuInput(
                InputKeys(packet.forward(), packet.backward(), packet.left(), packet.right(), packet.jump())
            )
        }

        // Menu: left click selects, the wheel (or a number key) moves the focus. The hotbar slot is always put back.
        events.addListener(PlayerHandAnimationEvent::class.java) { e ->
            Sessions[e.player]?.menuClick()
        }
        events.addListener(PlayerChangeHeldSlotEvent::class.java) { e ->
            e.isCancelled = true
            val s = Sessions[e.player] ?: return@addListener
            if (s.menu != null) s.menuScroll(MenuInput.scrollDirection(e.oldSlot.toInt(), e.newSlot.toInt()))
        }

        // Nothing can be broken, placed or dropped.
        events.addListener(PlayerBlockBreakEvent::class.java) { it.isCancelled = true }
        events.addListener(PlayerBlockPlaceEvent::class.java) { it.isCancelled = true }
        events.addListener(ItemDropEvent::class.java) { it.isCancelled = true }

        events.addListener(PlayerDisconnectEvent::class.java) { e ->
            BlockyType.packServer.forget(e.player)
            val s = Sessions.remove(e.player) ?: return@addListener
            s.dispose()
            MinecraftServer.getInstanceManager().unregisterInstance(s.instance)
        }
    }
}
