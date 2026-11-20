package com.javaniac.blockytype.game

import com.javaniac.blockytype.BlockyType
import com.javaniac.blockytype.menu.ProfileView
import com.javaniac.blockytype.profile.ProfileService
import com.javaniac.blockytype.profile.Lookup
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.key.Key
import com.javaniac.blockytype.sound.TimeWarning
import com.javaniac.blockytype.sound.Sounds
import com.javaniac.blockytype.commands.ChatSummary
import com.javaniac.blockytype.ServerConfig
import com.javaniac.blockytype.db.PlayerSettings
import com.javaniac.blockytype.db.PlayerStats
import com.javaniac.blockytype.db.Storage
import com.javaniac.blockytype.NetworkPanel
import com.javaniac.blockytype.NetworkSettings
import com.javaniac.blockytype.menu.FieldEdit
import com.javaniac.blockytype.menu.InputKeys
import com.javaniac.blockytype.menu.KeyRepeater
import com.javaniac.blockytype.menu.MenuSession
import com.javaniac.blockytype.menu.Nav
import com.javaniac.blockytype.menu.Page
import com.javaniac.blockytype.settings.ServerDefaults
import com.javaniac.blockytype.settings.ServerSettings
import com.javaniac.blockytype.themes.AbstractTheme
import com.javaniac.blockytype.withMinestomMain
import com.javaniac.blockytype.words.LanguageDownloader
import com.javaniac.blockytype.words.LanguageRegistry
import net.kyori.adventure.text.format.TextColor
import com.javaniac.blockytype.themes.DefaultTheme
import com.javaniac.blockytype.ui.CaretMove
import com.javaniac.blockytype.ui.CaretStyle
import com.javaniac.blockytype.ui.CaretTracker
import com.javaniac.blockytype.ui.HudRenderer
import com.javaniac.blockytype.ui.HudState
import com.javaniac.blockytype.ui.Role
import com.javaniac.blockytype.ui.Run
import com.javaniac.blockytype.ui.Stopwatch
import net.kyori.adventure.text.Component
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import kotlinx.coroutines.launch
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.BlockEntityDataPacket
import net.minestom.server.network.packet.server.play.CameraPacket
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import net.minestom.server.instance.block.BlockEntityType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("TestSession")

/** State of one player's typing test and its command block. Touch only from the Minestom tick thread. */
class TestSession(
    val player: Player,
    val instance: Instance,
    val blockPos: Point,
    /** Entity id of the camera stand (typing mode looks through it). */
    private val standId: Int,
    var settings: PlayerSettings = PlayerSettings(),
    /** Theme of this visit picked by `random-theme`; null once the player chooses a theme themselves. */
    private var sessionTheme: String? = null,
) {
    private var created = TestFactory.create(::effective)

    /** The `play-time-warning` sound of this test: plays once. */
    private var warning = TimeWarning(Sounds.warningSeconds(effective("play-time-warning")))

    val test: TypingTest get() = created.test

    /** Whether the server believes the (invisible) command block screen is open. */
    var screenOpen = false

    var result: TestResult? = null
        private set

    /** The second selected on the result graph (A/S/D/W). */
    private val resultCursor = ResultCursor()

    companion object {
        /** Results read for the averages and the pace caret. */
        const val STATS_RESULTS = 10

        /** Cursor step of a key on the result screen: A = -1, D = +1, everything else 0. */
        fun resultStep(nav: Nav): Int = when (nav) {
            Nav.LEFT -> -1
            Nav.RIGHT -> 1
            else -> 0
        }
    }

    private var timer: Task? = null

    private val caret = CaretTracker()

    /** Pending decision about an empty command box text, see [onEmptyText]. */
    private var emptyCheck: Task? = null

    /** How the player's client names our keys. Defaults until [startKeybindProbe] gets an answer. */
    var keybinds = Keybinds.DEFAULT
        private set

    private var probe: Task? = null

    /** The task that asks for the keybinds again now and then (`keybind-check`), and when the next ask is due. */
    private var keybindPoll: Task? = null
    private var nextKeybindCheck: Long? = null

    /** Starts the periodic keybind check. Called once, after the join probe was scheduled. */
    fun startKeybindPoll() {
        if (keybindPoll != null) return
        nextKeybindCheck = KeybindPoll.next(System.currentTimeMillis(), KeybindPoll.intervalMillis(effective("keybind-check")))
        keybindPoll = MinecraftServer.getSchedulerManager().buildTask { keybindPollTick() }.repeat(TaskSchedule.seconds(5)).schedule()
    }

    private fun keybindPollTick() {
        if (!player.isOnline) return
        val interval = KeybindPoll.intervalMillis(effective("keybind-check"))
        val now = System.currentTimeMillis()
        if (interval == null) {
            nextKeybindCheck = null
            return
        }
        val due = nextKeybindCheck ?: KeybindPoll.next(now, interval)!!.also { nextKeybindCheck = it }
        val idle = probe == null && menu == null && !screenOpen && edit == null && result == null &&
            test.state == TypingTest.State.READY
        if (KeybindPoll.due(now, due, idle)) {
            nextKeybindCheck = KeybindPoll.next(now, interval)
            startKeybindProbe()
        }
    }

    /**
     * Asks the client for its keybinds: opens an anvil holding an item named by keybind components. The client
     * resolves them and answers with its rename box text ([onProbeName]). Gives up after 5 s.
     */
    fun startKeybindProbe() {
        if (probe != null || menu != null || screenOpen) return
        val inventory = Inventory(InventoryType.ANVIL, Component.empty())
        inventory.setItemStack(0, KeybindProbe.item())
        player.sendPacket(Stopwatch.probePacket()) // hides the anvil screen, see Stopwatch.PROBE_TICKS
        player.openInventory(inventory)
        probe = MinecraftServer.getSchedulerManager().buildTask { endProbe() }
            .delay(TaskSchedule.seconds(5)).schedule()
    }

    /** The rename box text of the probe anvil. Ignored unless a probe is running. */
    fun onProbeName(text: String) {
        if (probe == null) return
        val parsed = Keybinds.parse(text) ?: return
        keybinds = parsed
        log.info("Keybinds of ${player.username}: $parsed")
        endProbe()
    }

    private fun endProbe() {
        probe?.cancel()
        probe = null
        player.closeInventory()
        Stopwatch.reset(player) // back to the normal animation clock
        render()
    }

    /** Open menu (settings or admin tab), or null while typing. */
    var menu: MenuSession? = null
        private set

    /** The player's value of setting [id]: own choice, then the server default, then the catalog default. */
    fun effective(id: String): String = ServerDefaults.effective(id, overrides())

    /** The player's own choices, with this visit's random theme on top. */
    private fun overrides(): Map<String, String> = sessionTheme?.let { settings.overrides + ("theme" to it) } ?: settings.overrides

    /** The resource pack this player should have, from the font, anti-aliasing, input box and theme settings. */
    fun packVariant() = ServerDefaults.packVariant(overrides())

    val theme: AbstractTheme
        get() = BlockyType.themes.firstOrNull { it.name.equals(effective("theme"), ignoreCase = true) } ?: DefaultTheme

    /** Applies [change] to the player's settings, redraws and stores them. */
    fun updateSettings(change: (PlayerSettings) -> PlayerSettings) {
        val before = TestFactory.SETTINGS.map(::effective)
        val theme = settings.overrides["theme"]
        val warningSeconds = Sounds.warningSeconds(effective("play-time-warning"))
        settings = change(settings)
        if (settings.overrides["theme"] != theme) sessionTheme = null // their own choice wins over the random one
        if (Sounds.warningSeconds(effective("play-time-warning")) != warningSeconds) {
            warning = TimeWarning(Sounds.warningSeconds(effective("play-time-warning")))
        }
        if (TestFactory.SETTINGS.map(::effective) != before) reset() // another mode or language: a new test
        settingsChanged()
        val saved = settings
        BlockyType.launch {
            try {
                Storage.saveSettings(player.uuid, saved)
            } catch (t: Throwable) {
                log.error("Could not save settings of ${player.username}", t)
            }
        }
    }

    /** Redraws after a setting changed (own or a server default) and sends a new pack if the variant changed. */
    fun settingsChanged() {
        BlockyType.packServer.sendAsync(player, packVariant())
        render()
    }

    /**
     * Opens a menu: the camera moves to the player (so the client sends its movement keys, used to navigate) and the command
     * block is removed (a right-click on it would open the invisible screen and release the mouse).
     */
    fun openMenu(page: Page) {
        if (menu != null) return
        // Q only reaches the server when the typing screen is closed (with it open the key types a letter),
        // so a Q means the player left the screen with Esc, which the server cannot see.
        emptyCheck?.cancel()
        emptyCheck = null
        screenOpen = false
        menu = MenuSession(PlayerMenuHost(this), page)
        repeater.reset()
        heldKeys = InputKeys() // key states from before the menu are stale
        repeatTask?.cancel() // a result screen already runs one
        repeatTask = MinecraftServer.getSchedulerManager().buildTask { repeatTick() }
            .repeat(TaskSchedule.tick(1)).schedule()
        instance.setBlock(blockPos, Block.AIR)
        player.sendPacket(CameraPacket(player.entityId))
        render()
    }

    /** W, A, S, D, space of the client's input packet: a press is one step, a held direction key repeats. */
    private val repeater = KeyRepeater()

    /** The last key state, replayed by [repeatTick] because the client only sends changes. */
    private var heldKeys = InputKeys()

    private var repeatTask: Task? = null

    fun menuInput(keys: InputKeys) {
        heldKeys = keys
        if (menu == null) {
            if (result != null) for (nav in repeater.update(keys, System.nanoTime())) resultNav(nav)
            return
        }
        for (nav in repeater.update(keys, System.nanoTime())) menuNav(nav)
    }

    private fun repeatTick() {
        val m = menu
        if (m == null) {
            if (result != null) for (nav in repeater.update(heldKeys, System.nanoTime())) resultNav(nav)
            return
        }
        val now = System.nanoTime()
        for (nav in repeater.update(heldKeys, now)) menuNav(nav)
        if (menu != null && edit == null && m.tick(now)) render() // long footer texts scroll
    }

    /**
     * The result screen: A selects the previous second of the graph, D the next one. The first step
     * selects the first (forward) or the last (backward) second.
     */
    private fun resultNav(nav: Nav) {
        val seconds = result?.perSecond?.size ?: return
        val dir = resultStep(nav)
        if (dir == 0) return
        resultCursor.step(dir, seconds)
        render()
    }

    /**
     * Result shown: like in a menu the camera moves to the player (the stand camera sends no movement keys) and the
     * command block is removed, so the keys scroll the graph and a click cannot open the invisible screen.
     */
    private fun beginResultNav() {
        emptyCheck?.cancel()
        emptyCheck = null
        screenOpen = false
        resultCursor.reset()
        repeater.reset()
        heldKeys = InputKeys()
        repeatTask?.cancel()
        repeatTask = MinecraftServer.getSchedulerManager().buildTask { repeatTick() }
            .repeat(TaskSchedule.tick(1)).schedule()
        instance.setBlock(blockPos, Block.AIR)
        player.sendPacket(CameraPacket(player.entityId))
    }

    /** Back to typing: the stand camera and the command block. */
    private fun endResultNav() {
        resultCursor.reset()
        repeatTask?.cancel()
        repeatTask = null
        heldKeys = InputKeys()
        player.sendPacket(CameraPacket(standId))
        instance.setBlock(blockPos, Block.COMMAND_BLOCK)
    }

    /** One keyboard step in a menu. Nothing happens while a value is being typed. */
    fun menuNav(nav: Nav) {
        val m = menu ?: return
        if (edit != null) return
        when (nav) {
            Nav.UP -> m.move(-1)
            Nav.DOWN -> m.move(1)
            Nav.LEFT -> m.moveCell(-1)
            Nav.RIGHT -> m.moveCell(1)
            Nav.SELECT -> m.activate()
        }
        if (menu != null) render()
    }

    /** Left click in a menu: selects the focused option (may close the menu). */
    fun menuClick() = menuNav(Nav.SELECT)

    /** Wheel (or number key): [direction] +1 moves the focus down. */
    fun menuScroll(direction: Int) {
        if (direction > 0) menuNav(Nav.DOWN) else if (direction < 0) menuNav(Nav.UP)
    }

    // ---- profiles

    /** Profiles loaded while the menu is open (dropped when it closes, so the next look sees fresh numbers). */
    private val profileViews = HashMap<UUID, ProfileView>()

    /**
     * The profile of [uuid]. The first call starts loading it in the background and answers
     * [ProfileView.Loading]; the menu is drawn again when it arrives.
     */
    fun profileView(uuid: UUID): ProfileView {
        profileViews[uuid]?.let { return it }
        profileViews[uuid] = ProfileView.Loading
        val textures = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)?.skin?.textures()
        BlockyType.launch {
            val view = try {
                Storage.playerByUuid(uuid)?.let { ProfileView.Ready(ProfileService.load(it, textures)) } ?: ProfileView.Missing
            } catch (t: Throwable) {
                log.error("Could not load the profile of $uuid", t)
                ProfileView.Missing
            }
            withMinestomMain {
                if (menu != null) {
                    profileViews[uuid] = view
                    render()
                }
            }
        }
        return ProfileView.Loading
    }

    /** Opens the profile of [uuid] in the menu (opening the menu first when it is closed). */
    fun openProfile(uuid: UUID) {
        if (menu == null) openMenu(Page.Profile(uuid)) else menu?.showProfile(uuid)
        if (menu != null) render()
    }

    /** Starts typing the name of a player to look up: the command block comes back so the player can right-click it. */
    fun beginLookup() {
        if (menu == null || edit != null) return
        edit = FieldEdit.lookup()
        instance.setBlock(blockPos, Block.COMMAND_BLOCK)
        render()
    }

    /** Enter while typing a name: opens that player's profile, or says why not. Anybody may look up anybody who joined. */
    private fun commitLookup(e: FieldEdit, command: String) {
        val name = e.commit(command.removePrefix("/t").trimStart()) ?: run {
            render()
            return
        }
        BlockyType.launch {
            val found = try {
                ProfileService.lookup(name)
            } catch (t: Throwable) {
                log.error("Could not look up $name", t)
                Lookup.Unknown
            }
            withMinestomMain {
                if (edit !== e) return@withMinestomMain // cancelled or replaced while the database answered
                when (found) {
                    is Lookup.Found -> {
                        cancelEdit()
                        menu?.showProfile(found.player.uuid)
                        render()
                    }
                    Lookup.Unknown -> { e.fail("never joined"); render() }
                    Lookup.Invalid -> { e.fail("not a valid name"); render() }
                }
            }
        }
    }

    // ---- typing a value (admin network settings) through the command block screen

    /** The value being typed, or null. The command block is in the world while this is set. */
    var edit: FieldEdit? = null
        private set

    /** Starts typing a network value: the command block comes back so the player can right-click it. */
    fun beginEdit(key: String) {
        val current = NetworkSettings.fields(BlockyType.network, NetworkPanel.snapshot()).firstOrNull { it.key == key } ?: return
        edit = FieldEdit(key, current.value)
        instance.setBlock(blockPos, Block.COMMAND_BLOCK)
        render()
    }

    /** The player right-clicked the block during an edit: the screen opens, prefilled with the draft. */
    fun onEditScreenOpened() {
        val e = edit ?: return
        player.sendPacket(
            BlockEntityDataPacket(
                blockPos, BlockEntityType.COMMAND_BLOCK,
                CompoundBinaryTag.builder().putString("Command", "/t " + e.draft).build()
            )
        )
    }

    /** Every edit of the box during an edit: [text] is what follows the `/t ` prefix. */
    fun onEditInput(text: String) {
        val e = edit ?: return
        e.type(text)
        render()
    }

    /** Enter in the box: saves a valid value (admins only, checked again), else shows the error. */
    fun onEditCommit(command: String) {
        val e = edit ?: run {
            quickRestart()
            return
        }
        if (e.isLookup) {
            commitLookup(e, command)
            return
        }
        if (!ServerConfig.isAdmin(player.uuid)) {
            cancelEdit()
            return
        }
        val value = e.commit(command.removePrefix("/t").trimStart())
        if (value == null) {
            render()
            return
        }
        try {
            ServerSettings.setNetwork(e.key, value)
        } catch (t: Throwable) {
            log.error("Could not save ${e.key}", t)
        }
        cancelEdit()
    }

    /** Enter in the typing screen with `quick-restart` on `enter`: a fresh test. The client closes its screen on Enter. */
    fun quickRestart() {
        if (menu != null || effective("quick-restart") != "enter") return
        screenOpen = false
        reset()
    }

    /** Stops typing: the block is removed again, which closes the client's screen. */
    fun cancelEdit() {
        if (edit == null) return
        edit = null
        instance.setBlock(blockPos, Block.AIR)
        render()
    }

    fun closeMenu() {
        if (menu == null) return
        menu = null
        profileViews.clear()
        heldKeys = InputKeys()
        repeater.reset()
        if (result == null) {
            repeatTask?.cancel()
            repeatTask = null
            player.sendPacket(CameraPacket(standId))
            instance.setBlock(blockPos, Block.COMMAND_BLOCK)
        } // else the result screen goes on: the camera stays on the player and the block stays away
        render()
    }

    /** The word list being downloaded for this player, if any. */
    private var downloading: String? = null

    /**
     * Sets the language. Lists that are not on disk yet are downloaded first (off the tick thread); the player keeps
     * the old language until the download worked.
     */
    fun setLanguage(name: String) {
        if (name !in LanguageRegistry.names()) return
        if (LanguageRegistry.isAvailable(name)) {
            updateSettings { it.with("language", name) }
            return
        }
        downloading?.let {
            say("already downloading $it, wait for it", theme.errorColor)
            return
        }
        downloading = name
        say("downloading the $name word list...", theme.subColor)
        BlockyType.launch {
            val result = runCatching { LanguageDownloader.download(name) }
            withMinestomMain {
                downloading = null
                result.onSuccess {
                    updateSettings { s -> s.with("language", name) }
                    say("language set to ${LanguageRegistry.entry(name)?.display ?: name}", theme.mainColor)
                }.onFailure { e ->
                    log.warn("Could not download language $name: ${e.message}")
                    say("could not download $name: ${e.message}", theme.errorColor)
                }
            }
        }
    }

    private fun say(text: String, color: TextColor) {
        player.sendMessage(Component.text("» ", theme.caretColor).append(Component.text(text, color)))
    }

    private var shownRemaining = -1

    /** Every tick of a running time test: ends it at the deadline, redraws when the countdown changes. */
    private fun tickTime() {
        val now = System.nanoTime()
        test.expire(now)
        if (test.state == TypingTest.State.FINISHED) {
            if (result == null) finish()
            return
        }
        val left = test.remainingSeconds(now)
        if (left != shownRemaining) {
            shownRemaining = left
            if (!test.checkMinimums(now)) {
                finish()
                return
            }
            if (warning.check(left)) Sounds.TIME_WARNING.let(::play)
            render()
        }
    }

    /** Fresh, not started test. */
    fun reset() {
        timer?.cancel()
        timer = null
        shownRemaining = -1
        created = TestFactory.create(::effective)
        warning = TimeWarning(Sounds.warningSeconds(effective("play-time-warning")))
        val hadResult = result != null
        result = null
        caret.reset()
        if (hadResult && menu == null) endResultNav()
        render()
    }

    /** How the player's settings make the test look. */
    val view: ViewOptions get() = ViewOptions.from(::effective)

    /** Best, last and average results of this player, loaded after the join and after each test (null until then). */
    var stats: PlayerStats? = null
        private set

    /** Reads the player's results (off the tick thread) and redraws. */
    fun refreshStats() {
        val uuid = player.uuid
        val mode = ResultView.modeName(test, created.language)
        BlockyType.launch {
            val loaded = runCatching {
                PlayerStats.of(Storage.recentResults(uuid, STATS_RESULTS), null, Storage.bestWpm(uuid, mode))
            }.getOrNull() ?: return@launch
            withMinestomMain {
                stats = loaded
                if (menu == null && test.state == TypingTest.State.READY) render()
            }
        }
    }

    fun render() {
        val now = System.nanoTime()
        val v = view
        var state: HudState = menu?.view()
            ?: result?.let { ResultView.toHudState(it, test, keybinds, resultCursor.index, v) }
            ?: TypingView.build(test, now, created.displayLanguage, v, stats, PaceCaret.target(v.paceMode, v.paceCustom, stats))
        if (state is HudState.Typing) {
            val k = keybinds
            val footer = if (!v.keyTips) {
                emptyList()
            } else if (test.state == TypingTest.State.READY) {
                Keybinds.join(
                    listOf(
                        k.typeHint(),
                        listOf(Run(k.restart, Role.MAIN), Run(" restart", Role.SUB)),
                    )
                )
            } else {
                listOf(Run(k.restart, Role.MAIN), Run(" restart", Role.SUB))
            }
            state = state.copy(
                caretMove = if (effective("animation") == "on") caret.current(now) else CaretMove.NONE,
                footer = footer,
                side = if (v.keyTips) k.side(ServerConfig.isAdmin(player.uuid)) else emptyList(),
            )
        }
        HudRenderer.update(player, state, theme)
    }

    /**
     * Called after an accepted edit: when the caret stepped one column, the HUD flags it so the shader slides it,
     * and the client animation clock restarts (see [Stopwatch]).
     */
    private fun trackCaret() {
        val now = System.nanoTime()
        val s = TypingView.build(test, now, "", view)
        if (caret.update(s.caretLine to s.caretColumn, now) != CaretMove.NONE && effective("animation") == "on") {
            Stopwatch.reset(player)
        }
    }

    /** Plays the click sound of `play-sound-on-click` for [char]. */
    private fun playClick(char: Char) {
        val event = Sounds.clickEvent(effective("play-sound-on-click"), char) ?: return
        play(event)
    }

    private fun playError() {
        val event = Sounds.errorEvent(effective("play-sound-on-error")) ?: return
        play(event)
    }

    private fun play(event: String) {
        val volume = Sounds.volume(effective("sound-volume"))
        if (volume <= 0f) return
        player.playSound(Sound.sound(Key.key(event), Sound.Source.MASTER, volume, 1f))
    }

    /** Sounds of a key the test took (a click, or the error sound when it was a mistake); [keysBefore] = the key count before. */
    private fun keySounds(input: String, keysBefore: Int) {
        if (test.keys.size <= keysBefore || input.isEmpty()) return
        if (test.keys.last().correct) playClick(input.last()) else playError()
    }

    /** Applies the text of the command box (without `/t `). */
    fun onInput(input: String) {
        val keysBefore = test.keys.size
        val outcome = test.apply(input, System.nanoTime())
        keySounds(input, keysBefore)
        when (outcome) {
            TypingTest.Outcome.UNCHANGED -> return
            TypingTest.Outcome.REJECTED -> if (test.state == TypingTest.State.FINISHED && result == null) finish() else prefill()
            TypingTest.Outcome.PENALIZED -> {
                prefill()
                ensureTimer()
                render()
            }
            TypingTest.Outcome.ACCEPTED -> {
                trackCaret()
                ensureTimer()
                if (test.typed != input) prefill() // a rule (delete on error) changed the text
                if (test.state == TypingTest.State.FINISHED) finish() else render()
            }
        }
    }

    /** Starts the clock task once the test runs: the deadline of a time test, the 1 s redraw of a words test. */
    private fun ensureTimer() {
        if (test.state != TypingTest.State.RUNNING || timer != null) return
        timer = if (test.mode is TestMode.Time) {
            MinecraftServer.getSchedulerManager().buildTask { tickTime() }.repeat(TaskSchedule.tick(1)).schedule()
        } else {
            MinecraftServer.getSchedulerManager().buildTask { tickWords() }
                .repeat(if (view.paceMode != "off" && view.paceStyle != CaretStyle.OFF) TaskSchedule.millis(200) else TaskSchedule.seconds(1))
                .schedule()
        }
    }

    private fun tickWords() {
        if (!test.checkMinimums(System.nanoTime())) {
            if (result == null) finish()
            return
        }
        render()
    }

    private fun finish() {
        timer?.cancel()
        timer = null
        val r = TestStats.result(test)
        result = r
        if (menu == null) beginResultNav()
        render()
        val failure = test.failure
        if (failure != null) {
            player.sendMessage(ChatSummary.failed(failure, test.rules, theme))
            return
        }
        val uuid = player.uuid
        val mode = ResultView.modeName(test, created.language)
        BlockyType.launch {
            try {
                val previousBest = Storage.bestWpm(uuid, mode)
                Storage.saveResult(uuid, mode, r)
                val recent = Storage.recentResults(uuid, STATS_RESULTS)
                withMinestomMain {
                    val best = if (previousBest == null || r.wpm > previousBest) r.wpm else previousBest
                    val loaded = PlayerStats.of(recent, null, best)
                    stats = loaded
                    sendSummary(r, previousBest, loaded.average)
                }
            } catch (t: Throwable) {
                log.error("Could not save test result", t)
                withMinestomMain { sendSummary(r, null, null) }
            }
        }
    }

    /** The chat message after a finished test (`chat-summary` setting). */
    private fun sendSummary(r: TestResult, previousBest: Double?, average: Double?) {
        val v = view
        val mode = created.test.mode.label
        for (line in ChatSummary.build(r, mode, created.displayLanguage, v.unit, v.decimals, effective("chat-summary"), previousBest, average, theme)) {
            player.sendMessage(line)
        }
    }

    /**
     * The box text became empty. That is also the first thing the client sends when it (re)opens the screen,
     * one packet *before* the right-click reaches the server, so the verdict waits two ticks: if the
     * right-click arrives ([onScreenOpened]) it was a reopen, otherwise the player erased the prefix.
     */
    fun onEmptyText() {
        if (emptyCheck != null) return
        emptyCheck = MinecraftServer.getSchedulerManager()
            .buildTask {
                emptyCheck = null
                if (screenOpen) cancel()
            }
            .delay(TaskSchedule.tick(2))
            .schedule()
    }

    /** The player right-clicked the block: the screen is (re)opened and prefilled. */
    fun onScreenOpened() {
        emptyCheck?.cancel()
        emptyCheck = null
        screenOpen = true
        prefill()
    }

    /** Test cancelled (box text lost its `/t ` prefix): reset and close the screen. */
    fun cancel() {
        emptyCheck?.cancel()
        emptyCheck = null
        reset()
        closeScreen()
    }

    /** Writes `/t ` + the accepted text into the box (also resyncs it after a rejected edit). */
    fun prefill() {
        player.sendPacket(
            BlockEntityDataPacket(
                blockPos, BlockEntityType.COMMAND_BLOCK,
                CompoundBinaryTag.builder().putString("Command", "/t " + test.typed).build()
            )
        )
    }

    /**
     * Closes the client's edit screen: it closes itself when its block disappears, so the block is
     * removed and restored a few ticks later.
     */
    fun closeScreen() {
        screenOpen = false
        instance.setBlock(blockPos, Block.AIR)
        instance.scheduler().buildTask { if (result == null) instance.setBlock(blockPos, Block.COMMAND_BLOCK) }
            .delay(TaskSchedule.tick(3))
            .schedule()
    }

    fun dispose() {
        keybindPoll?.cancel()
        menu = null
        repeatTask?.cancel()
        repeatTask = null
        probe?.cancel()
        probe = null
        timer?.cancel()
        timer = null
        emptyCheck?.cancel()
        emptyCheck = null
        HudRenderer.hide(player)
    }
}


object Sessions {
    private val sessions = ConcurrentHashMap<UUID, TestSession>()

    operator fun get(player: Player): TestSession? = sessions[player.uuid]

    fun put(session: TestSession) {
        sessions[session.player.uuid] = session
    }

    fun remove(player: Player): TestSession? = sessions.remove(player.uuid)

    fun all(): List<TestSession> = sessions.values.toList()
}
