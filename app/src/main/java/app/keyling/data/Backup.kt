package app.keyling.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/** Moves all progress to another phone: books, sittings, caught bugs, settings and the user's own mug, in one .keyling file. */
object Backup {
    suspend fun export(ctx: Context, dao: KeyDao, s: Settings, uri: Uri) =
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { write(ctx, dao, s, it) }

    /** The file itself, wherever it goes: the settings' «Save to a file» or Lingvale's copy of the whole family. */
    suspend fun write(ctx: Context, dao: KeyDao, s: Settings, out: OutputStream) {
        val j = JSONObject()
        j.put("app", "keyling"); j.put("version", 1); j.put("settings", s.json())
        j.put("books", JSONArray(dao.bookList().map { b -> JSONObject().apply {
            put("id", b.id); put("title", b.title); put("kind", b.kind); put("goal", b.goal); put("deadline", b.deadline); put("startWords", b.startWords)
            put("planned", b.planned); put("color", b.color); put("status", b.status); put("created", b.created); put("finished", b.finished); put("drafts", b.drafts) } }))
        j.put("entries", JSONArray(dao.entryList().map { e -> JSONObject().apply {
            put("bookId", e.bookId); put("day", e.day); put("at", e.at); put("kind", e.kind); put("words", e.words); put("minutes", e.minutes); put("chapter", e.chapter) } }))
        j.put("caught", JSONArray(dao.caughtList().map { c -> JSONObject().apply { put("kind", c.kind); put("at", c.at) } }))
        Mug.load(ctx)?.let { j.put("mug", JSONArray(it.toList())) }
        out.write(j.toString().toByteArray())
    }

    /** Replaces everything with the file's contents. Returns false if it isn't a Keyling file. */
    suspend fun import(ctx: Context, dao: KeyDao, store: SettingsStore, uri: Uri): Boolean =
        ctx.contentResolver.openInputStream(uri)?.use { read(ctx, dao, store, it) } ?: false

    /** Replaces everything with what [input] holds. Returns false, touching nothing, if it isn't a Keyling file. */
    suspend fun read(ctx: Context, dao: KeyDao, store: SettingsStore, input: InputStream): Boolean {
        val j = runCatching { JSONObject(input.readBytes().decodeToString()) }.getOrNull() ?: return false
        if (j.optString("app") != "keyling" || j.optJSONObject("settings") == null) return false
        dao.clearEntries(); dao.clearBooks(); dao.clearCaught()
        val ids = HashMap<Long, Long>()
        j.optJSONArray("books")?.let { a -> for (i in 0 until a.length()) { val b = a.getJSONObject(i)
            ids[b.getLong("id")] = dao.insert(Book(title = b.getString("title"), kind = b.optString("kind", "novel"), goal = b.optInt("goal", 80000), deadline = b.optLong("deadline"),
                startWords = b.optInt("startWords"), planned = b.optInt("planned"), color = b.optInt("color"), status = b.optString("status", "active"),
                created = b.optLong("created"), finished = b.optLong("finished"), drafts = b.optInt("drafts", 1))) } }
        j.optJSONArray("entries")?.let { a -> for (i in 0 until a.length()) { val e = a.getJSONObject(i)
            dao.insert(Entry(bookId = ids[e.getLong("bookId")] ?: continue, day = e.getLong("day"), at = e.getLong("at"), kind = e.optString("kind", "add"),
                words = e.optInt("words"), minutes = e.optInt("minutes"), chapter = e.optBoolean("chapter"))) } }
        j.optJSONArray("caught")?.let { a -> for (i in 0 until a.length()) { val c = a.getJSONObject(i); dao.insert(Caught(kind = c.getInt("kind"), at = c.getLong("at"))) } }
        val s = Settings.of(j.getJSONObject("settings"))
        store.replace(s.copy(book = ids[s.book] ?: ids.values.firstOrNull() ?: 0))
        j.optJSONArray("mug")?.let { a -> Mug.save(ctx, IntArray(a.length()) { a.getInt(it) }) }
        return true
    }
}

/** The user's own mug print: 28×33 posterized pixels kept in a small file. */
object Mug {
    private fun file(ctx: Context) = java.io.File(ctx.filesDir, "mug.bin")
    fun save(ctx: Context, px: IntArray) { val bb = java.nio.ByteBuffer.allocate(px.size * 4); px.forEach { bb.putInt(it) }; file(ctx).writeBytes(bb.array()) }
    fun load(ctx: Context): IntArray? { val f = file(ctx); if (!f.exists()) return null
        val bb = java.nio.ByteBuffer.wrap(f.readBytes()); return IntArray(bb.capacity() / 4) { bb.getInt() }.takeIf { it.size == 28 * 33 } }
}
