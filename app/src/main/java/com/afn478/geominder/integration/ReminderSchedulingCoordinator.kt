package com.afn478.geominder.integration

import com.afn478.geominder.alarm.AlarmScheduleResult
import com.afn478.geominder.alarm.ExactAlarmScheduler
import com.afn478.geominder.alarm.NoAlarmReason
import com.afn478.geominder.backup.PostImportScheduler
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.geofence.GeofenceOperationCallback
import com.afn478.geominder.geofence.GeofenceOperationResult
import com.afn478.geominder.geofence.GeofenceRegistrar
import com.afn478.geominder.geofence.GeofenceSpec
import com.afn478.geominder.ui.add.ReminderPostSaveActions
import com.afn478.geominder.ui.list.ReminderScheduleCommand
import com.afn478.geominder.ui.list.ReminderScheduleCommandHandler
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/** Applies every OS registration represented by a reminder as one explicit application command. */
class ReminderSchedulingCoordinator(
    private val exactAlarmScheduler: ExactAlarmScheduler,
    private val geofenceRegistrar: GeofenceRegistrar,
) : ReminderPostSaveActions, ReminderScheduleCommandHandler, PostImportScheduler {
    override suspend fun scheduleTimeTrigger(reminder: Reminder) {
        val result = exactAlarmScheduler.schedule(reminder)
        when (result) {
            is AlarmScheduleResult.Scheduled -> Unit
            is AlarmScheduleResult.NotApplicable
                if result.reason == NoAlarmReason.TIME_TRIGGER_EXPIRED -> Unit
            else -> throw ReminderSchedulingException("Time trigger was not scheduled: $result")
        }
    }

    override suspend fun registerGeoTrigger(reminder: Reminder) {
        val trigger = reminder.geoTrigger
            ?: throw ReminderSchedulingException("Reminder has no location trigger")
        when (val result = awaitGeofenceOperation { callback ->
            geofenceRegistrar.register(GeofenceSpec.from(reminder.id, trigger), callback)
        }) {
            GeofenceOperationResult.Success -> Unit
            else -> throw ReminderSchedulingException("Location trigger was not registered: $result")
        }
    }

    override suspend fun schedule(reminder: Reminder) {
        if (!reminder.isPending) {
            cancel(reminder)
            return
        }
        val failures = buildList {
            if (reminder.timeTrigger != null || reminder.snoozedUntil != null) {
                runCatching { scheduleTimeTrigger(reminder) }.exceptionOrNull()?.let(::add)
            }
            if (reminder.geoTrigger != null) {
                runCatching { registerGeoTrigger(reminder) }.exceptionOrNull()?.let(::add)
            }
        }
        if (failures.isNotEmpty()) throw failures.asSchedulingException()
    }

    override suspend fun handle(command: ReminderScheduleCommand) {
        when (command) {
            is ReminderScheduleCommand.Register -> schedule(command.reminder)
            is ReminderScheduleCommand.Cancel -> cancel(command.reminder)
        }
    }

    suspend fun cancel(reminder: Reminder) {
        val failures = buildList {
            runCatching { exactAlarmScheduler.cancel(reminder) }.exceptionOrNull()?.let(::add)
            reminder.geoTrigger?.let { trigger ->
                runCatching {
                    val result = awaitGeofenceOperation { callback ->
                        geofenceRegistrar.cancel(trigger.id, callback)
                    }
                    if (result !is GeofenceOperationResult.Success) {
                        throw ReminderSchedulingException("Location trigger was not cancelled: $result")
                    }
                }.exceptionOrNull()?.let(::add)
            }
        }
        if (failures.isNotEmpty()) throw failures.asSchedulingException()
    }

    override suspend fun cancelReminder(reminder: Reminder) = cancel(reminder)

    private suspend fun awaitGeofenceOperation(
        operation: (GeofenceOperationCallback) -> Unit,
    ): GeofenceOperationResult = withTimeout(GEOFENCE_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            runCatching {
                operation(
                    GeofenceOperationCallback { result ->
                        if (continuation.isActive) continuation.resume(result)
                    },
                )
            }.onFailure { error ->
                if (continuation.isActive) {
                    continuation.resume(GeofenceOperationResult.Failed(error))
                }
            }
        }
    }

    private fun List<Throwable>.asSchedulingException(): ReminderSchedulingException =
        ReminderSchedulingException(
            message = joinToString(prefix = "Trigger registration failed: ") {
                it.message ?: it::class.java.simpleName
            },
            cause = first(),
        ).also { combined -> drop(1).forEach(combined::addSuppressed) }

    private companion object {
        const val GEOFENCE_TIMEOUT_MILLIS = 10_000L
    }
}

class ReminderSchedulingException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
