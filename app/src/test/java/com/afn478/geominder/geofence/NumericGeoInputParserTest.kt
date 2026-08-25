package com.afn478.geominder.geofence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumericGeoInputParserTest {
    private val parser = NumericGeoInputParser(defaultRadiusMeters = 200.0)

    @Test
    fun `blank radius uses configured default`() {
        val result = parser.parse("40.7128", "-74.0060", "")

        assertEquals(
            NumericGeoInputResult.Valid(NumericGeoInput(40.7128, -74.0060, 200.0)),
            result,
        )
    }

    @Test
    fun `invalid fields are returned together`() {
        val result = parser.parse("91", "east", "0")

        assertTrue(result is NumericGeoInputResult.Invalid)
        val errors = (result as NumericGeoInputResult.Invalid).errors
        assertEquals(GeoInputError.OUT_OF_RANGE, errors[GeoInputField.LATITUDE])
        assertEquals(GeoInputError.NOT_A_NUMBER, errors[GeoInputField.LONGITUDE])
        assertEquals(GeoInputError.NOT_POSITIVE, errors[GeoInputField.RADIUS])
    }
}
