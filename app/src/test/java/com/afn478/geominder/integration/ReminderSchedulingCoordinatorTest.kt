package com.afn478.geominder.integration

import com.afn478.geominder.alarm.AlarmScheduleResult
import com.afn478.geominder.alarm.AlarmScheduleMode
import com.afn478.geominder.alarm.ExactAlarmScheduler
import com.afn478.geominder.domain.model.GeoTrigger
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.TimeTrigger
import com.afn478.geominder.domain.model.TriggerId
import com.afn478.geominder.geofence.GeofenceOperationCallback
import com.afn478.geominder.geofence.GeofenceOperationResult
import com.afn478.geominder.geofence.GeofenceRegistrar
import com.afn478.geominder.geofence.GeofenceSpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class ReminderSchedulingCoordinatorTest {
    @Test
    fun scheduleCombinedReminderActivatesBothTriggerKinds() = runBlocking {
        val alarms = FakeExactAlarmScheduler()
        val geofences = FakeGeofenceRegistrar()
        val coordinator = ReminderSchedulingCoordinator(alarms, geofences)
        val reminder = combinedReminder()

        coordinator.schedule(reminder)

        assertEquals(listOf(reminder), alarms.scheduled)
        assertEquals(listOf(reminder.geoTrigger!!.id), geofences.registered)
    }

    @Test
    fun geofenceFailureIsReportedToCaller() {
        val coordinator = ReminderSchedulingCoordinator(
            FakeExactAlarmScheduler(),
            FakeGeofenceRegistrar(registerResult = GeofenceOperationResult.RadiusTooLarge),
        )

        val error = assertThrows(ReminderSchedulingException::class.java) {
            runBlocking { coordinator.schedule(combinedReminder()) }
        }

        check(error.message.orEmpty().contains("Location trigger was not registered"))
    }

    @Test
    fun cancelCombinedReminderRemovesBothTriggerKinds() = runBlocking {
        val alarms = FakeExactAlarmScheduler()
        val geofences = FakeGeofenceRegistrar()
        val coordinator = ReminderSchedulingCoordinator(alarms, geofences)
        val reminder = combinedReminder()

        coordinator.cancel(reminder)

        assertEquals(listOf(reminder), alarms.cancelled)
        assertEquals(listOf(reminder.geoTrigger!!.id), geofences.cancelled)
    }

    private fun combinedReminder(): Reminder = Reminder(
        id = ReminderId("combined"),
        sourceText = "Tomorrow near the station",
        title = "Station",
        text = "Station",
        timeTrigger = TimeTrigger(TriggerId("time"), Instant.parse("2030-01-02T08:00:00Z")),
        geoTrigger = GeoTrigger(
            id = TriggerId("geo"),
            latitude = 40.7128,
            longitude = -74.0060,
            radiusMeters = 100.0,
        ),
        createdAt = Instant.parse("2030-01-01T08:00:00Z"),
    )

    private class FakeExactAlarmScheduler : ExactAlarmScheduler {
        val scheduled = mutableListOf<Reminder>()
        val cancelled = mutableListOf<Reminder>()

        override fun schedule(reminder: Reminder): AlarmScheduleResult {
            scheduled += reminder
            val trigger = requireNotNull(reminder.timeTrigger)
            val plan = com.afn478.geominder.alarm.AlarmPlanner.forReminder(reminder)
                as com.afn478.geominder.alarm.ReminderAlarmPlan.Schedule
            return AlarmScheduleResult.Scheduled(
                plan = plan.plan,
                scheduledAt = trigger.exactAt,
                scheduleMode = AlarmScheduleMode.EXACT,
            )
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

        override fun cancel(reminder: Reminder) {
            cancelled += reminder
        }

        override fun cancelTime(reminderId: ReminderId, triggerId: TriggerId) = Unit

        override fun cancelGatedCheck(reminderId: ReminderId, triggerId: TriggerId) = Unit

        override fun cancelSnooze(reminderId: ReminderId) = Unit
    }

    private class FakeGeofenceRegistrar(
        private val registerResult: GeofenceOperationResult = GeofenceOperationResult.Success,
    ) : GeofenceRegistrar {
        val registered = mutableListOf<TriggerId>()
        val cancelled = mutableListOf<TriggerId>()

        override fun register(spec: GeofenceSpec, callback: GeofenceOperationCallback) {
            registered += spec.triggerId
            callback.onComplete(registerResult)
        }

        override fun cancel(triggerId: TriggerId, callback: GeofenceOperationCallback) {
            cancelled += triggerId
            callback.onComplete(GeofenceOperationResult.Success)
        }
    }
}
