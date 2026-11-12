package app.keyling.ui.art

import java.io.DataInputStream
import java.util.zip.GZIPInputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** What stands on the desk: the surface and one thing of each kind ("none" for empty). */
data class Decor(val desk: String = "oak", val mug: String = "heart", val plant: String = "cactus", val lamp: String = "none", val mascot: String = "none")

object Desk {
    const val W = 260; const val H = 490
    /** Device pixels per art pixel the desk is drawn at: everything keeps its place in art pixels, three times finer. */
    const val S = 3
    val STACK_X = 196; val STACK_W = 46; val STACK_BASE = 284

    /** Where each thing stands by default: the middle of its foot (x) and its bottom row, in art pixels. */
    private val PLACE = mapOf("notebook" to (59 to 172), "notes" to (39 to 225), "eraser" to (74 to 222),
        "mug" to (196 to 143), "plant" to (137 to 216), "lamp" to (136 to 132), "mascot" to (128 to 265))
    /** Things that lie flat (their shadow is their own shape, offset) rather than stand (a soft pool at the foot). */
    private val FLAT = setOf("notebook", "notes", "eraser")

    private class Surf(val ramp: IntArray, val planks: Boolean, val seam: Int)
    private val SURF = mapOf(
        "oak" to Surf(rgbs(0x1a100b, 0x26170f, 0x342014, 0x45291a, 0x583420, 0x6c4127, 0x7f4f2f), true, rgb(0x140c08)),
        "walnut" to Surf(rgbs(0x0b0604, 0x130b07, 0x1c100a, 0x26160d, 0x321c11, 0x402416, 0x4e2d1c), true, rgb(0x070403)),
        "white" to Surf(rgbs(0x4e4840, 0x6a645b, 0x878075, 0xa39c90, 0xbcb5a8, 0xd2ccbf, 0xe4dfd4), true, rgb(0x5a544b)),
        "baize" to Surf(rgbs(0x06100a, 0x0b1a12, 0x11261a, 0x183222, 0x203f2b, 0x2a4d35, 0x355c40), false, 0),
    )

    /** A little noise, 0..1, the same every time for a device pixel. */
    private fun grain(x: Int, y: Int) = (((x * 374761393 + y * 668265263) xor (x * y)).toLong() and 0xFFFFFFFFL) % 1000 / 1000f

    /**
     * The desk top, a device pixel at a time: wood grain (or baize) lit from (lx, ly). The rows are shared out
     * over the cores: on the fine desk this is a million pixels, the slowest part of building the desk.
     */
    fun surface(b: Buf, kind: String, lx: Float = 60f, ly: Float = -30f, rad: Float = 470f) {
        val sf = SURF[kind] ?: SURF.getValue("oak"); val u = b.s; val seamW = (u + 1) / 2; val inv = 1f / u
        fun row(Y: Int) {
            val fy = (Y + .5f) * inv; val dy = (fy - ly) * (fy - ly); val pl = Y / (25 * u); val at = Y * b.pw
            val seamRow = sf.planks && Y % (25 * u) < seamW; val seamX = ((pl * 97 + 40) % W) * u
            val wave = sin(fy * .21f + pl * 1.9f) * 2.4f + pl * 2.7f      // the grain's bend, the same along the row
            for (X in 0 until b.pw) {
                val fx = (X + .5f) * inv; val l = max(0f, 1 - kotlin.math.sqrt((fx - lx) * (fx - lx) + dy) / rad)
                b.d[at + X] = if (sf.planks) {
                    if (seamRow || (X - seamX) in 0 until seamW) sf.seam
                    else { val v = .06f + l * .8f + (.5f * sin(fx * .06f + wave)) * .22f
                        if (u == 1) pick(sf.ramp, v, X, Y) else lerp(sf.ramp, v + (grain(X, Y) - .5f) * .05f) }
                } else { val v = .08f + l * .8f + (grain(X, Y) - .5f) * .14f; if (u == 1) pick(sf.ramp, v, X, Y) else lerp(sf.ramp, v) }
            }
        }
        if (b.ph < 200) for (Y in 0 until b.ph) row(Y) else java.util.stream.IntStream.range(0, b.ph).parallel().forEach { row(it) }
    }

    // ---- the things: drawn by Codex's image tool (art/desk), shrunk by tools/desk.py ----

    /** A thing's picture at [S] device pixels per art pixel, and its spots from tools/desk.py, in device pixels. */
    class Art(val buf: Buf, val spots: IntArray)
    private val arts = HashMap<String, Art?>()

    /** Reads resources/desk/<name>.bin: gzip of w, h, w*h ARGB ints, a count, that many ints of spots. */
    fun art(name: String): Art? = synchronized(arts) {
        arts.getOrPut(name) {
            runCatching {
                val stream = Desk::class.java.getResourceAsStream("/desk/$name.bin") ?: return@runCatching null
                DataInputStream(GZIPInputStream(stream).buffered()).use { st ->
                    val w = st.readInt(); val h = st.readInt()
                    val b = Buf(w / S, h / S, S)
                    val bytes = ByteArray(w * h * 4); st.readFully(bytes); java.nio.ByteBuffer.wrap(bytes).asIntBuffer().get(b.d)
                    Art(b, IntArray(st.readInt()) { st.readInt() })
                }
            }.getOrNull()
        }
    }

    /** The picture for a slot and its kind ("mug" + "heart" is mug_heart); the user's print goes on the plain mug. */
    private fun thing(slot: String, kind: String, custom: IntArray?): Art? {
        val name = if (slot in FLAT) slot else "${slot}_$kind"
        val a = art(name) ?: return null
        if (name != "mug_custom" || custom == null) return a
        return Art(printed(a, custom), a.spots)
    }

    private fun lum(c: Int) = (Buf.ch(c, 16) * .3f + Buf.ch(c, 8) * .59f + Buf.ch(c, 0) * .11f) / 255f

    /**
     * The plain white mug with the user's 28×33 print wrapped round its front (spots 2..5 are the box for it):
     * the print takes the mug's own light and shade, and squeezes towards the sides as the mug turns away.
     */
    private fun printed(a: Art, custom: IntArray): Buf {
        val src = a.buf; val b = Buf(src.w, src.h, src.s); b.copyFrom(src)
        if (a.spots.size < 6) return b
        val x0 = a.spots[2]; val y0 = a.spots[3]; val x1 = a.spots[4]; val y1 = a.spots[5]
        var white = .01f
        for (y in y0 until y1) for (x in x0 until x1) { val c = src.getPx(x, y); if (c ushr 24 >= 200) white = max(white, lum(c)) }
        for (y in y0 until y1) for (x in x0 until x1) {
            val c = src.getPx(x, y); if (c ushr 24 < 200) continue
            val l = lum(c); if (l < white * .55f) continue                        // the outline and dark bits stay
            val u = ((x + .5f - x0) / (x1 - x0)).coerceIn(0f, 1f); val v = ((y + .5f - y0) / (y1 - y0)).coerceIn(0f, 1f)
            val pu = .5f + asin(u * 2 - 1) / PI.toFloat()                         // round the curve of the mug
            val pc = custom[(v * 32.999f).toInt() * 28 + (pu * 27.999f).toInt()]
            b.d[y * b.pw + x] = scale(pc, (l / white * 1.08f).coerceIn(.3f, 1.15f))
        }
        return b
    }

    /** A thing with its shadow: a flat thing casts its own shape down and to the right, a standing one a soft pool at its foot. */
    private fun withShadow(src: Buf, flat: Boolean): Buf {
        val b = Buf(src.w + 6, src.h + 4, src.s); val u = src.s
        if (flat) {
            for (y in 0 until src.ph) for (x in 0 until src.pw) { val a = (src.d[y * src.pw + x] ushr 24) / 255f; if (a > 0) b.px(x + 2 * u, y + 3 * u, 0, a * .42f) }
        } else {
            val cx = (src.w / 2f + 3) * u; val cy = (src.h - .8f) * u; val rx = src.w * .46f * u; val ry = 2.6f * u
            for (y in floor(cy - ry).toInt()..(cy + ry).toInt()) for (x in floor(cx - rx).toInt()..(cx + rx).toInt()) {
                val dx = (x + .5f - cx) / rx; val dy = (y + .5f - cy) / ry; val d = dx * dx + dy * dy
                if (d < 1) b.px(x, y, 0, .5f * (1 - d * d))
            }
        }
        b.blitPx(src, 0, 0)
        return b
    }

    /** Rows of the keyboard's case, in art pixels: its top and bottom. */
    const val CASE_TOP = 304; const val CASE_BOTTOM = 414

    /**
     * The keyboard without its caps: the case with rounded corners, its lights and badge, the plate and the
     * shadows under the keys, drawn with its top [oy] art rows higher than on the desk. The desk draws it in
     * place; the residents' map draws the same keyboard cut out, so both are one model.
     */
    fun keyboard(b: Buf, set: CapSet, oy: Int) {
        val u = b.s
        val c = set.case; val cr = intArrayOf(shade(c, -.55f), shade(c, -.35f), shade(c, -.18f), c, shade(c, .2f), shade(c, .45f))
        val cx0 = 3; val cx1 = 257; val cy0 = CASE_TOP - oy; val cy1 = CASE_BOTTOM - oy; val rr = 2f * u
        b.rect(cx0 + 3, cy1, cx1 - cx0, 6, 0, .5f)
        b.paint(cx0, cy0, cx1 - cx0, cy1 - cy0) { fx, fyIn, X, Y ->
            val ex = if (X < cx0 * u + rr) cx0 * u + rr - X - .5f else if (X >= cx1 * u - rr) X + .5f - (cx1 * u - rr) else 0f
            val ey = if (Y < cy0 * u + rr) cy0 * u + rr - Y - .5f else if (Y >= cy1 * u - rr) Y + .5f - (cy1 * u - rr) else 0f
            val fy = fyIn + oy                                                // desk rows from here on
            val x = fx.toInt(); val y = fy.toInt(); val l = 1 - hypot(fx - 60f, fy - 250f) / 420f
            val v: Float? = when {
                ex * ex + ey * ey > rr * rr -> null
                y < CASE_TOP + 2 -> .92f - (fy - CASE_TOP) * .08f; y < 312 -> .6f + l * .35f; y < 398 && (x < 7 || x > 252) -> if (x < 7) .66f + l * .2f else .42f
                y < 316 -> .18f; y < 398 -> null; y < 406 -> .58f + l * .3f; y < 407 -> .95f; else -> .3f - (fy - 407) / 7f * .25f
            }
            if (v == null) 0 else lerp(cr, v)
        }
        b.rect(206, 306 - oy, 30, 4, shade(c, -.4f)); b.rect(207, 306 - oy, 28, 3, rgb(0xc9c3b4)); b.rect(207, 306 - oy, 28, 1, rgb(0xf2eee4)); for (x in intArrayOf(211, 221, 231)) b.p(x, 307 - oy, rgb(0x6a6458))
        // the lights: one green, two off
        b.blob(22.5f, 307.5f - oy, .8f, .8f, rgbs(0x4ac05a, 0x7fe08a, 0xc8ffd0), null)
        for (x in intArrayOf(28, 34)) b.blob(x + .5f, 307.5f - oy, .8f, .8f, rgbs(0x2a2a2a, 0x3a3a3a, 0x4a4a4a), null)
        val pl = intArrayOf(shade(set.plate, -.4f), set.plate, shade(set.plate, .12f))
        b.paint(7, 316 - oy, 246, 82) { fx, fy, _, _ -> lerp(pl, .45f + (1 - hypot(fx - 60f, fy + oy - 290f) / 300f) * .3f) }
        for (k in Board.keys) if (k !== Board.space || oy != 0) b.rect(k.x + k.w / 2 - 3, k.y + k.h - 1 - oy, 6, 2, rgb(0x151418))
    }

    /** The still part of the desk scene: surface, keyboard cable, case and plate, the nook under the space bar. */
    fun background(decor: Decor, set: CapSet, custom: IntArray?): Buf {
        val b = Buf(W, H, S); val u = S
        surface(b, decor.desk)
        // the keyboard's cable, a soft round cord
        val cab = rgbs(0x101014, 0x2a2a33, 0x44444f, 0x5a5a6a)
        var t = 0f
        while (t <= 1f) {
            val x = 96 - t * 100 + sin(t * 9) * 3; val y = 304 - sin(t * PI.toFloat() / 2) * 80
            for (j in 0 until 4 * u) { val k = (j + .5f) / (4 * u)
                b.px((x * u).roundToInt(), (y * u).roundToInt() + j, if (k < .12f || k > .9f) cab[0] else lerp(cab, 1 - abs(k - .35f) * 1.6f)) }
            t += .0015f / u
        }
        keyboard(b, set, 0)
        // the nook under the space bar
        val s = Board.space; val hx = s.x; val hy = s.y; val hw = s.w; val hh = s.h
        val glow = rgbs(0x0e080a, 0x1c100c, 0x2e1a10, 0x4a2a14, 0x6e401a, 0x96592a)
        b.paint(hx - 1, hy - 1, hw + 2, hh + 2) { fx, fy, _, _ -> lerp(glow, (1 - hypot((fx - (hx + hw / 2f)) / (hw * .55f), (fy - (hy + hh + 3f)) / (hh * 1.2f))) * 1.05f) }
        b.rect(hx - 1, hy - 1, hw + 2, 1, shade(set.plate, .25f))
        for (sxx in intArrayOf(hx + 4, hx + hw - 9)) { b.rect(sxx, hy + 2, 5, 7, rgb(0x0c0c10)); b.rect(sxx + 1, hy + 3, 3, 1, rgb(0x3a3a46)); b.rect(sxx + 1, hy + 4, 3, 2, rgb(0xd8d8d8)) }
        b.rect(hx + 6, hy + 10, hw - 12, 1, rgb(0xc8ccd6))
        listOf(rgb(0x7a4a8a) to rgb(0xa070b0), rgb(0x3f7a6a) to rgb(0x62a08e), rgb(0xc8913a) to rgb(0xe8b860)).forEachIndexed { i, (c1, c2) ->
            val y = hy + 9 - i * 2; val x = hx + 13 + i; b.rect(x, y, 11 - i * 2, 2, c1); b.rect(x, y, 11 - i * 2, 1, c2) }
        val lx = hx + hw - 24; b.rect(lx + 2, hy + 5, 1, 6, rgb(0x3a3020)); b.rect(lx, hy + 10, 5, 1, rgb(0x3a3020))
        for (j in 0 until 3) b.rect(lx + 1 - j / 2, hy + 2 + j, 3 + j, 1, if (j == 0) rgb(0xffe6a0) else rgb(0xe8a840))
        b.blob(lx + 2f, hy + 8f, 8f, 5f, rgbs(0xffd080, 0xffe6a0), null, alpha = .25f)
        for (x in hx + 30 until hx + hw - 30) b.p(x, hy + 11, if (x % 4 < 2) rgb(0x9a3b3b) else rgb(0xc8913a))
        return b
    }

    /**
     * A thing on the desk that can be moved: its picture with its shadow, where it stands by default (art pixels),
     * and its hot spot in art pixels from its top-left, or null: where steam rises, the wick, the lamp's light.
     */
    class Sprite(val id: String, val buf: Buf, val x: Int, val y: Int, val hot: Pair<Float, Float>? = null)

    /** Each desk thing on its own layer, so it can be dragged around. */
    fun sprites(decor: Decor, custom: IntArray?): List<Sprite> {
        fun make(id: String, kind: String = ""): Sprite? {
            if (kind == "none") return null
            val a = thing(id, kind, custom) ?: return null
            val (cx, bottom) = PLACE.getValue(id)
            val hot = if (a.spots.size >= 2) (a.spots[0] + .5f) / S to (a.spots[1] + .5f) / S else null
            return Sprite(id, withShadow(a.buf, id in FLAT), cx - a.buf.w / 2, bottom - a.buf.h, hot)
        }
        return listOfNotNull(make("notebook"), make("notes"), make("eraser"), make("lamp", decor.lamp), make("plant", decor.plant),
            make("mug", decor.mug), make("mascot", decor.mascot))
    }

    /** The lowest a thing's top may stand, so nothing is dragged onto the keyboard or off the desk. */
    fun clamp(s: Sprite, dx: Int, dy: Int): Pair<Int, Int> {
        val x = (s.x + dx).coerceIn(-s.buf.w / 3, W - s.buf.w * 2 / 3); val y = (s.y + dy).coerceIn(36, 300 - s.buf.h)
        return (x - s.x) to (y - s.y)
    }

    /** The manuscript on the desk: one sheet edge per four pages, tied with a red band. */
    fun stack(b: Buf, pages: Int) {
        val x0 = STACK_X; val w = STACK_W; val base = STACK_BASE; val hp = (pages / 4).coerceIn(3, 60)
        b.rect(x0 + 4, base - 2, w, 6, 0, .4f)
        for (j in 0 until hp) { val y = base - j; val jit = if ((j * 37) % 7 == 0) 1 else if ((j * 53) % 11 == 0) -1 else 0
            b.rect(x0 + jit, y, w, 1, when (j % 3) { 0 -> rgb(0xcbbd9c); 1 -> rgb(0xe6dcc4); else -> rgb(0xf2ead6) }); b.p(x0 + jit, y, rgb(0xa8987a)); b.p(x0 + jit + w - 1, y, rgb(0xb8a888)) }
        val ty = base - hp - 10
        for (y in ty..base - hp) for (x in x0 until x0 + w) b.p(x, y, pick(rgbs(0xe2d6b8, 0xefe6cf, 0xf8f1df), .4f + (x - x0).toFloat() / w * .2f + (y - ty) / 30f, x, y))
        b.rect(x0, ty, w, 1, rgb(0xc8bb98)); b.rect(x0 + 10, ty + 3, 24, 1, rgb(0x3d3a5c)); b.rect(x0 + 14, ty + 5, 16, 1, rgb(0x8a8aa0))
        b.rect(x0 + 30, ty, 3, hp + 11, rgb(0xb83a2a)); b.rect(x0 + 30, ty, 1, hp + 11, rgb(0xe05a40))
    }

    /** A small card picture of one decor item on a patch of the desk. */
    fun itemPreview(cat: String, id: String, desk: String, custom: IntArray?): Buf {
        val b = Buf(56, 52, S)
        surface(b, if (cat == "desk") id else desk, 10f, -20f, 110f)
        if (cat == "mug" && id == "custom" && custom == null) {
            b.rect(18, 14, 20, 20, 0, .35f); for (i in 0 until 12) b.p(28, 18 + i, rgb(0xfbf3e4)); for (i in 0 until 5) { b.p(28 - i, 18 + i, rgb(0xfbf3e4)); b.p(28 + i, 18 + i, rgb(0xfbf3e4)) }
        } else if (cat != "desk") thing(cat, id, custom)?.let { a -> b.blit(withShadow(a.buf, false), 28 - a.buf.w / 2, 47 - a.buf.h) }
        return b
    }

    /** The user's photo turned into a 28×33 posterized print for the mug. */
    fun posterize(src: IntArray): IntArray = IntArray(28 * 33) { i ->
        val x = i % 28; val y = i / 28; val c = src[i]
        fun q(v: Int) = (min(4f, floor(v / 255f * 5 + bay(x, y) - .5f + .5f)) * 63.75f).roundToInt().coerceIn(0, 255)
        Buf.OPAQUE or (q(Buf.ch(c, 16)) shl 16) or (q(Buf.ch(c, 8)) shl 8) or q(Buf.ch(c, 0))
    }
}
