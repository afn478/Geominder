package com.afn478.geominder.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReminderTest {
    private val now = Instant.parse("2026-08-24T08:00:00Z")

    @Test
    fun `pending reflects both lifecycle and enabled switch`() {
        val reminder = reminder()

        assertTrue(reminder.isPending)
        assertFalse(reminder.copy(enabled = false).isPending)
        assertFalse(
            reminder.copy(
                enabled = false,
                status = ReminderStatus.DISMISSED,
                dismissedAt = now,
            ).isPending,
        )
    }

    @Test
    fun `reminder rejects an aggregate without triggers`() {
        assertThrows(IllegalArgumentException::class.java) {
            reminder().copy(timeTrigger = null)
        }
    }

    @Test
    fun `geo trigger validates coordinates and radius`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeoTrigger(latitude = 91.0, longitude = -74.0, radiusMeters = 100.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GeoTrigger(latitude = 40.7128, longitude = -74.0060, radiusMeters = Double.NaN)
        }
    }

    private fun reminder() = Reminder(
        id = ReminderId("reminder-1"),
        sourceText = "Tonight take medicine",
        title = "Take medicine",
        text = "",
        timeTrigger = TimeTrigger(
            id = TriggerId("time-1"),
            exactAt = now.plusSeconds(3_600),
        ),
        createdAt = now,
    )
}
