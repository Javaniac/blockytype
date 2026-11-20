package com.javaniac.blockytype.pack

import com.javaniac.blockytype.sound.Sounds
import com.javaniac.blockytype.ui.ResultGraph
import com.javaniac.blockytype.ui.TextScale
import com.javaniac.blockytype.ui.UiFont
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

/**
 * Generates the BlockyType server resource pack as an in-memory file map (pack path to bytes).
 * Port of the former `build_pack.py`. The server calls it at startup and on `/pack reload`.
 */
object PackBuilder {
    private const val FONT_DIR = "assets/blockytype/font/"
    private const val TEXTURE_DIR = "assets/blockytype/textures/font/"
    private const val WIDGET_DIR = "assets/minecraft/textures/gui/sprites/widget/"
    private const val BOSS_BAR_DIR = "assets/minecraft/textures/gui/sprites/boss_bar/"

    private val TRANSPARENT = PngWriter.Pixel { _, _ -> PngWriter.rgba(0, 0, 0, 0) }

    /** The pack of [variant]. An unusable font falls back to the bundled one. */
    fun build(variant: PackVariant): Map<String, ByteArray> =
        build(FontRegistry.loadOrBundled(variant.font), variant.showInput, variant.antiAlias, variant.box)

    fun build(
        font: FontInfo = FontRegistry.loadOrBundled(FontRegistry.BUNDLED),
        showInput: Boolean = true,
        antiAlias: Boolean = true,
        box: BoxColors = BoxColors.DEFAULT,
    ): Map<String, ByteArray> {
        val files = sortedMapOf<String, ByteArray>()
        fun text(path: String, content: String) {
            files[path] = content.toByteArray(Charsets.UTF_8)
        }

        fun png(path: String, w: Int, h: Int, pixel: PngWriter.Pixel) {
            files[path] = PngWriter.encode(w, h, pixel)
        }

        fun nineSlice(path: String, border: Int) = text(
            "$path.mcmeta",
            Json.write(
                mapOf(
                    "gui" to mapOf(
                        "scaling" to linkedMapOf(
                            "type" to "nine_slice", "width" to 200, "height" to 20, "border" to border
                        )
                    )
                )
            )
        )

        // ---- pack.mcmeta, lang, sprites
        text(
            "pack.mcmeta", Json.write(
                linkedMapOf(
                    "pack" to linkedMapOf(
                        "description" to "BlockyType UI test pack",
                        "min_format" to listOf(ClientVersion.V26_2.packFormat, 0),
                        "max_format" to ClientVersion.V26_3.packFormat,
                    ),
                    "overlays" to mapOf(
                        "entries" to listOf(
                            linkedMapOf(
                                "directory" to "overlay_26_3",
                                "min_format" to listOf(ClientVersion.V26_3.packFormat, 0),
                                "max_format" to ClientVersion.V26_3.packFormat,
                            )
                        )
                    ),
                )
            )
        )

        // Blank the command block screen labels.
        text(
            "assets/minecraft/lang/en_us.json", Json.write(
                listOf(
                    "advMode.setCommand", "advMode.command", "advMode.previousOutput",
                    "advMode.mode.auto", "advMode.mode.redstone", "advMode.mode.sequence",
                    "advMode.mode.conditional", "advMode.mode.unconditional",
                    "advMode.mode.autoexec.bat", "advMode.mode.redstoneTriggered",
                ).associateWithTo(linkedMapOf()) { "" }
            )
        )

        // Command box: transparent when the input is hidden, else a flat box in the button style (shared sprite).
        for ((n, border) in listOf("text_field" to box.border, "text_field_highlighted" to box.borderFocus)) {
            if (showInput) {
                png("$WIDGET_DIR$n.png", 200, 20) { x, y ->
                    val c = if (x == 0 || x == 199 || y == 0 || y == 19) border else box.fill
                    PngWriter.rgba((c shr 16) and 255, (c shr 8) and 255, c and 255, 255)
                }
            } else {
                png("$WIDGET_DIR$n.png", 200, 20, TRANSPARENT)
            }
            nineSlice("$WIDGET_DIR$n.png", 1)
        }

        // The keybind probe opens an anvil: its background, name box, error icon and slot highlight are
        // transparent (the texts hide in text.vsh, the cursor in gui.vsh, the item has an empty model).
        png("assets/minecraft/textures/gui/container/anvil.png", 256, 256, TRANSPARENT)
        for (n in listOf(
            "container/anvil/text_field", "container/anvil/text_field_disabled", "container/anvil/error",
            "container/slot_highlight_back", "container/slot_highlight_front",
        )) {
            png("assets/minecraft/textures/gui/sprites/$n.png", 24, 24, TRANSPARENT)
        }
        text("assets/blockytype/items/blank.json", Json.write(linkedMapOf("model" to linkedMapOf("type" to "minecraft:empty"))))

        // BlockyType's own flat button style (original art, not derived from vanilla textures).
        // Alpha MARKER_ALPHA lets position_tex_color.fsh recognise them.
        data class Button(val name: String, val fill: IntArray, val border: IntArray, val slice: Int)
        for (b in listOf(
            Button("button", intArrayOf(58, 58, 62), intArrayOf(84, 84, 90), 3),
            Button("button_highlighted", intArrayOf(76, 76, 82), intArrayOf(220, 220, 220), 3),
            Button("button_disabled", intArrayOf(40, 40, 44), intArrayOf(56, 56, 60), 1),
        )) {
            png("$WIDGET_DIR${b.name}.png", 200, 20) { x, y ->
                val c = if (x == 0 || x == 199 || y == 0 || y == 19) b.border else b.fill
                PngWriter.rgba(c[0], c[1], c[2], Shaders.MARKER_ALPHA)
            }
            nineSlice("$WIDGET_DIR${b.name}.png", b.slice)
        }

        // ---- HUD: boss bar sprites (the whole UI is one boss bar title; the bar itself is invisible)
        for (c in listOf("pink", "blue", "red", "green", "yellow", "purple", "white")) {
            for (part in listOf("background", "progress")) {
                png("$BOSS_BAR_DIR${c}_$part.png", 182, 5, TRANSPARENT)
            }
        }
        for (n in listOf(6, 10, 12, 20)) {
            for (part in listOf("background", "progress")) {
                png("${BOSS_BAR_DIR}notched_${n}_$part.png", 182, 5, TRANSPARENT)
            }
        }

        // ---- Sounds (click, error and time warning sounds of the sound settings)
        files["assets/blockytype/sounds.json"] = Sounds.soundsJson().toByteArray(Charsets.UTF_8)
        for (f in Sounds.files()) files[Sounds.PACK_DIR + f] = Sounds.bytes(f)

        // ---- Fonts
        files[FONT_DIR + font.packFile] = font.bytes
        // Outside assets/: asset paths must be lowercase.
        FontRegistry.licenseOf(font.name)?.let { files["licenses/${font.name.lowercase()}.txt"] = it }
        // The font is scaled so one char advances exactly UiFont.MONO_ADV units (3x for the big font).
        // oversample ~ highest expected GUI scale keeps glyphs sharp.
        val fonts = listOf("mono" to font.monoSize, "mono_big" to font.monoBigSize) +
            listOf(TextScale.X1_5, TextScale.X2).map { it.font.value() to font.monoSize * it.factor }
        for ((name, size) in fonts) {
            text(
                "$FONT_DIR$name.json", Json.write(
                    mapOf(
                        "providers" to listOf(
                            linkedMapOf(
                                "type" to "ttf",
                                "file" to "blockytype:${font.packFile}",
                                "size" to size,
                                "oversample" to 8.0,
                                "shift" to listOf(0, 0),
                                "skip" to emptyList<String>(),
                            )
                        )
                    )
                )
            )
        }

        // UI glyphs (font blockytype:ui), see UiFont.
        png("${TEXTURE_DIR}square.png", 8, 8) { _, _ -> PngWriter.rgba(255, 255, 255, 255) }
        png("${TEXTURE_DIR}caret.png", 1, 11) { _, _ -> PngWriter.rgba(255, 255, 255, 255) }
        // Carets in the shape of a letter cell: a hollow box and an underline (the bottom two rows), 6 x 11 like the caret bar.
        png("${TEXTURE_DIR}caret_outline.png", 6, 11) { x, y ->
            if (x == 0 || x == 5 || y == 0 || y == 10) PngWriter.rgba(255, 255, 255, 255) else PngWriter.rgba(0, 0, 0, 0)
        }
        png("${TEXTURE_DIR}caret_underline.png", 6, 11) { _, y ->
            if (y >= 9) PngWriter.rgba(255, 255, 255, 255) else PngWriter.rgba(0, 0, 0, 0)
        }
        // Filled blocks (pills, strips, focus outline): one row of 7 cells, cell k fills its first 2^k columns.
        for ((file, height) in listOf("block.png" to UiFont.BLOCK_HEIGHT, "block_tall.png" to UiFont.TALL_HEIGHT)) {
            val cell = 1 shl UiFont.BLOCK_MAX_BIT
            png("$TEXTURE_DIR$file", cell * (UiFont.BLOCK_MAX_BIT + 1), height) { x, _ ->
                if (x % cell < (1 shl (x / cell))) PngWriter.rgba(255, 255, 255, 255) else PngWriter.rgba(0, 0, 0, 0)
            }
        }
        // The face of a profile: 8 glyphs (one per pixel row), each a FACE_PIXEL square in a FACE_SIZE tall cell.
        png("${TEXTURE_DIR}face.png", 8 * UiFont.FACE_PIXEL, UiFont.FACE_SIZE) { x, y ->
            if (y / UiFont.FACE_PIXEL == x / UiFont.FACE_PIXEL) PngWriter.rgba(255, 255, 255, 255) else PngWriter.rgba(0, 0, 0, 0)
        }
        // Result graph dots: one atlas; glyph DOT_BASE + h is a 1 x ATLAS cell with one white pixel h units above its bottom.
        png("${TEXTURE_DIR}graph.png", ResultGraph.ATLAS, ResultGraph.ATLAS) { x, y ->
            if (y == ResultGraph.ATLAS - 1 - x) PngWriter.rgba(255, 255, 255, 255) else PngWriter.rgba(0, 0, 0, 0)
        }
        val advances = linkedMapOf<String, Int>()
        for (k in 0..UiFont.MAX_SPACE_BIT) {
            advances[(UiFont.NEG_SPACE_BASE + k).toChar().toString()] = -(1 shl k)
            advances[(UiFont.POS_SPACE_BASE + k).toChar().toString()] = 1 shl k
        }
        text(
            "${FONT_DIR}ui.json", Json.write(
                mapOf(
                    "providers" to listOf(
                        linkedMapOf(
                            "type" to "bitmap", "file" to "blockytype:font/square.png",
                            "ascent" to 7, "height" to 8, "chars" to listOf(UiFont.SQUARE.toString()),
                        ),
                        linkedMapOf(
                            "type" to "bitmap", "file" to "blockytype:font/caret.png",
                            "ascent" to 8, "height" to 11, "chars" to listOf(UiFont.CARET.toString()),
                        ),
                        linkedMapOf(
                            "type" to "bitmap", "file" to "blockytype:font/caret_outline.png",
                            "ascent" to 8, "height" to 11, "chars" to listOf(UiFont.CARET_OUTLINE.toString()),
                        ),
                        linkedMapOf(
                            "type" to "bitmap", "file" to "blockytype:font/caret_underline.png",
                            "ascent" to 8, "height" to 11, "chars" to listOf(UiFont.CARET_UNDERLINE.toString()),
                        ),
                        linkedMapOf(
                            "type" to "bitmap", "file" to "blockytype:font/block.png",
                            "ascent" to UiFont.BLOCK_ASCENT, "height" to UiFont.BLOCK_HEIGHT,
                            "chars" to listOf((0..UiFont.BLOCK_MAX_BIT).joinToString("") { (UiFont.BLOCK_BASE + it).toChar().toString() }),
                        ),
                        linkedMapOf(
                            "type" to "bitmap", "file" to "blockytype:font/block_tall.png",
                            "ascent" to UiFont.TALL_ASCENT, "height" to UiFont.TALL_HEIGHT,
                            "chars" to listOf((0..UiFont.BLOCK_MAX_BIT).joinToString("") { (UiFont.TALL_BASE + it).toChar().toString() }),
                        ),
                        linkedMapOf(
                            "type" to "bitmap", "file" to "blockytype:font/graph.png",
                            "ascent" to 7, "height" to ResultGraph.ATLAS,
                            "chars" to listOf(
                                (0 until ResultGraph.ATLAS).joinToString("") { (ResultGraph.DOT_BASE + it).toChar().toString() }
                            ),
                        ),
                        linkedMapOf(
                            "type" to "bitmap", "file" to "blockytype:font/face.png",
                            "ascent" to 7, "height" to UiFont.FACE_SIZE,
                            "chars" to listOf((0 until 8).joinToString("") { (UiFont.FACE_BASE + it).toChar().toString() }),
                        ),
                        linkedMapOf("type" to "space", "advances" to advances),
                    )
                )
            )
        )

        // ---- Shaders
        for (v in ClientVersion.entries) {
            for ((path, source) in Shaders.files(v, showInput, antiAlias, box)) text(path, source)
        }

        return files
    }

    /** The pack as a zip with `pack.mcmeta` at its root. Deterministic: same input gives the same bytes (SHA-1). */
    fun zip(files: Map<String, ByteArray> = build()): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((path, bytes) in files.toSortedMap()) {
                zip.putNextEntry(ZipEntry(path).apply { time = FIXED_TIME })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Replaces the contents of [dir] with the generated pack. */
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun writeTo(dir: Path, files: Map<String, ByteArray> = build()) {
        if (dir.exists()) dir.deleteRecursively()
        for ((path, bytes) in files) {
            val target = dir.resolve(path)
            target.parent.createDirectories()
            target.writeBytes(bytes)
        }
    }

    private const val FIXED_TIME = 1_700_000_000_000L

}
