package com.afn478.geominder.parser

import com.afn478.geominder.localization.SupportedLanguage
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for source-language rules.
 *
 * Packs are created lazily. A device using German therefore never compiles or executes the
 * French, Japanese, or any other language's lexical regexes while parsing reminders.
 */
object TimeLanguagePacks {
    private val cache = ConcurrentHashMap<SupportedLanguage, TimeLanguagePack>()

    fun forLanguage(language: SupportedLanguage): TimeLanguagePack = cache.getOrPut(language) {
        create(language)
    }

    fun defaultsFor(language: SupportedLanguage): Map<String, LocalTime> =
        forLanguage(language).keywordTimes

    private fun create(language: SupportedLanguage): TimeLanguagePack {
        val common = commonClockPatterns()
        return when (language) {
            SupportedLanguage.ENGLISH -> englishPack(common)
            SupportedLanguage.GERMAN -> germanPack(common)
            SupportedLanguage.FRENCH -> frenchPack(common)
            SupportedLanguage.ITALIAN -> italianPack(common)
            SupportedLanguage.SPANISH -> spanishPack(common)
            SupportedLanguage.RUSSIAN -> russianPack(common)
            SupportedLanguage.JAPANESE -> japanesePack(common)
            SupportedLanguage.CHINESE -> chinesePack(common)
            SupportedLanguage.KOREAN -> koreanPack(common)
        }
    }

    private fun commonClockPatterns(): List<ClockPattern> = listOf(
        ClockPattern(
            regex = Regex("(?<![\\d.])(?<hour>\\d{1,2}):(?<minute>\\d{1,2})(?!\\d)"),
            priority = 100,
        ),
    )

    private fun englishPack(common: List<ClockPattern>) = TimeLanguagePack(
        language = SupportedLanguage.ENGLISH,
        locale = SupportedLanguage.ENGLISH.locale,
        keywordTimes = loadKeywordTimes(SupportedLanguage.ENGLISH),
        clockPatterns = common + listOf(
            ClockPattern(
                regex = Regex(
                    "(?iu)(?<![\\p{L}\\p{N}])(?:at\\s+)?" +
                        "(?<hour>\\d{1,2})(?::(?<minute>\\d{1,2}))?\\s*" +
                        "(?<period>am|pm)(?![\\p{L}\\p{N}])",
                ),
                priority = 105,
                modifierPosition = ModifierPosition.AFTER_HOUR,
                modifierAliases = mapOf("am" to HourModifier.AM, "pm" to HourModifier.PM),
                defaultModifier = HourModifier.AM,
            ),
            ClockPattern(
                regex = Regex(
                    "(?iu)(?<![\\p{L}\\p{N}])at\\s+" +
                        "(?<hour>2[0-3]|1\\d|0?\\d)(?![\\d.:])\\b" +
                        "(?!\\s*(?:am|pm)\\b)",
                ),
                priority = 85,
            ),
            clockWithPeriod(
                period = "in\\s+the\\s+morning|in\\s+the\\s+afternoon|in\\s+the\\s+evening|at\\s+night|tonight",
                aliases = PeriodAliases.englishPeriods,
                priority = 104,
            ),
            clockWithPrefix(
                prefix = "at",
                period = "in\\s+the\\s+morning|in\\s+the\\s+afternoon|in\\s+the\\s+evening|at\\s+night|tonight",
                aliases = PeriodAliases.englishPeriods,
                periodRequired = true,
                priority = 105,
            ),
        ),
        relativeDateRules = listOf(
            relativeDate("day\\s+after\\s+tomorrow", 2, 92),
            relativeDate("today", 0),
            relativeDate("tomorrow", 1),
        ),
        relativeDayRules = listOf(
            relativeDays("in\\s+(?<amount>\\d+)\\s+(?<unit>day|days|week|weeks)", mapOf(
                "day" to 1,
                "days" to 1,
                "week" to 7,
                "weeks" to 7,
            )),
        ),
        relativeDurationRules = listOf(
            relativeDuration("in\\s+(?<amount>\\d+)\\s+(?<unit>hour|hours|minute|minutes|mins?)", mapOf(
                "hour" to DurationUnit.HOURS,
                "hours" to DurationUnit.HOURS,
                "minute" to DurationUnit.MINUTES,
                "minutes" to DurationUnit.MINUTES,
                "min" to DurationUnit.MINUTES,
                "mins" to DurationUnit.MINUTES,
            )),
        ),
        nextWords = setOf("next"),
        fromPrefixes = listOf(Regex("(?iu)(?<![\\p{L}\\p{N}])from\\s+$")),
        fromSuffixes = emptyList(),
    )

    private fun germanPack(common: List<ClockPattern>) = TimeLanguagePack(
        language = SupportedLanguage.GERMAN,
        locale = SupportedLanguage.GERMAN.locale,
        keywordTimes = loadKeywordTimes(SupportedLanguage.GERMAN),
        clockPatterns = common + listOf(
            clockWithUnit(
                unit = "Uhr",
                period = "morgens|vormittags|mittags|nachmittags|abends|nachts",
                prefix = "(?:um|gegen)\\s+",
                aliases = PeriodAliases.germanPeriods,
                priority = 104,
            ),
            clockWithUnit(
                unit = "Uhr",
                period = "morgens|vormittags|mittags|nachmittags|abends|nachts",
                aliases = PeriodAliases.germanPeriods,
                priority = 103,
            ),
            clockWithPeriod(
                period = "morgens|vormittags|mittags|nachmittags|abends|nachts",
                aliases = PeriodAliases.germanPeriods,
                priority = 88,
            ),
            clockWithPrefix(
                prefix = "(?:um|gegen)",
                unit = "Uhr",
                period = "morgens|vormittags|mittags|nachmittags|abends|nachts",
                aliases = PeriodAliases.germanPeriods,
                priority = 102,
            ),
            clockWithPrefix("(?:um|gegen)", priority = 85),
        ),
        relativeDateRules = listOf(
            relativeDate("übermorgen", 2),
            relativeDate("heute", 0),
            relativeDate("morgen", 1),
        ),
        relativeDayRules = listOf(
            relativeDays("in\\s+(?<amount>\\d+)\\s+(?<unit>tagen?|wochen?)", mapOf(
                "tag" to 1,
                "tage" to 1,
                "tagen" to 1,
                "woche" to 7,
                "wochen" to 7,
            )),
        ),
        relativeDurationRules = listOf(
            relativeDuration("in\\s+(?<amount>\\d+)\\s+(?<unit>stunden?|minuten?)", mapOf(
                "stunde" to DurationUnit.HOURS,
                "stunden" to DurationUnit.HOURS,
                "minute" to DurationUnit.MINUTES,
                "minuten" to DurationUnit.MINUTES,
            )),
        ),
        nextWords = setOf("nächsten", "nächste", "nächster", "next"),
        fromPrefixes = listOf(Regex("(?iu)(?<![\\p{L}\\p{N}])ab\\s+$")),
        fromSuffixes = emptyList(),
    )

    private fun frenchPack(common: List<ClockPattern>) = TimeLanguagePack(
        language = SupportedLanguage.FRENCH,
        locale = SupportedLanguage.FRENCH.locale,
        keywordTimes = loadKeywordTimes(SupportedLanguage.FRENCH),
        clockPatterns = common + listOf(
            clockWithPrefix(
                prefix = "(?:à|a|vers(?:\\s+les?)?)",
                unit = "h|heures?",
                period = "du matin|de l['’]après-midi|de l['’]apres-midi|du soir|de la nuit",
                aliases = PeriodAliases.frenchPeriods,
                priority = 104,
            ),
            clockWithUnit("h|heures?", priority = 103),
            clockWithPeriod(
                period = "du matin|de l['’]après-midi|de l['’]apres-midi|du soir|de la nuit",
                aliases = PeriodAliases.frenchPeriods,
                priority = 88,
            ),
        ),
        relativeDateRules = listOf(
            relativeDate("après[- ]demain|apres[- ]demain", 2),
            relativeDate("aujourd['’]hui", 0),
            relativeDate("demain", 1),
        ),
        relativeDayRules = listOf(
            relativeDays("dans\\s+(?<amount>\\d+)\\s+(?<unit>jours?|semaines?)", mapOf(
                "jour" to 1,
                "jours" to 1,
                "semaine" to 7,
                "semaines" to 7,
            )),
        ),
        relativeDurationRules = listOf(
            relativeDuration("dans\\s+(?<amount>\\d+)\\s+(?<unit>heures?|minutes?)", mapOf(
                "heure" to DurationUnit.HOURS,
                "heures" to DurationUnit.HOURS,
                "minute" to DurationUnit.MINUTES,
                "minutes" to DurationUnit.MINUTES,
            )),
        ),
        nextWords = setOf("prochain", "prochaine", "prochains", "prochaines"),
        fromPrefixes = listOf(
            Regex("(?iu)(?<![\\p{L}\\p{N}])(?:à\\s+partir\\s+de|a\\s+partir\\s+de|dès|des)\\s+$"),
        ),
        fromSuffixes = emptyList(),
    )

    private fun italianPack(common: List<ClockPattern>) = TimeLanguagePack(
        language = SupportedLanguage.ITALIAN,
        locale = SupportedLanguage.ITALIAN.locale,
        keywordTimes = loadKeywordTimes(SupportedLanguage.ITALIAN),
        clockPatterns = common + listOf(
            clockWithPrefix(
                prefix = "(?:alle?|verso\\s+le|ore)",
                unit = "ore",
                period = "di mattina|del mattino|di pomeriggio|del pomeriggio|di sera|di notte",
                aliases = PeriodAliases.italianPeriods,
                priority = 104,
            ),
            clockWithUnit("ore", priority = 103),
            clockWithPeriod(
                period = "di mattina|del mattino|di pomeriggio|del pomeriggio|di sera|di notte",
                aliases = PeriodAliases.italianPeriods,
                priority = 88,
            ),
        ),
        relativeDateRules = listOf(
            relativeDate("dopodomani", 2),
            relativeDate("oggi", 0),
            relativeDate("domani", 1),
        ),
        relativeDayRules = listOf(
            relativeDays("(?:tra|fra)\\s+(?<amount>\\d+)\\s+(?<unit>giorni?|settimane?)", mapOf(
                "giorno" to 1,
                "giorni" to 1,
                "settimana" to 7,
                "settimane" to 7,
            )),
        ),
        relativeDurationRules = listOf(
            relativeDuration("(?:tra|fra)\\s+(?<amount>\\d+)\\s+(?<unit>ore|minuti?)", mapOf(
                "ora" to DurationUnit.HOURS,
                "ore" to DurationUnit.HOURS,
                "minuto" to DurationUnit.MINUTES,
                "minuti" to DurationUnit.MINUTES,
            )),
        ),
        nextWords = setOf("prossimo", "prossima", "prossimi", "prossime"),
        fromPrefixes = listOf(
            Regex("(?iu)(?<![\\p{L}\\p{N}])(?:a\\s+partire\\s+da|dalle?|dall['’])\\s+$"),
        ),
        fromSuffixes = emptyList(),
    )

    private fun spanishPack(common: List<ClockPattern>) = TimeLanguagePack(
        language = SupportedLanguage.SPANISH,
        locale = SupportedLanguage.SPANISH.locale,
        keywordTimes = loadKeywordTimes(SupportedLanguage.SPANISH),
        clockPatterns = common + listOf(
            clockWithPrefix(
                prefix = "(?:a|sobre)\\s+las?",
                unit = "horas?|h",
                period = "de la mañana|de la tarde|de la noche",
                aliases = PeriodAliases.spanishPeriods,
                priority = 104,
            ),
            clockWithUnit("horas?|h", priority = 103),
            clockWithPeriod(
                period = "de la mañana|de la tarde|de la noche",
                aliases = PeriodAliases.spanishPeriods,
                priority = 88,
            ),
        ),
        relativeDateRules = listOf(
            relativeDate("pasado\\s+mañana|pasado\\s+manana", 2, 92),
            relativeDate("hoy", 0),
            relativeDate("mañana|manana", 1),
        ),
        relativeDayRules = listOf(
            relativeDays("en\\s+(?<amount>\\d+)\\s+(?<unit>días?|dias?|semanas?)", mapOf(
                "día" to 1,
                "dias" to 1,
                "días" to 1,
                "semana" to 7,
                "semanas" to 7,
            )),
        ),
        relativeDurationRules = listOf(
            relativeDuration("en\\s+(?<amount>\\d+)\\s+(?<unit>horas?|minutos?)", mapOf(
                "hora" to DurationUnit.HOURS,
                "horas" to DurationUnit.HOURS,
                "minuto" to DurationUnit.MINUTES,
                "minutos" to DurationUnit.MINUTES,
            )),
        ),
        nextWords = setOf("próximo", "próxima", "próximos", "próximas", "proximo", "proxima"),
        fromPrefixes = listOf(
            Regex("(?iu)(?<![\\p{L}\\p{N}])(?:a\\s+partir\\s+de|desde)\\s+"),
        ),
        fromSuffixes = emptyList(),
    )

    private fun russianPack(common: List<ClockPattern>) = TimeLanguagePack(
        language = SupportedLanguage.RUSSIAN,
        locale = SupportedLanguage.RUSSIAN.locale,
        keywordTimes = loadKeywordTimes(SupportedLanguage.RUSSIAN),
        clockPatterns = common + listOf(
            clockWithPrefix(
                prefix = "в",
                unit = "час(?:а|ов)?",
                period = "утра|дня|вечера|ночи",
                aliases = PeriodAliases.russianPeriods,
                priority = 104,
            ),
            clockWithUnit("час(?:а|ов)?", priority = 103),
            clockWithPeriod(
                period = "утра|дня|вечера|ночи",
                aliases = PeriodAliases.russianPeriods,
                priority = 88,
            ),
        ),
        relativeDateRules = listOf(
            relativeDate("послезавтра", 2),
            relativeDate("сегодня", 0),
            relativeDate("завтра", 1),
        ),
        relativeDayRules = listOf(
            relativeDays("через\\s+(?<amount>\\d+)\\s+(?<unit>день|дня|дней|неделю|недели|недель)", mapOf(
                "день" to 1,
                "дня" to 1,
                "дней" to 1,
                "неделю" to 7,
                "недели" to 7,
                "недель" to 7,
            )),
        ),
        relativeDurationRules = listOf(
            relativeDuration("через\\s+(?<amount>\\d+)\\s+(?<unit>час|часа|часов|минуту|минуты|минут)", mapOf(
                "час" to DurationUnit.HOURS,
                "часа" to DurationUnit.HOURS,
                "часов" to DurationUnit.HOURS,
                "минуту" to DurationUnit.MINUTES,
                "минуты" to DurationUnit.MINUTES,
                "минут" to DurationUnit.MINUTES,
            )),
        ),
        nextWords = setOf("следующий", "следующая", "следующее", "следующие"),
        fromPrefixes = listOf(Regex("(?iu)(?<![\\p{L}\\p{N}])с\\s+$")),
        fromSuffixes = emptyList(),
    )

    private fun japanesePack(common: List<ClockPattern>) = TimeLanguagePack(
        language = SupportedLanguage.JAPANESE,
        locale = SupportedLanguage.JAPANESE.locale,
        keywordTimes = loadKeywordTimes(SupportedLanguage.JAPANESE),
        clockPatterns = common + listOf(
            cjkClock(
                period = "午前|午後|朝|昼|夕方|夜",
                unit = "時",
                minuteUnit = "分",
                aliases = PeriodAliases.japanesePeriods,
                priority = 104,
            ),
            cjkHalfClock(
                period = "午前|午後|朝|昼|夕方|夜",
                unit = "時",
                aliases = PeriodAliases.japanesePeriods,
                priority = 105,
            ),
        ),
        relativeDateRules = listOf(
            relativeDate("明後日|あさって", 2, 92, cjk = true),
            relativeDate("今日", 0, cjk = true),
            relativeDate("明日", 1, cjk = true),
        ),
        relativeDayRules = listOf(
            relativeDays("(?<amount>\\d+)\\s*(?<unit>日|週間)後", mapOf("日" to 1, "週間" to 7), cjk = true),
        ),
        relativeDurationRules = listOf(
            relativeDuration("(?<amount>\\d+)\\s*(?<unit>時間|分)後", mapOf(
                "時間" to DurationUnit.HOURS,
                "分" to DurationUnit.MINUTES,
            ), cjk = true),
        ),
        nextWords = setOf("次の", "来週"),
        fromPrefixes = listOf(Regex("から\\s*")),
        fromSuffixes = listOf(Regex("\\s*から")),
    )

    private fun chinesePack(common: List<ClockPattern>) = TimeLanguagePack(
        language = SupportedLanguage.CHINESE,
        locale = SupportedLanguage.CHINESE.locale,
        keywordTimes = loadKeywordTimes(SupportedLanguage.CHINESE),
        clockPatterns = common + listOf(
            cjkClock(
                period = "凌晨|早上|上午|中午|下午|傍晚|晚上",
                unit = "点|點",
                minuteUnit = "分",
                aliases = PeriodAliases.chinesePeriods,
                priority = 104,
            ),
            cjkHalfClock(
                period = "凌晨|早上|上午|中午|下午|傍晚|晚上",
                unit = "点|點",
                aliases = PeriodAliases.chinesePeriods,
                priority = 105,
            ),
        ),
        relativeDateRules = listOf(
            relativeDate("后天|後天", 2, 92, cjk = true),
            relativeDate("今天", 0, cjk = true),
            relativeDate("明天", 1, cjk = true),
        ),
        relativeDayRules = listOf(
            relativeDays("(?<amount>\\d+)\\s*(?<unit>天|日|周|週|星期|礼拜|禮拜)(?:后|後)", mapOf(
                "天" to 1,
                "日" to 1,
                "周" to 7,
                "週" to 7,
                "星期" to 7,
                "礼拜" to 7,
                "禮拜" to 7,
            ), cjk = true),
        ),
        relativeDurationRules = listOf(
            relativeDuration("(?<amount>\\d+)\\s*(?<unit>小时|小時|分钟|分鐘)(?:后|後)", mapOf(
                "小时" to DurationUnit.HOURS,
                "小時" to DurationUnit.HOURS,
                "分钟" to DurationUnit.MINUTES,
                "分鐘" to DurationUnit.MINUTES,
            ), cjk = true),
        ),
        nextWords = setOf("下一个", "下週", "下周"),
        fromPrefixes = listOf(Regex("从|從")),
        fromSuffixes = listOf(Regex("之后|之後")),
    )

    private fun koreanPack(common: List<ClockPattern>) = TimeLanguagePack(
        language = SupportedLanguage.KOREAN,
        locale = SupportedLanguage.KOREAN.locale,
        keywordTimes = loadKeywordTimes(SupportedLanguage.KOREAN),
        clockPatterns = common + listOf(
            cjkClock(
                period = "오전|오후|아침|낮|저녁|밤",
                unit = "시",
                minuteUnit = "분",
                aliases = PeriodAliases.koreanPeriods,
                priority = 104,
            ),
            cjkHalfClock(
                period = "오전|오후|아침|낮|저녁|밤",
                unit = "시",
                half = "반",
                aliases = PeriodAliases.koreanPeriods,
                priority = 105,
            ),
        ),
        relativeDateRules = listOf(
            relativeDate("모레", 2, 92, cjk = true),
            relativeDate("오늘", 0, cjk = true),
            relativeDate("내일", 1, cjk = true),
        ),
        relativeDayRules = listOf(
            relativeDays("(?<amount>\\d+)\\s*(?<unit>일|주)\\s*후", mapOf("일" to 1, "주" to 7), cjk = true),
        ),
        relativeDurationRules = listOf(
            relativeDuration("(?<amount>\\d+)\\s*(?<unit>시간|분)\\s*후", mapOf(
                "시간" to DurationUnit.HOURS,
                "분" to DurationUnit.MINUTES,
            ), cjk = true),
        ),
        nextWords = setOf("다음", "다음 주"),
        fromPrefixes = listOf(Regex("부터\\s*")),
        fromSuffixes = listOf(Regex("\\s*부터")),
    )

    private fun clockWithUnit(
        unit: String,
        period: String? = null,
        prefix: String? = null,
        aliases: Map<String, HourModifier> = emptyMap(),
        priority: Int,
    ): ClockPattern {
        val suffix = if (period == null) "" else "(?:\\s*(?<period>$period))?"
        return ClockPattern(
            regex = Regex(
                "(?iu)(?<![\\p{L}\\p{N}])" +
                    (prefix ?: "") +
                    "(?<hour>\\d{1,2})(?:(?:[.:])(?<minute>\\d{1,2}))?\\s*(?:$unit)\\b" +
                    suffix + "(?![\\p{L}\\p{N}])",
            ),
            priority = priority,
            modifierPosition = if (period == null) {
                ModifierPosition.NONE
            } else {
                ModifierPosition.AFTER_HOUR
            },
            modifierAliases = aliases,
        )
    }

    private fun clockWithPeriod(
        period: String,
        aliases: Map<String, HourModifier>,
        priority: Int,
    ): ClockPattern {
        return ClockPattern(
            regex = Regex(
                "(?iu)(?<![\\p{L}\\p{N}])" +
                    "(?<hour>\\d{1,2})(?:(?:[.:])(?<minute>\\d{1,2}))?\\s+" +
                    "(?<period>$period)(?![\\p{L}\\p{N}])",
            ),
            priority = priority,
            modifierPosition = ModifierPosition.AFTER_HOUR,
            modifierAliases = aliases,
        )
    }

    private fun clockWithPrefix(
        prefix: String,
        priority: Int,
        unit: String? = null,
        period: String? = null,
        periodRequired: Boolean = false,
        aliases: Map<String, HourModifier> = emptyMap(),
    ): ClockPattern {
        val unitPart = unit?.let { "(?:\\s*(?:$it)\\b)?" } ?: ""
        val periodPart = period?.let {
            if (periodRequired) "\\s+(?<period>$it)" else "(?:\\s*(?<period>$it))?"
        } ?: ""
        return ClockPattern(
            regex = Regex(
                "(?iu)(?<![\\p{L}\\p{N}])$prefix\\s+" +
                    "(?<hour>\\d{1,2})(?:(?:[.:])(?<minute>\\d{1,2}))?" +
                    unitPart + periodPart + "(?![\\p{L}\\p{N}])",
            ),
            priority = priority,
            modifierPosition = if (period == null) {
                ModifierPosition.NONE
            } else {
                ModifierPosition.AFTER_HOUR
            },
            modifierAliases = aliases,
        )
    }

    private fun cjkClock(
        period: String,
        unit: String,
        minuteUnit: String,
        aliases: Map<String, HourModifier>,
        priority: Int,
    ): ClockPattern = ClockPattern(
        regex = Regex(
            "(?iu)(?<period>$period)?\\s*(?<hour>\\d{1,2})(?:$unit)" +
                "(?:\\s*(?<minute>\\d{1,2})$minuteUnit)?",
        ),
        priority = priority,
        modifierPosition = ModifierPosition.BEFORE_HOUR,
        modifierAliases = aliases,
    )

    private fun cjkHalfClock(
        period: String,
        unit: String,
        half: String = "半",
        aliases: Map<String, HourModifier>,
        priority: Int,
    ): ClockPattern = ClockPattern(
        regex = Regex("(?iu)(?<period>$period)?\\s*(?<hour>\\d{1,2})(?:$unit)\\s*(?<half>$half)"),
        priority = priority,
        modifierPosition = ModifierPosition.BEFORE_HOUR,
        modifierAliases = aliases,
        halfGroup = "half",
    )

    private fun relativeDate(
        expression: String,
        days: Long,
        priority: Int = 90,
        cjk: Boolean = false,
    ): RelativeDateRule = RelativeDateRule(
        regex = languageRegex(expression, cjk),
        daysFromToday = days,
        priority = priority,
    )

    private fun relativeDays(
        expression: String,
        multipliers: Map<String, Long>,
        cjk: Boolean = false,
    ): RelativeDayRule = RelativeDayRule(
        regex = languageRegex(expression, cjk),
        unitMultipliers = multipliers,
    )

    private fun relativeDuration(
        expression: String,
        units: Map<String, DurationUnit>,
        cjk: Boolean = false,
    ): RelativeDurationRule = RelativeDurationRule(
        regex = languageRegex(expression, cjk),
        unitTypes = units,
    )

    private fun languageRegex(expression: String, cjk: Boolean): Regex = if (cjk) {
        Regex("(?iu)(?:$expression)")
    } else {
        Regex("(?iu)(?<![\\p{L}\\p{N}])(?:$expression)(?![\\p{L}\\p{N}])")
    }

    private fun loadKeywordTimes(language: SupportedLanguage): Map<String, LocalTime> {
        val resourceName = if (language == SupportedLanguage.ENGLISH) {
            "reminder_keyword_times.json"
        } else {
            "reminder_keyword_times_${language.languageTag}.json"
        }
        val stream = TimeLanguagePacks::class.java.classLoader
            ?.getResourceAsStream(resourceName)
            ?: TimeLanguagePacks::class.java.getResourceAsStream("/$resourceName")
            ?: error("Missing $resourceName resource")
        val json = stream.bufferedReader().use { it.readText() }
        val values = Regex("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"(\\d{2}):(\\d{2})\\\"")
            .findAll(json)
            .associate { match ->
                match.groupValues[1] to LocalTime.of(
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                )
            }
        require(values.isNotEmpty()) { "$resourceName contains no keyword times" }
        return values
            .mapKeys { (keyword, _) -> KeywordTimeDictionary.normalize(keyword) }
            .toSortedMap()
    }

    private object PeriodAliases {
        val englishPeriods = mapOf(
            "in the morning" to HourModifier.AM,
            "in the afternoon" to HourModifier.PM,
            "in the evening" to HourModifier.PM,
            "at night" to HourModifier.NIGHT,
            "tonight" to HourModifier.PM,
        )
        val germanPeriods = mapOf(
            "morgens" to HourModifier.AM,
            "vormittags" to HourModifier.AM,
            "mittags" to HourModifier.PM,
            "nachmittags" to HourModifier.PM,
            "abends" to HourModifier.PM,
            "nachts" to HourModifier.NIGHT,
        )
        val frenchPeriods = mapOf(
            "du matin" to HourModifier.AM,
            "de l'après-midi" to HourModifier.PM,
            "de l’après-midi" to HourModifier.PM,
            "du soir" to HourModifier.PM,
            "de la nuit" to HourModifier.NIGHT,
        )
        val italianPeriods = mapOf(
            "di mattina" to HourModifier.AM,
            "del mattino" to HourModifier.AM,
            "di pomeriggio" to HourModifier.PM,
            "del pomeriggio" to HourModifier.PM,
            "di sera" to HourModifier.PM,
            "di notte" to HourModifier.NIGHT,
        )
        val spanishPeriods = mapOf(
            "de la mañana" to HourModifier.AM,
            "de la tarde" to HourModifier.PM,
            "de la noche" to HourModifier.NIGHT,
        )
        val russianPeriods = mapOf(
            "утра" to HourModifier.AM,
            "дня" to HourModifier.PM,
            "вечера" to HourModifier.PM,
            "ночи" to HourModifier.NIGHT,
        )
        val japanesePeriods = mapOf(
            "午前" to HourModifier.AM,
            "朝" to HourModifier.AM,
            "午後" to HourModifier.PM,
            "昼" to HourModifier.PM,
            "夕方" to HourModifier.PM,
            "夜" to HourModifier.NIGHT,
        )
        val chinesePeriods = mapOf(
            "凌晨" to HourModifier.AM,
            "早上" to HourModifier.AM,
            "上午" to HourModifier.AM,
            "中午" to HourModifier.PM,
            "下午" to HourModifier.PM,
            "傍晚" to HourModifier.PM,
            "晚上" to HourModifier.NIGHT,
        )
        val koreanPeriods = mapOf(
            "오전" to HourModifier.AM,
            "아침" to HourModifier.AM,
            "낮" to HourModifier.PM,
            "오후" to HourModifier.PM,
            "저녁" to HourModifier.PM,
            "밤" to HourModifier.NIGHT,
        )
    }
}
