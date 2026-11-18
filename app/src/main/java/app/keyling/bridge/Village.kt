package app.keyling.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import app.keyling.data.Backup
import app.keyling.BuildConfig
import app.keyling.KeylingApp
import app.keyling.AppContainer
import app.keyling.data.Mug
import app.keyling.domain.Food
import app.keyling.domain.Lang
import app.keyling.domain.Resident
import app.keyling.domain.Residents
import app.keyling.domain.Stats
import app.keyling.domain.plural
import app.keyling.ui.art.Board
import app.keyling.ui.art.Buf
import app.keyling.ui.art.CapSets
import app.keyling.ui.art.Critters
import app.keyling.ui.art.Decor
import app.keyling.ui.art.Desk
import app.keyling.ui.art.Looks
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * The bridge to Lingvale, the village where the pets of all the -ling apps live together.
 *
 * Lingvale asks through ContentResolver.call() and nobody else gets an answer: the caller must be Lingvale signed
 * with its release key (a debug or demo build also answers a Lingvale built with the same debug key).
 * Protocol v2, the same in every -ling app:
 *  - "hello"    → {v}
 *  - "snapshot" → {json}: pets, species, days of activity, the last week's events
 *  - "art"      → {bytes}: one picture (pet_*, egg_*, desk_bg), painted from the app's own sprites
 *  - "backup"   (extras {fd}: a file open for writing) → {ok, name}: writes the same backup file the settings save
 *  - "restore"  (extras {fd}: a file open for reading) → {ok, error}: loads such a file, as the settings' «Load from a file»
 *    does, replacing what is on the phone; error is "newer" (a file from a newer version), "busy" or "bad"
 * Only "restore" changes anything here, and Lingvale asks the player before it calls it.
 */
class VillageProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (!trusted(ctx)) throw SecurityException("Only Lingvale may ask")
        return when (method) {
            "hello" -> Bundle().apply { putInt("v", VERSION) }
            "snapshot" -> Bundle().apply { putString("json", runBlocking { snapshot(ctx, container(ctx)) }.toString()) }
            "art" -> art(ctx, arg)
            "backup" -> backup(ctx, extras)
            "restore" -> restore(ctx, extras)
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun fd(extras: Bundle?): ParcelFileDescriptor? = extras?.getParcelable("fd")

    /** Writes the settings' backup file into the file Lingvale opened. */
    private fun backup(ctx: Context, extras: Bundle?): Bundle {
        val fd = fd(extras) ?: return Bundle().apply { putBoolean("ok", false) }
        val ok = runCatching {
            val c = container(ctx)
            ParcelFileDescriptor.AutoCloseOutputStream(fd).use { out -> runBlocking { Backup.write(ctx, c.dao, c.settings.now(), out) } }
        }.onFailure { runCatching { fd.close() } }.isSuccess
        return Bundle().apply { putBoolean("ok", ok); putString("name", "keyling.keyling") }
    }

    /** Loads a backup file from Lingvale, the way the settings do. */
    private fun restore(ctx: Context, extras: Bundle?): Bundle {
        val fd = fd(extras) ?: return Bundle().apply { putBoolean("ok", false); putString("error", "bad") }
        val ok = runCatching {
            val c = container(ctx)
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { input -> runBlocking { Backup.read(ctx, c.dao, c.settings, input) } }
        }.getOrElse { runCatching { fd.close() }; false }
        return Bundle().apply { putBoolean("ok", ok); if (!ok) putString("error", "bad") }
    }

    private fun trusted(ctx: Context): Boolean {
        val pkg = callingPackage ?: return false
        if (pkg != HUB && !pkg.startsWith("$HUB.")) return false
        val pm = ctx.packageManager
        if (HUB_CERTS.any { pm.hasSigningCertificate(pkg, it, PackageManager.CERT_INPUT_SHA256) }) return true
        return BuildConfig.DEBUG && pm.checkSignatures(pkg, ctx.packageName) == PackageManager.SIGNATURE_MATCH
    }

    private fun art(ctx: Context, name: String?): Bundle? {
        if (name == null || !ART.matches(name)) return null
        val bytes = resArt(ctx, container(ctx), name) ?: return null
        return Bundle().apply { putByteArray("bytes", bytes) }
    }

    /** The provider can be asked before the application has finished starting; wait for it a little. */
    private fun container(ctx: Context): AppContainer {
        repeat(100) {
            runCatching { return (ctx.applicationContext as KeylingApp).container }
            Thread.sleep(30)
        }
        error("not ready")
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val VERSION = 2
        const val HUB = "app.lingvale"
        /** SHA-256 of Lingvale's release certificate. */
        private val HUB_CERTS = listOf(hex("CEF40A1786896D829CFA53115693AC9A53FC0349EAB84BAF64EFA648592D1ABF"))
        private val ART = Regex("""(pet|egg|item|find)_[a-z0-9_]+|[a-z]+_bg""")
        private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        private fun color(c: Long) = "#%06X".format(c and 0xFFFFFF)
        private fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

        /** The cut of the residents' sprites sent to the village: the biggest one tools/cut.py makes. */
        private const val CUT = 144
        /** Pictures are scaled up (nearest neighbour) to about this height. */
        private const val TARGET = 288

        private fun png(bmp: Bitmap): ByteArray = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        private fun png(px: IntArray, w: Int, h: Int): ByteArray {
            val src = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
            val k = (TARGET.toFloat() / maxOf(w, h)).roundToInt().coerceAtLeast(1)
            return png(if (k == 1) src else Bitmap.createScaledBitmap(src, w * k, h * k, false))
        }

        /**
         * Keyling's residents are sprites in java resources (/pets/<id>_<stage>_<cut>.bin), read by Critters; the desk
         * is painted in code. pet_<id>_0 baby · _1 youngster · _2 grown · _5 grown asleep; egg_<id> is the dark shape of
         * a resident who hasn't moved in yet; desk_bg is the desk as the writer set it up, keycaps on, no resident.
         */
        private fun resArt(ctx: Context, c: AppContainer, name: String): ByteArray? {
            when {
                name.startsWith("egg_") -> {
                    val id = name.removePrefix("egg_"); if (Residents.all.none { it.id == id }) return null
                    val sp = Critters.sprite(id, 0, CUT) ?: return null
                    val b = Buf(sp.w + 4, sp.h + 4)
                    Critters.silhouette(b, id, 0, b.w / 2f, (sp.h + 1).toFloat(), CUT)
                    return png(b.d, b.pw, b.ph)
                }
                name.startsWith("pet_") -> {
                    val id = name.removePrefix("pet_").substringBeforeLast('_'); if (Residents.all.none { it.id == id }) return null
                    val frame = name.substringAfterLast('_')
                    val stage = when (frame) { "0" -> 0; "1" -> 1; "2", "5" -> 2; else -> return null }
                    val sp = Critters.sprite(id, stage, CUT) ?: return null
                    return png(if (frame == "5") sp.shut else sp.open, sp.w, sp.h)
                }
                name == "desk_bg" -> {
                    val s = runBlocking { c.settings.now() }; val set = CapSets.of(s.caps)
                    val decor = Decor(s.desk, s.mug, s.plant, s.lamp, s.mascot); val custom = Mug.load(ctx)
                    val wear = runBlocking { c.stats().activeDays }
                    val out = Buf(Desk.W, Desk.H, Desk.S)
                    out.copyFrom(c.art.bg(decor, set, custom))
                    fun at(id: String) = s.pos[id] ?: (0 to 0)
                    for (sp in c.art.sprites(decor, custom).sortedBy { it.y + it.buf.h + at(it.id).second }) {
                        val (dx, dy) = at(sp.id); out.blit(sp.buf, sp.x + dx, sp.y + dy)
                    }
                    val caps = c.art.caps(set, wear)
                    for (k in Board.keys) caps[k]?.let { out.blit(it.up, k.x, k.y) }
                    return png(out.toBitmap())
                }
                else -> return null
            }
        }

        /** What a resident has eaten so far would make its level: Resident.level, for any amount. */
        private fun levelAt(r: Resident, a: Int): Int {
            val open = r.id == "space" || a >= r.steps[0]
            return if (!open) 0 else 1 + r.steps.count { a >= it } - (if (r.id == "space") 0 else 1)
        }

        /** The least amount that brings [r] to [level], or null if no amount does. */
        private fun amountFor(r: Resident, level: Int): Int? = (listOf(0) + r.steps.toList()).firstOrNull { levelAt(r, it) >= level }

        /** The first level of each growth stage (Food.stageIndex): baby 0, youngster 3, grown 6, ancient 9. */
        private val STAGE_START = intArrayOf(0, 3, 6, 9)

        /** Growth within the stage the resident is in now, 0..1. */
        private fun progress(r: Resident, a: Int): Float {
            val lvl = levelAt(r, a)
            if (lvl == 0) return (a.toFloat() / r.steps[0]).coerceIn(0f, 1f)
            val si = Food.stageIndex(lvl); if (si >= 3) return 1f
            val lo = amountFor(r, STAGE_START[si].coerceAtLeast(1)) ?: return 1f
            val hi = amountFor(r, STAGE_START[si + 1]) ?: return 1f
            return if (hi <= lo) 1f else ((a - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f)
        }

        private fun ruWords(n: Int) = plural(n, "слово", "слова", "слов")

        /** Everything Lingvale shows of Keyling: the seven residents under the keys, sittings as days and events. */
        suspend fun snapshot(ctx: Context, c: AppContainer): JSONObject {
            val st = c.stats(); val s = st.s
            val books = st.books; val entries = st.entries; val caught = st.caught
            val today = LocalDate.now().toEpochDay()

            // When each resident first got food and when it moved in: replay the sittings day by day.
            val firstDay = (entries.map { it.day } + books.map { it.created }.filter { it > 0 }).minOrNull() ?: today
            val found = HashMap<String, Long>(); val hatched = HashMap<String, Long>()
            for (d in entries.map { it.day }.distinct().sorted()) {
                if (found.size == Residents.all.size && hatched.size == Residents.all.size) break
                val past = Stats(books, entries.filter { it.day <= d }, caught.filter { dayOf(it.at) <= d }, s, d)
                for (r in Residents.all) {
                    if (r.id !in found && (r.amount(past) > 0 || r.open(past))) found[r.id] = d
                    if (r.id !in hatched && r.open(past) && (r.id != "space" || r.amount(past) > 0)) hatched[r.id] = d
                }
            }

            // Who shows: residents who moved in, and those who have eaten a little but not enough yet (eggs).
            class Row(val idx: Int, val r: Resident, val amount: Int, val level: Int)
            val rows = Residents.all.mapIndexed { i, r -> Row(i, r, r.amount(st), r.level(st)) }
                .filter { it.level > 0 || it.amount > 0 }
            // The resident in the nook leads; the two most grown of the rest walk with it.
            val team = rows.filter { it.level > 0 && it.r.id != s.pet }.sortedByDescending { it.level }.take(2)
            fun slot(row: Row) = when { row.r.id == s.pet && row.level > 0 -> 0; else -> team.indexOf(row).let { if (it < 0) -1 else it + 1 } }

            val petsJson = JSONArray()
            rows.forEach { row ->
                val r = row.r
                val stage = if (row.level == 0) 0 else Food.stageIndex(row.level) + 1
                val f = found[r.id] ?: firstDay
                petsJson.put(JSONObject()
                    .put("id", row.idx + 1).put("species", r.id).put("name", if (Lang.ru) r.nameRu else r.nameEn)
                    .put("stage", stage).put("progress", progress(r, row.amount).toDouble())
                    .put("slot", slot(row)).put("shiny", false).put("found", f)
                    .put("hatched", if (stage == 0) 0 else hatched[r.id] ?: f))
            }
            val speciesJson = JSONArray()
            Residents.all.forEach { r ->
                val look = Looks.of[r.id]
                speciesJson.put(JSONObject().put("id", r.id).put("ru", r.nameRu).put("en", r.nameEn)
                    .put("color", color((look?.r?.get(3) ?: 0xC96A32).toLong()))
                    .put("aboutRu", r.aboutRu).put("aboutEn", r.aboutEn).put("rare", false))
            }
            val writing = setOf("add", "sprint", "edit")
            val daysJson = JSONArray()
            entries.filter { it.kind in writing && (it.words > 0 || it.minutes > 0) }.groupBy { it.day }.toSortedMap().forEach { (d, l) ->
                if (d > today - 400) daysJson.put(JSONObject().put("d", d).put("n", l.size).put("min", 0))
            }
            val titles = books.associate { it.id to it.title }
            val eventsJson = JSONArray()
            entries.filter { it.day > today - 8 }.forEach { e ->
                val t = titles[e.bookId].orEmpty(); val tail = if (t.isBlank()) "" else " · $t"
                val (ru, en) = when (e.kind) {
                    "add", "sprint" -> if (e.words <= 0) return@forEach else {
                        val sp = e.kind == "sprint"
                        val ch = if (e.chapter) " · глава" to " · chapter" else "" to ""
                        "+${e.words} ${ruWords(e.words)}${if (sp) " · спринт" else ""}${ch.first}$tail" to
                            "+${e.words} ${if (e.words == 1) "word" else "words"}${if (sp) " · sprint" else ""}${ch.second}$tail"
                    }
                    "edit" -> "Правка · ${e.minutes} мин$tail" to "Editing · ${e.minutes} min$tail"
                    "cut" -> "−${-e.words} ${ruWords(-e.words)} вычеркнуто$tail" to "−${-e.words} ${if (e.words == -1) "word" else "words"} cut$tail"
                    "rest" -> "Честный выходной" to "Honest day off"
                    else -> return@forEach
                }
                eventsJson.put(JSONObject().put("at", e.at).put("min", 0).put("ru", ru).put("en", en))
            }
            return JSONObject()
                .put("v", VERSION).put("app", "keyling").put("version", BuildConfig.VERSION_NAME)
                .put("ru", Lang.ru).put("scene", "desk_bg")
                .put("pets", petsJson).put("species", speciesJson).put("days", daysJson).put("events", eventsJson)
        }
    }
}
