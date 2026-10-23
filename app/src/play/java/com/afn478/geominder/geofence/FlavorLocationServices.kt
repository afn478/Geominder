package com.afn478.geominder.geofence

import android.content.Context
import android.location.Geocoder
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import java.util.Locale

/** Play build: Google Play services when installed, Android's own location APIs otherwise. */
object FlavorLocationServices {
    fun geofenceRegistrar(context: Context): GeofenceRegistrar {
        val primary by lazy {
            PlayGeofenceRegistrar(context, LocationServices.getGeofencingClient(context))
        }
        return FallbackGeofenceRegistrar(
            primary = { primary },
            fallback = PlatformGeofenceRegistrar(context),
            usePrimary = { isPlayServicesAvailable(context) },
        )
    }

    fun currentLocationProvider(context: Context): CurrentLocationProvider {
        val primary by lazy { FusedCurrentLocationProvider(context) }
        return FallbackCurrentLocationProvider(
            primary = { primary },
            fallback = PlatformCurrentLocationProvider(context),
            usePrimary = { isPlayServicesAvailable(context) },
        )
    }

    /** The platform geocoder where the device provides one, the open Photon service otherwise. */
    fun geocoding(context: Context, localeProvider: () -> Locale): Geocoding = FallbackGeocoding(
        platform = AndroidReverseGeocoder(context, localeProvider = localeProvider),
        open = PhotonGeocoder(localeProvider),
        usePlatform = Geocoder::isPresent,
    )

    private fun isPlayServicesAvailable(context: Context): Boolean = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
            ConnectionResult.SUCCESS
    }.getOrDefault(false)
}
