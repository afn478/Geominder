package com.geominder.reminder.geofence

import com.geominder.reminder.domain.model.GeoTrigger
import com.geominder.reminder.domain.model.ReminderId
import com.geominder.reminder.domain.model.TriggerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class GeoEnterCoordinatorTest {
    private val now = Instant.parse("2026-08-24T12:00:00Z")
    private val reminderId = ReminderId("reminder-1")

    @Test
    fun `early enter schedules exact-time verification without emitting`() {
        val scheduler = FakeScheduler()
        val emitted = mutableListOf<GeoTriggerEvent>()
        val trigger = trigger(activeFrom = now.plusSeconds(300))
        val coordinator = coordinator(scheduler, emitted)

        val result = coordinator.onEnter(reminderId, trigger)

        assertTrue(result is GeoEnterResult.VerificationScheduled)
        assertEquals(
            GeoVerificationRequest(reminderId, trigger.id, now.plusSeconds(300)),
            scheduler.scheduled.single(),
        )
        assertTrue(emitted.isEmpty())
    }

    @Test
    fun `verification emits only when current fix is within inclusive radius`() {
        val scheduler = FakeScheduler()
        val emitted = mutableListOf<GeoTriggerEvent>()
        val trigger = trigger(activeFrom = now.minusSeconds(1), radiusMeters = 120.0)
        val coordinator = coordinator(
            scheduler = scheduler,
            emitted = emitted,
            location = LocationFix(40.7128, -74.0060, 5f, now),
        )
        var verificationResult: GeoVerificationResult? = null

        coordinator.verifyAtActiveFrom(reminderId, trigger) { verificationResult = it }

        assertTrue(verificationResult is GeoVerificationResult.Delivered)
        assertEquals(GeoTriggerCause.ACTIVE_FROM_VERIFICATION, emitted.single().cause)
    }

    @Test
    fun `verification does not emit after device leaves radius`() {
        val emitted = mutableListOf<GeoTriggerEvent>()
        val coordinator = coordinator(
            scheduler = FakeScheduler(),
            emitted = emitted,
            location = LocationFix(40.7812, -73.9665, 5f, now),
        )
        var verificationResult: GeoVerificationResult? = null

        coordinator.verifyAtActiveFrom(reminderId, trigger(activeFrom = now.minusSeconds(1))) {
            verificationResult = it
        }

        assertTrue(verificationResult is GeoVerificationResult.Outside)
        assertTrue(emitted.isEmpty())
    }

    private fun trigger(
        activeFrom: Instant?,
        radiusMeters: Double = 120.0,
    ) = GeoTrigger(
        id = TriggerId("geo-1"),
        latitude = 40.7128,
        longitude = -74.0060,
        radiusMeters = radiusMeters,
        activeFrom = activeFrom,
    )

    private fun coordinator(
        scheduler: FakeScheduler,
        emitted: MutableList<GeoTriggerEvent>,
        location: LocationFix = LocationFix(40.7128, -74.0060, 5f, now),
    ) = GeoEnterCoordinator(
        locationProvider = CurrentLocationProvider { callback ->
            callback(LocationResult.Available(location))
            CancellationHandle {}
        },
        verificationScheduler = scheduler,
        triggerEmitter = GeoTriggerEmitter(emitted::add),
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    private class FakeScheduler : GeoVerificationScheduler {
        val scheduled = mutableListOf<GeoVerificationRequest>()

        override fun schedule(request: GeoVerificationRequest): GeoVerificationScheduleResult {
            scheduled += request
            return GeoVerificationScheduleResult.Scheduled
        }

        override fun cancel(reminderId: ReminderId, triggerId: TriggerId) = Unit
    }
}
