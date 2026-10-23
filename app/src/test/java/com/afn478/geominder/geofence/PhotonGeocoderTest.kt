package com.afn478.geominder.geofence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executor

class PhotonGeocoderTest {
    private val response = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","properties":{"name":"Empire State Building","street":"5th Avenue",
            "housenumber":"350","postcode":"10118","city":"New York","state":"New York",
            "country":"United States"},
           "geometry":{"type":"Point","coordinates":[-73.9857,40.7484]}},
          {"type":"Feature","properties":{"street":"Broadway","city":"New York"},
           "geometry":{"type":"Point","coordinates":[-73.99,40.75]}},
          {"type":"Feature","properties":{"name":"Broken"},"geometry":{"type":"Point","coordinates":[]}}
        ]}
    """.trimIndent()

    @Test
    fun `search results carry a name, an address line and GeoJSON coordinates`() {
        var requested: String? = null
        val geocoder = PhotonGeocoder(
            localeProvider = { Locale.GERMAN },
            fetch = { url -> requested = url; response },
        )

        val results = geocoder.search("empire state", nearLatitude = 40.7, nearLongitude = -74.0)

        assertEquals(2, results.size)
        assertEquals("Empire State Building", results[0].name)
        assertEquals(40.7484, results[0].latitude, 0.0)
        assertEquals(-73.9857, results[0].longitude, 0.0)
        assertTrue(results[0].detail.orEmpty().contains("5th Avenue 350"))
        assertTrue(requested.orEmpty().contains("q=empire+state"))
        assertTrue(requested.orEmpty().contains("&lat=40.7&lon=-74.0"))
        assertTrue(requested.orEmpty().contains("&lang=de"))
    }

    @Test
    fun `unsupported languages fall back to local names`() {
        var requested: String? = null
        PhotonGeocoder(localeProvider = { Locale.JAPANESE }, fetch = { requested = it; response })
            .search("tower", null, null)

        assertTrue(!requested.orEmpty().contains("lang="))
    }

    @Test
    fun `reverse geocoding falls back to coordinates when offline`() {
        var label: String? = null
        PhotonGeocoder(
            fetch = { throw IOException("offline") },
            workerExecutor = Executor { it.run() },
            callbackExecutor = Executor { it.run() },
        ).resolve(40.7484, -73.9857) { label = it }

        assertEquals("40.74840, -73.98570", label)
    }

    @Test
    fun `reverse geocoding names the place before its address`() {
        var label: String? = null
        PhotonGeocoder(
            fetch = { response },
            workerExecutor = Executor { it.run() },
            callbackExecutor = Executor { it.run() },
        ).resolve(40.7484, -73.9857) { label = it }

        assertTrue(label.orEmpty().startsWith("Empire State Building, 5th Avenue 350"))
    }

    @Test
    fun `an empty query never hits the network`() {
        val geocoder = PhotonGeocoder(fetch = { error("no request") })

        assertTrue(geocoder.search("  ", null, null).isEmpty())
        assertNull(PhotonResponseParser.parse("""{"features":[]}""").firstOrNull())
    }
}
