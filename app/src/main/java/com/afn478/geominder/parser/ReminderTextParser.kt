package com.afn478.geominder.parser

import com.afn478.geominder.domain.model.PresetLocation
import com.afn478.geominder.localization.SupportedLanguage
import java.text.DateFormatSymbols
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class ParserOptions(
    val defaultDateOnlyTime: LocalTime = LocalTime.of(9, 0),
)

/** A custom, offline parser. It intentionally has no Android or network dependencies. */
class ReminderTextParser private constructor(
    private val languagePack: TimeLanguagePack,
    private val keywordDictionary: KeywordTimeDictionary,
    private val keywordLocationDictionary: KeywordLocationDictionary,
    private val options: ParserOptions = ParserOptions(),
) {
    constructor(
        keywordOverrides: Map<String, LocalTime> = emptyMap(),
        options: ParserOptions = ParserOptions(),
        language: SupportedLanguage = SupportedLanguage.fromLocale(Locale.getDefault()),
        keywordLocationOverrides: Map<String, PresetLocation> = emptyMap(),
    ) : this(
        languagePack = TimeLanguagePacks.forLanguage(language),
        keywordDictionary = KeywordTimeDictionary(keywordOverrides, language),
        keywordLocationDictionary = KeywordLocationDictionary(keywordLocationOverrides),
        options = options,
    )

    val language: SupportedLanguage = languagePack.language
    val keywordTimes: Map<String, LocalTime> = keywordDictionary.entries
    val defaultKeywordTimes: Map<String, LocalTime> = keywordDictionary.defaultEntries
    val keywordLocations: Map<String, PresetLocation> = keywordLocationDictionary.entries

    fun parse(
        sourceText: String,
        context: ParseContext,
        detectTemporalExpressions: Boolean = true,
        detectGpsExpressions: Boolean = true,
    ): ParseResult {
        if (sourceText.isBlank()) return ParseResult(sourceText, context, emptyList())

        val issues = mutableListOf<ParseIssue>()
        val gps = if (detectGpsExpressions) parseGps(sourceText, issues) else null
        val occupiedGpsSpan = gps?.span
        val temporal = if (detectTemporalExpressions) {
            val date = dateCandidates(sourceText, context, issues).best()
            val time = timeCandidates(sourceText, occupiedGpsSpan, issues).best()
            val duration = durationCandidates(sourceText, context.now).best()
            buildTemporal(sourceText, context, date, time, duration, occupiedGpsSpan)
        } else {
            null
        }

        return ParseResult(
            sourceText = sourceText,
            context = context,
            detections = listOfNotNull(temporal, gps).sortedBy { it.span.start },
            issues = issues.distinctBy { it.message to it.span },
        )
    }

    private fun buildTemporal(
        source: String,
        context: ParseContext,
        date: DateCandidate?,
        time: TimeCandidate?,
        duration: InstantCandidate?,
        gpsSpan: SourceSpan?,
    ): DateTimeDetection? {
        if (date == null && duration != null && (time == null || duration.priority > time.priority)) {
            val zoned = duration.instant.atZone(context.zoneId)
            return DateTimeDetection(
                id = detectionId(DetectionType.DATE_TIME, duration.span),
                span = duration.span,
                sourceLabel = duration.span.textFrom(source),
                displayLabel = formatDateTime(zoned, context.locale),
                confidence = duration.confidence,
                date = zoned.toLocalDate(),
                time = zoned.toLocalTime(),
                instant = duration.instant,
                zoneId = context.zoneId,
                precision = TemporalPrecision.RELATIVE_DURATION,
                role = if (gpsSpan != null && isIntroducedByFrom(source, duration.span)) {
                    TemporalRole.GEO_ACTIVE_FROM
                } else {
                    TemporalRole.REMINDER_TRIGGER
                },
                expressionSpans = listOf(duration.span),
            )
        }
        if (date == null && time == null) return null

        val now = context.now.atZone(context.zoneId)
        val resolvedDate = date?.date ?: run {
            val today = now.toLocalDate()
            val todayAtTime = resolveLocalDateTime(today, requireNotNull(time).time, context.zoneId)
            if (todayAtTime.toInstant().isAfter(context.now)) today else today.plusDays(1)
        }
        val resolvedTime = time?.time ?: options.defaultDateOnlyTime
        val zoned = resolveLocalDateTime(resolvedDate, resolvedTime, context.zoneId)
        val span = when {
            date != null && time != null -> date.span.union(time.span)
            date != null -> date.span
            else -> requireNotNull(time).span
        }
        val precision = when {
            date != null && time != null -> TemporalPrecision.DATE_TIME
            date != null -> TemporalPrecision.DATE
            else -> TemporalPrecision.TIME
        }
        val expressionSpans = buildList {
            date?.span?.let { dateSpan ->
                add(dateSpan)
                addAll(temporalJoinerSpans(source, dateSpan))
            }
            time?.span?.let { timeSpan ->
                add(timeSpan)
                addAll(temporalJoinerSpans(source, timeSpan))
            }
        }

        return DateTimeDetection(
            id = detectionId(DetectionType.DATE_TIME, span),
            span = span,
            sourceLabel = span.textFrom(source),
            displayLabel = formatDateTime(zoned, context.locale),
            confidence = listOfNotNull(date?.confidence, time?.confidence).average(),
            date = zoned.toLocalDate(),
            time = zoned.toLocalTime(),
            instant = zoned.toInstant(),
            zoneId = context.zoneId,
            precision = precision,
            role = if (gpsSpan != null &&
                (isIntroducedByFrom(source, span) ||
                    (time?.isPreset == true && time.span.start >= gpsSpan.endExclusive))
            ) {
                TemporalRole.GEO_ACTIVE_FROM
            } else {
                TemporalRole.REMINDER_TRIGGER
            },
            expressionSpans = expressionSpans,
        )
    }

    private fun temporalJoinerSpans(source: String, temporalSpan: SourceSpan): List<SourceSpan> {
        val precedingText = source.substring(0, temporalSpan.start)
        return languagePack.temporalJoiners.flatMap { joiner ->
            joiner.findAll(precedingText)
                .filter { match -> precedingText.substring(match.range.last + 1).isBlank() }
                .map { match -> match.range.toSpan() }
                .toList()
        }
    }

    private fun isIntroducedByFrom(source: String, temporalSpan: SourceSpan): Boolean =
        languagePack.fromPrefixes.any { prefix ->
            prefix.containsMatchIn(source.substring(0, temporalSpan.start))
        } || languagePack.fromSuffixes.any { suffix ->
            suffix.containsMatchIn(source.substring(temporalSpan.endExclusive))
        }

    private fun parseGps(source: String, issues: MutableList<ParseIssue>): GpsDetection? {
        val candidates = mutableListOf<GpsCandidate>()

        LABELED_COORDINATES.findAll(source).forEach { match ->
            coordinateCandidate(
                match = match,
                latitudeText = match.groups[1]?.value,
                latitudeHemisphere = match.groups[2]?.value,
                longitudeText = match.groups[3]?.value,
                longitudeHemisphere = match.groups[4]?.value,
                priority = 100,
                confidence = 1.0,
                issues = issues,
            )?.let(candidates::add)
        }
        REVERSED_LABELED_COORDINATES.findAll(source).forEach { match ->
            coordinateCandidate(
                match = match,
                latitudeText = match.groups[3]?.value,
                latitudeHemisphere = match.groups[4]?.value,
                longitudeText = match.groups[1]?.value,
                longitudeHemisphere = match.groups[2]?.value,
                priority = 100,
                confidence = 1.0,
                issues = issues,
            )?.let(candidates::add)
        }
        PLAIN_COORDINATES.findAll(source).forEach { match ->
            coordinateCandidate(
                match = match,
                latitudeText = match.groups[1]?.value,
                latitudeHemisphere = match.groups[2]?.value,
                longitudeText = match.groups[3]?.value,
                longitudeHemisphere = match.groups[4]?.value,
                priority = 80,
                confidence = 0.95,
                issues = issues,
            )?.let(candidates::add)
        }
        keywordLocationDictionary.findMatches(source).forEach { match ->
            candidates += GpsCandidate(
                span = match.span,
                // An explicit coordinate pair is a more precise override when both are present.
                priority = 70,
                confidence = 0.95,
                latitude = match.location.latitude,
                longitude = match.location.longitude,
                radiusMeters = match.location.radiusMeters,
            )
        }

        val best = candidates.best() ?: return null
        return GpsDetection(
            id = detectionId(DetectionType.GPS, best.span),
            span = best.span,
            sourceLabel = best.span.textFrom(source),
            displayLabel = formatCoordinates(best.latitude, best.longitude),
            confidence = best.confidence,
            latitude = best.latitude,
            longitude = best.longitude,
            radiusMeters = best.radiusMeters,
        )
    }

    private fun coordinateCandidate(
        match: MatchResult,
        latitudeText: String?,
        latitudeHemisphere: String?,
        longitudeText: String?,
        longitudeHemisphere: String?,
        priority: Int,
        confidence: Double,
        issues: MutableList<ParseIssue>,
    ): GpsCandidate? {
        val span = match.range.toSpan()
        val latitude = signedCoordinate(latitudeText, latitudeHemisphere, "S")
        val longitude = signedCoordinate(longitudeText, longitudeHemisphere, "W")
        if (latitude == null || longitude == null || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            issues += ParseIssue(
                message = "Coordinates are outside the valid latitude/longitude range",
                span = span,
                code = ParseIssueCode.INVALID_COORDINATES,
            )
            return null
        }
        return GpsCandidate(span, priority, confidence, latitude, longitude)
    }

    private fun dateCandidates(
        source: String,
        context: ParseContext,
        issues: MutableList<ParseIssue>,
    ): List<DateCandidate> {
        val localToday = context.now.atZone(context.zoneId).toLocalDate()
        val candidates = mutableListOf<DateCandidate>()

        ISO_DATE.findAll(source).forEach { match ->
            addDateOrIssue(
                candidates,
                issues,
                match,
                priority = 110,
                confidence = 1.0,
            ) {
                LocalDate.of(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                )
            }
        }

        NUMERIC_DATE.findAll(source).forEach { match ->
            val first = match.groupValues[1].toInt()
            val second = match.groupValues[2].toInt()
            val year = match.groupValues[3].toInt().normalizeYear()
            val monthFirst = context.locale.country.equals("US", ignoreCase = true)
            val (day, month) = when {
                first > 12 -> first to second
                second > 12 -> second to first
                monthFirst -> second to first
                else -> first to second
            }
            addDateOrIssue(candidates, issues, match, 105, 0.98) {
                LocalDate.of(year, month, day)
            }
        }

        YEARLESS_DAY_MONTH.findAll(source).forEach { match ->
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            addDateOrIssue(candidates, issues, match, 107, 0.99) {
                var date = LocalDate.of(localToday.year, month, day)
                if (date.isBefore(localToday)) date = date.plusYears(1)
                date
            }
        }

        localizedMonthCandidates(source, localToday, sourceLocale(context)).forEach(candidates::add)

        languagePack.relativeDateRules.forEach { rule ->
            rule.regex.findAll(source).forEach { match ->
            candidates += DateCandidate(
                span = match.range.toSpan(),
                priority = rule.priority,
                confidence = 0.98,
                date = localToday.plusDays(rule.daysFromToday),
            )
            }
        }

        languagePack.relativeDayRules.forEach { rule ->
            rule.regex.findAll(source).forEach { match ->
                val amount = match.groupValue(rule.amountGroup)?.toLongOrNull() ?: return@forEach
                val unit = match.groupValue(rule.unitGroup)
                    ?.lowercase(languagePack.locale)
                    ?: return@forEach
                val multiplier = rule.unitMultipliers[unit] ?: return@forEach
                candidates += DateCandidate(
                    match.range.toSpan(),
                    priority = 95,
                    confidence = 0.98,
                    date = localToday.plusDays(amount * multiplier),
                )
            }
        }

        candidates += weekdayCandidates(source, localToday, languagePack, sourceLocale(context))
        return candidates
    }

    private fun sourceLocale(context: ParseContext): Locale =
        if (languagePack.language == SupportedLanguage.ENGLISH ||
            context.locale.language.equals(languagePack.language.languageTag, ignoreCase = true)
        ) {
            // Keep the parser's original context-driven behavior for the default English parser.
            // An explicitly selected non-English pack still falls back to its own locale when the
            // parse context comes from a different language.
            context.locale
        } else {
            languagePack.locale
        }

    private fun localizedMonthCandidates(
        source: String,
        today: LocalDate,
        locale: Locale,
    ): List<DateCandidate> {
        val monthNames = monthNames(locale)
        if (monthNames.isEmpty()) return emptyList()
        val alternatives = monthNames.keys.sortedByDescending(String::length).joinToString("|") { Regex.escape(it) }
        val dayFirst = Regex(
            "(?iu)(?<![\\p{L}\\p{N}])([0-3]?\\d)(?:st|nd|rd|th|\\.)?\\s+" +
                "($alternatives)\\.?(?:\\s*,?\\s*(\\d{4}))?(?![\\p{L}\\p{N}])",
        )
        val monthFirst = Regex(
            "(?iu)(?<![\\p{L}\\p{N}])($alternatives)\\.?\\s+" +
                "([0-3]?\\d)(?:st|nd|rd|th|\\.)?(?:\\s*,?\\s*(\\d{4}))?(?![\\p{L}\\p{N}])",
        )
        val output = mutableListOf<DateCandidate>()

        fun add(match: MatchResult, dayGroup: Int, monthGroup: Int, yearGroup: Int) {
            val day = match.groupValues[dayGroup].toIntOrNull() ?: return
            val monthKey = match.groupValues[monthGroup].trim().trimEnd('.').lowercase(locale)
            val month = monthNames[monthKey] ?: return
            val explicitYear = match.groups[yearGroup]?.value?.toIntOrNull()
            try {
                var date = LocalDate.of(explicitYear ?: today.year, month.value, day)
                if (explicitYear == null && date.isBefore(today)) date = date.plusYears(1)
                output += DateCandidate(match.range.toSpan(), 108, 0.99, date)
            } catch (_: DateTimeException) {
                // Invalid localized dates are ignored; numeric invalids are reported separately.
            }
        }

        dayFirst.findAll(source).forEach { add(it, 1, 2, 3) }
        monthFirst.findAll(source).forEach { add(it, 2, 1, 3) }
        return output
    }

    private fun weekdayCandidates(
        source: String,
        today: LocalDate,
        languagePack: TimeLanguagePack,
        locale: Locale,
    ): List<DateCandidate> {
        val weekdays = weekdayNames(locale)
        if (weekdays.isEmpty()) return emptyList()
        val alternatives = weekdays.keys.sortedByDescending(String::length).joinToString("|") { Regex.escape(it) }
        val nextWords = languagePack.nextWords
            .sortedByDescending(String::length)
            .joinToString("|") { Regex.escape(it) }
        val body = if (nextWords.isEmpty()) {
            "(?<day>$alternatives)"
        } else {
            "(?:(?<next>$nextWords)\\s*)?(?<day>$alternatives)"
        }
        val regex = if (languagePack.language.isScriptBased) {
            Regex("(?iu)$body\\.?")
        } else {
            Regex("(?iu)(?<![\\p{L}\\p{N}])$body\\.?(?![\\p{L}\\p{N}])")
        }
        return regex.findAll(source).mapNotNull { match ->
            val name = match.groupValue("day")?.trim()?.trimEnd('.')?.lowercase(locale)
                ?: return@mapNotNull null
            val dayOfWeek = weekdays[name] ?: return@mapNotNull null
            DateCandidate(
                span = match.range.toSpan(),
                priority = if (match.groupValue("next") != null) 82 else 80,
                confidence = 0.94,
                date = today.with(TemporalAdjusters.next(dayOfWeek)),
            )
        }.toList()
    }

    private fun timeCandidates(
        source: String,
        occupiedGpsSpan: SourceSpan?,
        issues: MutableList<ParseIssue>,
    ): List<TimeCandidate> {
        val candidates = mutableListOf<TimeCandidate>()

        languagePack.clockPatterns.forEach { pattern ->
            pattern.regex.findAll(source).forEach { match ->
                val span = match.range.toSpan()
                if (occupiedGpsSpan != null && span.overlaps(occupiedGpsSpan)) return@forEach
                parseClockMatch(pattern, match, span, issues)?.let { time ->
                    candidates += TimeCandidate(
                        span = span,
                        priority = pattern.priority,
                        confidence = if (pattern.priority >= 100) 1.0 else 0.85,
                        time = time,
                    )
                }
            }
        }

        keywordDictionary.findMatches(source).forEach { match ->
            candidates += TimeCandidate(match.span, 70, 0.9, match.time, isPreset = true)
        }
        return candidates
    }

    private fun parseClockMatch(
        pattern: ClockPattern,
        match: MatchResult,
        span: SourceSpan,
        issues: MutableList<ParseIssue>,
    ): LocalTime? {
        val hourText = match.groupValue(pattern.hourGroup)?.toIntOrNull() ?: return null
        val minuteText = match.groupValue(pattern.minuteGroup)?.toIntOrNull()
        val modifierText = match.groupValue(pattern.modifierGroup)
            ?.lowercase(languagePack.locale)
        val modifier = modifierText
            ?.let { pattern.modifierAliases[it] }
            ?: pattern.defaultModifier
        val minute = when {
            pattern.halfGroup?.let(match::groupValue) != null -> 30
            minuteText != null -> minuteText
            else -> 0
        }
        val hour = when (modifier) {
            HourModifier.TWENTY_FOUR_HOUR -> hourText
            HourModifier.AM -> hourText % 12
            HourModifier.PM -> hourText % 12 + 12
            HourModifier.NIGHT -> when {
                hourText == 12 -> 0
                hourText in 1..5 -> hourText
                else -> hourText % 12 + 12
            }
        }
        val valid = when (modifier) {
            HourModifier.TWENTY_FOUR_HOUR -> hourText in 0..23
            else -> hourText in 1..12
        } && minute in 0..59
        if (!valid) {
            issues += ParseIssue(
                message = "Time is outside the valid range",
                span = span,
                code = ParseIssueCode.INVALID_TIME,
            )
            return null
        }
        return LocalTime.of(hour, minute)
    }

    private fun durationCandidates(source: String, now: Instant): List<InstantCandidate> =
        languagePack.relativeDurationRules.flatMap { rule ->
            rule.regex.findAll(source).mapNotNull { match ->
                val amount = match.groupValue(rule.amountGroup)?.toLongOrNull()
                    ?: return@mapNotNull null
                val unit = match.groupValue(rule.unitGroup)
                    ?.lowercase(languagePack.locale)
                    ?: return@mapNotNull null
                val duration = when (rule.unitTypes[unit]) {
                    DurationUnit.HOURS -> Duration.ofHours(amount)
                    DurationUnit.MINUTES -> Duration.ofMinutes(amount)
                    null -> return@mapNotNull null
                }
                InstantCandidate(match.range.toSpan(), 96, 0.99, now.plus(duration))
            }.toList()
        }

    private fun addDateOrIssue(
        output: MutableList<DateCandidate>,
        issues: MutableList<ParseIssue>,
        match: MatchResult,
        priority: Int,
        confidence: Double,
        date: () -> LocalDate,
    ) {
        try {
            output += DateCandidate(match.range.toSpan(), priority, confidence, date())
        } catch (_: DateTimeException) {
            issues += ParseIssue(
                message = "Date is not valid",
                span = match.range.toSpan(),
                code = ParseIssueCode.INVALID_DATE,
            )
        }
    }

    private data class DateCandidate(
        override val span: SourceSpan,
        override val priority: Int,
        override val confidence: Double,
        val date: LocalDate,
    ) : Candidate

    private data class TimeCandidate(
        override val span: SourceSpan,
        override val priority: Int,
        override val confidence: Double,
        val time: LocalTime,
        val isPreset: Boolean = false,
    ) : Candidate

    private data class InstantCandidate(
        override val span: SourceSpan,
        override val priority: Int,
        override val confidence: Double,
        val instant: Instant,
    ) : Candidate

    private data class GpsCandidate(
        override val span: SourceSpan,
        override val priority: Int,
        override val confidence: Double,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Double? = null,
    ) : Candidate

    private interface Candidate {
        val span: SourceSpan
        val priority: Int
        val confidence: Double
    }

    private fun <T : Candidate> List<T>.best(): T? = sortedWith(
        compareByDescending<T> { it.priority }
            .thenBy { it.span.start }
            .thenByDescending { it.span.endExclusive - it.span.start },
    ).firstOrNull()

    companion object {
        /**
         * Creates a parser whose keyword dictionary contains exactly [keywordTimes].
         *
         * This is the appropriate construction path for a persisted Settings snapshot because an
         * absent built-in keyword remains absent rather than being restored as a default.
         */
        fun fromCompleteKeywordTable(
            keywordTimes: Map<String, LocalTime>,
            options: ParserOptions = ParserOptions(),
            language: SupportedLanguage = SupportedLanguage.fromLocale(Locale.getDefault()),
            keywordLocations: Map<String, PresetLocation> = emptyMap(),
        ): ReminderTextParser = ReminderTextParser(
            languagePack = TimeLanguagePacks.forLanguage(language),
            keywordDictionary = KeywordTimeDictionary.fromCompleteTable(keywordTimes, language),
            keywordLocationDictionary = KeywordLocationDictionary.fromCompleteTable(keywordLocations),
            options = options,
        )

        private val ISO_DATE = Regex("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)")
        private val NUMERIC_DATE = Regex("(?<!\\d)(\\d{1,2})[./](\\d{1,2})[./](\\d{2}|\\d{4})(?!\\d)")
        private val YEARLESS_DAY_MONTH = Regex("(?<![\\d.])([0-3]?\\d)\\.(0?[1-9]|1[0-2])(?![\\d.])")
        private val LABELED_COORDINATES = Regex(
            "(?i)\\b(?:lat|latitude)\\s*[:=]?\\s*" +
                "([+-]?\\d{1,3}(?:\\.\\d+)?)\\s*([NS])?\\s*[,; ]+\\s*" +
                "(?:lon|lng|longitude)\\s*[:=]?\\s*" +
                "([+-]?\\d{1,3}(?:\\.\\d+)?)\\s*([EW])?\\b",
        )
        private val REVERSED_LABELED_COORDINATES = Regex(
            "(?i)\\b(?:lon|lng|longitude)\\s*[:=]?\\s*" +
                "([+-]?\\d{1,3}(?:\\.\\d+)?)\\s*([EW])?\\s*[,; ]+\\s*" +
                "(?:lat|latitude)\\s*[:=]?\\s*" +
                "([+-]?\\d{1,3}(?:\\.\\d+)?)\\s*([NS])?\\b",
        )
        private val PLAIN_COORDINATES = Regex(
            "(?i)(?<![\\d.])([+-]?\\d{1,2}\\.\\d+)\\s*([NS])?\\s*[,;]\\s*([+-]?\\d{1,3}\\.\\d+)\\s*([EW])?(?![\\d.])",
        )
    }
}

private fun Int.normalizeYear(): Int = if (this in 0..99) 2000 + this else this

private fun IntRange.toSpan(): SourceSpan = SourceSpan(first, last + 1)

private fun MatchResult.groupValue(name: String): String? = runCatching {
    groups[name]?.value
}.getOrNull()

private fun SourceSpan.overlaps(other: SourceSpan): Boolean =
    start < other.endExclusive && other.start < endExclusive

private fun signedCoordinate(value: String?, hemisphere: String?, negativeHemisphere: String): Double? {
    val parsed = value?.toDoubleOrNull() ?: return null
    if (!parsed.isFinite()) return null
    if (hemisphere == null) return parsed
    val magnitude = kotlin.math.abs(parsed)
    return if (hemisphere.equals(negativeHemisphere, ignoreCase = true)) -magnitude else magnitude
}

private fun detectionId(type: DetectionType, span: SourceSpan): String =
    "${type.name.lowercase(Locale.ROOT)}:${span.start}:${span.endExclusive}"

private fun monthNames(locale: Locale): Map<String, Month> {
    val symbols = DateFormatSymbols.getInstance(locale)
    return buildMap {
        fun add(values: Array<String>) {
            values.forEachIndexed { index, raw ->
                val normalized = raw.trim().trimEnd('.').lowercase(locale)
                if (normalized.isNotEmpty() && index < 12) put(normalized, Month.of(index + 1))
            }
        }
        add(symbols.months)
        add(symbols.shortMonths)
    }
}

private fun weekdayNames(locale: Locale): Map<String, DayOfWeek> {
    val symbols = DateFormatSymbols.getInstance(locale)
    return buildMap {
        fun add(values: Array<String>) {
            values.forEachIndexed { calendarIndex, raw ->
                if (calendarIndex == 0 || raw.isBlank()) return@forEachIndexed
                val javaDay = if (calendarIndex == 1) 7 else calendarIndex - 1
                put(raw.trim().trimEnd('.').lowercase(locale), DayOfWeek.of(javaDay))
            }
        }
        add(symbols.weekdays)
        add(symbols.shortWeekdays)
    }
}
