package com.afn478.geominder.settings

import com.afn478.geominder.domain.model.PresetLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class SettingsCodecTest {
    @Test
    fun `keyword table round trips spaces punctuation unicode and equals signs`() {
        val entries = linkedMapOf(
            "after work" to LocalTime.of(17, 45),
            "café = closed" to LocalTime.of(22, 5),
        )

        assertEquals(entries, SettingsCodec.decodeKeywordTimes(SettingsCodec.encodeKeywordTimes(entries)))
    }

    @Test
    fun `empty keyword table survives persistence instead of restoring defaults`() {
        assertEquals(emptyMap<String, LocalTime>(), SettingsCodec.decodeKeywordTimes("v1"))
    }

    @Test
    fun `keyword location table round trips spaces punctuation unicode and coordinates`() {
        val entries = linkedMapOf(
            "at home" to PresetLocation(40.7128, -74.0060, 275.0),
            "café = closed" to PresetLocation(-33.8688, 151.2093, 125.5),
        )

        assertEquals(
            entries,
            SettingsCodec.decodeKeywordLocations(SettingsCodec.encodeKeywordLocations(entries)),
        )
    }

    @Test
    fun `empty keyword location table survives persistence`() {
        assertEquals(emptyMap<String, PresetLocation>(), SettingsCodec.decodeKeywordLocations("v1"))
    }

    @Test
    fun `malformed keyword location tables are rejected`() {
        assertNull(SettingsCodec.decodeKeywordLocations("v2\nhome=40.7,-74.0,100"))
        assertNull(SettingsCodec.decodeKeywordLocations("v1\nhome=95.0,-74.0,100"))
        assertNull(SettingsCodec.decodeKeywordLocations("v1\nhome=40.7,-74.0"))
    }

    @Test
    fun `malformed and future versions are rejected`() {
        assertNull(SettingsCodec.decodeKeywordTimes("v2\nmorning=08:00"))
        assertNull(SettingsCodec.decodeKeywordTimes("v1\nmorning=25:00"))
        assertNull(SettingsCodec.decodeKeywordTimes("v1\nmissing-time"))
    }

    @Test
    fun `invalid stored radius falls back to documented default`() {
        assertEquals(100.0, SettingsCodec.decodeRadius(null), 0.0)
        assertEquals(100.0, SettingsCodec.decodeRadius("NaN"), 0.0)
        assertEquals(100.0, SettingsCodec.decodeRadius("0"), 0.0)
        assertEquals(250.5, SettingsCodec.decodeRadius("250.5"), 0.0)
    }

    @Test
    fun `sort order decodes stored field and direction with safe defaults`() {
        assertEquals(
            ReminderSortOrder(
                field = ReminderSortField.TITLE,
                direction = ReminderSortDirection.DESCENDING,
            ),
            SettingsCodec.decodeSortOrder("TITLE", "DESCENDING"),
        )
        assertEquals(
            ReminderSortOrder.DEFAULT,
            SettingsCodec.decodeSortOrder("unknown", "unknown"),
        )
    }
}
