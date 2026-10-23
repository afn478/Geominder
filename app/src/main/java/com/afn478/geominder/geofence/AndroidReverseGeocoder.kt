package com.afn478.geominder.geofence

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Supplies a raw place or coordinate label; UI layers add any localized proximity wording. */
fun interface GeoLabelResolver {
    fun resolve(latitude: Double, longitude: Double, callback: (String) -> Unit)
}

/**
 * The platform [Geocoder]. Its backend is supplied by the device (Google Play services on most
 * phones), so [Geocoder.isPresent] decides whether it can be used at all.
 */
class AndroidReverseGeocoder(
    context: Context,
    private val callbackExecutor: Executor = ContextCompat.getMainExecutor(context),
    private val workerExecutor: Executor = GEOCODER_EXECUTOR,
    private val localeProvider: () -> Locale = Locale::getDefault,
) : Geocoding {
    private val applicationContext = context.applicationContext

    override fun resolve(latitude: Double, longitude: Double, callback: (String) -> Unit) {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
        val fallback = formatCoordinates(latitude, longitude)
        if (!Geocoder.isPresent()) {
            callbackExecutor.execute { callback(fallback) }
            return
        }

        val geocoder = Geocoder(applicationContext, localeProvider())

        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                geocoder.getFromLocation(
                    latitude,
                    longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            deliver(addresses.firstOrNull()?.displayName(), fallback, callback)
                        }

                        override fun onError(errorMessage: String?) {
                            deliver(null, fallback, callback)
                        }
                    },
                )
            }.onFailure {
                deliver(null, fallback, callback)
            }
        } else {
            workerExecutor.execute {
                @Suppress("DEPRECATION")
                val address = runCatching {
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                }.getOrNull()
                deliver(address?.displayName(), fallback, callback)
            }
        }
    }

    override fun search(query: String, nearLatitude: Double?, nearLongitude: Double?): List<PlaceResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || !Geocoder.isPresent()) return emptyList()
        // Blocking by contract, so the deprecated synchronous call is the simplest fit here.
        @Suppress("DEPRECATION")
        val addresses = Geocoder(applicationContext, localeProvider())
            .getFromLocationName(trimmed, SEARCH_LIMIT)
            .orEmpty()
        return addresses.filter { it.hasLatitude() && it.hasLongitude() }.map { address ->
            val full = address.displayName()
            val name = address.featureName?.takeIf { it.isNotBlank() && it != address.subThoroughfare }
                ?: full
                ?: String.format(Locale.US, "%.5f, %.5f", address.latitude, address.longitude)
            PlaceResult(
                name = name,
                detail = full?.takeIf { it != name },
                latitude = address.latitude,
                longitude = address.longitude,
            )
        }
    }

    private fun Address.displayName(): String? = GeoAddressFormatter.format(
        GeoAddressComponents(
            featureName = featureName,
            thoroughfare = thoroughfare,
            subThoroughfare = subThoroughfare,
            postalCode = postalCode,
            locality = locality,
            subLocality = subLocality,
            subAdminArea = subAdminArea,
            adminArea = adminArea,
            countryName = countryName,
        ),
        (0 until maxAddressLineIndex()).map { getAddressLine(it) },
    )

    private fun Address.maxAddressLineIndex(): Int = generateSequence(0) { it + 1 }
        .takeWhile { runCatching { getAddressLine(it) }.getOrNull() != null }
        .count()

    private fun deliver(name: String?, fallback: String, callback: (String) -> Unit) {
        val label = name?.takeIf(String::isNotBlank) ?: fallback
        callbackExecutor.execute { callback(label) }
    }

    private fun formatCoordinates(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.5f, %.5f", latitude, longitude)

    private companion object {
        const val SEARCH_LIMIT = 6
        val GEOCODER_EXECUTOR: Executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "geominder-geocoder").apply { isDaemon = true }
        }
    }
}
