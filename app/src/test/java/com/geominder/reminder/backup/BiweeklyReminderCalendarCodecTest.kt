package com.geominder.reminder.backup

import biweekly.Biweekly
import biweekly.component.VAlarm
import com.geominder.reminder.domain.model.GeoTrigger
import com.geominder.reminder.domain.model.Reminder
import com.geominder.reminder.domain.model.ReminderId
import com.geominder.reminder.domain.model.ReminderStatus
import com.geominder.reminder.domain.model.TimeTrigger
import com.geominder.reminder.domain.model.TriggerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant

class BiweeklyReminderCalendarCodecTest {
    private val codec = BiweeklyReminderCalendarCodec()

    @Test
    fun `combined reminder round trips with alarms location metadata and escaped text`() {
        val reminder = Reminder(
            id = ReminderId("reminder-1@example.test"),
            sourceText = "Buy milk, bread; and a backslash \\ after work\nUse the side door",
            title = "A deliberately long reminder title that causes the iCalendar writer " +
                "to fold this content line safely",
            text = "Milk, bread; eggs \\ cheese\nSecond line",
            enabled = false,
            status = ReminderStatus.SNOOZED,
            timeTrigger = TimeTrigger(
                id = TriggerId("time-alarm-1"),
                exactAt = Instant.parse("2026-08-25T18:30:00Z"),
            ),
            geoTrigger = GeoTrigger(
                id = TriggerId("geo-alarm-1"),
                latitude = 40.7505,
                longitude = -73.9934,
                radiusMeters = 125.5,
                label = "Penn Station, main hall",
                activeFrom = Instant.parse("2026-08-25T17:00:00Z"),
            ),
            createdAt = Instant.parse("2026-08-24T08:00:00Z"),
            updatedAt = Instant.parse("2026-08-24T09:15:00Z"),
            lastTriggeredAt = Instant.parse("2026-08-24T09:00:00Z"),
            snoozedUntil = Instant.parse("2026-08-25T18:30:00Z"),
        )

        val bytes = encode(reminder)
        val text = bytes.toString(Charsets.UTF_8)
        assertTrue("Expected RFC line folding", text.contains("\r\n "))

        val parsedByBiweekly = Biweekly.parse(ByteArrayInputStream(bytes)).first()
        val todo = parsedByBiweekly.todos.single()
        assertEquals(2, todo.alarms.size)
        val proximityAlarm = todo.alarms.single { alarm ->
            alarm.getExperimentalProperty("PROXIMITY")?.value == "ARRIVE"
        }
        val location = proximityAlarm.getExperimentalComponents("VLOCATION").single()
        assertEquals(
            "geo:40.7505,-73.9934;u=125.5",
            location.getProperty(biweekly.property.Url::class.java).value,
        )

        val decoded = codec.decode(
            ByteArrayInputStream(bytes),
            importedAt = Instant.parse("2030-01-01T00:00:00Z"),
        )
        assertEquals(1, decoded.totalTodos)
        assertTrue(decoded.issues.none { it.severity == CalendarImportIssueSeverity.ERROR })
        assertEquals(reminder, decoded.reminders.single())
    }

    @Test
    fun `representative RFC 9074 arrival alarm imports through biweekly`() {
        val text = calendar(
            """
            BEGIN:VTODO
            UID:rfc-9074-task
            DTSTAMP:20260824T100000Z
            CREATED:20260824T090000Z
            LAST-MODIFIED:20260824T100000Z
            SUMMARY:Buy milk
            DESCRIPTION:Remember to buy milk
            X-SOURCE-TEXT:buy milk when I arrive at the office
            X-ACTIVE-FROM-TIME:2026-08-24T16:00:00Z
            BEGIN:VALARM
            UID:77D80D14-906B-4257-963F-85B1E734DBB6
            ACTION:DISPLAY
            TRIGGER;VALUE=DATE-TIME:19760401T005545Z
            DESCRIPTION:Remember to buy milk
            PROXIMITY:ARRIVE
            BEGIN:VLOCATION
            UID:123456-abcdef-98765432
            NAME:Office
            URL:geo:40.758,-73.9855;u=10
            END:VLOCATION
            END:VALARM
            END:VTODO
            """,
        )

        val result = codec.decode(
            text.byteInputStream(),
            importedAt = Instant.parse("2030-01-01T00:00:00Z"),
        )

        assertEquals(1, result.reminders.size)
        val reminder = result.reminders.single()
        assertEquals(ReminderId("rfc-9074-task"), reminder.id)
        assertEquals("buy milk when I arrive at the office", reminder.sourceText)
        assertEquals(null, reminder.timeTrigger)
        assertEquals(40.758, reminder.geoTrigger?.latitude ?: 0.0, 0.0)
        assertEquals(-73.9855, reminder.geoTrigger?.longitude ?: 0.0, 0.0)
        assertEquals(10.0, reminder.geoTrigger?.radiusMeters ?: 0.0, 0.0)
        assertEquals("Office", reminder.geoTrigger?.label)
        assertEquals(
            Instant.parse("2026-08-24T16:00:00Z"),
            reminder.geoTrigger?.activeFrom,
        )
    }

    @Test
    fun `relative VALARM trigger is resolved against DTSTART`() {
        val text = calendar(
            """
            BEGIN:VTODO
            UID:relative-time-task
            DTSTAMP:20260824T100000Z
            SUMMARY:Call home
            DESCRIPTION:Call home before boarding
            DTSTART:20260824T183000Z
            BEGIN:VALARM
            ACTION:DISPLAY
            DESCRIPTION:Call home
            TRIGGER:-PT10M
            END:VALARM
            END:VTODO
            """,
        )

        val result = codec.decode(text.byteInputStream(), Instant.EPOCH)

        assertEquals(
            Instant.parse("2026-08-24T18:20:00Z"),
            result.reminders.single().timeTrigger?.exactAt,
        )
    }

    @Test
    fun `malformed todo components are skipped and reported independently`() {
        val text = calendar(
            """
            BEGIN:VTODO
            DTSTAMP:20260824T100000Z
            SUMMARY:Missing UID
            BEGIN:VALARM
            ACTION:DISPLAY
            DESCRIPTION:Bad
            TRIGGER;VALUE=DATE-TIME:20260825T100000Z
            END:VALARM
            END:VTODO
            BEGIN:VTODO
            UID:bad-location
            DTSTAMP:20260824T100000Z
            SUMMARY:Bad location
            BEGIN:VALARM
            ACTION:DISPLAY
            DESCRIPTION:Bad location
            TRIGGER;VALUE=DATE-TIME:19760401T005545Z
            PROXIMITY:ARRIVE
            BEGIN:VLOCATION
            UID:bad-location-component
            NAME:Nowhere
            URL:geo:200,-74.0;u=-1
            END:VLOCATION
            END:VALARM
            END:VTODO
            BEGIN:VTODO
            UID:valid-time
            DTSTAMP:20260824T100000Z
            SUMMARY:Valid
            BEGIN:VALARM
            ACTION:DISPLAY
            DESCRIPTION:Valid
            TRIGGER;VALUE=DATE-TIME:20260825T100000Z
            END:VALARM
            END:VTODO
            """,
        )

        val result = codec.decode(text.byteInputStream(), Instant.EPOCH)

        assertEquals(3, result.totalTodos)
        assertEquals(listOf("valid-time"), result.reminders.map { it.id.value })
        assertTrue(result.issues.any { it.code == "MISSING_UID" && it.componentIndex == 0 })
        assertTrue(result.issues.any { it.code == "INVALID_GEO_URI" && it.componentIndex == 1 })
    }

    @Test
    fun `import byte limit fails before an unbounded document is parsed`() {
        val limitedCodec = BiweeklyReminderCalendarCodec(CalendarImportLimits(maxBytes = 20))
        val input = ByteArrayInputStream("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n".toByteArray())

        val error = runCatching { limitedCodec.decode(input, Instant.EPOCH) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is CalendarImportLimitException)
    }

    private fun encode(reminder: Reminder): ByteArray = ByteArrayOutputStream().use { output ->
        codec.encode(listOf(reminder), output)
        output.toByteArray()
    }

    private fun calendar(components: String): String = buildString {
        append("BEGIN:VCALENDAR\r\n")
        append("VERSION:2.0\r\n")
        append("PRODID:-//Tests//Geominder//EN\r\n")
        append(components.trimIndent().replace("\n", "\r\n"))
        append("\r\nEND:VCALENDAR\r\n")
    }
}
