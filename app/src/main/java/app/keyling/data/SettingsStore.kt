package app.keyling.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** Everything that isn't a book or a sitting: looks, the shop, the wallet, goals. */
data class Settings(
    val theme: String = "system",        // system · light · dark
    val unit: String = "w",              // w = words, c = characters with spaces
    val norm: Int = 800,                 // words a day
    val yearGoal: Int = 250000,
    val book: Long = 0,                  // the book on the desk
    val pet: String = "space",           // the resident in the nook
    val sw: String = "brown",            // switch sound
    val sound: Boolean = true,
    val caps: String = "retro",          // keycap set
    val desk: String = "oak", val mug: String = "heart", val plant: String = "cactus", val lamp: String = "none", val mascot: String = "none",
    val coins: Int = 40,
    val owned: Set<String> = emptySet(), // "mug:glass", "caps:night", "sw:red" …
    val claimed: Set<String> = emptySet(),// achievements already paid out
    val pets: Int = 0,                   // times the pet was stroked
    val bestSprint: Int = 0,             // words per minute ×10 of the best sprint
    val ghostWins: Int = 0,
    val sprints: Int = 0,
    val onboarded: Boolean = false,
    val pos: Map<String, Pair<Int, Int>> = emptyMap(), // where desk things were moved: offset from their usual place
    val movedHint: Boolean = false,
    // a running sprint, kept here so it outlives a tab switch or the app being closed while you write elsewhere
    val sprintEnds: Long = 0,            // when it ends, epoch ms; 0 = no sprint
    val sprintMin: Int = 0,
    val sprintGhost: Int = 0,            // the ghost's words per minute ×10
) {
    fun json(): JSONObject = JSONObject().apply {
        put("theme", theme); put("unit", unit); put("norm", norm); put("yearGoal", yearGoal); put("book", book); put("pet", pet)
        put("sw", sw); put("sound", sound); put("caps", caps); put("desk", desk); put("mug", mug); put("plant", plant); put("lamp", lamp); put("mascot", mascot)
        put("coins", coins); put("owned", JSONArray(owned.toList())); put("claimed", JSONArray(claimed.toList())); put("pets", pets)
        put("bestSprint", bestSprint); put("ghostWins", ghostWins); put("sprints", sprints); put("onboarded", onboarded); put("movedHint", movedHint)
        put("sprintEnds", sprintEnds); put("sprintMin", sprintMin); put("sprintGhost", sprintGhost)
        put("pos", JSONObject().apply { pos.forEach { (k, v) -> put(k, JSONArray(listOf(v.first, v.second))) } })
    }

    companion object {
        fun of(j: JSONObject): Settings {
            fun set(k: String) = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
            val d = Settings()
            return Settings(j.optString("theme", d.theme), j.optString("unit", d.unit), j.optInt("norm", d.norm), j.optInt("yearGoal", d.yearGoal),
                j.optLong("book", d.book), j.optString("pet", d.pet), j.optString("sw", d.sw), j.optBoolean("sound", d.sound), j.optString("caps", d.caps),
                j.optString("desk", d.desk), j.optString("mug", d.mug), j.optString("plant", d.plant), j.optString("lamp", d.lamp), j.optString("mascot", d.mascot),
                j.optInt("coins", d.coins), set("owned"), set("claimed"), j.optInt("pets"), j.optInt("bestSprint"), j.optInt("ghostWins"), j.optInt("sprints"),
                j.optBoolean("onboarded"),
                j.optJSONObject("pos")?.let { o -> o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { it.getInt(0) to it.getInt(1) } } } ?: emptyMap(),
                j.optBoolean("movedHint"), j.optLong("sprintEnds"), j.optInt("sprintMin"), j.optInt("sprintGhost"))
        }
    }
}

private val Context.store by preferencesDataStore("keyling")
private val KEY = stringPreferencesKey("settings")

class SettingsStore(private val ctx: Context) {
    val settings: Flow<Settings> = ctx.store.data.map { p -> p[KEY]?.let { runCatching { Settings.of(JSONObject(it)) }.getOrNull() } ?: Settings() }
    suspend fun now(): Settings = settings.first()
    suspend fun update(f: (Settings) -> Settings) { ctx.store.edit { p ->
        val cur = p[KEY]?.let { runCatching { Settings.of(JSONObject(it)) }.getOrNull() } ?: Settings()
        p[KEY] = f(cur).json().toString() } }
    suspend fun replace(s: Settings) = update { s }
}
