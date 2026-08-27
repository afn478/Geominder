package com.afn478.geominder.domain.model

/** A named location configured in settings and copied into a reminder's geo trigger. */
data class PresetLocation(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = DEFAULT_RADIUS_METERS,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude must be between -90 and 90"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude must be between -180 and 180"
        }
        require(radiusMeters.isFinite() && radiusMeters > 0.0) {
            "Preset location radius must be a positive finite value"
        }
    }

    companion object {
        const val DEFAULT_RADIUS_METERS = 100.0
    }
}
