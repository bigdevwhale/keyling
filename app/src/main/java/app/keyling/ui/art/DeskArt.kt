package app.keyling.ui.art

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
    val SLOT_MUG = 180 to 104; val SLOT_PLANT = 124 to 196; val SLOT_LAMP = 112 to 80; val SLOT_MASCOT = 110 to 236
    val STACK_X = 196; val STACK_W = 46; val STACK_BASE = 284

    private class Surf(val ramp: IntArray, val planks: Boolean, val seam: Int)
    private val SURF = mapOf(
        "oak" to Surf(rgbs(0x1a100b, 0x26170f, 0x342014, 0x45291a, 0x583420, 0x6c4127, 0x7f4f2f), true, rgb(0x140c08)),
        "walnut" to Surf(rgbs(0x0b0604, 0x130b07, 0x1c100a, 0x26160d, 0x321c11, 0x402416, 0x4e2d1c), true, rgb(0x070403)),
        "white" to Surf(rgbs(0x4e4840, 0x6a645b, 0x878075, 0xa39c90, 0xbcb5a8, 0xd2ccbf, 0xe4dfd4), true, rgb(0x5a544b)),
        "baize" to Surf(rgbs(0x06100a, 0x0b1a12, 0x11261a, 0x183222, 0x203f2b, 0x2a4d35, 0x355c40), false, 0),
    )

    fun surface(b: Buf, kind: String, lx: Float = 60f, ly: Float = -30f, rad: Float = 470f) {
        val s = SURF[kind] ?: SURF.getValue("oak")
        for (y in 0 until b.h) { val pl = y / 25
            for (x in 0 until b.w) {
                val l = max(0f, 1 - hypot(x - lx, y - ly) / rad)
                if (s.planks) {
                    if (y % 25 == 0 || x == (pl * 97 + 40) % W) { b.p(x, y, s.seam); continue }
                    val gr = .5f + .5f * sin(x * .06f + sin(y * .21f + pl * 1.9f) * 2.4f + pl * 2.7f)
                    b.p(x, y, pick(s.ramp, .06f + l * .8f + (gr - .5f) * .22f, x, y))
                } else {
                    val n = (((x * 374761393 + y * 668265263) xor (x * y)).toLong() and 0xFFFFFFFFL) % 100 / 100f
                    b.p(x, y, pick(s.ramp, .08f + l * .8f + (n - .5f) * .14f, x, y))
                }
            }
        }
    }

    private fun Buf.shadow(x: Int, y: Int, w: Int, h: Int, a: Float = .4f) = rect(x, y, w, h, 0, a)
    private val INK = rgb(0x3d3a5c)
    private fun Buf.ink(r: Rng, x0: Int, x1: Int, y: Int, dense: Float = .75f) {
        var x = x0
        while (x < x1 - 3) { val wl = 3 + (r.next() * 7).toInt(); var i = 0
            while (i < wl && x < x1) { if (r.next() < dense) p(x, y - 1 - if (r.next() < .35f) 1 else 0, INK); if (r.next() < .2f) p(x, y - 3, INK); i++; x++ }
            x += 2 }
    }

    private fun Buf.notebook(r: Rng, nx: Int, ny: Int) {
        val nw = 92; val nh = 84; shadow(nx + 3, ny + 4, nw, nh, .45f)
        rect(nx - 2, ny - 2, nw + 4, nh + 4, rgb(0x22304f)); rect(nx - 2, ny - 2, nw + 4, 1, rgb(0x3a4f7a))
        val paper = rgbs(0xb9a986, 0xcfc09d, 0xe2d6b8, 0xefe6cf, 0xf8f1df); val sp = nx + nw / 2f
        for (y in ny until ny + nh) for (x in nx until nx + nw) { val ds = abs(x + .5f - sp) / (nw / 2f); val l = 1 - hypot(x - 50f, y - 20f) / 300f
            p(x, y, pick(paper, .25f + min(1f, ds * 3) * .5f + l * .25f, x, y)) }
        rect(sp.toInt() - 1, ny, 2, nh, rgb(0xa8987a))
        var yy = ny + 12; while (yy < ny + nh - 3) { rect(nx + 2, yy, nw / 2 - 4, 1, rgb(0xc3cdd8)); rect(sp.toInt() + 3, yy, nw / 2 - 5, 1, rgb(0xc3cdd8)); yy += 6 }
        rect(nx + 7, ny + 2, 1, nh - 4, rgb(0xe2a6a0))
        ink(r, nx + 10, nx + 36, ny + 8, .95f); rect(nx + 10, ny + 9, 24, 1, INK)
        for (i in 0 until 12) { val y = ny + 12 + i * 6; ink(r, nx + 10, sp.toInt() - 3 - (if (i == 11) 20 else 0), y); if (i < 8) ink(r, sp.toInt() + 4, nx + nw - 3 - (if (i == 7) 30 else 0), y) }
        rect(nx + nw - 31, ny + 12 + 7 * 6 - 4, 1, 4, rgb(0xc8553d))
        for (i in 0 until 6) rect(nx + nw - 6 + i, ny + nh - 1 - i, 6 - i, 1, if (i % 2 == 1) rgb(0xd8ccae) else rgb(0xc8b998))
        val py = ny + nh + 8; val p0 = nx + 16; shadow(p0 + 2, py + 3, 70, 3, .45f)
        for (x in p0 until p0 + 68) { val t = x - p0
            val cols = when { t < 5 -> rgbs(0xc86a7a, 0xe88a9a, 0xb05060); t < 8 -> rgbs(0xd8d8e0, 0xb0b0bc, 0x80808c); t < 60 -> rgbs(0xffe08a, 0xf2c14e, 0xc9962e); else -> null }
            if (cols != null) { p(x, py, cols[0]); p(x, py + 1, cols[1]); p(x, py + 2, cols[2]); p(x, py - 1, rgb(0x3a2a18)); p(x, py + 3, rgb(0x3a2a18)) }
            else { val k = t - 60; val hg = max(0, 3 - floor(k / 2.6f).toInt()); for (j in 0..hg) p(x, py + 1 - hg / 2 + j, if (k > 5) rgb(0x2e2e34) else rgb(0xe8c9a0)) }
        }
    }

    private fun Buf.pot(x0: Int, y0: Int, w: Int, h: Int, ramp: IntArray, rim: Int) {
        for (y in y0 until y0 + h) { val t = (y - y0).toFloat() / h; val inset = (t * w * .12f).roundToInt()
            for (x in x0 + inset until x0 + w - inset) { val u = (x - x0).toFloat() / w; p(x, y, pick(ramp, .95f - abs(u - .3f) * 1.5f - t * .2f, x, y)) } }
        for (x in x0 - 1 until x0 + w + 1) { p(x, y0, rim); p(x, y0 + 1, pick(ramp, .85f - abs((x - x0).toFloat() / w - .3f) * 1.2f, x, y0 + 1)); p(x, y0 + 2, ramp[0]) }
        blob(x0 + w / 2f, y0.toFloat(), w / 2f - 1, 2f, rgbs(0x1e120a, 0x2e1c10, 0x3e2616), null)
    }

    fun plant(b: Buf, kind: String, px0: Int, pt: Int) = with(b) {
        when (kind) {
            "cactus" -> { shadow(px0 + 6, pt + 14, 26, 6); pot(px0, pt, 26, 20, rgbs(0x5a2216, 0x82381f, 0xa8502e, 0xc86c44, 0xe08a60), rgb(0xe8a07a))
                val lf = rgbs(0x14301a, 0x245a2c, 0x3d8438, 0x62ad4c, 0x97d670)
                for ((dx, dy, rx, ry) in listOf(listOf(5, -14, 4, 9), listOf(20, -16, 4, 10), listOf(12, -22, 4, 12), listOf(1, -6, 7, 3), listOf(25, -7, 7, 3), listOf(16, -10, 3, 8), listOf(8, -10, 3, 8)))
                    blob((px0 + dx).toFloat(), (pt + dy).toFloat(), rx.toFloat(), ry.toFloat(), lf, rgb(0x0e2012))
                for ((dx, dy) in listOf(12 to -33, 20 to -25, 5 to -22)) { p(px0 + dx, pt + dy, rgb(0xff8fb0)); p(px0 + dx + 1, pt + dy, rgb(0xffc0d0)); p(px0 + dx, pt + dy - 1, rgb(0xff8fb0)) } }
            "succulent" -> { shadow(px0 + 8, pt + 10, 20, 5); pot(px0 + 4, pt + 2, 18, 14, rgbs(0x8a8a90, 0xb4b4ba, 0xd8d8dc, 0xf0f0f2, 0xffffff), rgb(0xffffff))
                val lf = rgbs(0x1f3e36, 0x2f5a4a, 0x4a8a72, 0x74b89a, 0xa8dcc0)
                for (i in 0 until 9) { val a = i / 9f * PI.toFloat() * 2; blob(px0 + 13 + cos(a) * 6, pt - 1 + sin(a) * 2.6f, 3.4f, 2.2f, lf, rgb(0x14261f)) }
                for (i in 0 until 5) { val a = i / 5f * PI.toFloat() * 2 + .3f; blob(px0 + 13 + cos(a) * 3, pt - 2 + sin(a) * 1.4f, 2.6f, 1.8f, lf, rgb(0x14261f)) }
                blob(px0 + 13f, pt - 2f, 2f, 1.5f, rgbs(0x74b89a, 0xa8dcc0, 0xd8f0e0), rgb(0x14261f))
                for (i in 0 until 9) { val a = i / 9f * PI.toFloat() * 2; p((px0 + 13 + cos(a) * 9).roundToInt(), (pt - 1 + sin(a) * 3.6f).roundToInt(), rgb(0xe88aa0)) } }
            "monstera" -> { shadow(px0 + 6, pt + 14, 28, 6); pot(px0, pt, 26, 20, rgbs(0x1c2228, 0x2a333c, 0x3a4652, 0x4e5c6a, 0x6a7a88), rgb(0x7a8a98))
                val lf = rgbs(0x0e2416, 0x18402a, 0x25603c, 0x36804e, 0x56a868)
                for (i in 0 until 5) p(px0 + 13, pt - 1 - i, rgb(0x2a5a30))
                for ((dx, dy, rx, ry) in listOf(listOf(-4, -14, 9, 6), listOf(16, -17, 10, 6), listOf(6, -26, 9, 7), listOf(22, -6, 8, 4), listOf(-2, -4, 7, 4))) {
                    val cx = px0 + dx + 4f; val cy = pt + dy.toFloat(); blob(cx, cy, rx.toFloat(), ry.toFloat(), lf, rgb(0x08180e))
                    for (i in -rx + 2 until rx - 1) p((cx + i).roundToInt(), (cy + i * .15f).roundToInt(), rgb(0x1a4a28))
                    for (k in intArrayOf(-1, 1)) for (j in 0 until 3) p((cx + k * (rx * .5f)).roundToInt(), (cy - ry + 1 + j).roundToInt(), rgb(0x08180e))
                } }
        }
    }

    /** [custom] is a 28×33 RGB picture (the user's own mug print), or null. */
    fun mug(b: Buf, kind: String, mx: Int, my: Int, custom: IntArray?) = with(b) {
        fun handle(ramp: IntArray, inner: Float = .35f) { for (y in my + 8 until my + 30) for (x in mx + 26 until mx + 35) { val dx = (x - (mx + 27)) / 7f; val dy = (y - (my + 19)) / 10f; val d = dx * dx + dy * dy; if (d < 1 && d > inner) p(x, y, pick(ramp, .6f - dx * .4f, x, y)) } }
        // a cylinder: the bottom edge is an arc, not a straight line
        fun inBody(x: Int, y: Int): Boolean { if (y <= my + 33) return true; val dx = (x + .5f - (mx + 14f)) / 14f; val dy = (y - (my + 33f)) / 4.5f; return dx * dx + dy * dy <= 1f }
        fun body(ramp: IntArray?, f: ((Int, Int, Float) -> Int)? = null) { for (y in my + 3 until my + 38) for (x in mx until mx + 28) { val u = (x - mx) / 27f
            if (!inBody(x, y)) continue
            p(x, y, f?.invoke(x, y, u) ?: pick(ramp!!, 1.05f - abs(u - .28f) * 1.7f - (if (y > my + 33) .3f else 0f), x, y)) } }
        fun rim(rr: IntArray, inner: IntArray) { blob(mx + 13.5f, my + 3f, 14f, 4f, rr, shade(rr[0], -.5f)); blob(mx + 13.5f, my + 3.5f, 11.5f, 2.6f, inner, null) }
        val tea = rgbs(0x1e100a, 0x2e1a12, 0x4a2c1c, 0x7a4a2e)
        if (kind == "thermo") { shadow(mx + 8, my + 36, 22, 5, .45f)
            val st = rgbs(0x3a3e46, 0x5a606a, 0x8a929e, 0xc0c8d2, 0xeef2f6)
            for (y in my - 4 until my + 38) for (x in mx + 3 until mx + 25) { val u = (x - mx - 3) / 21f; p(x, y, pick(st, 1 - abs(u - .3f) * 1.8f + (if (abs(u - .62f) < .04f) .6f else 0f), x, y)) }
            for (y in my - 10 until my - 3) for (x in mx + 3 until mx + 25) { val u = (x - mx - 3) / 21f; p(x, y, pick(rgbs(0x0e0e12, 0x1e1e24, 0x34343c, 0x4a4a54), .9f - abs(u - .3f) * 1.6f + (if (y == my - 10) .2f else 0f), x, y)) }
            rect(mx + 8, my - 11, 12, 2, rgb(0x1e1e24)); rect(mx + 9, my - 11, 10, 1, rgb(0x4a4a54))
            rect(mx + 7, my + 14, 14, 10, rgb(0xf2c14e)); rect(mx + 7, my + 14, 14, 1, rgb(0xffe08a)); rect(mx + 9, my + 17, 10, 1, rgb(0x7a4a1a)); rect(mx + 9, my + 20, 7, 1, rgb(0x7a4a1a))
            return@with }
        shadow(mx + 6, my + 36, 30, 5, .45f)
        when (kind) {
            "heart" -> { val m = rgbs(0x4a1c14, 0x7a2e1f, 0xa8432c, 0xc8553d, 0xe27a5c, 0xf6a685); handle(m); body(m); rim(rgbs(0xe27a5c, 0xf6a685, 0xffd0b8), tea)
                for ((i, j) in listOf(1 to 0, 2 to 0, 4 to 0, 5 to 0, 0 to 1, 1 to 1, 2 to 1, 3 to 1, 4 to 1, 5 to 1, 6 to 1, 1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2, 2 to 3, 3 to 3, 4 to 3, 3 to 4)) p(mx + 7 + i, my + 17 + j, rgb(0xfbe6d8)) }
            "enamel" -> { val en = rgbs(0x6a7078, 0x9aa0a8, 0xc8ccd2, 0xe8eaee, 0xffffff); handle(en); body(en)
                rect(mx, my + 3, 28, 2, rgb(0x2f4f8f)); rect(mx, my + 35, 28, 1, rgb(0x2f4f8f)); rim(rgbs(0x2f4f8f, 0x4a6ab0, 0x6a8ad0), tea)
                for ((i, j) in listOf(4 to 22, 5 to 22, 20 to 12, 21 to 30)) p(mx + i, my + j, rgb(0x2a2a30))
                blob(mx + 12f, my + 20f, 4f, 3f, rgbs(0xc8553d, 0xe27a5c), rgb(0x8a2a1a)); rect(mx + 12, my + 16, 1, 2, rgb(0x3d8438)) }
            "glass" -> { val sil = rgbs(0x3a3a42, 0x6a6a76, 0x9a9aa8, 0xc8c8d4, 0xeeeef6); handle(sil, .45f)
                fun rimY(x: Int) = my + 15 + (2.4f * kotlin.math.sqrt((1f - ((x + .5f - (mx + 14f)) / 14f).let { it * it }).coerceAtLeast(0f))).roundToInt()
                body(null) { x, y, u ->
                    if (y < rimY(x)) { val tv = 1 - abs(u - .3f) * 1.6f
                        if (y < my + 6) pick(rgbs(0xc88a50, 0xe8b070, 0xf6d6a8), tv, x, y) else pick(rgbs(0x5a2008, 0x8a3a10, 0xb85a1a, 0xd8803a), tv + (if (abs(u - .2f) < .05f) .5f else 0f), x, y) }
                    else { val orn = (x + y) % 4 == 0 || (x - y) % 4 == 0; pick(sil, 1.05f - abs(u - .28f) * 1.7f - (if (orn) .25f else 0f) - (if (y > my + 33) .3f else 0f), x, y) } }
                // the holder's curved rim, a bead along it, and a flared foot
                for (x in mx until mx + 28) { val y = rimY(x); p(x, y, rgb(0xeeeef6)); p(x, y + 1, rgb(0x9a9aa8)); if ((x - mx) % 3 == 1) p(x, y + 2, rgb(0xd8d8e4)) }
                for (x in mx - 1 until mx + 29) { val dx = (x + .5f - (mx + 14f)) / 15f; if (dx * dx > 1f) continue
                    val y = my + 36 + (1.6f * kotlin.math.sqrt(1 - dx * dx)).roundToInt(); p(x, y - 1, rgb(0xc8c8d4)); p(x, y, rgb(0x6a6a76)) }
                rim(rgbs(0xd8d8e0, 0xf6f6fa, 0xffffff), rgbs(0x5a2008, 0x8a3a10, 0xb85a1a))
                for (i in 0 until 14) { p(mx + 18 + i / 2, my + 2 - i, rgb(0xd0d0dc)); p(mx + 19 + i / 2, my + 2 - i, rgb(0x8a8a96)) }
                blob(mx + 9f, my + 4f, 3.5f, 1.6f, rgbs(0xe8c840, 0xfff080), rgb(0xa88a10)) }
            "custom" -> { val white = rgbs(0x8a8a90, 0xb4b4ba, 0xd8d8dc, 0xf0f0f2, 0xffffff); handle(white)
                if (custom == null) body(white) else body(null) { x, y, u ->
                    val py = (y - my - 5).coerceIn(0, 32); val c = custom[py * 28 + (x - mx)]
                    val k = (1.15f - abs(u - .28f) * 1.3f - (if (y > my + 33) .25f else 0f)).coerceIn(.35f, 1.25f); scale(c, k) }
                rim(rgbs(0xd8d8dc, 0xf0f0f2, 0xffffff), tea) }
        }
    }

    fun lamp(b: Buf, kind: String, x: Int, y: Int) = with(b) {
        if (kind != "banker") return@with
        shadow(x + 14, y + 48, 34, 5)
        blob(x + 26f, y + 46f, 14f, 4f, rgbs(0x5a3a10, 0x8a5a1a, 0xc8913a, 0xf0c86a), rgb(0x3a2408))
        for (j in y + 16 until y + 44) { p(x + 25, j, rgb(0xf0c86a)); p(x + 26, j, rgb(0xc8913a)); p(x + 27, j, rgb(0x8a5a1a)) }
        val gr = rgbs(0x0a2214, 0x123a22, 0x1a5432, 0x256e44, 0x3a9060, 0x7acc98)
        for (j in 0 until 14) { val half = 14 + j * .75f; var i = -half
            while (i < half) { val xx = (x + 26 + i).roundToInt(); val yy = y + 4 + j; val u = (i + half) / (2 * half)
                p(xx, yy, if (j == 0) rgb(0x0a2214) else pick(gr, .95f - j / 16f * .6f - abs(u - .35f) * .8f + (if (j == 2) .3f else 0f), xx, yy)); i += 1f } }
        rect(x + 15, y + 18, 23, 1, rgb(0xffe6a0)); rect(x + 13, y + 18, 2, 1, rgb(0xc8913a)); rect(x + 38, y + 18, 2, 1, rgb(0xc8913a))
        for (j in 0 until 8) p(x + 40, y + 19 + j, rgb(0xc8913a)); blob(x + 40f, y + 28f, 1.5f, 1.5f, rgbs(0xc8913a, 0xf0c86a), rgb(0x5a3a10))
    }

    fun mascot(b: Buf, kind: String, x: Int, y: Int) = with(b) {
        when (kind) {
            "cat" -> { shadow(x + 4, y + 24, 34, 4)
                val fur = rgbs(0x5a2a10, 0x8a4a1c, 0xb86a2a, 0xe09848, 0xf6c47a)
                for (i in 0 until 18) { val a = PI.toFloat() * (.1f + i / 18f * .9f); p((x + 20 + cos(a) * 16).roundToInt(), (y + 22 + sin(a) * 3).roundToInt(), rgb(0x8a4a1c)); p((x + 20 + cos(a) * 16).roundToInt(), (y + 21 + sin(a) * 3).roundToInt(), rgb(0xe09848)) }
                blob(x + 22f, y + 15f, 14f, 8f, fur, rgb(0x2a1408))
                for (i in 0 until 4) rect(x + 18 + i * 4, y + 9, 2, 3, rgb(0x8a4a1c))
                blob(x + 9f, y + 14f, 7.5f, 6.5f, fur, rgb(0x2a1408))
                for (dx in intArrayOf(3, 12)) for (j in 0 until 4) for (i in 0 until 4 - j) p(x + dx + i + (if (dx > 6) j else 0), y + 6 + 3 - j - 1, if (j == 3 - i || i == 0) rgb(0x2a1408) else rgb(0xe09848))
                p(x + 6, y + 15, rgb(0x2a1408)); p(x + 7, y + 16, rgb(0x2a1408)); p(x + 11, y + 15, rgb(0x2a1408)); p(x + 12, y + 16, rgb(0x2a1408)); p(x + 9, y + 17, rgb(0xc86a6a)) }
            "candle" -> { shadow(x + 14, y + 28, 22, 4)
                blob(x + 20f, y + 28f, 10f, 3f, rgbs(0x5a3a10, 0x8a5a1a, 0xc8913a, 0xf0c86a), rgb(0x3a2408))
                for (j in y + 10 until y + 27) for (i in 0 until 7) p(x + 17 + i, j, pick(rgbs(0xc8b890, 0xe8dcc0, 0xf8f0dc, 0xfffaf0), .9f - abs(i / 6f - .3f) * 1.4f, x + 17 + i, j))
                rect(x + 17, y + 10, 7, 1, rgb(0xfffaf0)); p(x + 21, y + 11, rgb(0xe8dcc0)); p(x + 21, y + 12, rgb(0xe8dcc0)); p(x + 20, y + 9, rgb(0x2a2a2a)) }
            "globe" -> { shadow(x + 12, y + 28, 24, 4)
                blob(x + 20f, y + 28f, 9f, 2.5f, rgbs(0x3a2010, 0x5a3418, 0x7a4a22), rgb(0x1a0e06)); rect(x + 19, y + 22, 3, 5, rgb(0x7a4a22))
                val oc = rgbs(0x0e2a4a, 0x1a4a7a, 0x2a6aa8, 0x4a90c8, 0x8ac0e8); val land = rgbs(0x1e4a1e, 0x3a7a30, 0x6aa848, 0xa8d070)
                for (j in -10..10) for (i in -10..10) { if (i * i + j * j > 100) continue; val xx = x + 20 + i; val yy = y + 12 + j
                    val isLand = sin(i * .55f + j * .2f) + cos(j * .6f - i * .15f) > 1.1f; val v = .6f - i / 10f * .3f - j / 10f * .35f
                    p(xx, yy, if (i * i + j * j > 90) rgb(0x0a1a2a) else pick(if (isLand) land else oc, v, xx, yy)) }
                for (a in 0..20) { val t = PI.toFloat() * (a / 20f) - PI.toFloat() / 2; p((x + 20 + cos(t) * 12).roundToInt(), (y + 12 + sin(t) * 12).roundToInt(), rgb(0xc8913a)) } }
        }
    }

    /** The still part of the desk scene: surface, things, keyboard case and plate, the nook under the space bar. */
    fun background(decor: Decor, set: CapSet, custom: IntArray?): Buf {
        val b = Buf(W, H)
        surface(b, decor.desk)
        val cab = rgbs(0x101014, 0x2a2a33, 0x44444f, 0x5a5a6a)
        var t = 0f; while (t <= 1f) { val x = (96 - t * 100 + sin(t * 9) * 3).roundToInt(); val y = (304 - sin(t * PI.toFloat() / 2) * 80).roundToInt()
            for (j in 0 until 4) b.p(x, y + j, cab[when (j) { 0 -> 3; 3 -> 0; 1 -> 2; else -> 1 }]); t += .0015f }
        // keyboard case
        val c = set.case; val cr = intArrayOf(shade(c, -.55f), shade(c, -.35f), shade(c, -.18f), c, shade(c, .2f), shade(c, .45f))
        val cx0 = 3; val cx1 = 257; val cy0 = 304; val cy1 = 414
        b.shadow(cx0 + 3, cy1, cx1 - cx0, 6, .5f)
        for (y in cy0 until cy1) for (x in cx0 until cx1) {
            val corner = (y < cy0 + 2 && (x < cx0 + 2 || x > cx1 - 3)) || (y > cy1 - 3 && (x < cx0 + 2 || x > cx1 - 3)); if (corner) continue
            val l = 1 - hypot(x - 60f, y - 250f) / 420f
            val v: Float? = when {
                y < cy0 + 2 -> .92f; y < 312 -> .6f + l * .35f; y < 398 && (x < 7 || x > 252) -> if (x < 7) .66f + l * .2f else .42f
                y < 316 -> .18f; y < 398 -> null; y < 406 -> .58f + l * .3f; y < 407 -> .95f; else -> .3f - (y - 407) / 7f * .25f
            }
            if (v != null) b.p(x, y, pick(cr, v, x, y))
        }
        b.rect(206, 306, 30, 4, shade(c, -.4f)); b.rect(207, 306, 28, 3, rgb(0xc9c3b4)); b.rect(207, 306, 28, 1, rgb(0xf2eee4)); for (x in intArrayOf(211, 221, 231)) b.p(x, 307, rgb(0x6a6458))
        b.p(22, 307, rgb(0x7fe08a)); b.p(28, 307, rgb(0x3a3a3a)); b.p(34, 307, rgb(0x3a3a3a))
        val pl = intArrayOf(shade(set.plate, -.4f), set.plate, shade(set.plate, .12f))
        for (y in 316 until 398) for (x in 7 until 253) b.p(x, y, pick(pl, .45f + (1 - hypot(x - 60f, y - 290f) / 300f) * .3f, x, y))
        for (k in Board.keys) if (k !== Board.space) b.rect(k.x + k.w / 2 - 3, k.y + k.h - 1, 6, 2, rgb(0x151418))
        // the nook under the space bar
        val s = Board.space; val hx = s.x; val hy = s.y; val hw = s.w; val hh = s.h
        val glow = rgbs(0x0e080a, 0x1c100c, 0x2e1a10, 0x4a2a14, 0x6e401a, 0x96592a)
        for (y in hy - 1 until hy + hh + 1) for (x in hx - 1 until hx + hw + 1) b.p(x, y, pick(glow, (1 - hypot((x - (hx + hw / 2f)) / (hw * .55f), (y - (hy + hh + 3f)) / (hh * 1.2f))) * 1.05f, x, y))
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

    /** A thing on the desk that can be moved: its picture and where it stands by default. */
    class Sprite(val id: String, val buf: Buf, val x: Int, val y: Int)

    /** Draws each desk thing on its own layer and crops it, so it can be dragged around. */
    fun sprites(decor: Decor, custom: IntArray?): List<Sprite> {
        val r = Rng(7)
        fun make(id: String, draw: (Buf) -> Unit): Sprite? {
            val full = Buf(W, H); draw(full)
            var x0 = W; var y0 = H; var x1 = -1; var y1 = -1
            for (y in 0 until H) for (x in 0 until W) if (full.d[y * W + x] ushr 24 != 0) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y }
            if (x1 < 0) return null
            val b = Buf(x1 - x0 + 1, y1 - y0 + 1); for (y in y0..y1) System.arraycopy(full.d, y * W + x0, b.d, (y - y0) * b.w, b.w)
            return Sprite(id, b, x0, y0)
        }
        return listOfNotNull(
            make("notebook") { it.notebook(r, 10, 74) },
            make("notes") { b -> val sx = 24; val sy = 196; b.shadow(sx + 2, sy + 3, 30, 28)
                for (y in sy until sy + 28) for (x in sx until sx + 30) b.p(x, y, pick(rgbs(0xe6c64a, 0xf6dc6b, 0xfff09a), .7f - (y - sy) / 60f + (x - sx) / 120f, x, y))
                b.rect(sx, sy, 30, 3, rgb(0xeccb4d)); b.ink(r, sx + 4, sx + 24, sy + 10, .9f); b.ink(r, sx + 4, sx + 18, sy + 16, .9f); b.ink(r, sx + 4, sx + 26, sy + 22, .9f) },
            make("eraser") { b -> val sx = 24; val sy = 196
                b.shadow(sx + 44, sy + 20, 16, 8); b.rect(sx + 42, sy + 16, 16, 8, rgb(0xe88a9a)); b.rect(sx + 42, sy + 16, 16, 2, rgb(0xffb0c0)); b.rect(sx + 42, sy + 22, 16, 2, rgb(0xb05868))
                b.rect(sx + 50, sy + 16, 8, 8, rgb(0x4a6ab0)); b.rect(sx + 50, sy + 16, 8, 2, rgb(0x6a8ad0)) },
            make("lamp") { lamp(it, decor.lamp, SLOT_LAMP.first, SLOT_LAMP.second) },
            make("plant") { plant(it, decor.plant, SLOT_PLANT.first, SLOT_PLANT.second) },
            make("mug") { mug(it, decor.mug, SLOT_MUG.first, SLOT_MUG.second, custom) },
            make("mascot") { mascot(it, decor.mascot, SLOT_MASCOT.first, SLOT_MASCOT.second) },
        )
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
        val b = Buf(56, 52)
        surface(b, if (cat == "desk") id else desk, 10f, -20f, 110f)
        when (cat) {
            "mug" -> if (id == "custom" && custom == null) {
                b.rect(18, 14, 20, 20, 0, .35f); for (i in 0 until 12) b.p(28, 18 + i, rgb(0xfbf3e4)); for (i in 0 until 5) { b.p(28 - i, 18 + i, rgb(0xfbf3e4)); b.p(28 + i, 18 + i, rgb(0xfbf3e4)) }
            } else mug(b, id, 12, if (id == "thermo") 14 else 8, custom)
            "plant" -> plant(b, id, 15, 34)
            "lamp" -> lamp(b, id, 2, 0)
            "mascot" -> mascot(b, id, 7, 14)
        }
        return b
    }

    /** The user's photo turned into a 28×33 posterized print for the mug. */
    fun posterize(src: IntArray): IntArray = IntArray(28 * 33) { i ->
        val x = i % 28; val y = i / 28; val c = src[i]
        fun q(v: Int) = (min(4f, floor(v / 255f * 5 + bay(x, y) - .5f + .5f)) * 63.75f).roundToInt().coerceIn(0, 255)
        Buf.OPAQUE or (q(Buf.ch(c, 16)) shl 16) or (q(Buf.ch(c, 8)) shl 8) or q(Buf.ch(c, 0))
    }
}
