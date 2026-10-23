package com.afn478.geominder.ui.map

import android.os.Bundle
import androidx.core.os.bundleOf

/** The place confirmed in the map picker, handed back to the screen that opened it. */
data class PickedLocation(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val label: String?,
) {
    fun toBundle(): Bundle = bundleOf(
        KEY_LATITUDE to latitude,
        KEY_LONGITUDE to longitude,
        KEY_RADIUS to radiusMeters,
        KEY_LABEL to label,
    )

    companion object {
        /** SavedStateHandle key on the opening screen's back stack entry. */
        const val RESULT_KEY = "picked_location"

        private const val KEY_LATITUDE = "latitude"
        private const val KEY_LONGITUDE = "longitude"
        private const val KEY_RADIUS = "radius"
        private const val KEY_LABEL = "label"

        fun fromBundle(bundle: Bundle): PickedLocation? {
            val latitude = bundle.getDouble(KEY_LATITUDE, Double.NaN)
            val longitude = bundle.getDouble(KEY_LONGITUDE, Double.NaN)
            val radius = bundle.getDouble(KEY_RADIUS, Double.NaN)
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
            if (!radius.isFinite() || radius <= 0.0) return null
            return PickedLocation(latitude, longitude, radius, bundle.getString(KEY_LABEL))
        }
    }
}
