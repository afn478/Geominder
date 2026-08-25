package com.afn478.geominder.parser

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
    keywordDictionary: KeywordTimeDictionary,
    private val options: ParserOptions = ParserOptions(),
) {
    constructor(
        keywordOverrides: Map<String, LocalTime> = emptyMap(),
        options: ParserOptions = ParserOptions(),
    ) : this(
        keywordDictionary = KeywordTimeDictionary(keywordOverrides),
        options = options,
    )

    val keywordTimes: Map<String, LocalTime> = keywordDictionary.entries

    fun parse(sourceText: String, context: ParseContext): ParseResult {
        if (sourceText.isBlank()) return ParseResult(sourceText, context, emptyList())

        val issues = mutableListOf<ParseIssue>()
        val gps = parseGps(sourceText, issues)
        val occupiedGpsSpan = gps?.span
        val date = dateCandidates(sourceText, context, issues).best()
        val time = timeCandidates(sourceText, occupiedGpsSpan, issues).best()
        val duration = durationCandidates(sourceText, context.now).best()
        val temporal = buildTemporal(sourceText, context, date, time, duration, occupiedGpsSpan)

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
        )
    }

    private fun isIntroducedByFrom(source: String, temporalSpan: SourceSpan): Boolean =
        FROM_PREFIX.containsMatchIn(
            source.substring(0, temporalSpan.start),
        )

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

        val best = candidates.best() ?: return null
        return GpsDetection(
            id = detectionId(DetectionType.GPS, best.span),
            span = best.span,
            sourceLabel = best.span.textFrom(source),
            displayLabel = formatCoordinates(best.latitude, best.longitude),
            confidence = best.confidence,
            latitude = best.latitude,
            longitude = best.longitude,
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
            issues += ParseIssue("Coordinates are outside the valid latitude/longitude range", span)
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

        localizedMonthCandidates(source, localToday, context.locale).forEach(candidates::add)

        RELATIVE_DATE.findAll(source).forEach { match ->
            val normalized = match.value.lowercase(Locale.ROOT)
            val days = when (normalized) {
                "today" -> 0L
                "tomorrow" -> 1L
                else -> 2L
            }
            candidates += DateCandidate(
                span = match.range.toSpan(),
                priority = if (days == 2L) 92 else 90,
                confidence = 0.98,
                date = localToday.plusDays(days),
            )
        }

        IN_DAYS.findAll(source).forEach { match ->
            val amount = match.groupValues[1].toLongOrNull() ?: return@forEach
            val multiplier = if (match.groupValues[2].startsWith("week", true)) 7L else 1L
            candidates += DateCandidate(
                match.range.toSpan(),
                priority = 95,
                confidence = 0.98,
                date = localToday.plusDays(amount * multiplier),
            )
        }

        candidates += weekdayCandidates(source, localToday, context.locale)
        return candidates
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
        locale: Locale,
    ): List<DateCandidate> {
        val weekdays = weekdayNames(locale)
        if (weekdays.isEmpty()) return emptyList()
        val alternatives = weekdays.keys.sortedByDescending(String::length).joinToString("|") { Regex.escape(it) }
        val regex = Regex("(?iu)(?<![\\p{L}\\p{N}])(?:(next)\\s+)?($alternatives)\\.?(?![\\p{L}\\p{N}])")
        return regex.findAll(source).mapNotNull { match ->
            val name = match.groupValues[2].trim().trimEnd('.').lowercase(locale)
            val dayOfWeek = weekdays[name] ?: return@mapNotNull null
            DateCandidate(
                span = match.range.toSpan(),
                priority = if (match.groups[1] != null) 82 else 80,
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

        TIME_12_HOUR.findAll(source).forEach { match ->
            val hourText = match.groupValues[1].toInt()
            val minute = match.groups[2]?.value?.toIntOrNull() ?: 0
            val period = match.groupValues[3].lowercase(Locale.ROOT)
            val hour = when {
                hourText !in 1..12 || minute !in 0..59 -> null
                period == "am" -> hourText % 12
                else -> hourText % 12 + 12
            }
            if (hour == null) {
                issues += ParseIssue("Time is outside the valid range", match.range.toSpan())
            } else {
                candidates += TimeCandidate(match.range.toSpan(), 105, 1.0, LocalTime.of(hour, minute))
            }
        }

        TIME_24_HOUR.findAll(source).forEach { match ->
            val span = match.range.toSpan()
            if (occupiedGpsSpan != null && span.overlaps(occupiedGpsSpan)) return@forEach
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            if (hour !in 0..23 || minute !in 0..59) {
                issues += ParseIssue("Time is outside the valid range", span)
            } else {
                candidates += TimeCandidate(span, 100, 1.0, LocalTime.of(hour, minute))
            }
        }

        AT_HOUR.findAll(source).forEach { match ->
            val span = match.range.toSpan()
            if (occupiedGpsSpan != null && span.overlaps(occupiedGpsSpan)) return@forEach
            candidates += TimeCandidate(
                span = span,
                priority = 85,
                confidence = 0.85,
                time = LocalTime.of(match.groupValues[1].toInt(), 0),
            )
        }

        keywordTimes.forEach { (keyword, value) ->
            val words = keyword.split(' ').joinToString("\\s+") { Regex.escape(it) }
            Regex("(?iu)(?<![\\p{L}\\p{N}])$words(?![\\p{L}\\p{N}])")
                .findAll(source)
                .forEach { match ->
                candidates += TimeCandidate(match.range.toSpan(), 70, 0.9, value, isPreset = true)
                }
        }
        return candidates
    }

    private fun durationCandidates(source: String, now: Instant): List<InstantCandidate> =
        IN_DURATION.findAll(source).mapNotNull { match ->
            val amount = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val duration = when {
                match.groupValues[2].startsWith("hour", true) -> Duration.ofHours(amount)
                else -> Duration.ofMinutes(amount)
            }
            InstantCandidate(match.range.toSpan(), 96, 0.99, now.plus(duration))
        }.toList()

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
            issues += ParseIssue("Date is not valid", match.range.toSpan())
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
        ): ReminderTextParser = ReminderTextParser(
            keywordDictionary = KeywordTimeDictionary.fromCompleteTable(keywordTimes),
            options = options,
        )

        private val ISO_DATE = Regex("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)")
        private val NUMERIC_DATE = Regex("(?<!\\d)(\\d{1,2})[./](\\d{1,2})[./](\\d{2}|\\d{4})(?!\\d)")
        private val YEARLESS_DAY_MONTH = Regex("(?<![\\d.])([0-3]?\\d)\\.(0?[1-9]|1[0-2])(?![\\d.])")
        private val RELATIVE_DATE = Regex("(?i)\\b(?:day\\s+after\\s+tomorrow|today|tomorrow)\\b")
        private val IN_DAYS = Regex("(?i)\\bin\\s+(\\d+)\\s+(day|days|week|weeks)\\b")
        private val IN_DURATION = Regex("(?i)\\bin\\s+(\\d+)\\s+(hour|hours|minute|minutes|mins?)\\b")
        private val FROM_PREFIX = Regex("(?iu)(?<![\\p{L}\\p{N}])from\\s+$")
        private val TIME_12_HOUR = Regex(
            "(?i)(?<![\\p{L}\\p{N}])(?:at\\s+)?" +
                "(\\d{1,2})(?::(\\d{1,2}))?\\s*(am|pm)(?![\\p{L}\\p{N}])",
        )
        private val TIME_24_HOUR = Regex("(?<!\\d)(\\d{1,2}):(\\d{1,2})(?!\\d)")
        private val AT_HOUR = Regex("(?i)\\bat\\s+(2[0-3]|1\\d|0?\\d)(?![\\d.:])\\b(?!\\s*(?:am|pm)\\b)")
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
