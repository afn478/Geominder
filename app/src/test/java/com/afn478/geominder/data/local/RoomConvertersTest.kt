package com.afn478.geominder.data.local

import com.afn478.geominder.domain.model.ReminderStatus
import com.afn478.geominder.domain.model.ReminderTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RoomConvertersTest {
    private val converters = RoomConverters()

    @Test
    fun `instant conversion is lossless at database precision`() {
        val instant = Instant.parse("2026-08-24T18:45:12.345Z")

        assertEquals(instant, converters.epochMillisToInstant(converters.instantToEpochMillis(instant)))
        assertNull(converters.instantToEpochMillis(null))
        assertNull(converters.epochMillisToInstant(null))
    }

    @Test
    fun `status conversion uses stable enum name`() {
        ReminderStatus.entries.forEach { status ->
            assertEquals(status, converters.nameToStatus(converters.statusToName(status)))
        }
    }

    @Test
    fun `tag conversion preserves nullable stable enum name`() {
        ReminderTag.entries.forEach { tag ->
            assertEquals(tag, converters.nameToTag(converters.tagToName(tag)))
        }
        assertNull(converters.tagToName(null))
        assertNull(converters.nameToTag(null))
    }
}
