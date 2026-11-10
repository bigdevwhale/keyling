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
    var grow = 1f
    var steam = true
    var candle = false
    val crumbs = ArrayList<Crumb>()
    val bugs = ArrayList<Bug>()
    val puffs = ArrayList<Puff>()
    val hearts = ArrayList<Spark>()
    val zs = ArrayList<Spark>()
    private val tmp = Buf(72, 52)
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
            if (puffT <= 0) { puffT = .16f + Random.nextFloat() * .12f
                puffs += Puff(Desk.SLOT_MUG.first + 9 + Random.nextFloat() * 10, Desk.SLOT_MUG.second + 2f, 3.2f + Random.nextFloat() * 1.6f, Random.nextFloat() * 6.28f, 2 + Random.nextFloat() * 2.5f, 5 + Random.nextFloat() * 3) }
            for (p in puffs) { p.t += dt; p.y -= p.sp * dt * (1 - p.t / p.life * .5f) }; puffs.removeAll { it.t >= it.life }
        }
    }

    private fun Buf.letter(ch: String, x: Float, y: Float, col: Int) {
        val dark = Glyphs.text(ch, rgb(0x2a1620)); val g = Glyphs.text(ch, col)
        val ix = x.roundToInt() - 4; val iy = y.roundToInt() - 5
        for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) blit(dark, ix + dx, iy + dy)
        blit(g, ix, iy)
    }

    /** Paints this moment over the still background [bg] into [out]. */
    var sprites: List<Desk.Sprite> = emptyList()
    var offsets: Map<String, Pair<Int, Int>> = emptyMap()
    var lifted: String? = null
    fun at(id: String): Pair<Int, Int> = offsets[id] ?: (0 to 0)

    fun render(out: Buf, bg: Buf, caps: Map<Key, Board.Caps>, plate: Int, pages: Int) {
        out.copyFrom(bg)
        for (sp in sprites.sortedBy { it.y + it.buf.h + at(it.id).second }) { val (dx, dy) = at(sp.id)
            if (sp.id == lifted) { out.rect(sp.x + dx + 3, sp.y + dy + 5, sp.buf.w - 2, sp.buf.h - 2, 0, .25f); out.blit(sp.buf, sp.x + dx, sp.y + dy - 3) }
            else out.blit(sp.buf, sp.x + dx, sp.y + dy) }
        val (mdx, mdy) = at("mug")
        if (steam) for (p in puffs) { val k = p.t / p.life
            val x = p.x + mdx + sin(p.ph + p.t * 1.3f) * p.amp * (.3f + k) + k * 4; val y = p.y + mdy
            val r = 1.5f + k * 5.5f; val a = (if (k < .15f) k / .15f else 1f) * (1 - k) * .6f; val rr = ceil(r).toInt()
            for (j in -rr..rr) for (i in -rr..rr) { val d = (i * i + j * j) / (r * r); if (d > 1) continue
                val xx = (x + i).roundToInt(); val yy = (y + j).roundToInt()
                if (bay(xx, yy) < a * 1.5f * (1 - d * .6f)) out.p(xx, yy, if (d < .3f) rgb(0xfffaf0) else rgb(0xe8e2d8), .6f + .35f * (1 - d)) } }
        if (candle) { val (mx, my) = Desk.SLOT_MASCOT; val (cdx, cdy) = at("mascot"); val fx = mx + 21 + cdx; val fy = my + 8 + cdy; val fl = sin(now * 11) * .6f + sin(now * 27) * .4f
            out.blob(fx.toFloat(), fy - 1f, 9f, 9f, rgbs(0xffb040, 0xffd070), null, alpha = .18f)
            for (j in 0 until 6) { val w = if (j < 2) 1 else if (j < 4) 2 else 1
                for (i in -w + 1 until w) out.p(fx + i + (if (j > 3) fl.roundToInt() else 0), fy - j, if (j < 2) rgb(0xfff6d0) else if (j < 4) rgb(0xffc040) else rgb(0xff7a20)) } }
        for (k in Board.keys) { if (k === Board.space) continue; val c = caps.getValue(k); out.blit(if (k.press > 0) c.dn else c.up, k.x, k.y) }
        val leg = ((now * 1000 / 110).toInt()) % 2
        for (b in bugs) { val x = b.x.roundToInt(); val y = b.y.roundToInt(); val d = if (b.v > 0) 1 else -1
            out.rect(x - 2, y - 1, 4, 2, rgb(0x5a1e2a)); out.rect(x - 1, y - 2, 2, 1, rgb(0xa8475a)); out.p(x - 1, y - 1, rgb(0xd07080)); out.p(x + d * 2, y - 1, rgb(0x1a0e14))
            out.p(x - 2 + leg, y + 1, rgb(0x1a0e14)); out.p(x + 1 - leg, y + 1, rgb(0x1a0e14)) }
        val s = Board.space; val hx = s.x; val hy = s.y; val hw = s.w
        for (x in hx + 10 until hx + hw - 10) { val y = hy + 1 + (abs(sin((x - hx - 10f) / ((hw - 20f) / 3f) * PI.toFloat())) * 1.5f).roundToInt(); out.p(x, y, rgb(0x1a1210))
            if ((x - hx) % 7 == 0) { val col = rgbs(0xffd36b, 0xff8fae, 0x8fe8d8)[((x - hx) / 7) % 3]; out.p(x, y + 1, col)
                if (sin(now * 3.3f + x) > 0) out.rect(x - 1, y, 3, 3, col, .35f) } }
        if (!dust) {
            val breathe = sin(now / .6f) * .6f
            out.rect(hx + 3, hy - 1, hw - 6, 3, 0, .4f)
            out.blit(caps.getValue(s).up, hx, hy + LID)
            for (mx in intArrayOf(hx + 9, hx + hw - 12)) { for (y in hy + LID + 14 until hy + 12) { out.p(mx, y, rgb(0xe8c890)); out.p(mx + 1, y, rgb(0xc8a060)); out.p(mx + 2, y, rgb(0x8a6a3a)) }
                out.rect(mx - 1, hy + LID + 12, 5, 3, rgb(0xb83a2a)); out.rect(mx, hy + LID + 12, 2, 1, rgb(0xe86a4a)) }
            tmp.clear(); Critters.draw(tmp, pet, tmp.w / 2f, tmp.h - 2f, .74f * grow, now, blink > 0, chew > 0)
            out.blit(tmp, (petX - tmp.w / 2f).roundToInt(), hy + 14 - tmp.h, clipY = hy + 12)
            out.rect(hx - 1, hy + 12, hw + 2, 2, shade(plate, .3f)); out.rect(hx - 1, hy + 14, hw + 2, 1, shade(plate, -.3f))
        } else out.blit(caps.getValue(s).up, hx, hy)
        if (dust) {
            for (y in Board.KY - 4 until 398) for (x in 7 until 253) { val h = ((x * 73856093) xor (y * 19349663)).toLong() and 0xFFFFFFFFL
                if (h % 11 == 0L) out.p(x, y, if (h % 3 != 0L) rgb(0xcfc6ba) else rgb(0x9a9188), .75f) }
            val wx = 252; val wy = 316
            for (i in 0 until 20) { out.p(wx - i, wy + (i * .9f).roundToInt(), rgb(0xe8e0d8), .6f); out.p(wx - i, wy, rgb(0xe8e0d8), .6f); out.p(wx, wy + i, rgb(0xe8e0d8), .6f) }
            for (z in zs) out.blit(Glyphs.text("z", rgb(0xfbf3e4)), z.x.roundToInt(), z.y.roundToInt(), alpha = (1 - z.t / 2.6f).coerceIn(0f, 1f))
        }
        for (c in crumbs) out.letter(c.ch, c.x, c.y - c.hop, if (c.ph == 2) rgb(0xffd08a) else rgb(0xfbf3e4))
        for (h in hearts) { if (h.t < 0) continue; val x = h.x.roundToInt(); val y = h.y.roundToInt(); val a = (1 - h.t).coerceIn(0f, 1f)
            for ((i, j) in listOf(1 to 0, 3 to 0, 0 to 1, 1 to 1, 2 to 1, 3 to 1, 4 to 1, 1 to 2, 2 to 2, 3 to 2, 2 to 3)) out.p(x + i, y + j, rgb(0xff6f8f), a)
            out.p(x + 1, y + 1, rgb(0xffd0dc), a) }
    }

    // ---- hit tests in art pixels ----
    /** The desk thing under (x, y), top-most first, by its painted pixels. */
    fun spriteAt(x: Float, y: Float): Desk.Sprite? = sprites.sortedByDescending { it.y + it.buf.h + at(it.id).second }.firstOrNull { sp ->
        val (dx, dy) = at(sp.id); val ix = (x - sp.x - dx).toInt(); val iy = (y - sp.y - dy).toInt()
        ix in 0 until sp.buf.w && iy in 0 until sp.buf.h && (sp.buf.d[iy * sp.buf.w + ix] ushr 24) > 160 }
    fun bugAt(x: Float, y: Float): Bug? = bugs.firstOrNull { hypot(it.x - x, it.y - y) < 8 }
    fun petAt(x: Float, y: Float) = !dust && hypot(petX - x, (petY - y) * 1.2f) < 16
    fun keyAt(x: Float, y: Float): Key? = Board.keys.firstOrNull { it !== Board.space && x >= it.x && x < it.x + it.w && y >= it.y && y < it.y + it.h }
}
