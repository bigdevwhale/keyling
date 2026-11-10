package app.keyling.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.keyling.container
import app.keyling.ui.art.Buf
import app.keyling.ui.theme.K
import app.keyling.ui.theme.T
import kotlin.math.roundToInt

/** The switch the user picked, so every keycap in the interface clicks like the real board. */
object Click { var sw = "brown" }

/**
 * A keycap-shaped button: a top face and a darker skirt under it; pressing sinks the cap.
 * [accent] uses the ink-blue cap. Clicking plays the chosen switch.
 */
@Composable
fun Keycap(modifier: Modifier = Modifier, accent: Boolean = false, down: Boolean = false, key: String? = null, radius: Dp = 12.dp,
           onClick: () -> Unit, content: @Composable () -> Unit) {
    val p = K.pal; val ctx = LocalContext.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sink = if (pressed || down) 3.dp else 0.dp
    val top = if (accent) p.xTop else p.kTop; val hi = if (accent) p.xHi else p.kHi; val side = if (accent) p.xSide else p.kSide; val o = if (accent) p.xO else p.kO
    val skirt = if (pressed || down) 1.dp else 4.dp
    Box(modifier.padding(bottom = 4.dp)) {
        Box(Modifier.matchParentSize().offset(y = 4.dp).clip(RoundedCornerShape(radius)).background(o))
        Box(Modifier.matchParentSize().offset(y = sink + skirt - 1.dp).clip(RoundedCornerShape(radius)).background(side))
        Box(Modifier.fillMaxSize().offset(y = sink).clip(RoundedCornerShape(radius)).background(Brush.verticalGradient(0f to hi, .35f to top, 1f to top))
            .border(1.dp, o.copy(alpha = .55f), RoundedCornerShape(radius))
            .clickable(src, null) { ctx.container.sound.click(Click.sw, key); onClick() }, contentAlignment = Alignment.Center) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides if (accent) p.xLeg else p.kLeg) { content() }
        }
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, pad: Dp = 16.dp, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val p = K.pal; val ctx = LocalContext.current
    Box(modifier.shadow(if (p.dark) 0.dp else 6.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
        .clip(RoundedCornerShape(18.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(18.dp))
        .then(if (onClick != null) Modifier.clickable { ctx.container.sound.click(Click.sw); onClick() } else Modifier).padding(pad)) { content() }
}

@Composable
fun Sec(text: String, modifier: Modifier = Modifier) = Text(text, style = T.sec, color = K.pal.mute, modifier = modifier.padding(start = 4.dp, top = 24.dp, bottom = 10.dp))

/** A segmented switch. */
@Composable
fun Tabs(items: List<String>, sel: Int, modifier: Modifier = Modifier, onSel: (Int) -> Unit) {
    val p = K.pal; val ctx = LocalContext.current
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.track).padding(4.dp)) {
        items.forEachIndexed { i, s ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (i == sel) p.surface else Color.Transparent)
                .clickable { ctx.container.sound.click(Click.sw); onSel(i) }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                Text(s, style = if (i == sel) T.smallB.copy(fontSize = T.body.fontSize) else T.body, color = if (i == sel) p.ink else p.mute, maxLines = 1)
            }
        }
    }
}

@Composable
fun Bar(frac: Float, modifier: Modifier = Modifier, color: Color = K.pal.acc, height: Dp = 6.dp) {
    val p = K.pal
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(p.track)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(height)).background(color))
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, style: TextStyle = T.smallB) =
    Text(text, style = style, color = fg, modifier = modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp))

/** Shows a pixel buffer with hard pixels, filling the width. */
@Composable
fun Pixels(buf: Buf, modifier: Modifier = Modifier, version: Any? = null) {
    val bmp = remember(buf, version) { buf.toBitmap().asImageBitmap() }
    Canvas(modifier.fillMaxWidth().aspectRatio(buf.w.toFloat() / buf.h)) {
        drawImage(bmp, IntOffset.Zero, IntSize(buf.w, buf.h), IntOffset.Zero, IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.None)
    }
}

/** Shows a bitmap that is redrawn in place every frame (the caller bumps [tick]). */
@Composable
fun LivePixels(bmp: Bitmap, tick: Int, modifier: Modifier = Modifier) {
    val img = remember(bmp) { bmp.asImageBitmap() }
    Canvas(modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height)) {
        @Suppress("UNUSED_EXPRESSION") tick
        drawImage(img, IntOffset.Zero, IntSize(bmp.width, bmp.height), IntOffset.Zero, IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.None)
    }
}

/** Simple vertical bars with labels under them; [hi] marks bars drawn in the accent, the rest are dim. */
@Composable
fun Bars(values: List<Int>, labels: List<String>, hi: (Int) -> Boolean, modifier: Modifier = Modifier, height: Dp = 80.dp, top: ((Int) -> String)? = null) {
    val p = K.pal; val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(height + if (top != null) 34.dp else 18.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                top?.let { Text(it(i), style = T.tiny, color = p.mute, maxLines = 1) }
                Box(Modifier.fillMaxWidth().height(height * (v.toFloat() / max)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (hi(i)) p.acc else p.dim))
                Text(labels.getOrElse(i) { "" }, style = T.tiny, color = p.mute, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/** A medal: a coloured disc with a line icon; an unearned one is grey with a progress ring. */
@Composable
fun Medal(icon: String, tier: Char, done: Boolean, progress: Float, size: Dp = 58.dp) {
    val p = K.pal
    val (c1, c2) = when (tier) { 'g' -> Color(0xFFF0C24E) to Color(0xFFC9952A); 's' -> Color(0xFFB9C3D0) to Color(0xFF8693A4); else -> Color(0xFFD0905A) to Color(0xFFA8693A) }
    Box(Modifier.drawBehind {
        if (done) drawCircle(Brush.verticalGradient(listOf(c1, c2)))
        else { drawCircle(p.track); drawArc(p.warm, -90f, 360f * progress.coerceIn(0f, 1f), true) }
        drawCircle(if (done) Brush.verticalGradient(listOf(c1, c2)) else Brush.verticalGradient(listOf(p.surface2, p.surface2)), radius = this.size.minDimension / 2 - 4.dp.toPx())
    }.then(Modifier.size(size)), contentAlignment = Alignment.Center) {
        Icon(icon, if (done) Color.White else p.mute, 22.dp)
    }
}

@Composable
fun Row2(left: String, right: String, last: Boolean = false) {
    val p = K.pal
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Text(left, style = T.body, color = p.mute, modifier = Modifier.weight(1f)); Text(right, style = T.body, color = p.ink)
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
    }
}

@Composable fun Gap(h: Dp) = Spacer(Modifier.height(h))
@Composable fun RowScope.Fill() = Spacer(Modifier.weight(1f))
