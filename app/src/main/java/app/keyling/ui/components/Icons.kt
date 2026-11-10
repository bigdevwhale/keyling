package app.keyling.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Line icons on a 24 grid, drawn from path data (stroke 1.8, round caps). */
object Icons {
    val paths = mapOf(
        "desk" to listOf("M4.5 7h15a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-15a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z", "M6 10.5h1M9.5 10.5h1M13 10.5h1M16.5 10.5h1M8 14.5h8"),
        "paw" to listOf("M5 14a7 6 0 0 0 14 0c0-3.5-3-6.5-7-6.5S5 10.5 5 14z", "M7.5 9 6 5l3.5 2.2M16.5 9 18 5l-3.5 2.2"),
        "chart" to listOf("M4 20h16", "M7 16v-5M12 16V6M17 16v-8"),
        "shop" to listOf("M5 10h14l-1.5 8h-11z", "M8 10l1-4h6l1 4M12 6V3"),
        "clock" to listOf("M12 5a8 8 0 1 1 0 16a8 8 0 1 1 0-16z", "M12 9v4l2.5 2.5M10 2h4"),
        "pen" to listOf("M4 20h4L19 9l-4-4L4 16v4z", "M13.5 6.5l4 4"),
        "gear" to listOf("M12 9a3 3 0 1 1 0 6a3 3 0 1 1 0-6z", "M12 2v3M12 19v3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M2 12h3M19 12h3M4.9 19.1L7 17M17 7l2.1-2.1"),
        "fire" to listOf("M12 2.5c.5 3.5 5.5 5.5 5.5 11a5.5 5.5 0 0 1-11 0c0-2.8 1.6-4.6 2.8-5.6.2 2 1 3.1 2.2 3.4-.4-3.4-.2-6.2.5-8.8z"),
        "cal" to listOf("M6 5h12a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M4 10h16M9 3v4M15 3v4"),
        "book" to listOf("M5 4h10a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3z", "M5 17a3 3 0 0 1 3-3h10"),
        "cut" to listOf("M6 4.5a2.5 2.5 0 1 1 0 5a2.5 2.5 0 1 1 0-5z", "M6 14.5a2.5 2.5 0 1 1 0 5a2.5 2.5 0 1 1 0-5z", "M8 8.5 20 17M8 15.5 20 7"),
        "bug" to listOf("M12 7a4 4 0 0 1 4 4v4a4 4 0 0 1-8 0v-4a4 4 0 0 1 4-4z", "M12 7V4M4 11h4M16 11h4M4 16h4M16 16h4"),
        "moon" to listOf("M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"),
        "sun" to listOf("M12 8a4 4 0 1 1 0 8a4 4 0 1 1 0-8z", "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"),
        "home" to listOf("M4 11l8-7 8 7v9H4z", "M10 20v-5h4v5"),
        "mug" to listOf("M5 8h11v7a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5z", "M16 10h1.5a2.5 2.5 0 0 1 0 5H16"),
        "key" to listOf("M5 7h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z", "M7 11h1M11 11h2M16 11h1M8 14.5h8"),
        "star" to listOf("M12 3l2.6 5.6 6 .7-4.5 4.1 1.2 6-5.3-3-5.3 3 1.2-6L3.4 9.3l6-.7z"),
        "q" to listOf("M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6V14M12 17.5v.5"),
        "rest" to listOf("M4 18v-6a4 4 0 0 1 4-4h8a4 4 0 0 1 4 4v6M4 14h16M4 18v2M20 18v2"),
        "plant" to listOf("M12 21V11", "M12 11C12 6 8 4 4 4c0 4 3 7 8 7zM12 13c0-4 3-6 8-6 0 4-3 6-8 6z"),
        "lamp" to listOf("M8 3h8l3 7H5z", "M12 10v8M8 21h8"),
        "sparkle" to listOf("M12 3l2.2 6.3L20.5 12l-6.3 2.2L12 20.5l-2.2-6.3L3.5 12l6.3-2.7z"),
        "surface" to listOf("M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M3 10h18M3 15h18"),
        "play" to listOf("M8 5v14l11-7z"),
        "upload" to listOf("M12 16V4M7 9l5-5 5 5M5 20h14"),
        "check" to listOf("M5 12.5l4.5 4.5L19 7"),
        "lock" to listOf("M7 11h10a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1z", "M8.5 11V8a3.5 3.5 0 0 1 7 0v3"),
        "back" to listOf("M15 5l-7 7 7 7"),
        "plus" to listOf("M12 5v14M5 12h14"),
    )

    private val cache = HashMap<String, ImageVector>()
    fun vector(name: String): ImageVector = cache.getOrPut(name) {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (d in paths[name] ?: paths.getValue("q"))
            b.addPath(PathParser().parsePathString(d).toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        b.build()
    }
}

@Composable
fun Icon(name: String, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val v = remember(name) { Icons.vector(name) }
    Image(v, null, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}
