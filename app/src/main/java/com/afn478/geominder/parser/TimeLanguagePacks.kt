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
    // The grammar intentionally covers compositional CJK numerals. Irregular lexical shorthand
    // such as 廿 or 卅 is left out rather than approximated by a regular expression.
    private const val CJK_NUMERAL_CHARACTERS = "〇零一二三四五六七八九十百千万萬亿億兆两兩壱弐参拾"
    private const val CLOCK_TEXT_END =
        "(?![\\p{L}\\p{N}]|[:.：]\\d|\\s+\\d)"
    private val CJK_AMOUNT_TOKEN = "(?:\\d+|[$CJK_NUMERAL_CHARACTERS]+)"
    private val CJK_CLOCK_NUMBER_TOKEN = "(?:\\d{1,2}|[$CJK_NUMERAL_CHARACTERS]+)"
    private val cache = ConcurrentHashMap<SupportedLanguage, TimeLanguagePack>()
    private val SINGLE_DURATION_COMPONENTS = listOf(
        DurationComponent(amountGroup = "amount", unitGroup = "unit"),
    )
    private val COMBINED_DURATION_COMPONENTS = listOf(
        DurationComponent(amountGroup = "amount", unitGroup = "unit"),
        DurationComponent(amountGroup = "amount2", unitGroup = "unit2", required = false),
    )
    private val COMPACT_DURATION_COMPONENTS = listOf(
        DurationComponent(amountGroup = "amount", unitGroup = "unit"),
        DurationComponent(
            amountGroup = "amount2",
            unitGroup = "unit2",
            required = false,
            defaultUnit = DurationUnit.MINUTES,
        ),
    )

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
            regex = Regex(
                "(?<![\\d.:：])(?<hour>\\d{1,2})[:.：](?<minute>\\d{1,2})" +
                    "(?!\\d|[:：.]\\d)",
            ),
            priority = 100,
        ),
    )

    private fun englishPack(common: List<ClockPattern>) = TimeLanguagePack(
        language = SupportedLanguage.ENGLISH,
        locale = SupportedLanguage.ENGLISH.locale,
        keywordTimes = loadKeywordTimes(SupportedLanguage.ENGLISH),
        clockPatterns = common + listOf(
            clockWithLeadingPeriod(
                period = periodExpression(PeriodAliases.englishDayParts),
                prefix = "at",
                aliases = PeriodAliases.englishDayParts,
                priority = 106,
            ),
            ClockPattern(
                regex = Regex(
                    "(?iu)(?<![\\p{L}\\p{N}])(?:at\\s+)?" +
                        "(?<hour>\\d{1,2})(?:[:.：](?<minute>\\d{1,2}))?\\s*" +
                        "(?<period>a\\.?m\\.?|p\\.?m\\.?)$CLOCK_TEXT_END",
                ),
                priority = 105,
                modifierPosition = ModifierPosition.AFTER_HOUR,
                modifierAliases = PeriodAliases.englishPeriods,
                defaultModifier = HourModifier.AM,
            ),
            clockWithPrefix(prefix = "at", priority = 101),
            ClockPattern(
                regex = Regex(
                    "(?iu)(?<![\\p{L}\\p{N}])at\\s+" +
                        "(?<hour>2[0-3]|1\\d|0?\\d)(?![\\d.:])\\b" +
                        "(?!\\s*(?:a\\.?m\\.?|p\\.?m\\.?)\\b)",
                ),
                priority = 85,
            ),
            clockWithPeriod(
                period = periodExpression(PeriodAliases.englishDayParts),
                aliases = PeriodAliases.englishDayParts,
                priority = 104,
            ),
            clockWithPrefix(
                prefix = "at",
                period = periodExpression(PeriodAliases.englishDayParts),
                aliases = PeriodAliases.englishDayParts,
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
            relativeDuration(
                "(?:in|after)\\s+(?<amount>\\d+)\\s*(?<unit>hours?|hrs?|h|minutes?|mins?|m)" +
                    "(?:\\s*(?:and|,\\s*and|,)?\\s*(?<amount2>\\d+)\\s*(?<unit2>hours?|hrs?|h|minutes?|mins?|m))?",
                mapOf(
                    "hour" to DurationUnit.HOURS,
                    "hours" to DurationUnit.HOURS,
                    "hr" to DurationUnit.HOURS,
                    "hrs" to DurationUnit.HOURS,
                    "h" to DurationUnit.HOURS,
                    "minute" to DurationUnit.MINUTES,
                    "minutes" to DurationUnit.MINUTES,
                    "min" to DurationUnit.MINUTES,
                    "mins" to DurationUnit.MINUTES,
                    "m" to DurationUnit.MINUTES,
                ),
                components = COMBINED_DURATION_COMPONENTS,
            ),
            relativeDuration(
                "(?<amount>\\d+)\\s*(?<unit>hours?|hrs?|h|minutes?|mins?|m)" +
                    "(?:\\s*(?:and|,\\s*and|,)?\\s*(?<amount2>\\d+)\\s*(?<unit2>hours?|hrs?|h|minutes?|mins?|m))?" +
                    "\\s+from\\s+now",
                mapOf(
                    "hour" to DurationUnit.HOURS,
                    "hours" to DurationUnit.HOURS,
                    "hr" to DurationUnit.HOURS,
                    "hrs" to DurationUnit.HOURS,
                    "h" to DurationUnit.HOURS,
                    "minute" to DurationUnit.MINUTES,
                    "minutes" to DurationUnit.MINUTES,
                    "min" to DurationUnit.MINUTES,
                    "mins" to DurationUnit.MINUTES,
                    "m" to DurationUnit.MINUTES,
                ),
                components = COMBINED_DURATION_COMPONENTS,
            ),
            compactDuration(
                prefix = "in|after",
                unitExpression = "hours?|hrs?|h",
                units = mapOf(
                    "hour" to DurationUnit.HOURS,
                    "hours" to DurationUnit.HOURS,
                    "hr" to DurationUnit.HOURS,
                    "hrs" to DurationUnit.HOURS,
                    "h" to DurationUnit.HOURS,
                ),
                connector = "and|,\\s*and|,",
            ),
            compactDuration(
                unitExpression = "hours?|hrs?|h",
                units = mapOf(
                    "hour" to DurationUnit.HOURS,
                    "hours" to DurationUnit.HOURS,
                    "hr" to DurationUnit.HOURS,
                    "hrs" to DurationUnit.HOURS,
                    "h" to DurationUnit.HOURS,
                ),
                connector = "and|,\\s*and|,",
                suffix = "\\s+from\\s+now",
            ),
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
            clockWithLeadingPeriodAfterMinute(
                period = periodExpression(PeriodAliases.germanPeriods),
                prefix = "(?:um|gegen)",
                unit = "Uhr",
                aliases = PeriodAliases.germanPeriods,
                priority = 107,
            ),
            clockWithLeadingPeriod(
                period = periodExpression(PeriodAliases.germanPeriods),
                prefix = "(?:um|gegen)",
                unit = "Uhr",
                aliases = PeriodAliases.germanPeriods,
                priority = 106,
            ),
            clockWithUnit(
                unit = "Uhr",
                period = periodExpression(PeriodAliases.germanPeriods),
                prefix = "(?:um|gegen)\\s+",
                aliases = PeriodAliases.germanPeriods,
                priority = 104,
            ),
            clockWithUnitAfterMinute(
                unit = "Uhr",
                period = periodExpression(PeriodAliases.germanPeriods),
                prefix = "(?:um|gegen)\\s+",
                aliases = PeriodAliases.germanPeriods,
                priority = 105,
            ),
            clockWithUnit(
                unit = "Uhr",
                period = periodExpression(PeriodAliases.germanPeriods),
                aliases = PeriodAliases.germanPeriods,
                priority = 103,
            ),
            clockWithUnitAfterMinute(
                unit = "Uhr",
                period = periodExpression(PeriodAliases.germanPeriods),
                aliases = PeriodAliases.germanPeriods,
                priority = 104,
            ),
            clockWithPeriod(
                period = periodExpression(PeriodAliases.germanPeriods),
                aliases = PeriodAliases.germanPeriods,
                priority = 102,
            ),
            clockWithPrefix(
                prefix = "(?:um|gegen)",
                unit = "Uhr",
                period = periodExpression(PeriodAliases.germanPeriods),
                aliases = PeriodAliases.germanPeriods,
                priority = 102,
            ),
            clockWithPrefix("(?:um|gegen)", priority = 101),
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
            relativeDuration(
                "(?:in|nach)\\s+(?<amount>\\d+)\\s*(?<unit>stunden?|std\\.?|h|minuten?|min\\.?|m)" +
                    "(?:\\s*(?:und|,\\s*und|,)?\\s*(?<amount2>\\d+)\\s*(?<unit2>stunden?|std\\.?|h|minuten?|min\\.?|m))?",
                mapOf(
                    "stunde" to DurationUnit.HOURS,
                    "stunden" to DurationUnit.HOURS,
                    "std" to DurationUnit.HOURS,
                    "std." to DurationUnit.HOURS,
                    "h" to DurationUnit.HOURS,
                    "minute" to DurationUnit.MINUTES,
                    "minuten" to DurationUnit.MINUTES,
                    "min" to DurationUnit.MINUTES,
                    "min." to DurationUnit.MINUTES,
                    "m" to DurationUnit.MINUTES,
                ),
                components = COMBINED_DURATION_COMPONENTS,
            ),
            compactDuration(
                prefix = "in|nach",
                unitExpression = "stunden?|std\\.?|h",
                units = mapOf(
                    "stunde" to DurationUnit.HOURS,
                    "stunden" to DurationUnit.HOURS,
                    "std" to DurationUnit.HOURS,
                    "std." to DurationUnit.HOURS,
                    "h" to DurationUnit.HOURS,
                ),
                connector = "und|,\\s*und|,",
            ),
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
            clockWithLeadingPeriodAfterMinute(
                period = periodExpression(PeriodAliases.frenchPeriods),
                prefix = "(?:à|a|vers(?:\\s+les?)?)",
                unit = "h|heures?",
                aliases = PeriodAliases.frenchPeriods,
                priority = 107,
            ),
            clockWithLeadingPeriod(
                period = periodExpression(PeriodAliases.frenchPeriods),
                prefix = "(?:à|a|vers(?:\\s+les?)?)",
                unit = "h|heures?",
                aliases = PeriodAliases.frenchPeriods,
                priority = 106,
            ),
            clockWithPrefix(
                prefix = "(?:à|a|vers(?:\\s+les?)?)",
                unit = "h|heures?",
                period = periodExpression(PeriodAliases.frenchPeriods),
                aliases = PeriodAliases.frenchPeriods,
                priority = 104,
            ),
            clockWithUnitAfterMinute(
                unit = "h|heures?",
                prefix = "(?:à|a|vers(?:\\s+les?)?)\\s+",
                period = periodExpression(PeriodAliases.frenchPeriods),
                aliases = PeriodAliases.frenchPeriods,
                priority = 105,
            ),
            clockWithUnit(
                unit = "h|heures?",
                period = periodExpression(PeriodAliases.frenchPeriods),
                aliases = PeriodAliases.frenchPeriods,
                priority = 103,
            ),
            clockWithUnitAfterMinute(
                unit = "h|heures?",
                period = periodExpression(PeriodAliases.frenchPeriods),
                aliases = PeriodAliases.frenchPeriods,
                priority = 104,
            ),
            clockWithPeriod(
                period = periodExpression(PeriodAliases.frenchPeriods),
                aliases = PeriodAliases.frenchPeriods,
                priority = 102,
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
            relativeDuration(
                "(?:dans|d['’]ici|après|apres)\\s+(?<amount>\\d+)\\s*(?<unit>heures?|h|minutes?|min)" +
                    "(?:\\s*(?:et|,\\s*et|,)?\\s*(?<amount2>\\d+)\\s*(?<unit2>heures?|h|minutes?|min))?",
                mapOf(
                    "heure" to DurationUnit.HOURS,
                    "heures" to DurationUnit.HOURS,
                    "h" to DurationUnit.HOURS,
                    "minute" to DurationUnit.MINUTES,
                    "minutes" to DurationUnit.MINUTES,
                    "min" to DurationUnit.MINUTES,
                ),
                components = COMBINED_DURATION_COMPONENTS,
            ),
            compactDuration(
                prefix = "dans|d['’]ici|après|apres",
                unitExpression = "heures?|h",
                units = mapOf(
                    "heure" to DurationUnit.HOURS,
                    "heures" to DurationUnit.HOURS,
                    "h" to DurationUnit.HOURS,
                ),
                connector = "et|,\\s*et|,",
            ),
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
            clockWithLeadingPeriodAfterMinute(
                period = periodExpression(PeriodAliases.italianPeriods),
                prefix = "(?:alle?|verso\\s+le|ore)",
                unit = "or(?:a|e)",
                aliases = PeriodAliases.italianPeriods,
                priority = 107,
            ),
            clockWithLeadingPeriod(
                period = periodExpression(PeriodAliases.italianPeriods),
                prefix = "(?:alle?|verso\\s+le|ore)",
                unit = "or(?:a|e)",
                aliases = PeriodAliases.italianPeriods,
                priority = 106,
            ),
            clockWithPrefix(
                prefix = "(?:alle?|verso\\s+le|ore)",
                unit = "or(?:a|e)",
                period = periodExpression(PeriodAliases.italianPeriods),
                aliases = PeriodAliases.italianPeriods,
                priority = 104,
            ),
            clockWithUnitAfterMinute(
                unit = "or(?:a|e)",
                prefix = "(?:alle?|verso\\s+le|ore)\\s+",
                period = periodExpression(PeriodAliases.italianPeriods),
                aliases = PeriodAliases.italianPeriods,
                priority = 105,
            ),
            clockWithUnit(
                unit = "or(?:a|e)",
                period = periodExpression(PeriodAliases.italianPeriods),
                aliases = PeriodAliases.italianPeriods,
                priority = 103,
            ),
            clockWithUnitAfterMinute(
                unit = "or(?:a|e)",
                period = periodExpression(PeriodAliases.italianPeriods),
                aliases = PeriodAliases.italianPeriods,
                priority = 104,
            ),
            clockWithPeriod(
                period = periodExpression(PeriodAliases.italianPeriods),
                aliases = PeriodAliases.italianPeriods,
                priority = 102,
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
            relativeDuration(
                "(?:tra|fra|dopo)\\s+(?<amount>\\d+)\\s*(?<unit>or(?:a|e)|h|minut(?:o|i)|min)" +
                    "(?:\\s*(?:e|,\\s*e|,)?\\s*(?<amount2>\\d+)\\s*(?<unit2>or(?:a|e)|h|minut(?:o|i)|min))?",
                mapOf(
                    "ora" to DurationUnit.HOURS,
                    "ore" to DurationUnit.HOURS,
                    "h" to DurationUnit.HOURS,
                    "minuto" to DurationUnit.MINUTES,
                    "minuti" to DurationUnit.MINUTES,
                    "min" to DurationUnit.MINUTES,
                ),
                components = COMBINED_DURATION_COMPONENTS,
            ),
            compactDuration(
                prefix = "tra|fra|dopo",
                unitExpression = "or(?:a|e)|h",
                units = mapOf(
                    "ora" to DurationUnit.HOURS,
                    "ore" to DurationUnit.HOURS,
                    "h" to DurationUnit.HOURS,
                ),
                connector = "e|,\\s*e|,",
            ),
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
            clockWithLeadingPeriodAfterMinute(
                period = periodExpression(PeriodAliases.spanishPeriods),
                prefix = "(?:a|sobre)\\s+las?",
                unit = "horas?|h",
                aliases = PeriodAliases.spanishPeriods,
                priority = 107,
            ),
            clockWithLeadingPeriod(
                period = periodExpression(PeriodAliases.spanishPeriods),
                prefix = "(?:a|sobre)\\s+las?",
                unit = "horas?|h",
                aliases = PeriodAliases.spanishPeriods,
                priority = 106,
            ),
            clockWithPrefix(
                prefix = "(?:a|sobre)\\s+las?",
                unit = "horas?|h",
                period = periodExpression(PeriodAliases.spanishPeriods),
                aliases = PeriodAliases.spanishPeriods,
                priority = 104,
            ),
            clockWithUnitAfterMinute(
                unit = "horas?|h",
                prefix = "(?:a|sobre)\\s+las?\\s+",
                period = periodExpression(PeriodAliases.spanishPeriods),
                aliases = PeriodAliases.spanishPeriods,
                priority = 105,
            ),
            clockWithUnit(
                unit = "horas?|h",
                period = periodExpression(PeriodAliases.spanishPeriods),
                aliases = PeriodAliases.spanishPeriods,
                priority = 103,
            ),
            clockWithUnitAfterMinute(
                unit = "horas?|h",
                period = periodExpression(PeriodAliases.spanishPeriods),
                aliases = PeriodAliases.spanishPeriods,
                priority = 104,
            ),
            clockWithPeriod(
                period = periodExpression(PeriodAliases.spanishPeriods),
                aliases = PeriodAliases.spanishPeriods,
                priority = 102,
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
            relativeDuration(
                "(?:en|dentro\\s+de|después\\s+de|despues\\s+de)\\s+(?<amount>\\d+)\\s*(?<unit>horas?|h|minutos?|min)" +
                    "(?:\\s*(?:y|,\\s*y|,)?\\s*(?<amount2>\\d+)\\s*(?<unit2>horas?|h|minutos?|min))?",
                mapOf(
                    "hora" to DurationUnit.HOURS,
                    "horas" to DurationUnit.HOURS,
                    "h" to DurationUnit.HOURS,
                    "minuto" to DurationUnit.MINUTES,
                    "minutos" to DurationUnit.MINUTES,
                    "min" to DurationUnit.MINUTES,
                ),
                components = COMBINED_DURATION_COMPONENTS,
            ),
            compactDuration(
                prefix = "en|dentro\\s+de|después\\s+de|despues\\s+de",
                unitExpression = "horas?|h",
                units = mapOf(
                    "hora" to DurationUnit.HOURS,
                    "horas" to DurationUnit.HOURS,
                    "h" to DurationUnit.HOURS,
                ),
                connector = "y|,\\s*y|,",
            ),
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
            clockWithLeadingPeriodAfterMinute(
                period = periodExpression(PeriodAliases.russianPeriods),
                prefix = "в",
                unit = "час(?:а|ов)?|ч\\.?",
                aliases = PeriodAliases.russianPeriods,
                priority = 107,
            ),
            clockWithLeadingPeriod(
                period = periodExpression(PeriodAliases.russianPeriods),
                prefix = "в",
                unit = "час(?:а|ов)?|ч\\.?",
                aliases = PeriodAliases.russianPeriods,
                priority = 106,
            ),
            clockWithPrefix(
                prefix = "в",
                unit = "час(?:а|ов)?|ч\\.?",
                period = periodExpression(PeriodAliases.russianPeriods),
                aliases = PeriodAliases.russianPeriods,
                priority = 104,
            ),
            clockWithUnitAfterMinute(
                unit = "час(?:а|ов)?|ч\\.?",
                prefix = "в\\s+",
                period = periodExpression(PeriodAliases.russianPeriods),
                aliases = PeriodAliases.russianPeriods,
                priority = 105,
            ),
            clockWithUnit(
                unit = "час(?:а|ов)?|ч\\.?",
                period = periodExpression(PeriodAliases.russianPeriods),
                aliases = PeriodAliases.russianPeriods,
                priority = 103,
            ),
            clockWithUnitAfterMinute(
                unit = "час(?:а|ов)?|ч\\.?",
                period = periodExpression(PeriodAliases.russianPeriods),
                aliases = PeriodAliases.russianPeriods,
                priority = 104,
            ),
            clockWithPeriod(
                period = periodExpression(PeriodAliases.russianPeriods),
                aliases = PeriodAliases.russianPeriods,
                priority = 102,
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
            relativeDuration(
                "(?:через|спустя)\\s+(?<amount>\\d+)\\s*(?<unit>час(?:а|ов)?|ч\\.?|минут(?:у|ы)?|мин\\.?)" +
                    "(?:\\s*(?:и|,\\s*и|,)?\\s*(?<amount2>\\d+)\\s*(?<unit2>час(?:а|ов)?|ч\\.?|минут(?:у|ы)?|мин\\.?))?",
                mapOf(
                    "час" to DurationUnit.HOURS,
                    "часа" to DurationUnit.HOURS,
                    "часов" to DurationUnit.HOURS,
                    "ч" to DurationUnit.HOURS,
                    "ч." to DurationUnit.HOURS,
                    "минуту" to DurationUnit.MINUTES,
                    "минуты" to DurationUnit.MINUTES,
                    "минут" to DurationUnit.MINUTES,
                    "мин" to DurationUnit.MINUTES,
                    "мин." to DurationUnit.MINUTES,
                ),
                components = COMBINED_DURATION_COMPONENTS,
            ),
            compactDuration(
                prefix = "через|спустя",
                unitExpression = "час(?:а|ов)?|ч\\.?",
                units = mapOf(
                    "час" to DurationUnit.HOURS,
                    "часа" to DurationUnit.HOURS,
                    "часов" to DurationUnit.HOURS,
                    "ч" to DurationUnit.HOURS,
                    "ч." to DurationUnit.HOURS,
                ),
                connector = "и|,\\s*и|,",
            ),
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
            cjkColonClock(
                period = periodExpression(PeriodAliases.japanesePeriods),
                aliases = PeriodAliases.japanesePeriods,
                priority = 105,
            ),
            cjkClock(
                period = periodExpression(PeriodAliases.japanesePeriods),
                unit = "時(?!間)",
                minuteUnit = "分",
                aliases = PeriodAliases.japanesePeriods,
                priority = 104,
            ),
            cjkHalfClock(
                period = periodExpression(PeriodAliases.japanesePeriods),
                unit = "時(?!間)",
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
            relativeDays(
                "(?<amount>$CJK_AMOUNT_TOKEN)\\s*(?<unit>日間|日|週間|週)後",
                mapOf(
                    "日間" to 1,
                    "日" to 1,
                    "週間" to 7,
                    "週" to 7,
                ),
                cjk = true,
            ),
        ),
        relativeDurationRules = listOf(
            relativeDuration(
                "(?<amount>$CJK_AMOUNT_TOKEN)\\s*(?<unit>時間|分)" +
                    "(?:\\s*(?:と|、|,\\s*と|,)?\\s*(?<amount2>$CJK_AMOUNT_TOKEN)\\s*(?<unit2>時間|分))?" +
                    "\\s*(?:後|あと)",
                mapOf(
                    "時間" to DurationUnit.HOURS,
                    "分" to DurationUnit.MINUTES,
                ),
                cjk = true,
                components = COMBINED_DURATION_COMPONENTS,
            ),
            relativeDuration(
                "(?:あと|今から)\\s*(?<amount>$CJK_AMOUNT_TOKEN)\\s*(?<unit>時間|分)" +
                    "(?:\\s*(?:と|、|,\\s*と|,)?\\s*(?<amount2>$CJK_AMOUNT_TOKEN)\\s*(?<unit2>時間|分))?" +
                    "\\s*(?:後|あと)?",
                mapOf(
                    "時間" to DurationUnit.HOURS,
                    "分" to DurationUnit.MINUTES,
                ),
                cjk = true,
                components = COMBINED_DURATION_COMPONENTS,
            ),
            relativeDuration(
                "(?<amount>\\d+)\\s*(?<unit>jikan|fun|pun|時間|分)" +
                    "(?:\\s*(?:to|and|と|、|,\\s*(?:to|and)|,)?\\s*(?<amount2>\\d+)\\s*(?<unit2>jikan|fun|pun|時間|分))?" +
                    "\\s*(?:ato|go|後|あと)",
                mapOf(
                    "jikan" to DurationUnit.HOURS,
                    "時間" to DurationUnit.HOURS,
                    "fun" to DurationUnit.MINUTES,
                    "pun" to DurationUnit.MINUTES,
                    "分" to DurationUnit.MINUTES,
                ),
                components = COMBINED_DURATION_COMPONENTS,
            ),
            relativeDuration(
                "(?:ato|ima\\s+kara)\\s+(?<amount>\\d+)\\s*(?<unit>jikan|fun|pun|時間|分)" +
                    "(?:\\s*(?:to|and|と|、|,\\s*(?:to|and)|,)?\\s*(?<amount2>\\d+)\\s*(?<unit2>jikan|fun|pun|時間|分))?" +
                    "\\s*(?:ato|go)?",
                mapOf(
                    "jikan" to DurationUnit.HOURS,
                    "時間" to DurationUnit.HOURS,
                    "fun" to DurationUnit.MINUTES,
                    "pun" to DurationUnit.MINUTES,
                    "分" to DurationUnit.MINUTES,
                ),
                components = COMBINED_DURATION_COMPONENTS,
            ),
            compactDuration(
                unitExpression = "時間",
                units = mapOf(
                    "時間" to DurationUnit.HOURS,
                ),
                connector = "と|、|,\\s*と|,",
                suffix = "\\s*(?:後|あと)",
                cjk = true,
            ),
            compactDuration(
                prefix = "あと|今から",
                unitExpression = "時間",
                units = mapOf(
                    "時間" to DurationUnit.HOURS,
                ),
                connector = "と|、|,\\s*と|,",
                cjk = true,
            ),
            compactDuration(
                unitExpression = "jikan|時間",
                units = mapOf(
                    "jikan" to DurationUnit.HOURS,
                    "時間" to DurationUnit.HOURS,
                ),
                connector = "to|and|と|、|,\\s*to|,",
                suffix = "\\s+(?:ato|go)",
            ),
            compactDuration(
                prefix = "ato|ima\\s+kara",
                unitExpression = "jikan|時間",
                units = mapOf(
                    "jikan" to DurationUnit.HOURS,
                    "時間" to DurationUnit.HOURS,
                ),
                connector = "to|and|と|、|,\\s*to|,",
            ),
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
            cjkColonClock(
                period = periodExpression(PeriodAliases.chinesePeriods),
                aliases = PeriodAliases.chinesePeriods,
                priority = 105,
            ),
            cjkClock(
                period = periodExpression(PeriodAliases.chinesePeriods),
                unit = "点(?:钟)?|點(?:鐘)?",
                minuteUnit = "分",
                aliases = PeriodAliases.chinesePeriods,
                priority = 104,
            ),
            cjkHalfClock(
                period = periodExpression(PeriodAliases.chinesePeriods),
                unit = "点(?:钟)?|點(?:鐘)?",
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
            relativeDays("(?<amount>$CJK_AMOUNT_TOKEN)\\s*(?<unit>天|日|周|週|星期|礼拜|禮拜)(?:后|後)", mapOf(
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
            relativeDuration(
                "(?<amount>$CJK_AMOUNT_TOKEN)\\s*(?<unit>小时|小時|分钟|分鐘)" +
                    "(?:\\s*(?:和|及|、|，|，\\s*(?:和|及)|,\\s*(?:和|及)|,)?\\s*(?<amount2>$CJK_AMOUNT_TOKEN)\\s*(?<unit2>小时|小時|分钟|分鐘))?" +
                    "\\s*(?:之后|之後|以后|以後|后|後)",
                mapOf(
                    "小时" to DurationUnit.HOURS,
                    "小時" to DurationUnit.HOURS,
                    "分钟" to DurationUnit.MINUTES,
                    "分鐘" to DurationUnit.MINUTES,
                ),
                cjk = true,
                components = COMBINED_DURATION_COMPONENTS,
            ),
            relativeDuration(
                "(?:再过|再過|还有|還有)\\s*(?<amount>$CJK_AMOUNT_TOKEN)\\s*(?<unit>小时|小時|分钟|分鐘)" +
                    "(?:\\s*(?:和|及|、|，|，\\s*(?:和|及)|,\\s*(?:和|及)|,)?\\s*(?<amount2>$CJK_AMOUNT_TOKEN)\\s*(?<unit2>小时|小時|分钟|分鐘))?",
                mapOf(
                    "小时" to DurationUnit.HOURS,
                    "小時" to DurationUnit.HOURS,
                    "分钟" to DurationUnit.MINUTES,
                    "分鐘" to DurationUnit.MINUTES,
                ),
                cjk = true,
                components = COMBINED_DURATION_COMPONENTS,
            ),
            compactDuration(
                unitExpression = "小时|小時",
                units = mapOf(
                    "小时" to DurationUnit.HOURS,
                    "小時" to DurationUnit.HOURS,
                ),
                connector = "和|及|、|，|，\\s*(?:和|及)|,\\s*(?:和|及)|,",
                suffix = "\\s*(?:之后|之後|以后|以後|后|後)",
                cjk = true,
            ),
            compactDuration(
                prefix = "再过|再過|还有|還有",
                unitExpression = "小时|小時",
                units = mapOf(
                    "小时" to DurationUnit.HOURS,
                    "小時" to DurationUnit.HOURS,
                ),
                connector = "和|及|、|，|，\\s*(?:和|及)|,\\s*(?:和|及)|,",
                cjk = true,
            ),
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
            cjkColonClock(
                period = periodExpression(PeriodAliases.koreanPeriods),
                aliases = PeriodAliases.koreanPeriods,
                priority = 105,
            ),
            cjkClock(
                period = periodExpression(PeriodAliases.koreanPeriods),
                unit = "시(?!간)",
                minuteUnit = "분",
                aliases = PeriodAliases.koreanPeriods,
                priority = 104,
            ),
            cjkHalfClock(
                period = periodExpression(PeriodAliases.koreanPeriods),
                unit = "시(?!간)",
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
            relativeDuration(
                "(?<amount>\\d+)\\s*(?<unit>시간|분)" +
                    "(?:\\s*(?:와|과|및|,\\s*(?:와|과|및)|,)?\\s*(?<amount2>\\d+)\\s*(?<unit2>시간|분))?" +
                    "\\s*(?:후|뒤)(?:에)?",
                mapOf(
                    "시간" to DurationUnit.HOURS,
                    "분" to DurationUnit.MINUTES,
                ),
                cjk = true,
                components = COMBINED_DURATION_COMPONENTS,
            ),
            relativeDuration(
                "앞으로\\s*(?<amount>\\d+)\\s*(?<unit>시간|분)" +
                    "(?:\\s*(?:와|과|및|,\\s*(?:와|과|및)|,)?\\s*(?<amount2>\\d+)\\s*(?<unit2>시간|분))?",
                mapOf(
                    "시간" to DurationUnit.HOURS,
                    "분" to DurationUnit.MINUTES,
                ),
                cjk = true,
                components = COMBINED_DURATION_COMPONENTS,
            ),
            compactDuration(
                unitExpression = "시간",
                units = mapOf(
                    "시간" to DurationUnit.HOURS,
                ),
                connector = "와|과|및|,\\s*와|,",
                suffix = "\\s*(?:후|뒤)(?:에)?",
                cjk = true,
            ),
            compactDuration(
                prefix = "앞으로",
                unitExpression = "시간",
                units = mapOf(
                    "시간" to DurationUnit.HOURS,
                ),
                connector = "와|과|및|,\\s*와|,",
                cjk = true,
            ),
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
                    "(?<hour>\\d{1,2})(?:(?:[.:：])(?<minute>\\d{1,2}))?\\s*(?:$unit)" +
                    suffix + CLOCK_TEXT_END,
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

    private fun clockWithUnitAfterMinute(
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
                    "(?<hour>\\d{1,2})\\s*(?:$unit)(?![\\p{L}])" +
                    "\\s*(?<minute>\\d{1,2})" +
                    suffix + CLOCK_TEXT_END,
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
                    "(?<hour>\\d{1,2})(?:(?:[.:：])(?<minute>\\d{1,2}))?\\s+" +
                    "(?<period>$period)$CLOCK_TEXT_END",
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
        val unitPart = unit?.let { "(?:\\s*(?:$it)(?![\\p{L}\\p{N}]))?" } ?: ""
        val periodPart = period?.let {
            if (periodRequired) "\\s+(?<period>$it)" else "(?:\\s*(?<period>$it))?"
        } ?: ""
        return ClockPattern(
            regex = Regex(
                    "(?iu)(?<![\\p{L}\\p{N}])$prefix\\s+" +
                    "(?<hour>\\d{1,2})(?:(?:[.:：])(?<minute>\\d{1,2}))?" +
                    unitPart + periodPart + CLOCK_TEXT_END,
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

    private fun clockWithLeadingPeriod(
        period: String,
        aliases: Map<String, HourModifier>,
        prefix: String? = null,
        unit: String? = null,
        priority: Int,
    ): ClockPattern {
        val prefixPart = prefix?.let { "(?:(?:$it)\\s+)?" } ?: ""
        val unitPart = unit?.let { "(?:\\s*(?:$it)(?![\\p{L}\\p{N}]))?" } ?: ""
        return ClockPattern(
            regex = Regex(
                "(?iu)(?<![\\p{L}\\p{N}])(?<period>$period)\\s+" +
                    prefixPart +
                    "(?<hour>\\d{1,2})(?:(?:[.:：])(?<minute>\\d{1,2}))?" +
                    unitPart + CLOCK_TEXT_END,
            ),
            priority = priority,
            modifierPosition = ModifierPosition.BEFORE_HOUR,
            modifierAliases = aliases,
        )
    }

    private fun clockWithLeadingPeriodAfterMinute(
        period: String,
        aliases: Map<String, HourModifier>,
        prefix: String? = null,
        unit: String,
        priority: Int,
    ): ClockPattern {
        val prefixPart = prefix?.let { "(?:(?:$it)\\s+)?" } ?: ""
        return ClockPattern(
            regex = Regex(
                "(?iu)(?<![\\p{L}\\p{N}])(?<period>$period)\\s+" +
                    prefixPart +
                    "(?<hour>\\d{1,2})\\s*(?:$unit)(?![\\p{L}\\p{N}])" +
                    "\\s*(?<minute>\\d{1,2})" + CLOCK_TEXT_END,
            ),
            priority = priority,
            modifierPosition = ModifierPosition.BEFORE_HOUR,
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
            "(?iu)(?<period>$period)?\\s*(?<![\\p{N}$CJK_NUMERAL_CHARACTERS])" +
                "(?<hour>$CJK_CLOCK_NUMBER_TOKEN)\\s*(?:$unit)" +
                "(?:\\s*(?<minute>$CJK_CLOCK_NUMBER_TOKEN)\\s*$minuteUnit)?" +
                "(?![\\p{N}$CJK_NUMERAL_CHARACTERS]|[:.：]\\d|\\s+\\d{1,2}[:.：]\\d)",
        ),
        priority = priority,
        modifierPosition = ModifierPosition.BEFORE_HOUR,
        modifierAliases = aliases,
    )

    private fun cjkColonClock(
        period: String,
        aliases: Map<String, HourModifier>,
        priority: Int,
    ): ClockPattern = ClockPattern(
        regex = Regex(
            "(?iu)(?<period>$period)\\s*(?<![\\p{N}$CJK_NUMERAL_CHARACTERS])" +
                "(?<hour>$CJK_CLOCK_NUMBER_TOKEN)[:.：](?<minute>$CJK_CLOCK_NUMBER_TOKEN)" +
                "(?![\\p{N}$CJK_NUMERAL_CHARACTERS]|[:.：]\\d)",
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
        regex = Regex(
            "(?iu)(?<period>$period)?\\s*(?<![\\p{N}$CJK_NUMERAL_CHARACTERS])" +
                "(?<hour>$CJK_CLOCK_NUMBER_TOKEN)\\s*(?:$unit)\\s*(?<half>$half)" +
                "(?![\\p{N}$CJK_NUMERAL_CHARACTERS]|[:.：]\\d)",
        ),
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

    private fun periodExpression(aliases: Map<String, HourModifier>): String = aliases.keys
        .sortedWith(compareByDescending<String> { it.length }.thenBy { it })
        .joinToString("|") { Regex.escape(it) }

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
        components: List<DurationComponent> = SINGLE_DURATION_COMPONENTS,
    ): RelativeDurationRule = RelativeDurationRule(
        regex = languageRegex(expression, cjk),
        unitTypes = units,
        components = components,
    )

    private fun compactDuration(
        prefix: String = "",
        unitExpression: String,
        units: Map<String, DurationUnit>,
        connector: String = "",
        suffix: String = "",
        cjk: Boolean = false,
    ): RelativeDurationRule {
        val prefixPart = if (prefix.isBlank()) {
            ""
        } else {
            "(?:$prefix)${if (cjk) "\\s*" else "\\s+"}"
        }
        val connectorPart = if (connector.isBlank()) {
            "\\s*"
        } else {
            "\\s*(?:$connector)?\\s*"
        }
        val amountToken = if (cjk) CJK_AMOUNT_TOKEN else "\\d+"
        return relativeDuration(
            prefixPart +
                "(?<amount>$amountToken)\\s*(?<unit>$unitExpression)" +
                connectorPart +
                "(?<amount2>$amountToken)" +
                suffix,
            units = units,
            cjk = cjk,
            components = COMPACT_DURATION_COMPONENTS,
        )
    }

    private fun languageRegex(expression: String, cjk: Boolean): Regex = if (cjk) {
        // Script-based languages do not have reliable word boundaries, so protect both Arabic
        // digits and adjacent compositional CJK numeral characters from partial matches.
        Regex(
            "(?iu)(?<![\\p{N}$CJK_NUMERAL_CHARACTERS])(?:$expression)" +
                "(?![\\p{N}$CJK_NUMERAL_CHARACTERS])",
        )
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
        val englishDayParts = mapOf(
            "in the morning" to HourModifier.AM,
            "in the afternoon" to HourModifier.PM,
            "in the evening" to HourModifier.PM,
            "at night" to HourModifier.NIGHT,
            "tonight" to HourModifier.PM,
        )
        val englishPeriods = englishDayParts + mapOf(
            "am" to HourModifier.AM,
            "am." to HourModifier.AM,
            "a.m" to HourModifier.AM,
            "a.m." to HourModifier.AM,
            "pm" to HourModifier.PM,
            "pm." to HourModifier.PM,
            "p.m" to HourModifier.PM,
            "p.m." to HourModifier.PM,
        )
        val germanPeriods = mapOf(
            "morgens" to HourModifier.AM,
            "vormittags" to HourModifier.AM,
            "mittags" to HourModifier.PM,
            "nachmittags" to HourModifier.PM,
            "abends" to HourModifier.PM,
            "nachts" to HourModifier.NIGHT,
            "am morgen" to HourModifier.AM,
            "am vormittag" to HourModifier.AM,
            "am mittag" to HourModifier.PM,
            "am nachmittag" to HourModifier.PM,
            "am abend" to HourModifier.PM,
            "nacht" to HourModifier.NIGHT,
            "in der nacht" to HourModifier.NIGHT,
            "mitternacht" to HourModifier.NIGHT,
        )
        val frenchPeriods = mapOf(
            "matin" to HourModifier.AM,
            "le matin" to HourModifier.AM,
            "du matin" to HourModifier.AM,
            "après-midi" to HourModifier.PM,
            "apres-midi" to HourModifier.PM,
            "l'après-midi" to HourModifier.PM,
            "l’après-midi" to HourModifier.PM,
            "l'apres-midi" to HourModifier.PM,
            "l’apres-midi" to HourModifier.PM,
            "de l'après-midi" to HourModifier.PM,
            "de l’après-midi" to HourModifier.PM,
            "de l'apres-midi" to HourModifier.PM,
            "de l’apres-midi" to HourModifier.PM,
            "soir" to HourModifier.PM,
            "le soir" to HourModifier.PM,
            "ce soir" to HourModifier.PM,
            "du soir" to HourModifier.PM,
            "nuit" to HourModifier.NIGHT,
            "la nuit" to HourModifier.NIGHT,
            "cette nuit" to HourModifier.NIGHT,
            "de la nuit" to HourModifier.NIGHT,
            "minuit" to HourModifier.NIGHT,
        )
        val italianPeriods = mapOf(
            "mattina" to HourModifier.AM,
            "mattino" to HourModifier.AM,
            "di mattina" to HourModifier.AM,
            "del mattino" to HourModifier.AM,
            "pomeriggio" to HourModifier.PM,
            "di pomeriggio" to HourModifier.PM,
            "del pomeriggio" to HourModifier.PM,
            "sera" to HourModifier.PM,
            "di sera" to HourModifier.PM,
            "notte" to HourModifier.NIGHT,
            "di notte" to HourModifier.NIGHT,
            "stasera" to HourModifier.PM,
        )
        val spanishPeriods = mapOf(
            "por la mañana" to HourModifier.AM,
            "por la manana" to HourModifier.AM,
            "de la mañana" to HourModifier.AM,
            "de la manana" to HourModifier.AM,
            "por la tarde" to HourModifier.PM,
            "de la tarde" to HourModifier.PM,
            "tarde" to HourModifier.PM,
            "por la noche" to HourModifier.NIGHT,
            "de la noche" to HourModifier.NIGHT,
            "noche" to HourModifier.NIGHT,
            "esta noche" to HourModifier.NIGHT,
        )
        val russianPeriods = mapOf(
            "утра" to HourModifier.AM,
            "утром" to HourModifier.AM,
            "утро" to HourModifier.AM,
            "дня" to HourModifier.PM,
            "днём" to HourModifier.PM,
            "днем" to HourModifier.PM,
            "вечера" to HourModifier.PM,
            "вечером" to HourModifier.PM,
            "вечер" to HourModifier.PM,
            "ночи" to HourModifier.NIGHT,
            "ночью" to HourModifier.NIGHT,
            "ночь" to HourModifier.NIGHT,
            "полночь" to HourModifier.NIGHT,
        )
        val japanesePeriods = mapOf(
            "午前" to HourModifier.AM,
            "朝" to HourModifier.AM,
            "あさ" to HourModifier.AM,
            "午後" to HourModifier.PM,
            "昼" to HourModifier.PM,
            "夕方" to HourModifier.PM,
            "晩" to HourModifier.PM,
            "夜" to HourModifier.NIGHT,
            "深夜" to HourModifier.NIGHT,
            "今夜" to HourModifier.NIGHT,
            "今晩" to HourModifier.NIGHT,
            "夜中" to HourModifier.NIGHT,
            "真夜中" to HourModifier.NIGHT,
        )
        val chinesePeriods = mapOf(
            "凌晨" to HourModifier.AM,
            "清晨" to HourModifier.AM,
            "早上" to HourModifier.AM,
            "早晨" to HourModifier.AM,
            "上午" to HourModifier.AM,
            "中午" to HourModifier.PM,
            "下午" to HourModifier.PM,
            "傍晚" to HourModifier.PM,
            "晚间" to HourModifier.PM,
            "晚上" to HourModifier.NIGHT,
            "夜里" to HourModifier.NIGHT,
            "夜裡" to HourModifier.NIGHT,
            "半夜" to HourModifier.NIGHT,
            "午夜" to HourModifier.NIGHT,
            "今晚" to HourModifier.NIGHT,
            "今夜" to HourModifier.NIGHT,
        )
        val koreanPeriods = mapOf(
            "새벽" to HourModifier.AM,
            "오전" to HourModifier.AM,
            "아침" to HourModifier.AM,
            "정오" to HourModifier.PM,
            "낮" to HourModifier.PM,
            "오후" to HourModifier.PM,
            "저녁" to HourModifier.PM,
            "밤" to HourModifier.NIGHT,
            "자정" to HourModifier.NIGHT,
            "한밤중" to HourModifier.NIGHT,
        )
    }
}
