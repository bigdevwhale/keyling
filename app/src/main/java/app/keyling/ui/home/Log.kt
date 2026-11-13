package app.keyling.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import app.keyling.container
import app.keyling.data.Entry
import app.keyling.domain.Stats
import app.keyling.domain.fmtDay
import app.keyling.domain.tr
import app.keyling.domain.words
import app.keyling.ui.components.Gap
import app.keyling.ui.theme.K
import app.keyling.ui.theme.T
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** What a sitting was, in a few words. */
fun entryLine(e: Entry): String = when (e.kind) {
    "sprint" -> tr("Спринт: ", "Sprint: ") + words(e.words) + (if (e.minutes > 0) tr(" за ${e.minutes} мин", " in ${e.minutes} min") else "")
    "cut" -> tr("Убрано ", "Cut ") + words(-e.words)
    "edit" -> tr("Правка: ${e.minutes} мин", "Editing: ${e.minutes} min")
    "rest" -> tr("Выходной", "Day off")
    "past" -> tr("Написано до Клавишки: ", "Before Keyling: ") + words(e.words)
    else -> "+" + words(e.words)
} + if (e.chapter) tr(" · глава ✓", " · chapter ✓") else ""

private fun dayLabel(day: Long, today: Long): String = when (day) {
    today -> tr("Сегодня", "Today")
    today - 1 -> tr("Вчера", "Yesterday")
    else -> fmtDay(day) + (LocalDate.ofEpochDay(day).year.let { if (it != LocalDate.ofEpochDay(today).year) " $it" else "" })
}

private fun clock(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).let { "%d:%02d".format(it.hour, it.minute) }

/** Every sitting logged so far, newest first; tap one to fix its number or delete it. */
@Composable
fun LogSheet(st: Stats, onClose: () -> Unit) {
    val p = K.pal
    var shown by remember { mutableIntStateOf(40) }
    var open by remember { mutableStateOf<Entry?>(null) }
    Sheet(onClose) {
        val e = open
        if (e != null) EntryEdit(st, e) { open = null }
        else {
            Text(tr("Мои записи", "My log"), style = T.h2, color = p.ink); Gap(4.dp)
            Text(tr("Нажмите на запись, чтобы исправить число или удалить её.", "Tap a sitting to fix its number or delete it."), style = T.small, color = p.mute)
            val list = st.entries.sortedByDescending { it.at }
            val titles = st.books.associate { it.id to it.title }; val manyBooks = list.map { it.bookId }.distinct().size > 1
            if (list.isEmpty()) { Gap(16.dp); Text(tr("Пока ни одной записи. Нажмите «Записать» на столе.", "Nothing logged yet. Tap «Log words» on the desk."), style = T.body, color = p.mute) }
            var lastDay = Long.MIN_VALUE
            for (en in list.take(shown)) {
                if (en.day != lastDay) { lastDay = en.day; Text(dayLabel(en.day, st.today), style = T.sec, color = p.mute, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)) }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = en }.padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(clock(en.at), style = T.small, color = p.mute, modifier = Modifier.width(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entryLine(en), style = T.bodyB, color = p.ink)
                        if (manyBooks) titles[en.bookId]?.let { Text(it, style = T.small, color = p.mute, maxLines = 1) }
                    }
                    Text("›", style = T.bodyB, color = p.mute)
                }
            }
            if (list.size > shown) { Gap(10.dp); Chip(tr("Показать ещё", "Show more"), false, Modifier.fillMaxWidth()) { shown += 40 } }
        }
    }
}

/** One sitting: its number, the chapter mark, and a way to delete it. */
@Composable
private fun EntryEdit(st: Stats, e: Entry, onBack: () -> Unit) {
    val c = LocalContext.current.container; val p = K.pal
    val minutes = e.kind == "edit"; val hasNum = e.kind != "rest"; val canChapter = e.kind == "add" || e.kind == "sprint"
    var text by remember(e.id) { mutableStateOf((if (minutes) e.minutes else abs(e.words)).toString()) }
    var chapter by remember(e.id) { mutableStateOf(e.chapter) }
    var sure by remember(e.id) { mutableStateOf(false) }
    val n = text.toIntOrNull() ?: 0
    val fixed = when { minutes -> e.copy(minutes = n); e.kind == "cut" -> e.copy(words = -n); else -> e.copy(words = n) }.copy(chapter = chapter && canChapter)

    Text("‹ " + tr("Все записи", "All sittings"), style = T.smallB, color = p.acc, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(vertical = 6.dp))
    Gap(6.dp)
    Text(entryLine(e), style = T.h2, color = p.ink)
    Text(dayLabel(e.day, st.today) + ", " + clock(e.at) + (st.books.firstOrNull { it.id == e.bookId }?.let { " · " + it.title } ?: ""), style = T.small, color = p.mute)
    Gap(14.dp)
    if (hasNum) {
        Text(when { minutes -> tr("Минут правки", "Minutes of editing"); e.kind == "cut" -> tr("Убрано слов", "Words cut"); else -> tr("Слов", "Words") }, style = T.small, color = p.mute)
        Gap(4.dp); NumField(text, "0", Modifier.fillMaxWidth()) { text = it }; Gap(10.dp)
    }
    if (canChapter) { Chip("✓ " + tr("Глава закончена", "Chapter finished"), chapter) { chapter = !chapter }; Gap(10.dp) }
    Text(tr("Буквицы пересчитаются: за лишнее спишутся, за недостающее добавятся.", "Coins follow the fix: extra ones are taken back, missing ones added."),
        style = T.small, color = p.mute, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.surface2).padding(12.dp))
    Gap(14.dp)
    if (hasNum || canChapter) { BigButton(tr("Сохранить", "Save"), enabled = (!hasNum || n > 0) && fixed != e) { c.write { editEntry(e, fixed) }; onBack() }; Gap(10.dp) }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (sure) { c.write { editEntry(e, null) }; onBack() } else sure = true }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(if (sure) tr("Нажмите ещё раз, чтобы удалить", "Tap again to delete") else tr("Удалить запись", "Delete this sitting"), style = T.bodyB, color = p.bad, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(4.dp))
}
