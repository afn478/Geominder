package com.afn478.geominder.alarm

import android.app.AlarmManager
import android.content.Context
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.TriggerId
import java.time.Clock
import java.time.Instant

sealed interface AlarmScheduleResult {
    data class Scheduled(
        val plan: AlarmPlan,
        /** Past deadlines are scheduled for now so AlarmManager delivers them promptly. */
        val scheduledAt: Instant,
        val scheduleMode: AlarmScheduleMode = AlarmScheduleMode.EXACT,
        val deliveryMode: AlarmDeliveryMode = AlarmDeliveryMode.FULL_SCREEN_ELIGIBLE,
        val fallbackReason: AlarmFallbackReason? = null,
    ) : AlarmScheduleResult

    data class NotApplicable(val reason: NoAlarmReason) : AlarmScheduleResult

    /** Kept for source compatibility; the scheduler now returns a scheduled inexact fallback. */
    @Deprecated("Exact-alarm denial now produces Scheduled with an inexact fallback mode")
    data class ExactAlarmAccessDenied(
        val permissionState: ExactAlarmPermissionState,
    ) : AlarmScheduleResult
}

enum class AlarmScheduleMode {
    EXACT,
    INEXACT_ALLOW_WHILE_IDLE,
}

enum class AlarmFallbackReason {
    EXACT_ALARM_ACCESS_DENIED,
    EXACT_SCHEDULING_SECURITY_EXCEPTION,
}

sealed interface AlarmSchedulingDecision {
    val deliveryMode: AlarmDeliveryMode

    data class Exact(
        override val deliveryMode: AlarmDeliveryMode,
    ) : AlarmSchedulingDecision

    data class InexactFallback(
        val reason: AlarmFallbackReason,
        override val deliveryMode: AlarmDeliveryMode = AlarmDeliveryMode.PLAIN_NOTIFICATION,
    ) : AlarmSchedulingDecision
}

/** Pure policy shared by scheduling and JVM tests. */
object AlarmSchedulingPolicy {
    fun forCapabilities(capabilities: AlarmDeliveryCapabilities): AlarmSchedulingDecision =
        if (!capabilities.canScheduleExactAlarm) {
            AlarmSchedulingDecision.InexactFallback(
                reason = AlarmFallbackReason.EXACT_ALARM_ACCESS_DENIED,
            )
        } else {
            AlarmSchedulingDecision.Exact(
                // Full-screen access is rechecked when the alert is built. The alarm event
                // only needs to record that exact scheduling was available at scheduling time.
                deliveryMode = AlarmDeliveryMode.FULL_SCREEN_ELIGIBLE,
            )
        }

    fun afterExactSchedulingSecurityException(): AlarmSchedulingDecision.InexactFallback =
        AlarmSchedulingDecision.InexactFallback(
            reason = AlarmFallbackReason.EXACT_SCHEDULING_SECURITY_EXCEPTION,
        )
}

interface ExactAlarmScheduler {
    fun schedule(reminder: Reminder): AlarmScheduleResult

    fun scheduleGatedCheck(
        reminderId: ReminderId,
        triggerId: TriggerId,
        checkAt: Instant,
    ): AlarmScheduleResult

    fun scheduleSnoozeCheck(
        reminderId: ReminderId,
        checkAt: Instant,
    ): AlarmScheduleResult

    fun cancel(reminder: Reminder)

    fun cancelTime(reminderId: ReminderId, triggerId: TriggerId)

    fun cancelGatedCheck(reminderId: ReminderId, triggerId: TriggerId)

    fun cancelSnooze(reminderId: ReminderId)
}

/** AlarmManager-backed one-shot alarms, exact when access permits. It never starts a service. */
class AndroidExactAlarmScheduler(
    context: Context,
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java),
    private val permissionController: AlarmPermissionController = AlarmPermissionController(context),
    private val clock: Clock = Clock.systemUTC(),
) : ExactAlarmScheduler {
    private val intentFactory = AlarmIntentFactory(context.applicationContext)

    override fun schedule(reminder: Reminder): AlarmScheduleResult {
        val decision = AlarmPlanner.forReminder(reminder)
        if (decision is ReminderAlarmPlan.None) {
            if (decision.reason == NoAlarmReason.NOT_PENDING) {
                cancel(reminder)
            }
            return AlarmScheduleResult.NotApplicable(decision.reason)
        }

        val plan = (decision as ReminderAlarmPlan.Schedule).plan
        when (plan.identity.kind) {
            AlarmKind.TIME -> cancelSnooze(reminder.id)
            AlarmKind.SNOOZE -> reminder.timeTrigger?.let {
                cancelTime(reminder.id, it.id)
            }
            AlarmKind.GATED_CHECK -> Unit
        }
        return schedule(plan)
    }

    override fun scheduleGatedCheck(
        reminderId: ReminderId,
        triggerId: TriggerId,
        checkAt: Instant,
    ): AlarmScheduleResult = schedule(
        AlarmPlanner.gatedCheck(reminderId, triggerId, checkAt),
    )

    override fun scheduleSnoozeCheck(
        reminderId: ReminderId,
        checkAt: Instant,
    ): AlarmScheduleResult = schedule(
        AlarmPlanner.snoozeCheck(reminderId, checkAt),
    )

    override fun cancel(reminder: Reminder) {
        reminder.timeTrigger?.let { cancelTime(reminder.id, it.id) }
        reminder.geoTrigger?.let { cancelGatedCheck(reminder.id, it.id) }
        cancelSnooze(reminder.id)
    }

    override fun cancelTime(reminderId: ReminderId, triggerId: TriggerId) {
        cancel(AlarmIdentity(AlarmKind.TIME, reminderId, triggerId))
    }

    override fun cancelGatedCheck(reminderId: ReminderId, triggerId: TriggerId) {
        cancel(AlarmIdentity(AlarmKind.GATED_CHECK, reminderId, triggerId))
    }

    override fun cancelSnooze(reminderId: ReminderId) {
        cancel(AlarmIdentity(AlarmKind.SNOOZE, reminderId))
    }

    private fun schedule(plan: AlarmPlan): AlarmScheduleResult {
        val scheduledAt = maxOf(plan.triggerAt, clock.instant())
        return when (val decision = AlarmSchedulingPolicy.forCapabilities(
            permissionController.capabilities(),
        )) {
            is AlarmSchedulingDecision.Exact -> scheduleExact(plan, scheduledAt, decision)
            is AlarmSchedulingDecision.InexactFallback ->
                scheduleInexactFallback(plan, scheduledAt, decision)
        }
    }

    private fun scheduleExact(
        plan: AlarmPlan,
        scheduledAt: Instant,
        decision: AlarmSchedulingDecision.Exact,
    ): AlarmScheduleResult {
        val operation = checkNotNull(
            intentFactory.pendingIntent(
                identity = plan.identity,
                deliveryMode = decision.deliveryMode,
            ),
        )
        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                scheduledAt.toEpochMilli(),
                operation,
            )
            AlarmScheduleResult.Scheduled(
                plan = plan,
                scheduledAt = scheduledAt,
                scheduleMode = AlarmScheduleMode.EXACT,
                deliveryMode = decision.deliveryMode,
            )
        } catch (_: SecurityException) {
            scheduleInexactFallback(
                plan = plan,
                scheduledAt = scheduledAt,
                decision = AlarmSchedulingPolicy.afterExactSchedulingSecurityException(),
            )
        }
    }

    private fun scheduleInexactFallback(
        plan: AlarmPlan,
        scheduledAt: Instant,
        decision: AlarmSchedulingDecision.InexactFallback,
    ): AlarmScheduleResult {
        // FLAG_UPDATE_CURRENT replaces any full-screen-eligible extras from a failed exact attempt
        // without changing the stable PendingIntent identity used by cancellation.
        val operation = checkNotNull(
            intentFactory.pendingIntent(
                identity = plan.identity,
                deliveryMode = decision.deliveryMode,
            ),
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            scheduledAt.toEpochMilli(),
            operation,
        )
        return AlarmScheduleResult.Scheduled(
            plan = plan,
            scheduledAt = scheduledAt,
            scheduleMode = AlarmScheduleMode.INEXACT_ALLOW_WHILE_IDLE,
            deliveryMode = decision.deliveryMode,
            fallbackReason = decision.reason,
        )
    }

    private fun cancel(identity: AlarmIdentity) {
        val operation = intentFactory.pendingIntent(identity, create = false) ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }
}
