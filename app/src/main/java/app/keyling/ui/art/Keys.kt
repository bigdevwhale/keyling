package app.keyling.ui.art

import kotlin.math.round

/** One set of keycaps: alphas, modifiers, accents, their legends, the case and the plate. */
class CapSet(val id: String, val a: Int, val m: Int, val x: Int, val la: Int, val lm: Int, val lx: Int, val case: Int, val plate: Int, val price: Int)

object CapSets {
    val all = listOf(
        CapSet("retro", rgb(0xece3cc), rgb(0xa59d8a), rgb(0xd0603f), rgb(0x4a4338), rgb(0xf4eee0), rgb(0xfff3e8), rgb(0xd6ccb4), rgb(0x2c2a2f), 0),
        CapSet("pastel", rgb(0xfbf3ec), rgb(0xb7d5e6), rgb(0xf2b35c), rgb(0x7a6488), rgb(0x3f6680), rgb(0x6a3e10), rgb(0xecd0da), rgb(0x3a3040), 0),
        CapSet("night", rgb(0x3c3e4c), rgb(0x2a2c37), rgb(0xe05a8a), rgb(0x9fe3c9), rgb(0xe05a8a), rgb(0xffe6f0), rgb(0x1e1f27), rgb(0x0e0e13), 120),
        CapSet("walnut", rgb(0xead8b2), rgb(0xa87c4f), rgb(0x5b7f3a), rgb(0x4a2f1a), rgb(0x2a190c), rgb(0xeef6dc), rgb(0x6e4528), rgb(0x2a1d14), 200),
        CapSet("sea", rgb(0xeef4f2), rgb(0x2f5d73), rgb(0xe8a33d), rgb(0x2f5d73), rgb(0xeef4f2), rgb(0x2a1a05), rgb(0xcfe0dc), rgb(0x1c2a30), 160),
    )
    fun of(id: String) = all.firstOrNull { it.id == id } ?: all[0]
}

class CapPal(base: Int, val leg: Int) {
    val o = shade(base, -.7f); val top = base; val hi = shade(base, .35f); val lo = shade(base, -.07f)
    val side = shade(base, -.16f); val sideD = shade(base, -.3f); val front = shade(base, -.22f); val frontD = shade(base, -.4f)
}

/** Letter frequencies in Russian prose (per cent): the most used keys wear first. */
val FREQ = mapOf(
    "О" to 10.97f, "Е" to 8.45f, "А" to 8.01f, "И" to 7.35f, "Н" to 6.7f, "Т" to 6.26f, "С" to 5.47f, "Р" to 4.73f, "В" to 4.54f, "Л" to 4.4f,
    "К" to 3.49f, "М" to 3.21f, "Д" to 2.98f, "П" to 2.81f, "У" to 2.62f, "Я" to 2.01f, "Ы" to 1.9f, "Ь" to 1.74f, "Г" to 1.7f, "З" to 1.65f,
    "Б" to 1.59f, "Ч" to 1.44f, "Й" to 1.21f, "Х" to .97f, "Ж" to .94f, "Ш" to .73f, "Ю" to .64f, "Ц" to .48f, "Щ" to .36f, "Э" to .32f,
    "Ф" to .26f, "Ъ" to .04f,
)
val LETTERS = FREQ.keys.toList()

class Key(val lab: String, val id: String?, val type: Char, val r: Int, val x: Int, val y: Int, val w: Int, val h: Int, val freq: Float) {
    var press = 0f
}

/** The 60% ЙЦУКЕН board and how a keycap is drawn. */
object Board {
    const val PX = 16; const val PY = 16; const val KY = 318; const val KX = 10

    private val rows: List<List<Triple<String, Float, Pair<String?, Char>>>> = run {
        fun a(s: String) = s.map { Triple(it.toString(), 1f, null as String? to 'a') }
        listOf(
            listOf(Triple("Esc", 1f, "esc" to 'x')) + a("1234567890-=") + listOf(Triple("⌫", 2f, "bksp" to 'm')),
            listOf(Triple("↹", 1.5f, "tab" to 'm')) + a("ЙЦУКЕНГШЩЗХЪ") + listOf(Triple("\\", 1.5f, null as String? to 'm')),
            listOf(Triple("⇪", 1.75f, "caps" to 'm')) + a("ФЫВАПРОЛДЖЭ") + listOf(Triple("⏎", 2.25f, "enter" to 'x')),
            listOf(Triple("⇧", 2.25f, "shift" to 'm')) + a("ЯЧСМИТЬБЮ") + listOf(Triple(".", 1f, null as String? to 'a'), Triple("⇧", 2.75f, "shiftR" to 'm')),
            listOf(Triple("", 1.25f, null as String? to 'm'), Triple("", 1.25f, null as String? to 'm'), Triple("", 1.25f, null as String? to 'm'),
                Triple(" ", 6.25f, "space" to 'a'),
                Triple("", 1.25f, null as String? to 'm'), Triple("", 1.25f, null as String? to 'm'), Triple("", 1.25f, null as String? to 'm'), Triple("", 1.25f, null as String? to 'm')),
        )
    }

    val keys: List<Key> = buildList {
        rows.forEachIndexed { r, row ->
            var acc = 0f
            for ((lab, u, meta) in row) {
                val (id, type) = meta
                val x = round(acc * PX).toInt() + KX; val w = round(u * PX).toInt() - 2
                val f = FREQ[lab] ?: when {
                    id == "space" -> 14f; lab == "." -> 2.4f; lab.firstOrNull()?.isDigit() == true -> .3f; id == "bksp" -> 3f; else -> .8f
                }
                add(Key(lab, id, type, r, x, KY + r * PY, w, 14, f))
                acc += u
            }
        }
    }
    val space: Key = keys.first { it.id == "space" }

    /**
     * A sculpted keycap seen from the front and above: dished top, side skirts, a taller front skirt. [x], [y],
     * [w], [h] are art pixels; the cap is drawn a device pixel at a time, so on a fine buffer its corners are
     * round, its outline thin, its shading smooth and its legend set in a plain face.
     */
    fun drawCap(b: Buf, x: Int, y: Int, w: Int, h: Int, p: CapPal, lab: String, wear: Float) {
        val u = b.s; val fine = u > 1
        val X = x * u; val Y = y * u; val W = w * u; val H = h * u
        val ft = (if (h >= 20) 7 else 5) * u; val te = Y + H - ft          // the front skirt starts at device row te
        val r = 2.5f * u                                                    // the corner radius
        fun inCap(i: Int, j: Int): Boolean {                                // (i, j) device pixels inside the cap
            if (i < 0 || j < 0 || i >= W || j >= H) return false
            val cx = if (i < r) r - i - .5f else if (i >= W - r) i + .5f - (W - r) else 0f
            val cy = if (j < r) r - j - .5f else if (j >= H - r) j + .5f - (H - r) else 0f
            return cx * cx + cy * cy <= r * r
        }
        // the top face: inset 3 art pixels from the sides, from the second art row to the front skirt
        val fx0 = 3 * u; val fx1 = W - 3 * u; val fy0 = 2 * u; val fy1 = te - Y; val fr = 1.5f * u
        fun inFace(i: Int, j: Int): Boolean {
            if (i < fx0 || i >= fx1 || j < fy0 || j >= fy1) return false
            val cx = if (i < fx0 + fr) fx0 + fr - i - .5f else if (i >= fx1 - fr) i + .5f - (fx1 - fr) else 0f
            val cy = if (j < fy0 + fr) fy0 + fr - j - .5f else if (j >= fy1 - fr) j + .5f - (fy1 - fr) else 0f
            return cx * cx + cy * cy <= fr * fr
        }
        val face = intArrayOf(p.lo, p.top, p.top, p.hi); val front = intArrayOf(p.front, p.frontD)
        fun tone(rr: IntArray, v: Float, i: Int, j: Int) = if (fine) lerp(rr, v) else pick(rr, v, i, j)
        val fh = (fy1 - fy0).toFloat()
        for (j in 0 until H) for (i in 0 until W) {
            if (!inCap(i, j)) continue
            val c = when {
                !inCap(i - 1, j) || !inCap(i + 1, j) || !inCap(i, j - 1) || !inCap(i, j + 1) -> p.o
                inFace(i, j) -> { val ax = i.toFloat() / u
                    tone(face, .25f + (j - fy0) / fh * .55f + (if (ax < 5) .2f else 0f) - (if (ax > w - 6) .25f else 0f), X + i, Y + j) }
                j >= te - Y -> if (j < te - Y + (u + 1) / 2 && i in fx0 until fx1) p.hi
                    else tone(front, (j - (te - Y)).toFloat() / (ft - u).coerceAtLeast(1) * .9f + (if (i > W * .75f) .15f else 0f), X + i, Y + j)
                j < fy0 -> p.lo
                i < W / 2 -> p.side
                else -> p.sideD
            }
            b.px(X + i, Y + j, c)
        }
        if (wear > .04f) {
            // the shine a finger polishes into the middle of the top
            val cx = W / 2f; val cy = fy0 + fh * .55f; val rx = minOf(W * .32f, 9f * u); val ry = fh * .32f
            for (j in fy0 until fy1) for (i in fx0 until fx1) {
                if (!inFace(i, j)) continue
                val dx = (i - cx) / rx; val dy = (j - cy) / ry; val d = dx * dx + dy * dy
                if (d >= 1) continue
                if (fine) b.px(X + i, Y + j, p.hi, (wear * (1 - d) * .8f).coerceAtMost(.9f))
                else if (bay(X + i, Y + j) < wear * (1 - d) * .7f) b.px(X + i, Y + j, if (d < .25f && wear > .6f && (X + i + Y + j) % 4 == 0) shade(p.hi, .4f) else p.hi)
            }
        }
        val big = h >= 20
        if (lab.isNotBlank() && (big || lab.length == 1)) {
            val size = if (!fine) (if (lab.length > 1) 5f else 8f) else if (big) (if (lab.length > 1) 6.5f else 10f) else if (lab.length > 1) 5f else 7f
            val g = Glyphs.text(lab, p.leg, size, u, smooth = fine)
            val (gx0, gx1) = Glyphs.inkSpan(g)
            val lx = if (big) X + 5 * u else X + (W - (gx1 - gx0)) / 2 - gx0
            val ly = if (!fine) (if (big) Y + 4 * u else Y + 2 * u) - u
                else if (big) Y + 3 * u else Y + fy0 + ((fh - size * .73f * u) / 2).toInt() - (size * .27f * u).toInt()
            b.blitPx(g, lx, ly)
            if (wear > .05f) {
                // A worn legend: the top colour creeps back over the print.
                b.blitPx(Glyphs.text(lab, p.top, size, u, smooth = fine), lx, ly, alpha = minOf(.85f, wear * .9f))
            }
        }
    }

    class Caps(val up: Buf, val dn: Buf)

    /** Every keycap of [set] drawn twice, up and pressed, with wear from [wearDays] of writing, [s] device pixels per art pixel. */
    fun buildCaps(set: CapSet, wearDays: Int, s: Int = 1): Map<Key, Caps> {
        val wk = wearDays / 300f
        return keys.associateWith { k ->
            val p = when (k.type) { 'a' -> CapPal(set.a, set.la); 'x' -> CapPal(set.x, set.lx); else -> CapPal(set.m, set.lm) }
            val wear = minOf(1f, k.freq / 11f * wk)
            val up = Buf(k.w, k.h, s).also { drawCap(it, 0, 0, k.w, k.h, p, k.lab, wear) }
            val dd = if (k.h >= 20) 2 else 1
            val dn = Buf(k.w, k.h, s).also { drawCap(it, 0, dd, k.w, k.h - dd, p, k.lab, wear) }
            Caps(up, dn)
        }
    }

    /** Three big caps (an accent, a worn letter, a modifier) for the set's card in the shop. */
    fun preview(set: CapSet, s: Int = 1): Buf {
        val b = Buf(90, 40, s)
        b.rect(0, 0, 90, 40, set.plate); b.rect(0, 0, 90, 3, set.case); b.rect(0, 37, 90, 3, set.case)
        drawCap(b, 3, 7, 26, 24, CapPal(set.x, set.lx), "Esc", 0f)
        drawCap(b, 31, 7, 26, 24, CapPal(set.a, set.la), "О", .6f)
        drawCap(b, 59, 7, 28, 24, CapPal(set.m, set.lm), "⇧", 0f)
        return b
    }
}
