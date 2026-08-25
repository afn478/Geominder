package com.afn478.geominder.geofence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardGeoInputParserTest {
    @Test
    fun `parses comma and whitespace separated decimal coordinates`() {
        assertCoordinates(40.7128, -74.0060, "40.7128,-74.0060")
        assertCoordinates(40.7128, -74.0060, " 40.7128  -74.0060 ")
        assertCoordinates(40.7128, -74.0060, "40.7128° N, 74.0060° W")
    }

    @Test
    fun `prefers the pin in an OsmAnd link over its map centre`() {
        assertCoordinates(
            40.7580,
            -73.9855,
            "https://osmand.net/map/?pin=40.7580,-73.9855#9/40.7812/-73.9665",
        )
    }

    @Test
    fun `parses OsmAnd and OpenStreetMap map fragments`() {
        assertCoordinates(40.7812, -73.9665, "https://osmand.net/map/#9/40.7812/-73.9665")
        assertCoordinates(40.7128, -74.0060, "https://www.openstreetmap.org/#map=16/40.7128/-74.0060")
    }

    @Test
    fun `parses geo Google and labeled formats`() {
        assertCoordinates(40.7128, -74.0060, "geo:40.7128,-74.0060?z=16")
        assertCoordinates(40.7484, -73.9857, "https://maps.google.com/maps/@40.7484,-73.9857,11z")
        assertCoordinates(40.7128, -74.0060, "Latitude: 40.7128; Longitude: -74.0060")
        assertCoordinates(40.7128, -74.0060, "longitude=-74.0060 latitude=40.7128")
    }

    @Test
    fun `parses degrees minutes and seconds copied from map apps`() {
        assertCoordinates(40.7128, -74.0060, "40°42′46.08″N 74°00′21.6″W", tolerance = 0.000001)
        assertCoordinates(40.7128, -74.0060, "40°42.768′ N, 74°00.36′ W", tolerance = 0.000001)
    }

    @Test
    fun `rejects absent and out of range coordinates`() {
        assertNull(ClipboardGeoInputParser.parse("Penn Station"))
        assertNull(ClipboardGeoInputParser.parse("95.0, -74.0"))
    }

    private fun assertCoordinates(
        latitude: Double,
        longitude: Double,
        input: String,
        tolerance: Double = 0.0,
    ) {
        val parsed = requireNotNull(ClipboardGeoInputParser.parse(input))
        assertEquals(latitude, parsed.latitude, tolerance)
        assertEquals(longitude, parsed.longitude, tolerance)
    }
}
