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
        // the dark slot under the cap, with a warm glow
        b.rect(11, 30, 32, 13, rgb(0x1c100c)); b.rect(11, 30, 32, 1, rgb(0x4a3a30))
        for (y in 31 until 43) for (x in 12 until 42) { val d = kotlin.math.hypot((x - 27f) / 16f, (y - 44f) / 12f); if (d < 1) b.p(x, y, rgb(0x6e401a), (1 - d) * .8f) }
        val pet = Buf(54, 54); Critters.draw(pet, "space", 27f, 47f, .78f, 1.3f, false, false)
        b.blit(pet, 0, 0, clipY = 42)
        b.rect(10, 41, 34, 2, rgb(0x3a3940)); b.rect(10, 43, 34, 1, rgb(0x1a1a20))
        // the lifted keycap above
        b.rect(12, 22, 30, 2, 0, .35f); Board.drawCap(b, 11, 5, 32, 17, CapPal(set.a, set.la), "", 0f)
        return b
    }

    private fun write(b: Buf, name: String) {
        val out = File(System.getProperty("render.out") ?: "build/art").apply { mkdirs() }
        File(out, name).outputStream().use { o -> o.write("${b.w} ${b.h}\n".toByteArray())
            for (c in b.d) { o.write((c shr 16) and 255); o.write((c shr 8) and 255); o.write(c and 255); o.write((c ushr 24) and 255) } }
    }

    @Test fun icon_fg() = write(icon(), "icon_fg.rgba")
}
