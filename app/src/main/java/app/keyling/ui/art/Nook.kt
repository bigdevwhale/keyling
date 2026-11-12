package app.keyling.ui.art

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** A resident's home under its key, seen close up with the keycap lifted off. */
object Nook {
    const val W = 150; const val H = 92

    private val LAB = mapOf("space" to "", "enter" to "⏎", "bksp" to "⌫", "caps" to "⇪", "tab" to "↹", "shift" to "⇧", "esc" to "Esc")
    private val WIDE = mapOf("space" to 132, "enter" to 104, "bksp" to 100, "caps" to 100, "tab" to 100, "shift" to 104, "esc" to 96)

    /** [lift] 0..1 raises the cap; [t] is seconds for breathing and little idle things. */
    fun render(b: Buf, id: String, open: Boolean, set: CapSet, t: Float, lift: Float, mouth: Boolean = false, stage: Int = 0) {
        b.clear()
        val fine = b.s > 1
        fun tone(r: IntArray, v: Float, x: Int, y: Int) = if (fine) lerp(r, v) else pick(r, v, x, y)
        val pl = intArrayOf(shade(set.plate, -.4f), set.plate, shade(set.plate, .15f))
        b.paint(0, 0, W, H) { fx, fy, X, Y -> tone(pl, .5f - hypot(fx - W / 2f, fy - 30f) / 220f, X, Y) }
        val hw = WIDE[id] ?: 100; val hx = (W - hw) / 2; val hy = 36; val hh = 50
        // the hole and its warm light
        val glow = if (open) rgbs(0x0e080a, 0x1c100c, 0x2e1a10, 0x4a2a14, 0x6e401a, 0x96592a) else rgbs(0x08060a, 0x0e0a10, 0x141018, 0x1a1420)
        b.paint(hx, hy, hw, hh) { fx, fy, X, Y -> tone(glow, (1 - hypot((fx - (hx + hw / 2f)) / (hw * .6f), (fy - (hy + hh + 2f)) / (hh * 1.1f))) * 1.05f, X, Y) }
        b.rect(hx, hy, hw, 1, shade(set.plate, .3f))
        if (open) furnish(b, id, hx, hy, hw, hh, t)
        // the resident
        val blink = (t % 3.7f) < .14f
        val cx = W / 2f; val cy = hy + 18f
        if (open) { val tmp = Buf(W, H, b.s); Critters.draw(tmp, id, stage, cx, (hy + hh - 4).toFloat(), Critters.cut(Critters.BIG, b.s), t, blink, mouth || (id == "caps" && (t % 2f) < .9f)); b.blit(tmp, 0, 0, clipY = hy + hh - 4) }
        else if ((t % 2.4f) < .25f) { // something rustles in the dark
            for (ex in floatArrayOf(cx - 3.5f, cx + 3.5f)) b.blob(ex, cy + .5f, .7f, .7f, rgbs(0xe8a838, 0xffd36b, 0xfff0b0), null)
        }
        b.rect(hx - 1, hy + hh - 4, hw + 2, 2, shade(set.plate, .3f)); b.rect(hx - 1, hy + hh - 2, hw + 2, 1, shade(set.plate, -.3f))
        // the keycap above, raised by [lift]
        val type = if (id == "enter" || id == "esc") 'x' else if (id == "space") 'a' else 'm'
        val p = when (type) { 'a' -> CapPal(set.a, set.la); 'x' -> CapPal(set.x, set.lx); else -> CapPal(set.m, set.lm) }
        val capY = (hy - 2 - lift * 30).roundToInt().coerceAtLeast(-12)
        if (lift > .05f) b.rect(hx + 3, hy - 1, hw - 6, 3, 0, .35f * lift)
        Board.drawCap(b, hx, capY, hw, 26, p, LAB[id] ?: "", 0f)
        if (!open && lift < .05f) { val wob = if ((t % 2.4f) < .25f) 1 else 0; b.rect(hx + 4, hy + 26 + wob, hw - 8, 1, 0, .3f) }
    }

    private fun furnish(b: Buf, id: String, hx: Int, hy: Int, hw: Int, hh: Int, t: Float) {
        val floor = hy + hh - 5
        when (id) {
            "space" -> {
                for (x in hx + 6 until hx + hw - 6) { val y = hy + 3 + (abs(sin((x - hx) / 16f)) * 2).roundToInt(); b.p(x, y, rgb(0x1a1210))
                    if ((x - hx) % 8 == 0) b.p(x, y + 1, rgbs(0xffd36b, 0xff8fae, 0x8fe8d8)[((x - hx) / 8) % 3]) }
                listOf(rgb(0x7a4a8a), rgb(0x3f7a6a), rgb(0xc8913a)).forEachIndexed { i, c -> b.rect(hx + 8 + i, floor - 3 - i * 3, 14 - i * 2, 3, c); b.rect(hx + 8 + i, floor - 3 - i * 3, 14 - i * 2, 1, shade(c, .3f)) }
                val lx = hx + hw - 18; b.rect(lx + 2, floor - 10, 1, 9, rgb(0x3a3020)); b.rect(lx, floor - 1, 5, 1, rgb(0x3a3020)); b.rect(lx - 1, floor - 13, 7, 3, rgb(0xe8a840)); b.rect(lx, floor - 13, 5, 1, rgb(0xffe6a0))
                b.blob(lx + 2f, floor - 6f, 9f, 6f, rgbs(0xffd080, 0xffe6a0), null, alpha = .22f)
                for (x in hx + 30 until hx + hw - 30) b.p(x, floor + 1, if (x % 4 < 2) rgb(0x9a3b3b) else rgb(0xc8913a))
            }
            "enter" -> { // a feast for every finished chapter
                b.rect(hx + 6, hy + 4, hw - 12, 7, rgb(0x2f4f8f)); b.rect(hx + 6, hy + 4, hw - 12, 1, rgb(0x6a8ad0))
                if (b.s == 1) b.blit(Glyphs.text("¶", rgb(0xfff3e0)), hx + hw / 2 - 3, hy + 3) else b.blit(Glyphs.text("¶", rgb(0xfff3e0), 7f, b.s, smooth = true), hx + hw / 2 - 2, hy + 3)
                for (i in 0 until hw - 12 step 6) { b.p(hx + 6 + i, hy + 11, rgb(0x2f4f8f)); b.p(hx + 7 + i, hy + 12, rgb(0x2f4f8f)) }
                val cx = hx + 10; b.rect(cx, floor - 6, 12, 5, rgb(0xf6dcc0)); b.rect(cx, floor - 6, 12, 1, rgb(0xffffff)); b.rect(cx, floor - 3, 12, 1, rgb(0xe88aa0)); b.rect(cx - 1, floor - 1, 14, 1, rgb(0xd8d8e0))
                for (k in 0 until 3) { b.p(cx + 2 + k * 4, floor - 8, rgb(0xf0f0f0)); b.p(cx + 2 + k * 4, floor - 7, rgb(0xf0f0f0)); if (sin(t * 9 + k) > -.3f) b.p(cx + 2 + k * 4, floor - 9, rgb(0xffc040)) }
            }
            "bksp" -> { // eraser crumbs and crossed-out drafts
                for (i in 0 until 26) { val x = hx + 6 + ((i * 37) % (hw - 12)); val y = floor - ((i * 13) % 3); b.p(x, y, if (i % 3 == 0) rgb(0xff9e98) else rgb(0xe86a6a)) }
                val px = hx + 6; b.rect(px, hy + 6, 14, 18, rgb(0xefe6cf)); for (j in 0 until 5) { b.rect(px + 2, hy + 9 + j * 3, 10, 1, rgb(0x8a8aa0)); b.rect(px + 1, hy + 9 + j * 3, 12, 1, rgb(0xc8553d), .7f) }
                b.rect(hx + hw - 16, floor - 2, 11, 2, rgb(0xf2c14e)); b.rect(hx + hw - 16, floor - 2, 2, 2, rgb(0xe88a9a)); b.p(hx + hw - 5, floor - 1, rgb(0x2e2e34))
            }
            "caps" -> { // a stage with a microphone; notes float up while it sings
                val mx = hx + hw - 14; b.rect(mx, floor - 14, 1, 13, rgb(0x8a8a96)); b.rect(mx - 2, floor - 1, 5, 1, rgb(0x5a5a66)); b.blob(mx + .5f, floor - 16f, 2f, 2.5f, rgbs(0x3a3a46, 0x6a6a76, 0xa8a8b4), rgb(0x1a1a20))
                for (k in 0 until 3) { val ph = (t * .6f + k / 3f) % 1f; val nx = hx + 12 + k * 7 + (sin(ph * 6) * 2).roundToInt(); val ny = (floor - 6 - ph * 22).roundToInt()
                    val a = 1 - ph; b.rect(nx, ny, 2, 2, rgb(0xffe07a), a); b.rect(nx + 1, ny - 4, 1, 4, rgb(0xffe07a), a) }
                for (x in hx + 4 until hx + hw - 4) b.p(x, floor + 1, rgb(0x7a2a3a))
            }
            "tab" -> { // the plan: index cards on strings
                for (k in 0 until 5) { val x = hx + 6 + k * ((hw - 18) / 4); val y = hy + 6 + (k % 2) * 3
                    b.rect(x, y, 9, 7, rgb(0xfdf6e0)); b.rect(x, y, 9, 1, rgb(0xe8553d)); b.rect(x + 1, y + 3, 6, 1, rgb(0x8a8aa0)); b.rect(x + 1, y + 5, 4, 1, rgb(0x8a8aa0))
                    if (k < 4) for (i in 0 until (hw - 18) / 4) b.p(x + 9 + i, y + 2, rgb(0x6a5040)) }
                for (i in 0 until hw - 12) { b.p(hx + 6 + i, floor, rgb(0xe8c860)); if (i % 4 == 0) b.p(hx + 6 + i, floor - 1, rgb(0x6a5020)) }
            }
            "shift" -> { // a moon night-light and twinkling stars
                b.blob(hx + 12f, hy + 10f, 5f, 5f, rgbs(0xe8d890, 0xfff0b0, 0xfffbe0), null); b.blob(hx + 14f, hy + 9f, 4f, 4f, rgbs(0x0e0a10, 0x141018), null)
                for (k in 0 until 7) { val x = hx + 24 + (k * 11) % (hw - 28); val y = hy + 3 + (k * 7) % 10; if (sin(t * 2.3f + k * 1.7f) > 0) { b.p(x, y, rgb(0xfff6c0)); b.p(x - 1, y, rgb(0xfff6c0), .4f); b.p(x + 1, y, rgb(0xfff6c0), .4f) } }
                val cx = hx + hw - 14; b.rect(cx, floor - 5, 6, 5, rgb(0xd8d8e0)); b.rect(cx + 1, floor - 5, 4, 1, rgb(0x6a3a1a)); b.p(cx + 6, floor - 4, rgb(0xd8d8e0)); b.p(cx + 6, floor - 3, rgb(0xd8d8e0))
                if ((t % 1.5f) < 1f) b.p(cx + 2, floor - 7 - ((t % 1.5f) * 3).roundToInt(), rgb(0xffffff), .5f)
            }
            "esc" -> { // packed for a day off: a suitcase with stickers and a train ticket
                val sx = hx + 5; b.rect(sx, floor - 10, 16, 10, rgb(0x8a4a2a)); b.rect(sx, floor - 10, 16, 1, rgb(0xb86a3a)); b.rect(sx + 6, floor - 12, 4, 2, rgb(0x3a2010))
                b.rect(sx + 2, floor - 7, 4, 3, rgb(0x6aa8d8)); b.rect(sx + 9, floor - 6, 4, 3, rgb(0xf2c14e))
                val tx = hx + hw - 18; b.rect(tx, floor - 4, 12, 4, rgb(0xf6dcc0)); b.rect(tx + 3, floor - 4, 1, 4, rgb(0xc8553d))
                for (x in hx + 24 until hx + hw - 22) { val y = hy + 8 + (sin((x - hx - 24) / ((hw - 46f) / 3.14f)) * 4).roundToInt(); b.p(x, y, rgb(0xc8b090)) }
            }
        }
    }

    /** The map's rows: the desk's keyboard from the top of its case to the bottom, in art pixels. */
    const val MAP_TOP = Desk.CASE_TOP; const val MAP_H = Desk.CASE_BOTTOM - Desk.CASE_TOP

    private var base: Buf? = null; private var baseKey = ""

    /**
     * The keyboard with every resident's home key marked, for the residents map: the same keyboard as on the
     * desk ([Desk.keyboard], drawn once per set and kept), with the same caps; [b] is [MAP_H] art rows tall.
     */
    fun keyboard(b: Buf, caps: Map<Key, Board.Caps>, set: CapSet, homes: Map<String, Boolean>, sel: String?, t: Float) {
        val key = "${set.id}|${b.w}|${b.h}|${b.s}"
        val bb = base?.takeIf { baseKey == key } ?: Buf(b.w, b.h, b.s).also { Desk.keyboard(it, set, MAP_TOP); base = it; baseKey = key }
        b.copyFrom(bb)
        val y0 = MAP_TOP
        for (k in Board.keys) {
            val home = k.id?.takeIf { homes.containsKey(it) }
            val lifted = home != null && home == sel
            val cap = caps[k] ?: continue
            val y = k.y - y0 + (if (lifted) -4 else 0)
            if (lifted) b.rect(k.x, k.y - y0, k.w, k.h, rgb(0x96592a))
            b.blit(cap.up, k.x, y)
            if (home != null) { val open = homes.getValue(home)
                val glow = if (open) rgb(0xffd36b) else rgb(0x8a7a9a); val pulse = if (lifted) 1f else .55f + .45f * sin(t * 3 + k.x)
                b.blob(k.x + k.w / 2f, k.y - y0 + k.h + 1.5f, 2.2f, .7f, intArrayOf(glow), null, alpha = pulse) }
        }
    }
}
