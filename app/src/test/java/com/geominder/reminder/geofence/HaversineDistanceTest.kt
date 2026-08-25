package com.geominder.reminder.geofence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaversineDistanceTest {
    @Test
    fun `same point has zero distance`() {
        assertEquals(0.0, HaversineDistance.meters(40.7128, -74.0060, 40.7128, -74.0060), 0.0)
    }

    @Test
    fun `distance includes the radius boundary`() {
        val distance = HaversineDistance.meters(40.7128, -74.0060, 40.7138, -74.0060)

        assertTrue(HaversineDistance.isWithin(40.7128, -74.0060, 40.7138, -74.0060, distance))
        assertFalse(
            HaversineDistance.isWithin(40.7128, -74.0060, 40.7138, -74.0060, Math.nextDown(distance)),
        )
    }

    @Test
    fun `one latitude degree matches mean-earth distance`() {
        assertEquals(111_195.0, HaversineDistance.meters(40.7128, -74.0060, 41.7128, -74.0060), 1.0)
    }
}
