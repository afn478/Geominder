package com.geominder.reminder.settings

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
}
