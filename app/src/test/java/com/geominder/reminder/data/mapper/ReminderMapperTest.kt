package com.geominder.reminder.data.mapper

import com.geominder.reminder.data.local.entity.ReminderWithTriggers
import com.geominder.reminder.domain.model.GeoTrigger
import com.geominder.reminder.domain.model.Reminder
import com.geominder.reminder.domain.model.ReminderId
import com.geominder.reminder.domain.model.ReminderStatus
import com.geominder.reminder.domain.model.TimeTrigger
import com.geominder.reminder.domain.model.TriggerId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ReminderMapperTest {
    @Test
    fun `round trip preserves complete aggregate`() {
        val reminder = Reminder(
            id = ReminderId("reminder-1"),
            sourceText = "At 8 remind me near the station",
            title = "Catch the train",
            text = "Platform 4",
            enabled = true,
            status = ReminderStatus.SNOOZED,
            timeTrigger = TimeTrigger(
                id = TriggerId("time-1"),
                exactAt = Instant.parse("2026-09-01T06:00:00Z"),
            ),
            geoTrigger = GeoTrigger(
                id = TriggerId("geo-1"),
                latitude = 40.7505,
                longitude = -73.9934,
                radiusMeters = 125.5,
                label = "Penn Station",
                activeFrom = Instant.parse("2026-09-01T05:30:00Z"),
            ),
            createdAt = Instant.parse("2026-08-24T08:00:00Z"),
            updatedAt = Instant.parse("2026-08-24T09:00:00Z"),
            lastTriggeredAt = Instant.parse("2026-08-24T08:30:00Z"),
            snoozedUntil = Instant.parse("2026-08-24T09:30:00Z"),
        )

        val entities = reminder.toEntities()
        val roundTripped = ReminderWithTriggers(
            reminder = entities.reminder,
            timeTrigger = entities.timeTrigger,
            geoTrigger = entities.geoTrigger,
        ).toDomain()

        assertEquals(reminder, roundTripped)
    }
}
