package com.afn478.geominder.geofence

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

fun interface GeoLabelResolver {
    fun resolve(latitude: Double, longitude: Double, callback: (String) -> Unit)
}

/** Resolves an optional address while always returning a useful `near X` label. */
class AndroidReverseGeocoder(
    context: Context,
    private val callbackExecutor: Executor = ContextCompat.getMainExecutor(context),
    private val workerExecutor: Executor = GEOCODER_EXECUTOR,
    locale: Locale = Locale.getDefault(),
) : GeoLabelResolver {
    private val geocoder = Geocoder(context.applicationContext, locale)

    override fun resolve(latitude: Double, longitude: Double, callback: (String) -> Unit) {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
        val fallback = formatLabel(formatCoordinates(latitude, longitude))
        if (!Geocoder.isPresent()) {
            callbackExecutor.execute { callback(fallback) }
            return
        }

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
        val label = name?.takeIf(String::isNotBlank)?.let(::formatLabel) ?: fallback
        callbackExecutor.execute { callback(label) }
    }

    private fun formatLabel(place: String): String = "near $place"

    private fun formatCoordinates(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.5f, %.5f", latitude, longitude)

    private companion object {
        val GEOCODER_EXECUTOR: Executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "geominder-geocoder").apply { isDaemon = true }
        }
    }
}
