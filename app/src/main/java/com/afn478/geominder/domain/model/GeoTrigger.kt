package com.afn478.geominder.domain.model

import java.time.Instant

/**
 * An enter-transition geofence. [activeFrom] gates delivery, not registration: when an
 * enter event arrives before it, the scheduler waits until this instant and verifies
 * that the device is still inside the radius.
 */
data class GeoTrigger(
    val id: TriggerId = TriggerId.create(),
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val label: String? = null,
    val activeFrom: Instant? = null,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
        require(radiusMeters.isFinite() && radiusMeters > 0.0) {
            "Geofence radius must be a positive finite value"
        }
        require(label == null || label.isNotBlank()) { "Geofence label must not be blank" }
    }
}
