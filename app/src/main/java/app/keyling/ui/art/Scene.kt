package app.keyling.ui.art

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Everything on the desk that moves: letters falling to the pet, typo bugs, steam, hearts, snores. */
class Scene {
    class Crumb(val ch: String, var x: Float, var y: Float, val land: Float) { var vy = 0f; var ph = 0; var t = 0f; var hop = 0f; var dead = false }
    class Bug(var x: Float, var y: Float, var v: Float, val kind: Int)
    class Puff(val x: Float, var y: Float, val life: Float, val ph: Float, val amp: Float, val sp: Float) { var t = 0f }
    class Spark(var x: Float, var y: Float) { var t = -0f }

    var now = 0f
    var dust = false
    var pet = "space"
    var stage = 0
    var steam = true
    var candle = false
    val crumbs = ArrayList<Crumb>()
    val bugs = ArrayList<Bug>()
    val puffs = ArrayList<Puff>()
    val hearts = ArrayList<Spark>()
    val zs = ArrayList<Spark>()
    private val tmp = Buf(72, 40)
    private val tmpFine = Buf(72, 40, Desk.S)
    private val LID = -36
    private var chew = 0f; private var blink = 0f; private var puffT = 0f
    var onEat: (() -> Unit)? = null

    val petX get() = Board.space.x + Board.space.w / 2f
    val petY get() = Board.space.y + 2f

    init { repeat(2) { spawnBug() } }

    fun spawnBug() { val r = Random.nextInt(4); bugs += Bug(12 + Random.nextFloat() * 235, (Board.KY + r * Board.PY + 15).toFloat(), (if (Random.nextBoolean()) -1 else 1) * (5 + Random.nextFloat() * 5), Random.nextInt(9)) }

    private fun pickLetter(): String { var s = 0f; FREQ.values.forEach { s += it }; var r = Random.nextFloat() * s
        for ((k, v) in FREQ) { r -= v; if (r <= 0) return k }; return "О" }

    fun drop(n: Int, chars: List<String>? = null) {
        repeat(n) { i -> crumbs += Crumb(chars?.getOrNull(i) ?: pickLetter(), 12 + Random.nextFloat() * 236, Board.KY - 70 - Random.nextFloat() * 70,
            Board.KY + Random.nextInt(4) * Board.PY + 6 + Random.nextFloat() * 3) }
    }

    fun pet() { repeat(3) { i -> hearts += Spark(petX - 8 + Random.nextFloat() * 14, petY - 16).also { it.t = -i * .18f } } }

    fun step(dt: Float) {
        now += dt
        for (k in Board.keys) if (k.press > 0) k.press -= dt
        if (chew > 0) chew -= dt
        if (blink > 0) blink -= dt else if (Random.nextFloat() < dt / 3.5f) blink = .14f
        val mx = petX; val my = petY + 3
        for (c in crumbs) when (c.ph) {
            0 -> { c.vy += 220 * dt; c.y += c.vy * dt; if (c.y >= c.land) { c.y = c.land; c.ph = 1; c.t = .25f + Random.nextFloat() * .7f } }
            1 -> { c.t -= dt; if (c.t <= 0 && !dust) { c.ph = 2; c.t = 0f } }
            else -> { c.t += dt; val dx = mx - c.x; val dy = my - c.y; val d = hypot(dx, dy); val sp = 70 * dt; c.hop = abs(sin(c.t * 9)) * 3
                if (d < sp + 2) { c.dead = true; chew = .3f; onEat?.invoke() } else { c.x += dx / d * sp; c.y += dy / d * sp } }
        }
        crumbs.removeAll { it.dead }
        for (b in bugs) { b.x += b.v * dt; if (b.x < 4 || b.x > Desk.W - 4) { b.v = -b.v; b.x = b.x.coerceIn(4f, Desk.W - 4f) }; if (Random.nextFloat() < dt * .25f) b.v = -b.v }
        for (h in hearts) { h.t += dt; if (h.t > 0) h.y -= 14 * dt }; hearts.removeAll { it.t >= 1 }
        if (dust && Random.nextFloat() < dt * 1.1f) zs += Spark(petX + 14, Board.space.y - 2f)
        for (z in zs) { z.t += dt; z.y -= 7 * dt; z.x += sin(z.t * 3) * .25f }; zs.removeAll { it.t >= 2.6f }
        if (!steam) puffs.clear() else {
            puffT -= dt
            // steam rises from the middle of the tea (the mug's hot spot); it follows the mug when moved
            val mug = sprites.firstOrNull { it.id == "mug" }; val hot = mug?.hot
            if (puffT <= 0 && mug != null && hot != null) { puffT = .16f + Random.nextFloat() * .12f
                puffs += Puff(mug.x + hot.first - 5 + Random.nextFloat() * 10, mug.y + hot.second, 3.2f + Random.nextFloat() * 1.6f, Random.nextFloat() * 6.28f, 2 + Random.nextFloat() * 2.5f, 5 + Random.nextFloat() * 3) }
            for (p in puffs) { p.t += dt; p.y -= p.sp * dt * (1 - p.t / p.life * .5f) }; puffs.removeAll { it.t >= it.life }
        }
    }

    /** A falling letter centred on (x, y), with a dark rim; on a fine buffer in a plain face, its rim thin and round. */
    private fun Buf.letter(ch: String, x: Float, y: Float, col: Int) {
        if (s == 1) {
            val dark = Glyphs.text(ch, rgb(0x2a1620)); val g = Glyphs.text(ch, col)
            val ix = x.roundToInt() - 4; val iy = y.roundToInt() - 5
            for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) blit(dark, ix + dx, iy + dy)
            blit(g, ix, iy); return
        }
        val dark = Glyphs.text(ch, rgb(0x2a1620), 9f, s, smooth = true); val g = Glyphs.text(ch, col, 9f, s, smooth = true)
        val (g0, g1) = Glyphs.inkSpan(g)
        val ix = (x * s).roundToInt() - (g0 + g1) / 2; val iy = ((y - 7.6f) * s).roundToInt()
        val r = (s + 1) / 2 + 1
        for (dy in -r..r) for (dx in -r..r) if (dx * dx + dy * dy in 1..r * r + 1) blitPx(dark, ix + dx, iy + dy)
        blitPx(g, ix, iy)
    }

    /** A heart about [k] art pixels wide with its top-left at (x, y), smooth on a fine buffer. */
    private fun Buf.heart(x: Int, y: Int, a: Float) {
        if (s == 1) {
            for ((i, j) in listOf(1 to 0, 3 to 0, 0 to 1, 1 to 1, 2 to 1, 3 to 1, 4 to 1, 1 to 2, 2 to 2, 3 to 2, 2 to 3)) p(x + i, y + j, rgb(0xff6f8f), a)
            p(x + 1, y + 1, rgb(0xffd0dc), a); return
        }
        val w = 5f * s; val h = 4.6f * s
        for (j in 0 until h.toInt() + 1) for (i in 0 until w.toInt() + 1) {
            val u = ((i + .5f) / w - .5f) * 2.4f; val v = (1 - (j + .5f) / h) * 2.3f - 1.05f
            val f = (u * u + v * v - 1).let { it * it * it } - u * u * v * v * v
            if (f <= 0) px(x * s + i, y * s + j, if (hypot(u + .55f, v - .45f) < .32f) rgb(0xffd0dc) else rgb(0xff6f8f), a)
        }
    }

    /** A typo bug at (x, y) facing [d] (±1), its legs at step [leg]. */
    private fun Buf.bug(x: Int, y: Int, d: Int, leg: Int) {
        if (s == 1) {
            rect(x - 2, y - 1, 4, 2, rgb(0x5a1e2a)); rect(x - 1, y - 2, 2, 1, rgb(0xa8475a)); p(x - 1, y - 1, rgb(0xd07080)); p(x + d * 2, y - 1, rgb(0x1a0e14))
            p(x - 2 + leg, y + 1, rgb(0x1a0e14)); p(x + 1 - leg, y + 1, rgb(0x1a0e14)); return
        }
        val ink = rgb(0x1a0e14)
        for (k in 0..2) { val lx = x - 1.6f + k * 1.6f + (if ((k + leg) % 2 == 0) .35f else -.35f)
            for (t in 0 until s) { px(((lx) * s).roundToInt() + t / 2, ((y + .2f) * s).roundToInt() + t, ink) } }
        blob(x + d * 1.9f, y - .9f, 1f, .9f, rgbs(0x2a0e18, 0x3a1624), ink)
        blob(x - d * .3f, y - .8f, 2.3f, 1.5f, rgbs(0x5a1e2a, 0xa8475a, 0xd07080), ink)
        px(((x + d * 2.2f) * s).roundToInt(), ((y - 1.2f) * s).roundToInt(), rgb(0xffe0e8))
    }

    /** Paints this moment over the still background [bg] into [out]. */
    var sprites: List<Desk.Sprite> = emptyList()
    var offsets: Map<String, Pair<Int, Int>> = emptyMap()
    var lifted: String? = null
    fun at(id: String): Pair<Int, Int> = offsets[id] ?: (0 to 0)

    /** Keycaps missing from [caps] (still being built) are left out: the desk shows without them for a moment. */
    fun render(out: Buf, bg: Buf, caps: Map<Key, Board.Caps>, plate: Int, pages: Int) {
        out.copyFrom(bg)
        for (sp in sprites.sortedBy { it.y + it.buf.h + at(it.id).second }) { val (dx, dy) = at(sp.id)
            if (sp.id == lifted) { out.rect(sp.x + dx + 3, sp.y + dy + 5, sp.buf.w - 2, sp.buf.h - 2, 0, .25f); out.blit(sp.buf, sp.x + dx, sp.y + dy - 3) }
            else out.blit(sp.buf, sp.x + dx, sp.y + dy) }
        val (mdx, mdy) = at("mug"); val u = out.s
        // steam: soft puffs, a device pixel at a time
        if (steam) for (p in puffs) { val k = p.t / p.life
            val x = p.x + mdx + sin(p.ph + p.t * 1.3f) * p.amp * (.3f + k) + k * 4; val y = p.y + mdy
            val r = 1.5f + k * 5.5f; val a = (if (k < .15f) k / .15f else 1f) * (1 - k) * .6f; val rr = ceil(r * u).toInt()
            for (j in -rr..rr) for (i in -rr..rr) { val d = (i * i + j * j) / (r * r * u * u); if (d > 1) continue
                val xx = (x * u).roundToInt() + i; val yy = (y * u).roundToInt() + j
                if (u == 1) { if (bay(xx, yy) < a * 1.5f * (1 - d * .6f)) out.px(xx, yy, if (d < .3f) rgb(0xfffaf0) else rgb(0xe8e2d8), .6f + .35f * (1 - d)) }
                else out.px(xx, yy, if (d < .3f) rgb(0xfffaf0) else rgb(0xe8e2d8), a * (1 - d) * .9f) } }
        // the candle's flame stands on its wick (the mascot's hot spot)
        val cs = if (candle) sprites.firstOrNull { it.id == "mascot" } else null; val wick = cs?.hot
        if (cs != null && wick != null) { val (cdx, cdy) = at("mascot"); val fx = cs.x + cdx + wick.first; val fy = cs.y + cdy + wick.second
            val fl = sin(now * 11) * .6f + sin(now * 27) * .4f
            // a warm glow fading out from the flame
            val gr = 10f * u; val gx = fx * u; val gy = (fy - 3f) * u
            for (j in (gy - gr).toInt()..(gy + gr).toInt()) for (i in (gx - gr).toInt()..(gx + gr).toInt()) {
                val d = hypot(i + .5f - gx, j + .5f - gy) / gr; if (d < 1) out.px(i, j, rgb(0xffc060), (1 - d) * (1 - d) * (.22f + fl * .03f)) }
            // a teardrop: round at the bottom, its tip swaying
            val fh = 6.5f; val fw = 1.6f
            for (j in 0 until (fh * u).toInt()) { val t = j / (fh * u); val yy = ((fy - .5f) * u).roundToInt() - j
                val half = fw * u * (if (t < .35f) kotlin.math.sqrt(t / .35f) else (1 - t) / .65f) ; val sway = fl * t * t * u
                for (i in -half.toInt() - 1..half.toInt() + 1) { val dx = abs(i + .5f - sway) / half.coerceAtLeast(.5f); if (dx > 1) continue
                    out.px((fx * u).roundToInt() + i, yy, when { t < .3f && dx < .55f -> rgb(0xfff6d0); t < .6f -> rgb(0xffc040); else -> rgb(0xff7a20) }, if (dx > .8f) .6f else 1f) } } }
        for (k in Board.keys) { if (k === Board.space) continue; val c = caps[k] ?: continue; out.blit(if (k.press > 0) c.dn else c.up, k.x, k.y) }
        val leg = ((now * 1000 / 110).toInt()) % 2
        for (b in bugs) out.bug(b.x.roundToInt(), b.y.roundToInt(), if (b.v > 0) 1 else -1, leg)
        val s = Board.space; val hx = s.x; val hy = s.y; val hw = s.w
        for (x in hx + 10 until hx + hw - 10) { val y = hy + 1 + (abs(sin((x - hx - 10f) / ((hw - 20f) / 3f) * PI.toFloat())) * 1.5f).roundToInt(); out.p(x, y, rgb(0x1a1210))
            if ((x - hx) % 7 == 0) { val col = rgbs(0xffd36b, 0xff8fae, 0x8fe8d8)[((x - hx) / 7) % 3]; out.p(x, y + 1, col)
                if (sin(now * 3.3f + x) > 0) out.rect(x - 1, y, 3, 3, col, .35f) } }
        if (!dust) {
            val breathe = sin(now / .6f) * .6f
            out.rect(hx + 3, hy - 1, hw - 6, 3, 0, .4f)
            caps[s]?.let { out.blit(it.up, hx, hy + LID) }
            for (mx in intArrayOf(hx + 9, hx + hw - 12)) { for (y in hy + LID + 14 until hy + 12) { out.p(mx, y, rgb(0xe8c890)); out.p(mx + 1, y, rgb(0xc8a060)); out.p(mx + 2, y, rgb(0x8a6a3a)) }
                out.rect(mx - 1, hy + LID + 12, 5, 3, rgb(0xb83a2a)); out.rect(mx, hy + LID + 12, 2, 1, rgb(0xe86a4a)) }
            val tmp = if (out.s == Desk.S) tmpFine else tmp
            tmp.clear(); Critters.draw(tmp, pet, stage, tmp.w / 2f, tmp.h - 2f, if (tmp.s == Desk.S) Critters.DESK_FINE else Critters.DESK, now, blink > 0, chew > 0)
            out.blit(tmp, (petX - tmp.w / 2f).roundToInt(), hy + 14 - tmp.h, clipY = hy + 12)
            out.rect(hx - 1, hy + 12, hw + 2, 2, shade(plate, .3f)); out.rect(hx - 1, hy + 14, hw + 2, 1, shade(plate, -.3f))
        } else caps[s]?.let { out.blit(it.up, hx, hy) }
        if (dust) {
            // specks a device pixel each, and a cobweb in the corner
            for (y in (Board.KY - 4) * u until 398 * u) for (x in 7 * u until 253 * u) { val h = ((x * 73856093) xor (y * 19349663)).toLong() and 0xFFFFFFFFL
                if (h % (11L * u * u) == 0L) out.px(x, y, if (h % 3 != 0L) rgb(0xcfc6ba) else rgb(0x9a9188), .75f) }
            val wx = 252 * u; val wy = 316 * u
            for (i in 0 until 20 * u) { out.px(wx - i, wy + (i * .9f).roundToInt(), rgb(0xe8e0d8), .6f); out.px(wx - i, wy, rgb(0xe8e0d8), .6f); out.px(wx, wy + i, rgb(0xe8e0d8), .6f) }
            for (z in zs) out.blit(Glyphs.text("z", rgb(0xfbf3e4), if (u == 1) 8f else 7f, u, smooth = u > 1), z.x.roundToInt(), z.y.roundToInt(), alpha = (1 - z.t / 2.6f).coerceIn(0f, 1f))
        }
        for (c in crumbs) out.letter(c.ch, c.x, c.y - c.hop, if (c.ph == 2) rgb(0xffd08a) else rgb(0xfbf3e4))
        for (h in hearts) { if (h.t < 0) continue; out.heart(h.x.roundToInt(), h.y.roundToInt(), (1 - h.t).coerceIn(0f, 1f)) }
    }

    // ---- hit tests in art pixels ----
    /** The desk thing under (x, y), top-most first, by its painted pixels. */
    fun spriteAt(x: Float, y: Float): Desk.Sprite? = sprites.sortedByDescending { it.y + it.buf.h + at(it.id).second }.firstOrNull { sp ->
        val (dx, dy) = at(sp.id); val ix = (x - sp.x - dx).toInt(); val iy = (y - sp.y - dy).toInt()
        val u = sp.buf.s; val px = ((x - sp.x - dx) * u).toInt(); val py = ((y - sp.y - dy) * u).toInt()
        ix in 0 until sp.buf.w && iy in 0 until sp.buf.h && (sp.buf.getPx(px, py) ushr 24) > 160 }
    fun bugAt(x: Float, y: Float): Bug? = bugs.firstOrNull { hypot(it.x - x, it.y - y) < 8 }
    fun petAt(x: Float, y: Float) = !dust && hypot(petX - x, (petY - y) * 1.2f) < 16
    fun keyAt(x: Float, y: Float): Key? = Board.keys.firstOrNull { it !== Board.space && x >= it.x && x < it.x + it.w && y >= it.y && y < it.y + it.h }
}
