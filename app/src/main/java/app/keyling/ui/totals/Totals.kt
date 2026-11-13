package app.keyling.ui.totals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.keyling.container
import app.keyling.data.Settings
import app.keyling.domain.Ach
import app.keyling.domain.Achievements
import app.keyling.domain.CHARS_PER_WORD
import app.keyling.domain.CLASSICS
import app.keyling.domain.Lang
import app.keyling.domain.Milestones
import app.keyling.domain.PAGE
import app.keyling.domain.SHEET
import app.keyling.domain.Stats
import app.keyling.domain.bugName
import app.keyling.domain.daysW
import app.keyling.domain.fmt
import app.keyling.domain.fmtDay
import app.keyling.domain.tr
import app.keyling.domain.words
import app.keyling.ui.Share
import app.keyling.ui.components.Bar
import app.keyling.ui.components.Bars
import app.keyling.ui.components.Card
import app.keyling.ui.components.Gap
import app.keyling.ui.components.Icon
import app.keyling.ui.components.Keycap
import app.keyling.ui.components.Medal
import app.keyling.ui.components.Pill
import app.keyling.ui.components.Row2
import app.keyling.ui.components.Sec
import app.keyling.ui.components.Tabs
import app.keyling.ui.home.BigButton
import app.keyling.ui.home.BookSheet
import app.keyling.ui.home.LogSheet
import app.keyling.ui.home.COVERS
import app.keyling.ui.home.NumField
import app.keyling.ui.home.Sheet
import app.keyling.ui.home.statusName
import app.keyling.ui.theme.K
import app.keyling.ui.theme.T
import java.time.LocalDate
import kotlin.math.max

@Composable
fun TotalsScreen(st: Stats, s: Settings, nav: app.keyling.ui.Nav) {
    val ctx = LocalContext.current; val p = K.pal
    var tab by rememberSaveable { mutableIntStateOf(app.keyling.ui.Start.sub) }
    var sheet by remember { mutableStateOf<Any?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 18.dp).padding(top = 22.dp, bottom = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Итоги", "Totals"), style = T.title, color = p.ink, modifier = Modifier.weight(1f))
            Keycap(Modifier.size(44.dp), onClick = { nav.card = app.keyling.ui.Shareable(listOf(
                tr("Год", "Year") to { Share.year(ctx, st, s) }, tr("Месяц", "Month") to { Share.month(ctx, st) }, tr("Неделя", "Week") to { Share.week(ctx, st) },
                tr("Книга", "Book") to { Share.book(ctx, st, s) }, tr("Серия", "Streak") to { Share.streak(ctx, st) }, tr("Календарь", "Calendar") to { Share.calendar(ctx, st) },
                tr("Рекорды", "Records") to { Share.records(ctx, st) })) }) { Icon("upload", p.kLeg, 20.dp) }
        }
        Gap(16.dp)
        Tabs(listOf(tr("Год", "Year"), tr("Книги", "Books"), tr("Рекорды", "Records"), tr("Награды", "Awards")), tab) { tab = it }
        when (tab) {
            0 -> Year(st, s) { sheet = "goal" }
            1 -> Books(st, s, onLog = { sheet = "log" }) { sheet = "book" }
            2 -> Records(st)
            else -> Awards(st) { sheet = it }
        }
    }
    when (val sh = sheet) {
        "goal" -> GoalSheet(s) { sheet = null }
        "book" -> BookSheet(st, s) { sheet = null }
        "log" -> LogSheet(st) { sheet = null }
        is Ach -> Sheet({ sheet = null }) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Medal(sh.icon, sh.tier, sh.done, sh.value.toFloat() / sh.target, 110.dp) }
            Gap(12.dp); Text(sh.name, style = T.h2, color = p.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(sh.about, style = T.body, color = p.mute, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Gap(12.dp)
            Row2(tr("Уровень", "Tier"), when (sh.tier) { 'g' -> tr("золото", "gold"); 's' -> tr("серебро", "silver"); else -> tr("бронза", "bronze") })
            Row2(tr("Награда", "Reward"), "${sh.reward} " + tr("буквиц", "coins"))
            Row2(tr("Прогресс", "Progress"), if (sh.done) tr("получено", "earned") else if (sh.secret) tr("секрет", "secret") else "${fmt(sh.value)} / ${fmt(sh.target)}", last = true)
            if (sh.done) { Gap(14.dp); BigButton(tr("Поделиться", "Share")) { nav.card = app.keyling.ui.Shareable(listOf(sh.name to { Share.achievement(ctx, sh) })); sheet = null } }
        }
    }
}

@Composable
private fun Year(st: Stats, s: Settings, onGoal: () -> Unit) {
    val p = K.pal; val goal = s.yearGoal; val (should, forecast, need) = st.pace(goal)
    Sec(tr("Цель на ${st.year} год", "Goal for ${st.year}"))
    Card {
        Column {
            Row(verticalAlignment = Alignment.Bottom) { Text(fmt(st.yearTotal), style = T.big, color = p.ink); Spacer(Modifier.width(8.dp)); Text(tr("из ", "of ") + fmt(goal) + tr(" слов", " words"), style = T.body, color = p.mute, modifier = Modifier.padding(bottom = 5.dp)) }
            Gap(12.dp)
            Box(Modifier.fillMaxWidth().height(22.dp)) {
                Bar(st.yearTotal.toFloat() / goal, Modifier.align(Alignment.Center), height = 12.dp)
                Canvas(Modifier.fillMaxWidth().height(22.dp)) { val x = size.width * should / goal; drawRoundRect(p.ink, Offset(x - 1.5f, 0f), Size(3f, size.height), CornerRadius(2f)) }
            }
            Gap(10.dp)
            val diff = st.yearTotal - should
            Text((if (diff >= 0) tr("Вы опережаете график на ${fmt(diff)} слов. ", "You're ${fmt(diff)} words ahead of pace. ") else tr("До графика не хватает ${fmt(-diff)} слов. ", "${fmt(-diff)} words behind pace. ")) +
                    tr("В том же темпе к 31 декабря будет около ${fmt(forecast)}. ", "At this pace you'll have about ${fmt(forecast)} by 31 December. ") +
                    (if (need > 0) tr("Чтобы точно успеть – ${fmt(need)} слов в день.", "To make it, ${fmt(need)} words a day.") else tr("Цель года взята!", "Year goal reached!")), style = T.body, color = p.mute)
            Gap(12.dp)
            Text(tr("Изменить цель", "Change the goal"), style = T.bodyB, color = p.acc, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onGoal() }.padding(vertical = 4.dp))
        }
    }
    Sec(tr("Календарь письма", "Writing calendar"))
    Card { Heatmap(st) }
    Sec(tr("По месяцам", "By month"))
    Card { val mNames = if (Lang.ru) listOf("Я", "Ф", "М", "А", "М", "И", "И", "А", "С", "О", "Н", "Д") else listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
        val cur = LocalDate.ofEpochDay(st.today).monthValue - 1
        Bars(st.months.toList(), mNames, { it <= cur }) }
    Sec(tr("Когда пишется", "When you write"))
    Card { Column {
        val peak = st.hours.indices.maxByOrNull { st.hours[it] } ?: 20
        Bars(st.hours.toList(), List(24) { if (it % 6 == 0) "$it" else "" }, { kotlin.math.abs(it - peak) <= 2 }, height = 64.dp)
        Text(when (peak) { in 5..10 -> tr("Вы утренний писатель – лучшие слова приходят до обеда.", "You're a morning writer: the best words come before noon.")
            in 11..17 -> tr("Вы дневной писатель – пишете при свете.", "You're a daytime writer.")
            in 18..23 -> tr("Вы вечерний писатель. Полуночник уже присматривается.", "You're an evening writer. Night Owl is watching.")
            else -> tr("Вы ночной писатель – Полуночник доволен.", "You're a night writer – Night Owl approves.") }, style = T.small, color = p.mute)
    } }
    Sec(tr("По дням недели, в среднем", "Average by weekday"))
    Card { val wd = st.weekdays; val names = if (Lang.ru) listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс") else listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
        val best = wd.indices.maxByOrNull { wd[it] } ?: 0; Bars(wd.toList(), names, { it == best }, top = { fmt(wd[it]) }) }
    Sec(tr("Всего за год", "This year"))
    val items = listOf(fmt(st.yearTotal) to tr("слов", "words"), String.format("%.1f", st.yearTotal * CHARS_PER_WORD / SHEET) to tr("авторских листов", "author's sheets"),
        "${st.activeDays}" to tr("дней с письмом", "writing days"), fmt(st.avgPerDay) to tr("слов в день письма", "words per writing day"),
        "${st.editMin / 60}" to tr("часов правки", "hours of editing"), "${st.sprintCount}" to tr("спринтов", "sprints"),
        fmt((st.yearTotal * CHARS_PER_WORD / PAGE).toInt()) to tr("страниц", "pages"), "${st.chapters}" to tr("глав закончено", "chapters done"), "${st.caught.size}" to tr("бесов поймано", "bugs caught"))
    for (row in items.chunked(3)) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for ((v, l) in row) Card(Modifier.weight(1f), pad = 12.dp) { Column { Text(v, style = T.num, color = p.ink); Text(l, style = T.tiny, color = p.mute) } } }; Gap(8.dp) }
}

@Composable
private fun Heatmap(st: Stats) {
    val p = K.pal; val start = LocalDate.of(st.year, 1, 1); val off = start.dayOfWeek.value - 1
    Column {
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val cols = 53; val cell = size.width / cols; val sq = cell - 1.5f
            st.yearDays.forEachIndexed { i, w ->
                val idx = i + off; val x = (idx / 7) * cell; val y = (idx % 7) * (size.height / 7)
                val col = when { i > st.dayOfYear -> p.heat[0].copy(alpha = .45f); w == 0 -> p.heat[0]; w < 500 -> p.heat[1]; w < 900 -> p.heat[2]; w < 1500 -> p.heat[3]; else -> p.heat[4] }
                drawRoundRect(col, Offset(x, y), Size(sq, size.height / 7 - 1.5f), CornerRadius(2f))
                if (i == st.dayOfYear) drawRoundRect(p.ink, Offset(x, y), Size(sq, size.height / 7 - 1.5f), CornerRadius(2f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            }
        }
        Gap(8.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("меньше", "less"), style = T.tiny, color = p.mute); Spacer(Modifier.width(4.dp))
            for (c in p.heat) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(c)); Spacer(Modifier.width(3.dp)) }
            Text(tr("больше", "more"), style = T.tiny, color = p.mute)
        }
    }
}

@Composable
private fun Books(st: Stats, s: Settings, onLog: () -> Unit, onEdit: () -> Unit) {
    val p = K.pal; val book = st.books.firstOrNull { it.id == s.book } ?: st.books.firstOrNull() ?: return
    val w = st.bookWords(book); val chars = (w * CHARS_PER_WORD).toInt()
    Sec(tr("Сейчас пишется", "Writing now"))
    Card(onClick = onEdit) { Column {
        Text(book.title, style = T.bookTitle, color = p.ink); Text(statusName(book.status), style = T.small, color = p.mute)
        Gap(10.dp); Bar(w.toFloat() / book.goal, height = 10.dp); Gap(6.dp)
        Text("${fmt(w)} ${tr("из", "of")} ${fmt(book.goal)} ${tr("слов", "words")}", style = T.small, color = p.mute)
        Gap(12.dp)
        val per = max(1, st.avgPerDay); val left = max(0, book.goal - w); val finish = st.today + (left + per - 1) / per
        for (row in listOf(listOf(String.format("%.1f", chars.toFloat() / SHEET) to tr("авторских листов", "author's sheets"), fmt(chars / PAGE) to tr("страниц", "pages"), "${st.bookChapters(book)}" to tr("глав", "chapters")),
            listOf("${st.bookDays(book)}" to tr("дней в работе", "days of work"), "${st.bookEdit(book) / 60} " + tr("ч", "h") to tr("правки", "of editing"), (if (left == 0) "✓" else fmtDay(finish)) to tr("прогноз финиша", "forecast finish")))) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for ((v, l) in row) Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(p.surface2).padding(10.dp)) { Text(v, style = T.bodyB, color = p.ink); Text(l, style = T.tiny, color = p.mute) } }
            Gap(8.dp) }
    } }
    Gap(10.dp)
    Card(onClick = onLog) { Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(tr("Мои записи", "My log"), style = T.bodyB, color = p.ink)
            Text(tr("Все записи по дням: исправить число или удалить", "Every sitting by day: fix a number or delete it"), style = T.small, color = p.mute) }
        Text("›", style = T.bodyB, color = p.mute) } }
    Sec(tr("Все книги", "All books"))
    Card(pad = 12.dp) { Column { st.books.forEachIndexed { i, b ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp, 46.dp).clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 6.dp, bottomEnd = 6.dp)).background(COVERS[b.color % COVERS.size]))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(b.title, style = T.bodyB, color = p.ink); Text(statusName(b.status) + " · " + words(st.bookWords(b)) + (if (b.drafts > 1) tr(" · ${b.drafts}-я редакция", " · draft ${b.drafts}") else ""), style = T.small, color = p.mute)
                Gap(5.dp); Bar(st.bookWords(b).toFloat() / b.goal, height = 4.dp) }
        } } } }
    Sec(tr("Уже длиннее, чем…", "Already longer than…"))
    Card(pad = 12.dp) { Column { CLASSICS.forEachIndexed { i, (ru, en, n) -> Row2(tr(ru, en), if (w >= n) "✓ " + fmt(n) else tr("ещё ", "") + fmt(n - w) + tr("", " to go"), last = i == CLASSICS.lastIndex) } } }
    Sec(tr("Банка бесов-опечаток", "The typo-bug jar"))
    Card { Column {
        Text(tr("Поймано: ${st.caught.size}. Ловите их между клавишами на столе.", "Caught: ${st.caught.size}. Catch them between the keys on the desk."), style = T.small, color = p.mute); Gap(8.dp)
        val counts = st.caught.groupingBy { it.kind }.eachCount()
        for (row in counts.entries.chunked(2)) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { for ((k, n) in row) Pill("${bugName(k)} ×$n", p.surface2, p.ink) }; Gap(6.dp) }
    } }
}

@Composable
private fun Records(st: Stats) {
    val p = K.pal
    Sec(tr("Личные рекорды", "Personal records"))
    Card(pad = 12.dp) { Column {
        val best = st.bestDay; val (bw, bwEnd) = st.bestWeek; val (lg, lgEnd) = st.longest
        val bm = st.months.indices.maxByOrNull { st.months[it] } ?: 0
        val monthNames = if (Lang.ru) listOf("январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь") else listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val rows = listOfNotNull(
            best?.let { Triple("fire", tr("Лучший день", "Best day") to fmtDay(it.day), words(it.words)) },
            Triple("cal", tr("Лучшая неделя", "Best week") to (fmtDay(bwEnd - 6) + " – " + fmtDay(bwEnd)), words(bw)),
            Triple("cal", tr("Лучший месяц", "Best month") to monthNames[bm], words(st.months[bm])),
            Triple("star", tr("Самая длинная серия", "Longest streak") to tr("до ", "until ") + fmtDay(lgEnd), daysW(lg)),
            Triple("sparkle", tr("Текущая серия", "Current streak") to tr("не прерывайте!", "keep it going!"), daysW(st.streak)),
            st.s.bestSprint.takeIf { it > 0 }?.let { Triple("clock", tr("Лучший спринт", "Best sprint") to tr("призрак", "the ghost"), tr("${it / 10} слов/мин", "${it / 10} wpm")) },
            st.latestWord?.let { (m, d) -> Triple("moon", tr("Самое позднее слово", "Latest word") to fmtDay(d), "%02d:%02d".format(m / 60, m % 60)) },
            st.biggestCut.takeIf { it > 0 }?.let { Triple("cut", tr("Больше всего вырезано", "Biggest cut") to tr("за одну правку", "in one go"), "−" + fmt(it)) },
        )
        rows.forEachIndexed { i, (icon, lr, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(p.accSoft), contentAlignment = Alignment.Center) { Icon(icon, p.acc, 19.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(lr.first, style = T.body, color = p.ink); Text(lr.second, style = T.small, color = p.mute) }
                Text(v, style = T.bodyB, color = p.ink)
            }
        }
    } }
    Sec(tr("Ваш путь", "Your road"))
    Card { Column { val road = Milestones.road(st)
        road.forEachIndexed { i, m -> Row(Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(22.dp)) {
                Box(Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).background(if (m.day != null) p.acc else if (m.progress > 0) p.warm else p.dim))
                if (i < road.lastIndex) Box(Modifier.width(2.dp).height(44.dp).background(p.line))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).padding(bottom = 10.dp)) {
                Text(m.title, style = if (m.day != null) T.bodyB else T.body, color = if (m.day != null || m.progress > 0) p.ink else p.mute)
                Text(listOfNotNull(m.day?.let { fmtDay(it) }, m.note.takeIf { it.isNotEmpty() }).joinToString(" · "), style = T.small, color = p.mute)
                if (m.day == null && m.progress > 0) { Gap(5.dp); Bar(m.progress, color = p.warm, height = 5.dp) }
            }
        } }
    } }
}

@Composable
private fun Awards(st: Stats, onOpen: (Ach) -> Unit) {
    val p = K.pal; val all = Achievements.all(st)
    Gap(14.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.weight(1f), pad = 12.dp) { Column { Text("${all.count { it.done }} / ${all.size}", style = T.num, color = p.ink); Text(tr("наград получено", "awards earned"), style = T.tiny, color = p.mute) } }
        Card(Modifier.weight(1f), pad = 12.dp) { Column { Text(fmt(all.filter { it.done }.sumOf { it.reward }), style = T.num, color = p.ink); Text(tr("буквиц за награды", "coins from awards"), style = T.tiny, color = p.mute) } }
    }
    for ((cat, list) in all.groupBy { it.cat }) {
        Sec("$cat · ${list.count { it.done }} ${tr("из", "of")} ${list.size}")
        for (row in list.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (a in row) Card(Modifier.weight(1f), pad = 10.dp, onClick = { onOpen(a) }) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Medal(if (a.secret && !a.done) "q" else a.icon, a.tier, a.done, a.value.toFloat() / a.target)
                        Gap(8.dp); Text(if (a.secret && !a.done) "???" else a.name, style = T.smallB, color = if (a.done) p.ink else p.mute, textAlign = TextAlign.Center, maxLines = 2)
                        Text(if (a.done) "✓" else if (a.secret) tr("секрет", "secret") else "${fmt(minOf(a.value, a.target))} / ${fmt(a.target)}", style = T.tiny, color = p.mute)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Gap(10.dp)
        }
    }
}

@Composable
private fun GoalSheet(s: Settings, onClose: () -> Unit) {
    val c = LocalContext.current.container; val p = K.pal; var v by remember { mutableStateOf(s.yearGoal.toString()) }
    Sheet(onClose) {
        Text(tr("Цель на год", "Year goal"), style = T.h2, color = p.ink); Gap(10.dp)
        NumField(v, "250000", Modifier.fillMaxWidth()) { v = it }; Gap(8.dp)
        Text(tr("Для справки: NaNoWriMo – это 50 000 слов за месяц; роман обычно 70–100 тысяч.", "For reference: NaNoWriMo is 50,000 words a month; a novel is usually 70–100k."), style = T.small, color = p.mute)
        Gap(14.dp); BigButton(tr("Сохранить", "Save"), enabled = (v.toIntOrNull() ?: 0) >= 1000) { c.write { settings.update { it.copy(yearGoal = v.toInt()) } }; onClose() }
    }
}
