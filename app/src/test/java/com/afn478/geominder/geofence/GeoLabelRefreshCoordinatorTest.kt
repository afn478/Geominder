package com.afn478.geominder.geofence

import com.afn478.geominder.domain.model.GeoTrigger
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.TimeTrigger
import com.afn478.geominder.domain.model.TriggerId
import com.afn478.geominder.domain.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GeoLabelRefreshCoordinatorTest {
    @Test
    fun `startup resolves blank and flexible coordinate labels but not human labels`() = runBlocking {
        val blank = reminder(null)
        val fallback = reminder(" near 40.7128,   -74.0060 ")
        val human = reminder("42 Broadway, New York")
        val repository = FakeRepository(listOf(blank, fallback, human))
        GeoLabelRefreshCoordinator(
            repository,
            GeoLabelResolver { _, _, callback -> callback("42 Broadway, New York") },
            FakeNetworkMonitor(),
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        ).also { it.start() }

        assertEquals(2, repository.saved.size)
        assertTrue(repository.saved.all { it.geoTrigger?.label == "42 Broadway, New York" })
    }

    @Test
    fun `fallback resolver result is not persisted`() = runBlocking {
        val repository = FakeRepository(listOf(reminder(null)))
        GeoLabelRefreshCoordinator(
            repository,
            GeoLabelResolver { _, _, callback -> callback("near 40.71280, -74.00600") },
            FakeNetworkMonitor(),
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        ).also { it.start() }
        assertTrue(repository.saved.isEmpty())
    }

    private fun reminder(label: String?): Reminder = Reminder(
        id = ReminderId("reminder-$label"), sourceText = "source", title = "title", text = "text",
        timeTrigger = TimeTrigger(exactAt = Instant.parse("2026-08-25T10:00:00Z")),
        geoTrigger = GeoTrigger(TriggerId("trigger-$label"), 40.7128, -74.0060, 100.0, label),
        createdAt = Instant.parse("2026-08-25T09:00:00Z"),
    )

    private class FakeNetworkMonitor : NetworkAvailabilityMonitor {
        override fun start(onAvailable: () -> Unit) = Unit
        override fun stop() = Unit
    }

    private class FakeRepository(initial: List<Reminder>) : ReminderRepository {
        private val values = initial.toMutableList()
        val saved = mutableListOf<Reminder>()
        override fun observeAll(): Flow<List<Reminder>> = flowOf(values)
        override fun observe(id: ReminderId): Flow<Reminder?> = flowOf(values.find { it.id == id })
        override suspend fun get(id: ReminderId) = values.find { it.id == id }
        override suspend fun getPending() = values.filter(Reminder::isPending)
        override suspend fun save(reminder: Reminder) { saved += reminder }
        override suspend fun delete(id: ReminderId) = Unit
        override suspend fun setEnabled(id: ReminderId, enabled: Boolean, changedAt: Instant) = Unit
        override suspend fun recordTriggered(id: ReminderId, triggeredAt: Instant) = Unit
        override suspend fun snooze(id: ReminderId, until: Instant, changedAt: Instant) = Unit
        override suspend fun dismiss(id: ReminderId, changedAt: Instant) = Unit
        override suspend fun complete(id: ReminderId, changedAt: Instant) = Unit
    }
}
