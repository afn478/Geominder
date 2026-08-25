package com.geominder.reminder.backup

import com.geominder.reminder.domain.model.Reminder

/** Result of converting a calendar stream into domain reminders, before persistence. */
data class CalendarDecodeResult(
    val entries: List<DecodedCalendarReminder>,
    val totalTodos: Int,
    val issues: List<CalendarImportIssue>,
) {
    val reminders: List<Reminder>
        get() = entries.map(DecodedCalendarReminder::reminder)
}

data class DecodedCalendarReminder(
    val reminder: Reminder,
    val componentIndex: Int,
)

enum class CalendarImportIssueSeverity {
    WARNING,
    ERROR,
}

/** A safe-to-display explanation of a parse, validation, persistence, or scheduling problem. */
data class CalendarImportIssue(
    val code: String,
    val message: String,
    val severity: CalendarImportIssueSeverity,
    val componentIndex: Int? = null,
    val uid: String? = null,
)

enum class CalendarImportDisposition {
    INSERTED,
    UPDATED,
    SKIPPED,
}

data class CalendarImportItem(
    val uid: String?,
    val componentIndex: Int,
    val disposition: CalendarImportDisposition,
    val message: String? = null,
)

/** Detailed import outcome. A fatal error leaves [items] empty and changes no reminders. */
data class CalendarImportResult(
    val totalTodos: Int,
    val inserted: Int,
    val updated: Int,
    val skipped: Int,
    val scheduled: Int,
    val schedulingFailed: Int,
    val items: List<CalendarImportItem>,
    val issues: List<CalendarImportIssue>,
    val fatalError: String? = null,
) {
    val succeeded: Boolean
        get() = fatalError == null
}

data class CalendarExportResult(
    val exported: Int,
)

/** Runs after a reminder has been successfully upserted during restore. */
fun interface PostImportScheduler {
    suspend fun schedule(reminder: Reminder)

    companion object {
        val NoOp = PostImportScheduler { }
    }
}
