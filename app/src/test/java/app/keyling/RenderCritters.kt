package app.keyling

import app.keyling.ui.art.Buf
import app.keyling.ui.art.Critters
import org.junit.Test
import java.io.File

/** `-Prender`: draws every resident at every stage on one sheet (app/build/art/critters.ppm), to look at the art. */
class RenderCritters {
    @Test fun sheet() {
        val ids = listOf("space", "enter", "bksp", "caps", "tab", "shift", "esc")
        val cw = 68; val ch = 54; val b = Buf(cw * 4, ch * ids.size)
        b.rect(0, 0, b.w, b.h, 0x2a1a1c or Buf.OPAQUE)
        ids.forEachIndexed { i, id -> for (st in 0..3) Critters.draw(b, id, st, st * cw + cw / 2f, i * ch + ch - 3f, Critters.BIG, 0f, false, false) }
        val out = File(System.getProperty("render.out") ?: "build/art").apply { mkdirs() }
        // plain PPM: no image libraries on the unit-test classpath
        File(out, "critters.ppm").outputStream().use { o ->
            o.write("P6 ${b.w} ${b.h} 255\n".toByteArray())
            for (c in b.d) { o.write((c shr 16) and 255); o.write((c shr 8) and 255); o.write(c and 255) } }
    }
}
