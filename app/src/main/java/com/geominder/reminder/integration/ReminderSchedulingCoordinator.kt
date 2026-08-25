package com.geominder.reminder.integration

import com.geominder.reminder.alarm.AlarmScheduleResult
import com.geominder.reminder.alarm.ExactAlarmScheduler
import com.geominder.reminder.backup.PostImportScheduler
import com.geominder.reminder.domain.model.Reminder
import com.geominder.reminder.geofence.GeofenceOperationCallback
import com.geominder.reminder.geofence.GeofenceOperationResult
import com.geominder.reminder.geofence.GeofenceRegistrar
import com.geominder.reminder.geofence.GeofenceSpec
import com.geominder.reminder.ui.add.ReminderPostSaveActions
import com.geominder.reminder.ui.list.ReminderScheduleCommand
import com.geominder.reminder.ui.list.ReminderScheduleCommandHandler
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
        if (result !is AlarmScheduleResult.Scheduled) {
            throw ReminderSchedulingException("Time trigger was not scheduled: $result")
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
