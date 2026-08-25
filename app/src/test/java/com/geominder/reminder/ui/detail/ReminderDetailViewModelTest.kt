package com.geominder.reminder.ui.detail

import com.geominder.reminder.domain.model.Reminder
import com.geominder.reminder.domain.model.ReminderId
import com.geominder.reminder.domain.model.GeoTrigger
import com.geominder.reminder.domain.model.TimeTrigger
import com.geominder.reminder.domain.repository.ReminderRepository
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Test

class ReminderDetailViewModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `observes and presents the requested reminder`() {
        val reminder = reminder()
        val repository = FakeRepository(reminder)
        val state = ReminderDetailViewModel(repository, reminder.id, injectedScope = scope).uiState.value

        assertFalse(state.isLoading)
        assertEquals(reminder.title, state.title)
        assertEquals(reminder.sourceText, state.sourceText)
        assertNotNull(state.timeText)
    }

    @Test
    fun `updates and deletion flow into state`() {
        val reminder = reminder()
        val repository = FakeRepository(reminder)
        val viewModel = ReminderDetailViewModel(repository, reminder.id, injectedScope = scope)

        repository.state.value = reminder.copy(title = "Updated")
        assertEquals("Updated", viewModel.uiState.value.title)
        repository.state.value = null
        assertTrue(viewModel.uiState.value.isNotFound)
    }

    @Test
    fun `distance is localized while coordinates remain invariant`() {
        val reminder = reminder().copy(
            geoTrigger = GeoTrigger(
                latitude = 40.7128,
                longitude = -74.0060,
                radiusMeters = 1_500.0,
            ),
        )
        val us = ReminderDetailUiState.present(reminder, ZoneOffset.UTC, Locale.US)
        val german = ReminderDetailUiState.present(reminder, ZoneOffset.UTC, Locale.GERMANY)

        assertEquals("40.71280, -74.00600", us.geoCoordinates)
        assertEquals(us.geoCoordinates, german.geoCoordinates)
        assertEquals("1.5 km", us.geoRadius)
        assertEquals("1,5 km", german.geoRadius)
    }

    private fun reminder() = Reminder(
        id = ReminderId("detail-test"),
        sourceText = "Call Mum at 18:00",
        title = "Call Mum",
        text = "Call Mum at 18:00",
        timeTrigger = TimeTrigger(exactAt = Instant.parse("2026-08-25T18:00:00Z")),
        createdAt = Instant.parse("2026-08-24T10:00:00Z"),
    )

    private class FakeRepository(initial: Reminder?) : ReminderRepository {
        val state = MutableStateFlow(initial)
        override fun observeAll(): Flow<List<Reminder>> = state.map { listOfNotNull(it) }
        override fun observe(id: ReminderId): Flow<Reminder?> = state
        override suspend fun get(id: ReminderId): Reminder? = state.value
        override suspend fun getPending(): List<Reminder> = listOfNotNull(state.value)
        override suspend fun save(reminder: Reminder) { state.value = reminder }
        override suspend fun delete(id: ReminderId) { state.value = null }
        override suspend fun setEnabled(id: ReminderId, enabled: Boolean, changedAt: Instant) = Unit
        override suspend fun recordTriggered(id: ReminderId, triggeredAt: Instant) = Unit
        override suspend fun snooze(id: ReminderId, until: Instant, changedAt: Instant) = Unit
        override suspend fun dismiss(id: ReminderId, changedAt: Instant) = Unit
        override suspend fun complete(id: ReminderId, changedAt: Instant) = Unit
    }
}
