package com.afn478.geominder.geofence

/** A search hit: a short [name] plus an optional address line for telling similar names apart. */
data class PlaceResult(
    val name: String,
    val detail: String?,
    val latitude: Double,
    val longitude: Double,
)

fun interface PlaceSearch {
    /**
     * Finds places matching [query], preferring ones near [nearLatitude]/[nearLongitude] when
     * given. Blocking; call it off the main thread. Throws when the search service can't be
     * reached, and returns an empty list when nothing matches.
     */
    fun search(
        query: String,
        nearLatitude: Double?,
        nearLongitude: Double?,
    ): List<PlaceResult>
}

/** Reverse (coordinates to label) and forward (text to places) geocoding from one backend. */
interface Geocoding : GeoLabelResolver, PlaceSearch

/**
 * Uses the platform [Geocoder][android.location.Geocoder] when the device has a backend for it
 * and an open service otherwise.
 */
class FallbackGeocoding(
    private val platform: Geocoding,
    private val open: Geocoding,
    private val usePlatform: () -> Boolean,
) : Geocoding {
    override fun resolve(latitude: Double, longitude: Double, callback: (String) -> Unit) =
        (if (usePlatform()) platform else open).resolve(latitude, longitude, callback)

    override fun search(query: String, nearLatitude: Double?, nearLongitude: Double?): List<PlaceResult> {
        if (usePlatform()) {
            val results = runCatching { platform.search(query, nearLatitude, nearLongitude) }
                .getOrDefault(emptyList())
            if (results.isNotEmpty()) return results
        }
        return open.search(query, nearLatitude, nearLongitude)
    }
}
