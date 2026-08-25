package com.afn478.geominder.geofence

import com.afn478.geominder.domain.model.GeoTrigger
import com.afn478.geominder.domain.model.ReminderId
import java.time.Clock
import java.time.Instant

sealed interface GeoEnterResult {
    data class Delivered(val event: GeoTriggerEvent) : GeoEnterResult
    data class VerificationScheduled(val result: GeoVerificationScheduleResult) : GeoEnterResult
}

sealed interface GeoVerificationResult {
    data class Delivered(val event: GeoTriggerEvent) : GeoVerificationResult
    data class Rescheduled(val result: GeoVerificationScheduleResult) : GeoVerificationResult
    data class Outside(val distanceMeters: Double) : GeoVerificationResult
    data class LocationUnavailable(val reason: LocationFailure) : GeoVerificationResult
}

/** Implements active-from gating for enter events and the deferred inside-radius check. */
class GeoEnterCoordinator(
    private val locationProvider: CurrentLocationProvider,
    private val verificationScheduler: GeoVerificationScheduler,
    private val triggerEmitter: GeoTriggerEmitter,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun onEnter(
        reminderId: ReminderId,
        trigger: GeoTrigger,
        occurredAt: Instant = clock.instant(),
    ): GeoEnterResult = when (val decision = GeoActiveFromGate.decide(occurredAt, trigger.activeFrom)) {
        GeoGateDecision.FireNow -> {
            val event = GeoTriggerEvent(
                reminderId = reminderId,
                triggerId = trigger.id,
                occurredAt = occurredAt,
                cause = GeoTriggerCause.ENTER,
            )
            triggerEmitter.emit(event)
            GeoEnterResult.Delivered(event)
        }

        is GeoGateDecision.VerifyAt -> GeoEnterResult.VerificationScheduled(
            verificationScheduler.schedule(
                GeoVerificationRequest(
                    reminderId = reminderId,
                    triggerId = trigger.id,
                    verifyAt = decision.instant,
                ),
            ),
        )
    }

    fun verifyAtActiveFrom(
        reminderId: ReminderId,
        trigger: GeoTrigger,
        callback: (GeoVerificationResult) -> Unit,
    ): CancellationHandle {
        val now = clock.instant()
        val decision = GeoActiveFromGate.decide(now, trigger.activeFrom)
        if (decision is GeoGateDecision.VerifyAt) {
            callback(
                GeoVerificationResult.Rescheduled(
                    verificationScheduler.schedule(
                        GeoVerificationRequest(reminderId, trigger.id, decision.instant),
                    ),
                ),
            )
            return CancellationHandle {}
        }

        return locationProvider.locate { result ->
            when (result) {
                is LocationResult.Unavailable -> callback(
                    GeoVerificationResult.LocationUnavailable(result.reason),
                )

                is LocationResult.Available -> {
                    val fix = result.fix
                    val distance = HaversineDistance.meters(
                        startLatitude = fix.latitude,
                        startLongitude = fix.longitude,
                        endLatitude = trigger.latitude,
                        endLongitude = trigger.longitude,
                    )
                    if (distance <= trigger.radiusMeters) {
                        val event = GeoTriggerEvent(
                            reminderId = reminderId,
                            triggerId = trigger.id,
                            occurredAt = now,
                            cause = GeoTriggerCause.ACTIVE_FROM_VERIFICATION,
                        )
                        triggerEmitter.emit(event)
                        callback(GeoVerificationResult.Delivered(event))
                    } else {
                        callback(GeoVerificationResult.Outside(distance))
                    }
                }
            }
        }
    }

    fun cancelVerification(reminderId: ReminderId, trigger: GeoTrigger) {
        verificationScheduler.cancel(reminderId, trigger.id)
    }
}
