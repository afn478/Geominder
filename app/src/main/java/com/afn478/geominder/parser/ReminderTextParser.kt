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
        var selectedDateSpan: SourceSpan? = null
        var selectedDurationSpan: SourceSpan? = null
        val temporal = if (detectTemporalExpressions) {
            val dateOptions = dateCandidates(sourceText, context, issues)
            val provisionalDate = dateOptions.best()
            val duration = durationCandidates(sourceText, context.now).best()
            val timeCandidates = timeCandidates(
                source = sourceText,
                occupiedGpsSpan = occupiedGpsSpan,
                issues = issues,
                date = provisionalDate,
                duration = duration,
            )
            val date = selectDateCandidate(dateOptions, timeCandidates)
            val time = selectTimeCandidate(timeCandidates, duration, date)
            selectedDateSpan = date?.span
            selectedDurationSpan = duration?.span
            buildTemporal(sourceText, context, date, time, duration, occupiedGpsSpan)
        } else {
            null
        }

        return ParseResult(
            sourceText = sourceText,
            context = context,
            detections = listOfNotNull(temporal, gps).sortedBy { it.span.start },
            issues = issues.deduplicateOverlappingClockIssues(
                dateSpan = selectedDateSpan,
                durationSpan = selectedDurationSpan,
            ),
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
        // A clock-with-unit rule may match only the numeric fragment inside a relative duration
        // (for example, "3 heures" inside "dans 3 heures"). A containing duration is the more
        // complete expression, even when the fragment's clock priority is higher.
        if (date == null && duration != null && (
                time == null ||
                    duration.priority > time.priority ||
                    duration.span.contains(time.span)
            )
        ) {
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

        // A dotted clock can overlap a full numeric date such as "08.05.2026". These are
        // competing interpretations, not a date-plus-time combination; the containing date wins.
        val (resolvedDateCandidate, resolvedTimeCandidate) = when {
            date == null || time == null || !date.span.overlaps(time.span) -> date to time
            date.span.contains(time.span) && date.span != time.span -> date to null
            time.span.contains(date.span) && time.span != date.span -> null to time
            date.span == time.span && date.span.textFrom(source).count { it == '.' || it == '/' } < 2 ->
                null to time
            time.priority > date.priority -> null to time
            else -> date to null
        }

        val now = context.now.atZone(context.zoneId)
        val resolvedDate = resolvedDateCandidate?.date ?: run {
            val today = now.toLocalDate()
            val todayAtTime = resolveLocalDateTime(
                today,
                requireNotNull(resolvedTimeCandidate).time,
                context.zoneId,
            )
            if (todayAtTime.toInstant().isAfter(context.now)) today else today.plusDays(1)
        }
        val resolvedTime = resolvedTimeCandidate?.time ?: options.defaultDateOnlyTime
        val zoned = resolveLocalDateTime(resolvedDate, resolvedTime, context.zoneId)
        val span = when {
            resolvedDateCandidate != null && resolvedTimeCandidate != null ->
                resolvedDateCandidate.span.union(resolvedTimeCandidate.span)
            resolvedDateCandidate != null -> resolvedDateCandidate.span
            else -> requireNotNull(resolvedTimeCandidate).span
        }
        val precision = when {
            resolvedDateCandidate != null && resolvedTimeCandidate != null -> TemporalPrecision.DATE_TIME
            resolvedDateCandidate != null -> TemporalPrecision.DATE
            else -> TemporalPrecision.TIME
        }
        val expressionSpans = buildList {
            resolvedDateCandidate?.span?.let { dateSpan ->
                add(dateSpan)
                addAll(temporalJoinerSpans(source, dateSpan))
            }
            resolvedTimeCandidate?.span?.let { timeSpan ->
                add(timeSpan)
                addAll(temporalJoinerSpans(source, timeSpan))
            }
            if (resolvedDateCandidate != null && resolvedTimeCandidate != null) {
                addAll(isoDateTimeSeparatorSpans(source, resolvedDateCandidate.span, resolvedTimeCandidate.span))
            }
        }.distinct().sortedBy(SourceSpan::start)

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
                    (resolvedTimeCandidate?.isPreset == true &&
                        resolvedTimeCandidate.span.start >= gpsSpan.endExclusive))
            ) {
                TemporalRole.GEO_ACTIVE_FROM
            } else {
                TemporalRole.REMINDER_TRIGGER
            },
            expressionSpans = expressionSpans,
        )
    }

    private fun selectTimeCandidate(
        candidates: List<TimeCandidate>,
        duration: InstantCandidate?,
        date: DateCandidate?,
    ): TimeCandidate? {
        val candidatesOutsideDate = if (date == null) {
            candidates
        } else {
            candidates.filterNot { candidate ->
                date.span.contains(candidate.span)
            }.ifEmpty { candidates }
        }
        val best = candidatesOutsideDate.best() ?: return null
        if (duration == null || !duration.span.contains(best.span)) return best

        // A localized clock rule can recognize the unit-bearing fragment inside a duration (for
        // example, "3 heures" in "dans 3 heures, 8:05"). If another clock is outside that
        // duration, let it compete normally instead of allowing the nested fragment to hide it.
        return candidatesOutsideDate
            .filterNot { candidate -> duration.span.contains(candidate.span) }
            .best()
            ?: best
    }

    private fun selectDateCandidate(
        candidates: List<DateCandidate>,
        timeCandidates: List<TimeCandidate>,
    ): DateCandidate? {
        val best = candidates.best() ?: return null
        if (timeCandidates.any { time -> time.span.contains(best.span) && time.span != best.span }) {
            return candidates
                .filterNot { candidate ->
                    timeCandidates.any { time -> time.span.contains(candidate.span) && time.span != candidate.span }
                }
                .best()
                ?: best
        }
        if (timeCandidates.none { it.span == best.span }) return best

        // A two-part dotted value can be either a clock or a yearless date. If another date
        // candidate does not share the winning clock's exact span, prefer that date and use the
        // dotted clock as its time (for example, "8.05 25.12"). A lone "8.05" falls back to the
        // clock because there is no competing date candidate.
        return candidates
            .filterNot { candidate -> timeCandidates.any { time -> time.span == candidate.span } }
            .best()
            ?: best
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

    private fun isoDateTimeSeparatorSpans(
        source: String,
        dateSpan: SourceSpan,
        timeSpan: SourceSpan,
    ): List<SourceSpan> {
        if (dateSpan.endExclusive > timeSpan.start) return emptyList()
        val between = source.substring(dateSpan.endExclusive, timeSpan.start)
        val separatorOffset = between.indexOfFirst { it == 'T' || it == 't' }
        return if (separatorOffset >= 0 && between.replace("T", "", ignoreCase = true).isBlank()) {
            listOf(
                SourceSpan(
                    start = dateSpan.endExclusive + separatorOffset,
                    endExclusive = dateSpan.endExclusive + separatorOffset + 1,
                ),
            )
        } else {
            emptyList()
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
            // A two-part dotted value is also a localized clock (for example, "8.05" in
            // German). Keep an explicit year-bearing date ahead of this fallback so a clock
            // following a date cannot replace the date candidate.
            addDateOrIssue(candidates, issues, match, 104, 0.99) {
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
                val amount = match.groupValue(rule.amountGroup)
                    ?.let { parseAmount(it, languagePack.language) }
                    ?: return@forEach
                val unit = match.groupValue(rule.unitGroup)
                    ?.lowercase(languagePack.locale)
                    ?: return@forEach
                val multiplier = rule.unitMultipliers[unit] ?: return@forEach
                val days = runCatching { Math.multiplyExact(amount, multiplier) }.getOrNull()
                    ?: return@forEach
                val date = runCatching { localToday.plusDays(days) }.getOrNull()
                    ?: return@forEach
                candidates += DateCandidate(
                    match.range.toSpan(),
                    priority = 95,
                    confidence = 0.98,
                    date = date,
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
        date: DateCandidate?,
        duration: InstantCandidate?,
    ): List<TimeCandidate> {
        val candidates = mutableListOf<TimeCandidate>()
        val unsupportedClockSpans = languagePack.unsupportedClockPatterns
            .flatMap { pattern -> pattern.findAll(source).map { it.range.toSpan() }.toList() }

        languagePack.clockPatterns.forEach { pattern ->
            pattern.regex.findAll(source).forEach { match ->
                val span = match.range.toSpan()
                if (occupiedGpsSpan != null && span.overlaps(occupiedGpsSpan)) return@forEach
                if (unsupportedClockSpans.any { it.overlaps(span) }) return@forEach
                parseClockMatch(
                    pattern = pattern,
                    match = match,
                    span = span,
                    issues = issues,
                    reportInvalidTime = (date == null || !date.span.contains(span)) &&
                        (duration == null || !duration.span.contains(span)),
                )?.let { time ->
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
        reportInvalidTime: Boolean = true,
    ): LocalTime? {
        val hourText = match.groupValue(pattern.hourGroup)
            ?.let { parseAmount(it, languagePack.language) }
            ?.takeIf { it <= Int.MAX_VALUE }
            ?.toInt()
            ?: return null
        val minuteText = match.groupValue(pattern.minuteGroup)
            ?.let { parseAmount(it, languagePack.language) }
            ?.takeIf { it <= Int.MAX_VALUE }
            ?.toInt()
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
            if (reportInvalidTime) {
                issues += ParseIssue(
                    message = "Time is outside the valid range",
                    span = span,
                    code = ParseIssueCode.INVALID_TIME,
                )
            }
            return null
        }
        return LocalTime.of(hour, minute)
    }

    private fun durationCandidates(source: String, now: Instant): List<InstantCandidate> =
        languagePack.relativeDurationRules.flatMap { rule ->
            rule.regex.findAll(source).mapNotNull { match ->
                val duration = durationForMatch(rule, match) ?: return@mapNotNull null
                val instant = runCatching { now.plus(duration) }.getOrNull() ?: return@mapNotNull null
                InstantCandidate(match.range.toSpan(), 96, 0.99, instant)
            }.toList()
        }

    private fun durationForMatch(rule: RelativeDurationRule, match: MatchResult): Duration? {
        var total = Duration.ZERO
        rule.components.forEach { component ->
            val amountText = match.groupValue(component.amountGroup)
            val unitText = match.groupValue(component.unitGroup)
            if (amountText == null) {
                if (component.required) return null
                return@forEach
            }
            val amount = parseAmount(amountText, languagePack.language) ?: return null
            val unitType = when {
                unitText != null -> rule.unitTypes[unitText.lowercase(languagePack.locale)]
                else -> component.defaultUnit
            } ?: return null
            val componentDuration = runCatching {
                when (unitType) {
                    DurationUnit.HOURS -> Duration.ofHours(amount)
                    DurationUnit.MINUTES -> Duration.ofMinutes(amount)
                }
            }.getOrNull() ?: return null
            total = runCatching { total.plus(componentDuration) }.getOrNull() ?: return null
        }
        return total
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

    private fun <T : Candidate> List<T>.best(): T? {
        val highestPriority = maxOfOrNull { it.priority } ?: return null
        val highestPriorityCandidates = filter { it.priority == highestPriority }
        // Alternative rules can produce nested candidates at the same priority. Keep the
        // enclosing candidate so a more complete expression does not lose to its prefix.
        val enclosingCandidates = highestPriorityCandidates.filter { candidate ->
            highestPriorityCandidates.any { other ->
                other !== candidate && candidate.span.contains(other.span)
            }
        }
        return (enclosingCandidates.ifEmpty { highestPriorityCandidates })
            .sortedWith(
                compareBy<T> { it.span.start }
                    .thenByDescending { it.span.endExclusive - it.span.start },
            )
            .firstOrNull()
    }

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

private fun SourceSpan.contains(other: SourceSpan): Boolean =
    start <= other.start && endExclusive >= other.endExclusive

private fun List<ParseIssue>.deduplicateOverlappingClockIssues(
    dateSpan: SourceSpan? = null,
    durationSpan: SourceSpan? = null,
): List<ParseIssue> {
    val invalidTimeSpans = asSequence()
        .filter { it.code == ParseIssueCode.INVALID_TIME }
        .mapNotNull { it.span }
        .toList()
    return filterNot { issue ->
        if (issue.code != ParseIssueCode.INVALID_TIME) return@filterNot false
        val span = issue.span ?: return@filterNot false
        if (dateSpan?.contains(span) == true || durationSpan?.contains(span) == true) return@filterNot true
        invalidTimeSpans.any { other -> other != span && other.contains(span) }
    }.distinctBy { it.message to it.span }
}

private fun parseAmount(value: String, language: SupportedLanguage): Long? =
    value.toLongOrNull() ?: when (language) {
        SupportedLanguage.JAPANESE,
        SupportedLanguage.CHINESE,
        -> parseCjkInteger(value)
        else -> null
    }

// Deliberately limited to compositional CJK numerals. Lexical shorthand such as 廿 or 卅 is
// irregular and is not part of the regular-expression grammar.
private val CJK_DIGIT_VALUES = mapOf(
    '〇' to 0L,
    '零' to 0L,
    '一' to 1L,
    '壱' to 1L,
    '二' to 2L,
    '弐' to 2L,
    '两' to 2L,
    '兩' to 2L,
    '三' to 3L,
    '参' to 3L,
    '四' to 4L,
    '五' to 5L,
    '六' to 6L,
    '七' to 7L,
    '八' to 8L,
    '九' to 9L,
)
private val CJK_SMALL_UNITS = mapOf(
    '十' to 10L,
    '拾' to 10L,
    '百' to 100L,
    '千' to 1_000L,
)
private val CJK_LARGE_UNITS = mapOf(
    '万' to 10_000L,
    '萬' to 10_000L,
    '亿' to 100_000_000L,
    '億' to 100_000_000L,
    '兆' to 1_000_000_000_000L,
)

private fun parseCjkInteger(value: String): Long? {
    if (value.isEmpty()) return null

    return runCatching {
        var total = 0L
        var section = 0L
        var number = 0L
        value.forEach { character ->
            when {
                CJK_DIGIT_VALUES.containsKey(character) -> {
                    number = Math.addExact(
                        Math.multiplyExact(number, 10L),
                        CJK_DIGIT_VALUES.getValue(character),
                    )
                }

                CJK_SMALL_UNITS.containsKey(character) -> {
                    val unitNumber = if (number == 0L) 1L else number
                    section = Math.addExact(
                        section,
                        Math.multiplyExact(unitNumber, CJK_SMALL_UNITS.getValue(character)),
                    )
                    number = 0L
                }

                CJK_LARGE_UNITS.containsKey(character) -> {
                    section = Math.addExact(section, number)
                    val sectionValue = if (section == 0L) 1L else section
                    total = Math.addExact(
                        total,
                        Math.multiplyExact(sectionValue, CJK_LARGE_UNITS.getValue(character)),
                    )
                    section = 0L
                    number = 0L
                }

                else -> throw IllegalArgumentException("Unsupported CJK numeral")
            }
        }
        Math.addExact(total, Math.addExact(section, number))
    }.getOrNull()
}

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
