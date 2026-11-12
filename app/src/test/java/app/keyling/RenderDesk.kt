package app.keyling

import android.graphics.Bitmap
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import app.keyling.ui.art.Board
import app.keyling.ui.art.Buf
import app.keyling.ui.art.CapSets
import app.keyling.ui.art.Decor
import app.keyling.ui.art.Desk
import app.keyling.ui.art.Glyphs
import app.keyling.ui.art.Scene
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** `-Prender`: draws the desk with every set of things, and the shop's cards, to app/build/art/desk-*.png, to look at them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderDesk {
    private val out = File(System.getProperty("render.out") ?: "build/art").apply { mkdirs() }
    private fun save(b: Buf, name: String) = File(out, "$name.png").outputStream().use { b.toBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }

    @Test fun desks() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Glyphs.face = ResourcesCompat.getFont(ctx, R.font.press_start); Glyphs.smoothFace = ResourcesCompat.getFont(ctx, R.font.inter_semibold)
        // a photo for the plain mug: stripes
        val print = IntArray(28 * 33) { i -> if ((i % 28 + i / 28) / 4 % 2 == 0) 0xFF2f5d73.toInt() else 0xFFe8a33d.toInt() }
        val looks = listOf(
            Decor("oak", "heart", "cactus", "banker", "candle") to "retro",
            Decor("walnut", "glass", "monstera", "none", "cat") to "night",
            Decor("white", "enamel", "succulent", "banker", "globe") to "sea",
            Decor("baize", "custom", "cactus", "none", "none") to "walnut",
            Decor("oak", "thermo", "succulent", "none", "cat") to "pastel",
        )
        looks.forEachIndexed { i, (decor, caps) ->
            val set = CapSets.of(caps)
            val bg = Desk.background(decor, set, print); val frame = Buf(Desk.W, Desk.H, Desk.S)
            val sc = Scene().apply { sprites = Desk.sprites(decor, print); steam = decor.mug != "thermo"; candle = decor.mascot == "candle"; pet = listOf("space", "enter", "caps", "tab", "esc")[i]; stage = i % 4 }
            sc.drop(10); repeat(70) { sc.step(1 / 30f) }; sc.pet(); repeat(8) { sc.step(1 / 30f) }
            sc.render(frame, bg, Board.buildCaps(set, 200, Desk.S), set.plate, 120)
            save(frame, "desk-$i")
        }
        // the shop's cards
        val cards = Buf(56 * 6, 52 * 3, Desk.S)
        listOf("mug" to listOf("heart", "enamel", "glass", "thermo", "custom"), "plant" to listOf("cactus", "succulent", "monstera"),
            "mascot" to listOf("cat", "candle", "globe", "none"), "lamp" to listOf("banker")).flatMap { (c, ids) -> ids.map { c to it } }
            .forEachIndexed { i, (c, id) -> cards.blit(Desk.itemPreview(c, id, "oak", null), i % 6 * 56, i / 6 * 52) }
        save(cards, "desk-shop")
        val sets = Buf(90, 40 * CapSets.all.size, Desk.S)
        CapSets.all.forEachIndexed { i, s -> sets.blit(Board.preview(s, Desk.S), 0, i * 40) }
        save(sets, "desk-sets")
        // the residents' screen: the map, each nook with its resident grown up, the portraits in the list
        val retro = CapSets.of("retro"); val ids = listOf("space", "enter", "bksp", "caps", "tab", "shift", "esc")
        val map = Buf(Desk.W, app.keyling.ui.art.Nook.MAP_H, Desk.S)
        app.keyling.ui.art.Nook.keyboard(map, Board.buildCaps(retro, 200, Desk.S), retro, ids.associateWith { it != "esc" }, "enter", 1f)
        save(map, "res-map")
        val nooks = Buf(app.keyling.ui.art.Nook.W * 4, app.keyling.ui.art.Nook.H * 2, Desk.S)
        ids.forEachIndexed { i, id -> val n = Buf(app.keyling.ui.art.Nook.W, app.keyling.ui.art.Nook.H, Desk.S)
            app.keyling.ui.art.Nook.render(n, id, id != "esc", retro, 1.3f, 1f, stage = i % 4); nooks.blit(n, i % 4 * app.keyling.ui.art.Nook.W, i / 4 * app.keyling.ui.art.Nook.H) }
        save(nooks, "res-nooks")
        val faces = Buf(40 * 7, 34 * 2, Desk.S)
        ids.forEachIndexed { i, id -> faces.blit(app.keyling.ui.art.portrait(id, true, 2, 40, 34, app.keyling.ui.art.Critters.SMALL), i * 40, 0)
            faces.blit(app.keyling.ui.art.portrait(id, false, 0, 40, 34, app.keyling.ui.art.Critters.SMALL), i * 40, 34) }
        save(faces, "res-faces")
    }
}
