package app.keyling.domain

import app.keyling.data.Book
import app.keyling.data.Caught
import app.keyling.data.Entry
import app.keyling.data.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

object Lang { @Volatile var ru = true }
fun tr(ru: String, en: String) = if (Lang.ru) ru else en

fun fmt(n: Int): String = String.format(if (Lang.ru) Locale("ru") else Locale.US, "%,d", n).replace(',', if (Lang.ru) ' ' else ',')
fun fmtDay(day: Long): String = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern(if (Lang.ru) "d MMMM" else "d MMMM", if (Lang.ru) Locale("ru") else Locale.UK))
fun plural(n: Int, one: String, few: String, many: String): String { val a = n % 100; val b = n % 10
    return if (a in 11..14) many else when (b) { 1 -> one; 2, 3, 4 -> few; else -> many } }
fun words(n: Int) = if (Lang.ru) "${fmt(n)} ${plural(n, "слово", "слова", "слов")}" else "${fmt(n)} ${if (n == 1) "word" else "words"}"
fun daysW(n: Int) = if (Lang.ru) "$n ${plural(n, "день", "дня", "дней")}" else "$n ${if (n == 1) "day" else "days"}"

const val CHARS_PER_WORD = 6.4f
const val SHEET = 40000      // авторский лист, characters
const val PAGE = 1800        // машинописная страница, characters

/** A day of writing, all sittings together. */
class Day(val day: Long) {
    var words = 0; var edit = 0; var rest = false; var chapters = 0; var night = false; var early = false; var sprints = 0; var latest = -1
}

/** Everything the screens show, worked out from the sittings. */
class Stats(val books: List<Book>, val entries: List<Entry>, val caught: List<Caught>, val s: Settings, val today: Long, zone: ZoneId = ZoneId.systemDefault()) {
    val days = HashMap<Long, Day>()
    val hours = IntArray(24)
    var fed = 0; var editMin = 0; var chapters = 0; var sprintCount = 0; var restDays = 0; var nightSessions = 0; var earlySessions = 0; var biggestCut = 0

    init {
        for (e in entries) {
            val d = days.getOrPut(e.day) { Day(e.day) }
            val t = Instant.ofEpochMilli(e.at).atZone(zone); val h = t.hour; val mins = h * 60 + t.minute
            when (e.kind) {
                "add", "sprint" -> { if (e.words > 0) { d.words += e.words; fed += e.words; hours[h] += e.words
                    if (h < 5) { d.night = true; d.latest = max(d.latest, mins) }; if (h in 5..6) d.early = true } }
                "cut" -> biggestCut = max(biggestCut, -e.words)
                "edit" -> { d.edit += e.minutes; editMin += e.minutes }
                "rest" -> { d.rest = true; restDays++ }
            }
            if (e.kind == "sprint") { d.sprints++; sprintCount++ }
            if (e.chapter) { d.chapters++; chapters++ }
        }
        nightSessions = days.values.count { it.night }; earlySessions = days.values.count { it.early }
    }

    fun bookWords(b: Book) = b.startWords + entries.filter { it.bookId == b.id && it.kind in setOf("add", "sprint", "cut", "past") }.sumOf { it.words }
    fun bookDays(b: Book) = entries.filter { it.bookId == b.id && it.words > 0 }.map { it.day }.distinct().size
    fun bookChapters(b: Book) = entries.count { it.bookId == b.id && it.chapter }
    fun bookEdit(b: Book) = entries.filter { it.bookId == b.id && it.kind == "edit" }.sumOf { it.minutes }

    val todayWords get() = days[today]?.words ?: 0
    private fun wrote(d: Long) = (days[d]?.words ?: 0) > 0
    private fun kept(d: Long) = wrote(d) || days[d]?.rest == true

    /** Writing days in a row up to today (or yesterday, if today is still empty); rest days keep the chain. */
    val streak: Int get() {
        var d = if (kept(today)) today else today - 1; var n = 0; val spare = s.pet == "esc"; val used = HashSet<Long>()
        while (true) {
            if (kept(d)) { if (wrote(d)) n++; d--; continue }
            val week = Math.floorDiv(d + 3, 7L) // Monday-based weeks
            if (spare && n > 0 && week !in used && kept(d - 1)) { used += week; d--; continue }
            break
        }
        return n
    }
    val longest: Pair<Int, Long> get() { var best = 0; var end = 0L; var run = 0
        val all = days.keys.sorted(); if (all.isEmpty()) return 0 to today
        var d = all.first(); while (d <= today) { if (kept(d)) { if (wrote(d)) run++ } else run = 0; if (run > best) { best = run; end = d }; d++ }
        return best to end }
    /** No words and no rest for a week: dust settles on the keyboard. */
    val dusty: Boolean get() { if (entries.isEmpty()) return false; for (i in 0..6) if (kept(today - i)) return false; return true }

    val year = LocalDate.ofEpochDay(today).year
    private val yearStart = LocalDate.of(year, 1, 1).toEpochDay()
    val yearDays: List<Int> = (0 until LocalDate.ofEpochDay(yearStart).lengthOfYear()).map { days[yearStart + it]?.words ?: 0 }
    val dayOfYear = (today - yearStart).toInt()
    val yearTotal = yearDays.take(dayOfYear + 1).sum()
    val activeDays = yearDays.take(dayOfYear + 1).count { it > 0 }
    val months: IntArray = IntArray(12).also { m -> yearDays.forEachIndexed { i, w -> m[LocalDate.ofEpochDay(yearStart + i).monthValue - 1] += w } }
    val weekdays: IntArray get() { val sum = IntArray(7); val cnt = IntArray(7)
        for ((d, v) in days) if (v.words > 0) { val wd = LocalDate.ofEpochDay(d).dayOfWeek.value - 1; sum[wd] += v.words; cnt[wd]++ }
        return IntArray(7) { if (cnt[it] == 0) 0 else sum[it] / cnt[it] } }
    val allWords get() = fed
    val bestDay: Day? get() = days.values.maxByOrNull { it.words }?.takeIf { it.words > 0 }
    val bestWeek: Pair<Int, Long> get() { var best = 0; var end = 0L; val all = days.keys.sorted(); if (all.isEmpty()) return 0 to today
        var d = all.first(); while (d <= today) { var s7 = 0; for (i in 0..6) s7 += days[d - i]?.words ?: 0; if (s7 > best) { best = s7; end = d }; d++ }; return best to end }
    val latestWord: Pair<Int, Long>? get() = days.values.filter { it.latest >= 0 }.maxByOrNull { it.latest }?.let { it.latest to it.day }
    val avgPerDay get() = if (activeDays == 0) 0 else yearTotal / activeDays

    /** Year pace: where the writer should be today, and where this pace ends the year. */
    fun pace(goal: Int): Triple<Int, Int, Int> {
        val len = yearDays.size; val should = (goal.toLong() * (dayOfYear + 1) / len).toInt()
        val per = yearTotal.toFloat() / (dayOfYear + 1); val forecast = (yearTotal + per * (len - 1 - dayOfYear)).toInt()
        val need = if (yearTotal >= goal) 0 else ((goal - yearTotal) + (len - dayOfYear) - 1) / max(1, len - dayOfYear)
        return Triple(should, forecast, need)
    }
}
