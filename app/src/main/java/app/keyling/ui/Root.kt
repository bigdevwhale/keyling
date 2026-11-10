package app.keyling.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.keyling.container
import app.keyling.data.Settings
import app.keyling.domain.Stats
import app.keyling.domain.tr
import app.keyling.ui.components.Click
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import app.keyling.ui.components.Icon
import app.keyling.ui.components.Keycap
import app.keyling.ui.home.HomeScreen
import app.keyling.ui.residents.ResidentsScreen
import app.keyling.ui.theme.K
import app.keyling.ui.theme.T
import app.keyling.ui.totals.TotalsScreen
import app.keyling.ui.workshop.WorkshopScreen
import kotlinx.coroutines.delay

/** Where to land when one screen sends you to another (a resident's key, a desk item). */
/** Where a demo/screenshot launch opens (set from the intent in demo builds only). */
object Start { var tab = 0; var sub = 0; var resident: String? = null; var shop = 0 }

/** Cards that can be shared from one place: their tab names and how to draw each. */
class Shareable(val options: List<Pair<String, () -> Card>>)

class Nav { var card by mutableStateOf<Shareable?>(null); var tab by mutableIntStateOf(0); var resident by mutableStateOf<String?>(null); var shopCat by mutableStateOf<String?>(null); var homeSheet by mutableStateOf<String?>(null) }

@Composable
fun Root(s: Settings) {
    val c = LocalContext.current.container
    val books by c.dao.books().collectAsState(initial = emptyList())
    val entries by c.dao.entries().collectAsState(initial = emptyList())
    val caught by c.dao.caught().collectAsState(initial = emptyList())
    val stats = remember(books, entries, caught, s) { Stats(books, entries, caught, s, c.today()) }
    val nav = remember { Nav().apply { tab = Start.tab; resident = Start.resident } }
    Click.sw = s.sw
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { c.news.collect { note = it; delay(2600); note = null } }
    val p = K.pal
    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (nav.tab) {
                    0 -> HomeScreen(stats, s, nav)
                    1 -> ResidentsScreen(stats, s, nav)
                    2 -> TotalsScreen(stats, s, nav)
                    else -> WorkshopScreen(stats, s, nav)
                }
            }
            Dock(nav.tab) { nav.tab = it }
        }
        nav.card?.let { CardPreview(it) { nav.card = null } }
        AnimatedVisibility(note != null, Modifier.align(Alignment.BottomCenter).padding(bottom = 170.dp), enter = fadeIn(), exit = fadeOut()) {
            Text(note ?: "", style = T.body, color = p.bg, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(p.ink).padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

/** The tab bar: four keycaps on a strip of keyboard case. */
@Composable
private fun Dock(sel: Int, onSel: (Int) -> Unit) {
    val p = K.pal
    val tabs = listOf("desk" to tr("Стол", "Desk"), "paw" to tr("Жители", "Residents"), "chart" to tr("Итоги", "Totals"), "shop" to tr("Мастерская", "Workshop"))
    Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(p.caseHi, p.case, p.caseLo))).navigationBarsPadding()
        .padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        tabs.forEachIndexed { i, (icon, label) ->
            Keycap(Modifier.weight(1f).height(60.dp), accent = i == sel, down = i == sel, onClick = { onSel(i) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, if (i == sel) p.xLeg else p.kLeg, 20.dp)
                    Text(label, style = T.smallB.copy(fontSize = T.tiny.fontSize), color = if (i == sel) p.xLeg else p.kLeg, maxLines = 1)
                }
            }
        }
    }
}

/** The card as it will be posted, with Share and Save under it. */
@Composable
private fun CardPreview(set: Shareable, onClose: () -> Unit) {
    val ctx = LocalContext.current; val c = ctx.container; val p = K.pal
    var idx by remember { mutableIntStateOf(0) }
    val made = remember { HashMap<Int, Card>() }
    val card = made.getOrPut(idx) { set.options[idx].second() }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (set.options.size > 1) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    set.options.forEachIndexed { i, (label, _) ->
                        Text(label, style = T.bodyB, color = if (i == idx) p.accInk else p.ink, modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (i == idx) p.acc else p.surface).clickable { c.sound.click(Click.sw); idx = i }.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
            }
            androidx.compose.foundation.Image(card.bmp.asImageBitmap(), null, Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)))
            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Keycap(Modifier.weight(1f).height(54.dp), onClick = { val ok = Share.save(ctx, card.bmp)
                    c.news.tryEmit(if (ok) tr("Открытка сохранена в галерею", "Saved to the gallery") else tr("Не удалось сохранить", "Couldn't save")) }) {
                    Text(tr("Сохранить", "Save"), style = T.btn, color = p.kLeg) }
                Keycap(Modifier.weight(1f).height(54.dp), accent = true, key = "enter", onClick = { Share.send(ctx, card.bmp, card.title); onClose() }) {
                    Text(tr("Поделиться", "Share"), style = T.btn, color = p.xLeg) }
            }
        }
    }
}
