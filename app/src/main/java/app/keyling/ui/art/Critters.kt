package app.keyling.ui.art

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The seven residents, each its own kind of creature, drawn from shaded shapes on a small grid.
 * Coordinates are art pixels relative to the creature's feet (x to the right, y up is negative), times [s].
 */
object Critters {
    private class Pen(val b: Buf, val cx: Float, val by: Float, val s: Float) {
        fun X(x: Float) = cx + x * s
        fun Y(y: Float) = by + y * s
        fun blob(x: Float, y: Float, rx: Float, ry: Float, ramp: IntArray, out: Int?) = b.blob(X(x), Y(y), max(.8f, rx * s), max(.8f, ry * s), ramp, out)
        fun px(x: Float, y: Float, c: Int) = b.p(X(x).roundToInt(), Y(y).roundToInt(), c)
        fun dot(x: Float, y: Float, w: Float, h: Float, c: Int) = b.rect(X(x).roundToInt(), Y(y).roundToInt(), max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()), c)
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, c: Int, th: Float = 1f) {
            val n = (max(abs(x1 - x0), abs(y1 - y0)) * s * 2).toInt() + 1; val t = max(1, (th * s).roundToInt())
            for (i in 0..n) { val k = i.toFloat() / n; b.rect((X(x0 + (x1 - x0) * k) - t / 2f).roundToInt(), (Y(y0 + (y1 - y0) * k) - t / 2f).roundToInt(), t, t, c) }
        }
        fun tri(ax: Float, ay: Float, bx: Float, byy: Float, qx: Float, qy: Float, c: Int) {
            val x0 = min(X(ax), min(X(bx), X(qx))).toInt(); val x1 = max(X(ax), max(X(bx), X(qx))).toInt() + 1
            val y0 = min(Y(ay), min(Y(byy), Y(qy))).toInt(); val y1 = max(Y(ay), max(Y(byy), Y(qy))).toInt() + 1
            fun e(px: Float, py: Float, ux: Float, uy: Float, vx: Float, vy: Float) = (vx - ux) * (py - uy) - (vy - uy) * (px - ux)
            for (y in y0..y1) for (x in x0..x1) { val fx = x + .5f; val fy = y + .5f
                val a = e(fx, fy, X(ax), Y(ay), X(bx), Y(byy)); val b1 = e(fx, fy, X(bx), Y(byy), X(qx), Y(qy)); val c1 = e(fx, fy, X(qx), Y(qy), X(ax), Y(ay))
                if ((a >= 0 && b1 >= 0 && c1 >= 0) || (a <= 0 && b1 <= 0 && c1 <= 0)) b.p(x, y, c) }
        }
        /** A glossy eye: dark oval, two white glints; shut it's a curved line. */
        fun eye(x: Float, y: Float, w: Float, h: Float, shut: Boolean, out: Int) {
            if (shut) { line(x - w / 2, y, x, y + .8f, out); line(x, y + .8f, x + w / 2, y, out); return }
            blob(x, y, w / 2, h / 2, rgbs(0x120a10, 0x22141e, 0x3a2a40), null)
            px(x - w / 4, y - h / 4, rgb(0xffffff)); if (w * s > 3.5f) px(x - w / 4 + 1 / s, y - h / 4, rgb(0xffffff)); px(x + w / 4, y + h / 4, rgb(0x8a7aa8))
        }
        fun blush(x: Float, y: Float) { for (i in 0..2) for (j in 0..1) if ((i + j) % 2 == 0) px(x + i / s - 1, y + j / s, rgb(0xff8a8a)) }
    }

    private val WHITE = rgb(0xffffff)

    /** Draws resident [id] standing at (cx, by). [t] drives breathing and idle motions. */
    fun draw(b: Buf, id: String, cx: Float, by: Float, s: Float, t: Float, blink: Boolean, mouth: Boolean) {
        val P = Pen(b, cx, by, s); val br = sin(t / .6f) * .6f
        when (id) {
            "space" -> P.dormouse(br, blink, mouth, t)
            "enter" -> P.heron(br, blink, mouth, t)
            "bksp" -> P.eraser(br, blink, mouth, t)
            "caps" -> P.frog(br, blink, mouth, t)
            "tab" -> P.inchworm(br, blink, mouth, t)
            "shift" -> P.owl(br, blink, mouth, t)
            else -> P.snail(br, blink, mouth, t)
        }
    }

    /** The same creature as a dark shape with a question mark, for residents who haven't moved in. */
    fun silhouette(b: Buf, id: String, cx: Float, by: Float, s: Float) {
        val tmp = Buf(b.w, b.h); draw(tmp, id, cx, by, s, 0f, true, false)
        val dark = rgbs(0x1c1418, 0x281e24, 0x33282e)
        for (i in tmp.d.indices) { val c = tmp.d[i]; if (c ushr 24 == 0) continue
            val l = (Buf.ch(c, 16) + Buf.ch(c, 8) + Buf.ch(c, 0)) / 765f; b.p(i % b.w, i / b.w, dark[(l * 2.99f).toInt()]) }
        b.blit(Glyphs.text("?", rgb(0x6a5560)), (cx - 3).roundToInt(), (by - 26 * s).roundToInt())
    }

    // ---------- Пробельчик: a dormouse with cheeks full of letters ----------
    private fun Pen.dormouse(br: Float, blink: Boolean, mouth: Boolean, t: Float) {
        val O = rgb(0x3a2230); val fur = rgbs(0x9c4a26, 0xc96a32, 0xec8f45, 0xffb866, 0xffe0a0); val cream = rgbs(0xe9b98a, 0xf7d3a0, 0xfff0d0)
        // tail curling up behind
        for (i in 0..14) { val a = i / 14f * 2.6f; val x = 12 + sin(a) * 7; val y = -4 - i * 1.6f
            blob(x, y, 2.6f, 2.6f, if (i > 11) rgbs(0x6a3018, 0x8a4020) else fur, O) }
        blob(0f, -12f, 15f, 11.5f + br, fur, O)                          // body
        blob(0f, -10f, 9f, 7f + br * .5f, cream, null)                    // belly
        for (sx in intArrayOf(-1, 1)) { blob(sx * 9f, -36f, 4.6f, 4.6f, fur, O); blob(sx * 9f, -36f, 2.4f, 2.6f, rgbs(0xe88080, 0xffb0b0), null) }
        blob(0f, -26f, 12f, 9.5f, fur, O)                                // head
        val puff = if (mouth) 1.2f else 0f
        for (sx in intArrayOf(-1, 1)) blob(sx * 7.5f, -21.5f, 4.8f + puff, 3.8f + puff, cream, null)  // stuffed cheeks
        eye(-4.5f, -28f, 3.4f, 4.2f, blink, O); eye(4.5f, -28f, 3.4f, 4.2f, blink, O)
        dot(-1f, -24.5f, 2f, 1.2f, rgb(0xd0607a))
        if (mouth) dot(-1f, -22.5f, 2.2f, 1.6f, rgb(0x5a2030)) else { px(-1f, -22.8f, O); px(1f, -22.8f, O) }
        for (sx in intArrayOf(-1, 1)) { line(sx * 10f, -23f, sx * 16f, -24f, rgb(0x7a4a30)); line(sx * 10f, -22f, sx * 16f, -21f, rgb(0x7a4a30)) }
        // paws holding a letter tile
        dot(-4.5f, -16f, 9f, 8f, rgb(0xf8ecd0)); dot(-4.5f, -16f, 9f, 1f, WHITE); dot(-4.5f, -8.8f, 9f, 1f, rgb(0xb8a080))
        val letterA = arrayOf(".###.", "#...#", "#####", "#...#", "#...#")
        for ((j, row) in letterA.withIndex()) for ((i, ch) in row.withIndex()) if (ch == '#') dot(-2.5f + i, -14.5f + j, 1f, 1f, rgb(0x3d3a5c))
        for (sx in intArrayOf(-1, 1)) blob(sx * 5.6f, -12f, 2f, 2.2f, fur, O)
        for (sx in intArrayOf(-1, 1)) blob(sx * 7f, -1f, 4f, 2f, fur, O)
    }

    // ---------- Абзацик: a heron with a ¶-shaped crest and a bow tie ----------
    private fun Pen.heron(br: Float, blink: Boolean, mouth: Boolean, t: Float) {
        val O = rgb(0x1a2440); val fe = rgbs(0x2c4f86, 0x4a76b8, 0x7fb0e6, 0xb5d8f7, 0xeef8ff); val leg = rgb(0xe8a040)
        for (sx in intArrayOf(-1, 1)) { line(sx * 3f, -10f, sx * 3.5f, -1f, leg, 1.2f); line(sx * 3.5f, -1f, sx * 3.5f + 3f, 0f, leg); line(sx * 3.5f, -1f, sx * 3.5f - 2f, 0f, leg) }
        blob(0f, -16f, 9f, 9.5f + br, fe, O)                                   // body
        blob(-3f, -15f, 6f, 7.5f, rgbs(0x1f3a66, 0x2c4f86, 0x4a76b8), O)       // wing
        for (i in 0..3) line(-7f + i * 1.5f, -10f - i, -4f + i * 1.5f, -9f - i, rgb(0x1a2f50))
        val sway = sin(t * 1.2f) * .8f
        for (i in 0..12) { val k = i / 12f; blob(2 + sin(k * 3.6f) * 2.5f + sway * k, -23f - k * 13, 3f, 2.6f, fe, O) } // neck
        val hx = 2 + sway; val hy = -38f
        blob(hx, hy, 5.5f, 4.6f, fe, O)
        tri(hx + 4, hy - 1.5f, hx + 4, hy + 1.2f, hx + 13, hy + .5f, rgb(0xe8a040)); line(hx + 4, hy + .3f, hx + 12, hy + .6f, rgb(0xa86a20))
        if (mouth) tri(hx + 4, hy + 1.2f, hx + 4, hy + 2.8f, hx + 10, hy + 1.6f, rgb(0xc87a30))
        eye(hx + 1f, hy - 1f, 2.6f, 2.8f, blink, O)
        // the ¶ crest
        line(hx - 2f, hy - 4f, hx - 2f, hy - 11f, O, 1.2f); line(hx + 1f, hy - 4f, hx + 1f, hy - 11f, O, 1.2f)
        blob(hx - 3.5f, hy - 9.5f, 2.6f, 2f, rgbs(0x1a2440, 0x2c4f86), O); line(hx - 3f, hy - 11.5f, hx + 2f, hy - 11.5f, O, 1.2f)
        // bow tie
        tri(2f, -27f, -3f, -29.5f, -3f, -24.5f, rgb(0xc8303a)); tri(2f, -27f, 7f, -29.5f, 7f, -24.5f, rgb(0xc8303a)); dot(1f, -28f, 2f, 2f, rgb(0x8a1a24))
    }

    // ---------- Затирыш: a school eraser in a paper sleeve ----------
    private fun Pen.eraser(br: Float, blink: Boolean, mouth: Boolean, t: Float) {
        val O = rgb(0x3a1824); val pink = rgbs(0xa83a4a, 0xd65a68, 0xf08088, 0xffb0b0, 0xffe0dc); val sl = rgbs(0x1f3f7a, 0x2f5aa8, 0x4f7ad0, 0x8fb0f0)
        val top = -26f - br; val l = -15f; val r = 15f
        for (sx in intArrayOf(-1, 1)) { dot(sx * 7f - 2, -4f, 4f, 4f, rgb(0x8a2a3a)); dot(sx * 7f - 3, -1.5f, 6f, 1.5f, rgb(0x5a1a24)) }
        // body: a rounded block with a lighter top face
        val x0 = X(l).roundToInt(); val x1 = X(r).roundToInt(); val y0 = Y(top).roundToInt(); val y1 = Y(-3f).roundToInt(); val bev = max(2, (4 * s).roundToInt())
        for (y in y0..y1) for (x in x0..x1) {
            val corner = (x - x0 < 2 || x1 - x < 2) && (y - y0 < 2 || y1 - y < 2); if (corner && (x == x0 || x == x1 || y == y0 || y == y1)) continue
            val edge = x == x0 || x == x1 || y == y0 || y == y1
            val u = (x - x0).toFloat() / (x1 - x0); val v = if (y - y0 < bev) .95f else .75f - (y - y0 - bev).toFloat() / (y1 - y0) * .4f - abs(u - .3f) * .3f
            b.p(x, y, if (edge) O else pick(pink, v, x, y))
        }
        // sleeve over the right half, with printed lines
        val sx0 = X(2f).roundToInt()
        for (y in y0 + 1 until y1) for (x in sx0 until x1) b.p(x, y, if (y - y0 < bev) sl[3] else pick(sl, .7f - (x - sx0).toFloat() / (x1 - sx0) * .4f, x, y))
        b.rect(sx0, y0 + 1, 1, y1 - y0 - 1, rgb(0xd8e4ff))
        for (k in 0..2) b.rect(sx0 + 3, Y(-18f + k * 4).roundToInt(), ((r - 6) * s).roundToInt().coerceAtMost(x1 - sx0 - 5), 1, rgb(0xd8e4ff))
        // stern face on the pink part
        eye(-9f, -15f, 3f, 3.6f, blink, O); eye(-3f, -15f, 3f, 3.6f, blink, O)
        line(-11f, -19.5f, -7.5f, -18f, O, 1.1f); line(-1f, -19.5f, -4.5f, -18f, O, 1.1f)
        if (mouth) dot(-7.5f, -11f, 3f, 2f, rgb(0x5a1a24)) else line(-8f, -10.5f, -4f, -10.5f, O)
        // a pencil stub under its arm and crumbs at the feet
        line(13f, -9f, 22f, -16f, rgb(0xf2c14e), 2f); line(21f, -15f, 23.5f, -17f, rgb(0x2e2e34), 1.2f)
        for (i in 0..5) px(-14f + i * 5.5f, -0.5f - (i % 2), if (i % 2 == 0) pink[3] else pink[2])
    }

    // ---------- Горлан: a frog with a singing throat sac ----------
    private fun Pen.frog(br: Float, blink: Boolean, mouth: Boolean, t: Float) {
        val O = rgb(0x142a10); val g = rgbs(0x2a5a1e, 0x3f8a2c, 0x62b040, 0x98d860, 0xd8f8a0); val belly = rgbs(0xc8d880, 0xe8f0a8, 0xf8fcd8)
        for (sx in intArrayOf(-1, 1)) blob(sx * 12f, -5f, 7f, 5f, g, O)        // folded back legs
        blob(0f, -11f, 15f, 10f + br, g, O)
        blob(0f, -8f, 9f, 5.5f, belly, null)
        for ((x, y) in listOf(-8f to -15f, 6f to -13f, 10f to -8f, -11f to -9f)) blob(x, y, 1.6f, 1.3f, rgbs(0x2a5a1e, 0x3f8a2c), null)
        for (sx in intArrayOf(-1, 1)) { blob(sx * 6f, -2f, 3f, 2f, g, O); for (k in -1..1) px(sx * 6f + k * 1.5f, 0f, g[3]) }
        // eyes on top
        for (sx in intArrayOf(-1, 1)) { blob(sx * 7.5f, -21f, 5f, 5f, g, O); blob(sx * 7.5f, -21.5f, 3.6f, 3.6f, rgbs(0xe8e8d0, 0xffffff), null)
            if (blink) line(sx * 7.5f - 3, -21.5f, sx * 7.5f + 3, -21.5f, O, 1.2f) else { dot(sx * 7.5f - 1, -23f, 2.4f, 3.4f, rgb(0x120a10)); px(sx * 7.5f - .6f, -22.6f, WHITE) } }
        // a wide mouth and the sac that swells as it sings
        val sing = if (mouth) 1f else (.5f + .5f * sin(t * 4)).coerceAtLeast(0f)
        line(-10f, -14f, 10f, -14f, O, 1.1f); px(-11f, -15f, O); px(11f, -15f, O)
        blob(0f, -9.5f, 5f + sing * 3f, 3f + sing * 2.4f, rgbs(0xe8c040, 0xffe070, 0xfff4b0), rgb(0x8a6a10))
        blush(-13f, -16f); blush(11f, -16f)
    }

    // ---------- Отступ: an inchworm in round glasses, measuring indents ----------
    private fun Pen.inchworm(br: Float, blink: Boolean, mouth: Boolean, t: Float) {
        val O = rgb(0x16301c); val g = rgbs(0x3a6a20, 0x5a9a30, 0x86c84a, 0xb8e878, 0xe8ffc0)
        val arch = 9f + 4f * (.5f + .5f * sin(t * 1.6f))
        val segs = 7
        for (i in 0 until segs) { val k = i / (segs - 1f); val x = -17f + k * 28f; val y = -4f - sin(k * PI.toFloat()) * arch
            blob(x, y, 4.6f, 4.4f, g, O); px(x, y - 3.5f, g[4])
            if (i == 0 || i == segs - 1) for (l in -1..1) px(x + l * 1.5f, -0.2f, O) }
        val hx = 15f; val hy = -9f
        blob(hx, hy, 6.5f, 6f, g, O)
        line(hx - 1f, hy - 6f, hx - 3f, hy - 10f, O); line(hx + 2f, hy - 6f, hx + 4f, hy - 10f, O); px(hx - 3f, hy - 10.5f, rgb(0xf2c14e)); px(hx + 4f, hy - 10.5f, rgb(0xf2c14e))
        eye(hx - 2.2f, hy - 1f, 2.2f, 2.6f, blink, O); eye(hx + 2.6f, hy - 1f, 2.2f, 2.6f, blink, O)
        // round glasses
        for (ex in floatArrayOf(hx - 2.2f, hx + 2.6f)) for (a in 0 until 16) { val an = a / 16f * 2 * PI.toFloat(); px(ex + cos(an) * 2.3f, hy - 1f + sin(an) * 2.3f, rgb(0x3a3020)) }
        line(hx - .2f, hy - 1.5f, hx + .4f, hy - 1.5f, rgb(0x3a3020))
        if (mouth) dot(hx - .5f, hy + 2.5f, 2f, 1.5f, rgb(0x2a1a10)) else { px(hx - 1f, hy + 2.8f, O); px(hx, hy + 3.2f, O); px(hx + 1f, hy + 2.8f, O) }
        blush(hx - 5f, hy + 1f)
    }

    // ---------- Полуночник: an owl in a night cap ----------
    private fun Pen.owl(br: Float, blink: Boolean, mouth: Boolean, t: Float) {
        val O = rgb(0x1a1438); val f = rgbs(0x2c2458, 0x463c8a, 0x6457b0, 0x8a7fd1, 0xc8c0f4); val face = rgbs(0xa8a0d8, 0xd0c8f0, 0xf0ecff)
        for (sx in intArrayOf(-1, 1)) { line(sx * 3f, -2f, sx * 3f, 0f, rgb(0xe8a838)); px(sx * 3f - 1, 0f, rgb(0xe8a838)); px(sx * 3f + 1, 0f, rgb(0xe8a838)) }
        blob(0f, -15f, 11.5f, 14f + br, f, O)
        for (r in 0..2) for (k in -2..2) px(k * 2.6f + (r % 2), -12f + r * 3f, f[0])                  // chest feathers
        for (sx in intArrayOf(-1, 1)) { blob(sx * 10.5f, -13f, 4f, 8.5f, rgbs(0x1e1840, 0x2c2458, 0x463c8a), O)
            for (k in 0..2) line(sx * 9f, -16f + k * 3f, sx * 12f, -14f + k * 3f, rgb(0x1a1438)) }
        tri(-9f, -24f, -5f, -26f, -9.5f, -32f, f[1]); tri(9f, -24f, 5f, -26f, 9.5f, -32f, f[1])            // ear tufts
        blob(0f, -21f, 9.5f, 7.5f, face, null)
        for (sx in intArrayOf(-1, 1)) {
            blob(sx * 4.6f, -21.5f, 4.2f, 4.2f, rgbs(0xc88a28, 0xe8a838, 0xffd36b, 0xfff0b0), O)
            if (blink) line(sx * 4.6f - 3f, -21.5f, sx * 4.6f + 3f, -21.5f, O, 1.2f)
            else { blob(sx * 4.6f, -21.5f, 2.2f, 2.4f, rgbs(0x120a10, 0x22141e), null); px(sx * 4.6f - 1f, -22.6f, WHITE) }
        }
        tri(-1.5f, -18.5f, 1.5f, -18.5f, 0f, -15.5f, rgb(0xe8a838)); if (mouth) dot(-1f, -15.5f, 2f, 1f, rgb(0x5a3010))
        // night cap drooping to one side, with a pompom
        tri(-8f, -27f, 8f, -27f, 12f, -38f, rgb(0x3d3570)); tri(4f, -30f, 12f, -38f, 15f, -31f, rgb(0x3d3570))
        line(-8f, -27f, 8f, -27f, rgb(0xf3e6d8), 1.6f); blob(15f, -30f, 2.4f, 2.4f, rgbs(0xe8e0d0, 0xffffff), rgb(0x2c2458))
    }

    // ---------- Беглец: a snail with a little suitcase on its shell ----------
    private fun Pen.snail(br: Float, blink: Boolean, mouth: Boolean, t: Float) {
        val O = rgb(0x1a2a2a); val body = rgbs(0x4a6a6a, 0x6a9090, 0x90b8b4, 0xc0e0d8, 0xe8fff8)
        val shell = rgbs(0x6a3a1a, 0x9a5a2a, 0xc8803a, 0xe8a860, 0xf8d090)
        blob(0f, -3.5f, 19f, 4f, body, O)                                         // the foot
        // the shell: a shaded disc with a spiral groove
        val sx = -4f; val sy = -15f; val sr = 11.5f
        blob(sx, sy, sr, sr, shell, rgb(0x2a1408))
        for (i in 0..60) { val a = i / 60f * 4.2f * PI.toFloat(); val rr = sr * (1 - i / 66f); px(sx + cos(a) * rr * .85f, sy + sin(a) * rr * .85f, rgb(0x6a3a1a)) }
        // head and eye stalks on the right
        val hx = 15f; val hy = -11f + br * .5f
        blob(hx, hy, 4.5f, 7f, body, O)
        for ((ex, ey) in listOf(hx - 1f to hy - 12f, hx + 3.5f to hy - 10.5f)) { line(hx, hy - 5f, ex, ey, body[1], 1.1f)
            blob(ex, ey, 1.8f, 1.8f, rgbs(0xe8fff8, 0xffffff), O); if (!blink) px(ex + .4f, ey, rgb(0x120a10)) }
        if (mouth) dot(hx + 1f, hy + 1.5f, 2f, 1.5f, rgb(0x2a3a3a)) else { px(hx + 1f, hy + 2f, O); px(hx + 2f, hy + 1.6f, O) }
        blush(hx + 1f, hy - 1f)
        // a striped scarf blowing back and a suitcase strapped on the shell
        for (i in 0..6) { dot(hx - 4f + i, hy + 3.5f, 1f, 1.6f, if (i % 2 == 0) rgb(0xc8553d) else rgb(0xf3e6d8)) }
        line(hx - 4f, hy + 4f, hx - 9f, hy + 6f + sin(t * 3) * 1.2f, rgb(0xc8553d), 1.4f)
        dot(sx - 6f, sy - 17f, 12f, 8f, rgb(0x8a4a2a)); dot(sx - 6f, sy - 17f, 12f, 1f, rgb(0xb86a3a)); dot(sx - 2f, sy - 19f, 4f, 2f, rgb(0x3a2010))
        dot(sx - 4.5f, sy - 14.5f, 3f, 3f, rgb(0x6aa8d8)); dot(sx + 1f, sy - 14f, 3f, 2.5f, rgb(0xf2c14e))
        line(sx - 7f, sy - 9f, sx + 7f, sy - 9f, rgb(0x3a2010))
    }
}
