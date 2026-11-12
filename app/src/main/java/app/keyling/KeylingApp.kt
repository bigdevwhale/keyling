package app.keyling

import android.app.Application
import android.content.Context
import androidx.core.content.res.ResourcesCompat
import app.keyling.data.Book
import app.keyling.data.Caught
import app.keyling.data.DemoSeed
import app.keyling.data.Entry
import app.keyling.data.KeyDb
import app.keyling.data.Settings
import app.keyling.data.SettingsStore
import app.keyling.domain.Achievements
import app.keyling.domain.Ability
import app.keyling.domain.Lang
import app.keyling.domain.Stats
import app.keyling.ui.art.Glyphs
import app.keyling.ui.art.ArtCache
import app.keyling.ui.art.CapSets
import app.keyling.ui.art.Decor
import app.keyling.data.Mug
import app.keyling.ui.Sound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What a sitting earned, for the toast and the pet's reaction. */
data class Reward(val coins: Int, val norm: Boolean, val chapter: Boolean, val achievements: List<String>)

class AppContainer(context: Context) {
    val app: Context = context.applicationContext
    val db = KeyDb.create(app)
    val dao = db.dao()
    val settings = SettingsStore(app)
    val sound = Sound(app)
    /** Desk drawings, kept on disk too; named by when this version was installed, so an update draws them anew. */
    val art = ArtCache(java.io.File(app.cacheDir, "art"), runCatching { app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime }.getOrDefault(0L))
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Little notes for the screens: «+12 буквиц», an achievement… */
    val news = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun write(block: suspend AppContainer.() -> Unit) { scope.launch { block() } }

    fun today(): Long = LocalDate.now().toEpochDay()

    suspend fun stats(): Stats = Stats(dao.bookList(), dao.entryList(), dao.caughtList(), settings.now(), today())

    /** Makes sure there is a book to write in, and returns its id. */
    suspend fun currentBook(): Long {
        val s = settings.now(); val books = dao.bookList()
        books.firstOrNull { it.id == s.book }?.let { return it.id }
        val id = books.firstOrNull { it.status == "active" }?.id ?: dao.insert(Book(title = if (Lang.ru) "Моя книга" else "My book", created = today()))
        settings.update { it.copy(book = id) }
        return id
    }

    /** Logs written words (feeds the pet, pays буквицы), or a decrease as a cut. */
    suspend fun addWords(n: Int, chapter: Boolean = false, kind: String = "add", minutes: Int = 0): Reward {
        val book = currentBook(); val before = stats(); val s = settings.now()
        val day = today(); val now = System.currentTimeMillis()
        if (n < 0) { dao.insert(Entry(bookId = book, day = day, at = now, kind = "cut", words = n)); return reconcile(0, false, false) }
        dao.insert(Entry(bookId = book, day = day, at = now, kind = kind, words = n, minutes = minutes, chapter = chapter))
        val goal = Ability.normAt(s.pet, s.norm)
        val normHit = before.todayWords < goal && before.todayWords + n >= goal
        val hour = java.time.LocalTime.now().hour
        val coins = Ability.wordCoins(s.pet, n, kind, hour) + (if (normHit) 20 else 0) + (if (chapter) Ability.chapterCoins(s.pet) else 0)
        return reconcile(coins, normHit, chapter)
    }

    suspend fun setTotal(total: Int): Int {
        val book = currentBook(); val st = stats(); val b = st.books.first { it.id == book }
        val delta = total - st.bookWords(b)
        if (delta != 0) addWords(delta)
        return delta
    }

    suspend fun logEdit(minutes: Int) = run {
        dao.insert(Entry(bookId = currentBook(), day = today(), at = System.currentTimeMillis(), kind = "edit", minutes = minutes))
        reconcile(Ability.editCoins(settings.now().pet, minutes), false, false)
    }

    suspend fun restDay() = run { dao.insert(Entry(bookId = currentBook(), day = today(), at = System.currentTimeMillis(), kind = "rest")); reconcile(0, false, false) }

    suspend fun pastWords(n: Int) { val id = currentBook(); dao.bookList().firstOrNull { it.id == id }?.let { dao.update(it.copy(startWords = it.startWords + n)) } }

    suspend fun catchBug(kind: Int) { dao.insert(Caught(kind = kind, at = System.currentTimeMillis())); reconcile(1, false, false) }

    suspend fun stroke() { settings.update { it.copy(pets = it.pets + 1) }; reconcile(0, false, false) }

    /** Pays [coins] and any newly earned achievements once. */
    suspend fun reconcile(coins: Int, norm: Boolean, chapter: Boolean): Reward {
        val st = stats(); val s = st.s
        val fresh = Achievements.all(st).filter { it.done && it.id !in s.claimed }
        val bonus = fresh.sumOf { it.reward }
        settings.update { it.copy(coins = it.coins + coins + bonus, claimed = it.claimed + fresh.map { a -> a.id }) }
        fresh.forEach { news.tryEmit("🏅 " + it.name + " · +" + it.reward) }
        return Reward(coins + bonus, norm, chapter, fresh.map { it.name })
    }

    suspend fun buy(key: String, price: Int): Boolean {
        val s = settings.now(); if (s.coins < price) return false
        settings.update { it.copy(coins = it.coins - price, owned = it.owned + key) }; reconcile(0, false, false); return true
    }
}

class KeylingApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Lang.ru = resources.configuration.locales[0].language == "ru"
        Glyphs.face = ResourcesCompat.getFont(this, R.font.press_start)
        Glyphs.smoothFace = ResourcesCompat.getFont(this, R.font.inter_semibold)
        container = AppContainer(this)
        // Build the desk while the window is still opening, so it's there on the first frame: its three pieces
        // side by side, each on its own core. The desk screen waits for these rather than building them again.
        container.scope.launch(Dispatchers.Default) { runCatching {
            val s = container.settings.now(); val set = CapSets.of(s.caps); val decor = Decor(s.desk, s.mug, s.plant, s.lamp, s.mascot)
            val custom = Mug.load(this@KeylingApp)
            launch { runCatching { container.art.bg(decor, set, custom) } }
            launch { runCatching { container.art.sprites(decor, custom) } }
            launch { runCatching { container.art.caps(set, container.stats().activeDays) } } } }
    }
}

val Context.container: AppContainer get() = (applicationContext as KeylingApp).container
