package com.afn478.geominder.geofence

import android.content.Context
import java.util.Locale

/** Libre build: Android's own location APIs only; no Google Play services code is included. */
object FlavorLocationServices {
    fun geofenceRegistrar(context: Context): GeofenceRegistrar = PlatformGeofenceRegistrar(context)

    fun currentLocationProvider(context: Context): CurrentLocationProvider =
        PlatformCurrentLocationProvider(context)

    /**
     * Always the open Photon service: the platform geocoder usually forwards to Google, which
     * this build avoids.
     */
    @Suppress("UNUSED_PARAMETER")
    fun geocoding(context: Context, localeProvider: () -> Locale): Geocoding =
        PhotonGeocoder(localeProvider)
}
