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
 * A small ARGB pixel buffer every picture is painted into. Screens scale it up with nearest-neighbour
 * filtering. Colours are 0xAARRGGBB ints; drawing past the edges is ignored.
 *
 * [w] and [h] are in art pixels, the units every drawing works in. Each art pixel is [s]×[s] device
 * pixels ([pw]×[ph] in all, in [d]): the plain calls ([p], [rect]) fill a whole art pixel, the `px`
 * calls and the shaded ones ([paint], [blob], a finer [blit]) work a device pixel at a time, so the
 * same scene can be drawn finer without moving anything in it.
 */
class Buf(val w: Int, val h: Int, val s: Int = 1) {
    val pw = w * s; val ph = h * s
    val d = IntArray(pw * ph)

    fun copyFrom(o: Buf) = System.arraycopy(o.d, 0, d, 0, d.size)
    /** The colour at an art pixel (its top-left device pixel). */
    fun get(x: Int, y: Int): Int = if (x < 0 || y < 0 || x >= w || y >= h) 0 else d[y * s * pw + x * s]
    /** The colour at a device pixel. */
    fun getPx(x: Int, y: Int): Int = if (x < 0 || y < 0 || x >= pw || y >= ph) 0 else d[y * pw + x]

    /** Paints a device pixel, blending by [a] (0..1) over what is there. */
    fun px(x: Int, y: Int, c: Int, a: Float = 1f) {
        if (x < 0 || y < 0 || x >= pw || y >= ph || a <= 0f) return
        val i = y * pw + x
        if (a >= 1f) { d[i] = c or OPAQUE; return }
        val o = d[i]
        if (o ushr 24 == 0) { d[i] = (c and 0xFFFFFF) or ((a * 255).toInt() shl 24); return }
        val r = ch(o, 16) + ((ch(c, 16) - ch(o, 16)) * a).toInt()
        val g = ch(o, 8) + ((ch(c, 8) - ch(o, 8)) * a).toInt()
        val b = ch(o, 0) + ((ch(c, 0) - ch(o, 0)) * a).toInt()
        d[i] = (max(o ushr 24, (a * 255).toInt()) shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Paints an art pixel. */
    fun p(x: Int, y: Int, c: Int, a: Float = 1f) {
        if (s == 1) { px(x, y, c, a); return }
        if (x < 0 || y < 0 || x >= w || y >= h || a <= 0f) return
        for (j in y * s until y * s + s) for (i in x * s until x * s + s) px(i, j, c, a)
    }

    fun rect(x: Int, y: Int, w: Int, h: Int, c: Int, a: Float = 1f) = rectPx(x * s, y * s, w * s, h * s, c, a)
    fun rectPx(x: Int, y: Int, w: Int, h: Int, c: Int, a: Float = 1f) {
        for (j in max(0, y) until min(ph, y + h)) for (i in max(0, x) until min(pw, x + w)) px(i, j, c, a)
    }

    /**
     * Paints the art-pixel box (x, y, w, h) a device pixel at a time: [f] gets each one's centre in art
     * units (fractional) and its device coordinates, and returns a colour, or 0 to leave it.
     */
    inline fun paint(x: Int, y: Int, w: Int, h: Int, a: Float = 1f, f: (fx: Float, fy: Float, X: Int, Y: Int) -> Int) {
        val inv = 1f / s
        for (Y in max(0, y * s) until min(ph, (y + h) * s)) { val fy = (Y + .5f) * inv
            for (X in max(0, x * s) until min(pw, (x + w) * s)) { val c = f((X + .5f) * inv, fy, X, Y); if (c != 0) px(X, Y, c, a) } }
    }

    fun clear() = d.fill(0)

    /**
     * Draws [o] with its top-left at art pixel (x, y), skipping its empty pixels; [alpha] fades the whole
     * of it; rows at or below art row [clipY] are skipped. A buffer as fine as this one lands device pixel
     * for device pixel; a coarser one is scaled up.
     */
    fun blit(o: Buf, x: Int, y: Int, alpha: Float = 1f, clipY: Int = Int.MAX_VALUE) = blitPx(o, x * s, y * s, alpha, if (clipY == Int.MAX_VALUE) clipY else clipY * s)

    /** [blit] at device pixel (x, y), clipped at device row [clipY]. */
    fun blitPx(o: Buf, x: Int, y: Int, alpha: Float = 1f, clipY: Int = Int.MAX_VALUE) {
        if (o.s == s) {
            // the whole frame is redrawn this way every tick: solid pixels inside the buffer are written straight in
            for (j in 0 until o.ph) { val yy = y + j; if (yy >= clipY || yy >= ph) break; if (yy < 0) continue
                val row = yy * pw; val src = j * o.pw
                for (i in 0 until o.pw) { val c = o.d[src + i]; val ca = c ushr 24; if (ca == 0) continue
                    val xx = x + i
                    if (ca == 255 && alpha >= 1f) { if (xx in 0 until pw) d[row + xx] = c } else px(xx, yy, c, ca / 255f * alpha) } }
            return
        }
        // Different fineness: each device pixel here takes the source pixel under it.
        val tw = o.pw * s / o.s; val th = o.ph * s / o.s
        for (j in 0 until th) { val yy = y + j; if (yy >= clipY || yy >= ph) break; if (yy < 0) continue; val sy = j * o.s / s
            for (i in 0 until tw) { val c = o.d[sy * o.pw + i * o.s / s]; val a = (c ushr 24) / 255f * alpha; if (a > 0f) px(x + i, yy, c, a) } }
    }

    fun toBitmap(): Bitmap = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888).also { it.setPixels(d, 0, pw, 0, 0, pw, ph) }

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

/** The colour at brightness [v] (0..1) along [r], blended between neighbours rather than dithered. */
fun lerp(r: IntArray, v: Float): Int {
    val t = v.coerceIn(0f, 1f) * (r.size - 1); val i = floor(t).toInt().coerceAtMost(r.size - 2).coerceAtLeast(0); val f = t - i
    val a = r[i]; val b = r[min(r.size - 1, i + 1)]
    fun m(sh: Int) = (Buf.ch(a, sh) + (Buf.ch(b, sh) - Buf.ch(a, sh)) * f).roundToInt().coerceIn(0, 255)
    return Buf.OPAQUE or (m(16) shl 16) or (m(8) shl 8) or m(0)
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

/**
 * A shaded, optionally outlined ellipse, lit from the top left; centre and radii in art pixels. Drawn a
 * device pixel at a time, so on a fine buffer its edge is smooth and its outline thin.
 */
fun Buf.blob(cx: Float, cy: Float, rx: Float, ry: Float, ramp: IntArray, out: Int?, clipY: Int = Int.MAX_VALUE, alpha: Float = 1f) {
    val k = s.toFloat()
    fun ins(x: Int, y: Int): Boolean { val a = ((x + .5f) / k - cx) / rx; val b = ((y + .5f) / k - cy) / ry; return a * a + b * b <= 1f }
    val clip = if (clipY == Int.MAX_VALUE) clipY else clipY * s
    for (y in floor((cy - ry - 1) * k).toInt()..((cy + ry + 1) * k).toInt()) {
        if (y >= clip) continue
        for (x in floor((cx - rx - 1) * k).toInt()..((cx + rx + 1) * k).toInt()) {
            if (!ins(x, y)) continue
            if (out != null && (!ins(x - 1, y) || !ins(x + 1, y) || !ins(x, y - 1) || !ins(x, y + 1))) { px(x, y, out, alpha); continue }
            val nx = ((x + .5f) / k - cx) / rx; val ny = ((y + .5f) / k - cy) / ry
            px(x, y, pick(ramp, .55f - nx * .3f - ny * .4f, x, y), alpha)
        }
    }
}

/**
 * Text drawn into small buffers and cached: Press Start 2P (8 px cells) without smoothing for the pixel
 * look, or, with [smooth], a plain semibold face, antialiased, for text on a fine buffer.
 */
object Glyphs {
    var face: Typeface? = null
    var smoothFace: Typeface? = null
    private val cache = HashMap<String, Buf>()

    /** [size] is in art pixels; the buffer has [s] device pixels per art pixel, like the one it's drawn into. */
    @Synchronized fun text(str: String, color: Int, size: Float = 8f, s: Int = 1, smooth: Boolean = false): Buf = cache.getOrPut("$str|$color|$size|$s|$smooth") {
        val paint = Paint().apply {
            isAntiAlias = smooth; textSize = size * s; this.color = color
            typeface = if (smooth) smoothFace ?: Typeface.DEFAULT_BOLD else face ?: Typeface.MONOSPACE
            isFilterBitmap = false
        }
        val w = max(1, (paint.measureText(str) / s).toInt() + 2)
        val h = (size * 1.5f).toInt() + 2
        val b = Buf(w, h, s)
        val bmp = Bitmap.createBitmap(b.pw, b.ph, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(str, 0f, size * s, paint)
        bmp.getPixels(b.d, 0, b.pw, 0, 0, b.pw, b.ph)
        // Pixel text keeps hard edges: anything half-covered is a pixel, the rest is empty.
        if (!smooth) for (i in b.d.indices) b.d[i] = if (b.d[i] ushr 24 >= 110) (color or Buf.OPAQUE) else 0
        bmp.recycle()
        b
    }

    /** The first and one past the last device column with ink in [b]. */
    fun inkSpan(b: Buf): Pair<Int, Int> {
        var first = b.pw; var last = -1
        for (x in 0 until b.pw) for (y in 0 until b.ph) if (b.d[y * b.pw + x] ushr 24 != 0) { if (x < first) first = x; last = x }
        return if (last < 0) 0 to 0 else first to last + 1
    }

    /** Width of the inked part of [b], in art pixels, for centring. */
    fun inkWidth(b: Buf): Int {
        var last = 0
        for (x in 0 until b.pw) for (y in 0 until b.ph) if (b.d[y * b.pw + x] ushr 24 != 0) last = x
        return (last + b.s) / b.s
    }
}
