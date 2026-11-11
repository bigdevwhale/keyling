package app.keyling.ui.art

import java.io.DataInputStream
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt

/**
 * The seven residents, drawn as sheets of four growth stages (art/sheets, by Codex's image tool) and cut
 * into small sprites by tools/cut.py. Here they come to life: blinking, and hopping as they chew.
 */
object Critters {
    /** Sprite heights cut for each place: the nook and cards, the desk, the small list portraits. */
    const val BIG = 48; const val DESK = 34; const val SMALL = 26
    private val STAGES = arrayOf("baby", "young", "adult", "ancient")

    /** One cut sprite: its pixels open-eyed and shut-eyed. */
    class Sprite(val w: Int, val h: Int, val open: IntArray, val shut: IntArray)

    private val cache = HashMap<String, Sprite?>()

    fun sprite(id: String, stage: Int, size: Int): Sprite? = synchronized(cache) {
        val name = "${id}_${STAGES[stage.coerceIn(0, 3)]}_$size"
        cache.getOrPut(name) { runCatching { load(name) }.getOrNull() }
    }

    /** Reads a tools/cut.py sprite: gzip of w, h, w*h ARGB ints, w*h lid bytes, eye count, 4 ints per eye box. */
    private fun load(name: String): Sprite? {
        val stream = Critters::class.java.getResourceAsStream("/pets/$name.bin") ?: return null
        DataInputStream(GZIPInputStream(stream).buffered()).use { s ->
            val w = s.readInt(); val h = s.readInt()
            val px = IntArray(w * h) { s.readInt() }
            val lid = BooleanArray(w * h) { s.readByte().toInt() != 0 }
            val eyes = List(s.readInt()) { IntArray(4) { s.readInt() } }
            val shut = px.copyOf()
            for (e in eyes) close(shut, w, h, e, lid)
            return Sprite(w, h, px, shut)
        }
    }

    private fun lum(c: Int) = Buf.ch(c, 16) * .3f + Buf.ch(c, 8) * .59f + Buf.ch(c, 0) * .11f

    /** Shuts an eye: its pixels take the commonest skin colour around it, with a dark line across the middle. */
    private fun close(d: IntArray, w: Int, h: Int, e: IntArray, lid: BooleanArray) {
        val (x0, y0, x1, y1) = e
        val around = HashMap<Int, Int>(); val plain = HashMap<Int, Int>()
        for (y in y0 - 2..y1 + 1) for (x in x0 - 2..x1 + 1) {
            if (x in x0 until x1 && y in y0 until y1) continue
            if (x !in 0 until w || y !in 0 until h || lid[y * w + x]) continue
            val c = d[y * w + x]
            if (c ushr 24 == 0 || lum(c) < 40) continue
            around[c] = (around[c] ?: 0) + 1
            val r = Buf.ch(c, 16); val g = Buf.ch(c, 8)
            if (!(r - g > 50 && r > 170 && Buf.ch(c, 0) >= g - 12)) plain[c] = (plain[c] ?: 0) + 1   // not blush
        }
        // Skin over blush, unless the skin is all pink (the eraser).
        val skin = (plain.takeIf { it.values.sum() * 4 >= around.values.sum() } ?: around).maxByOrNull { it.value }?.key ?: return
        var lx = Int.MAX_VALUE; var rx = -1
        for (y in y0 until y1) for (x in x0 until x1) { val i = y * w + x
            if (lid[i] && d[i] ushr 24 != 0) { d[i] = skin; if (x < lx) lx = x; if (x > rx) rx = x } }
        if (rx < 0) return
        val mid = (y0 + y1) / 2; val ink = rgb(0x2a1620)
        for (x in lx..rx) { val dy = if (x == lx || x == rx) 0 else 1; val i = (mid + dy) * w + x; if (mid + dy in 0 until h) d[i] = ink }
    }

    /**
     * Draws resident [id] at growth [stage] (0 baby … 3 ancient) with its feet on row [by], centred on [cx].
     * [size] picks the cut ([BIG], [DESK], [SMALL]); [t] is seconds, for the hop while [mouth] is open.
     */
    fun draw(b: Buf, id: String, stage: Int, cx: Float, by: Float, size: Int, t: Float, blink: Boolean, mouth: Boolean) {
        val sp = sprite(id, stage, size) ?: return
        val src = if (blink) sp.shut else sp.open
        // Chewing (or singing) makes it hop a pixel, the whole of it at once: bending a sprite this small
        // a pixel at a time shows as a seam across the body.
        val hop = if (mouth && (t * 7).toInt() % 2 == 0) -1 else 0
        val x0 = (cx - sp.w / 2f).roundToInt(); val y0 = by.roundToInt() - sp.h + 1 + hop
        for (y in 0 until sp.h) for (x in 0 until sp.w) { val c = src[y * sp.w + x]; if (c ushr 24 != 0) b.p(x0 + x, y0 + y, c) }
    }

    /** The same creature as a dark shape with a question mark, for residents who haven't moved in. */
    fun silhouette(b: Buf, id: String, stage: Int, cx: Float, by: Float, size: Int) {
        val sp = sprite(id, stage, size) ?: return
        val dark = rgbs(0x1c1418, 0x281e24, 0x33282e)
        val x0 = (cx - sp.w / 2f).roundToInt(); val y0 = by.roundToInt() - sp.h + 1
        var top = sp.h
        for (y in 0 until sp.h) for (x in 0 until sp.w) { val c = sp.open[y * sp.w + x]; if (c ushr 24 == 0) continue
            if (y < top) top = y; b.p(x0 + x, y0 + y, dark[(lum(c) / 255f * 2.99f).toInt()]) }
        b.blit(Glyphs.text("?", rgb(0x6a5560)), (cx - 3).roundToInt(), y0 + (top + sp.h) / 2 - 6)
    }
}
