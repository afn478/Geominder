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
    fun `prefers a Google place pin over the viewport centre`() {
        assertCoordinates(
            40.7506,
            -73.9935,
            "https://www.google.com/maps/place/Penn+Station/@40.7489,-73.9900,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d40.7506!4d-73.9935",
        )
    }

    @Test
    fun `parses coordinates in map link paths and parameters`() {
        assertCoordinates(40.7128, -74.0060, "https://www.google.com/maps/search/40.7128,+-74.0060?entry=tts")
        assertCoordinates(40.7128, -74.0060, "https://www.google.com/maps/place/40.7128,-74.0060")
        assertCoordinates(40.7128, -74.0060, "https://maps.apple.com/place?coordinate=40.7128,-74.0060&name=Pin")
        assertCoordinates(
            40.7580,
            -73.9855,
            "https://www.openstreetmap.org/?mlat=40.7580&mlon=-73.9855#map=17/40.7812/-73.9665",
        )
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
