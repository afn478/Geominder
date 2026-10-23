package com.afn478.geominder.geofence

import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.TriggerId
import org.junit.Assert.assertEquals
import org.junit.Test

class FallbackLocationServicesTest {
    private val spec = GeofenceSpec(
        reminderId = ReminderId("reminder"),
        triggerId = TriggerId("trigger"),
        requestId = StableGeofenceId.from(TriggerId("trigger")),
        latitude = 40.7,
        longitude = -74.0,
        radiusMeters = 150.0,
        activeFrom = null,
    )

    @Test
    fun `registers with the platform while Play services is missing`() {
        val play = RecordingRegistrar("play")
        val platform = RecordingRegistrar("platform")
        val registrar = FallbackGeofenceRegistrar({ play }, platform, usePrimary = { false })

        registrar.register(spec) {}

        assertEquals(emptyList<String>(), play.calls)
        assertEquals(listOf("register trigger"), platform.calls)
    }

    @Test
    fun `switching to Play services removes the platform alert first`() {
        val play = RecordingRegistrar("play")
        val platform = RecordingRegistrar("platform")
        val registrar = FallbackGeofenceRegistrar({ play }, platform, usePrimary = { true })

        var result: GeofenceOperationResult? = null
        registrar.register(spec) { result = it }

        assertEquals(listOf("cancel trigger"), platform.calls)
        assertEquals(listOf("register trigger"), play.calls)
        assertEquals(GeofenceOperationResult.Success, result)
    }

    @Test
    fun `cancelling clears both backends when Play services is available`() {
        val play = RecordingRegistrar("play")
        val platform = RecordingRegistrar("platform")

        FallbackGeofenceRegistrar({ play }, platform, usePrimary = { true }).cancel(TriggerId("trigger")) {}
        FallbackGeofenceRegistrar({ error("Play services is absent") }, platform, usePrimary = { false })
            .cancel(TriggerId("trigger")) {}

        assertEquals(listOf("cancel trigger"), play.calls)
        assertEquals(listOf("cancel trigger", "cancel trigger"), platform.calls)
    }

    @Test
    fun `current location comes from the available backend`() {
        var used: String? = null
        val play = CurrentLocationProvider { used = "play"; CancellationHandle {} }
        val platform = CurrentLocationProvider { used = "platform"; CancellationHandle {} }

        FallbackCurrentLocationProvider({ play }, platform, usePrimary = { false }).locate {}
        assertEquals("platform", used)
        FallbackCurrentLocationProvider({ play }, platform, usePrimary = { true }).locate {}
        assertEquals("play", used)
    }

    private class RecordingRegistrar(private val name: String) : GeofenceRegistrar {
        val calls = mutableListOf<String>()

        override fun register(spec: GeofenceSpec, callback: GeofenceOperationCallback) {
            calls += "register ${spec.triggerId.value}"
            callback.onComplete(GeofenceOperationResult.Success)
        }

        override fun cancel(triggerId: TriggerId, callback: GeofenceOperationCallback) {
            calls += "cancel ${triggerId.value}"
            callback.onComplete(GeofenceOperationResult.Success)
        }

        override fun toString(): String = name
    }
}
