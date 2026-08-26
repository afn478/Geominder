package com.afn478.geominder.ui.list

import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.ReminderStatus
import com.afn478.geominder.domain.model.TimeTrigger
import com.afn478.geominder.domain.repository.ReminderRepository
import com.afn478.geominder.settings.ReminderSettings
import com.afn478.geominder.settings.ReminderSortDirection
import com.afn478.geominder.settings.ReminderSortField
import com.afn478.geominder.settings.ReminderSortOrder
import com.afn478.geominder.settings.SettingsRepository
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
        assertEquals("Active", viewModel.uiState.value.items.single().lifecycle.label)
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
    fun `confirmed deletion cancels scheduling before deleting persistence`() {
        val events = mutableListOf<String>()
        val repository = FakeRepository(listOf(reminder()), events)
        val commands = RecordingCommandHandler(events)
        val viewModel = viewModel(repository, commands)

        viewModel.requestDelete(ID)
        assertEquals(ID, viewModel.uiState.value.deleteCandidate?.id)
        viewModel.confirmDelete()

        assertEquals(listOf("command:Cancel", "delete"), events)
        assertTrue(repository.current().isEmpty())
        assertNull(viewModel.uiState.value.deleteCandidate)
    }

    @Test
    fun `failed cancellation leaves reminder persisted and surfaces error`() {
        val repository = FakeRepository(listOf(reminder()))
        val viewModel = viewModel(
            repository = repository,
            handler = ReminderScheduleCommandHandler { error("Scheduler unavailable") },
        )

        viewModel.requestDelete(ID)
        viewModel.confirmDelete()

        assertEquals(1, repository.current().size)
        assertEquals("Scheduler unavailable", viewModel.uiState.value.message)
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
        assertEquals("Persistence unavailable", viewModel.uiState.value.message)
    }

    @Test
    fun `failed deletion restores registration for a pending reminder`() {
        val events = mutableListOf<String>()
        val repository = FakeRepository(
            reminders = listOf(reminder()),
            events = events,
            failDelete = true,
        )
        val viewModel = viewModel(repository, RecordingCommandHandler(events))

        viewModel.requestDelete(ID)
        viewModel.confirmDelete()

        assertEquals(listOf("command:Cancel", "delete", "command:Register"), events)
        assertEquals(1, repository.current().size)
        assertEquals("Persistence unavailable", viewModel.uiState.value.message)
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
        assertEquals("Edit this reminder to re-arm it", viewModel.uiState.value.message)
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
        assertEquals("Persistence unavailable", viewModel.uiState.value.message)
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
        assertEquals("Persistence unavailable", viewModel.uiState.value.message)
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
