package com.afn478.geominder.geofence

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object HaversineDistance {
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    fun meters(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double,
    ): Double {
        require(startLatitude in -90.0..90.0 && endLatitude in -90.0..90.0)
        require(startLongitude in -180.0..180.0 && endLongitude in -180.0..180.0)

        val latitudeDelta = Math.toRadians(endLatitude - startLatitude)
        val longitudeDelta = Math.toRadians(endLongitude - startLongitude)
        val startLatitudeRadians = Math.toRadians(startLatitude)
        val endLatitudeRadians = Math.toRadians(endLatitude)
        val haversine = sin(latitudeDelta / 2).let { it * it } +
            cos(startLatitudeRadians) * cos(endLatitudeRadians) *
            sin(longitudeDelta / 2).let { it * it }
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }

    fun isWithin(
        latitude: Double,
        longitude: Double,
        centerLatitude: Double,
        centerLongitude: Double,
        radiusMeters: Double,
    ): Boolean {
        require(radiusMeters.isFinite() && radiusMeters > 0.0)
        return meters(latitude, longitude, centerLatitude, centerLongitude) <= radiusMeters
    }
}
