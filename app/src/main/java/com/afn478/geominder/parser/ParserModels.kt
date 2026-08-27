package com.afn478.geominder.parser

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Everything that makes a parse deterministic and straightforward to test. */
data class ParseContext(
    val now: Instant,
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val locale: Locale = Locale.getDefault(),
)

data class SourceSpan(
    val start: Int,
    val endExclusive: Int,
) {
    init {
        require(start >= 0) { "Span start cannot be negative" }
        require(endExclusive >= start) { "Span end cannot precede its start" }
    }

    fun textFrom(source: String): String = source.substring(start, endExclusive)

    internal fun union(other: SourceSpan): SourceSpan = SourceSpan(
        start = minOf(start, other.start),
        endExclusive = maxOf(endExclusive, other.endExclusive),
    )
}

enum class DetectionType {
    DATE_TIME,
    GPS,
}

enum class TemporalPrecision {
    DATE,
    TIME,
    DATE_TIME,
    RELATIVE_DURATION,
}

enum class TemporalRole {
    REMINDER_TRIGGER,
    GEO_ACTIVE_FROM,
}

sealed interface EditableDetection {
    val id: String
    val type: DetectionType
    val span: SourceSpan
    val sourceLabel: String
    val displayLabel: String
    val confidence: Double
}

data class DateTimeDetection(
    override val id: String,
    override val span: SourceSpan,
    override val sourceLabel: String,
    override val displayLabel: String,
    override val confidence: Double,
    val date: LocalDate,
    val time: LocalTime,
    val instant: Instant,
    val zoneId: ZoneId,
    val precision: TemporalPrecision,
    val role: TemporalRole = TemporalRole.REMINDER_TRIGGER,
    val expressionSpans: List<SourceSpan> = listOf(span),
) : EditableDetection {
    override val type: DetectionType = DetectionType.DATE_TIME

    init {
        require(confidence in 0.0..1.0) { "Confidence must be between zero and one" }
    }
}

data class GpsDetection(
    override val id: String,
    override val span: SourceSpan,
    override val sourceLabel: String,
    override val displayLabel: String,
    override val confidence: Double,
    val latitude: Double,
    val longitude: Double,
    /** Non-null when the coordinates came from a named location preset. */
    val radiusMeters: Double? = null,
) : EditableDetection {
    override val type: DetectionType = DetectionType.GPS

    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude must be between -90 and 90"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude must be between -180 and 180"
        }
        require(radiusMeters == null || radiusMeters.isFinite() && radiusMeters > 0.0) {
            "Preset radius must be a positive finite value"
        }
        require(confidence in 0.0..1.0) { "Confidence must be between zero and one" }
    }
}

enum class ParseIssueCode {
    INVALID_COORDINATES,
    INVALID_TIME,
    INVALID_DATE,
    UNKNOWN,
}

data class ParseIssue(
    val message: String,
    val span: SourceSpan? = null,
    val code: ParseIssueCode = ParseIssueCode.UNKNOWN,
)

sealed interface DetectionEdit {
    val detectionId: String

    data class DateTime(
        override val detectionId: String,
        val date: LocalDate,
        val time: LocalTime,
    ) : DetectionEdit

    data class Gps(
        override val detectionId: String,
        val latitude: Double,
        val longitude: Double,
    ) : DetectionEdit
}

/**
 * Immutable result consumed by a ViewModel. Detection IDs remain stable after chip edits.
 */
data class ParseResult(
    val sourceText: String,
    val context: ParseContext,
    val detections: List<EditableDetection>,
    val issues: List<ParseIssue> = emptyList(),
) {
    val dateTime: DateTimeDetection?
        get() = detections.filterIsInstance<DateTimeDetection>().firstOrNull()

    val gps: GpsDetection?
        get() = detections.filterIsInstance<GpsDetection>().firstOrNull()

    /** Returns the source text with the recognized temporal expression removed. */
    fun textWithoutTimeExpression(): String {
        val spans = dateTime?.expressionSpans
            .orEmpty()
            .filter { span ->
                span.start < span.endExclusive &&
                    span.start >= 0 &&
                    span.endExclusive <= sourceText.length
            }
            .sortedBy(SourceSpan::start)
        if (spans.isEmpty()) {
            return sourceText
        }

        val filteredText = buildString {
            var cursor = 0
            spans.forEach { span ->
                if (span.start >= cursor) {
                    append(sourceText, cursor, span.start)
                }
                cursor = maxOf(cursor, span.endExclusive)
            }
            append(sourceText, cursor, sourceText.length)
        }

        return filteredText
            .replace(INLINE_WHITESPACE, " ")
            .trim()
    }

    /** Applies the value committed by an editable chip and returns a new parse result. */
    fun applyEdit(edit: DetectionEdit): ParseResult {
        var found = false
        val edited = detections.map { detection ->
            if (detection.id != edit.detectionId) return@map detection
            found = true
            when {
                detection is DateTimeDetection && edit is DetectionEdit.DateTime -> {
                    val resolved = resolveLocalDateTime(edit.date, edit.time, detection.zoneId)
                    detection.copy(
                        displayLabel = formatDateTime(resolved, context.locale),
                        date = resolved.toLocalDate(),
                        time = resolved.toLocalTime(),
                        instant = resolved.toInstant(),
                        precision = TemporalPrecision.DATE_TIME,
                    )
                }

                detection is GpsDetection && edit is DetectionEdit.Gps -> detection.copy(
                    displayLabel = formatCoordinates(edit.latitude, edit.longitude),
                    latitude = edit.latitude,
                    longitude = edit.longitude,
                )

                else -> throw IllegalArgumentException(
                    "Edit type does not match detection ${edit.detectionId}",
                )
            }
        }
        require(found) { "Unknown detection ID: ${edit.detectionId}" }
        return copy(detections = edited)
    }
}

private val INLINE_WHITESPACE = Regex("[\\t ]+")

internal fun resolveLocalDateTime(
    date: LocalDate,
    time: LocalTime,
    zoneId: ZoneId,
): ZonedDateTime {
    val local = LocalDateTime.of(date, time)
    val offsets = zoneId.rules.getValidOffsets(local)
    return when {
        offsets.isNotEmpty() -> ZonedDateTime.ofLocal(local, zoneId, offsets.first())
        else -> {
            val transition = requireNotNull(zoneId.rules.getTransition(local))
            ZonedDateTime.ofLocal(local, zoneId, transition.offsetBefore)
        }
    }
}

internal fun formatDateTime(value: ZonedDateTime, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .format(value)

internal fun formatCoordinates(latitude: Double, longitude: Double): String =
    String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude)
