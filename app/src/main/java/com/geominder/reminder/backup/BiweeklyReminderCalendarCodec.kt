package com.geominder.reminder.backup

import biweekly.Biweekly
import biweekly.ICalDataType
import biweekly.ICalVersion
import biweekly.ICalendar
import biweekly.component.ICalComponent
import biweekly.component.RawComponent
import biweekly.component.VAlarm
import biweekly.component.VTodo
import biweekly.io.scribe.property.TextPropertyScribe
import biweekly.parameter.Related
import biweekly.property.Name
import biweekly.property.Status
import biweekly.property.TextProperty
import biweekly.property.Trigger
import biweekly.property.Uid
import biweekly.property.Url
import com.geominder.reminder.domain.model.GeoTrigger
import com.geominder.reminder.domain.model.Reminder
import com.geominder.reminder.domain.model.ReminderId
import com.geominder.reminder.domain.model.ReminderStatus
import com.geominder.reminder.domain.model.TimeTrigger
import com.geominder.reminder.domain.model.TriggerId
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.Date

/** iCalendar codec whose grammar, escaping, and line folding are all handled by biweekly. */
class BiweeklyReminderCalendarCodec(
    private val limits: CalendarImportLimits = CalendarImportLimits(),
) : ReminderCalendarCodec {
    override fun encode(reminders: Collection<Reminder>, output: OutputStream) {
        val calendar = ICalendar().apply {
            setProductId(PRODUCT_ID)
            reminders.forEach { addTodo(it.toVTodo()) }
        }

        Biweekly.write(calendar)
            .register(SourceTextScribe())
            .version(ICalVersion.V2_0)
            .foldLines(true)
            .go(output)
    }

    override fun decode(input: InputStream, importedAt: Instant): CalendarDecodeResult {
        val parseWarnings = mutableListOf<List<biweekly.io.ParseWarning>>()
        val calendars = Biweekly.parse(SizeLimitedInputStream(input, limits.maxBytes))
            .register(SourceTextScribe())
            .warnings(parseWarnings)
            .all()

        val issues = parseWarnings.flatten().map { warning ->
            CalendarImportIssue(
                code = "ICAL_PARSE_WARNING_${warning.code ?: "UNKNOWN"}",
                message = buildString {
                    append(warning.message)
                    warning.lineNumber?.let { append(" (line ").append(it).append(')') }
                },
                severity = CalendarImportIssueSeverity.WARNING,
            )
        }.toMutableList()

        if (calendars.isEmpty()) {
            issues += CalendarImportIssue(
                code = "NO_CALENDAR",
                message = "The document does not contain an iCalendar object.",
                severity = CalendarImportIssueSeverity.ERROR,
            )
            return CalendarDecodeResult(emptyList(), 0, issues)
        }

        val todos = calendars.flatMap(ICalendar::getTodos)
        if (todos.size > limits.maxTodos) {
            throw CalendarImportLimitException(
                "The calendar contains ${todos.size} tasks; the limit is ${limits.maxTodos}.",
            )
        }

        val entries = buildList {
            todos.forEachIndexed { index, todo ->
                val decoded = decodeTodo(todo, index, importedAt)
                issues += decoded.issues
                decoded.reminder?.let { add(DecodedCalendarReminder(it, index)) }
            }
        }

        return CalendarDecodeResult(
            entries = entries,
            totalTodos = todos.size,
            issues = issues,
        )
    }

    private fun decodeTodo(
        todo: VTodo,
        componentIndex: Int,
        importedAt: Instant,
    ): DecodedTodo {
        val uid = todo.uid?.value?.trim()
        if (uid.isNullOrEmpty()) {
            return DecodedTodo.error(componentIndex, null, "MISSING_UID", "VTODO has no UID.")
        }
        if (uid.length > limits.maxUidLength) {
            return DecodedTodo.error(
                componentIndex,
                uid.take(limits.maxUidLength),
                "UID_TOO_LONG",
                "VTODO UID exceeds ${limits.maxUidLength} characters.",
            )
        }

        val issues = mutableListOf<CalendarImportIssue>()
        val timeTrigger = decodeTimeTrigger(todo, componentIndex, uid, issues)
        val geoTrigger = decodeGeoTrigger(todo, componentIndex, uid, issues)
        if (timeTrigger == null && geoTrigger == null) {
            issues += issue(
                componentIndex,
                uid,
                "MISSING_TRIGGER",
                "VTODO has no supported time or arrival trigger.",
                CalendarImportIssueSeverity.ERROR,
            )
            return DecodedTodo(null, issues)
        }

        val title = todo.summary?.value?.takeIf(String::isNotBlank)
            ?: todo.description?.value?.takeIf(String::isNotBlank)
            ?: "Imported reminder"
        val text = todo.description?.value ?: title
        val sourceText = (
            todo.getProperty(SourceText::class.java)?.value
                ?: todo.rawValue(X_SOURCE_TEXT)
            )?.takeIf(String::isNotBlank) ?: text

        val stampedAt = todo.lastModified?.value?.toInstant()
            ?: todo.dateTimeStamp?.value?.toInstant()
        val createdAt = todo.created?.value?.toInstant() ?: stampedAt ?: importedAt
        val requestedUpdatedAt = stampedAt ?: createdAt
        val updatedAt = if (requestedUpdatedAt.isBefore(createdAt)) {
            issues += issue(
                componentIndex,
                uid,
                "TIMESTAMP_ORDER_NORMALIZED",
                "LAST-MODIFIED preceded CREATED and was normalized.",
                CalendarImportIssueSeverity.WARNING,
            )
            createdAt
        } else {
            requestedUpdatedAt
        }

        val importedStatus = decodeStatus(todo, componentIndex, uid, issues)
        val snoozedUntil = todo.instantValue(X_SNOOZED_UNTIL, componentIndex, uid, issues)
        val dismissedAt = todo.instantValue(X_DISMISSED_AT, componentIndex, uid, issues)
        val status = when {
            importedStatus == ReminderStatus.SNOOZED && snoozedUntil == null -> {
                issues += issue(
                    componentIndex,
                    uid,
                    "INVALID_SNOOZED_STATE",
                    "SNOOZED status had no valid snooze deadline; restored as PENDING.",
                    CalendarImportIssueSeverity.WARNING,
                )
                ReminderStatus.PENDING
            }

            else -> importedStatus
        }

        val normalizedDismissedAt = when {
            status == ReminderStatus.DISMISSED -> dismissedAt ?: updatedAt
            else -> dismissedAt
        }

        val reminder = runCatching {
            Reminder(
                id = ReminderId(uid),
                sourceText = sourceText,
                title = title,
                text = text,
                enabled = todo.booleanValue(X_ENABLED, default = true, componentIndex, uid, issues),
                status = status,
                timeTrigger = timeTrigger,
                geoTrigger = geoTrigger,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastTriggeredAt = todo.instantValue(X_LAST_TRIGGERED_AT, componentIndex, uid, issues),
                snoozedUntil = snoozedUntil,
                dismissedAt = normalizedDismissedAt,
            )
        }.getOrElse { error ->
            issues += issue(
                componentIndex,
                uid,
                "INVALID_REMINDER",
                error.message ?: "VTODO could not be converted into a reminder.",
                CalendarImportIssueSeverity.ERROR,
            )
            null
        }

        return DecodedTodo(reminder, issues)
    }

    private fun decodeTimeTrigger(
        todo: VTodo,
        componentIndex: Int,
        uid: String,
        issues: MutableList<CalendarImportIssue>,
    ): TimeTrigger? {
        val timeAlarms = todo.alarms.filterNot { it.hasArrivalProximity() }
        val candidates = timeAlarms.mapNotNull { alarm ->
            val triggerAt = alarm.trigger?.let { trigger ->
                trigger.date?.toInstant()
                    ?: trigger.duration?.let { duration ->
                        val anchor = when (trigger.related) {
                            Related.END -> todo.dateDue?.value
                            else -> todo.dateStart?.value
                        }
                        anchor?.let(duration::add)?.toInstant()
                    }
            }
            triggerAt?.let { alarm to it }
        }
        if (timeAlarms.isNotEmpty() && candidates.isEmpty()) {
            issues += issue(
                componentIndex,
                uid,
                "INVALID_TIME_TRIGGER",
                "VALARM has no resolvable absolute or relative TRIGGER.",
                CalendarImportIssueSeverity.ERROR,
            )
        }
        if (candidates.size > 1) {
            issues += issue(
                componentIndex,
                uid,
                "MULTIPLE_TIME_TRIGGERS",
                "Only the first of ${candidates.size} time alarms was restored.",
                CalendarImportIssueSeverity.WARNING,
            )
        }

        return candidates.firstOrNull()?.let { (alarm, exactAt) ->
            TimeTrigger(
                id = TriggerId(alarm.componentUid() ?: "$uid-time"),
                exactAt = exactAt,
            )
        }
    }

    private fun decodeGeoTrigger(
        todo: VTodo,
        componentIndex: Int,
        uid: String,
        issues: MutableList<CalendarImportIssue>,
    ): GeoTrigger? {
        val proximityAlarms = todo.alarms.filter { it.hasArrivalProximity() }
        val candidates = proximityAlarms.mapNotNull { alarm ->
            val location = alarm.getExperimentalComponents(VLOCATION).firstOrNull()
            if (location == null) {
                issues += issue(
                    componentIndex,
                    uid,
                    "MISSING_VLOCATION",
                    "Arrival alarm has no VLOCATION component.",
                    CalendarImportIssueSeverity.ERROR,
                )
                return@mapNotNull null
            }

            val geoUri = location.getProperty(Url::class.java)?.value
                ?: location.rawValue("URL")
            val parsed = geoUri?.let(::parseGeoUri)
            if (parsed == null) {
                issues += issue(
                    componentIndex,
                    uid,
                    "INVALID_GEO_URI",
                    "VLOCATION must contain a valid geo URI with a positive u radius.",
                    CalendarImportIssueSeverity.ERROR,
                )
                return@mapNotNull null
            }

            val activeFrom = (alarm.rawValue(X_ACTIVE_FROM_TIME)
                ?: todo.rawValue(X_ACTIVE_FROM_TIME))?.let { raw ->
                parseInstant(raw, componentIndex, uid, X_ACTIVE_FROM_TIME, issues)
            }
            val triggerId = alarm.componentUid()
                ?: location.componentUid()?.removeSuffix("-location")
                ?: "$uid-geo"
            val label = location.getProperty(Name::class.java)?.value
                ?: location.rawValue("NAME")

            runCatching {
                GeoTrigger(
                    id = TriggerId(triggerId),
                    latitude = parsed.latitude,
                    longitude = parsed.longitude,
                    radiusMeters = parsed.radiusMeters,
                    label = label?.takeIf(String::isNotBlank),
                    activeFrom = activeFrom,
                )
            }.getOrElse { error ->
                issues += issue(
                    componentIndex,
                    uid,
                    "INVALID_GEO_TRIGGER",
                    error.message ?: "VLOCATION contains invalid coordinates.",
                    CalendarImportIssueSeverity.ERROR,
                )
                null
            }
        }

        if (proximityAlarms.size > 1) {
            issues += issue(
                componentIndex,
                uid,
                "MULTIPLE_GEO_TRIGGERS",
                "Only the first valid arrival alarm was restored.",
                CalendarImportIssueSeverity.WARNING,
            )
        }
        return candidates.firstOrNull()
    }

    private fun decodeStatus(
        todo: VTodo,
        componentIndex: Int,
        uid: String,
        issues: MutableList<CalendarImportIssue>,
    ): ReminderStatus {
        todo.rawValue(X_STATUS)?.let { raw ->
            runCatching { ReminderStatus.valueOf(raw.uppercase()) }
                .onFailure {
                    issues += issue(
                        componentIndex,
                        uid,
                        "INVALID_STATUS",
                        "Unknown $X_STATUS value '$raw'; standard STATUS was used.",
                        CalendarImportIssueSeverity.WARNING,
                    )
                }
                .getOrNull()
                ?.let { return it }
        }

        return when {
            todo.status?.isCompleted == true -> ReminderStatus.COMPLETED
            todo.status?.isCancelled == true -> ReminderStatus.DISMISSED
            else -> ReminderStatus.PENDING
        }
    }

    private fun Reminder.toVTodo(): VTodo = VTodo().also { todo ->
        todo.setUid(id.value)
        todo.setDateTimeStamp(updatedAt.toDate())
        todo.setCreated(createdAt.toDate())
        todo.setLastModified(updatedAt.toDate())
        todo.setSummary(title)
        todo.setDescription(text)
        todo.status = when (status) {
            ReminderStatus.PENDING -> Status.needsAction()
            ReminderStatus.SNOOZED -> Status.inProgress()
            ReminderStatus.DISMISSED -> Status.cancelled()
            ReminderStatus.COMPLETED -> Status.completed()
        }
        if (status == ReminderStatus.COMPLETED) {
            todo.setCompleted(lastTriggeredAt?.toDate() ?: updatedAt.toDate())
        }

        todo.addProperty(SourceText(sourceText))
        todo.addText(X_STATUS, status.name)
        todo.addText(X_ENABLED, enabled.toString())
        lastTriggeredAt?.let { todo.addText(X_LAST_TRIGGERED_AT, it.toString()) }
        snoozedUntil?.let { todo.addText(X_SNOOZED_UNTIL, it.toString()) }
        dismissedAt?.let { todo.addText(X_DISMISSED_AT, it.toString()) }

        timeTrigger?.let { trigger ->
            todo.setDateStart(trigger.exactAt.toDate())
            todo.addAlarm(
                VAlarm.display(Trigger(trigger.exactAt.toDate()), text).apply {
                    addProperty(Uid(trigger.id.value))
                    addText(X_TRIGGER_KIND, TRIGGER_KIND_TIME)
                },
            )
        }

        geoTrigger?.let { trigger ->
            trigger.activeFrom?.let { todo.addText(X_ACTIVE_FROM_TIME, it.toString()) }
            todo.addAlarm(trigger.toProximityAlarm(text))
        }
    }

    private fun GeoTrigger.toProximityAlarm(description: String): VAlarm =
        VAlarm.display(Trigger(Date.from(IGNORED_PROXIMITY_TRIGGER)), description).apply {
            addProperty(Uid(id.value))
            addText(PROXIMITY, PROXIMITY_ARRIVE)
            addText(X_TRIGGER_KIND, TRIGGER_KIND_GEO)
            activeFrom?.let { addText(X_ACTIVE_FROM_TIME, it.toString()) }
            addComponent(
                RawComponent(VLOCATION).apply {
                    addProperty(Uid("${id.value}-location"))
                    label?.let { addProperty(Name(it)) }
                    addProperty(Url(toGeoUri()))
                },
            )
        }

    private fun GeoTrigger.toGeoUri(): String = buildString {
        append("geo:")
        append(latitude)
        append(',')
        append(longitude)
        append(";u=")
        append(radiusMeters)
    }

    private fun ICalComponent.addText(name: String, value: String) {
        addExperimentalProperty(name, ICalDataType.TEXT, value)
    }

    private fun ICalComponent.rawValue(name: String): String? =
        getExperimentalProperty(name)?.value

    private fun ICalComponent.componentUid(): String? =
        getProperty(Uid::class.java)?.value?.takeIf(String::isNotBlank)
            ?: rawValue("UID")?.takeIf(String::isNotBlank)

    private fun VAlarm.hasArrivalProximity(): Boolean =
        rawValue(PROXIMITY)?.equals(PROXIMITY_ARRIVE, ignoreCase = true) == true

    private fun ICalComponent.instantValue(
        name: String,
        componentIndex: Int,
        uid: String,
        issues: MutableList<CalendarImportIssue>,
    ): Instant? = rawValue(name)?.let { parseInstant(it, componentIndex, uid, name, issues) }

    private fun ICalComponent.booleanValue(
        name: String,
        default: Boolean,
        componentIndex: Int,
        uid: String,
        issues: MutableList<CalendarImportIssue>,
    ): Boolean {
        val raw = rawValue(name) ?: return default
        return when (raw.lowercase()) {
            "true" -> true
            "false" -> false
            else -> {
                issues += issue(
                    componentIndex,
                    uid,
                    "INVALID_BOOLEAN",
                    "$name must be TRUE or FALSE; '$raw' was ignored.",
                    CalendarImportIssueSeverity.WARNING,
                )
                default
            }
        }
    }

    private fun parseInstant(
        raw: String,
        componentIndex: Int,
        uid: String,
        property: String,
        issues: MutableList<CalendarImportIssue>,
    ): Instant? = runCatching { Instant.parse(raw) }.getOrElse {
        issues += issue(
            componentIndex,
            uid,
            "INVALID_INSTANT",
            "$property does not contain a valid UTC instant.",
            CalendarImportIssueSeverity.WARNING,
        )
        null
    }

    private fun parseGeoUri(value: String): ParsedGeoUri? {
        if (!value.startsWith("geo:", ignoreCase = true)) return null
        val body = value.substringAfter(':')
        val coordinatePart = body.substringBefore(';')
        val coordinates = coordinatePart.split(',')
        if (coordinates.size !in 2..3) return null
        val latitude = coordinates[0].toDoubleOrNull() ?: return null
        val longitude = coordinates[1].toDoubleOrNull() ?: return null
        val radius = body.substringAfter(';', missingDelimiterValue = "")
            .split(';')
            .mapNotNull { parameter ->
                val separator = parameter.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                parameter.substring(0, separator) to parameter.substring(separator + 1)
            }
            .firstOrNull { (name, _) -> name.equals("u", ignoreCase = true) }
            ?.second
            ?.toDoubleOrNull()
            ?: return null
        if (!latitude.isFinite() || !longitude.isFinite() || !radius.isFinite() || radius <= 0.0) {
            return null
        }
        return ParsedGeoUri(latitude, longitude, radius)
    }

    private fun issue(
        componentIndex: Int,
        uid: String?,
        code: String,
        message: String,
        severity: CalendarImportIssueSeverity,
    ) = CalendarImportIssue(code, message, severity, componentIndex, uid)

    private data class ParsedGeoUri(
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Double,
    )

    private data class DecodedTodo(
        val reminder: Reminder?,
        val issues: List<CalendarImportIssue>,
    ) {
        companion object {
            fun error(
                componentIndex: Int,
                uid: String?,
                code: String,
                message: String,
            ) = DecodedTodo(
                reminder = null,
                issues = listOf(
                    CalendarImportIssue(
                        code = code,
                        message = message,
                        severity = CalendarImportIssueSeverity.ERROR,
                        componentIndex = componentIndex,
                        uid = uid,
                    ),
                ),
            )
        }
    }

    private class SourceText(value: String) : TextProperty(value)

    private class SourceTextScribe : TextPropertyScribe<SourceText>(
        SourceText::class.java,
        X_SOURCE_TEXT,
    ) {
        override fun newInstance(value: String, version: ICalVersion): SourceText = SourceText(value)
    }

    companion object {
        private const val PRODUCT_ID = "-//Geominder//Reminder Backup 1.0//EN"
        private const val PROXIMITY = "PROXIMITY"
        private const val PROXIMITY_ARRIVE = "ARRIVE"
        private const val VLOCATION = "VLOCATION"
        private const val X_SOURCE_TEXT = "X-SOURCE-TEXT"
        private const val X_ACTIVE_FROM_TIME = "X-ACTIVE-FROM-TIME"
        private const val X_STATUS = "X-GEOMINDER-STATUS"
        private const val X_ENABLED = "X-GEOMINDER-ENABLED"
        private const val X_LAST_TRIGGERED_AT = "X-GEOMINDER-LAST-TRIGGERED-AT"
        private const val X_SNOOZED_UNTIL = "X-GEOMINDER-SNOOZED-UNTIL"
        private const val X_DISMISSED_AT = "X-GEOMINDER-DISMISSED-AT"
        private const val X_TRIGGER_KIND = "X-GEOMINDER-TRIGGER-KIND"
        private const val TRIGGER_KIND_TIME = "TIME"
        private const val TRIGGER_KIND_GEO = "GEO"

        /** RFC 9074's conventional ignored trigger for proximity alarms. */
        private val IGNORED_PROXIMITY_TRIGGER = Instant.parse("1976-04-01T00:55:45Z")
    }
}

data class CalendarImportLimits(
    val maxBytes: Long = 10L * 1024L * 1024L,
    val maxTodos: Int = 10_000,
    val maxUidLength: Int = 512,
) {
    init {
        require(maxBytes > 0) { "Maximum byte count must be positive" }
        require(maxTodos > 0) { "Maximum task count must be positive" }
        require(maxUidLength > 0) { "Maximum UID length must be positive" }
    }
}

class CalendarImportLimitException(message: String) : IOException(message)

private class SizeLimitedInputStream(
    input: InputStream,
    private val maxBytes: Long,
) : FilterInputStream(input) {
    private var bytesRead = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) account(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) account(count.toLong())
        return count
    }

    private fun account(count: Long) {
        bytesRead += count
        if (bytesRead > maxBytes) {
            throw CalendarImportLimitException("Calendar exceeds the $maxBytes-byte import limit.")
        }
    }
}

private fun Instant.toDate(): Date = Date.from(this)
