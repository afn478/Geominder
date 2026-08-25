package com.geominder.reminder.geofence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class GeoAddressFormatterTest {
    @Test
    fun `rich components are not collapsed to feature name`() {
        val result = GeoAddressFormatter.format(
            GeoAddressComponents(
                featureName = "42",
                thoroughfare = "Broadway",
                postalCode = "10004",
                locality = "New York",
                adminArea = "New York",
                countryName = "United States",
            ),
        )

        assertEquals("Broadway 42, 10004 New York, New York, United States", result)
        assertTrue(result!!.count { it == ',' } == 3)
    }

    @Test
    fun `rich platform line wins and components are fallback`() {
        val components = GeoAddressComponents(thoroughfare = "Broadway", postalCode = "10004", locality = "New York")
        assertEquals(
            "42 Broadway, New York, NY 10004, United States",
            GeoAddressFormatter.format(components, listOf("42 Broadway, New York, NY 10004, United States")),
        )
        assertEquals("Broadway, 10004 New York", GeoAddressFormatter.format(components))
    }

    @Test
    fun `empty address data returns null`() {
        assertNull(GeoAddressFormatter.format(GeoAddressComponents(featureName = "12"), listOf("12")))
        assertNull(GeoAddressFormatter.format(GeoAddressComponents()))
    }

    @Test
    fun `deduplication is stable under Turkish default locale`() {
        val originalLocale = Locale.getDefault()
        try {
            val components = GeoAddressComponents(locality = "Inwood")
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val turkishResult = GeoAddressFormatter.format(components, listOf("inwood"))

            Locale.setDefault(Locale.ROOT)
            val rootResult = GeoAddressFormatter.format(components, listOf("inwood"))

            assertEquals("Inwood", turkishResult)
            assertEquals(rootResult, turkishResult)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
