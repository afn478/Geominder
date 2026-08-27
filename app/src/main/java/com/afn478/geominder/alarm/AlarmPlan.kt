package com.afn478.geominder.alarm

import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.TriggerId
import java.time.Instant

data class AlarmPlan(
    val identity: AlarmIdentity,
    val triggerAt: Instant,
)

enum class NoAlarmReason {
    NOT_PENDING,
    NO_TIME_TRIGGER,
    TIME_TRIGGER_EXPIRED,
}

sealed interface ReminderAlarmPlan {
    data class Schedule(val plan: AlarmPlan) : ReminderAlarmPlan

    data class None(val reason: NoAlarmReason) : ReminderAlarmPlan
}

/** Pure scheduling decisions, kept independent of the Android framework for JVM tests. */
object AlarmPlanner {
    fun forReminder(reminder: Reminder): ReminderAlarmPlan {
        if (!reminder.isPending) {
            return ReminderAlarmPlan.None(NoAlarmReason.NOT_PENDING)
        }

        reminder.snoozedUntil?.let { snoozedUntil ->
            return ReminderAlarmPlan.Schedule(
                AlarmPlan(
                    identity = AlarmIdentity(
                        kind = AlarmKind.SNOOZE,
                        reminderId = reminder.id,
                    ),
                    triggerAt = snoozedUntil,
                ),
            )
        }

        val timeTrigger = reminder.timeTrigger
            ?: return ReminderAlarmPlan.None(NoAlarmReason.NO_TIME_TRIGGER)
        return ReminderAlarmPlan.Schedule(
            AlarmPlan(
                identity = AlarmIdentity(
                    kind = AlarmKind.TIME,
                    reminderId = reminder.id,
                    triggerId = timeTrigger.id,
                ),
                triggerAt = timeTrigger.exactAt,
            ),
        )
    }

    /** Plans a reminder for registration, omitting a one-shot time trigger that has elapsed. */
    fun forReminder(reminder: Reminder, now: Instant): ReminderAlarmPlan {
        val decision = forReminder(reminder)
        if (
            decision is ReminderAlarmPlan.Schedule &&
            decision.plan.identity.kind == AlarmKind.TIME &&
            decision.plan.triggerAt.isBefore(now)
        ) {
            return ReminderAlarmPlan.None(NoAlarmReason.TIME_TRIGGER_EXPIRED)
        }
        return decision
    }

    fun gatedCheck(
        reminderId: ReminderId,
        triggerId: TriggerId,
        checkAt: Instant,
    ): AlarmPlan = AlarmPlan(
        identity = AlarmIdentity(
            kind = AlarmKind.GATED_CHECK,
            reminderId = reminderId,
            triggerId = triggerId,
        ),
        triggerAt = checkAt,
    )

    fun snoozeCheck(
        reminderId: ReminderId,
        checkAt: Instant,
    ): AlarmPlan = AlarmPlan(
        identity = AlarmIdentity(
            kind = AlarmKind.SNOOZE,
            reminderId = reminderId,
        ),
        triggerAt = checkAt,
    )
}
