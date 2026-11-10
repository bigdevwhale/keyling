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

    /** A sculpted keycap seen from the front and above: dished top, side skirts, a taller front skirt. */
    fun drawCap(b: Buf, x: Int, y: Int, w: Int, h: Int, p: CapPal, lab: String, wear: Float) {
        val ft = if (h >= 20) 7 else 5; val te = y + h - ft
        b.rect(x + 3, y, w - 6, 1, p.o)
        b.p(x + 1, y + 1, p.o); b.p(x + w - 2, y + 1, p.o); b.rect(x + 2, y + 1, w - 4, 1, p.lo)
        b.rect(x, y + 2, 1, h - 3, p.o); b.rect(x + w - 1, y + 2, 1, h - 3, p.o); b.rect(x + 1, y + h - 1, w - 2, 1, p.o)
        b.rect(x + 1, y + 2, 2, h - 3, p.side); b.rect(x + w - 3, y + 2, 2, h - 3, p.sideD)
        val fr = intArrayOf(p.front, p.frontD)
        for (j in 0 until ft - 1) for (i in 1 until w - 1) b.p(x + i, te + j, pick(fr, j.toFloat() / (ft - 2) * .9f + (if (i > w * .75f) .15f else 0f), x + i, te + j))
        b.rect(x + 3, te, w - 6, 1, p.hi)
        val fh = te - (y + 2)
        val top = intArrayOf(p.lo, p.top, p.top, p.hi)
        for (j in 0 until fh) for (i in 3 until w - 3) {
            val v = .25f + j.toFloat() / fh * .55f + (if (i < 5) .2f else 0f) - (if (i > w - 6) .25f else 0f)
            b.p(x + i, y + 2 + j, pick(top, v, x + i, y + 2 + j))
        }
        b.p(x + 3, y + 2, p.side); b.p(x + w - 4, y + 2, p.sideD); b.p(x + 3, te - 1, p.side); b.p(x + w - 4, te - 1, p.sideD)
        if (wear > .04f) {
            val cx = x + w / 2f; val cy = y + 2 + fh * .55f; val rx = minOf(w * .32f, 9f); val ry = fh * .32f
            for (j in 0 until fh) for (i in 3 until w - 3) {
                val dx = (x + i - cx) / rx; val dy = (y + 2 + j - cy) / ry; val d = dx * dx + dy * dy
                if (d < 1 && bay(x + i, y + 2 + j) < wear * (1 - d) * .7f)
                    b.p(x + i, y + 2 + j, if (d < .25f && wear > .6f && (x + i + y + j) % 4 == 0) shade(p.hi, .4f) else p.hi)
            }
        }
        val big = h >= 20
        if (lab.isNotBlank() && (big || lab.length == 1)) {
            val g = Glyphs.text(lab, p.leg, if (lab.length > 1) 5f else 8f)
            val lx = if (big) x + 5 else x + (w - Glyphs.inkWidth(g)) / 2
            val ly = if (big) y + 4 else y + 2
            b.blit(g, lx, ly - 1)
            if (wear > .05f) {
                // A worn legend: the top colour creeps back over the print.
                val fade = Glyphs.text(lab, p.top, if (lab.length > 1) 5f else 8f)
                b.blit(fade, lx, ly - 1, alpha = minOf(.85f, wear * .9f))
            }
        }
    }

    class Caps(val up: Buf, val dn: Buf)

    /** Every keycap of [set] drawn twice, up and pressed, with wear from [wearDays] of writing. */
    fun buildCaps(set: CapSet, wearDays: Int): Map<Key, Caps> {
        val wk = wearDays / 300f
        return keys.associateWith { k ->
            val p = when (k.type) { 'a' -> CapPal(set.a, set.la); 'x' -> CapPal(set.x, set.lx); else -> CapPal(set.m, set.lm) }
            val wear = minOf(1f, k.freq / 11f * wk)
            val up = Buf(k.w, k.h).also { drawCap(it, 0, 0, k.w, k.h, p, k.lab, wear) }
            val dd = if (k.h >= 20) 2 else 1
            val dn = Buf(k.w, k.h).also { drawCap(it, 0, dd, k.w, k.h - dd, p, k.lab, wear) }
            Caps(up, dn)
        }
    }

    /** Three big caps (an accent, a worn letter, a modifier) for the set's card in the shop. */
    fun preview(set: CapSet): Buf {
        val b = Buf(90, 40)
        b.rect(0, 0, 90, 40, set.plate); b.rect(0, 0, 90, 3, set.case); b.rect(0, 37, 90, 3, set.case)
        drawCap(b, 3, 7, 26, 24, CapPal(set.x, set.lx), "Esc", 0f)
        drawCap(b, 31, 7, 26, 24, CapPal(set.a, set.la), "О", .6f)
        drawCap(b, 59, 7, 28, 24, CapPal(set.m, set.lm), "⇧", 0f)
        return b
    }
}
