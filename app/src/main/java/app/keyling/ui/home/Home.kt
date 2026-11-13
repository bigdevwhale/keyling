package app.keyling.ui.home

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.keyling.container
import app.keyling.data.Mug
import app.keyling.data.Settings
import app.keyling.domain.CHARS_PER_WORD
import app.keyling.domain.PAGE
import app.keyling.domain.PERSONAS
import app.keyling.domain.Residents
import app.keyling.domain.Stats
import app.keyling.domain.bugName
import app.keyling.domain.fmt
import app.keyling.domain.tr
import app.keyling.ui.Nav
import app.keyling.ui.art.Board
import app.keyling.ui.art.Buf
import app.keyling.ui.art.CapSets
import app.keyling.ui.art.Decor
import app.keyling.ui.art.Desk
import app.keyling.ui.art.Scene
import app.keyling.ui.components.Bar
import app.keyling.ui.components.Click
import app.keyling.ui.components.Icon
import app.keyling.ui.components.Keycap
import app.keyling.ui.theme.K
import app.keyling.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** A running sprint: when it ends and how fast the ghost (the best sprint so far) writes. */
class Sprint(val minutes: Int, val endsAt: Long, val ghostWpm10: Int)

@Composable
fun HomeScreen(st: Stats, s: Settings, nav: Nav) {
    val ctx = LocalContext.current; val c = ctx.container; val p = K.pal; val scope = rememberCoroutineScope()
    val scene = remember { Scene() }
    val book = st.books.firstOrNull { it.id == s.book } ?: st.books.firstOrNull()
    val bookWords = book?.let { st.bookWords(it) } ?: 0
    val set = CapSets.of(s.caps)
    val custom = remember(s.mug) { Mug.load(ctx) }
    val decor = Decor(s.desk, s.mug, s.plant, s.lamp, s.mascot)
    val bgK = c.art.bgKey(decor, set, custom); val capsK = c.art.capsKey(set, st.activeDays)
    val bg by produceState<Buf?>(c.art.peekBg(bgK), bgK) { value = withContext(Dispatchers.Default) { c.art.bg(decor, set, custom) } }
    val spK = c.art.spritesKey(decor, custom)
    val sprites by produceState(c.art.peekSprites(spK) ?: emptyList(), spK) { value = withContext(Dispatchers.Default) { c.art.sprites(decor, custom) } }
    // where things stand: saved offsets, plus the one being dragged right now
    var drag by remember { mutableStateOf<Pair<String, Pair<Int, Int>>?>(null) }
    val caps by produceState<Map<app.keyling.ui.art.Key, Board.Caps>?>(c.art.peekCaps(capsK) ?: c.art.peekAnyCaps(set), capsK) { value = withContext(Dispatchers.Default) { c.art.caps(set, st.activeDays) } }
    val pages = (bookWords * CHARS_PER_WORD / PAGE).roundToInt()
    val frame = remember { Buf(Desk.W, Desk.H, Desk.S) }
    val bmp = remember { Bitmap.createBitmap(frame.pw, frame.ph, Bitmap.Config.ARGB_8888) }
    val img = remember(bmp) { bmp.asImageBitmap() }
    var tick by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var bubble by remember { mutableStateOf<String?>(null) }
    var bubbleAt by remember { mutableLongStateOf(0L) }
    var sheet by remember { mutableStateOf<String?>(null) }   // write · edit · sprint · sprintEnd · book · log
    // the sprint lives in settings, not here: leaving the screen or the app must not lose it
    val sprint = if (s.sprintEnds > 0) Sprint(s.sprintMin, s.sprintEnds, s.sprintGhost) else null
    fun endSprint() = c.write { settings.update { it.copy(sprintEnds = 0) } }
    val persona = PERSONAS.getValue(s.pet)
    fun say(text: String) { bubble = text; bubbleAt = System.currentTimeMillis() }

    scene.sprites = sprites; scene.offsets = drag?.let { (id, o) -> s.pos + (id to o) } ?: s.pos; scene.lifted = drag?.first
    scene.stage = app.keyling.domain.Food.stageIndex(app.keyling.domain.Residents.of(s.pet).level(st))
    scene.dust = st.dusty; scene.pet = s.pet; scene.steam = s.mug != "thermo"; scene.candle = s.mascot == "candle"
    val stNow by androidx.compose.runtime.rememberUpdatedState(st)
    val sprintNow by androidx.compose.runtime.rememberUpdatedState(sprint)
    // the greeting waits a moment; the desk itself is drawn from the first frame
    LaunchedEffect(Unit) {
        scene.drop(8)
        launch { delay(1100); val st = stNow; say(if (st.dusty) tr("Хр-р… Тут всё в пыли. Хватит пятидесяти слов, чтобы сдуть.", "Zzz… It's all dusty. Fifty words will blow it away.")
            else if (st.todayWords < s.norm) tr("Привет! До нормы ${fmt(s.norm - st.todayWords)} слов.", "Hi! ${fmt(s.norm - st.todayWords)} words to today's goal.") else persona.line(Random.nextInt(9))) }
        var last = 0L
        while (true) withFrameNanos { ns ->
            if (last == 0L) last = ns
            val dt = ((ns - last) / 1e9f).coerceAtMost(.05f)
            if (dt >= 1 / 32f) { last = ns
                if (sprintNow != null && Random.nextFloat() < dt * 5) Board.keys.filter { it.freq > .5f }.random().press = .1f
                scene.step(dt)
                val b = bg; val k = caps
                if (b != null) { scene.render(frame, b, k ?: emptyMap(), set.plate, pages); bmp.setPixels(frame.d, 0, frame.pw, 0, 0, frame.pw, frame.ph) }
                tick++ }
        }
    }
    var chapterPre by remember { mutableStateOf(false) }
    // another screen asked to feed someone: open the right sheet
    LaunchedEffect(nav.homeSheet) { when (val a = nav.homeSheet) {
        null -> {}
        "rest" -> { c.write { restDay() }; say(PERSONAS.getValue("esc").line(0)) }
        "chapter" -> { chapterPre = true; sheet = "write" }
        else -> sheet = a }
        nav.homeSheet = null }
    LaunchedEffect(bubbleAt) { if (bubble != null) { delay(3200); bubble = null } }
    LaunchedEffect(s.sprintEnds) { val sp = sprint ?: return@LaunchedEffect
        // `now` is read by the timer text, so every update recomposes it (tick only redraws the canvas)
        while (System.currentTimeMillis() < sp.endsAt) { delay(500); now = System.currentTimeMillis() }
        // ended while we were away: just ask how it went, without the click
        if (System.currentTimeMillis() - sp.endsAt < 5_000) c.sound.click(s.sw, "enter")
        sheet = "sprintEnd" }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF120C0E))) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }; val hPx = with(density) { maxHeight.toPx() }
        val k = max(wPx / Desk.W, hPx / Desk.H); val ox = (wPx - Desk.W * k) / 2; val oy = hPx - Desk.H * k
        Canvas(Modifier.fillMaxSize().pointerInput(s.pos, sprites) {
            // hold a thing, then drag it; it's saved where it's dropped
            var start = 0 to 0; var acc = Offset.Zero; var id: String? = null
            detectDragGesturesAfterLongPress(
                onDragStart = { o -> val x = (o.x - ox) / k; val y = (o.y - oy) / k
                    scene.spriteAt(x, y)?.let { sp -> id = sp.id; start = s.pos[sp.id] ?: (0 to 0); acc = Offset.Zero; drag = sp.id to start; c.sound.click(s.sw) } },
                onDrag = { ch, d -> val i = id ?: return@detectDragGesturesAfterLongPress; ch.consume(); acc += d
                    val sp = sprites.first { it.id == i }
                    drag = i to Desk.clamp(sp, start.first + (acc.x / k).roundToInt(), start.second + (acc.y / k).roundToInt()) },
                onDragEnd = { val d = drag; if (d != null) { c.sound.click(s.sw); c.write { settings.update { it.copy(pos = it.pos + d, movedHint = true) } } }
                    scope.launch { delay(120); drag = null }; id = null },
                onDragCancel = { drag = null; id = null })
        }.pointerInput(s, st) {
            detectTapGestures { o ->
                val x = (o.x - ox) / k; val y = (o.y - oy) / k
                scene.bugAt(x, y)?.let { b -> scene.bugs.remove(b); c.sound.click(s.sw); c.write { catchBug(b.kind) }
                    c.news.tryEmit(tr("Пойман бес: ", "Caught: ") + bugName(b.kind)); scope.launch { delay(6000); scene.spawnBug() }; return@detectTapGestures }
                if (scene.petAt(x, y)) { scene.pet(); c.write { stroke() }; say(persona.line(Random.nextInt(9))); return@detectTapGestures }
                scene.spriteAt(x, y)?.let { sp ->
                    if (!s.movedHint) c.news.tryEmit(tr("Удерживайте вещь, чтобы передвинуть её по столу", "Hold a thing to move it around the desk"))
                    if (sp.id in setOf("mug", "plant", "lamp", "mascot")) { nav.shopCat = sp.id; nav.tab = 3 }
                    return@detectTapGestures }
                scene.keyAt(x, y)?.let { key -> key.press = .12f
                    c.sound.click(s.sw, when (key.id) { "enter" -> "enter"; "bksp" -> "backspace"; else -> null })
                    if (key.lab.length == 1 && key.lab[0] in 'А'..'Я') scene.drop(1, listOf(key.lab))
                    if (key.id != null && Residents.all.any { it.id == key.id }) { nav.resident = key.id; nav.tab = 1 } }
            }
        }) {
            @Suppress("UNUSED_EXPRESSION") tick
            drawImage(img, IntOffset.Zero, IntSize(frame.pw, frame.ph), IntOffset(ox.roundToInt(), oy.roundToInt()), IntSize((Desk.W * k).roundToInt(), (Desk.H * k).roundToInt()), filterQuality = FilterQuality.None)
            val lamp = sprites.firstOrNull { it.id == "lamp" }; val hot = lamp?.hot
            if (lamp != null && hot != null) { val (ldx, ldy) = scene.at("lamp"); val lc = Offset(ox + (lamp.x + hot.first + ldx) * k, oy + (lamp.y + hot.second + ldy) * k)
                drawCircle(Brush.radialGradient(listOf(Color(0x38FFD68C), Color.Transparent), lc, 150 * k), 150 * k, lc) }
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x8C0A0406)), Offset(size.width * .4f, size.height * .4f), size.height * .8f))
        }

        // pet's speech bubble, over the lifted space bar
        // the bubble's bottom sits just above the lifted space bar
        val bubbleBottom = with(density) { (oy + (Board.space.y - 40) * k).toDp() }
        AnimatedVisibility(bubble != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxWidth().height(bubbleBottom)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Text(bubble ?: "", style = T.body, color = p.ink, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 260.dp).shadow(10.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(p.surface).padding(horizontal = 14.dp, vertical = 9.dp))
            }
        }

        // the book and today's goal
        Row(Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth().shadow(14.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
            .background(p.surface.copy(alpha = .94f)).clickable { sheet = "book" }.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(book?.title ?: tr("Новая книга", "New book"), style = T.bookTitle, color = p.ink, maxLines = 1)
                val ch = book?.let { st.bookChapters(it) } ?: 0
                Text((if (ch > 0) tr("гл. ${ch + 1} · ", "ch. ${ch + 1} · ") else "") + "${fmt(bookWords)} ${tr("из", "of")} ${fmt(book?.goal ?: 0)} ${tr("слов", "words")}", style = T.small, color = p.mute)
                Spacer(Modifier.height(7.dp))
                Bar(bookWords.toFloat() / max(1, book?.goal ?: 1), Modifier.width(190.dp), height = 5.dp)
            }
            Ring(st.todayWords, s.norm)
        }
        sprint?.let { sp -> val left = max(0L, sp.endsAt - max(now, System.currentTimeMillis())) / 1000
            val el = sp.minutes * 60 - left; val ghost = (sp.ghostWpm10 * el / 600f).roundToInt()
            Text("⏱ ${left / 60}:${(left % 60).toString().padStart(2, '0')}  ·  " + tr("призрак уже на $ghost словах", "ghost is at $ghost words"), style = T.smallB, color = p.ink,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 104.dp).clip(RoundedCornerShape(20.dp)).background(p.surface).padding(horizontal = 14.dp, vertical = 7.dp)) }

        // three keys: sprint, write (Enter), edit
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
            Keycap(Modifier.size(56.dp), onClick = { sheet = if (sprint != null) "sprintEnd" else "sprint" }) { Icon("clock", p.kLeg, 22.dp) }
            Keycap(Modifier.width(170.dp).height(56.dp), accent = true, key = "enter", onClick = { sheet = "write" }) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("Записать", "Log words"), style = T.btn, color = p.xLeg); Spacer(Modifier.width(10.dp)); Text("⏎", style = T.btn, color = p.xLeg.copy(alpha = .7f)) }
            }
            Keycap(Modifier.size(56.dp), key = "backspace", onClick = { sheet = "edit" }) { Icon("pen", p.kLeg, 22.dp) }
        }
    }

    when (sheet) {
        "write", "edit" -> WriteSheet(st, s, if (sheet == "edit") 2 else 0, chapterPre, onLog = { sheet = "log"; chapterPre = false }, onClose = { sheet = null; chapterPre = false }) { n, line -> scene.dust = false; if (n > 0) scene.drop((n / 40).coerceIn(3, 14)); say(line) }
        "sprint" -> SprintStart(s, onClose = { sheet = null }) { m -> val ends = System.currentTimeMillis() + m * 60_000L; val ghost = max(s.bestSprint, 150)
            c.write { settings.update { it.copy(sprintEnds = ends, sprintMin = m, sprintGhost = ghost) } }; sheet = null
            say(PERSONAS.getValue("caps").line(0)) }
        "sprintEnd" -> SprintEnd(s, sprint, onClose = { sheet = null
            // closing the sheet mid-sprint keeps the sprint going; once it's over, closing drops it
            if (sprint != null && System.currentTimeMillis() >= sprint.endsAt) endSprint() }) { line -> say(line); scene.drop(10) }
        "book" -> BookSheet(st, s) { sheet = null }
        "log" -> LogSheet(st) { sheet = null }
    }
}

@Composable
private fun Ring(v: Int, goal: Int) {
    val p = K.pal
    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 6.dp.toPx(); val r = size.minDimension / 2 - w / 2
            drawCircle(p.track, r, style = Stroke(w))
            drawArc(p.acc, -90f, 360f * (v.toFloat() / max(1, goal)).coerceAtMost(1f), false, Offset(w / 2, w / 2), androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fmt(v), style = T.num.copy(fontSize = T.body.fontSize), color = p.ink)
            Text(tr("из ", "of ") + fmt(goal), style = T.tiny, color = p.mute)
        }
    }
}
