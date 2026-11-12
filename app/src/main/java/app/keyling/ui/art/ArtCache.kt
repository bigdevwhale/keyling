package app.keyling.ui.art

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer

/**
 * Built desk backgrounds, keycap sets and desk things, kept between screens so the desk shows at once.
 *
 * A piece asked for while it's being built waits for that build instead of starting a second one (the app
 * starts building the desk at launch, and the desk screen often asks before that's done). Backgrounds and
 * keycaps are also kept on disk in [dir], so after the first launch the desk is read back rather than drawn
 * again; [stamp] (when the app was installed) names them, so a new version never shows an old drawing.
 */
class ArtCache(private val dir: File? = null, private val stamp: Long = 0) {
    private val bgs = LinkedHashMap<String, Buf>()
    private val caps = LinkedHashMap<String, Map<Key, Board.Caps>>()
    private val sprites = LinkedHashMap<String, List<Desk.Sprite>>()
    private val building = HashMap<String, Any>()

    init {
        // drawings from an earlier install
        dir?.listFiles()?.forEach { if (!it.name.startsWith("$stamp-")) it.delete() }
    }

    /** A way to keep a piece on disk as a list of buffers. */
    private class Disk<T>(val out: (T) -> List<Buf>, val back: (List<Buf>) -> T?)

    /** The piece kept under [k] in [into], or read from disk, or [make] it once, whoever asks, and keep it (at most [keep] of them). */
    private fun <T : Any> once(k: String, into: LinkedHashMap<String, T>, keep: Int, disk: Disk<T>?, make: () -> T): T {
        synchronized(this) { into[k] }?.let { return it }
        val lock = synchronized(this) { building.getOrPut(k) { Any() } }
        synchronized(lock) {
            synchronized(this) { into[k] }?.let { return it }
            val f = if (disk != null && dir != null) File(dir, "$stamp-" + k.replace(Regex("[^A-Za-z0-9]"), "_")) else null
            val v = f?.let { runCatching { read(it)?.let(disk!!.back) }.getOrNull() }
                ?: make().also { v -> if (f != null) runCatching { write(f, disk!!.out(v)) } }
            synchronized(this) { into[k] = v; while (into.size > keep) into.remove(into.keys.first()); building.remove(k) }
            return v
        }
    }

    fun spritesKey(decor: Decor, custom: IntArray?) = "$decor|${custom?.contentHashCode()}"
    @Synchronized fun peekSprites(k: String) = sprites[k]
    fun sprites(decor: Decor, custom: IntArray?): List<Desk.Sprite> = once(spritesKey(decor, custom), sprites, 8, SPRITES) { Desk.sprites(decor, custom) }

    /** Desk things on disk: their pictures, then one more buffer holding each one's slot, place and hot spot. */
    private val SPRITES = Disk<List<Desk.Sprite>>({ l ->
        val meta = Buf(5 * l.size, 1)
        l.forEachIndexed { i, sp -> val h = sp.hot
            intArrayOf(SLOTS.indexOf(sp.id), sp.x, sp.y, h?.let { (it.first * 1000).toInt() } ?: -1, h?.let { (it.second * 1000).toInt() } ?: -1).copyInto(meta.d, i * 5) }
        l.map { it.buf } + meta
    }, { bufs ->
        val meta = bufs.lastOrNull()?.takeIf { it.h == 1 && it.w == 5 * (bufs.size - 1) } ?: return@Disk null
        List(bufs.size - 1) { i -> val m = meta.d.copyOfRange(i * 5, i * 5 + 5)
            Desk.Sprite(SLOTS.getOrNull(m[0]) ?: return@Disk null, bufs[i], m[1], m[2], if (m[3] < 0) null else m[3] / 1000f to m[4] / 1000f) }
    })
    private val SLOTS = listOf("notebook", "notes", "eraser", "lamp", "plant", "mug", "mascot")

    fun bgKey(decor: Decor, set: CapSet, custom: IntArray?) = "${decor.desk}|${set.id}"
    @Synchronized fun peekBg(k: String) = bgs[k]
    fun bg(decor: Decor, set: CapSet, custom: IntArray?): Buf =
        once(bgKey(decor, set, custom), bgs, 8, Disk({ listOf(it) }, { it.singleOrNull() })) { Desk.background(decor, set, custom) }

    fun capsKey(set: CapSet, wear: Int, s: Int = Desk.S) = "${set.id}|${wear / 5}|$s"
    @Synchronized fun peekCaps(k: String) = caps[k]
    /** Any keycaps of [set] already built at fineness [s], whatever their wear: to show while the right ones are drawn. */
    @Synchronized fun peekAnyCaps(set: CapSet, s: Int = Desk.S) = caps.entries.lastOrNull { it.key.startsWith("${set.id}|") && it.key.endsWith("|$s") }?.value
    /** Every keycap of [set], drawn at [s] device pixels per art pixel: the desk's fineness, or 1 for the residents' map. */
    fun caps(set: CapSet, wear: Int, s: Int = Desk.S): Map<Key, Board.Caps> =
        once(capsKey(set, wear, s), caps, 6, Disk({ m -> Board.keys.flatMap { listOf(m.getValue(it).up, m.getValue(it).dn) } },
            { l -> if (l.size != Board.keys.size * 2) null else Board.keys.withIndex().associate { (i, k) -> k to Board.Caps(l[i * 2], l[i * 2 + 1]) } })) {
            Board.buildCaps(set, wear / 5 * 5, s)
        }

    // ---- on disk: a count, then for each buffer w, h, s and its pixels ----

    private fun write(f: File, bufs: List<Buf>) {
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        DataOutputStream(tmp.outputStream().buffered(1 shl 16)).use { o ->
            o.writeInt(bufs.size)
            for (b in bufs) {
                o.writeInt(b.w); o.writeInt(b.h); o.writeInt(b.s)
                val bb = ByteBuffer.allocate(b.d.size * 4); bb.asIntBuffer().put(b.d); o.write(bb.array())
            }
        }
        tmp.renameTo(f)
    }

    private fun read(f: File): List<Buf>? {
        if (!f.exists()) return null
        DataInputStream(f.inputStream().buffered(1 shl 16)).use { i ->
            return List(i.readInt()) {
                val b = Buf(i.readInt(), i.readInt(), i.readInt())
                val bytes = ByteArray(b.d.size * 4); i.readFully(bytes); ByteBuffer.wrap(bytes).asIntBuffer().get(b.d)
                b
            }
        }
    }
}
