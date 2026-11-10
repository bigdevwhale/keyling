package app.keyling.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.keyling.container
import app.keyling.data.Book
import app.keyling.data.Settings
import app.keyling.domain.PERSONAS
import app.keyling.domain.Stats
import app.keyling.domain.fmt
import app.keyling.domain.tr
import app.keyling.ui.components.Click
import app.keyling.ui.components.Gap
import app.keyling.ui.components.Keycap
import app.keyling.ui.components.Tabs
import app.keyling.ui.theme.K
import app.keyling.ui.theme.T
import kotlin.math.max
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(onClose: () -> Unit, content: @Composable () -> Unit) {
    val p = K.pal
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.surface,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(p.dim)) }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 22.dp).navigationBarsPadding()) { content() }
    }
}

@Composable
fun NumField(value: String, hint: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val p = K.pal
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(p.bg).border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 13.dp)) {
        if (value.isEmpty()) Text(hint, style = T.body, color = p.mute)
        BasicTextField(value, { v -> onChange(v.filter { it.isDigit() }.take(7)) }, textStyle = T.body.copy(color = p.ink), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), cursorBrush = SolidColor(p.acc), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun TextField1(value: String, hint: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val p = K.pal
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(p.bg).border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 13.dp)) {
        if (value.isEmpty()) Text(hint, style = T.body, color = p.mute)
        BasicTextField(value, { onChange(it.take(60)) }, textStyle = T.body.copy(color = p.ink), singleLine = true, cursorBrush = SolidColor(p.acc), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun Chip(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = K.pal; val c = LocalContext.current.container
    Text(text, style = T.body, color = if (on) p.acc else p.ink, modifier = modifier.clip(RoundedCornerShape(12.dp)).background(if (on) p.accSoft else p.surface2)
        .border(1.dp, if (on) p.acc else p.surface2, RoundedCornerShape(12.dp)).clickable { c.sound.click(Click.sw); onClick() }.padding(horizontal = 12.dp, vertical = 9.dp))
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val p = K.pal
    Keycap(modifier.fillMaxWidth().height(54.dp), accent = enabled, key = "enter", onClick = { if (enabled) onClick() }) { Text(text, style = T.btn, color = if (enabled) p.xLeg else p.mute) }
}

/** Logging a sitting: words added, the manuscript's total now, or minutes of editing. */
@Composable
fun WriteSheet(st: Stats, s: Settings, start: Int, chapterPre: Boolean = false, onClose: () -> Unit, onDone: (Int, String) -> Unit) {
    val c = LocalContext.current.container; val p = K.pal
    var mode by remember { mutableIntStateOf(start) }
    var text by remember { mutableStateOf("") }
    var chapter by remember { mutableStateOf(chapterPre) }
    val n = text.toIntOrNull() ?: 0
    val book = st.books.firstOrNull { it.id == s.book } ?: st.books.firstOrNull()
    val bookWords = book?.let { st.bookWords(it) } ?: 0
    Sheet(onClose) {
        Text(tr("Что сделано?", "What did you do?"), style = T.h2, color = p.ink); Gap(12.dp)
        Tabs(listOf(tr("Прибавилось", "Added"), tr("Всего в книге", "Book total"), tr("Правка", "Editing")), mode) { mode = it; text = "" }
        Gap(14.dp)
        when (mode) {
            0 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for (v in listOf(100, 250, 500, 1000)) Chip("+$v", n == v, Modifier.weight(1f)) { text = v.toString() } }
                Gap(10.dp); NumField(text, tr("Своё число слов", "Your own number"), Modifier.fillMaxWidth()) { text = it }
                Gap(10.dp); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("✓ " + tr("Глава закончена", "Chapter finished"), chapter) { chapter = !chapter }
                    Chip(tr("Сегодня выходной", "Day off today"), false) { c.write { restDay() }; onDone(0, PERSONAS.getValue("esc").line(0)); onClose() } }
                Gap(12.dp)
                val after = st.todayWords + n; val left = app.keyling.domain.Ability.normAt(s.pet, s.norm) - after
                Text(if (n > 0) tr("Станет ${fmt(bookWords + n)} слов в книге · ", "The book will have ${fmt(bookWords + n)} words · ") +
                        (if (left > 0) tr("до нормы ещё ${fmt(left)}", "${fmt(left)} to today's goal") else tr("норма выполнена", "goal reached")) + " · +${app.keyling.domain.Ability.wordCoins(s.pet, n, "add", java.time.LocalTime.now().hour) + (if (chapter) app.keyling.domain.Ability.chapterCoins(s.pet) else 0)} " + tr("буквиц", "coins")
                    else tr("Сегодня ${fmt(st.todayWords)} из ${fmt(s.norm)} · буквица за каждые 25 слов", "Today ${fmt(st.todayWords)} of ${fmt(s.norm)} · a coin for every 25 words"),
                    style = T.small, color = p.mute, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.surface2).padding(12.dp))
                Gap(14.dp)
                BigButton(tr("Скормить", "Feed it"), enabled = n > 0) { c.write { addWords(n, chapter) }
                    onDone(n, if (chapter) PERSONAS.getValue("enter").line(0) else PERSONAS.getValue(s.pet).line(Random.nextInt(9))); onClose() }
            }
            1 -> {
                NumField(text, tr("Например, ${fmt(bookWords + 640)}", "For example ${fmt(bookWords + 640)}"), Modifier.fillMaxWidth()) { text = it }
                Gap(8.dp); Text(tr("Посмотрите счётчик в Word или Google Docs и введите итог – Клавишка сама посчитает, сколько прибавилось.",
                    "Look at the counter in Word or Google Docs and type the total – Keyling works out what's new."), style = T.small, color = p.mute)
                Gap(14.dp)
                BigButton(tr("Учесть", "Count it"), enabled = n > 0) { val d = n - bookWords
                    c.write { setTotal(n) }
                    onDone(max(0, d), if (d < 0) PERSONAS.getValue("bksp").line(0) else if (d == 0) tr("Ничего не прибавилось. Бывает!", "Nothing new. It happens!") else PERSONAS.getValue(s.pet).line(Random.nextInt(9))); onClose() }
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for ((v, l) in listOf(15 to "15 мин", 30 to "30 мин", 60 to tr("1 час", "1 h"), 120 to tr("2 часа", "2 h")))
                    Chip(if (v < 60) tr(l, "$v min") else l, n == v, Modifier.weight(1f)) { text = v.toString() } }
                Gap(10.dp); Text(tr("Правка кормит Затирыша: слов не прибавляется, но работа идёт.", "Editing feeds Erasel: no new words, but the work goes on."), style = T.small, color = p.mute)
                Gap(14.dp)
                BigButton(tr("Записать правку", "Log editing"), enabled = n > 0) { c.write { logEdit(n) }; onDone(0, PERSONAS.getValue("bksp").line(2)); onClose() }
            }
        }
    }
}

@Composable
fun SprintStart(s: Settings, onClose: () -> Unit, onGo: (Int) -> Unit) {
    val p = K.pal; var m by remember { mutableIntStateOf(15) }
    Sheet(onClose) {
        Text(tr("Спринт", "Sprint"), style = T.h2, color = p.ink); Gap(6.dp)
        Text(tr("Пишите где удобно, Клавишка засечёт время. Призрак – это ваш лучший спринт: попробуйте его обогнать.",
            "Write wherever you like, Keyling keeps time. The ghost is your best sprint: try to beat it."), style = T.body, color = p.mute)
        Gap(14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for (v in listOf(10, 15, 25, 45)) Chip(tr("$v мин", "$v min"), m == v, Modifier.weight(1f)) { m = v } }
        Gap(10.dp)
        Text(if (s.bestSprint > 0) tr("Призрак пишет ${s.bestSprint / 10} слов в минуту – это ${s.bestSprint * m / 10} слов за $m минут.", "The ghost writes ${s.bestSprint / 10} words a minute: ${s.bestSprint * m / 10} in $m minutes.")
            else tr("Первый спринт задаст призрака.", "Your first sprint sets the ghost."), style = T.small, color = p.mute)
        Gap(14.dp); BigButton(tr("Начать", "Start")) { onGo(m) }
    }
}

@Composable
fun SprintEnd(s: Settings, sp: Sprint?, onClose: () -> Unit, onDone: (String) -> Unit) {
    val c = LocalContext.current.container; val p = K.pal; var text by remember { mutableStateOf("") }
    val minutes = sp?.let { max(1, ((System.currentTimeMillis() - (it.endsAt - it.minutes * 60_000L)) / 60_000L).toInt().coerceAtMost(it.minutes)) } ?: 15
    Sheet(onClose) {
        Text(tr("Сколько написано за спринт?", "How much did you write?"), style = T.h2, color = p.ink); Gap(12.dp)
        NumField(text, tr("Слов за $minutes мин", "Words in $minutes min"), Modifier.fillMaxWidth()) { text = it }
        Gap(14.dp)
        BigButton(tr("Готово", "Done"), enabled = (text.toIntOrNull() ?: 0) > 0) {
            val n = text.toInt(); val wpm10 = n * 10 / minutes; val ghost = s.bestSprint * minutes / 10; val won = s.bestSprint > 0 && n > ghost
            c.write { settings.update { it.copy(sprints = it.sprints + 1, bestSprint = max(it.bestSprint, wpm10), ghostWins = it.ghostWins + if (won) 1 else 0) }; addWords(n, kind = "sprint", minutes = minutes) }
            onDone(if (won) tr("Призрак обогнан на ${n - ghost} слов! Горлан поёт.", "You beat the ghost by ${n - ghost} words! Shouter sings.") else if (s.bestSprint == 0) tr("Призрак родился. Теперь есть кого догонять.", "The ghost is born. Now there's someone to chase.")
                else tr("Призрак был на ${ghost - n} слов впереди. В следующий раз!", "The ghost was ${ghost - n} words ahead. Next time!"))
            onClose()
        }
    }
}

/** The book on the desk: its name, goal, plan; switch to another, start a new one, finish the draft. */
@Composable
fun BookSheet(st: Stats, s: Settings, onClose: () -> Unit) {
    val c = LocalContext.current.container; val p = K.pal
    val book = st.books.firstOrNull { it.id == s.book } ?: st.books.firstOrNull()
    var title by remember { mutableStateOf(book?.title ?: "") }
    var goal by remember { mutableStateOf((book?.goal ?: 80000).toString()) }
    var planned by remember { mutableStateOf((book?.planned ?: 0).takeIf { it > 0 }?.toString() ?: "") }
    var start by remember { mutableStateOf("") }
    Sheet(onClose) {
        Text(tr("Книга на столе", "The book on the desk"), style = T.h2, color = p.ink); Gap(12.dp)
        TextField1(title, tr("Название", "Title"), Modifier.fillMaxWidth()) { title = it }; Gap(8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { Text(tr("Цель, слов", "Goal, words"), style = T.small, color = p.mute); Gap(4.dp); NumField(goal, "80000", Modifier.fillMaxWidth()) { goal = it } }
            Column(Modifier.weight(1f)) { Text(tr("Глав в плане", "Planned chapters"), style = T.small, color = p.mute); Gap(4.dp); NumField(planned, "0", Modifier.fillMaxWidth()) { planned = it } }
        }
        Gap(8.dp); Text(tr("Написано до Клавишки (слов) – учтётся в книге, но жителей не накормит", "Written before Keyling (words) – counts for the book, feeds nobody"), style = T.small, color = p.mute)
        Gap(4.dp); NumField(start, "0", Modifier.fillMaxWidth()) { start = it }
        Gap(14.dp)
        BigButton(tr("Сохранить", "Save"), enabled = title.isNotBlank()) {
            c.write {
                val b = book ?: return@write
                dao.update(b.copy(title = title.trim(), goal = goal.toIntOrNull()?.coerceAtLeast(1000) ?: b.goal, planned = planned.toIntOrNull() ?: 0, startWords = b.startWords + (start.toIntOrNull() ?: 0)))
                reconcile(0, false, false)
            }; onClose()
        }
        Gap(18.dp)
        Text(tr("Другие книги", "Other books"), style = T.sec, color = p.mute); Gap(6.dp)
        for (b in st.books.filter { it.id != book?.id }) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { c.write { settings.update { it.copy(book = b.id) } }; onClose() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp, 34.dp).clip(RoundedCornerShape(3.dp)).background(COVERS[b.color % COVERS.size]))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(b.title, style = T.bodyB, color = p.ink); Text("${fmt(st.bookWords(b))} ${tr("слов", "words")} · " + statusName(b.status), style = T.small, color = p.mute) }
        }
        Gap(8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("+ " + tr("Новая книга", "New book"), false, Modifier.weight(1f)) { c.write { val id = dao.insert(Book(title = tr("Новая книга", "New book"), created = today(), color = st.books.size)); settings.update { it.copy(book = id) } }; onClose() }
            if (book != null && book.status != "done") Chip("✓ " + tr("Черновик дописан", "Draft finished"), false, Modifier.weight(1f)) {
                c.write { dao.update(book.copy(status = "done", finished = today())); reconcile(100, false, false); news.tryEmit(tr("Конец! Книга переплетена · +100 буквиц", "The End! The book is bound · +100 coins")) }; onClose() }
            else if (book != null) Chip(tr("Новая редакция", "New draft"), false, Modifier.weight(1f)) { c.write { dao.update(book.copy(status = "active", drafts = book.drafts + 1, finished = 0)); reconcile(0, false, false) }; onClose() }
        }
    }
}

val COVERS = listOf(androidx.compose.ui.graphics.Color(0xFF2F4F8F), androidx.compose.ui.graphics.Color(0xFF3F7A6A), androidx.compose.ui.graphics.Color(0xFF7A4A8A),
    androidx.compose.ui.graphics.Color(0xFFB0583A), androidx.compose.ui.graphics.Color(0xFFC8913A), androidx.compose.ui.graphics.Color(0xFF4A5560))
fun statusName(s: String) = when (s) { "done" -> tr("дописана", "finished"); "paused" -> tr("на паузе", "paused"); else -> tr("в работе", "in progress") }
