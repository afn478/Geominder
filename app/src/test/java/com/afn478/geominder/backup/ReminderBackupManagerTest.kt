package com.afn478.geominder.backup

import biweekly.Biweekly
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.ReminderStatus
import com.afn478.geominder.domain.model.TimeTrigger
import com.afn478.geominder.domain.repository.ReminderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ReminderBackupManagerTest {
    @Test
    fun `import upserts by UID skips duplicates and invokes scheduling callback`() = runBlocking {
        val existing = reminder("existing", "Old title")
        val repository = FakeReminderRepository(existing)
        val scheduled = mutableListOf<String>()
        val manager = manager(
            repository = repository,
            scheduler = PostImportScheduler { scheduled += it.id.value },
        )
        val input = calendar(
            timeTodo("existing", "Updated title", "20260825T100000Z"),
            timeTodo("new", "First new title", "20260825T110000Z"),
            timeTodo("new", "Duplicate title", "20260825T120000Z"),
        )

        val result = manager.importFrom(input.byteInputStream())

        assertTrue(result.succeeded)
        assertEquals(1, result.inserted)
        assertEquals(1, result.updated)
        assertEquals(1, result.skipped)
        assertEquals(2, result.scheduled)
        assertEquals(0, result.schedulingFailed)
        assertEquals(listOf("existing", "new"), scheduled)
        assertEquals("Updated title", repository.values.getValue("existing").title)
        assertEquals("First new title", repository.values.getValue("new").title)
        assertTrue(result.issues.any { it.code == "DUPLICATE_UID" })
    }

    @Test
    fun `scheduling failure is reported without rolling back a successful upsert`() = runBlocking {
        val repository = FakeReminderRepository()
        val manager = manager(
            repository = repository,
            scheduler = PostImportScheduler { error("Exact alarm permission unavailable") },
        )

        val result = manager.importFrom(
            calendar(timeTodo("saved", "Saved reminder", "20260825T100000Z")).byteInputStream(),
        )

        assertEquals(1, result.inserted)
        assertEquals(0, result.scheduled)
        assertEquals(1, result.schedulingFailed)
        assertTrue("saved" in repository.values)
        assertTrue(result.issues.any { it.code == "SCHEDULING_FAILED" })
    }

    @Test
    fun `fatal parse failure returns a detailed result and performs no writes`() = runBlocking {
        val repository = FakeReminderRepository()
        val codec = BiweeklyReminderCalendarCodec(CalendarImportLimits(maxBytes = 8))
        val manager = manager(repository, codec = codec)

        val result = manager.importFrom(ByteArrayInputStream(ByteArray(32) { 'A'.code.toByte() }))

        assertFalse(result.succeeded)
        assertEquals(0, repository.saveCalls)
        assertEquals("IMPORT_FAILED", result.issues.single().code)
    }

    @Test
    fun `export uses a stable created-time and UID order`() = runBlocking {
        val later = reminder("z-later", "Later", createdAt = Instant.parse("2026-08-24T11:00:00Z"))
        val sameTimeB = reminder("b", "B", createdAt = Instant.parse("2026-08-24T10:00:00Z"))
        val sameTimeA = reminder("a", "A", createdAt = Instant.parse("2026-08-24T10:00:00Z"))
        val repository = FakeReminderRepository(later, sameTimeB, sameTimeA)
        val manager = manager(repository)
        val output = ByteArrayOutputStream()

        val result = manager.exportTo(output)

        assertEquals(3, result.exported)
        val parsed = Biweekly.parse(ByteArrayInputStream(output.toByteArray())).first()
        assertEquals(listOf("a", "b", "z-later"), parsed.todos.map { it.uid.value })
    }

    private fun manager(
        repository: FakeReminderRepository,
        codec: ReminderCalendarCodec = BiweeklyReminderCalendarCodec(),
        scheduler: PostImportScheduler = PostImportScheduler.NoOp,
    ) = ReminderBackupManager(
        repository = repository,
        codec = codec,
        postImportScheduler = scheduler,
        clock = Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC),
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun reminder(
        id: String,
        title: String,
        createdAt: Instant = Instant.parse("2026-08-24T10:00:00Z"),
    ) = Reminder(
        id = ReminderId(id),
        sourceText = title,
        title = title,
        text = title,
        status = ReminderStatus.PENDING,
        timeTrigger = TimeTrigger(exactAt = Instant.parse("2026-08-25T10:00:00Z")),
        createdAt = createdAt,
    )

    private fun timeTodo(uid: String, title: String, triggerAt: String): String = """
        BEGIN:VTODO
        UID:$uid
        DTSTAMP:20260824T100000Z
        SUMMARY:$title
        DESCRIPTION:$title
        BEGIN:VALARM
        ACTION:DISPLAY
        DESCRIPTION:$title
        TRIGGER;VALUE=DATE-TIME:$triggerAt
        END:VALARM
        END:VTODO
    """.trimIndent()

    private fun calendar(vararg todos: String): String = buildString {
        append("BEGIN:VCALENDAR\r\n")
        append("VERSION:2.0\r\n")
        append("PRODID:-//Tests//Geominder//EN\r\n")
        todos.forEach { append(it.replace("\n", "\r\n")).append("\r\n") }
        append("END:VCALENDAR\r\n")
    }
}

private class FakeReminderRepository(
    vararg initial: Reminder,
) : ReminderRepository {
    val values = initial.associateByTo(linkedMapOf()) { it.id.value }
    var saveCalls = 0

    override fun observeAll(): Flow<List<Reminder>> = flowOf(values.values.toList())

    override fun observe(id: ReminderId): Flow<Reminder?> = flowOf(values[id.value])

    override suspend fun get(id: ReminderId): Reminder? = values[id.value]

    override suspend fun getPending(): List<Reminder> = values.values.filter(Reminder::isPending)

    override suspend fun save(reminder: Reminder) {
        saveCalls++
        values[reminder.id.value] = reminder
    }

    override suspend fun delete(id: ReminderId) {
        values.remove(id.value)
    }

    override suspend fun setEnabled(id: ReminderId, enabled: Boolean, changedAt: Instant) = Unit

    override suspend fun recordTriggered(id: ReminderId, triggeredAt: Instant) = Unit

    override suspend fun snooze(id: ReminderId, until: Instant, changedAt: Instant) = Unit

    override suspend fun dismiss(id: ReminderId, changedAt: Instant) = Unit

    override suspend fun complete(id: ReminderId, changedAt: Instant) = Unit
}
