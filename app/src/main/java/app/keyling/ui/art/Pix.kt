package app.keyling.ui.art

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A small ARGB pixel buffer every picture is painted into, one art pixel per cell. Screens scale it
 * up with nearest-neighbour filtering. Colours are 0xAARRGGBB ints; drawing past the edges is ignored.
 */
class Buf(val w: Int, val h: Int) {
    val d = IntArray(w * h)

    fun copyFrom(o: Buf) = System.arraycopy(o.d, 0, d, 0, d.size)
    fun get(x: Int, y: Int): Int = if (x < 0 || y < 0 || x >= w || y >= h) 0 else d[y * w + x]

    /** Paints a pixel, blending by [a] (0..1) over what is there. */
    fun p(x: Int, y: Int, c: Int, a: Float = 1f) {
        if (x < 0 || y < 0 || x >= w || y >= h || a <= 0f) return
        val i = y * w + x
        if (a >= 1f) { d[i] = c or OPAQUE; return }
        val o = d[i]
        if (o ushr 24 == 0) { d[i] = (c and 0xFFFFFF) or ((a * 255).toInt() shl 24); return }
        val r = ch(o, 16) + ((ch(c, 16) - ch(o, 16)) * a).toInt()
        val g = ch(o, 8) + ((ch(c, 8) - ch(o, 8)) * a).toInt()
        val b = ch(o, 0) + ((ch(c, 0) - ch(o, 0)) * a).toInt()
        d[i] = (max(o ushr 24, (a * 255).toInt()) shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun rect(x: Int, y: Int, w: Int, h: Int, c: Int, a: Float = 1f) {
        for (j in y until y + h) for (i in x until x + w) p(i, j, c, a)
    }

    fun clear() = d.fill(0)

    /** Draws [o] at (x, y), skipping its empty pixels; [alpha] fades the whole of it; rows at or below [clipY] are skipped. */
    fun blit(o: Buf, x: Int, y: Int, alpha: Float = 1f, clipY: Int = Int.MAX_VALUE) {
        for (j in 0 until o.h) {
            if (y + j >= clipY) break
            for (i in 0 until o.w) {
                val c = o.d[j * o.w + i]
                val a = (c ushr 24) / 255f * alpha
                if (a > 0f) p(x + i, y + j, c, a)
            }
        }
    }

    fun toBitmap(): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(d, 0, w, 0, 0, w, h) }

    companion object {
        const val OPAQUE = 0xFF000000.toInt()
        fun ch(c: Int, sh: Int) = (c shr sh) and 0xFF
    }
}

fun rgb(hex: Int): Int = hex or Buf.OPAQUE
fun rgbs(vararg hex: Int): IntArray = IntArray(hex.size) { rgb(hex[it]) }

private val BAY = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

/** 4×4 ordered-dither threshold for a pixel, 0..1. */
fun bay(x: Int, y: Int): Float = (BAY[(y and 3) * 4 + (x and 3)] + .5f) / 16f

/** Picks a tone from [r] for brightness [v] (0..1), dithering between neighbours. */
fun pick(r: IntArray, v: Float, x: Int, y: Int): Int {
    val vv = v.coerceIn(0f, .999f)
    val t = vv * (r.size - 1)
    val i = floor(t).toInt()
    return r[min(r.size - 1, i + if (t - i > bay(x, y)) 1 else 0)]
}

/** Lightens (f > 0) towards white or darkens (f < 0) towards black. */
fun shade(c: Int, f: Float): Int {
    fun m(v: Int) = (if (f > 0) v + (255 - v) * f else v * (1 + f)).roundToInt().coerceIn(0, 255)
    return Buf.OPAQUE or (m(Buf.ch(c, 16)) shl 16) or (m(Buf.ch(c, 8)) shl 8) or m(Buf.ch(c, 0))
}

fun scale(c: Int, k: Float): Int {
    fun m(v: Int) = (v * k).roundToInt().coerceIn(0, 255)
    return Buf.OPAQUE or (m(Buf.ch(c, 16)) shl 16) or (m(Buf.ch(c, 8)) shl 8) or m(Buf.ch(c, 0))
}

/** mulberry32: the same little random numbers every time for one seed. */
class Rng(private var s: Int) {
    fun next(): Float {
        s += 0x6D2B79F5
        var t = s
        t = (t xor (t ushr 15)) * (1 or t)
        t = t + ((t xor (t ushr 7)) * (61 or t)) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }
}

/** A shaded, optionally outlined ellipse, lit from the top left. */
fun Buf.blob(cx: Float, cy: Float, rx: Float, ry: Float, ramp: IntArray, out: Int?, clipY: Int = Int.MAX_VALUE, alpha: Float = 1f) {
    fun ins(x: Int, y: Int): Boolean { val a = (x + .5f - cx) / rx; val b = (y + .5f - cy) / ry; return a * a + b * b <= 1f }
    for (y in floor(cy - ry - 1).toInt()..(cy + ry + 1).toInt()) {
        if (y >= clipY) continue
        for (x in floor(cx - rx - 1).toInt()..(cx + rx + 1).toInt()) {
            if (!ins(x, y)) continue
            if (out != null && (!ins(x - 1, y) || !ins(x + 1, y) || !ins(x, y - 1) || !ins(x, y + 1))) { p(x, y, out, alpha); continue }
            val nx = (x + .5f - cx) / rx; val ny = (y + .5f - cy) / ry
            p(x, y, pick(ramp, .55f - nx * .3f - ny * .4f, x, y), alpha)
        }
    }
}

/** Pixel text in Press Start 2P (8 px cells), drawn without smoothing and cached as small buffers. */
object Glyphs {
    var face: Typeface? = null
    private val cache = HashMap<String, Buf>()

    @Synchronized fun text(s: String, color: Int, size: Float = 8f): Buf = cache.getOrPut("$s|$color|$size") {
        val paint = Paint().apply {
            isAntiAlias = false; textSize = size; this.color = color; typeface = face ?: Typeface.MONOSPACE
            isFilterBitmap = false
        }
        val w = max(1, paint.measureText(s).toInt() + 2)
        val h = (size * 1.5f).toInt() + 2
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(s, 0f, size, paint)
        val b = Buf(w, h)
        bmp.getPixels(b.d, 0, w, 0, 0, w, h)
        // Hard edges: anything half-covered is a pixel, the rest is empty.
        for (i in b.d.indices) b.d[i] = if (b.d[i] ushr 24 >= 110) (color or Buf.OPAQUE) else 0
        bmp.recycle()
        b
    }

    /** Width of the inked part of [s], for centring. */
    fun inkWidth(b: Buf): Int {
        var last = 0
        for (x in 0 until b.w) for (y in 0 until b.h) if (b.d[y * b.w + x] != 0) last = x
        return last + 1
    }
}
