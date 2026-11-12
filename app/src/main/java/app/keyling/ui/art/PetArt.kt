package app.keyling.ui.art

import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** How a resident looks: an outline + five-tone body ramp, a belly ramp and one accessory. */
/** [shape]: round · egg (tall) · eraser (a rubber block in a paper sleeve) · long (a stretched Tab) · owl (big eyes) · drop (a ghost on the run). */
class Look(val r: IntArray, val belly: IntArray, val acc: String, val shape: String = "round")

object Looks {
    val of = mapOf(
        "space" to Look(rgbs(0x3a2230, 0x9c4a26, 0xc96a32, 0xec8f45, 0xffb866, 0xffe0a0), rgbs(0xe9b98a, 0xf7d3a0, 0xfff0d0), "ears"),
        "enter" to Look(rgbs(0x1e2440, 0x3b5f9a, 0x5584c4, 0x7fb0e6, 0xa9d2f7, 0xe0f2ff), rgbs(0xa9c8e8, 0xcfe6fa, 0xf0f8ff), "antenna", "egg"),
        "bksp" to Look(rgbs(0x3a1824, 0x9a3040, 0xc84a58, 0xe86a6a, 0xff9e98, 0xffd6cc), rgbs(0xe9a8a0, 0xf7c6c0, 0xfff0ea), "horns", "eraser"),
        "caps" to Look(rgbs(0x3a2a10, 0xa07818, 0xc99a28, 0xefc23e, 0xffe07a, 0xfff6c8), rgbs(0xe8cf88, 0xf6e6b0, 0xfff8e0), "tuft"),
        "tab" to Look(rgbs(0x16301c, 0x2f6a38, 0x4a9450, 0x74b866, 0xa6dc8e, 0xe0f8c8), rgbs(0xb8dca8, 0xd8f0c8, 0xf0fbe8), "sprout", "long"),
        "shift" to Look(rgbs(0x1e1838, 0x463c8a, 0x6457b0, 0x8a7fd1, 0xb5abf2, 0xe6e0ff), rgbs(0xc8c0ee, 0xe0daf8, 0xf4f0ff), "cap", "owl"),
        "esc" to Look(rgbs(0x20262e, 0x4f5a66, 0x6b7682, 0x9aa5b1, 0xc4ccd6, 0xeef2f6), rgbs(0xc8d0d8, 0xe0e6ec, 0xf6f8fa), "scarf", "drop"),
    )
}

private val SILH = rgbs(0x1c1418, 0x2a2026, 0x352a31, 0x3e3139, 0x463840)
private val EYE = rgb(0x22141e); private val WHITE = rgb(0xffffff); private val GLINT = rgb(0x8a6aa0)
private val BLUSH = rgb(0xff8a8a); private val MOUTH = rgb(0x3a1424); private val TONGUE = rgb(0xff7088)

/** A round furry critter lit from the top left, with its accessory and a face. */
fun Buf.pet(cx: Float, cy: Float, rx: Float, ry: Float, look: Look, silh: Boolean = false, blink: Boolean = false,
            mouth: Boolean = false, clipY: Int = Int.MAX_VALUE) {
    val R = look.r; val o = if (silh) rgb(0x120c0e) else R[0]
    val ramp = if (silh) SILH else R.copyOfRange(1, 6)
    fun P(x: Int, y: Int, c: Int) { if (y < clipY) p(x, y, c) }
    fun fur(x: Int, y: Int) = if ((x * 13 + y * 7) % 5 == 0) .12f else 0f
    val shape = look.shape
    val rx = when (shape) { "egg" -> rx * .82f; "long" -> rx * 1.3f; else -> rx }
    val ry = when (shape) { "egg" -> ry * 1.18f; "long" -> ry * .78f; else -> ry }
    fun ins(x: Int, y: Int): Boolean {
        val nx = (x + .5f - cx) / rx; val ny = (y + .5f - cy) / ry
        return when (shape) {
            "eraser" -> nx * nx * nx * nx + ny * ny * ny * ny <= 1f
            "egg" -> { val k = nx / (1f - .18f * ny); k * k + ny * ny <= 1 + (if (ny < .2f) fur(x, y) else 0f) }
            "drop" -> if (ny > .55f) nx * nx + .55f * .55f <= 1f && ny < .75f + .18f * kotlin.math.sin(nx * 9f) else nx * nx + ny * ny <= 1f
            else -> nx * nx + ny * ny <= 1 + (if (ny < .2f && shape != "owl") fur(x, y) else 0f)
        }
    }
    val acc = look.acc
    when (acc) {
        "ears" -> for (s in intArrayOf(-1, 1)) {
            blob(cx + s * rx * .62f, cy - ry * .82f, rx * .26f, ry * .36f, if (silh) ramp else R.copyOfRange(2, 6), o, clipY)
            if (!silh) blob(cx + s * rx * .62f, cy - ry * .8f, rx * .12f, ry * .18f, rgbs(0xff9a9a, 0xffc0b8), null, clipY)
        }
        "horns" -> for (s in intArrayOf(-1, 1)) {
            for (i in 0 until 5) { val x = (cx + s * (rx * .38f + i * .6f)).roundToInt(); val y = (cy - ry * .82f - i).roundToInt()
                P(x - 1, y, o); P(x, y, if (silh) ramp[3] else if (i > 2) rgb(0xfff4e0) else rgb(0xe8d8b8)); P(x + 1, y, o) }
            P((cx + s * (rx * .38f + 3)).roundToInt(), (cy - ry * .82f - 5).roundToInt(), o)
        }
        "antenna" -> { for (i in 0 until 7) P((cx + i * .3f).roundToInt(), (cy - ry - i).roundToInt(), o)
            blob(cx + 2.5f, cy - ry - 8, 2.6f, 2.6f, if (silh) ramp else rgbs(0xffd060, 0xfff3a0, 0xffffff), o, clipY) }
        "tuft" -> for ((dx, l) in listOf(-3 to 5, 0 to 7, 3 to 5)) for (i in 0 until l) {
            val x = (cx + dx + dx * i * .15f).roundToInt(); val y = (cy - ry + 1 - i).roundToInt()
            P(x, y, if (i == l - 1) o else if (silh) ramp[3] else R[4]); P(x + 1, y, o)
        }
        "sprout" -> { for (i in 0 until 6) P(cx.roundToInt(), (cy - ry - i).roundToInt(), if (silh) ramp[2] else rgb(0x3d8438))
            val lf = rgbs(0x3d8438, 0x62ad4c, 0x97d670)
            blob(cx - 3, cy - ry - 5, 2.5f, 1.5f, if (silh) ramp else lf, o, clipY); blob(cx + 3, cy - ry - 6, 2.5f, 1.5f, if (silh) ramp else lf, o, clipY) }
    }
    for (y in floor(cy - ry - 2).toInt()..(cy + ry + 1).toInt()) for (x in floor(cx - rx - 2).toInt()..(cx + rx + 1).toInt()) {
        if (!ins(x, y)) continue
        if (!ins(x - 1, y) || !ins(x + 1, y) || !ins(x, y - 1) || !ins(x, y + 1)) { P(x, y, o); continue }
        val nx = (x + .5f - cx) / rx; val ny = (y + .5f - cy) / ry; val z = sqrt((1 - nx * nx - ny * ny).coerceAtLeast(0f))
        val v = .42f - nx * .3f - ny * .36f + z * .3f
        if (!silh) {
            val bx = (x + .5f - cx) / (rx * .6f); val by = (y + .5f - (cy + ry * .42f)) / (ry * .5f)
            if (bx * bx + by * by < 1) { P(x, y, pick(look.belly, v + .15f, x, y)); continue }
        }
        val fs = if (!silh && (x * 7 + y * 3) % 11 == 0 && ny < .4f) -.18f else 0f
        P(x, y, pick(ramp, v + fs, x, y))
    }
    if (!silh && shape == "eraser") { // a blue paper sleeve round the middle, like a school eraser
        val top = (cy + ry * .15f).roundToInt(); val bot = (cy + ry * .62f).roundToInt()
        for (y in top..bot) for (x in floor(cx - rx - 1).toInt()..(cx + rx + 1).toInt()) if (ins(x, y) && ins(x - 1, y) && ins(x + 1, y))
            P(x, y, pick(rgbs(0x1f3f7a, 0x2f5aa8, 0x4f7ad0, 0x8fb0f0), .7f - (x - cx) / rx * .35f - (y - top).toFloat() / (bot - top + 1) * .3f, x, y))
        for (x in floor(cx - rx).toInt()..(cx + rx).toInt()) if (ins(x, top)) P(x, top, rgb(0xd8e4ff))
    }
    if (silh) {
        val q = Glyphs.text("?", rgb(0x6a5560))
        blit(q, (cx - 3).roundToInt(), (cy - 4).roundToInt(), clipY = clipY); return
    }
    if (acc == "cap") {
        val capR = rgbs(0x2c2458, 0x3d3570, 0x544a96)
        for (j in 0 until 7) { var i = -rx * .75f + j * 1.2f
            while (i < rx * .6f - j * .4f) { val x = (cx + i).roundToInt(); P(x, (cy - ry + 3 - j).roundToInt(), if (j == 0) rgb(0xf3e6d8) else pick(capR, .6f - j * .05f, x, j)); i += 1f } }
        blob(cx + rx * .55f, cy - ry - 3, 2.2f, 2.2f, rgbs(0xe8e0d0, 0xffffff), rgb(0x2c2458), clipY)
    }
    val ex = round(rx * .36f).toInt(); val ey = round(cy - ry * .12f).toInt(); val ew = if (rx > 12) 3 else 2; val eh = if (rx > 12) 4 else 3
    val icx = cx.roundToInt()
    if (shape == "owl" && !blink) { // big round night eyes with amber rims
        for (sx in intArrayOf(icx - ex - 1, icx + ex + 1)) { blob(sx.toFloat(), ey + 1.5f, 3.6f, 3.6f, rgbs(0xe8a838, 0xffd36b, 0xfff0b0), R[0], clipY)
            for (j in 0 until 3) for (i in 0 until 2) P(sx - 1 + i, ey + j, EYE); P(sx - 1, ey, WHITE) }
        val my = ey + 6; P(icx, my, rgb(0xe8a838)); P(icx - 1, my - 1, rgb(0xe8a838)); P(icx + 1, my - 1, rgb(0xe8a838))
        return
    }
    fun eye(x0: Int) {
        if (blink) { P(x0, ey + eh - 2, R[0]); for (i in 1 until ew) P(x0 + i, ey + eh - 1, R[0]); P(x0 + ew - 1, ey + eh - 2, R[0]); return }
        for (j in 0 until eh) for (i in 0 until ew) P(x0 + i, ey + j, EYE)
        P(x0, ey, WHITE); if (ew > 2) P(x0 + 1, ey, WHITE); P(x0 + ew - 1, ey + eh - 1, GLINT)
    }
    eye(icx - ex - ew + 1); eye(icx + ex)
    if (rx > 9) for (j in 0 until 2) for (i in 0 until 4) if ((i + j) % 2 == 0) { P(icx - ex - ew - 3 + i, ey + eh + 1 + j, BLUSH); P(icx + ex + ew - 1 + i, ey + eh + 1 + j, BLUSH) }
    val my = ey + eh + 1
    if (mouth) { for (j in 0 until 3) for (i in -2 until 2) P(icx + i, my + j, MOUTH); P(icx - 1, my + 2, TONGUE); P(icx, my + 2, TONGUE) }
    else { P(icx - 2, my, R[0]); P(icx - 1, my + 1, R[0]); P(icx, my, R[0]); P(icx + 1, my + 1, R[0]); P(icx + 2, my, R[0]) }
    if (acc == "scarf") {
        var i = -rx * .7f
        while (i < rx * .7f) { val x = (cx + i).roundToInt(); val red = (floor(i / 2).toInt() % 2) != 0
            P(x, (cy + ry * .45f).roundToInt(), if (red) rgb(0xc8553d) else rgb(0xf3e6d8)); P(x, (cy + ry * .45f).roundToInt() + 1, if (red) rgb(0x933a28) else rgb(0xd8c8b8)); i += 1f }
        for (j in 0 until 5) P((cx + rx * .3f).roundToInt(), (cy + ry * .45f + 2 + j).roundToInt(), if (j % 2 == 1) rgb(0xc8553d) else rgb(0xf3e6d8))
    }
}

/**
 * A resident's portrait in its nook at growth [stage], for cards and sheets; [size] is the sprite cut (Critters.BIG …)
 * as it would be at one device pixel per art pixel: drawn [s] times finer, with the cut [s] times bigger.
 */
fun portrait(id: String, open: Boolean, stage: Int = 0, w: Int = 64, h: Int = 52, size: Int = Critters.BIG, s: Int = Desk.S): Buf {
    val b = Buf(w, h, s)
    val bg = rgbs(0x140c0e, 0x1e1316, 0x2a1a1c, 0x3e2618)
    b.paint(0, 0, w, h) { fx, fy, X, Y -> val v = 1 - kotlin.math.hypot((fx - w / 2f) / (w * .62f), (fy - h - 4f) / (h * .65f)); if (s > 1) lerp(bg, v) else pick(bg, v, X, Y) }
    val cut = Critters.cut(size, s)
    if (open) Critters.draw(b, id, stage, w / 2f, h - 7f, cut, 0f, false, false) else Critters.silhouette(b, id, stage, w / 2f, h - 7f, cut)
    b.rect(0, h - 7, w, 7, rgb(0x2a2026)); b.rect(0, h - 7, w, 1, rgb(0x463840))
    return b
}
