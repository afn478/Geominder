package com.afn478.geominder.geofence

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class GeoActiveFromGateTest {
    private val now = Instant.parse("2026-08-24T12:00:00Z")

    @Test
    fun `missing active-from fires immediately`() {
        assertEquals(GeoGateDecision.FireNow, GeoActiveFromGate.decide(now, null))
    }

    @Test
    fun `enter before active-from schedules verification`() {
        val activeFrom = now.plusSeconds(60)

        assertEquals(
            GeoGateDecision.VerifyAt(activeFrom),
            GeoActiveFromGate.decide(now, activeFrom),
        )
    }

    @Test
    fun `active-from boundary fires immediately`() {
        assertEquals(GeoGateDecision.FireNow, GeoActiveFromGate.decide(now, now))
    }
}
