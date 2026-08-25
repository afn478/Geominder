package com.geominder.reminder.geofence

import com.geominder.reminder.domain.model.ReminderId
import com.geominder.reminder.domain.model.TriggerId
import java.time.Instant

data class GeoVerificationRequest(
    val reminderId: ReminderId,
    val triggerId: TriggerId,
    val verifyAt: Instant,
)

sealed interface GeoVerificationScheduleResult {
    data object Scheduled : GeoVerificationScheduleResult
    data object ExactAlarmPermissionRequired : GeoVerificationScheduleResult
    data class Failed(val cause: Throwable) : GeoVerificationScheduleResult
}

interface GeoVerificationScheduler {
    fun schedule(request: GeoVerificationRequest): GeoVerificationScheduleResult
    fun cancel(reminderId: ReminderId, triggerId: TriggerId)
}

enum class GeoTriggerCause { ENTER, ACTIVE_FROM_VERIFICATION }

data class GeoTriggerEvent(
    val reminderId: ReminderId,
    val triggerId: TriggerId,
    val occurredAt: Instant,
    val cause: GeoTriggerCause,
)

fun interface GeoTriggerEmitter {
    fun emit(event: GeoTriggerEvent)
}

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val measuredAt: Instant,
)

sealed interface LocationResult {
    data class Available(val fix: LocationFix) : LocationResult
    data class Unavailable(val reason: LocationFailure) : LocationResult
}

enum class LocationFailure {
    PERMISSION_REQUIRED,
    BACKGROUND_PERMISSION_REQUIRED,
    LOCATION_DISABLED,
    PLAY_SERVICES_UNAVAILABLE,
    NO_LOCATION,
    REQUEST_FAILED,
}

fun interface CancellationHandle {
    fun cancel()
}

fun interface CurrentLocationProvider {
    fun locate(callback: (LocationResult) -> Unit): CancellationHandle
}

/**
 * Installed by the application layer during Application.onCreate. Receivers deliberately
 * expose IDs only, so integration can load the latest enabled reminder before acting.
 */
interface GeoFeatureEntryPoint {
    fun onGeofenceEnter(
        triggerIds: Set<TriggerId>,
        occurredAt: Instant,
        completion: () -> Unit,
    )

    fun onVerificationDue(
        reminderId: ReminderId,
        triggerId: TriggerId,
        completion: () -> Unit,
    )

    fun onGeofencingError(errorCode: Int)
}

object GeoFeatureRuntime {
    @Volatile
    var entryPoint: GeoFeatureEntryPoint? = null
}
