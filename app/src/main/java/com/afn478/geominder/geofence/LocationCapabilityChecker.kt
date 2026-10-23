package com.afn478.geominder.geofence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

sealed interface LocationCapability {
    data object Available : LocationCapability
    data object FineLocationPermissionRequired : LocationCapability
    data object BackgroundLocationPermissionRequired : LocationCapability
    data object LocationServicesDisabled : LocationCapability
}

/** Permission and settings checks shared by the platform and Play services location backends. */
class LocationCapabilityChecker(context: Context) {
    private val applicationContext = context.applicationContext

    fun check(requireBackgroundLocation: Boolean): LocationCapability {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return LocationCapability.FineLocationPermissionRequired
        }
        if (
            requireBackgroundLocation &&
            !hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            return LocationCapability.BackgroundLocationPermissionRequired
        }

        val locationManager = applicationContext.getSystemService(LocationManager::class.java)
        if (locationManager?.isLocationEnabled != true) {
            return LocationCapability.LocationServicesDisabled
        }
        return LocationCapability.Available
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(applicationContext, permission) ==
            PackageManager.PERMISSION_GRANTED
}

internal fun LocationCapability.toLocationFailure(): LocationFailure = when (this) {
    LocationCapability.Available -> error("Available is not a failure")
    LocationCapability.FineLocationPermissionRequired -> LocationFailure.PERMISSION_REQUIRED
    LocationCapability.BackgroundLocationPermissionRequired ->
        LocationFailure.BACKGROUND_PERMISSION_REQUIRED
    LocationCapability.LocationServicesDisabled -> LocationFailure.LOCATION_DISABLED
}
