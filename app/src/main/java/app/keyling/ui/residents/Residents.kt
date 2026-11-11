package app.keyling.ui.residents

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.keyling.container
import app.keyling.data.Settings
import app.keyling.domain.Ability
import app.keyling.domain.Food
import app.keyling.domain.PERSONAS
import app.keyling.domain.Residents
import app.keyling.domain.Stats
import app.keyling.domain.fmt
import app.keyling.domain.tr
import app.keyling.ui.Nav
import app.keyling.ui.Share
import app.keyling.ui.Shareable
import app.keyling.ui.art.Board
import app.keyling.ui.art.Buf
import app.keyling.ui.art.CapSets
import app.keyling.ui.art.Nook
import app.keyling.ui.components.Bar
import app.keyling.ui.components.Card
import app.keyling.ui.components.Click
import app.keyling.ui.components.Gap
import app.keyling.ui.components.Icon
import app.keyling.ui.components.Keycap
import app.keyling.ui.components.Pill
import app.keyling.ui.components.Pixels
import app.keyling.ui.home.BigButton
import app.keyling.ui.theme.K
import app.keyling.ui.theme.T
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The residents' map: the keyboard with every home key glowing. Tapping a key lifts its cap and shows
 * the resident at home. Below: what it does on the desk, what it eats, how it grows, and how to feed it now.
 */
@Composable
fun ResidentsScreen(st: Stats, s: Settings, nav: Nav) {
    val ctx = LocalContext.current; val c = ctx.container; val p = K.pal
    var sel by remember { mutableStateOf(nav.resident ?: s.pet) }
    LaunchedEffect(nav.resident) { nav.resident?.let { sel = it; nav.resident = null } }
    val set = CapSets.of(s.caps)
    val caps = remember(set) { c.art.caps(set, st.activeDays) }
    val homes = Residents.all.associate { it.id to it.open(st) }
    val r = Residents.of(sel); val open = r.open(st); val persona = PERSONAS.getValue(sel)
    val level = r.level(st); val onDesk = s.pet == sel
    val mapBuf = remember { Buf(260, 104) }; val mapBmp = remember { Bitmap.createBitmap(260, 104, Bitmap.Config.ARGB_8888) }
    val nookBuf = remember { Buf(Nook.W, Nook.H) }; val nookBmp = remember { Bitmap.createBitmap(Nook.W, Nook.H, Bitmap.Config.ARGB_8888) }
    var tick by remember { mutableIntStateOf(0) }
    var lift by remember(sel) { mutableFloatStateOf(0f) }
    var line by remember(sel) { mutableStateOf(persona.line(0)) }
    var talk by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(sel, level) {
        var last = 0L; var t = 0f
        while (true) withFrameNanos { ns -> if (last == 0L) last = ns; val dt = (ns - last) / 1e9f
            if (dt >= 1 / 30f) { last = ns; t += dt; lift = (lift + dt * 2.2f).coerceAtMost(1f); if (talk > 0) talk -= dt
                Nook.keyboard(mapBuf, caps, set, homes, sel, t); mapBmp.setPixels(mapBuf.d, 0, mapBuf.w, 0, 0, mapBuf.w, mapBuf.h)
                Nook.render(nookBuf, sel, open, set, t, if (open) easeOut(lift) else lift * .25f, talk > 0, Food.stageIndex(level))
                nookBmp.setPixels(nookBuf.d, 0, nookBuf.w, 0, 0, nookBuf.w, nookBuf.h); tick++ } }
    }
    fun feed() { Food.action(sel)?.let { (a, _) -> nav.homeSheet = a; nav.tab = 0 } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 18.dp).padding(top = 22.dp, bottom = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Жители клавиш", "Key residents"), style = T.title, color = p.ink, modifier = Modifier.weight(1f))
            Pill("${homes.values.count { it }} / ${homes.size}", p.surface2, p.ink, style = T.bodyB)
        }
        Gap(6.dp)
        Text(tr("Под каждой клавишей-домиком живёт кто-то свой. Нажмите на клавишу – колпачок поднимется.", "Someone lives under each home key. Tap a key and its cap will lift."), style = T.small, color = p.mute)
        Gap(14.dp)
        // the map
        val mapImg = remember(mapBmp) { mapBmp.asImageBitmap() }
        Canvas(Modifier.fillMaxWidth().aspectRatio(260 / 104f).clip(RoundedCornerShape(14.dp)).pointerInput(Unit) {
            detectTapGestures { o -> val k = size.width / 260f; val x = o.x / k; val y = o.y / k + Board.KY - 14
                Board.keys.firstOrNull { x >= it.x && x < it.x + it.w && y >= it.y - 3 && y < it.y + it.h + 3 }?.let { key ->
                    c.sound.click(s.sw, when (key.id) { "space" -> "space"; "enter" -> "enter"; "bksp" -> "backspace"; else -> null })
                    if (key.id != null && homes.containsKey(key.id)) sel = key.id } }
        }) { @Suppress("UNUSED_EXPRESSION") tick
            drawImage(mapImg, IntOffset.Zero, IntSize(260, 104), IntOffset.Zero, IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.None) }
        Gap(8.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFFD36B))); Spacer(Modifier.width(6.dp)); Text(tr("живёт", "lives here"), style = T.tiny, color = p.mute)
            Spacer(Modifier.width(14.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF8A7A9A))); Spacer(Modifier.width(6.dp)); Text(tr("ещё не поселился", "not moved in yet"), style = T.tiny, color = p.mute)
        }
        Gap(10.dp)
        // quick picker
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (x in Residents.all) { val o = x.open(st)
                Keycap(Modifier.weight(1f).height(62.dp), accent = x.id == sel, down = x.id == sel, onClick = { sel = x.id }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val stage = Food.stageIndex(x.level(st))
                        val pb = remember(x.id, o, stage) { app.keyling.ui.art.portrait(x.id, o, stage, 40, 34, app.keyling.ui.art.Critters.SMALL) }
                        Pixels(pb, Modifier.width(34.dp))
                        Text(if (o) "${x.level(st)}" else "?", style = T.tiny, color = if (x.id == sel) p.xLeg else p.kLeg)
                    }
                }
            }
        }
        Gap(14.dp)

        // the nook and who lives there
        val nookImg = remember(nookBmp) { nookBmp.asImageBitmap() }
        Card(pad = 0.dp) { Column {
            Box {
                Canvas(Modifier.fillMaxWidth().aspectRatio(Nook.W / Nook.H.toFloat()).pointerInput(sel, open) {
                    detectTapGestures { if (open) { line = persona.line(Random.nextInt(9)); talk = 1.2f; c.sound.click(Click.sw); c.write { stroke() } } }
                }) { @Suppress("UNUSED_EXPRESSION") tick
                    drawImage(nookImg, IntOffset.Zero, IntSize(Nook.W, Nook.H), IntOffset.Zero, IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.None) }
                if (onDesk) Pill(tr("живёт на столе", "on the desk"), p.acc, p.accInk, Modifier.align(Alignment.TopStart).padding(12.dp))
                if (open) Keycap(Modifier.align(Alignment.TopEnd).padding(10.dp).size(42.dp), onClick = { nav.card = Shareable(listOf(r.name to { Share.resident(ctx, r, st, s) })) }) { Icon("upload", p.kLeg, 18.dp) }
            }
            if (open) Text("«$line»", style = T.body.copy(fontFamily = app.keyling.ui.theme.Lora), color = p.ink, modifier = Modifier.fillMaxWidth().background(p.surface2).padding(horizontal = 16.dp, vertical = 10.dp))
            Column(Modifier.padding(16.dp)) {
                Text(if (open) r.name else tr("Кто-то шуршит…", "Something rustles…"), style = T.h2, color = p.ink)
                Gap(4.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(tr("под ", "under ") + r.key, p.surface2, p.ink)
                    if (open) Pill(r.stage(st) + " · " + tr("ур. ", "lvl ") + level, p.warmSoft, p.warm)
                }
                if (open) { Gap(10.dp); Text(persona.trait + ". " + tr("Любит ", "Loves ") + persona.likes + ".", style = T.small, color = p.mute) }
            }
        } }

        // what it does on the desk
        Gap(12.dp)
        val (abName, abText) = Ability.of(sel)
        Card { Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(p.accSoft), contentAlignment = Alignment.Center) { Icon("sparkle", p.acc, 20.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Способность: ", "Ability: ") + abName, style = T.bodyB, color = p.ink)
                Text(abText, style = T.body, color = p.ink)
                Gap(6.dp)
                Text(when { onDesk -> tr("✓ Работает – житель на столе.", "✓ Active – it's on the desk.")
                    open -> tr("Работает, только пока житель живёт на столе.", "Works only while it lives on the desk.")
                    else -> tr("Откроется, когда житель поселится.", "Unlocks when it moves in.") }, style = T.smallB, color = if (onDesk) p.ok else p.mute)
            }
        } }

        // how it grows
        Gap(12.dp)
        Card { Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(p.warmSoft), contentAlignment = Alignment.Center) { Icon("star", p.warm, 20.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (open) tr("Как растёт", "How it grows") else tr("Как поселить", "How to bring it in"), style = T.bodyB, color = p.ink)
                    Text(Food.what(sel), style = T.body, color = p.ink)
                }
            }
            Gap(14.dp)
            val amt = r.amount(st); val next = if (open) r.nextAt(st) else r.steps[0]
            val prev = if (!open) 0 else r.steps.lastOrNull { amt >= it } ?: 0
            val unit = tr(r.unitRu, r.unitEn)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (open) tr("Уровень $level", "Level $level") else tr("Ещё не здесь", "Not here yet"), style = T.num, color = p.ink, modifier = Modifier.weight(1f))
                Text("${fmt(amt)} $unit", style = T.small, color = p.mute)
            }
            Gap(8.dp)
            Bar(if (next == null) 1f else (amt - prev).toFloat() / (next - prev).coerceAtLeast(1), color = p.warm, height = 8.dp)
            Gap(6.dp)
            Text(when {
                next == null -> tr("Вырос до конца – древний житель.", "Fully grown – an ancient resident.")
                !open -> tr("Ещё ${fmt(next - amt)} $unit – и он поселится.", "${fmt(next - amt)} more $unit and it moves in.")
                else -> tr("Ещё ${fmt(next - amt)} $unit до ${level + 1} уровня.", "${fmt(next - amt)} more $unit to level ${level + 1}.")
            }, style = T.small, color = p.mute)
            if (open) {
                Gap(14.dp)
                Ladder(level, r.steps.size + (if (sel == "space") 1 else 0))
                Gap(8.dp)
                Text(tr("С уровнями житель подрастает: на 3-м становится подростком, на 6-м – взрослым, на 9-м – древним.",
                    "It grows with its levels: a youngster at 3, grown at 6, ancient at 9."), style = T.small, color = p.mute)
            }
            val act = Food.action(sel)
            if (act != null) { Gap(14.dp)
                Keycap(Modifier.fillMaxWidth().height(50.dp), onClick = { feed() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon("plus", p.kLeg, 18.dp); Spacer(Modifier.width(8.dp)); Text(tr("Накормить: ", "Feed it: ") + act.second.replaceFirstChar { it.lowercase() }, style = T.bodyB, color = p.kLeg) } } }
            else { Gap(10.dp); Text(tr("Это не сделать по кнопке: просто напишите что-нибудь после полуночи.", "There's no button for this: just write something after midnight."), style = T.small, color = p.mute) }
        } }

        // invite to the desk
        Gap(16.dp)
        if (open && !onDesk) BigButton(tr("Позвать на стол", "Invite to the desk")) {
            c.write { settings.update { it.copy(pet = sel) }; reconcile(0, false, false) }
            c.news.tryEmit(tr("${r.name} перебрался под пробел – способность включена", "${r.name} moved under the space bar – ability on")); nav.tab = 0 }
        else if (onDesk) Text(tr("Живёт на столе, в уголке под пробелом. Чтобы позвать другого, выберите его и нажмите «Позвать на стол».",
            "Lives on the desk, in the nook under the space bar. To invite another, pick it and tap “Invite to the desk”."), style = T.small, color = p.mute, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** The level steps with the four stages written under them; done steps are filled, the current one is marked. */
@Composable
private fun Ladder(level: Int, steps: Int) {
    val p = K.pal; val n = steps.coerceAtMost(11)
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 1..n) Box(Modifier.weight(1f).height(if (i == level) 14.dp else 10.dp).clip(RoundedCornerShape(4.dp))
                .background(when { i < level -> p.warm; i == level -> p.acc; else -> p.track }))
        }
        Gap(4.dp)
        Row {
            for ((label, span) in listOf(tr("малыш", "baby") to 2, tr("подросток", "youngster") to 3, tr("взрослый", "grown") to 3, tr("древний", "ancient") to (n - 8)))
                if (span > 0) Text(label, style = T.tiny, color = p.mute, textAlign = TextAlign.Center, modifier = Modifier.weight(span.toFloat()))
        }
    }
}

private fun easeOut(t: Float) = 1 - (1 - t) * (1 - t) * (1 - t)
