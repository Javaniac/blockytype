package com.javaniac.blockytype.pack

import com.javaniac.blockytype.ui.Element
import com.javaniac.blockytype.ui.ResultGraph
import com.javaniac.blockytype.ui.ResultLayout
import com.javaniac.blockytype.ui.UiFont

/** Client versions the pack supports. Shader syntax differs between them. */
enum class ClientVersion(val label: String, val assetsDir: String, val packFormat: Int) {
    /** Base `assets/`, pack format 88: `#moj_import`, no layout qualifiers. */
    V26_2("26.2", "assets", 88),

    /** `overlay_26_3/`, pack format 97: SPIR-V "renderpearl" renderer, `#include`, layout locations. */
    V26_3("26.3", "overlay_26_3/assets", 97),
}

/**
 * Core shader overrides. Every override starts from that version's vanilla file.
 * See CLAUDE.md "Rendering plan" and "Hiding the vanilla command block screen" for the rules.
 */
object Shaders {
    /** Alpha of BlockyType's own button sprites; `position_tex_color.fsh` discards these in the button rectangles. */
    const val MARKER_ALPHA = 254

    /** `Globals.GameTime` below this means the keybind probe runs (see `Stopwatch.PROBE_TICKS`): hide all vanilla text. */
    const val PROBE_GAME_TIME = "-0.25"

    private fun isOld(v: ClientVersion) = v == ClientVersion.V26_2

    private fun dyn(v: ClientVersion): String {
        val order = if (isOld(v)) {
            listOf("mat4 ModelViewMat;", "vec4 ColorModulator;", "vec3 ModelOffset;", "mat4 TextureMat;")
        } else {
            listOf("mat4 ModelViewMat;", "mat4 TextureMat;", "vec4 ColorModulator;", "vec3 ModelOffset;")
        }
        return "layout(std140) uniform DynamicTransforms {\n" + order.joinToString("") { "    $it\n" } + "};"
    }

    private const val PROJ = "layout(std140) uniform Projection {\n    mat4 ProjMat;\n};"

    private fun header(v: ClientVersion) =
        "#version 330\n" + if (isOld(v)) "" else "#extension GL_ARB_separate_shader_objects : require\n"

    private fun loc(v: ClientVersion, n: Int) = if (isOld(v)) "" else "layout(location = $n) "

    private fun imp(v: ClientVersion, name: String) =
        (if (isOld(v)) "#moj_import" else "#include") + " <minecraft:$name.glsl>"

    private const val BUTTON_AREA = """// Command block screen button rectangles, in GUI units (dx = x - width/2):
//   output toggle dx 130..150,    y 135..155
//   mode buttons  dx -154..154,   y 165..185
//   done / cancel dx -154..-4 / 4..154, y height/4+132 .. height/4+152
bool isCommandBlockButtonArea(float dx, float y, float guiHeight) {
    float doneY = floor(guiHeight / 4.0) + 132.0;
    if (y >= 134.0 && y <= 156.0 && dx >= 128.0 && dx <= 152.0) return true;
    if (y >= 164.0 && y <= 186.0 && abs(dx) <= 155.0) return true;
    if (y >= doneY - 1.0 && y <= doneY + 21.0 && abs(dx) <= 155.0) return true;
    return false;
}
"""

    /** Relative pack path (inside the version's assets dir) to file content, for every shader of [v]. */
    fun files(v: ClientVersion, showInput: Boolean = true, antiAlias: Boolean = true, box: BoxColors = BoxColors.DEFAULT): Map<String, String> {
        val dir = v.assetsDir + "/minecraft/shaders/core/"
        return linkedMapOf(
            dir + "text.vsh" to textVsh(v, showInput, box),
            dir + "text.fsh" to textFsh(v, antiAlias),
            dir + "gui.vsh" to guiVsh(v, showInput),
            dir + "position_tex_color.vsh" to ptcVsh(v),
            dir + "position_tex_color.fsh" to ptcFsh(v),
        )
    }

    private fun textVsh(v: ClientVersion, showInput: Boolean, box: BoxColors): String = """${header(v)}
// BlockyType override of vanilla ${v.label} core/text.vsh (pack format ${v.packFormat}).
// Adds: hides the vanilla command block screen text (typed command, append cursor,
// suggestion ghost text, syntax highlighting, usage hint, previous output,
// button labels) by making those vertices transparent.

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
${imp(v, "fog")}
${imp(v, "sample_lightmap")}
#endif

${imp(v, "dynamictransforms")}
${imp(v, "projection")}
${imp(v, "globals")}

${loc(v, 0)}in vec3 Position;
${loc(v, 1)}in vec4 Color;
${loc(v, 2)}in vec2 UV0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
${loc(v, 3)}in ivec2 UV2;
#endif

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
uniform sampler2D Sampler2;
${loc(v, 0)}out float sphericalVertexDistance;
${loc(v, 1)}out float cylindricalVertexDistance;
#endif

${loc(v, 2)}out vec4 vertexColor;
${loc(v, 3)}out vec2 texCoord0;

#ifdef IS_GUI
// True when the RGB color equals `value` (0-255 per channel), within rounding.
bool isColor(vec3 rgb, vec3 value) {
    return all(lessThan(abs(rgb * 255.0 - value), vec3(1.5)));
}

// Text drawn with a drop shadow also emits a copy at color * 0.25.
bool isColorOrShadow(vec3 rgb, vec3 value) {
    return isColor(rgb, value) || isColor(rgb, floor(value * 0.25));
}

// Colors CommandSuggestions uses: GRAY literal/usage, AQUA/YELLOW/GREEN/LIGHT_PURPLE/GOLD
// arguments, RED unparsed / errors.
bool isCommandSyntaxColor(vec3 rgb) {
    return isColorOrShadow(rgb, vec3(170.0))
        || isColorOrShadow(rgb, vec3(85.0, 255.0, 255.0))
        || isColorOrShadow(rgb, vec3(255.0, 255.0, 85.0))
        || isColorOrShadow(rgb, vec3(85.0, 255.0, 85.0))
        || isColorOrShadow(rgb, vec3(255.0, 85.0, 255.0))
        || isColorOrShadow(rgb, vec3(255.0, 170.0, 0.0))
        || isColorOrShadow(rgb, vec3(255.0, 85.0, 85.0));
}

// Label-sized rectangles of the command block screen buttons (labels are centered, drawn
// at button y + 6). Kept tight so same-colored labels in other menus are not hit.
//   Done   centered at dx -79, Cancel centered at dx +79, y height/4+138
//   O / X  centered at dx 140, y 141
bool isCommandBlockLabelArea(float dx, float y, float guiHeight) {
    float labelY = floor(guiHeight / 4.0) + 138.0;
    if (y >= labelY - 1.0 && y <= labelY + 10.0 && dx >= -100.0 && dx <= -58.0) return true;
    if (y >= labelY - 1.0 && y <= labelY + 10.0 && dx >= 55.0 && dx <= 103.0) return true;
    if (y >= 140.0 && y <= 151.0 && dx >= 132.0 && dx <= 148.0) return true;
    return false;
}

// Vanilla AbstractCommandBlockEditScreen layout, in GUI units (dx = x - width/2):
//   command box   dx -150..150, y 50..70
//   usage hint    one line at y 74 (fill 71..84), starting at the argument column
//                 (dx about -132 for "/t ", further left when the box scrolls), GRAY
//   previous box  dx -150..126, y 135..155
bool isCommandBlockScreenText(vec3 guiPos, vec3 rgb) {
    float guiWidth = 2.0 / ProjMat[0][0];
    float guiHeight = 2.0 / abs(ProjMat[1][1]);
    float dx = guiPos.x - guiWidth * 0.5;
    float y = guiPos.y;

${if (showInput) "" else """    // Command box: plain text (0xE0E0E0), suggestion ghost text (0x808080), syntax highlighting.
    if (y >= 49.0 && y <= 71.0 && abs(dx) <= 152.0) {
        return isColorOrShadow(rgb, vec3(224.0))
            || isColorOrShadow(rgb, vec3(128.0))
            || isCommandSyntaxColor(rgb);
    }
"""}    // Usage hint (e.g. "<text>"): CommandSuggestions.USAGE_FORMAT = GRAY 0xAAAAAA.
    // Assumes the /t argument name is short (usage ends left of dx -40).
    if (y >= 72.0 && y <= 86.0 && dx <= -40.0) {
        return isColorOrShadow(rgb, vec3(170.0));
    }
    // Button labels (Done, Cancel, O/X): white when active, 0xA0A0A0 when inactive.
    if (isCommandBlockLabelArea(dx, y, guiHeight)) {
        return isColorOrShadow(rgb, vec3(255.0)) || isColorOrShadow(rgb, vec3(160.0));
    }
    // Previous output, read-only color 0xA0A0A0.
    if (y >= 134.0 && y <= 156.0 && abs(dx) <= 152.0) {
        return isColorOrShadow(rgb, vec3(160.0));
    }
    return false;
}

${if (!showInput) "" else """// Visible input: the command box text keeps its place but loses the syntax highlighting colors.
bool isInputBoxText(vec3 guiPos, vec3 rgb) {
    float guiWidth = 2.0 / ProjMat[0][0];
    float dx = guiPos.x - guiWidth * 0.5;
    float y = guiPos.y;
    if (y < 49.0 || y > 71.0 || abs(dx) > 152.0) return false;
    return isColorOrShadow(rgb, vec3(224.0))
        || isColorOrShadow(rgb, vec3(128.0))
        || isCommandSyntaxColor(rgb);
}

// Text colors are bright, their drop shadows are drawn at a quarter of that.
vec3 inputTextColor(vec3 rgb) {
    vec3 text = vec3(${box.text shr 16 and 255}.0 / 255.0, ${box.text shr 8 and 255}.0 / 255.0, ${box.text and 255}.0 / 255.0);
    return max(rgb.r, max(rgb.g, rgb.b)) > 0.4 ? text : text * 0.25;
}
"""}
// ---------------------------------------------------------------- BlockyType HUD
// The game UI is the title of one boss bar. The server makes the title 0 wide (trailing
// negative space), so it starts exactly at (floor(guiWidth / 2), 3). Every element is a
// segment that starts at that origin. Its glyphs carry a marker in the color low bits:
//   R & 7 == 3, element id = (G & 3) << 2 | (B & 3)
// No vanilla text color or shadow has R & 7 == 3 (0x00 0x15 0x2A 0x3F 0x55 0xAA 0xE0 0xFF ...).
// The shader moves each element to its anchor. Sizes stay in GUI units (follow GUI scale).
const float HUD_TITLE_Y = 3.0;

// Caret slide. A caret glyph has R & 8 set; G & 4 = animate, B & 4 = it came from the left (else the right).
// The server resets the client game time (SetTimePacket) on every caret move, so GameTime * 24000 is the
// number of ticks since then, advancing smoothly every frame (partial ticks included).
const float HUD_CARET_SLIDE = 6.0;  // GUI units: one mono char
const float HUD_CARET_TICKS = 3.0;  // slide duration

float hudCaretOffset(vec3 rgb) {
    ivec3 c = ivec3(round(rgb * 255.0));
    if ((c.r & 7) != 3 || (c.r & 8) == 0 || (c.g & 4) == 0) return 0.0;
    float t = clamp(GameTime * 24000.0 / HUD_CARET_TICKS, 0.0, 1.0);
    float ease = 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t);
    float dir = (c.b & 4) != 0 ? -1.0 : 1.0;
    return dir * HUD_CARET_SLIDE * (1.0 - ease);
}

// Element id 0..31 (ids 16..31 have the page bit G & 4 set), or -1 when the color is no element marker.
int hudElementId(vec3 rgb) {
    ivec3 c = ivec3(round(rgb * 255.0));
    if ((c.r & 7) != 3) return -1;
    int id = ((c.g & 3) << 2) | (c.b & 3);
    if ((c.r & 8) == 0 && (c.g & 4) != 0) id = id + 16;
    return id;
}

// Anchor (GUI units) of element `id`; segment-local coordinates are added to it.
vec2 hudAnchor(int id, float gw, float gh) {
    float cx = floor(gw * 0.5);
    float cy = floor(gh * 0.5);
    if (id == 1) return vec2(cx, 14.0);                              // top config bar
    if (id == 2) return vec2(cx, cy - 46.0);                         // live stats
    if (id >= 3 && id <= 5) return vec2(cx, cy - 26.0 + 16.0 * float(id - 3)); // text lines
    if (id == ${Element.RESULT_ERRORS}) return vec2(cx, cy + (${ResultLayout.ERRORS_DY}.0));     // result: error markers
    if (id == 7) return vec2(cx, gh - 20.0);                         // footer hints
    if (id == 8) return vec2(cx, cy - 82.0);                         // result: big numbers
    if (id == 9) return vec2(cx, cy + (${ResultGraph.TOP}.0));                 // result: graph top (and y max label)
    if (id == ${Element.RESULT_STATS_LABEL}) return vec2(cx, cy + (${ResultLayout.STATS_LABEL_DY}.0)); // result: stat labels
    if (id == 11) return vec2(cx, cy - 112.0);                       // result: big number labels
    if (id == ${Element.RESULT_STATS}) return vec2(cx, cy + (${ResultLayout.STATS_VALUE_DY}.0));   // result: stat values
    if (id == ${Element.RESULT_X_LABELS}) return vec2(cx, cy + (${ResultLayout.X_LABELS_DY}.0));    // result: x axis labels
    if (id == ${Element.RESULT_Y_MID}) return vec2(cx, cy + (${ResultLayout.Y_MID_DY}.0));        // result: y mid label
    if (id == ${Element.RESULT_Y_ZERO}) return vec2(cx, cy + (${ResultLayout.Y_ZERO_DY}.0));       // result: y zero label (the zero line sits MARGIN above the frame bottom)
    // Menus (ids 16..31), relative to the screen center.
    if (id == ${UiFont.MENU_HEADER_ID}) return vec2(cx, cy + (${UiFont.MENU_HEADER_DY}.0));
    if (id >= ${UiFont.MENU_ROW_FIRST_ID} && id <= ${UiFont.MENU_ROW_LAST_ID}) return vec2(cx, cy + (${UiFont.MENU_ROW0_DY}.0) + ${UiFont.MENU_ROW_PITCH}.0 * float(id - ${UiFont.MENU_ROW_FIRST_ID}));
    if (id == ${UiFont.MENU_FOOTER_ID}) return vec2(cx, cy + (${UiFont.MENU_FOOTER_DY}.0));
    // Keybind hints: segment origin at the right screen edge, the text is placed to the left of it.
    if (id >= ${UiFont.SIDE_FIRST_ID} && id < ${UiFont.SIDE_FIRST_ID + UiFont.SIDE_ROWS}) return vec2(gw - ${UiFont.SIDE_MARGIN}.0, ${UiFont.SIDE_TOP}.0 + ${UiFont.SIDE_ROW_PITCH}.0 * float(id - ${UiFont.SIDE_FIRST_ID}));
    return vec2(cx, HUD_TITLE_Y);
}
#endif

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
#else
    vertexColor = Color;
#endif

#ifdef IS_GUI
    vec3 guiPos = (ModelViewMat * vec4(Position, 1.0)).xyz;
    int hudId = hudElementId(Color.rgb);
    if (hudId >= 0 && guiPos.y > -96.0 && guiPos.y < 96.0) {
        float gw = round(2.0 / ProjMat[0][0]);
        float gh = round(2.0 / abs(ProjMat[1][1]));
        vec2 local = guiPos.xy - vec2(floor(gw * 0.5), HUD_TITLE_Y);
        vec2 p;
        if (hudId == 0) {
            // Background: the 8x8 square at local (0..8, 0..8) becomes the whole screen.
            // Overshoots by 8 units: gw/gh are rounded, the window may not be a multiple of the scale.
            p = vec2(local.x > 4.0 ? gw + 8.0 : -8.0, local.y > 4.0 ? gh + 8.0 : -8.0);
        } else {
            p = hudAnchor(hudId, gw, gh) + local;
        }
        p.x += hudCaretOffset(Color.rgb);
        gl_Position = ProjMat * vec4(p, guiPos.z, 1.0);
    } else if (GameTime < $PROBE_GAME_TIME) {
        vertexColor.a = 0.0; // keybind probe: the anvil's title, labels and name box text
${if (showInput) """    } else if (isInputBoxText(guiPos, Color.rgb)) {
        vertexColor.rgb = inputTextColor(Color.rgb);
""" else ""}    } else if (isCommandBlockScreenText(guiPos, Color.rgb)) {
        vertexColor.a = 0.0; // text.fsh discards alpha < 0.1
    }
#endif

    texCoord0 = UV0;
}
"""

    /**
     * Vanilla text.fsh plus a hand-written box filter for HUD glyphs (marker `R & 7 == 3`).
     * The font atlas sampler is NEAREST and the glyph quads are scaled by guiScale / oversample, so the
     * default look is jagged. The filter integrates the pixel footprint over the atlas texels
     * (`texelFetch`), which is exact area sampling when minifying and bilinear when magnifying.
     */
    private fun textFsh(v: ClientVersion, antiAlias: Boolean): String {
        val old = isOld(v)
        return """${header(v)}
// BlockyType override of vanilla ${v.label} core/text.fsh (pack format ${v.packFormat}).
// Adds: area-filtered (anti-aliased) sampling for HUD glyphs.

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
${imp(v, "fog")}
#endif

${imp(v, "dynamictransforms")}
${if (old) "" else imp(v, "oit")}

uniform sampler2D Sampler0;

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
${loc(v, 0)}in float sphericalVertexDistance;
${loc(v, 1)}in float cylindricalVertexDistance;
#endif

${loc(v, 2)}in vec4 vertexColor;
${loc(v, 3)}in vec2 texCoord0;

${if (old) "out vec4 fragColor;" else "#ifndef OIT_ALPHA_ONLY\n${loc(v, 0)}out vec4 fragColor;\n#endif"}

// Glyphs drawn bigger than the atlas (stretched background square, 1 px caret, other bitmaps: less than one
// texel per screen pixel) keep the vanilla nearest sampling; only minified font glyphs are filtered.
// A footprint this large is never reached, so with anti-aliasing off the filter never runs.
const float MIN_FILTER_FOOTPRINT = ${if (antiAlias) "0.9" else "1000000.0"};

vec4 fetchTexel(ivec2 p) {
#ifdef IS_GRAYSCALE
    return texelFetch(Sampler0, p, 0).rrrr;
#else
    return texelFetch(Sampler0, p, 0);
#endif
}

// Average of the atlas over the pixel's footprint (at least one texel wide, so magnification is bilinear).
// `footprint` = size of the pixel in texels; must come from derivatives taken in uniform control flow.
vec4 areaSample(vec2 uv, vec2 footprint) {
    ivec2 size = textureSize(Sampler0, 0);
    vec2 p = uv * vec2(size);
    vec2 w = max(footprint, vec2(1.0));
    vec2 lo = p - w * 0.5;
    vec2 hi = p + w * 0.5;
    ivec2 first = ivec2(floor(lo));
    ivec2 last = min(ivec2(ceil(hi)) - 1, first + 7); // at most 8 x 8 texels
    vec4 sum = vec4(0.0);
    for (int y = first.y; y <= last.y; y++) {
        float wy = min(hi.y, float(y + 1)) - max(lo.y, float(y));
        for (int x = first.x; x <= last.x; x++) {
            float wx = min(hi.x, float(x + 1)) - max(lo.x, float(x));
            sum += fetchTexel(clamp(ivec2(x, y), ivec2(0), size - 1)) * (wx * wy);
        }
    }
    return sum / (w.x * w.y);
}

${if (old) "" else """vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    #endif

    #if !defined(IS_SEE_THROUGH) && !defined(IS_GUI)

    #ifdef OIT_ACCUMULATE
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif

    color = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
    #endif

    return color;
}
"""}
void main() {
    // Derivatives first, outside any branch.
    vec2 sizeF = vec2(textureSize(Sampler0, 0));
    vec2 dx = dFdx(texCoord0) * sizeF;
    vec2 dy = dFdy(texCoord0) * sizeF;
    vec2 footprint = vec2(length(vec2(dx.x, dy.x)), length(vec2(dx.y, dy.y)));
    float maxFootprint = max(footprint.x, footprint.y);

#ifdef IS_GUI
    bool hudGlyph = (int(round(vertexColor.r * 255.0)) & 7) == 3 && maxFootprint >= MIN_FILTER_FOOTPRINT;
#else
    bool hudGlyph = false;
#endif

    vec4 texColor;
    if (hudGlyph) {
        texColor = areaSample(texCoord0, footprint);
    } else {
#ifdef IS_GRAYSCALE
        texColor = texture(Sampler0, texCoord0).rrrr;
#else
        texColor = texture(Sampler0, texCoord0);
#endif
    }

${if (old) """#ifdef IS_SEE_THROUGH
    vec4 color = texColor * vertexColor;
#else
    vec4 color = texColor * vertexColor * ColorModulator;
#endif
    if (color.a < 0.1) {
        discard;
    }

#ifdef IS_SEE_THROUGH
    fragColor = color * ColorModulator;
#elif defined(IS_GUI)
    fragColor = color;
#else
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
#endif""" else """    vec4 color = texColor * vertexColor * ColorModulator;

    if (color.a < 0.1) {
        discard;
    }

#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#else
    fragColor = calculateFinalColor(color);
#endif"""}
}
"""
    }

    private fun guiVsh(v: ClientVersion, showInput: Boolean): String = """${header(v)}
// BlockyType override of vanilla ${v.label} core/gui.vsh (pack format ${v.packFormat}).
// Adds: hides the in-game screen dim gradient, the command box insert cursor and the
// command usage hint background.

// Can't moj_import in things used during startup, when resource packs don't exist.
// This is a copy of dynamicimports.glsl and projection.glsl
${dyn(v)}
$PROJ

${loc(v, 0)}in vec3 Position;
${loc(v, 1)}in vec4 Color;

${loc(v, 0)}out vec4 vertexColor;

bool isRgba(vec4 color, vec4 rgba255) {
    return all(lessThan(abs(color * 255.0 - rgba255), vec4(1.5)));
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;

    vec3 guiPos = (ModelViewMat * vec4(Position, 1.0)).xyz;
    float dx = guiPos.x - (2.0 / ProjMat[0][0]) * 0.5;
    float y = guiPos.y;

    // Screen.extractTransparentBackground: fillGradient(0xC0101010 -> 0xD0101010).
    if (isRgba(Color, vec4(16.0, 16.0, 16.0, 192.0)) || isRgba(Color, vec4(16.0, 16.0, 16.0, 208.0))) {
        vertexColor.a = 0.0; // gui.fsh discards alpha == 0
    }

${if (showInput) "" else """    // Insert cursor (TextCursorUtils.extractInsertCursor) inside the command box: fill in 0xFFE0E0E0.
    if (abs(dx) <= 152.0 && y >= 49.0 && y <= 71.0 && isRgba(Color, vec4(224.0, 224.0, 224.0, 255.0))) {
        vertexColor.a = 0.0;
    }
"""}
    // Anvil name box cursor (the keybind probe's invisible screen): AnvilScreen is 176 x 166, centered, and its
    // name box sits at (62, 24) with 103 x 12. The insert cursor is a white fill.
    float anvilCy = floor((2.0 / abs(ProjMat[1][1])) * 0.5);
    if (dx >= -32.0 && dx <= 84.0 && y >= anvilCy - 63.0 && y <= anvilCy - 44.0 && isRgba(Color, vec4(255.0, 255.0, 255.0, 255.0))) {
        vertexColor.a = 0.0;
    }

    // CommandSuggestions usage hint background (one line, y 71..84) below the command box:
    // fill in fillColor 0x80000000 (Integer.MIN_VALUE). Same short-argument-name assumption
    // as text.vsh.
    if (y >= 70.0 && y <= 85.0 && dx <= -40.0 && isRgba(Color, vec4(0.0, 0.0, 0.0, 128.0))) {
        vertexColor.a = 0.0;
    }
}
"""

    private fun ptcVsh(v: ClientVersion): String = """${header(v)}
// BlockyType override of vanilla ${v.label} core/position_tex_color.vsh (pack format ${v.packFormat}).
// Adds: flags vertices inside the command block screen button rectangles.

// Can't moj_import in things used during startup, when resource packs don't exist.
// This is a copy of dynamicimports.glsl and projection.glsl
${dyn(v)}
$PROJ

${loc(v, 0)}in vec3 Position;
${loc(v, 1)}in vec2 UV0;
${loc(v, 2)}in vec4 Color;

${loc(v, 0)}out vec2 texCoord0;
${loc(v, 1)}out vec4 vertexColor;
${loc(v, 2)}out float buttonArea;

$BUTTON_AREA
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
    vertexColor = Color;

    vec3 guiPos = (ModelViewMat * vec4(Position, 1.0)).xyz;
    float guiWidth = 2.0 / ProjMat[0][0];
    float guiHeight = 2.0 / abs(ProjMat[1][1]);
    buttonArea = isCommandBlockButtonArea(guiPos.x - guiWidth * 0.5, guiPos.y, guiHeight) ? 1.0 : 0.0;
}
"""

    private fun ptcFsh(v: ClientVersion): String = """${header(v)}
// BlockyType override of vanilla ${v.label} core/position_tex_color.fsh (pack format ${v.packFormat}).
// Adds: discards marker button sprites (alpha $MARKER_ALPHA) inside the command block button rectangles.

// Can't moj_import in things used during startup, when resource packs don't exist.
// This is a copy of dynamicimports.glsl
${dyn(v)}

uniform sampler2D Sampler0;

${loc(v, 0)}in vec2 texCoord0;
${loc(v, 1)}in vec4 vertexColor;
${loc(v, 2)}in float buttonArea;

${loc(v, 0)}out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    // BlockyType button sprites use alpha $MARKER_ALPHA as a marker. Drop them inside the
    // command block screen button rectangles only.
    if (buttonArea > 0.99 && abs(texel.a * 255.0 - $MARKER_ALPHA.0) < 0.5) {
        discard;
    }
    vec4 color = texel * vertexColor;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
"""
}
