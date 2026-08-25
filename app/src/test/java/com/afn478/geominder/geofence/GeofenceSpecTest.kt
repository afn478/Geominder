package com.afn478.geominder.geofence

import com.afn478.geominder.domain.model.GeoTrigger
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.TriggerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeofenceSpecTest {
    @Test
    fun `request ID is stable and recovers trigger ID`() {
        val triggerId = TriggerId("trigger-123")
        val trigger = GeoTrigger(
            id = triggerId,
            latitude = 40.7128,
            longitude = -74.0060,
            radiusMeters = 125.0,
        )

        val first = GeofenceSpec.from(ReminderId("reminder-1"), trigger)
        val second = GeofenceSpec.from(ReminderId("reminder-1"), trigger)

        assertEquals("geominder:geo:trigger-123", first.requestId)
        assertEquals(first.requestId, second.requestId)
        assertEquals(triggerId, StableGeofenceId.parse(first.requestId))
    }

    @Test
    fun `unowned request ID is ignored`() {
        assertNull(StableGeofenceId.parse("another-app:trigger-123"))
    }
}
