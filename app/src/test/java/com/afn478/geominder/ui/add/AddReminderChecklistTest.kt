package com.afn478.geominder.ui.add

import com.afn478.geominder.domain.model.ChecklistItem
import com.afn478.geominder.domain.model.ChecklistItemId
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.TimeTrigger
import com.afn478.geominder.domain.repository.ReminderRepository
import com.afn478.geominder.geofence.CancellationHandle
import com.afn478.geominder.geofence.CurrentLocationProvider
import com.afn478.geominder.geofence.GeoLabelResolver
import com.afn478.geominder.parser.ReminderTextParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class AddReminderChecklistTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-24T10:15:00Z"), ZoneId.of("UTC"))

    @Test
    fun `checklist text is saved verbatim and never feeds trigger detection`() {
        val repository = FakeReminderRepository()
        val actions = RecordingPostSaveActions()
        val viewModel = viewModel(repository, actions)

        viewModel.onSourceTextChange("Groceries")
        viewModel.onChecklistClick()
        val first = viewModel.uiState.value.checklist.single().id
        viewModel.onChecklistItemTextChange(first, "Milk tomorrow at 8:00")
        viewModel.addChecklistItem(after = first)
        viewModel.onChecklistItemTextChange(
            viewModel.uiState.value.checklist.last().id,
            "Bread at home",
        )
        viewModel.addChecklistItem()
        viewModel.save()

        val saved = repository.saved.single()
        assertEquals("Groceries", saved.title)
        assertEquals(listOf("Milk tomorrow at 8:00", "Bread at home"), saved.checklist.map { it.text })
        assertTrue(saved.checklist.none(ChecklistItem::done))
        // Only the default one-hour trigger: nothing in the rows was recognised.
        assertEquals(Instant.parse("2026-08-24T11:15:00Z"), saved.timeTrigger?.exactAt)
        assertNull(saved.geoTrigger)
        assertEquals(listOf(saved), actions.scheduled)
    }

    @Test
    fun `pasted lines split into rows and enter inserts below the current row`() {
        val viewModel = viewModel()

        viewModel.onChecklistClick()
        val first = viewModel.uiState.value.checklist.single().id
        viewModel.onChecklistItemTextChange(first, "Eggs\nFlour\r\n\nSugar")

        val rows = viewModel.uiState.value.checklist
        assertEquals(listOf("Eggs", "Flour", "Sugar"), rows.map(ChecklistDraftItem::text))
        assertEquals(rows.last().id, viewModel.uiState.value.checklistFocusItemId)

        viewModel.addChecklistItem(after = first)
        val inserted = viewModel.uiState.value.checklist[1]
        assertEquals("", inserted.text)
        assertEquals(inserted.id, viewModel.uiState.value.checklistFocusItemId)
    }

    @Test
    fun `removing rows moves focus up and the last removal closes the checklist`() {
        val viewModel = viewModel()

        viewModel.onChecklistClick()
        val first = viewModel.uiState.value.checklist.single().id
        viewModel.onChecklistItemTextChange(first, "One")
        viewModel.addChecklistItem(after = first)
        val second = viewModel.uiState.value.checklist.last().id

        viewModel.removeChecklistItem(second)
        assertEquals(first, viewModel.uiState.value.checklistFocusItemId)
        assertTrue(viewModel.uiState.value.hasUnsavedInput)

        viewModel.removeChecklistItem(first)
        assertFalse(viewModel.uiState.value.checklistVisible)
        assertFalse(viewModel.uiState.value.hasUnsavedInput)
    }

    @Test
    fun `editing keeps item ids order and ticks`() {
        val original = Reminder(
            id = ReminderId("r1"),
            sourceText = "Pack",
            title = "Pack",
            text = "Pack",
            timeTrigger = TimeTrigger(exactAt = Instant.parse("2026-08-25T08:00:00Z")),
            checklist = listOf(
                ChecklistItem(ChecklistItemId("a"), "Passport", done = true),
                ChecklistItem(ChecklistItemId("b"), "Charger"),
            ),
            createdAt = Instant.parse("2026-08-24T08:00:00Z"),
        )
        val repository = FakeReminderRepository(original)
        val viewModel = viewModel(repository, editingReminderId = original.id)

        assertEquals(
            original.checklist.map { ChecklistDraftItem(it.id, it.text, it.done) },
            viewModel.uiState.value.checklist,
        )
        viewModel.onChecklistItemDoneChange(ChecklistItemId("b"), true)
        viewModel.save()

        val saved = repository.saved.single()
        assertEquals(listOf("a", "b"), saved.checklist.map { it.id.value })
        assertTrue(saved.checklist.all(ChecklistItem::done))
    }

    @Test
    fun `repository default toggles a single item without touching the reminder`() = kotlinx.coroutines.runBlocking {
        val original = Reminder(
            id = ReminderId("r1"),
            sourceText = "Pack",
            title = "Pack",
            text = "Pack",
            timeTrigger = TimeTrigger(exactAt = Instant.parse("2026-08-25T08:00:00Z")),
            checklist = listOf(
                ChecklistItem(ChecklistItemId("a"), "Passport"),
                ChecklistItem(ChecklistItemId("b"), "Charger"),
            ),
            createdAt = Instant.parse("2026-08-24T08:00:00Z"),
        )
        val repository = FakeReminderRepository(original)

        repository.setChecklistItemDone(original.id, ChecklistItemId("b"), done = true)

        val saved = repository.saved.single()
        assertEquals(listOf(false, true), saved.checklist.map(ChecklistItem::done))
        assertEquals(original.copy(checklist = saved.checklist), saved)
    }

    private fun viewModel(
        repository: FakeReminderRepository = FakeReminderRepository(),
        actions: RecordingPostSaveActions = RecordingPostSaveActions(),
        editingReminderId: ReminderId? = null,
    ): AddReminderViewModel = AddReminderViewModel(
        repository = repository,
        parser = ReminderTextParser(),
        defaultGeoRadiusProvider = DefaultGeoRadiusProvider { 150.0 },
        locationProvider = CurrentLocationProvider { CancellationHandle {} },
        geoLabelResolver = GeoLabelResolver { _, _, callback -> callback("") },
        postSaveActions = actions,
        clock = clock,
        localeProvider = { Locale.US },
        injectedScope = CoroutineScope(Dispatchers.Unconfined),
        editingReminderId = editingReminderId,
    )

    private class RecordingPostSaveActions : ReminderPostSaveActions {
        val scheduled = mutableListOf<Reminder>()

        override suspend fun cancelReminder(reminder: Reminder) = Unit

        override suspend fun scheduleTimeTrigger(reminder: Reminder) {
            scheduled += reminder
        }

        override suspend fun registerGeoTrigger(reminder: Reminder) = Unit
    }

    private class FakeReminderRepository(initial: Reminder? = null) : ReminderRepository {
        val saved = mutableListOf<Reminder>().apply { initial?.let(::add) }

        override fun observeAll(): Flow<List<Reminder>> = MutableStateFlow(saved.toList())

        override fun observe(id: ReminderId): Flow<Reminder?> =
            MutableStateFlow(saved.firstOrNull { it.id == id })

        override suspend fun get(id: ReminderId): Reminder? = saved.firstOrNull { it.id == id }

        override suspend fun getPending(): List<Reminder> = saved.filter(Reminder::isPending)

        override suspend fun save(reminder: Reminder) {
            saved.removeAll { it.id == reminder.id }
            saved += reminder
        }

        override suspend fun delete(id: ReminderId) {
            saved.removeAll { it.id == id }
        }

        override suspend fun setEnabled(id: ReminderId, enabled: Boolean, changedAt: Instant) = Unit

        override suspend fun recordTriggered(id: ReminderId, triggeredAt: Instant) = Unit

        override suspend fun snooze(id: ReminderId, until: Instant, changedAt: Instant) = Unit

        override suspend fun dismiss(id: ReminderId, changedAt: Instant) = Unit

        override suspend fun complete(id: ReminderId, changedAt: Instant) = Unit
    }
}
