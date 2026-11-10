package app.keyling.data

import app.keyling.AppContainer
import app.keyling.domain.Lang
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** The demo build (-Pdemo) starts with a writer's year already lived: three books, a streak, a few residents. */
object DemoSeed {
    suspend fun seedIfEmpty(c: AppContainer) {
        if (c.dao.bookList().isNotEmpty()) return
        val ru = Lang.ru; val today = c.today(); val zone = ZoneId.systemDefault()
        val start = LocalDate.of(LocalDate.ofEpochDay(today).year, 1, 5).toEpochDay()
        val dream = c.dao.insert(Book(title = if (ru) "Сон в руку" else "A Dream Come True", kind = "novella", goal = 42000, status = "done", color = 2,
            created = start, finished = start + 133, drafts = 2, planned = 12))
        val wind = c.dao.insert(Book(title = if (ru) "Северный ветер" else "North Wind", kind = "novel", goal = 80000, deadline = today + 111, color = 0,
            created = start + 140, planned = 22, startWords = 0))
        val shore = c.dao.insert(Book(title = if (ru) "Рассказы о маяке" else "Lighthouse Stories", kind = "stories", goal = 60000, status = "paused", color = 1, created = start + 134))
        val r = Random(42)
        var d = start; var ch = 0
        while (d <= today) {
            val date = LocalDate.ofEpochDay(d); val wd = date.dayOfWeek.value - 1
            val off = d < today - 13 && r.nextFloat() < (if (wd >= 5) .3f else .13f)
            val vacation = d - start in 120..129
            val book = when { d - start < 134 -> dream; d - start < 200 -> shore; else -> wind }
            fun at(h: Int, m: Int) = date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
            if (vacation && d - start == 121L) c.dao.insert(Entry(bookId = book, day = d, at = at(10, 0), kind = "rest"))
            else if (!off && !vacation) {
                val w = (250 + r.nextFloat() * 850 + (if (r.nextFloat() < .07f) 1400 + r.nextFloat() * 1600 else 0f) + (if (wd == 5) 250 else 0)).toInt()
                val h = if (r.nextFloat() < .7f) 19 + r.nextInt(4) else if (r.nextFloat() < .5f) 6 else 23
                val chapter = r.nextFloat() < .09f
                if (chapter) ch++
                if (r.nextFloat() < .12f) { c.dao.insert(Entry(bookId = book, day = d, at = at(h, 5), kind = "sprint", words = w / 3, minutes = 15)); c.dao.insert(Entry(bookId = book, day = d, at = at(h, 40), words = w - w / 3, chapter = chapter)) }
                else c.dao.insert(Entry(bookId = book, day = d, at = at(h, r.nextInt(50)), words = w, chapter = chapter))
                if (r.nextFloat() < .18f) c.dao.insert(Entry(bookId = book, day = d, at = at(h, 55), kind = "edit", minutes = 15 + r.nextInt(70)))
                if (r.nextFloat() < .04f) c.dao.insert(Entry(bookId = book, day = d, at = at(1, 20 + r.nextInt(30)), words = 120 + r.nextInt(300)))
            }
            d++
        }
        c.dao.insert(Entry(bookId = dream, day = start + 160, at = LocalDate.ofEpochDay(start + 160).atTime(18, 0).atZone(zone).toInstant().toEpochMilli(), kind = "cut", words = -6200))
        repeat(38) { c.dao.insert(Caught(kind = r.nextInt(6), at = System.currentTimeMillis() - it * 86_400_000L * 5)) }
        c.settings.update { it.copy(book = wind, coins = 340, owned = setOf("caps:pastel"), pets = 63, sprints = 46, ghostWins = 7, bestSprint = 280, onboarded = true) }
        // the year's awards count as already given, so the demo doesn't open with a shower of notes
        val done = app.keyling.domain.Achievements.all(c.stats()).filter { it.done }.map { it.id }
        c.settings.update { it.copy(coins = 340, claimed = done.toSet()) }
    }
}
