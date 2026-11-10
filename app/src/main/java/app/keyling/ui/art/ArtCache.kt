package app.keyling.ui.art

/** Built desk backgrounds and keycap sets, kept between screens so the desk shows at once. */
class ArtCache {
    private val bgs = LinkedHashMap<String, Buf>()
    private val caps = LinkedHashMap<String, Map<Key, Board.Caps>>()
    private val sprites = LinkedHashMap<String, List<Desk.Sprite>>()

    fun spritesKey(decor: Decor, custom: IntArray?) = "$decor|${custom?.contentHashCode()}"
    @Synchronized fun peekSprites(k: String) = sprites[k]
    fun sprites(decor: Decor, custom: IntArray?): List<Desk.Sprite> {
        val k = spritesKey(decor, custom); peekSprites(k)?.let { return it }
        val l = Desk.sprites(decor, custom)
        synchronized(this) { sprites[k] = l; while (sprites.size > 8) sprites.remove(sprites.keys.first()) }
        return l
    }

    fun bgKey(decor: Decor, set: CapSet, custom: IntArray?) = "${decor.desk}|${set.id}"
    fun capsKey(set: CapSet, wear: Int) = "${set.id}|${wear / 5}"

    @Synchronized fun peekBg(k: String) = bgs[k]
    @Synchronized fun peekCaps(k: String) = caps[k]

    fun bg(decor: Decor, set: CapSet, custom: IntArray?): Buf {
        val k = bgKey(decor, set, custom); peekBg(k)?.let { return it }
        val b = Desk.background(decor, set, custom)
        synchronized(this) { bgs[k] = b; while (bgs.size > 8) bgs.remove(bgs.keys.first()) }
        return b
    }

    fun caps(set: CapSet, wear: Int): Map<Key, Board.Caps> {
        val k = capsKey(set, wear); peekCaps(k)?.let { return it }
        val m = Board.buildCaps(set, wear / 5 * 5)
        synchronized(this) { caps[k] = m; while (caps.size > 6) caps.remove(caps.keys.first()) }
        return m
    }
}
