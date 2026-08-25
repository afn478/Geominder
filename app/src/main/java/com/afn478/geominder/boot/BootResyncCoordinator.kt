package com.afn478.geominder.boot

import com.afn478.geominder.alarm.AlarmScheduleResult
import com.afn478.geominder.alarm.ExactAlarmScheduler
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.TriggerId
import com.afn478.geominder.domain.repository.ReminderRepository
import com.afn478.geominder.geofence.GeofenceOperationCallback
import com.afn478.geominder.geofence.GeofenceOperationResult
import com.afn478.geominder.geofence.GeofenceRegistrar
import com.afn478.geominder.geofence.GeofenceSpec
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

data class BootResyncReport(
    val loadedReminderCount: Int,
    val ignoredInactiveCount: Int,
    val scheduledAlarmCount: Int,
    val registeredGeofenceCount: Int,
    val failures: List<BootResyncFailure>,
) {
    val isFullySuccessful: Boolean
        get() = failures.isEmpty()

    companion object {
        fun fatal(cause: Throwable): BootResyncReport = BootResyncReport(
            loadedReminderCount = 0,
            ignoredInactiveCount = 0,
            scheduledAlarmCount = 0,
            registeredGeofenceCount = 0,
            failures = listOf(BootResyncFailure.Unexpected(cause)),
        )
    }
}

sealed interface BootResyncFailure {
    data class RepositoryLoad(val cause: Throwable) : BootResyncFailure

    data class AlarmException(
        val reminderId: ReminderId,
        val cause: Throwable,
    ) : BootResyncFailure

    data class AlarmRejected(
        val reminderId: ReminderId,
        val result: AlarmScheduleResult,
    ) : BootResyncFailure

    data class GeofenceRejected(
        val reminderId: ReminderId,
        val triggerId: TriggerId,
        val result: GeofenceOperationResult,
    ) : BootResyncFailure

    data class GeofenceTimedOut(
        val reminderId: ReminderId,
        val triggerId: TriggerId,
    ) : BootResyncFailure

    data class Unexpected(val cause: Throwable) : BootResyncFailure
}

/**
 * Recreates OS registrations from durable state after the OS has cleared them.
 *
 * Alarm and geofence work are deliberately independent. A combined reminder therefore restores
 * both registrations, and a failure in either branch cannot prevent the other branch from running.
 */
class BootResyncCoordinator(
    private val repository: ReminderRepository,
    private val alarmScheduler: ExactAlarmScheduler,
    private val geofenceRegistrar: GeofenceRegistrar,
    private val geofenceTimeoutMillis: Long = DEFAULT_GEOFENCE_TIMEOUT_MILLIS,
) {
    init {
        require(geofenceTimeoutMillis > 0L) { "Geofence timeout must be positive" }
    }

    suspend fun resynchronize(): BootResyncReport {
        val reminders = try {
            repository.getPending()
        } catch (error: Throwable) {
            return BootResyncReport(
                loadedReminderCount = 0,
                ignoredInactiveCount = 0,
                scheduledAlarmCount = 0,
                registeredGeofenceCount = 0,
                failures = listOf(BootResyncFailure.RepositoryLoad(error)),
            )
        }

        val activeReminders = reminders.filter(Reminder::isPending)
        val alarmResults = restoreAlarms(activeReminders)
        val geofenceResults = restoreGeofences(activeReminders)

        return BootResyncReport(
            loadedReminderCount = reminders.size,
            ignoredInactiveCount = reminders.size - activeReminders.size,
            scheduledAlarmCount = alarmResults.count { it == null },
            registeredGeofenceCount = geofenceResults.count { it == null },
            failures = alarmResults.filterNotNull() + geofenceResults.filterNotNull(),
        )
    }

    private fun restoreAlarms(reminders: List<Reminder>): List<BootResyncFailure?> = reminders
        .filter { reminder -> reminder.needsAlarmRegistration() }
        .map { reminder ->
            try {
                when (val result = alarmScheduler.schedule(reminder)) {
                    is AlarmScheduleResult.Scheduled -> null
                    else -> BootResyncFailure.AlarmRejected(reminder.id, result)
                }
            } catch (error: Throwable) {
                BootResyncFailure.AlarmException(reminder.id, error)
            }
        }

    private suspend fun restoreGeofences(
        reminders: List<Reminder>,
    ): List<BootResyncFailure?> = coroutineScope {
        reminders.mapNotNull { reminder ->
            reminder.geoTrigger?.let { trigger -> reminder to GeofenceSpec.from(reminder.id, trigger) }
        }.map { (reminder, spec) ->
            async {
                when (val result = awaitRegistration(spec)) {
                    GeofenceRegistrationOutcome.Success -> null
                    GeofenceRegistrationOutcome.TimedOut -> BootResyncFailure.GeofenceTimedOut(
                        reminderId = reminder.id,
                        triggerId = spec.triggerId,
                    )

                    is GeofenceRegistrationOutcome.Rejected ->
                        BootResyncFailure.GeofenceRejected(
                            reminderId = reminder.id,
                            triggerId = spec.triggerId,
                            result = result.result,
                        )
                }
            }
        }.awaitAll()
    }

    private suspend fun awaitRegistration(spec: GeofenceSpec): GeofenceRegistrationOutcome =
        withTimeoutOrNull(geofenceTimeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val completed = AtomicBoolean(false)
                continuation.invokeOnCancellation { completed.set(true) }

                try {
                    geofenceRegistrar.register(
                        spec,
                        GeofenceOperationCallback { result ->
                            if (completed.compareAndSet(false, true) && continuation.isActive) {
                                continuation.resume(
                                    when (result) {
                                        GeofenceOperationResult.Success ->
                                            GeofenceRegistrationOutcome.Success

                                        else -> GeofenceRegistrationOutcome.Rejected(result)
                                    },
                                )
                            }
                        },
                    )
                } catch (error: Throwable) {
                    if (completed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(
                            GeofenceRegistrationOutcome.Rejected(
                                GeofenceOperationResult.Failed(error),
                            ),
                        )
                    }
                }
            }
        } ?: GeofenceRegistrationOutcome.TimedOut

    private fun Reminder.needsAlarmRegistration(): Boolean =
        snoozedUntil != null || timeTrigger != null

    private sealed interface GeofenceRegistrationOutcome {
        data object Success : GeofenceRegistrationOutcome
        data object TimedOut : GeofenceRegistrationOutcome
        data class Rejected(val result: GeofenceOperationResult) : GeofenceRegistrationOutcome
    }

    private companion object {
        const val DEFAULT_GEOFENCE_TIMEOUT_MILLIS = 7_500L
    }
}
