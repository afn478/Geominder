package com.afn478.geominder.geofence

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Geocoding with Photon (https://photon.komoot.io), an open geocoder built on OpenStreetMap
 * data. It needs no account or key, and works without Google Play services.
 */
class PhotonGeocoder(
    private val localeProvider: () -> Locale = Locale::getDefault,
    private val fetch: (String) -> String = { HttpText.get(it) },
    private val workerExecutor: Executor = WORKER,
    private val callbackExecutor: Executor = Executor { Handler(Looper.getMainLooper()).post(it) },
) : Geocoding {
    override fun resolve(latitude: Double, longitude: Double, callback: (String) -> Unit) {
        val fallback = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
        workerExecutor.execute {
            val label = runCatching {
                val url = "$BASE_URL/reverse?lat=$latitude&lon=$longitude&limit=1${languageParameter()}"
                PhotonResponseParser.parse(fetch(url)).firstOrNull()?.label
            }.getOrNull()
            callbackExecutor.execute { callback(label?.takeIf(String::isNotBlank) ?: fallback) }
        }
    }

    override fun search(query: String, nearLatitude: Double?, nearLongitude: Double?): List<PlaceResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val bias = if (nearLatitude != null && nearLongitude != null) {
            "&lat=$nearLatitude&lon=$nearLongitude"
        } else {
            ""
        }
        val url = "$BASE_URL/api/?q=${encode(trimmed)}&limit=$SEARCH_LIMIT$bias${languageParameter()}"
        return PhotonResponseParser.parse(fetch(url)).map { it.toPlaceResult() }
    }

    /** Photon only localizes a few languages; others get local names, which beats an error. */
    private fun languageParameter(): String =
        localeProvider().language.takeIf { it in SUPPORTED_LANGUAGES }?.let { "&lang=$it" }.orEmpty()

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private companion object {
        const val BASE_URL = "https://photon.komoot.io"
        const val SEARCH_LIMIT = 6
        val SUPPORTED_LANGUAGES = setOf("de", "en", "fr")
        val WORKER: Executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "geominder-photon").apply { isDaemon = true }
        }
    }
}

internal data class PhotonPlace(
    val name: String?,
    val components: GeoAddressComponents,
    val latitude: Double,
    val longitude: Double,
) {
    /** The address-style label used for reminders, like the platform geocoder produces. */
    val label: String?
        get() = listOfNotNull(name, address).distinct().joinToString(", ").ifEmpty { null }

    private val address: String?
        get() = GeoAddressFormatter.format(components)

    fun toPlaceResult(): PlaceResult {
        val title = name ?: address ?: String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
        return PlaceResult(
            name = title,
            detail = address?.takeIf { it != title },
            latitude = latitude,
            longitude = longitude,
        )
    }
}

internal object PhotonResponseParser {
    fun parse(json: String): List<PhotonPlace> {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { index ->
            val feature = features.optJSONObject(index) ?: return@mapNotNull null
            val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
                ?: return@mapNotNull null
            val longitude = coordinates.optDouble(0)
            val latitude = coordinates.optDouble(1)
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return@mapNotNull null
            val properties = feature.optJSONObject("properties") ?: JSONObject()
            fun text(key: String): String? =
                properties.optString(key, "").trim().takeIf(String::isNotEmpty)
            PhotonPlace(
                name = text("name"),
                // The name is kept apart: the formatter would read it as a house number.
                components = GeoAddressComponents(
                    thoroughfare = text("street"),
                    subThoroughfare = text("housenumber"),
                    postalCode = text("postcode"),
                    locality = text("city") ?: text("town") ?: text("village"),
                    subLocality = text("district"),
                    adminArea = text("state"),
                    countryName = text("country"),
                ),
                latitude = latitude,
                longitude = longitude,
            )
        }
    }
}
