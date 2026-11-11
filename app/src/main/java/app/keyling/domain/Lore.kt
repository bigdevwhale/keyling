package app.keyling.domain

import java.time.LocalDate

/** A creature living under one key. Its food is one kind of writing work; [steps] are the amounts for each level. */
class Resident(val id: String, val key: String, val nameRu: String, val nameEn: String, val aboutRu: String, val aboutEn: String,
               val howRu: String, val howEn: String, val unitRu: String, val unitEn: String, val steps: IntArray, val food: (Stats) -> Int) {
    val name get() = tr(nameRu, nameEn)
    val about get() = tr(aboutRu, aboutEn)
    val how get() = tr(howRu, howEn)
    fun amount(st: Stats) = food(st)
    fun open(st: Stats) = id == "space" || amount(st) >= steps[0]
    fun level(st: Stats) = if (!open(st)) 0 else 1 + steps.count { amount(st) >= it } - (if (id == "space") 0 else 1)
    fun nextAt(st: Stats): Int? = steps.firstOrNull { amount(st) < it }
    fun stage(st: Stats) = when (level(st)) { in 0..2 -> tr("малыш", "baby"); in 3..5 -> tr("подросток", "youngster"); in 6..8 -> tr("взрослый", "grown"); else -> tr("древний", "ancient") }
}

object Residents {
    val all = listOf(
        Resident("space", "Space", "Пробельчик", "Spacelet", "Живёт под пробелом и ест любые буквы. Растёт от каждого слова.", "Lives under the space bar and eats any letter. Every word makes it grow.",
            "Живёт с первого дня.", "Here from day one.", "слов", "words", intArrayOf(500, 2000, 5000, 10000, 20000, 40000, 70000, 110000, 160000, 250000)) { it.fed },
        Resident("enter", "Enter", "Абзацик", "Paragraphling", "Приходит на праздничный ужин, когда ты отмечаешь законченную главу.", "Comes to a feast whenever you mark a finished chapter.",
            "Отметь первую законченную главу.", "Mark your first finished chapter.", "глав", "chapters", intArrayOf(1, 3, 6, 10, 15, 22, 30, 40, 55, 75)) { it.chapters },
        Resident("bksp", "Backspace", "Затирыш", "Erasel", "Питается правкой. Наконец у редактуры есть свой зверь.", "Feeds on editing. At last editing has its own creature.",
            "Запиши первую правку.", "Log your first editing session.", "минут правки", "minutes of editing", intArrayOf(15, 120, 300, 600, 1200, 2100, 3300, 4800, 6600, 9000)) { it.editMin },
        Resident("caps", "Caps Lock", "Горлан", "Shouter", "Обожает спринты. Чем быстрее печатаешь, тем громче поёт.", "Loves sprints. The faster you type, the louder it sings.",
            "Пройди первый спринт.", "Do your first sprint.", "спринтов", "sprints", intArrayOf(1, 3, 6, 10, 15, 22, 30, 40, 55, 75)) { it.sprintCount },
        Resident("tab", "Tab", "Отступ", "Indent", "Любит планы и порядок. Сидит над оглавлением.", "Loves plans and order. Sits over the table of contents.",
            "Распиши план книги хотя бы на пять глав.", "Plan a book of at least five chapters.", "глав в плане", "planned chapters", intArrayOf(5, 10, 15, 22, 30, 40, 52, 66, 82, 100)) { st -> st.books.sumOf { it.planned } },
        Resident("shift", "Shift", "Полуночник", "Night Owl", "Не спит, пока ты пишешь после полуночи.", "Stays up while you write after midnight.",
            "Пиши после полуночи три ночи.", "Write after midnight on three nights.", "ночей", "nights", intArrayOf(3, 6, 10, 15, 22, 30, 40, 52, 66, 82)) { it.nightSessions },
        Resident("esc", "Esc", "Беглец", "Escapee", "Приходит в честный выходной и бережёт серию, пока ты отдыхаешь.", "Comes on an honest day off and keeps your streak while you rest.",
            "Объяви первый честный выходной.", "Declare your first honest day off.", "выходных", "days off", intArrayOf(1, 3, 6, 10, 15, 22, 30, 40, 55, 75)) { it.restDays },
    )
    fun of(id: String) = all.firstOrNull { it.id == id } ?: all[0]
}

/** One achievement: [value] of [target], a medal tier, a reward in буквицы. */
class Ach(val id: String, val cat: String, val name: String, val about: String, val value: Int, val target: Int, val icon: String, val tier: Char, val reward: Int, val secret: Boolean = false) {
    val done get() = value >= target
}

object Achievements {
    fun all(st: Stats): List<Ach> {
        val s = st.s; val best = st.bestDay?.words ?: 0; val longest = st.longest.first
        val nov = st.months[10]; val finished = st.books.count { it.status == "done" }
        val bought = s.owned.count { it.startsWith("desk:") || it.startsWith("mug:") || it.startsWith("plant:") || it.startsWith("lamp:") || it.startsWith("mascot:") }
        val openRes = Residents.all.count { it.open(st) }
        val current = st.books.firstOrNull { it.id == s.book } ?: st.books.firstOrNull()
        val curWords = current?.let { st.bookWords(it) } ?: 0
        val kinds = st.caught.map { it.kind }.distinct().size
        fun W(ru: String, en: String) = tr(ru, en)
        return listOf(
            Ach("w1", W("Слова", "Words"), W("Первое слово", "First word"), W("Скормить Пробельчику первое слово", "Feed Spacelet its first word"), st.fed, 1, "pen", 'b', 5),
            Ach("w1k", W("Слова", "Words"), W("Тысяча за день", "A thousand a day"), W("Написать 1000 слов за один день", "Write 1,000 words in one day"), best, 1000, "fire", 'b', 20),
            Ach("w3k", W("Слова", "Words"), W("Три тысячи", "Three thousand"), W("Написать 3000 слов за один день", "Write 3,000 words in one day"), best, 3000, "fire", 's', 60),
            Ach("w5k", W("Слова", "Words"), W("Пять тысяч", "Five thousand"), W("Написать 5000 слов за один день", "Write 5,000 words in one day"), best, 5000, "fire", 'g', 150),
            Ach("w10kw", W("Слова", "Words"), W("Неделя в 10 000", "A 10,000 week"), W("Написать 10 000 слов за семь дней", "Write 10,000 words in seven days"), st.bestWeek.first, 10000, "cal", 's', 60),
            Ach("nano", W("Слова", "Words"), W("Ноябрьский марафон", "November marathon"), W("50 000 слов за ноябрь – как в NaNoWriMo", "50,000 words in November, NaNoWriMo-style"), nov, 50000, "cal", 'g', 300),
            Ach("mchars", W("Слова", "Words"), W("Миллион знаков", "A million characters"), W("Миллион знаков с пробелами за всё время", "A million characters with spaces, all time"), (st.fed * CHARS_PER_WORD).toInt(), 1_000_000, "pen", 'g', 200),
            Ach("s7", W("Постоянство", "Steadiness"), W("Семь дней", "Seven days"), W("Писать семь дней подряд", "Write seven days in a row"), longest, 7, "cal", 'b', 20),
            Ach("s30", W("Постоянство", "Steadiness"), W("Месяц без пропусков", "A month unbroken"), W("Писать 30 дней подряд", "Write 30 days in a row"), longest, 30, "cal", 's', 100),
            Ach("s100", W("Постоянство", "Steadiness"), W("Сто дней", "A hundred days"), W("Писать 100 дней подряд", "Write 100 days in a row"), longest, 100, "cal", 'g', 500),
            Ach("early", W("Постоянство", "Steadiness"), W("Ранняя пташка", "Early bird"), W("Писать до семи утра – пять раз", "Write before 7 a.m. five times"), st.earlySessions, 5, "sun", 'b', 30),
            Ach("night", W("Постоянство", "Steadiness"), W("Ночная смена", "Night shift"), W("Писать после полуночи три ночи", "Write after midnight on three nights"), st.nightSessions, 3, "moon", 's', 40),
            Ach("rest", W("Постоянство", "Steadiness"), W("Честный отдых", "Honest rest"), W("Объявить выходной вместо того, чтобы корить себя", "Declare a day off instead of blaming yourself"), st.restDays, 1, "rest", 'b', 20),
            Ach("ch1", W("Книги", "Books"), W("Первая глава", "First chapter"), W("Отметить первую законченную главу", "Mark your first finished chapter"), st.chapters, 1, "book", 'b', 20),
            Ach("ch10", W("Книги", "Books"), W("Десять глав", "Ten chapters"), W("Закончить десять глав", "Finish ten chapters"), st.chapters, 10, "book", 's', 80),
            Ach("end", W("Книги", "Books"), W("Конец", "The End"), W("Дописать черновик книги целиком", "Finish a whole draft"), finished, 1, "star", 'g', 300),
            Ach("draft2", W("Книги", "Books"), W("Вторая редакция", "Second draft"), W("Начать вторую редакцию книги", "Start a second draft of a book"), st.books.maxOfOrNull { it.drafts }?.minus(1) ?: 0, 1, "cut", 's', 100),
            Ach("tri", W("Книги", "Books"), W("Трилогия", "Trilogy"), W("Дописать три книги", "Finish three books"), finished, 3, "book", 'g', 500),
            Ach("gatsby", W("Книги", "Books"), W("Длиннее «Гэтсби»", "Longer than Gatsby"), W("Книга длиннее 47 000 слов", "A book longer than 47,000 words"), curWords, 47000, "book", 's', 80),
            Ach("master", W("Книги", "Books"), W("Длиннее «Мастера»", "Longer than The Master"), W("Книга длиннее 140 000 слов – как «Мастер и Маргарита»", "A book longer than 140,000 words, like The Master and Margarita"), curWords, 140000, "book", 'g', 300),
            Ach("cut1k", W("Правка и бесы", "Editing & typos"), W("Беспощадный редактор", "Ruthless editor"), W("Вырезать 1000 слов за одну правку", "Cut 1,000 words in one go"), st.biggestCut, 1000, "cut", 'b', 30),
            Ach("ed100", W("Правка и бесы", "Editing & typos"), W("Сто часов правки", "A hundred hours of editing"), W("Потратить на правку 100 часов", "Spend 100 hours editing"), st.editMin / 60, 100, "cut", 'g', 200),
            Ach("bug50", W("Правка и бесы", "Editing & typos"), W("Охотник на бесов", "Typo hunter"), W("Поймать 50 бесов-опечаток", "Catch 50 typo bugs"), st.caught.size, 50, "bug", 's', 80),
            Ach("bug9", W("Правка и бесы", "Editing & typos"), W("Коллекционер бесов", "Bug collector"), W("Поймать все девять видов бесов", "Catch all nine kinds of bugs"), kinds, 9, "bug", 'g', 150),
            Ach("sp1", W("Спринты", "Sprints"), W("Первый спринт", "First sprint"), W("Пройти первый спринт", "Do your first sprint"), st.sprintCount, 1, "clock", 'b', 10),
            Ach("ghost", W("Спринты", "Sprints"), W("Призрак позади", "Ghost behind"), W("Обогнать своего призрака десять раз", "Beat your ghost ten times"), s.ghostWins, 10, "clock", 's', 60),
            Ach("wpm30", W("Спринты", "Sprints"), W("30 слов в минуту", "30 words a minute"), W("Держать темп 30 слов в минуту весь спринт", "Keep 30 words a minute through a sprint"), s.bestSprint / 10, 30, "clock", 'g', 120),
            Ach("move", W("Жители и стол", "Residents & desk"), W("Новосёл", "Housewarming"), W("Позвать другого жителя под пробел", "Invite another resident under the space bar"), if (s.pet != "space") 1 else 0, 1, "paw", 'b', 20),
            Ach("full", W("Жители и стол", "Residents & desk"), W("Полный дом", "Full house"), W("Поселить всех семерых жителей", "Get all seven residents to move in"), openRes, 7, "paw", 'g', 300),
            Ach("old", W("Жители и стол", "Residents & desk"), W("Старожил", "Old-timer"), W("Вырастить жителя до 5 уровня", "Raise a resident to level 5"), Residents.all.maxOf { it.level(st) }, 5, "paw", 's', 60),
            Ach("pat", W("Жители и стол", "Residents & desk"), W("Ласковые руки", "Gentle hands"), W("Погладить жителя сто раз", "Stroke a resident a hundred times"), s.pets, 100, "paw", 'b', 30),
            Ach("ownmug", W("Жители и стол", "Residents & desk"), W("Своя кружка", "My own mug"), W("Поставить на стол кружку со своим фото", "Put a mug with your own photo on the desk"), if (s.mug == "custom") 1 else 0, 1, "mug", 'b', 20),
            Ach("cosy", W("Жители и стол", "Residents & desk"), W("Уютный стол", "Cosy desk"), W("Купить пять вещей для стола", "Buy five things for the desk"), bought, 5, "home", 's', 60),
            Ach("capsall", W("Жители и стол", "Residents & desk"), W("Коллекция колпачков", "Keycap collector"), W("Собрать все наборы колпачков", "Collect every keycap set"), 2 + s.owned.count { it.startsWith("caps:") }, 5, "key", 's', 80),
            Ach("round", W("Секретные", "Secret"), W("Ровно", "Exactly"), W("Написать за день ровно 1234 слова", "Write exactly 1,234 words in a day"), if (st.days.values.any { it.words == 1234 }) 1 else 0, 1, "q", 's', 100, true),
            Ach("ny", W("Секретные", "Secret"), W("Слово в Новый год", "New Year's word"), W("Написать хоть слово 1 января", "Write anything on 1 January"), if (st.days.values.any { LocalDate.ofEpochDay(it.day).dayOfYear == 1 && it.words > 0 }) 1 else 0, 1, "q", 'g', 150, true),
            Ach("coffee", W("Секретные", "Secret"), W("Кофейная пауза", "Coffee break"), W("Погладить жителя, пока на столе стакан в подстаканнике", "Stroke a resident while the glass in its holder stands on the desk"), if (s.mug == "glass" && s.pets > 0) 1 else 0, 1, "q", 'b', 50, true),
        )
    }
}

/** A step on the writer's road, with the day it was reached (or null if it's still ahead). */
class Mile(val title: String, val note: String, val day: Long?, val progress: Float = 1f)

object Milestones {
    private val WORDS = intArrayOf(1000, 10000, 25000, 50000, 100000, 150000, 200000, 250000, 500000, 750000, 1_000_000)
    private val DAYS = intArrayOf(10, 50, 100, 200, 300, 500)
    fun road(st: Stats): List<Mile> {
        val out = ArrayList<Mile>(); var cum = 0; var days = 0; var wi = 0; var di = 0
        val first = st.days.keys.minOrNull()
        if (first != null) out += Mile(tr("Первое слово в Клавишке", "First word in Keyling"), tr("Пробельчик вылупился", "Spacelet hatched"), first)
        for (d in st.days.keys.sorted()) { val v = st.days.getValue(d); cum += v.words; if (v.words > 0) days++
            while (wi < WORDS.size && cum >= WORDS[wi]) { val w = WORDS[wi]; out += Mile(words(w), if (w >= 6250) tr("≈ ${fmt((w * CHARS_PER_WORD / SHEET).toInt())} авт. л.", "≈ ${fmt((w * CHARS_PER_WORD / 1800).toInt())} pages") else "", d); wi++ }
            while (di < DAYS.size && days >= DAYS[di]) { out += Mile(tr("${DAYS[di]} дней с письмом", "${DAYS[di]} writing days"), "", d); di++ } }
        for (b in st.books.filter { it.status == "done" && it.finished > 0 }) out += Mile(tr("Черновик дописан", "Draft finished"), "«${b.title}», ${words(st.bookWords(b))}", b.finished)
        out.sortBy { it.day }
        if (wi < WORDS.size) out += Mile(words(WORDS[wi]), tr("ещё ${fmt(WORDS[wi] - cum)}", "${fmt(WORDS[wi] - cum)} to go"), null, cum.toFloat() / WORDS[wi])
        if (di < DAYS.size) out += Mile(tr("${DAYS[di]} дней с письмом", "${DAYS[di]} writing days"), tr("ещё ${DAYS[di] - days}", "${DAYS[di] - days} to go"), null, days.toFloat() / DAYS[di])
        for (w in WORDS.drop(wi + 1).take(2)) out += Mile(words(w), tr("впереди", "ahead"), null, 0f)
        return out
    }
}

/** Famous books by length (words), to measure a manuscript against. */
val CLASSICS = listOf(
    Triple("«Шинель»", "The Overcoat", 9000), Triple("«Маленький принц»", "The Little Prince", 17000), Triple("«Старик и море»", "The Old Man and the Sea", 26600),
    Triple("«Собачье сердце»", "Heart of a Dog", 30000), Triple("«451° по Фаренгейту»", "Fahrenheit 451", 46000), Triple("«Великий Гэтсби»", "The Great Gatsby", 47000),
    Triple("«Над пропастью во ржи»", "The Catcher in the Rye", 73000), Triple("«Мастер и Маргарита»", "The Master and Margarita", 140000),
)

/** The nine kinds of typo bugs that crawl between the keys. */
val BUGS_RU = listOf("«тся/ться»", "лишняя запятая", "«было было»", "двойной пробел", "потерянная «ё»", "очепятка", "«в течении»", "висячий предлог", "«одеть пальто»")
val BUGS_EN = listOf("its/it's", "a stray comma", "\"the the\"", "a double space", "their/there", "a typo", "\"could of\"", "a dangling modifier", "affect/effect")
fun bugName(kind: Int) = (if (Lang.ru) BUGS_RU else BUGS_EN)[kind % 9]

/** The shop: what can stand on the desk, and the price in буквицы. */
class Item(val cat: String, val id: String, val ru: String, val en: String, val price: Int) { val name get() = tr(ru, en); val key get() = "$cat:$id" }
object Shop {
    val items = listOf(
        Item("desk", "oak", "Дуб", "Oak", 0), Item("desk", "walnut", "Тёмный орех", "Dark walnut", 120), Item("desk", "white", "Белёное дерево", "Whitewashed", 150), Item("desk", "baize", "Зелёное сукно", "Green baize", 200),
        Item("mug", "heart", "Кружка с сердцем", "Heart mug", 0), Item("mug", "enamel", "Эмалированная", "Enamel mug", 60), Item("mug", "glass", "Стакан в подстаканнике", "Tea glass in a holder", 120),
        Item("mug", "thermo", "Термокружка", "Travel mug", 80), Item("mug", "custom", "Своя кружка", "Your own mug", 0),
        Item("plant", "cactus", "Кактус", "Cactus", 0), Item("plant", "succulent", "Эхеверия", "Echeveria", 50), Item("plant", "monstera", "Монстера", "Monstera", 90), Item("plant", "none", "Без растения", "No plant", 0),
        Item("lamp", "none", "Без лампы", "No lamp", 0), Item("lamp", "banker", "Банкирская лампа", "Banker's lamp", 250),
        Item("mascot", "none", "Пусто", "Nothing", 0), Item("mascot", "candle", "Свеча", "Candle", 40), Item("mascot", "globe", "Глобус", "Globe", 140), Item("mascot", "cat", "Спящий кот", "Sleeping cat", 300),
    )
    val switches = listOf(Triple("blue", 0, true), Triple("brown", 0, true), Triple("red", 80, false))
}

/** Who each resident is: a temper, what it loves, and the things it says when stroked or fed. */
class Persona(val traitRu: String, val traitEn: String, val likesRu: String, val likesEn: String, val linesRu: List<String>, val linesEn: List<String>) {
    val trait get() = tr(traitRu, traitEn)
    val likes get() = tr(likesRu, likesEn)
    fun line(i: Int) = (if (Lang.ru) linesRu else linesEn).let { it[Math.floorMod(i, it.size)] }
}

val PERSONAS = mapOf(
    "space" to Persona("Добродушный обжора", "Good-natured glutton", "длинные слова и тёплую гирлянду", "long words and warm fairy lights",
        listOf("Хрум. Хорошее слово попалось.", "Под пробелом тепло, пиши спокойно.", "Можно ещё абзац? Очень вкусно.", "Я съел букву «ё». Прости."),
        listOf("Crunch. That was a good word.", "It's warm under the space bar, write on.", "One more paragraph? It's delicious.", "I ate a semicolon. Sorry.")),
    "enter" to Persona("Торжественный и немного важный", "Ceremonious and a bit grand", "точку в конце главы и торт со свечками", "the full stop at a chapter's end and cake with candles",
        listOf("Глава закончена? Несите торт!", "Новый абзац – новая жизнь.", "Я подожду. Хорошая глава стоит ожидания."),
        listOf("Chapter done? Bring the cake!", "A new paragraph is a new life.", "I'll wait. A good chapter is worth it.")),
    "bksp" to Persona("Строгий, но справедливый", "Strict but fair", "вычёркивать лишнее и крошки от ластика", "crossing things out and eraser crumbs",
        listOf("Это слово тут лишнее. Дай его мне.", "Меньше – значит лучше.", "Правка – тоже письмо. Я сыт."),
        listOf("That word doesn't belong. Give it here.", "Less is more.", "Editing is writing too. I'm full.")),
    "caps" to Persona("Громкий и азартный", "Loud and fired up", "спринты, микрофон и восклицательные знаки", "sprints, a microphone and exclamation marks",
        listOf("БЫСТРЕЕ! Призрак догоняет!", "ЕЩЁ ОДИН СПРИНТ?!", "Я пою, когда ты печатаешь."),
        listOf("FASTER! The ghost is catching up!", "ONE MORE SPRINT?!", "I sing when you type.")),
    "tab" to Persona("Аккуратист и планировщик", "Tidy planner", "карточки с планом и ровные отступы", "index cards and neat indents",
        listOf("Сначала план, потом глава.", "Карточка на месте? Отлично.", "Пять глав в плане – и я спокоен."),
        listOf("Plan first, then the chapter.", "Card in place? Perfect.", "Five chapters planned and I'm calm.")),
    "shift" to Persona("Мечтатель и сова", "Dreamer and night owl", "луну, какао и тишину после полуночи", "the moon, cocoa and the hush after midnight",
        listOf("Самые смелые слова приходят ночью.", "Ещё не спишь? Я тоже.", "Луна сегодня – как запятая."),
        listOf("The boldest words come at night.", "Still up? Me too.", "The moon tonight looks like a comma.")),
    "esc" to Persona("Путешественник, бережёт силы", "Traveller who saves strength", "чемодан наготове и честные выходные", "a packed suitcase and honest days off",
        listOf("Отдых – часть работы.", "Серия в безопасности, я присмотрю.", "Вернёмся – и напишем лучше."),
        listOf("Rest is part of the work.", "Your streak is safe, I'm watching it.", "We'll come back and write better.")),
)

/** What a resident does while it lives on the desk (in the nook under the space bar). */
object Ability {
    fun of(id: String): Pair<String, String> = when (id) {
        "space" -> tr("Сытость", "Full belly") to tr("+25% буквиц за написанные слова.", "+25% coins for written words.")
        "enter" -> tr("Праздник главы", "Chapter feast") to tr("За законченную главу – 100 буквиц вместо 50.", "100 coins for a finished chapter instead of 50.")
        "bksp" -> tr("Чистовик", "Clean copy") to tr("Правка оплачивается вдвое: буквица за каждые 5 минут.", "Editing pays double: a coin for every 5 minutes.")
        "caps" -> tr("Громкий спринт", "Loud sprint") to tr("Слова, написанные в спринте, приносят двойные буквицы.", "Words written in a sprint earn double coins.")
        "tab" -> tr("Мягкая норма", "Gentle goal") to tr("Дневная норма засчитывается уже на 90%.", "The daily goal counts as reached at 90%.")
        "shift" -> tr("Ночная смена", "Night shift") to tr("Слова после 22:00 приносят в полтора раза больше буквиц.", "Words after 10 p.m. earn one and a half times the coins.")
        else -> tr("Запасной день", "Spare day") to tr("Один пропущенный день в неделю не рвёт серию.", "One missed day a week doesn't break your streak.")
    }

    /** Coins for [n] new words, with the desk resident's ability. */
    fun wordCoins(pet: String, n: Int, kind: String, hour: Int): Int {
        var k = 1f
        if (pet == "space") k *= 1.25f
        if (pet == "caps" && kind == "sprint") k *= 2f
        if (pet == "shift" && (hour >= 22 || hour < 5)) k *= 1.5f
        return (n / 25f * k).toInt()
    }
    fun chapterCoins(pet: String) = if (pet == "enter") 100 else 50
    fun editCoins(pet: String, minutes: Int) = if (pet == "bksp") minutes / 5 else minutes / 10
    fun normAt(pet: String, norm: Int) = if (pet == "tab") (norm * .9f).toInt() else norm
}

/** What each resident eats, in plain words, and the quickest way to feed it right now. */
object Food {
    fun what(id: String): String = when (id) {
        "space" -> tr("Ест слова: каждое записанное слово – еда.", "Eats words: every word you log.")
        "enter" -> tr("Ест законченные главы: отмечайте «Глава закончена», когда записываете слова.", "Eats finished chapters: tick “Chapter finished” when you log words.")
        "bksp" -> tr("Ест правку: каждые записанные минуты редактуры.", "Eats editing: every minute of editing you log.")
        "caps" -> tr("Ест спринты: каждый пройденный спринт.", "Eats sprints: every sprint you finish.")
        "tab" -> tr("Ест план: главы, расписанные в плане книги.", "Eats the plan: chapters planned for your book.")
        "shift" -> tr("Ест ночи: дни, когда вы писали после полуночи.", "Eats nights: days you wrote after midnight.")
        else -> tr("Ест отдых: честные выходные, объявленные заранее.", "Eats rest: honest days off you declare.")
    }
    /** The action that feeds it: write · chapter · edit · sprint · book · rest · null (can't be done on demand). */
    fun action(id: String): Pair<String, String>? = when (id) {
        "space" -> "write" to tr("Записать слова", "Log words")
        "enter" -> "chapter" to tr("Отметить главу", "Mark a chapter")
        "bksp" -> "edit" to tr("Записать правку", "Log editing")
        "caps" -> "sprint" to tr("Начать спринт", "Start a sprint")
        "tab" -> "book" to tr("Расписать план книги", "Plan the book")
        "esc" -> "rest" to tr("Объявить выходной", "Take a day off")
        else -> null
    }
    /** 0 baby · 1 youngster · 2 grown · 3 ancient. */
    fun stageIndex(level: Int) = when (level) { in 0..2 -> 0; in 3..5 -> 1; in 6..8 -> 2; else -> 3 }
}
