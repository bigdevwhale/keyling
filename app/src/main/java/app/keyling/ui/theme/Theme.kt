package app.keyling.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.keyling.R

/** The design tokens: neutral pages, one ink-blue accent, amber only for буквицы, green for things done. */
class Pal(
    val dark: Boolean,
    val bg: Color, val surface: Color, val surface2: Color, val ink: Color, val mute: Color, val line: Color, val track: Color, val dim: Color,
    val acc: Color, val accInk: Color, val accSoft: Color, val warm: Color, val warmSoft: Color, val ok: Color, val bad: Color,
    val heat: List<Color>,
    val kTop: Color, val kHi: Color, val kSide: Color, val kO: Color, val kLeg: Color,
    val xTop: Color, val xHi: Color, val xSide: Color, val xO: Color, val xLeg: Color,
    val case: Color, val caseHi: Color, val caseLo: Color,
)

private fun c(hex: Long) = Color(0xFF000000 or hex)

val Light = Pal(false,
    c(0xF6F4EF), c(0xFFFFFF), c(0xF2EFE9), c(0x1E1C19), c(0x7C766E), c(0xEAE5DD), c(0xECE8E1), c(0xE1DCD3),
    c(0x3557C9), c(0xFFFFFF), c(0xE8EDFB), c(0xD98A1E), c(0xFBF0DE), c(0x2F9363), c(0xD2493F),
    listOf(c(0xECE8E1), c(0xCAD5F5), c(0x95ABEC), c(0x5B7CDD), c(0x3557C9)),
    c(0xFFFFFF), c(0xFFFFFF), c(0xDAD5CC), c(0xC9C3B9), c(0x3A3631),
    c(0x3557C9), c(0x4D6EDC), c(0x22398F), c(0x1A2C70), c(0xFFFFFF),
    c(0xECE8E1), c(0xF9F7F3), c(0xDAD4CA))

val Dark = Pal(true,
    c(0x121419), c(0x1B1E25), c(0x242832), c(0xECEAE6), c(0x8C93A1), c(0x2A2F39), c(0x262B35), c(0x2F3541),
    c(0x7E9BFF), c(0x0E1530), c(0x222B47), c(0xF2B45A), c(0x33291A), c(0x5CC08A), c(0xF06A5E),
    listOf(c(0x232831), c(0x2A3866), c(0x3D55A6), c(0x5D7DE6), c(0xA2B6FF)),
    c(0x2B2F39), c(0x383D49), c(0x16181E), c(0x0C0E12), c(0xD7DAE1),
    c(0x6383EC), c(0x7D98F2), c(0x3449A0), c(0x1F2D68), c(0xFFFFFF),
    c(0x1A1D24), c(0x272B34), c(0x0F1115))

val LocalPal = staticCompositionLocalOf { Light }

object K {
    val pal: Pal @Composable get() = LocalPal.current
}

val Lora = FontFamily(Font(R.font.lora_medium, FontWeight.Medium), Font(R.font.lora_semibold, FontWeight.SemiBold), Font(R.font.lora_italic, FontWeight.Medium, FontStyle.Italic))
val Inter = FontFamily(Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium), Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_semibold, FontWeight.Bold))

object T {
    val title = TextStyle(fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp)
    val h2 = TextStyle(fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 26.sp)
    val bookTitle = TextStyle(fontFamily = Lora, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 23.sp)
    val big = TextStyle(fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 30.sp)
    val body = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp)
    val bodyB = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
    val small = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp)
    val smallB = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
    val tiny = TextStyle(fontFamily = Inter, fontSize = 10.5.sp, lineHeight = 13.sp)
    val sec = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    val num = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    val btn = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
}

@Composable
fun KeylingTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) Dark else Light
    val scheme = if (dark) darkColorScheme(primary = p.acc, background = p.bg, surface = p.surface, onSurface = p.ink, onBackground = p.ink)
    else lightColorScheme(primary = p.acc, background = p.bg, surface = p.surface, onSurface = p.ink, onBackground = p.ink)
    CompositionLocalProvider(LocalPal provides p) { MaterialTheme(colorScheme = scheme, content = content) }
}
