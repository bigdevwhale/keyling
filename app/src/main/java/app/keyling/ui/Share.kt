package app.keyling.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.keyling.R
import app.keyling.data.Settings
import app.keyling.domain.Ach
import app.keyling.domain.CHARS_PER_WORD
import app.keyling.domain.PERSONAS
import app.keyling.domain.Resident
import app.keyling.domain.SHEET
import app.keyling.domain.Stats
import app.keyling.domain.fmt
import app.keyling.domain.tr
import app.keyling.ui.art.Board
import app.keyling.ui.art.Buf
import app.keyling.ui.art.CapSets
import app.keyling.ui.art.Decor
import app.keyling.ui.art.Desk
import app.keyling.ui.art.Nook
import app.keyling.ui.art.Scene
import app.keyling.data.Mug
import java.io.File
import kotlin.math.roundToInt

/** Pretty 1080×1350 cards (stats, a resident, an achievement) to post anywhere, shared through Android's share sheet. */
/** A finished card and the line that goes with it. */
class Card(val bmp: Bitmap, val title: String)

object Share {
    private const val W = 1080; private const val H = 1350

    private class Fonts(ctx: Context) {
        val lora: Typeface = ResourcesCompat.getFont(ctx, R.font.lora_semibold) ?: Typeface.SERIF
        val loraI: Typeface = ResourcesCompat.getFont(ctx, R.font.lora_italic) ?: Typeface.SERIF
        val inter: Typeface = ResourcesCompat.getFont(ctx, R.font.inter_regular) ?: Typeface.SANS_SERIF
        val interB: Typeface = ResourcesCompat.getFont(ctx, R.font.inter_semibold) ?: Typeface.SANS_SERIF
    }

    private fun base(top: Int, bottom: Int): Pair<Bitmap, Canvas> {
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), top, bottom, Shader.TileMode.CLAMP) })
        return b to c
    }
    private fun Canvas.pixels(buf: Buf, x: Int, y: Int, w: Int) {
        val bmp = buf.toBitmap(); val h = w * buf.h / buf.w
        drawBitmap(bmp, Rect(0, 0, buf.w, buf.h), Rect(x, y, x + w, y + h), Paint().apply { isFilterBitmap = false }); bmp.recycle()
    }
    private fun Canvas.text(s: String, x: Float, y: Float, size: Float, face: Typeface, color: Int, center: Boolean = false) =
        drawText(s, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = face; this.color = color; if (center) textAlign = Paint.Align.CENTER })
    private fun Canvas.footer(f: Fonts) {
        text(tr("Клавишка · писательский питомец", "Keyling · a pet for writers"), W / 2f, H - 60f, 30f, f.inter, 0xB0FFFFFF.toInt(), center = true)
    }

    fun send(ctx: Context, bmp: Bitmap, title: String) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }; val f = File(dir, "keyling.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val i = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, tr("Поделиться открыткой", "Share the card")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Saves the card to Pictures/Keyling in the gallery. */
    fun save(ctx: Context, bmp: Bitmap): Boolean = runCatching {
        val v = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "keyling-${System.currentTimeMillis()}.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Keyling") }
        val uri = ctx.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)!!
        ctx.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; true }.getOrDefault(false)

    /** The year so far: words, streak, the desk with the pet, the best day. */
    fun year(ctx: Context, st: Stats, s: Settings): Card {
        val f = Fonts(ctx); val (bmp, c) = base(0xFF1B2440.toInt(), 0xFF0E1220.toInt())
        // the desk, cropped to the keyboard and the nook
        val set = CapSets.of(s.caps); val decor = Decor(s.desk, s.mug, s.plant, s.lamp, s.mascot)
        val bg = Desk.background(decor, set, Mug.load(ctx)); val frame = Buf(Desk.W, Desk.H)
        Scene().apply { pet = s.pet; steam = false; render(frame, bg, Board.buildCaps(set, st.activeDays), set.plate, 0) }
        val crop = Buf(Desk.W, 150); for (y in 0 until 150) System.arraycopy(frame.d, (y + 270) * Desk.W, crop.d, y * Desk.W, Desk.W)
        val rr = RectF(60f, 520f, W - 60f, 520f + (W - 120f) * 150 / Desk.W)
        c.save(); c.clipPath(android.graphics.Path().apply { addRoundRect(rr, 36f, 36f, android.graphics.Path.Direction.CW) }); c.pixels(crop, 60, 520, W - 120); c.restore()
        c.text(tr("Мой ${st.year} год в Клавишке", "My ${st.year} in Keyling"), 80f, 150f, 46f, f.loraI, 0xFFC8D2F0.toInt())
        c.text(fmt(st.yearTotal), 80f, 290f, 150f, f.lora, 0xFFFFFFFF.toInt())
        c.text(tr("слов написано", "words written"), 84f, 350f, 44f, f.inter, 0xFFC8D2F0.toInt())
        val items = listOf(
            "${st.activeDays}" to tr("дней с письмом", "writing days"), "${st.longest.first}" to tr("дней подряд – рекорд", "days in a row (best)"),
            (st.yearTotal * CHARS_PER_WORD / SHEET).let { String.format("%.1f", it) } to tr("авторских листов", "author's sheets"))
        items.forEachIndexed { i, (v, l) -> val x = 80f + i * 320f; c.text(v, x, 440f, 64f, f.interB, 0xFFFFFFFF.toInt()); c.text(l, x, 484f, 28f, f.inter, 0xFF9AA6C8.toInt()) }
        val y0 = rr.bottom + 90
        st.bestDay?.let { c.text(tr("Лучший день: ${fmt(it.words)} слов", "Best day: ${fmt(it.words)} words"), 80f, y0, 40f, f.interB, 0xFFFFD27A.toInt()) }
        c.text(tr("Под пробелом живёт ", "Under the space bar lives ") + app.keyling.domain.Residents.of(s.pet).name, 80f, y0 + 64, 36f, f.inter, 0xFFC8D2F0.toInt())
        c.footer(f); return Card(bmp, tr("Мой год в Клавишке: ${fmt(st.yearTotal)} слов", "My year in Keyling: ${fmt(st.yearTotal)} words"))
    }

    /** A resident at home, with its name, temper and level. */
    fun resident(ctx: Context, r: Resident, st: Stats, s: Settings): Card {
        val f = Fonts(ctx); val (bmp, c) = base(0xFF2A1A1C.toInt(), 0xFF120C0E.toInt())
        val nook = Buf(Nook.W, Nook.H); Nook.render(nook, r.id, true, CapSets.of(s.caps), 1.3f, 1f, stage = app.keyling.domain.Food.stageIndex(r.level(st)))
        val rr = RectF(60f, 130f, W - 60f, 130f + (W - 120f) * Nook.H / Nook.W)
        c.save(); c.clipPath(android.graphics.Path().apply { addRoundRect(rr, 36f, 36f, android.graphics.Path.Direction.CW) }); c.pixels(nook, 60, 130, W - 120); c.restore()
        val y = rr.bottom + 120; val p = PERSONAS.getValue(r.id)
        c.text(r.name, 80f, y, 92f, f.lora, 0xFFFFFFFF.toInt())
        c.text(tr("живёт под ${r.key} · ${r.stage(st)} · ур. ${r.level(st)}", "lives under ${r.key} · ${r.stage(st)} · lvl ${r.level(st)}"), 84f, y + 66, 36f, f.inter, 0xFFE8C9A8.toInt())
        c.text("«" + p.line(0) + "»", 84f, y + 150, 40f, f.loraI, 0xFFFBF3E4.toInt())
        c.footer(f); return Card(bmp, r.name)
    }

    /** A medal for one achievement. */
    fun achievement(ctx: Context, a: Ach): Card {
        val f = Fonts(ctx); val (bmp, c) = base(0xFF1E1C19.toInt(), 0xFF0E0D0C.toInt())
        val (c1, c2) = when (a.tier) { 'g' -> 0xFFF0C24E.toInt() to 0xFFC9952A.toInt(); 's' -> 0xFFB9C3D0.toInt() to 0xFF8693A4.toInt(); else -> 0xFFD0905A.toInt() to 0xFFA8693A.toInt() }
        val cx = W / 2f; val cy = 520f
        c.drawCircle(cx, cy, 230f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, cy - 230, 0f, cy + 230, c1, c2, Shader.TileMode.CLAMP) })
        c.drawCircle(cx, cy, 190f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f; color = 0x55FFFFFF })
        c.text(when (a.tier) { 'g' -> "★"; 's' -> "✦"; else -> "●" }, cx, cy + 70, 200f, f.interB, 0xFFFFFFFF.toInt(), center = true)
        c.text(a.name, cx, 900f, 80f, f.lora, 0xFFFFFFFF.toInt(), center = true)
        c.text(a.about, cx, 970f, 34f, f.inter, 0xFFC8C0B4.toInt(), center = true)
        c.footer(f); return Card(bmp, a.name)
    }

    // ---------- more cards: month, week, book, streak, calendar, records ----------
    private fun Canvas.bars(values: List<Int>, x: Float, y: Float, w: Float, h: Float, hi: (Int) -> Boolean, col: Int, dim: Int) {
        val max = (values.maxOrNull() ?: 1).coerceAtLeast(1); val gap = 8f; val bw = (w - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v -> val bh = (h * v / max).coerceAtLeast(6f); val left = x + i * (bw + gap)
            drawRoundRect(RectF(left, y + h - bh, left + bw, y + h), 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (hi(i)) col else dim }) }
    }
    private fun Canvas.kicker(f: Fonts, s: String, col: Int) = text(s, 80f, 150f, 46f, f.loraI, col)
    private fun Canvas.big(f: Fonts, n: String, label: String, y: Float = 290f) { text(n, 80f, y, 150f, f.lora, 0xFFFFFFFF.toInt()); text(label, 84f, y + 60, 44f, f.inter, 0xFFD8DCEA.toInt()) }
    private fun Canvas.facts(f: Fonts, items: List<Pair<String, String>>, y: Float, sub: Int) =
        items.forEachIndexed { i, (v, l) -> val x = 80f + i * 320f; text(v, x, y, 64f, f.interB, 0xFFFFFFFF.toInt()); text(l, x, y + 44, 28f, f.inter, sub) }

    private val MONTHS_RU = listOf("январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")
    private val MONTHS_EN = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val ru get() = app.keyling.domain.Lang.ru

    fun month(ctx: Context, st: Stats): Card {
        val f = Fonts(ctx); val (bmp, c) = base(0xFF2A3A2E.toInt(), 0xFF101814.toInt())
        val d = java.time.LocalDate.ofEpochDay(st.today); val first = d.withDayOfMonth(1).toEpochDay()
        val days = (0 until d.lengthOfMonth()).map { st.days[first + it]?.words ?: 0 }; val sum = days.sum(); val active = days.count { it > 0 }
        val name = if (ru) MONTHS_RU[d.monthValue - 1] else MONTHS_EN[d.monthValue - 1]
        c.kicker(f, tr("Мой $name в Клавишке", "My $name in Keyling"), 0xFFBFE0C8.toInt())
        c.big(f, fmt(sum), tr("слов за месяц", "words this month"))
        c.facts(f, listOf("$active" to tr("дней с письмом", "writing days"), fmt(days.maxOrNull() ?: 0) to tr("лучший день", "best day"), fmt(if (active > 0) sum / active else 0) to tr("в среднем", "on average")), 470f, 0xFF9CC4A8.toInt())
        c.bars(days, 80f, 620f, W - 160f, 420f, { first + it == st.today }, 0xFFFFD27A.toInt(), 0xFF6FB088.toInt())
        c.text("1", 84f, 1090f, 28f, f.inter, 0xFF9CC4A8.toInt()); c.text("${days.size}", W - 120f, 1090f, 28f, f.inter, 0xFF9CC4A8.toInt())
        c.footer(f); return Card(bmp, tr("Мой $name: ${fmt(sum)} слов", "My $name: ${fmt(sum)} words"))
    }

    fun week(ctx: Context, st: Stats): Card {
        val f = Fonts(ctx); val (bmp, c) = base(0xFF3A2A44.toInt(), 0xFF140E1A.toInt())
        val days = (6 downTo 0).map { st.days[st.today - it]?.words ?: 0 }; val sum = days.sum(); val top = days.max().coerceAtLeast(1)
        val loc = if (ru) java.util.Locale("ru") else java.util.Locale.UK
        val names = (6 downTo 0).map { java.time.LocalDate.ofEpochDay(st.today - it).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, loc) }
        c.kicker(f, tr("Моя неделя письма", "My writing week"), 0xFFD8C8F0.toInt())
        c.big(f, fmt(sum), tr("слов за семь дней", "words in seven days"))
        c.bars(days, 80f, 540f, W - 160f, 440f, { days[it] == top }, 0xFFFFD27A.toInt(), 0xFF8A78B8.toInt())
        val bw = (W - 160f - 48f) / 7
        names.forEachIndexed { i, n -> val x = 80f + i * (bw + 8) + bw / 2
            c.text(n, x, 1030f, 32f, f.inter, 0xFFB8A8D8.toInt(), center = true)
            c.text(fmt(days[i]), x, 980f - 440f * days[i] / top - 14, 28f, f.interB, 0xFFFFFFFF.toInt(), center = true) }
        c.text(tr("Серия: ${st.streak} дн. подряд", "Streak: ${st.streak} days"), 80f, 1140f, 40f, f.interB, 0xFFFFD27A.toInt())
        c.footer(f); return Card(bmp, tr("Неделя письма: ${fmt(sum)} слов", "My writing week: ${fmt(sum)} words"))
    }

    fun book(ctx: Context, st: Stats, s: Settings): Card {
        val f = Fonts(ctx); val (bmp, c) = base(0xFF3A2418.toInt(), 0xFF140C08.toInt())
        val b = st.books.firstOrNull { it.id == s.book } ?: st.books.first(); val w = st.bookWords(b); val pct = (w * 100 / b.goal.coerceAtLeast(1)).coerceAtMost(100)
        c.kicker(f, tr("Я пишу книгу", "I'm writing a book"), 0xFFF0D0B0.toInt())
        c.text("«${b.title}»", 80f, 290f, 84f, f.loraI, 0xFFFFFFFF.toInt())
        c.text(tr("${fmt(w)} из ${fmt(b.goal)} слов", "${fmt(w)} of ${fmt(b.goal)} words"), 84f, 370f, 44f, f.inter, 0xFFE0C8B0.toInt())
        val bar = RectF(80f, 420f, W - 80f, 470f)
        c.drawRoundRect(bar, 25f, 25f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF })
        c.drawRoundRect(RectF(bar.left, bar.top, bar.left + bar.width() * pct / 100f, bar.bottom), 25f, 25f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB866.toInt() })
        c.text("$pct%", 80f, 540f, 40f, f.interB, 0xFFFFD8A8.toInt())
        val chars = (w * CHARS_PER_WORD).toInt()
        c.facts(f, listOf(String.format("%.1f", chars.toFloat() / SHEET) to tr("авторских листов", "author's sheets"), fmt(chars / 1800) to tr("страниц", "pages"), "${st.bookChapters(b)}" to tr("глав", "chapters")), 660f, 0xFFC8A890.toInt())
        app.keyling.domain.CLASSICS.lastOrNull { it.third <= w }?.let { (r, e, _) -> c.text(tr("Уже длиннее, чем $r", "Already longer than $e"), 80f, 830f, 38f, f.loraI, 0xFFFFD8A8.toInt()) }
        val lines = (chars / 1800 / 20).coerceIn(2, 30)
        for (i in 0 until lines) { val y = 1120f - i * 9f; c.drawRect(560f + (i % 3) * 3f, y, 980f + (i % 2) * 4f, y + 6f, Paint().apply { color = if (i % 2 == 0) 0xFFF2EAD6.toInt() else 0xFFD8CCB0.toInt() }) }
        c.drawRect(760f, 1120f - lines * 9f, 772f, 1126f, Paint().apply { color = 0xFFB83A2A.toInt() })
        c.footer(f); return Card(bmp, tr("Моя книга «${b.title}»: $pct%", "My book «${b.title}»: $pct%"))
    }

    fun streak(ctx: Context, st: Stats): Card {
        val f = Fonts(ctx); val (bmp, c) = base(0xFF4A2A10.toInt(), 0xFF180C04.toInt())
        c.kicker(f, tr("Пишу каждый день", "Writing every day"), 0xFFFFD8A8.toInt())
        c.text("${st.streak}", W / 2f, 560f, 360f, f.lora, 0xFFFFFFFF.toInt(), center = true)
        c.text(tr("дней подряд", "days in a row"), W / 2f, 650f, 56f, f.inter, 0xFFFFD8A8.toInt(), center = true)
        val cell = (W - 160f) / 30
        for (i in 0 until 30) { val d = st.today - 29 + i; val w = st.days[d]?.words ?: 0; val rest = st.days[d]?.rest == true
            c.drawRoundRect(RectF(80f + i * cell, 780f, 80f + i * cell + cell - 6, 780f + cell - 6), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when { w > 0 -> 0xFFFFB866.toInt(); rest -> 0xFF8AA0C8.toInt(); else -> 0x33FFFFFF } }) }
        c.text(tr("последние 30 дней", "the last 30 days"), 80f, 900f, 30f, f.inter, 0xFFC8A888.toInt())
        c.text(tr("Рекорд: ${st.longest.first} дн.", "Best: ${st.longest.first} days"), 80f, 1000f, 44f, f.interB, 0xFFFFFFFF.toInt())
        c.footer(f); return Card(bmp, tr("${st.streak} дней подряд пишу", "${st.streak} days of writing in a row"))
    }

    fun calendar(ctx: Context, st: Stats): Card {
        val f = Fonts(ctx); val (bmp, c) = base(0xFF16224A.toInt(), 0xFF0A0F22.toInt())
        c.kicker(f, tr("Мой ${st.year} год, день за днём", "My ${st.year}, day by day"), 0xFFC8D2F0.toInt())
        c.big(f, "${st.activeDays}", tr("дней с письмом", "writing days"))
        val off = java.time.LocalDate.of(st.year, 1, 1).dayOfWeek.value - 1
        val cell = (W - 160f) / 53
        val heat = intArrayOf(0x22FFFFFF, 0xFF2A3866.toInt(), 0xFF3D55A6.toInt(), 0xFF5D7DE6.toInt(), 0xFFA2B6FF.toInt())
        st.yearDays.forEachIndexed { i, w -> val idx = i + off; val x = 80f + (idx / 7) * cell; val y = 560f + (idx % 7) * cell * 1.9f
            val lv = when { i > st.dayOfYear || w == 0 -> 0; w < 500 -> 1; w < 900 -> 2; w < 1500 -> 3; else -> 4 }
            c.drawRoundRect(RectF(x, y, x + cell - 3, y + cell * 1.9f - 4), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = heat[lv] }) }
        c.facts(f, listOf(fmt(st.yearTotal) to tr("слов", "words"), "${st.longest.first}" to tr("рекорд серии", "best streak"), fmt(st.avgPerDay) to tr("в день письма", "per writing day")), 1060f, 0xFF9AA6C8.toInt())
        c.footer(f); return Card(bmp, tr("${st.activeDays} дней с письмом в ${st.year}", "${st.activeDays} writing days in ${st.year}"))
    }

    fun records(ctx: Context, st: Stats): Card {
        val f = Fonts(ctx); val (bmp, c) = base(0xFF2A2A30.toInt(), 0xFF0E0E12.toInt())
        c.kicker(f, tr("Мои писательские рекорды", "My writing records"), 0xFFE0D8C8.toInt())
        val rows = listOfNotNull(st.bestDay?.let { tr("Лучший день", "Best day") to fmt(it.words) },
            tr("Лучшая неделя", "Best week") to fmt(st.bestWeek.first), tr("Лучший месяц", "Best month") to fmt(st.months.max()),
            tr("Самая длинная серия", "Longest streak") to "${st.longest.first} " + tr("дн.", "days"),
            st.s.bestSprint.takeIf { it > 0 }?.let { tr("Лучший спринт", "Best sprint") to tr("${it / 10} слов/мин", "${it / 10} wpm") },
            st.latestWord?.let { (m, _) -> tr("Самое позднее слово", "Latest word") to "%02d:%02d".format(m / 60, m % 60) })
        rows.forEachIndexed { i, (l, v) -> val y = 300f + i * 150f
            c.drawRoundRect(RectF(70f, y - 80f, W - 70f, y + 40f), 30f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x18FFFFFF })
            c.text(l, 110f, y - 6, 40f, f.inter, 0xFFC8C0B4.toInt())
            c.drawText(v, W - 110f, y - 6, Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 56f; typeface = f.interB; color = 0xFFFFD27A.toInt(); textAlign = Paint.Align.RIGHT }) }
        c.footer(f); return Card(bmp, tr("Мои писательские рекорды", "My writing records"))
    }
}
