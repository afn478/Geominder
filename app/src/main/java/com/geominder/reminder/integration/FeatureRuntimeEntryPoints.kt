package com.geominder.reminder.integration

import android.util.Log
import com.geominder.reminder.alarm.AlarmDeliveryMode
import com.geominder.reminder.alarm.AlarmEvent
import com.geominder.reminder.alarm.AlarmEventHandler
import com.geominder.reminder.alarm.AlarmKind
import com.geominder.reminder.alarm.AlarmPermissionController
import com.geominder.reminder.alarm.ExactAlarmScheduler
import com.geominder.reminder.alert.AlertAction
import com.geominder.reminder.alert.AlertActionEvent
import com.geominder.reminder.alert.AlertActionHandler
import com.geominder.reminder.alert.AlertDeliveryCoordinator
import com.geominder.reminder.domain.model.ReminderId
import com.geominder.reminder.domain.model.TriggerId
import com.geominder.reminder.domain.repository.ReminderRepository
import com.geominder.reminder.geofence.GeoEnterCoordinator
import com.geominder.reminder.geofence.GeoEnterResult
import com.geominder.reminder.geofence.GeoFeatureEntryPoint
import com.geominder.reminder.geofence.GeoTriggerEmitter
import com.geominder.reminder.geofence.GeoVerificationScheduleResult
import com.geominder.reminder.geofence.GeoVerificationResult
import com.geominder.reminder.geofence.GeofenceOperationCallback
import com.geominder.reminder.geofence.GeofenceRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApplicationFeatureRuntime(
    private val repository: ReminderRepository,
    private val exactAlarmScheduler: ExactAlarmScheduler,
    private val geofenceRegistrar: GeofenceRegistrar,
    private val alarmPermissionController: AlarmPermissionController,
    private val alertCoordinator: AlertDeliveryCoordinator,
    geoEnterCoordinatorFactory: (GeoTriggerEmitter) -> GeoEnterCoordinator,
    private val clock: Clock = Clock.systemUTC(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AlarmEventHandler, GeoFeatureEntryPoint, AlertActionHandler {
    // Delivery is awaited from the coordinator result so goAsync() stays alive until the alert is
    // recorded and posted. The coordinator's emitter is intentionally inert at this boundary.
    private val geoEnterCoordinator = geoEnterCoordinatorFactory(GeoTriggerEmitter { })

    override suspend fun onAlarm(context: android.content.Context, event: AlarmEvent) {
        when (event.kind) {
            AlarmKind.TIME,
            AlarmKind.SNOOZE,
            -> deliver(
                reminderId = event.reminderId,
                fullScreenEligible = event.deliveryMode == AlarmDeliveryMode.FULL_SCREEN_ELIGIBLE,
            )
            AlarmKind.GATED_CHECK -> {
                val triggerId = event.triggerId ?: return
                awaitGeoVerification(event.reminderId, triggerId)
            }
        }
    }

    override fun onGeofenceEnter(
        triggerIds: Set<TriggerId>,
        occurredAt: Instant,
        completion: () -> Unit,
    ) {
        launchReceiverWork(completion) {
            val reminders = repository.getPending()
            val byTriggerId = reminders.mapNotNull { reminder ->
                reminder.geoTrigger?.let { trigger -> trigger.id to (reminder to trigger) }
            }.toMap()
            triggerIds.forEach { triggerId ->
                val (reminder, trigger) = byTriggerId[triggerId] ?: return@forEach
                when (val result = geoEnterCoordinator.onEnter(reminder.id, trigger, occurredAt)) {
                    is GeoEnterResult.Delivered -> deliver(reminder.id, fullScreenEligible = true)
                    is GeoEnterResult.VerificationScheduled -> {
                        if (result.result is GeoVerificationScheduleResult.Failed) {
                            throw result.result.cause
                        }
                    }
                }
            }
        }
    }

    override fun onVerificationDue(
        reminderId: ReminderId,
        triggerId: TriggerId,
        completion: () -> Unit,
    ) {
        launchReceiverWork(completion) { awaitGeoVerification(reminderId, triggerId) }
    }

    override fun onGeofencingError(errorCode: Int) {
        Log.e(TAG, "Geofencing event failed with code $errorCode")
    }

    override suspend fun handle(event: AlertActionEvent) {
        val reminder = repository.get(event.reminderId) ?: return
        val now = clock.instant()
        when (event.action) {
            AlertAction.SNOOZE -> {
                reminder.geoTrigger?.let { trigger ->
                    awaitGeofenceCancellation(trigger.id)
                }
                val until = now.plus(event.snoozeDuration)
                repository.snooze(event.reminderId, until, now)
                when (val result = exactAlarmScheduler.scheduleSnoozeCheck(event.reminderId, until)) {
                    is com.geominder.reminder.alarm.AlarmScheduleResult.Scheduled -> Unit
                    else -> error("Snooze alarm was not scheduled: $result")
                }
            }
            AlertAction.DISMISS -> {
                exactAlarmScheduler.cancel(reminder)
                reminder.geoTrigger?.let { awaitGeofenceCancellation(it.id) }
                repository.dismiss(event.reminderId, now)
            }
            AlertAction.DONE -> {
                exactAlarmScheduler.cancel(reminder)
                reminder.geoTrigger?.let { awaitGeofenceCancellation(it.id) }
                repository.complete(event.reminderId, now)
            }
        }
    }

    private suspend fun deliver(reminderId: ReminderId, fullScreenEligible: Boolean) {
        val currentExactAccess = alarmPermissionController.capabilities().canScheduleExactAlarm
        alertCoordinator.onTriggered(
            reminderId = reminderId,
            hasExactAlarmAccess = fullScreenEligible && currentExactAccess,
        )
    }

    private suspend fun awaitGeoVerification(reminderId: ReminderId, triggerId: TriggerId) {
        val reminder = repository.get(reminderId) ?: return
        if (!reminder.isPending) return
        val trigger = reminder.geoTrigger?.takeIf { it.id == triggerId } ?: return
        val result = suspendCancellableCoroutine { continuation ->
            val cancellation = geoEnterCoordinator.verifyAtActiveFrom(reminderId, trigger) {
                result ->
                if (continuation.isActive) continuation.resume(result)
            }
            continuation.invokeOnCancellation { cancellation.cancel() }
        }
        when (result) {
            is GeoVerificationResult.Delivered -> deliver(reminderId, fullScreenEligible = true)
            is GeoVerificationResult.LocationUnavailable ->
                Log.w(TAG, "Location unavailable during gated check: ${result.reason}")
            is GeoVerificationResult.Rescheduled -> {
                if (result.result is GeoVerificationScheduleResult.Failed) {
                    throw result.result.cause
                }
            }
            is GeoVerificationResult.Outside -> Unit
        }
    }

    private suspend fun awaitGeofenceCancellation(triggerId: TriggerId) {
        withTimeout(GEOFENCE_OPERATION_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                geofenceRegistrar.cancel(
                    triggerId,
                    GeofenceOperationCallback { result ->
                        if (continuation.isActive) {
                            if (result is com.geominder.reminder.geofence.GeofenceOperationResult.Success) {
                                continuation.resume(Unit)
                            } else {
                                continuation.resumeWithException(
                                    IllegalStateException("Geofence cancellation failed: $result"),
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    private fun launchReceiverWork(completion: () -> Unit, block: suspend () -> Unit) {
        val completed = AtomicBoolean(false)
        fun finish() {
            if (completed.compareAndSet(false, true)) completion()
        }
        runCatching {
            scope.launch {
                try {
                    block()
                } catch (error: Throwable) {
                    Log.e(TAG, "Receiver work failed", error)
                } finally {
                    finish()
                }
            }
        }.onFailure {
            Log.e(TAG, "Unable to launch receiver work", it)
            finish()
        }
    }

    private companion object {
        const val TAG = "GeominderRuntime"
        const val GEOFENCE_OPERATION_TIMEOUT_MILLIS = 8_000L
    }
}
