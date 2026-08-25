package com.afn478.geominder.backup

import com.afn478.geominder.domain.repository.ReminderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock

/** Coordinates snapshot export and UID-based import without depending on a UI framework. */
class ReminderBackupManager(
    private val repository: ReminderRepository,
    private val codec: ReminderCalendarCodec = BiweeklyReminderCalendarCodec(),
    private val postImportScheduler: PostImportScheduler = PostImportScheduler.NoOp,
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun exportTo(output: OutputStream): CalendarExportResult = withContext(ioDispatcher) {
        val reminders = repository.observeAll().first()
            .sortedWith(compareBy({ it.createdAt }, { it.id.value }))
        codec.encode(reminders, output)
        CalendarExportResult(exported = reminders.size)
    }

    suspend fun importFrom(input: InputStream): CalendarImportResult = withContext(ioDispatcher) {
        val decoded = runCatching { codec.decode(input, clock.instant()) }
            .getOrElse { error ->
                return@withContext CalendarImportResult(
                    totalTodos = 0,
                    inserted = 0,
                    updated = 0,
                    skipped = 0,
                    scheduled = 0,
                    schedulingFailed = 0,
                    items = emptyList(),
                    issues = listOf(
                        CalendarImportIssue(
                            code = "IMPORT_FAILED",
                            message = error.message ?: "The calendar could not be read.",
                            severity = CalendarImportIssueSeverity.ERROR,
                        ),
                    ),
                    fatalError = error.message ?: "The calendar could not be read.",
                )
            }

        val issues = decoded.issues.toMutableList()
        val items = mutableListOf<CalendarImportItem>()
        val entriesByIndex = decoded.entries.associateBy(DecodedCalendarReminder::componentIndex)
        val seenUids = mutableSetOf<String>()
        var inserted = 0
        var updated = 0
        var scheduled = 0
        var schedulingFailed = 0

        repeat(decoded.totalTodos) { componentIndex ->
            val entry = entriesByIndex[componentIndex]
            if (entry == null) {
                val componentIssue = issues.firstOrNull {
                    it.componentIndex == componentIndex &&
                        it.severity == CalendarImportIssueSeverity.ERROR
                }
                items += CalendarImportItem(
                    uid = componentIssue?.uid,
                    componentIndex = componentIndex,
                    disposition = CalendarImportDisposition.SKIPPED,
                    message = componentIssue?.message ?: "VTODO was malformed.",
                )
                return@repeat
            }

            val reminder = entry.reminder
            val uid = reminder.id.value
            if (!seenUids.add(uid)) {
                val message = "A second VTODO with UID '$uid' was skipped."
                issues += CalendarImportIssue(
                    code = "DUPLICATE_UID",
                    message = message,
                    severity = CalendarImportIssueSeverity.ERROR,
                    componentIndex = componentIndex,
                    uid = uid,
                )
                items += CalendarImportItem(
                    uid = uid,
                    componentIndex = componentIndex,
                    disposition = CalendarImportDisposition.SKIPPED,
                    message = message,
                )
                return@repeat
            }

            val existed = runCatching { repository.get(reminder.id) }.getOrElse { error ->
                val message = error.message ?: "Existing reminder lookup failed."
                issues += CalendarImportIssue(
                    code = "LOOKUP_FAILED",
                    message = message,
                    severity = CalendarImportIssueSeverity.ERROR,
                    componentIndex = componentIndex,
                    uid = uid,
                )
                items += CalendarImportItem(
                    uid = uid,
                    componentIndex = componentIndex,
                    disposition = CalendarImportDisposition.SKIPPED,
                    message = message,
                )
                return@repeat
            } != null

            val persisted = runCatching { repository.save(reminder) }
            if (persisted.isFailure) {
                val message = persisted.exceptionOrNull()?.message ?: "Reminder could not be saved."
                issues += CalendarImportIssue(
                    code = "SAVE_FAILED",
                    message = message,
                    severity = CalendarImportIssueSeverity.ERROR,
                    componentIndex = componentIndex,
                    uid = uid,
                )
                items += CalendarImportItem(
                    uid = uid,
                    componentIndex = componentIndex,
                    disposition = CalendarImportDisposition.SKIPPED,
                    message = message,
                )
                return@repeat
            }

            val disposition = if (existed) {
                updated++
                CalendarImportDisposition.UPDATED
            } else {
                inserted++
                CalendarImportDisposition.INSERTED
            }

            val schedulingError = runCatching { postImportScheduler.schedule(reminder) }
                .exceptionOrNull()
            if (schedulingError == null) {
                scheduled++
            } else {
                schedulingFailed++
                issues += CalendarImportIssue(
                    code = "SCHEDULING_FAILED",
                    message = schedulingError.message ?: "Reminder scheduling failed after import.",
                    severity = CalendarImportIssueSeverity.WARNING,
                    componentIndex = componentIndex,
                    uid = uid,
                )
            }
            items += CalendarImportItem(
                uid = uid,
                componentIndex = componentIndex,
                disposition = disposition,
                message = schedulingError?.message,
            )
        }

        CalendarImportResult(
            totalTodos = decoded.totalTodos,
            inserted = inserted,
            updated = updated,
            skipped = items.count { it.disposition == CalendarImportDisposition.SKIPPED },
            scheduled = scheduled,
            schedulingFailed = schedulingFailed,
            items = items,
            issues = issues,
        )
    }
}
