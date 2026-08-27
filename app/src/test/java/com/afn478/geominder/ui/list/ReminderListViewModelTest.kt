package com.afn478.geominder.ui.list

import com.afn478.geominder.R
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.ReminderStatus
import com.afn478.geominder.domain.model.ReminderTag
import com.afn478.geominder.domain.model.TimeTrigger
import com.afn478.geominder.domain.repository.ReminderRepository
import com.afn478.geominder.settings.ReminderSettings
import com.afn478.geominder.settings.ReminderSortDirection
import com.afn478.geominder.settings.ReminderSortField
import com.afn478.geominder.settings.ReminderSortOrder
import com.afn478.geominder.settings.SettingsRepository
import com.afn478.geominder.localization.plainValue
import com.afn478.geominder.localization.resourceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.LocalTime

class ReminderListViewModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `observes repository and maps list state`() {
        val reminder = reminder()
        val viewModel = viewModel(FakeRepository(listOf(reminder)))

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(reminder.id, viewModel.uiState.value.items.single().id)
        assertEquals(
            R.string.status_active,
            viewModel.uiState.value.items.single().lifecycle.label.resourceId(),
        )
    }

    @Test
    fun `changing sort order updates the list and persists the preference`() {
        val first = reminder(id = ReminderId("first"), title = "Zebra")
        val second = reminder(id = ReminderId("second"), title = "Alpha")
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(
            repository = FakeRepository(listOf(first, second)),
            settingsRepository = settings,
        )
        val sortOrder = ReminderSortOrder(
            field = ReminderSortField.TITLE,
            direction = ReminderSortDirection.ASCENDING,
        )

        viewModel.setSortOrder(sortOrder)

        assertEquals(sortOrder, viewModel.uiState.value.sortOrder)
        assertEquals(listOf(second.id, first.id), viewModel.uiState.value.items.map { it.id })
        assertEquals(sortOrder, settings.settings.value.sortOrder)
    }

    @Test
    fun `tag filter toggles between matching reminders and all reminders`() {
        val red = reminder(id = ReminderId("red")).copy(tag = ReminderTag.RED)
        val blue = reminder(id = ReminderId("blue")).copy(tag = ReminderTag.BLUE)
        val viewModel = viewModel(FakeRepository(listOf(red, blue)))

        viewModel.toggleTagFilter(ReminderTag.RED)
        assertEquals(ReminderTag.RED, viewModel.uiState.value.selectedTag)
        assertEquals(listOf(red.id), viewModel.uiState.value.items.map { it.id })

        viewModel.toggleTagFilter(ReminderTag.RED)
        assertNull(viewModel.uiState.value.selectedTag)
        assertEquals(setOf(red.id, blue.id), viewModel.uiState.value.items.map { it.id }.toSet())

        viewModel.toggleTagFilter(ReminderTag.PURPLE)
        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun `selection supports long press seed select all and inverse selection`() {
        val first = reminder(id = ReminderId("first"))
        val second = reminder(id = ReminderId("second"), title = "Second")
        val viewModel = viewModel(FakeRepository(listOf(first, second)))

        viewModel.startSelection(first.id)

        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf(first.id), viewModel.uiState.value.selectedReminderIds)

        viewModel.selectAllReminders()
        assertEquals(
            setOf(first.id, second.id),
            viewModel.uiState.value.selectedReminderIds,
        )

        viewModel.invertSelection()
        assertTrue(viewModel.uiState.value.selectedReminderIds.isEmpty())

        viewModel.toggleSelection(second.id)
        assertEquals(setOf(second.id), viewModel.uiState.value.selectedReminderIds)
        viewModel.exitSelectionMode()
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedReminderIds.isEmpty())
    }

    @Test
    fun `deleting selected reminders moves all to trash and cancels each schedule`() {
        val events = mutableListOf<String>()
        val first = reminder(id = ReminderId("first"))
        val second = reminder(id = ReminderId("second"), title = "Second")
        val repository = FakeRepository(listOf(first, second), events)
        val viewModel = viewModel(repository, RecordingCommandHandler(events))

        viewModel.startSelection(first.id)
        viewModel.toggleSelection(second.id)
        viewModel.deleteSelectedReminders()

        assertEquals(
            listOf("command:Cancel", "moveToTrash", "command:Cancel", "moveToTrash"),
            events,
        )
        assertTrue(repository.current().all(Reminder::isDeleted))
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.selectedReminderIds.isEmpty())
        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertEquals(R.string.reminders_deleted, viewModel.uiState.value.message?.resourceId())
    }

    @Test
    fun `deleting selected reminders from trash permanently removes all`() {
        val events = mutableListOf<String>()
        val first = reminder(id = ReminderId("first")).copy(deletedAt = NOW)
        val second = reminder(id = ReminderId("second"), title = "Second")
            .copy(deletedAt = NOW)
        val repository = FakeRepository(listOf(first, second), events)
        val viewModel = viewModel(repository, RecordingCommandHandler(events))

        viewModel.toggleTrash()
        viewModel.startSelection(first.id)
        viewModel.toggleSelection(second.id)
        viewModel.deleteSelectedReminders()

        assertEquals(
            listOf("command:Cancel", "delete", "command:Cancel", "delete"),
            events,
        )
        assertTrue(repository.current().isEmpty())
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun `restoring selected reminders restores all and registers pending reminders`() {
        val events = mutableListOf<String>()
        val pending = reminder(id = ReminderId("pending")).copy(deletedAt = NOW)
        val completed = reminder(id = ReminderId("completed"), title = "Completed", enabled = false)
            .copy(
                deletedAt = NOW,
                status = ReminderStatus.COMPLETED,
            )
        val repository = FakeRepository(listOf(pending, completed), events)
        val viewModel = viewModel(repository, RecordingCommandHandler(events))

        viewModel.toggleTrash()
        viewModel.startSelection(pending.id)
        viewModel.toggleSelection(completed.id)
        viewModel.restoreSelectedReminders()

        assertEquals(
            listOf("command:Register", "restoreFromTrash", "restoreFromTrash"),
            events,
        )
        assertTrue(repository.current().none(Reminder::isDeleted))
        assertTrue(repository.current().first { it.id == pending.id }.isPending)
        assertEquals(
            ReminderStatus.COMPLETED,
            repository.current().first { it.id == completed.id }.status,
        )
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertEquals(R.string.reminders_restored, viewModel.uiState.value.message?.resourceId())
    }

    @Test
    fun `disabling cancels registration and persists the change`() {
        val events = mutableListOf<String>()
        val repository = FakeRepository(listOf(reminder()), events)
        val commands = RecordingCommandHandler(events)
        val viewModel = viewModel(repository, commands)

        viewModel.setEnabled(ID, false)

        assertFalse(repository.current().single().enabled)
        val cancel = commands.commands.single() as ReminderScheduleCommand.Cancel
        assertFalse(cancel.reminder.enabled)
        assertEquals(listOf("command:Cancel", "setEnabled:false"), events)
        assertTrue(viewModel.uiState.value.busyReminderIds.isEmpty())
    }

    @Test
    fun `enabling emits registration with updated reminder`() {
        val repository = FakeRepository(listOf(reminder(enabled = false)))
        val commands = RecordingCommandHandler()
        val viewModel = viewModel(repository, commands)

        viewModel.setEnabled(ID, true)

        assertTrue(repository.current().single().enabled)
        val register = commands.commands.single() as ReminderScheduleCommand.Register
        assertTrue(register.reminder.enabled)
        assertEquals(NOW, register.reminder.updatedAt)
    }

    @Test
    fun `deleting active reminder moves it to trash and exposes undo`() {
        val events = mutableListOf<String>()
        val repository = FakeRepository(listOf(reminder()), events)
        val commands = RecordingCommandHandler(events)
        val viewModel = viewModel(repository, commands)

        viewModel.deleteReminder(ID)

        assertEquals(listOf("command:Cancel", "moveToTrash"), events)
        assertTrue(repository.current().single().isDeleted)
        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertEquals(ID, viewModel.uiState.value.undoDeleteReminderId)
    }

    @Test
    fun `failed cancellation leaves reminder persisted and surfaces error`() {
        val repository = FakeRepository(listOf(reminder()))
        val viewModel = viewModel(
            repository = repository,
            handler = ReminderScheduleCommandHandler { error("Scheduler unavailable") },
        )

        viewModel.deleteReminder(ID)

        assertEquals(1, repository.current().size)
        assertEquals("Scheduler unavailable", viewModel.uiState.value.message?.plainValue())
        assertTrue(viewModel.uiState.value.busyReminderIds.isEmpty())
    }

    @Test
    fun `failed persistence after disabling compensates by registering again`() {
        val events = mutableListOf<String>()
        val repository = FakeRepository(
            reminders = listOf(reminder()),
            events = events,
            failSetEnabled = true,
        )
        val commands = RecordingCommandHandler(events)
        val viewModel = viewModel(repository, commands)

        viewModel.setEnabled(ID, false)

        assertTrue(repository.current().single().enabled)
        assertEquals(
            listOf("command:Cancel", "setEnabled:false", "command:Register"),
            events,
        )
        assertEquals("Persistence unavailable", viewModel.uiState.value.message?.plainValue())
    }

    @Test
    fun `failed move to trash restores registration for a pending reminder`() {
        val events = mutableListOf<String>()
        val repository = FakeRepository(
            reminders = listOf(reminder()),
            events = events,
            failMoveToTrash = true,
        )
        val viewModel = viewModel(repository, RecordingCommandHandler(events))

        viewModel.deleteReminder(ID)

        assertEquals(listOf("command:Cancel", "moveToTrash", "command:Register"), events)
        assertEquals(1, repository.current().size)
        assertEquals("Persistence unavailable", viewModel.uiState.value.message?.plainValue())
    }

    @Test
    fun `undo restores a deleted pending reminder and re-registers it`() {
        val events = mutableListOf<String>()
        val repository = FakeRepository(listOf(reminder()), events)
        val viewModel = viewModel(repository, RecordingCommandHandler(events))

        viewModel.deleteReminder(ID)
        viewModel.undoDelete(ID)

        assertEquals(
            listOf("command:Cancel", "moveToTrash", "command:Register", "restoreFromTrash"),
            events,
        )
        assertFalse(repository.current().single().isDeleted)
        assertEquals(listOf(ID), viewModel.uiState.value.items.map(ReminderListItem::id))
        assertNull(viewModel.uiState.value.undoDeleteReminderId)
    }

    @Test
    fun `deleting from trash permanently removes the reminder`() {
        val events = mutableListOf<String>()
        val repository = FakeRepository(
            reminders = listOf(reminder().copy(deletedAt = NOW)),
            events = events,
        )
        val viewModel = viewModel(repository, RecordingCommandHandler(events))

        viewModel.toggleTrash()
        viewModel.deleteReminder(ID)

        assertEquals(listOf("command:Cancel", "delete"), events)
        assertTrue(repository.current().isEmpty())
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun `restoring from trash re-registers the reminder`() {
        val events = mutableListOf<String>()
        val repository = FakeRepository(
            reminders = listOf(reminder().copy(deletedAt = NOW)),
            events = events,
        )
        val viewModel = viewModel(repository, RecordingCommandHandler(events))

        viewModel.toggleTrash()
        viewModel.restoreReminder(ID)

        assertEquals(listOf("command:Register", "restoreFromTrash"), events)
        assertFalse(repository.current().single().isDeleted)
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun `terminal reminder must be edited instead of toggled to re-arm`() {
        val repository = FakeRepository(
            listOf(
                reminder(enabled = false).copy(
                    status = ReminderStatus.COMPLETED,
                ),
            ),
        )
        val commands = RecordingCommandHandler()
        val viewModel = viewModel(repository, commands)

        viewModel.setEnabled(ID, true)

        assertFalse(repository.current().single().enabled)
        assertTrue(commands.commands.isEmpty())
        assertEquals(
            R.string.edit_to_rearm_reminder,
            viewModel.uiState.value.message?.resourceId(),
        )
    }

    @Test
    fun `marking done cancels before completing and exposes completed state`() {
        val events = mutableListOf<String>()
        val repository = FakeRepository(listOf(reminder()), events)
        val viewModel = viewModel(repository, RecordingCommandHandler(events))

        viewModel.markDone(ID)

        assertEquals(listOf("command:Cancel", "complete"), events)
        assertEquals(ReminderStatus.COMPLETED, repository.current().single().status)
        assertTrue(viewModel.uiState.value.items.single().isCompleted)
    }

    @Test
    fun `failed completion restores pending registration and surfaces error`() {
        val events = mutableListOf<String>()
        val repository = FakeRepository(
            reminders = listOf(reminder()),
            events = events,
            failComplete = true,
        )
        val viewModel = viewModel(repository, RecordingCommandHandler(events))

        viewModel.markDone(ID)

        assertEquals(
            listOf("command:Cancel", "complete", "command:Register"),
            events,
        )
        assertEquals(ReminderStatus.PENDING, repository.current().single().status)
        assertEquals("Persistence unavailable", viewModel.uiState.value.message?.plainValue())
    }

    @Test
    fun `unchecking completed reminder registers before reopening`() {
        val events = mutableListOf<String>()
        val repository = FakeRepository(
            reminders = listOf(reminder(enabled = false).copy(status = ReminderStatus.COMPLETED)),
            events = events,
        )
        val viewModel = viewModel(repository, RecordingCommandHandler(events))

        viewModel.setCompleted(ID, completed = false)

        assertEquals(listOf("command:Register", "reopen"), events)
        assertEquals(ReminderStatus.PENDING, repository.current().single().status)
        assertTrue(repository.current().single().enabled)
        assertFalse(viewModel.uiState.value.items.single().isCompleted)
    }

    @Test
    fun `failed reopening cancels speculative registration and surfaces error`() {
        val events = mutableListOf<String>()
        val repository = FakeRepository(
            reminders = listOf(reminder(enabled = false).copy(status = ReminderStatus.COMPLETED)),
            events = events,
            failReopen = true,
        )
        val viewModel = viewModel(repository, RecordingCommandHandler(events))

        viewModel.setCompleted(ID, completed = false)

        assertEquals(listOf("command:Register", "reopen", "command:Cancel"), events)
        assertEquals(ReminderStatus.COMPLETED, repository.current().single().status)
        assertFalse(repository.current().single().enabled)
        assertTrue(viewModel.uiState.value.items.single().isCompleted)
        assertEquals("Persistence unavailable", viewModel.uiState.value.message?.plainValue())
    }

    private fun viewModel(
        repository: FakeRepository,
        handler: ReminderScheduleCommandHandler = RecordingCommandHandler(),
        settingsRepository: SettingsRepository? = null,
    ) = ReminderListViewModel(
        repository = repository,
        scheduleCommandHandler = handler,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
        injectedScope = scope,
        settingsRepository = settingsRepository,
    )

    private fun reminder(
        id: ReminderId = ID,
        title: String = "Call Mum",
        enabled: Boolean = true,
    ) = Reminder(
        id = id,
        sourceText = "Call Mum tomorrow",
        title = title,
        text = "Call Mum tomorrow",
        enabled = enabled,
        timeTrigger = TimeTrigger(exactAt = NOW.plus(Duration.ofDays(1))),
        createdAt = NOW.minus(Duration.ofDays(1)),
        updatedAt = NOW.minus(Duration.ofHours(1)),
    )

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(ReminderSettings())
        override val settings: StateFlow<ReminderSettings> = state

        override suspend fun setDefaultRadiusMeters(radiusMeters: Double) {}

        override suspend fun upsertKeywordTime(keyword: String, time: LocalTime) {}

        override suspend fun removeKeyword(keyword: String) {}

        override suspend fun resetKeywordTimes() {}

        override suspend fun setSortOrder(sortOrder: ReminderSortOrder) {
            state.value = state.value.copy(sortOrder = sortOrder)
        }
    }

    private class RecordingCommandHandler(
        private val events: MutableList<String> = mutableListOf(),
    ) : ReminderScheduleCommandHandler {
        val commands = mutableListOf<ReminderScheduleCommand>()

        override suspend fun handle(command: ReminderScheduleCommand) {
            commands += command
            events += when (command) {
                is ReminderScheduleCommand.Cancel -> "command:Cancel"
                is ReminderScheduleCommand.Register -> "command:Register"
            }
        }
    }

    private class FakeRepository(
        reminders: List<Reminder>,
        private val events: MutableList<String> = mutableListOf(),
        private val failSetEnabled: Boolean = false,
        private val failDelete: Boolean = false,
        private val failMoveToTrash: Boolean = false,
        private val failComplete: Boolean = false,
        private val failReopen: Boolean = false,
    ) : ReminderRepository {
        private val reminders = MutableStateFlow(reminders)

        fun current(): List<Reminder> = reminders.value

        override fun observeAll(): Flow<List<Reminder>> = reminders

        override fun observe(id: ReminderId): Flow<Reminder?> =
            reminders.map { values -> values.firstOrNull { it.id == id } }

        override suspend fun get(id: ReminderId): Reminder? =
            reminders.value.firstOrNull { it.id == id }

        override suspend fun getPending(): List<Reminder> = reminders.value.filter(Reminder::isPending)

        override suspend fun save(reminder: Reminder) {
            reminders.value = reminders.value.filterNot { it.id == reminder.id } + reminder
        }

        override suspend fun delete(id: ReminderId) {
            events += "delete"
            if (failDelete) error("Persistence unavailable")
            reminders.value = reminders.value.filterNot { it.id == id }
        }

        override suspend fun moveToTrash(id: ReminderId, changedAt: Instant) {
            events += "moveToTrash"
            if (failMoveToTrash) error("Persistence unavailable")
            update(id) { it.copy(updatedAt = changedAt, deletedAt = changedAt) }
        }

        override suspend fun restoreFromTrash(id: ReminderId, changedAt: Instant) {
            events += "restoreFromTrash"
            update(id) { it.copy(updatedAt = changedAt, deletedAt = null) }
        }

        override suspend fun setEnabled(id: ReminderId, enabled: Boolean, changedAt: Instant) {
            events += "setEnabled:$enabled"
            if (failSetEnabled) error("Persistence unavailable")
            update(id) { it.copy(enabled = enabled, updatedAt = changedAt) }
        }

        override suspend fun recordTriggered(id: ReminderId, triggeredAt: Instant) {
            update(id) { it.copy(lastTriggeredAt = triggeredAt, updatedAt = triggeredAt) }
        }

        override suspend fun snooze(id: ReminderId, until: Instant, changedAt: Instant) {
            update(id) {
                it.copy(
                    enabled = true,
                    status = ReminderStatus.SNOOZED,
                    snoozedUntil = until,
                    updatedAt = changedAt,
                )
            }
        }

        override suspend fun dismiss(id: ReminderId, changedAt: Instant) {
            update(id) {
                it.copy(
                    enabled = false,
                    status = ReminderStatus.DISMISSED,
                    dismissedAt = changedAt,
                    updatedAt = changedAt,
                )
            }
        }

        override suspend fun complete(id: ReminderId, changedAt: Instant) {
            events += "complete"
            if (failComplete) error("Persistence unavailable")
            update(id) {
                it.copy(
                    enabled = false,
                    status = ReminderStatus.COMPLETED,
                    updatedAt = changedAt,
                )
            }
        }

        override suspend fun reopen(id: ReminderId, changedAt: Instant) {
            events += "reopen"
            if (failReopen) error("Persistence unavailable")
            update(id) {
                it.copy(
                    enabled = true,
                    status = ReminderStatus.PENDING,
                    updatedAt = changedAt,
                )
            }
        }

        private fun update(id: ReminderId, transform: (Reminder) -> Reminder) {
            reminders.value = reminders.value.map { if (it.id == id) transform(it) else it }
        }
    }

    private companion object {
        val ID = ReminderId("list-reminder")
        val NOW: Instant = Instant.parse("2026-08-24T12:00:00Z")
    }
}
