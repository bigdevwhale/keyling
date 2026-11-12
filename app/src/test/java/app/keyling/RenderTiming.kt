package app.keyling

import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import app.keyling.ui.art.Board
import app.keyling.ui.art.CapSets
import app.keyling.ui.art.Decor
import app.keyling.ui.art.Desk
import app.keyling.ui.art.Glyphs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** `-Prender`: how long the desk's pieces take to build, to see what keeps the desk black at first. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderTiming {
    @Test fun timing() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Glyphs.face = ResourcesCompat.getFont(ctx, R.font.press_start); Glyphs.smoothFace = ResourcesCompat.getFont(ctx, R.font.inter_semibold)
        val decor = Decor("oak", "heart", "cactus", "banker", "candle"); val set = CapSets.of("retro")
        fun t(name: String, f: () -> Unit) { val t0 = System.nanoTime(); f(); println("TIMING $name ${(System.nanoTime() - t0) / 1_000_000} ms") }
        repeat(2) { round ->
            println("TIMING round $round")
            t("background") { Desk.background(decor, set, null) }
            t("surface") { Desk.surface(app.keyling.ui.art.Buf(Desk.W, Desk.H, Desk.S), "oak") }
            t("glyphs") { for (l in app.keyling.ui.art.LETTERS) Glyphs.text(l + round, -1, 7f, Desk.S, smooth = true) }
            t("caps") { Board.buildCaps(set, 200 + round * 5, Desk.S) }
            t("sprites") { Desk.sprites(decor.copy(mug = if (round == 0) "heart" else "glass"), null) }
        }
    }
}
