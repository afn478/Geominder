package com.afn478.geominder.geofence

import java.time.Instant

sealed interface GeoGateDecision {
    data object FireNow : GeoGateDecision
    data class VerifyAt(val instant: Instant) : GeoGateDecision
}

object GeoActiveFromGate {
    fun decide(now: Instant, activeFrom: Instant?): GeoGateDecision =
        if (activeFrom == null || !now.isBefore(activeFrom)) {
            GeoGateDecision.FireNow
        } else {
            GeoGateDecision.VerifyAt(activeFrom)
        }
}
