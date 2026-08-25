package com.afn478.geominder.geofence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

sealed interface LocationCapability {
    data object Available : LocationCapability
    data object FineLocationPermissionRequired : LocationCapability
    data object BackgroundLocationPermissionRequired : LocationCapability
    data object LocationServicesDisabled : LocationCapability
    data class PlayServicesUnavailable(val errorCode: Int) : LocationCapability
}

class LocationCapabilityChecker(
    context: Context,
    private val playServicesAvailability: GoogleApiAvailability = GoogleApiAvailability.getInstance(),
) {
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

        val playServicesStatus =
            playServicesAvailability.isGooglePlayServicesAvailable(applicationContext)
        if (playServicesStatus != ConnectionResult.SUCCESS) {
            return LocationCapability.PlayServicesUnavailable(playServicesStatus)
        }
        return LocationCapability.Available
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(applicationContext, permission) ==
            PackageManager.PERMISSION_GRANTED
}
