package app.keyling

import androidx.test.core.app.ApplicationProvider
import app.keyling.data.Book
import app.keyling.data.Entry
import app.keyling.data.Settings
import app.keyling.domain.Lang
import app.keyling.domain.Stats
import app.keyling.ui.Share
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/** `-Prender`: draws every share card to app/build/art/card-*.png, to look at them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru")
class RenderCards {
    @Test fun cards() {
        Lang.ru = true
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val today = LocalDate.of(2026, 11, 10).toEpochDay(); val r = Random(3)
        val book = Book(id = 1, title = "Северный ветер", goal = 80000, planned = 22)
        val entries = (0 until 300).mapNotNull { i -> val d = today - 299 + i
            if (r.nextFloat() < .15f && i < 286) null else Entry(bookId = 1, day = d, at = LocalDate.ofEpochDay(d).atTime(20, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                words = 300 + r.nextInt(900), chapter = r.nextFloat() < .05f) }
        val s = Settings(book = 1, bestSprint = 280)
        val st = Stats(listOf(book), entries, emptyList(), s, today)
        val out = File(System.getProperty("render.out") ?: "build/art").apply { mkdirs() }
        val cards = mapOf("year" to Share.year(ctx, st, s), "month" to Share.month(ctx, st), "week" to Share.week(ctx, st), "book" to Share.book(ctx, st, s),
            "streak" to Share.streak(ctx, st), "calendar" to Share.calendar(ctx, st), "records" to Share.records(ctx, st))
        for ((n, c) in cards) File(out, "card-$n.png").outputStream().use { c.bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
