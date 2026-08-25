package com.geominder.reminder.alert

import com.geominder.reminder.domain.model.Reminder
import com.geominder.reminder.domain.model.ReminderId
import com.geominder.reminder.domain.model.TimeTrigger
import com.geominder.reminder.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class AlertCoordinatorsTest {
    private val now = Instant.parse("2026-08-24T12:00:00Z")
    private val reminder = Reminder(
        id = ReminderId("stable-id"),
        sourceText = "Call Alex at noon",
        title = "Call Alex",
        text = "At noon",
        timeTrigger = TimeTrigger(exactAt = now),
        createdAt = now.minusSeconds(60),
    )
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `trigger is recorded before the alert is delivered`() = runBlocking {
        val repository = RecordingRepository(reminder)
        val coordinator = AlertDeliveryCoordinator(
            repository = repository,
            alertDelivery = AlertDelivery { deliveredReminder, exactAlarmAccess ->
                assertTrue(repository.triggered)
                assertEquals(reminder, deliveredReminder)
                assertTrue(exactAlarmAccess)
                AlertPresentation.NORMAL_NOTIFICATION
            },
            clock = clock,
        )

        val result = coordinator.onTriggered(reminder.id)

        assertEquals(
            AlertDeliveryResult.Delivered(AlertPresentation.NORMAL_NOTIFICATION),
            result,
        )
        assertEquals(now, repository.triggeredAt)
    }

    @Test
    fun `snooze persists lifecycle before scheduling stable reminder id`() = runBlocking {
        val repository = RecordingRepository(reminder)
        var scheduled: Pair<ReminderId, Instant>? = null
        val handler = RepositoryAlertActionHandler(
            repository = repository,
            snoozeScheduler = AlertSnoozeScheduler { reminderId, at ->
                assertTrue(repository.snoozed)
                scheduled = reminderId to at
            },
            clock = clock,
        )

        handler.handle(
            AlertActionEvent(
                reminderId = reminder.id,
                action = AlertAction.SNOOZE,
                snoozeDuration = Duration.ofMinutes(15),
            ),
        )

        assertEquals(reminder.id to now.plusSeconds(15 * 60), scheduled)
    }

    @Test
    fun `dismiss uses repository lifecycle method`() = runBlocking {
        val repository = RecordingRepository(reminder)
        val handler = RepositoryAlertActionHandler(
            repository = repository,
            snoozeScheduler = AlertSnoozeScheduler { _, _ -> error("Must not schedule") },
            clock = clock,
        )

        handler.handle(AlertActionEvent(reminder.id, AlertAction.DISMISS))

        assertTrue(repository.dismissed)
        assertEquals(now, repository.dismissedAt)
    }

    @Test
    fun `done completes at the current time without scheduling snooze`() = runBlocking {
        val repository = RecordingRepository(reminder)
        val handler = RepositoryAlertActionHandler(
            repository = repository,
            snoozeScheduler = AlertSnoozeScheduler { _, _ -> error("Must not schedule") },
            clock = clock,
        )

        handler.handle(AlertActionEvent(reminder.id, AlertAction.DONE))

        assertTrue(repository.completed)
        assertEquals(now, repository.completedAt)
    }
}

private class RecordingRepository(
    private val reminder: Reminder,
) : ReminderRepository {
    var triggered = false
        private set
    var triggeredAt: Instant? = null
        private set
    var snoozed = false
        private set
    var dismissed = false
        private set
    var dismissedAt: Instant? = null
        private set
    var completed = false
        private set
    var completedAt: Instant? = null
        private set

    override fun observeAll(): Flow<List<Reminder>> = flowOf(listOf(reminder))

    override fun observe(id: ReminderId): Flow<Reminder?> = flowOf(getMatching(id))

    override suspend fun get(id: ReminderId): Reminder? = getMatching(id)

    override suspend fun getPending(): List<Reminder> = listOf(reminder)

    override suspend fun save(reminder: Reminder) = Unit

    override suspend fun delete(id: ReminderId) = Unit

    override suspend fun setEnabled(id: ReminderId, enabled: Boolean, changedAt: Instant) = Unit

    override suspend fun recordTriggered(id: ReminderId, triggeredAt: Instant) {
        require(id == reminder.id)
        triggered = true
        this.triggeredAt = triggeredAt
    }

    override suspend fun snooze(id: ReminderId, until: Instant, changedAt: Instant) {
        require(id == reminder.id)
        require(until.isAfter(changedAt))
        snoozed = true
    }

    override suspend fun dismiss(id: ReminderId, changedAt: Instant) {
        require(id == reminder.id)
        dismissed = true
        dismissedAt = changedAt
    }

    override suspend fun complete(id: ReminderId, changedAt: Instant) {
        require(id == reminder.id)
        completed = true
        completedAt = changedAt
    }

    private fun getMatching(id: ReminderId): Reminder? = reminder.takeIf { it.id == id }
}
