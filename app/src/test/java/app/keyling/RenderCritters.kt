package app.keyling

import app.keyling.ui.art.Buf
import app.keyling.ui.art.Critters
import org.junit.Test
import java.io.File

/** `-Prender`: draws every resident big on one sheet (app/build/art/critters.png), to look at the art. */
class RenderCritters {
    @Test fun sheet() {
        val ids = listOf("space", "enter", "bksp", "caps", "tab", "shift", "esc")
        val cell = 64; val b = Buf(cell * ids.size, 60)
        b.rect(0, 0, b.w, b.h, 0x2a1a1c or Buf.OPAQUE)
        ids.forEachIndexed { i, id -> Critters.draw(b, id, i * cell + cell / 2f, 54f, 1f, 1.3f, false, false) }
        val out = File(System.getProperty("render.out") ?: "build/art").apply { mkdirs() }
        // plain PPM: no image libraries on the unit-test classpath
        File(out, "critters.ppm").outputStream().use { o ->
            o.write("P6 ${b.w} ${b.h} 255\n".toByteArray())
            for (c in b.d) { o.write((c shr 16) and 255); o.write((c shr 8) and 255); o.write(c and 255) } }
    }
}
