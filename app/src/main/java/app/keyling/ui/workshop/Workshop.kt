package app.keyling.ui.workshop

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.keyling.container
import app.keyling.currentLanguage
import app.keyling.data.Backup
import app.keyling.data.Mug
import app.keyling.data.Settings
import app.keyling.domain.Shop
import app.keyling.domain.Stats
import app.keyling.domain.fmt
import app.keyling.domain.tr
import app.keyling.setLanguage
import app.keyling.ui.Nav
import app.keyling.ui.art.Board
import app.keyling.ui.art.Buf
import app.keyling.ui.art.CapSets
import app.keyling.ui.art.Decor
import app.keyling.ui.art.Desk
import app.keyling.ui.art.Scene
import app.keyling.ui.components.Card
import app.keyling.ui.components.Click
import app.keyling.ui.components.Gap
import app.keyling.ui.components.Icon
import app.keyling.ui.components.Keycap
import app.keyling.ui.components.Pixels
import app.keyling.ui.components.Sec
import app.keyling.ui.components.Tabs
import app.keyling.ui.home.Chip
import app.keyling.ui.home.NumField
import app.keyling.ui.home.Sheet
import app.keyling.ui.theme.K
import app.keyling.ui.theme.T
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val CAT_ICON = mapOf("desk" to "surface", "mug" to "mug", "plant" to "plant", "lamp" to "lamp", "mascot" to "sparkle")
private val SET_NAMES = mapOf("retro" to ("Бежевое ретро" to "Beige retro"), "pastel" to ("Пастель" to "Pastel"), "night" to ("Ночной город" to "Night city"),
    "walnut" to ("Ореховое дерево" to "Walnut"), "sea" to ("Морской бриз" to "Sea breeze"))
private val SW_NAMES get() = mapOf("blue" to Triple("Синие", "Blue", tr("Звонкий клик. Слышно, что работа идёт.", "A crisp click. You can hear the work.")),
    "brown" to Triple("Коричневые", "Brown", tr("Мягкий тактильный щелчок.", "A soft tactile bump.")), "red" to Triple("Красные", "Red", tr("Тихие, линейные. Для ночного письма.", "Quiet and linear. For writing at night.")))

/** Something being tried on before it's bought. */
private data class Trial(val cat: String, val id: String, val price: Int, val name: String)

@Composable
fun WorkshopScreen(st: Stats, s: Settings, nav: Nav) {
    val ctx = LocalContext.current; val c = ctx.container; val p = K.pal; val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(app.keyling.ui.Start.shop) }
    var cat by rememberSaveable { mutableStateOf("mug") }
    var trial by remember { mutableStateOf<Trial?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    LaunchedEffect(nav.shopCat) { nav.shopCat?.let { cat = it; tab = 0; nav.shopCat = null } }
    var custom by remember { mutableStateOf(Mug.load(ctx)) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
            val w = src.width; val h = src.height; val k = maxOf(28f / w, 33f / h)
            val sc = Bitmap.createScaledBitmap(src, (w * k).roundToInt().coerceAtLeast(28), (h * k).roundToInt().coerceAtLeast(33), true)
            val crop = Bitmap.createBitmap(sc, (sc.width - 28) / 2, (sc.height - 33) / 2, 28, 33)
            val px = IntArray(28 * 33); crop.getPixels(px, 0, 28, 0, 0, 28, 33)
            val mug = Desk.posterize(px); Mug.save(ctx, mug); custom = mug
            c.settings.update { it.copy(mug = "custom") }; c.reconcile(0, false, false)
            c.news.tryEmit(tr("Ваша кружка теперь на столе", "Your mug is on the desk now"))
        }
    }
    DisposableEffect(Unit) { onDispose { trial = null } }

    // the effective look: what's owned and set, with the trial on top
    val decor = Decor(s.desk, s.mug, s.plant, s.lamp, s.mascot).let { d -> trial?.let { t -> when (t.cat) {
        "desk" -> d.copy(desk = t.id); "mug" -> d.copy(mug = t.id); "plant" -> d.copy(plant = t.id); "lamp" -> d.copy(lamp = t.id); "mascot" -> d.copy(mascot = t.id); else -> d } } ?: d }
    val capsId = trial?.takeIf { it.cat == "caps" }?.id ?: s.caps
    val set = CapSets.of(capsId)
    val bgK = c.art.bgKey(decor, set, custom); val capsK = c.art.capsKey(set, st.activeDays)
    val bg by androidx.compose.runtime.produceState<Buf?>(c.art.peekBg(bgK), bgK) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { c.art.bg(decor, set, custom) } }
    val caps by androidx.compose.runtime.produceState<Map<app.keyling.ui.art.Key, Board.Caps>?>(c.art.peekCaps(capsK) ?: c.art.peekAnyCaps(set), capsK) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { c.art.caps(set, st.activeDays) } }
    val scene = remember { Scene() }
    val frame = remember { Buf(Desk.W, Desk.H, Desk.S) }; val bmp = remember { Bitmap.createBitmap(frame.pw, frame.ph, Bitmap.Config.ARGB_8888) }; val img = remember(bmp) { bmp.asImageBitmap() }
    var tick by remember { mutableIntStateOf(0) }
    val spK = c.art.spritesKey(decor, custom)
    val sprites by androidx.compose.runtime.produceState(c.art.peekSprites(spK) ?: emptyList(), spK) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { c.art.sprites(decor, custom) } }
    scene.sprites = sprites; scene.offsets = s.pos
    scene.pet = s.pet; scene.steam = decor.mug != "thermo"; scene.candle = decor.mascot == "candle"
    LaunchedEffect(Unit) { var last = 0L
        while (true) withFrameNanos { ns -> if (last == 0L) last = ns; val dt = (ns - last) / 1e9f
            if (dt >= 1 / 30f) { last = ns; scene.step(dt); val b = bg; val k = caps; if (b != null) { scene.render(frame, b, k ?: emptyMap(), set.plate, 60); bmp.setPixels(frame.d, 0, frame.pw, 0, 0, frame.pw, frame.ph) }; tick++ } } }

    fun has(key: String, price: Int) = price == 0 || key in s.owned
    fun pick(cat: String, id: String, price: Int, name: String) {
        if (has("$cat:$id", price)) { trial = null; c.write { settings.update { when (cat) {
            "desk" -> it.copy(desk = id); "mug" -> it.copy(mug = id); "plant" -> it.copy(plant = id); "lamp" -> it.copy(lamp = id); "mascot" -> it.copy(mascot = id); "caps" -> it.copy(caps = id); else -> it } } } }
        else trial = Trial(cat, id, price, name)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 18.dp).padding(top = 22.dp, bottom = if (trial != null) 110.dp else 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Мастерская", "Workshop"), style = T.title, color = p.ink, modifier = Modifier.weight(1f))
                Row(Modifier.clip(RoundedCornerShape(20.dp)).background(p.warmSoft).clickable { c.news.tryEmit(tr("Буквица за каждые 25 слов, 20 за дневную норму, 50 за главу, ещё – за награды", "A coin per 25 words, 20 for the daily goal, 50 per chapter, more for awards")) }
                    .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Coin(); Spacer(Modifier.width(6.dp)); Text(fmt(s.coins), style = T.bodyB, color = p.ink) }
                Spacer(Modifier.width(8.dp))
                Keycap(Modifier.size(42.dp), onClick = { showSettings = true }) { Icon("gear", p.kLeg, 20.dp) }
            }
            Gap(16.dp)
            Tabs(listOf(tr("Стол", "Desk"), tr("Клавиатура", "Keyboard"), tr("Звук", "Sound")), tab) { trial = null; tab = it }
            if (tab < 2) {
                Gap(14.dp)
                val y0 = if (tab == 1) 302 else 58; val h = if (tab == 1) 118 else 196
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, p.line, RoundedCornerShape(18.dp))) {
                    Canvas(Modifier.fillMaxWidth().aspectRatio(Desk.W / h.toFloat())) { @Suppress("UNUSED_EXPRESSION") tick
                        drawImage(img, IntOffset(0, y0 * Desk.S), IntSize(frame.pw, h * Desk.S), IntOffset.Zero, IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.None) }
                    Text(trial?.let { tr("Примерка: ", "Trying: ") + it.name } ?: if (tab == 1) tr("Ваша клавиатура", "Your keyboard") else tr("Ваш стол", "Your desk"),
                        style = T.smallB, color = if (trial != null) Color(0xFF2A1A05) else p.ink,
                        modifier = Modifier.padding(10.dp).clip(RoundedCornerShape(20.dp)).background(if (trial != null) p.warm else p.surface.copy(alpha = .9f)).padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            when (tab) {
                0 -> {
                    Gap(12.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (k in listOf("desk", "mug", "plant", "lamp", "mascot")) {
                            val on = cat == k
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) p.ink else p.track).clickable { c.sound.click(Click.sw); trial = null; cat = k }.padding(vertical = 9.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(CAT_ICON.getValue(k), if (on) p.bg else p.mute, 18.dp)
                                Text(when (k) { "desk" -> tr("Стол", "Desk"); "mug" -> tr("Кружка", "Mug"); "plant" -> tr("Цветок", "Plant"); "lamp" -> tr("Лампа", "Lamp"); else -> tr("Талисман", "Charm") },
                                    style = T.tiny, color = if (on) p.bg else p.mute, maxLines = 1)
                            }
                        }
                    }
                    Gap(12.dp)
                    val items = Shop.items.filter { it.cat == cat }
                    for (row in items.chunked(3)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (it in row) {
                                val current = when (cat) { "desk" -> s.desk; "mug" -> s.mug; "plant" -> s.plant; "lamp" -> s.lamp; else -> s.mascot } == it.id
                                val trying = trial?.cat == cat && trial?.id == it.id
                                val prev = remember(it.key, s.desk, custom) { Desk.itemPreview(cat, it.id, s.desk, custom) }
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(p.surface)
                                    .border(2.dp, if (trying) p.warm else if (current) p.acc else p.line, RoundedCornerShape(16.dp))
                                    .clickable { c.sound.click(Click.sw)
                                        if (it.id == "custom") { trial = null; if (custom == null || current) pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) else pick("mug", "custom", 0, it.name) }
                                        else pick(cat, it.id, it.price, it.name) }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.clip(RoundedCornerShape(10.dp))) { Pixels(prev, version = custom) }
                                    Gap(6.dp); Text(it.name, style = T.smallB, color = p.ink, textAlign = TextAlign.Center, maxLines = 2)
                                    Gap(3.dp); Badge(current && !trying, if (it.id == "custom") true else has(it.key, it.price), it.price, s.coins,
                                        if (it.id == "custom") (if (custom == null) tr("Загрузить фото", "Upload photo") else if (current) tr("Сменить фото", "New photo") else null) else null)
                                    Gap(4.dp)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Gap(8.dp)
                    }
                }
                1 -> {
                    Gap(12.dp)
                    for (row in CapSets.all.chunked(2)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (cs in row) { val name = SET_NAMES.getValue(cs.id).let { tr(it.first, it.second) }; val on = s.caps == cs.id; val trying = trial?.cat == "caps" && trial?.id == cs.id
                                val pv = remember(cs.id) { Board.preview(cs, Desk.S) }
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(p.surface).border(2.dp, if (trying) p.warm else if (on) p.acc else p.line, RoundedCornerShape(16.dp))
                                    .clickable { c.sound.click(Click.sw); pick("caps", cs.id, cs.price, tr("набор «$name»", "the $name set")) }.padding(8.dp)) {
                                    Box(Modifier.clip(RoundedCornerShape(9.dp))) { Pixels(pv) }
                                    Gap(6.dp); Row(verticalAlignment = Alignment.CenterVertically) { Text(name, style = T.smallB, color = p.ink, modifier = Modifier.weight(1f))
                                        Badge(on && !trying, has("caps:${cs.id}", cs.price), cs.price, s.coins, null) }
                                } }
                            repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Gap(10.dp)
                    }
                    Text(tr("Колпачки стираются от письма: чаще всего О, Е, А и пробел. Чем больше дней с письмом, тем ярче блестят.",
                        "Keycaps wear from writing: О, Е, А and space go first. The more writing days, the brighter they shine."), style = T.small, color = p.mute)
                }
                else -> {
                    Gap(12.dp)
                    Card(pad = 6.dp) { Column {
                        for ((id, price, _) in Shop.switches) { val (ru, en, about) = SW_NAMES.getValue(id); val on = s.sw == id; val owned = price == 0 || "sw:$id" in s.owned
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                if (owned) c.write { settings.update { it.copy(sw = id) } } else scope.launch { if (c.buy("sw:$id", price)) c.settings.update { it.copy(sw = id) } else c.news.tryEmit(tr("Не хватает ${price - s.coins} буквиц", "${price - s.coins} coins short")) }
                                c.sound.click(id) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(p.surface2).clickable { listOf(0L, 140L, 260L, 420L).forEach { d -> android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ c.sound.click(id) }, d) } },
                                    contentAlignment = Alignment.Center) { Icon("play", p.ink, 16.dp) }
                                Spacer(Modifier.width(12.dp))
                                Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(when (id) { "blue" -> Color(0xFF3F7FD8); "brown" -> Color(0xFF8A5A34); else -> Color(0xFFD0443A) }))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) { Text(tr(ru, en), style = T.bodyB, color = p.ink); Text(about, style = T.small, color = p.mute) }
                                Badge(on, owned, price, s.coins, if (!on && owned) tr("Поставить", "Use") else null)
                            } }
                    } }
                    Gap(10.dp)
                    Text(tr("Записи настоящих переключателей – из проекта kbsim (MIT). Звук играет на всех клавишах интерфейса.",
                        "Recordings of real switches from the kbsim project (MIT). Every key in the app plays it."), style = T.small, color = p.mute)
                }
            }
        }
        // try-before-you-buy bar
        trial?.let { t -> val ok = s.coins >= t.price
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(p.surface).border(1.dp, p.line).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.name.replaceFirstChar { it.uppercase() }, style = T.bodyB, color = p.ink, maxLines = 1)
                    Text(if (ok) tr("Примерка · останется ${fmt(s.coins - t.price)}", "Trying on · ${fmt(s.coins - t.price)} left after") else tr("Не хватает ${fmt(t.price - s.coins)} – это около ${fmt((t.price - s.coins) * 25)} слов", "${fmt(t.price - s.coins)} short – about ${fmt((t.price - s.coins) * 25)} words"),
                        style = T.small, color = p.mute)
                }
                Spacer(Modifier.width(8.dp))
                Keycap(Modifier.width(84.dp).height(46.dp), onClick = { trial = null }) { Text(tr("Снять", "Undo"), style = T.bodyB, color = p.kLeg) }
                Spacer(Modifier.width(8.dp))
                Keycap(Modifier.width(120.dp).height(46.dp), accent = ok, key = "enter", onClick = {
                    if (ok) scope.launch { if (c.buy("${t.cat}:${t.id}", t.price)) { c.settings.update { when (t.cat) {
                        "desk" -> it.copy(desk = t.id); "mug" -> it.copy(mug = t.id); "plant" -> it.copy(plant = t.id); "lamp" -> it.copy(lamp = t.id); "mascot" -> it.copy(mascot = t.id); "caps" -> it.copy(caps = t.id); else -> it } }
                        c.news.tryEmit(tr("Куплено: ", "Bought: ") + t.name); trial = null } } }) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("Купить", "Buy") + " · ${t.price}", style = T.bodyB, color = if (ok) p.xLeg else p.mute) } }
            }
        }
    }
    if (showSettings) SettingsSheet(s) { showSettings = false }
}

@Composable
private fun Coin(size: androidx.compose.ui.unit.Dp = 20.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(5.dp)).background(Brush.verticalGradient(listOf(Color(0xFFF6CF78), Color(0xFFD9962E)))), contentAlignment = Alignment.Center) {
        Text(tr("Б", "K"), style = T.smallB.copy(fontFamily = app.keyling.ui.theme.Lora), color = Color(0xFF4A2A05))
    }
}

@Composable
private fun Badge(on: Boolean, has: Boolean, price: Int, coins: Int, label: String?) {
    val p = K.pal
    val (text, bg, fg) = when {
        on -> Triple("✓ " + tr("Стоит", "In use"), p.accSoft, p.acc)
        label != null -> Triple(label, p.surface2, p.mute)
        has -> Triple(tr("Есть", "Owned"), Color.Transparent, p.mute)
        else -> Triple("$price", if (coins >= price) p.warmSoft else p.track, if (coins >= price) p.warm else p.mute)
    }
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 7.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!on && !has && label == null) { Coin(12.dp); Spacer(Modifier.width(4.dp)) }
        Text(text, style = T.smallB.copy(fontSize = T.tiny.fontSize), color = fg, maxLines = 1)
    }
}

/** Theme, language, counting, the daily goal and moving progress to another phone. */
@Composable
private fun SettingsSheet(s: Settings, onClose: () -> Unit) {
    val ctx = LocalContext.current; val c = ctx.container; val p = K.pal; val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch { Backup.export(ctx, c.dao, c.settings.now(), uri); c.news.tryEmit(tr("Файл сохранён. Откройте его в Клавишке на другом телефоне", "Saved. Open it in Keyling on the other phone")) } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { val ok = Backup.import(ctx, c.dao, c.settings, uri)
            c.news.tryEmit(if (ok) tr("Прогресс перенесён", "Progress moved over") else tr("Это не файл Клавишки", "That's not a Keyling file")); onClose() } }
    var past by remember { mutableStateOf("") }
    Sheet(onClose) {
        Text(tr("Настройки", "Settings"), style = T.h2, color = p.ink)
        Sec(tr("Тема", "Theme"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, l) in listOf("system" to tr("Как в системе", "System"), "light" to tr("Светлая", "Light"), "dark" to tr("Тёмная", "Dark")))
                Chip(l, s.theme == id, Modifier.weight(1f)) { c.write { settings.update { it.copy(theme = id) } } }
        }
        Sec(tr("Язык", "Language"))
        val lang = currentLanguage()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, l) in listOf("" to tr("Как в системе", "System"), "ru" to "Русский", "en" to "English"))
                Chip(l, lang == id, Modifier.weight(1f)) { setLanguage(id) }
        }
        Sec(tr("Что считать", "What to count"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(tr("Слова", "Words"), s.unit == "w", Modifier.weight(1f)) { c.write { settings.update { it.copy(unit = "w") } } }
            Chip(tr("Знаки с пробелами", "Characters"), s.unit == "c", Modifier.weight(1f)) { c.write { settings.update { it.copy(unit = "c") } } }
        }
        Sec(tr("Дневная норма, слов", "Daily goal, words"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Keycap(Modifier.size(48.dp), onClick = { c.write { settings.update { it.copy(norm = (it.norm - 100).coerceAtLeast(100)) } } }) { Text("−", style = T.btn, color = p.kLeg) }
            Text(fmt(s.norm), style = T.num, color = p.ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Keycap(Modifier.size(48.dp), onClick = { c.write { settings.update { it.copy(norm = (it.norm + 100).coerceAtMost(10000)) } } }) { Text("+", style = T.btn, color = p.kLeg) }
        }
        Sec(tr("Звук клавиш", "Key sounds"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(tr("Включён", "On"), s.sound, Modifier.weight(1f)) { c.write { settings.update { it.copy(sound = true) } } }
            Chip(tr("Выключен", "Off"), !s.sound, Modifier.weight(1f)) { c.write { settings.update { it.copy(sound = false) } } }
        }
        Sec(tr("Перенос прогресса", "Move your progress"))
        Card(pad = 6.dp) { Column {
            Line("upload", tr("Сохранить в файл", "Save to a file"), tr("Книги, история, жители, стол и буквицы – в один файл .keyling", "Books, history, residents, desk and coins in one .keyling file")) { export.launch("keyling.keyling") }
            Line("home", tr("Загрузить из файла", "Load from a file"), tr("С другого телефона или из старой копии. Заменит текущие данные.", "From another phone or an old copy. Replaces what's here.")) { import.launch(arrayOf("*/*")) }
        } }
        Gap(8.dp)
        Text(tr("Ещё копия сама уходит в резервную копию Android, если она включена.", "A copy also goes into Android's own backup, if it's on."), style = T.small, color = p.mute)
        Sec(tr("Написано до Клавишки", "Written before Keyling"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumField(past, tr("Слов в текущей книге", "Words in the current book"), Modifier.weight(1f)) { past = it }
            Spacer(Modifier.width(8.dp))
            Keycap(Modifier.width(96.dp).height(48.dp), accent = (past.toIntOrNull() ?: 0) > 0, onClick = { past.toIntOrNull()?.takeIf { it > 0 }?.let { n -> c.write { pastWords(n) }; past = ""
                c.news.tryEmit(tr("Учтено ${fmt(n)} слов – стопка рукописи подросла", "Counted ${fmt(n)} words – the manuscript grew")) } }) { Text(tr("Учесть", "Add"), style = T.bodyB, color = if ((past.toIntOrNull() ?: 0) > 0) p.xLeg else p.kLeg) }
        }
        Sec("StillCraft")
        Text(tr("Клавишка – одно из приложений StillCraft. Пишите нам в Telegram.", "Keyling is one of the StillCraft apps. Talk to us on Telegram."), style = T.small, color = p.mute)
        Gap(8.dp)
        Card(pad = 6.dp) { Column {
            Line("bug", tr("Сообщить об ошибке", "Report a bug"), tr("что-то сломалось или выглядит не так", "something broke or looks wrong")) { open(ctx, Community.BUG) }
            Line("sparkle", tr("Предложить идею", "Suggest an idea"), tr("что сделало бы Клавишку лучше", "what would make Keyling better")) { open(ctx, Community.IDEA) }
            Line("star", tr("Следить за обновлениями", "Follow updates"), tr("бот напишет, когда выйдет новая версия", "the bot writes when a new version is out")) { open(ctx, Community.FOLLOW) }
            Line("paw", tr("Чат StillCraft", "StillCraft chat"), tr("все приложения и те, кто ими пользуется", "all the apps and the people who use them")) { open(ctx, Community.CHAT) }
            Line("mug", tr("Поддержать Клавишку", "Support Keyling"), tr("на новых жителей и колпачки", "for new residents and keycaps")) { open(ctx, Community.SUPPORT) }
            Line("book", tr("Исходный код", "Source code"), "github.com/bigdevwhale/keyling") { open(ctx, "https://github.com/bigdevwhale/keyling") }
        } }
        Gap(14.dp)
        Text(tr("Клавишка ${app.keyling.BuildConfig.VERSION_NAME} · без рекламы и аккаунтов, работает без интернета. Шрифты Press Start 2P, Lora и Inter (SIL OFL), звуки клавиш из kbsim (MIT).",
            "Keyling ${app.keyling.BuildConfig.VERSION_NAME} · no ads, no accounts, works offline. Fonts Press Start 2P, Lora and Inter (SIL OFL), key sounds from kbsim (MIT)."), style = T.tiny, color = p.mute)
    }
}

@Composable
private fun Line(icon: String, title: String, sub: String, onClick: () -> Unit) {
    val p = K.pal; val c = LocalContext.current.container
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { c.sound.click(Click.sw); onClick() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(p.accSoft), contentAlignment = Alignment.Center) { Icon(icon, p.acc, 18.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, style = T.bodyB, color = p.ink); Text(sub, style = T.small, color = p.mute) }
    }
}

/** The StillCraft bot: deep links file a bug or an idea for this app, or follow its releases. */
object Community {
    private const val BOT = "https://t.me/ArcaneWhisperBot?start="
    const val SLUG = "keyling"
    const val BUG = "${BOT}bug_$SLUG"
    const val IDEA = "${BOT}wish_$SLUG"
    const val FOLLOW = "${BOT}f_$SLUG"
    const val CHAT = "https://t.me/+Q4gFEDk8O842OGVi"
    const val SUPPORT = "https://bigdevwhale.github.io/stillcraft/support/"
}

private fun open(ctx: android.content.Context, url: String) = runCatching {
    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
