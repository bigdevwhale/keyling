package app.keyling

import app.keyling.ui.art.Board
import app.keyling.ui.art.Buf
import app.keyling.ui.art.CapPal
import app.keyling.ui.art.CapSets
import app.keyling.ui.art.Critters
import app.keyling.ui.art.rgb
import org.junit.Test
import java.io.File

/** `-Prender`: the launcher icon's foreground (54×54 art, transparent) as raw RGBA, plus the store logo on its background. */
class RenderIcon {
    private fun icon(): Buf {
        val b = Buf(54, 54); val set = CapSets.of("retro")
        val beige = CapPal(set.a, set.la); val grey = CapPal(set.m, set.lm); val red = CapPal(set.x, set.lx)
        // a 2×2 block of keys; the bottom-right one is lifted and the dormouse peeks out from under it
        Board.drawCap(b, 10, 8, 16, 15, red, "", 0f)
        Board.drawCap(b, 28, 8, 16, 15, beige, "", 0f)
        Board.drawCap(b, 10, 30, 16, 15, grey, "", 0f)
        b.rect(28, 30, 16, 15, rgb(0x1c100c))
        for (y in 31 until 45) for (x in 28 until 44) { val d = kotlin.math.hypot((x - 36f) / 9f, (y - 46f) / 11f); if (d < 1) b.p(x, y, rgb(0x8e5622), (1 - d) * .9f) }
        val pet = Buf(54, 54); Critters.draw(pet, "space", 36f, 50f, .44f, 1.3f, false, false)
        b.blit(pet, 0, 0, clipY = 44)
        b.rect(27, 44, 18, 1, rgb(0x3a3940))
        b.rect(29, 29, 14, 2, 0, .35f)
        Board.drawCap(b, 28, 25, 16, 10, beige, "", 0f)
        return b
    }

    private fun write(b: Buf, name: String) {
        val out = File(System.getProperty("render.out") ?: "build/art").apply { mkdirs() }
        File(out, name).outputStream().use { o -> o.write("${b.w} ${b.h}\n".toByteArray())
            for (c in b.d) { o.write((c shr 16) and 255); o.write((c shr 8) and 255); o.write(c and 255); o.write((c ushr 24) and 255) } }
    }

    @Test fun icon_fg() = write(icon(), "icon_fg.rgba")
}
