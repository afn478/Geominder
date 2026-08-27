package com.afn478.geominder.alarm

import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.ReminderStatus
import com.afn478.geominder.domain.model.TimeTrigger
import com.afn478.geominder.domain.model.TriggerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AlarmPlannerTest {
    @Test
    fun `snoozedUntil takes precedence over original exact time`() {
        val exactAt = Instant.parse("2026-08-25T08:00:00Z")
        val snoozedUntil = Instant.parse("2026-08-25T08:15:00Z")
        val reminder = reminder(
            status = ReminderStatus.SNOOZED,
            exactAt = exactAt,
            snoozedUntil = snoozedUntil,
        )

        val result = AlarmPlanner.forReminder(reminder)

        assertTrue(result is ReminderAlarmPlan.Schedule)
        val plan = (result as ReminderAlarmPlan.Schedule).plan
        assertEquals(snoozedUntil, plan.triggerAt)
        assertEquals(AlarmKind.SNOOZE, plan.identity.kind)
        assertEquals(null, plan.identity.triggerId)
    }

    @Test
    fun `pending time reminder uses exactAt`() {
        val exactAt = Instant.parse("2026-08-25T08:00:00Z")
        val reminder = reminder(exactAt = exactAt)

        val plan = (AlarmPlanner.forReminder(reminder) as ReminderAlarmPlan.Schedule).plan

        assertEquals(exactAt, plan.triggerAt)
        assertEquals(AlarmKind.TIME, plan.identity.kind)
        assertEquals(reminder.timeTrigger?.id, plan.identity.triggerId)
    }

    @Test
    fun `past pending time reminder is not scheduled`() {
        val now = Instant.parse("2026-08-25T09:00:00Z")
        val reminder = reminder(
            exactAt = Instant.parse("2026-08-25T08:00:00Z"),
        )

        assertEquals(
            ReminderAlarmPlan.None(NoAlarmReason.TIME_TRIGGER_EXPIRED),
            AlarmPlanner.forReminder(reminder, now),
        )
    }

    @Test
    fun `future snooze remains schedulable even when original time has elapsed`() {
        val snoozedUntil = Instant.parse("2026-08-25T09:30:00Z")
        val reminder = reminder(
            status = ReminderStatus.SNOOZED,
            exactAt = Instant.parse("2026-08-25T08:00:00Z"),
            snoozedUntil = snoozedUntil,
        )

        val result = AlarmPlanner.forReminder(
            reminder,
            Instant.parse("2026-08-25T09:00:00Z"),
        )

        assertEquals(
            ReminderAlarmPlan.Schedule(
                AlarmPlan(
                    identity = AlarmIdentity(AlarmKind.SNOOZE, reminder.id),
                    triggerAt = snoozedUntil,
                ),
            ),
            result,
        )
    }

    @Test
    fun `disabled reminder is not scheduled`() {
        val reminder = reminder(
            enabled = false,
            exactAt = Instant.parse("2026-08-25T08:00:00Z"),
        )

        assertEquals(
            ReminderAlarmPlan.None(NoAlarmReason.NOT_PENDING),
            AlarmPlanner.forReminder(reminder),
        )
    }

    @Test
    fun `identity request code and action are stable and kind-specific`() {
        val reminderId = ReminderId("reminder-17")
        val triggerId = TriggerId("trigger-4")
        val first = AlarmIdentity(AlarmKind.TIME, reminderId, triggerId)
        val recreated = AlarmIdentity(AlarmKind.TIME, reminderId, triggerId)
        val gated = AlarmIdentity(AlarmKind.GATED_CHECK, reminderId, triggerId)
        val snooze = AlarmIdentity(AlarmKind.SNOOZE, reminderId)

        assertEquals(first.requestCode, recreated.requestCode)
        assertEquals(AlarmContract.ACTION_TIME_REMINDER, first.kind.action)
        assertEquals(AlarmContract.ACTION_GATED_CHECK, gated.kind.action)
        assertEquals(AlarmContract.ACTION_SNOOZE, snooze.kind.action)
        assertNotEquals(first.requestCode, gated.requestCode)
        assertNotEquals(first.requestCode, snooze.requestCode)
    }

    private fun reminder(
        enabled: Boolean = true,
        status: ReminderStatus = ReminderStatus.PENDING,
        exactAt: Instant,
        snoozedUntil: Instant? = null,
    ): Reminder {
        val createdAt = Instant.parse("2026-08-24T10:00:00Z")
        return Reminder(
            id = ReminderId("reminder-17"),
            sourceText = "Call Alex tomorrow",
            title = "Call Alex",
            text = "",
            enabled = enabled,
            status = status,
            timeTrigger = TimeTrigger(
                id = TriggerId("trigger-4"),
                exactAt = exactAt,
            ),
            createdAt = createdAt,
            snoozedUntil = snoozedUntil,
        )
    }
}
