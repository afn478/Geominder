package com.geominder.reminder.boot

import com.geominder.reminder.alarm.AlarmPlan
import com.geominder.reminder.alarm.AlarmPlanner
import com.geominder.reminder.alarm.AlarmScheduleResult
import com.geominder.reminder.alarm.ExactAlarmScheduler
import com.geominder.reminder.alarm.ReminderAlarmPlan
import com.geominder.reminder.domain.model.GeoTrigger
import com.geominder.reminder.domain.model.Reminder
import com.geominder.reminder.domain.model.ReminderId
import com.geominder.reminder.domain.model.ReminderStatus
import com.geominder.reminder.domain.model.TimeTrigger
import com.geominder.reminder.domain.model.TriggerId
import com.geominder.reminder.domain.repository.ReminderRepository
import com.geominder.reminder.geofence.GeofenceOperationCallback
import com.geominder.reminder.geofence.GeofenceOperationResult
import com.geominder.reminder.geofence.GeofenceRegistrar
import com.geominder.reminder.geofence.GeofenceSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BootResyncCoordinatorTest {
    @Test
    fun `restores every pending alarm and geofence including snoozes`() = runBlocking {
        val timeOnly = reminder(id = "time", withTime = true)
        val geoOnly = reminder(id = "geo", withGeo = true)
        val snoozeAt = Instant.parse("2026-08-25T11:30:00Z")
        val snoozedGeo = reminder(
            id = "snoozed-geo",
            status = ReminderStatus.SNOOZED,
            snoozedUntil = snoozeAt,
            withGeo = true,
        )
        val scheduler = RecordingAlarmScheduler()
        val registrar = RecordingGeofenceRegistrar()

        val report = coordinator(
            reminders = listOf(timeOnly, geoOnly, snoozedGeo),
            scheduler = scheduler,
            registrar = registrar,
        ).resynchronize()

        assertTrue(report.isFullySuccessful)
        assertEquals(2, report.scheduledAlarmCount)
        assertEquals(2, report.registeredGeofenceCount)
        assertEquals(listOf(timeOnly.id, snoozedGeo.id), scheduler.scheduled.map(Reminder::id))
        assertEquals(snoozeAt, scheduler.plans.getValue(snoozedGeo.id).triggerAt)
        assertEquals(listOf(geoOnly.id, snoozedGeo.id), registrar.specs.map(GeofenceSpec::reminderId))
    }

    @Test
    fun `combined reminder restores both trigger types and preserves activeFrom`() = runBlocking {
        val activeFrom = Instant.parse("2026-08-25T16:00:00Z")
        val combined = reminder(
            id = "combined",
            withTime = true,
            withGeo = true,
            activeFrom = activeFrom,
        )
        val scheduler = RecordingAlarmScheduler()
        val registrar = RecordingGeofenceRegistrar()

        val report = coordinator(
            reminders = listOf(combined),
            scheduler = scheduler,
            registrar = registrar,
        ).resynchronize()

        assertEquals(1, report.scheduledAlarmCount)
        assertEquals(1, report.registeredGeofenceCount)
        assertEquals(combined.id, scheduler.scheduled.single().id)
        assertEquals(activeFrom, registrar.specs.single().activeFrom)
    }

    @Test
    fun `defensively ignores inactive reminders returned by repository`() = runBlocking {
        val active = reminder(id = "active", withTime = true, withGeo = true)
        val disabled = reminder(id = "disabled", enabled = false, withTime = true, withGeo = true)
        val dismissedAt = Instant.parse("2026-08-24T12:00:00Z")
        val dismissed = reminder(
            id = "dismissed",
            enabled = false,
            status = ReminderStatus.DISMISSED,
            dismissedAt = dismissedAt,
            withTime = true,
            withGeo = true,
        )
        val scheduler = RecordingAlarmScheduler()
        val registrar = RecordingGeofenceRegistrar()

        val report = coordinator(
            reminders = listOf(active, disabled, dismissed),
            scheduler = scheduler,
            registrar = registrar,
        ).resynchronize()

        assertEquals(3, report.loadedReminderCount)
        assertEquals(2, report.ignoredInactiveCount)
        assertEquals(listOf(active.id), scheduler.scheduled.map(Reminder::id))
        assertEquals(listOf(active.id), registrar.specs.map(GeofenceSpec::reminderId))
    }

    @Test
    fun `partial failures are aggregated while later registrations continue`() = runBlocking {
        val alarmFailure = reminder(id = "alarm-failure", withTime = true)
        val alarmSuccess = reminder(id = "alarm-success", withTime = true)
        val geoFailure = reminder(id = "geo-failure", withGeo = true)
        val geoSuccess = reminder(id = "geo-success", withGeo = true)
        val scheduler = RecordingAlarmScheduler(failingReminderId = alarmFailure.id)
        val registrar = RecordingGeofenceRegistrar(failingReminderId = geoFailure.id)

        val report = coordinator(
            reminders = listOf(alarmFailure, alarmSuccess, geoFailure, geoSuccess),
            scheduler = scheduler,
            registrar = registrar,
        ).resynchronize()

        assertFalse(report.isFullySuccessful)
        assertEquals(1, report.scheduledAlarmCount)
        assertEquals(1, report.registeredGeofenceCount)
        assertEquals(listOf(alarmFailure.id, alarmSuccess.id), scheduler.scheduled.map(Reminder::id))
        assertEquals(listOf(geoFailure.id, geoSuccess.id), registrar.specs.map(GeofenceSpec::reminderId))
        assertTrue(report.failures.any { it is BootResyncFailure.AlarmException })
        assertTrue(report.failures.any { it is BootResyncFailure.GeofenceRejected })
    }

    @Test
    fun `repository failure becomes an aggregate failure without invoking registrars`() = runBlocking {
        val scheduler = RecordingAlarmScheduler()
        val registrar = RecordingGeofenceRegistrar()
        val coordinator = BootResyncCoordinator(
            repository = FakeRepository(error = IllegalStateException("database unavailable")),
            alarmScheduler = scheduler,
            geofenceRegistrar = registrar,
        )

        val report = coordinator.resynchronize()

        assertTrue(report.failures.single() is BootResyncFailure.RepositoryLoad)
        assertTrue(scheduler.scheduled.isEmpty())
        assertTrue(registrar.specs.isEmpty())
    }

    @Test
    fun `missing geofence callback times out and returns a completion report`() = runBlocking {
        val reminder = reminder(id = "no-callback", withGeo = true)
        val coordinator = BootResyncCoordinator(
            repository = FakeRepository(listOf(reminder)),
            alarmScheduler = RecordingAlarmScheduler(),
            geofenceRegistrar = object : GeofenceRegistrar {
                override fun register(spec: GeofenceSpec, callback: GeofenceOperationCallback) = Unit

                override fun cancel(
                    triggerId: TriggerId,
                    callback: GeofenceOperationCallback,
                ) = Unit
            },
            geofenceTimeoutMillis = 25L,
        )

        val report = coordinator.resynchronize()

        assertEquals(0, report.registeredGeofenceCount)
        assertTrue(report.failures.single() is BootResyncFailure.GeofenceTimedOut)
    }

    private fun coordinator(
        reminders: List<Reminder>,
        scheduler: RecordingAlarmScheduler,
        registrar: RecordingGeofenceRegistrar,
    ) = BootResyncCoordinator(
        repository = FakeRepository(reminders),
        alarmScheduler = scheduler,
        geofenceRegistrar = registrar,
        geofenceTimeoutMillis = 100L,
    )

    private fun reminder(
        id: String,
        enabled: Boolean = true,
        status: ReminderStatus = ReminderStatus.PENDING,
        dismissedAt: Instant? = null,
        snoozedUntil: Instant? = null,
        withTime: Boolean = false,
        withGeo: Boolean = false,
        activeFrom: Instant? = null,
    ): Reminder {
        val createdAt = Instant.parse("2026-08-24T10:00:00Z")
        return Reminder(
            id = ReminderId(id),
            sourceText = "Reminder $id",
            title = "Reminder $id",
            text = "",
            enabled = enabled,
            status = status,
            timeTrigger = if (withTime) {
                TimeTrigger(
                    id = TriggerId("$id-time"),
                    exactAt = Instant.parse("2026-08-25T10:00:00Z"),
                )
            } else {
                null
            },
            geoTrigger = if (withGeo) {
                GeoTrigger(
                    id = TriggerId("$id-geo"),
                    latitude = 40.7128,
                    longitude = -74.0060,
                    radiusMeters = 125.0,
                    activeFrom = activeFrom,
                )
            } else {
                null
            },
            createdAt = createdAt,
            snoozedUntil = snoozedUntil,
            dismissedAt = dismissedAt,
        )
    }
}

private class FakeRepository(
    private val reminders: List<Reminder> = emptyList(),
    private val error: Throwable? = null,
) : ReminderRepository {
    override fun observeAll(): Flow<List<Reminder>> = emptyFlow()
    override fun observe(id: ReminderId): Flow<Reminder?> = emptyFlow()
    override suspend fun get(id: ReminderId): Reminder? = reminders.firstOrNull { it.id == id }
    override suspend fun getPending(): List<Reminder> = error?.let { throw it } ?: reminders
    override suspend fun save(reminder: Reminder) = Unit
    override suspend fun delete(id: ReminderId) = Unit
    override suspend fun setEnabled(id: ReminderId, enabled: Boolean, changedAt: Instant) = Unit
    override suspend fun recordTriggered(id: ReminderId, triggeredAt: Instant) = Unit
    override suspend fun snooze(id: ReminderId, until: Instant, changedAt: Instant) = Unit
    override suspend fun dismiss(id: ReminderId, changedAt: Instant) = Unit
    override suspend fun complete(id: ReminderId, changedAt: Instant) = Unit
}

private class RecordingAlarmScheduler(
    private val failingReminderId: ReminderId? = null,
) : ExactAlarmScheduler {
    val scheduled = mutableListOf<Reminder>()
    val plans = mutableMapOf<ReminderId, AlarmPlan>()

    override fun schedule(reminder: Reminder): AlarmScheduleResult {
        scheduled += reminder
        if (reminder.id == failingReminderId) error("alarm failure")
        val plan = (AlarmPlanner.forReminder(reminder) as ReminderAlarmPlan.Schedule).plan
        plans[reminder.id] = plan
        return AlarmScheduleResult.Scheduled(plan = plan, scheduledAt = plan.triggerAt)
    }

    override fun scheduleGatedCheck(
        reminderId: ReminderId,
        triggerId: TriggerId,
        checkAt: Instant,
    ): AlarmScheduleResult = error("Not used")

    override fun scheduleSnoozeCheck(
        reminderId: ReminderId,
        checkAt: Instant,
    ): AlarmScheduleResult = error("Not used")

    override fun cancel(reminder: Reminder) = Unit
    override fun cancelTime(reminderId: ReminderId, triggerId: TriggerId) = Unit
    override fun cancelGatedCheck(reminderId: ReminderId, triggerId: TriggerId) = Unit
    override fun cancelSnooze(reminderId: ReminderId) = Unit
}

private class RecordingGeofenceRegistrar(
    private val failingReminderId: ReminderId? = null,
) : GeofenceRegistrar {
    val specs = mutableListOf<GeofenceSpec>()

    override fun register(spec: GeofenceSpec, callback: GeofenceOperationCallback) {
        specs += spec
        val result = if (spec.reminderId == failingReminderId) {
            GeofenceOperationResult.Failed(IllegalStateException("geofence failure"))
        } else {
            GeofenceOperationResult.Success
        }
        callback.onComplete(result)
    }

    override fun cancel(triggerId: TriggerId, callback: GeofenceOperationCallback) =
        callback.onComplete(GeofenceOperationResult.Success)
}
