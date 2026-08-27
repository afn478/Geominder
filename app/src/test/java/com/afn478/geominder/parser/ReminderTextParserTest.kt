package com.afn478.geominder.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

class ReminderTextParserTest {
    private val utc = ZoneId.of("UTC")
    private val context = ParseContext(
        now = Instant.parse("2026-08-24T10:15:00Z"),
        zoneId = utc,
        locale = Locale.US,
    )

    @Test
    fun `tonight uses preset time and rolls a passed time to tomorrow`() {
        val result = ReminderTextParser().parse("Take medicine tonight", context.copy(
            now = Instant.parse("2026-08-24T21:00:00Z"),
        ))

        assertEquals(LocalDate.of(2026, 8, 25), result.dateTime?.date)
        assertEquals(LocalTime.of(20, 0), result.dateTime?.time)
        assertEquals(TemporalPrecision.TIME, result.dateTime?.precision)
        assertEquals("tonight", result.dateTime?.sourceLabel)
    }

    @Test
    fun `keyword override replaces the shipped default`() {
        val parser = ReminderTextParser(
            keywordOverrides = mapOf("  Tonight " to LocalTime.of(22, 30)),
        )

        val detection = parser.parse("Tonight call home", context).dateTime

        assertEquals(LocalTime.of(22, 30), detection?.time)
        assertEquals(Instant.parse("2026-08-24T22:30:00Z"), detection?.instant)
        assertEquals(LocalTime.of(8, 0), parser.keywordTimes["morning"])
    }

    @Test
    fun `settings can add a custom phrase to the keyword table`() {
        val parser = ReminderTextParser(
            keywordOverrides = mapOf("school run" to LocalTime.of(15, 45)),
        )

        assertEquals(
            LocalTime.of(15, 45),
            parser.parse("Leave for the school run", context).dateTime?.time,
        )
    }

    @Test
    fun `complete keyword table preserves removal of a shipped default`() {
        val parser = ReminderTextParser.fromCompleteKeywordTable(
            keywordTimes = mapOf("morning" to LocalTime.of(7, 30)),
        )

        assertEquals(mapOf("morning" to LocalTime.of(7, 30)), parser.keywordTimes)
        assertEquals(LocalTime.of(7, 30), parser.parse("Call in the morning", context).dateTime?.time)
        assertTrue(parser.parse("Take medicine tonight", context).detections.isEmpty())
    }

    @Test
    fun `empty complete keyword table disables all keyword detections`() {
        val parser = ReminderTextParser.fromCompleteKeywordTable(emptyMap())

        assertTrue(parser.keywordTimes.isEmpty())
        assertTrue(parser.parse("Morning evening tonight", context).detections.isEmpty())
    }

    @Test
    fun `temporal detection can be disabled while GPS detection remains enabled`() {
        val result = ReminderTextParser().parse(
            sourceText = "Meet tomorrow at 8 near 40.7128, -74.0060",
            context = context,
            detectTemporalExpressions = false,
        )

        assertNull(result.dateTime)
        assertNotNull(result.gps)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `GPS detection can be disabled independently`() {
        val result = ReminderTextParser().parse(
            sourceText = "Meet tomorrow near 40.7128, -74.0060",
            context = context,
            detectGpsExpressions = false,
        )

        assertNull(result.gps)
        assertNotNull(result.dateTime)
    }

    @Test
    fun `complete table normalizes keys and rejects normalized duplicates`() {
        val parser = ReminderTextParser.fromCompleteKeywordTable(
            mapOf("  AFTER   WORK " to LocalTime.of(17, 45)),
        )

        assertEquals(setOf("after work"), parser.keywordTimes.keys)
        assertEquals(
            LocalTime.of(17, 45),
            parser.parse("Call after work", context).dateTime?.time,
        )
        assertThrows(IllegalArgumentException::class.java) {
            KeywordTimeDictionary.fromCompleteTable(
                mapOf(
                    "After Work" to LocalTime.of(17, 0),
                    " after   work " to LocalTime.of(18, 0),
                ),
            )
        }
    }

    @Test
    fun `legacy dictionary constructor still treats values as overrides`() {
        val dictionary = KeywordTimeDictionary(
            overrides = mapOf(" Tonight " to LocalTime.of(23, 0)),
        )

        assertEquals(LocalTime.of(23, 0), dictionary.entries["tonight"])
        assertEquals(LocalTime.of(8, 0), dictionary.entries["morning"])
    }

    @Test
    fun `relative date combines with explicit twelve hour time`() {
        val detection = ReminderTextParser()
            .parse("Tomorrow at 8:45 pm submit report", context)
            .dateTime

        assertEquals(LocalDate.of(2026, 8, 25), detection?.date)
        assertEquals(LocalTime.of(20, 45), detection?.time)
        assertEquals(Instant.parse("2026-08-25T20:45:00Z"), detection?.instant)
        assertEquals(TemporalPrecision.DATE_TIME, detection?.precision)
    }

    @Test
    fun `ISO date and twenty four hour time parse exactly`() {
        val detection = ReminderTextParser()
            .parse("Renew permit 2026-09-03 07:05", context)
            .dateTime

        assertEquals(LocalDate.of(2026, 9, 3), detection?.date)
        assertEquals(LocalTime.of(7, 5), detection?.time)
        assertEquals(Instant.parse("2026-09-03T07:05:00Z"), detection?.instant)
    }

    @Test
    fun `ambiguous numeric dates follow locale ordering`() {
        val parser = ReminderTextParser()
        val us = parser.parse("Meet 03/04/2027", context.copy(locale = Locale.US))
        val swiss = parser.parse("Meet 03/04/2027", context.copy(locale = Locale.UK))

        assertEquals(LocalDate.of(2027, 3, 4), us.dateTime?.date)
        assertEquals(LocalDate.of(2027, 4, 3), swiss.dateTime?.date)
    }

    @Test
    fun `localized month names and weekdays are recognized`() {
        val german = context.copy(locale = Locale.GERMANY)
        val month = ReminderTextParser().parse("Termin am 25. Dezember 2026", german)
        val weekday = ReminderTextParser().parse("Termin nächsten Dienstag", german)

        assertEquals(LocalDate.of(2026, 12, 25), month.dateTime?.date)
        assertEquals(LocalDate.of(2026, 8, 25), weekday.dateTime?.date)
        assertEquals(
            LocalDate.of(2026, 8, 25),
            ReminderTextParser().parse("Meet Tuesday", context).dateTime?.date,
        )
    }

    @Test
    fun `relative durations resolve from the injected instant`() {
        val detection = ReminderTextParser().parse("Check oven in 90 minutes", context).dateTime

        assertEquals(Instant.parse("2026-08-24T11:45:00Z"), detection?.instant)
        assertEquals(TemporalPrecision.RELATIVE_DURATION, detection?.precision)
    }

    @Test
    fun `removes date and time spans without removing text between them`() {
        val result = ReminderTextParser().parse("Tomorrow call home at 8:00", context)

        assertEquals("call home", result.textWithoutTimeExpression())
    }

    @Test
    fun `twenty minute duration resolves from the fixed context instant`() {
        val result = ReminderTextParser().parse("Check oven in 20 minutes", context)

        assertEquals(Instant.parse("2026-08-24T10:35:00Z"), result.dateTime?.instant)
        assertEquals(TemporalPrecision.RELATIVE_DURATION, result.dateTime?.precision)
    }

    @Test
    fun `day month and next weekday resolve to upcoming dates`() {
        val dayMonth = ReminderTextParser().parse("Renew on 25.12", context).dateTime
        val nextMonday = ReminderTextParser().parse("Do it next monday", context).dateTime
        val friday = ReminderTextParser().parse("Send it on Friday", context).dateTime

        assertEquals(LocalDate.of(2026, 12, 25), dayMonth?.date)
        assertEquals(LocalDate.of(2026, 8, 31), nextMonday?.date)
        assertEquals(LocalDate.of(2026, 8, 28), friday?.date)
    }

    @Test
    fun `days duration resolves as a date`() {
        val detection = ReminderTextParser().parse("Check again in 3 days", context).dateTime

        assertEquals(LocalDate.of(2026, 8, 27), detection?.date)
        assertEquals(TemporalPrecision.DATE, detection?.precision)
    }

    @Test
    fun `default keyword table is loaded from the shipped resource`() {
        assertEquals(LocalTime.of(8, 0), KeywordTimeDictionary.DEFAULTS["morning"])
        assertEquals(LocalTime.of(20, 0), KeywordTimeDictionary.DEFAULTS["tonight"])
        assertEquals(KeywordTimeDictionary.DEFAULTS, ReminderTextParser().keywordTimes)
    }

    @Test
    fun `relative duration beats a lower confidence keyword conflict`() {
        val detection = ReminderTextParser()
            .parse("Check the oven in 90 minutes this evening", context)
            .dateTime

        assertEquals(Instant.parse("2026-08-24T11:45:00Z"), detection?.instant)
        assertEquals(TemporalPrecision.RELATIVE_DURATION, detection?.precision)
    }

    @Test
    fun `an hour introduced by at is accepted without matching ordinary numbers`() {
        val parsed = ReminderTextParser().parse("Call at 9 about invoice 42", context)
        val unrelated = ReminderTextParser().parse("Invoice 42", context)

        assertEquals(LocalTime.of(9, 0), parsed.dateTime?.time)
        assertTrue(unrelated.detections.isEmpty())
    }

    @Test
    fun `time only resolution uses the provided timezone for rollover`() {
        val newYork = ParseContext(
            now = Instant.parse("2026-08-24T23:30:00Z"),
            zoneId = ZoneId.of("America/New_York"),
            locale = Locale.US,
        )

        val sameDay = ReminderTextParser().parse("Call at 5 pm", newYork).dateTime
        val nextDay = ReminderTextParser().parse("Call at 4 pm", newYork).dateTime

        assertEquals(Instant.parse("2026-08-25T21:00:00Z"), sameDay?.instant)
        assertEquals(Instant.parse("2026-08-25T20:00:00Z"), nextDay?.instant)
    }

    @Test
    fun `DST gap is advanced by the timezone transition`() {
        val newYork = context.copy(
            now = Instant.parse("2026-03-01T10:00:00Z"),
            zoneId = ZoneId.of("America/New_York"),
        )

        val detection = ReminderTextParser().parse("Wake 2026-03-08 at 02:30", newYork).dateTime

        assertEquals(LocalTime.of(3, 30), detection?.time)
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), detection?.instant)
    }

    @Test
    fun `labeled and plain coordinate pairs are detected`() {
        val parser = ReminderTextParser()
        val labeled = parser.parse("Alert at latitude: 40.7580, longitude: -73.9855", context).gps
        val plain = parser.parse("Arrive at 40.7128, -74.0060", context).gps

        assertEquals(40.7580, labeled?.latitude ?: 0.0, 0.000001)
        assertEquals(-73.9855, labeled?.longitude ?: 0.0, 0.000001)
        assertEquals(40.7128, plain?.latitude ?: 0.0, 0.000001)
        assertEquals(-74.0060, plain?.longitude ?: 0.0, 0.000001)
    }

    @Test
    fun `coordinate hemispheres override numeric signs`() {
        val gps = ReminderTextParser()
            .parse("Go to lat -40.7128 N, lon 74.0060 W", context)
            .gps

        assertEquals(40.7128, gps?.latitude ?: 0.0, 0.0)
        assertEquals(-74.0060, gps?.longitude ?: 0.0, 0.0)
    }

    @Test
    fun `explicit longitude latitude order is accepted`() {
        val gps = ReminderTextParser()
            .parse("Meet at longitude -74.0060 latitude 40.7128", context)
            .gps

        assertEquals(40.7128, gps?.latitude ?: 0.0, 0.000001)
        assertEquals(-74.0060, gps?.longitude ?: 0.0, 0.000001)
    }

    @Test
    fun `date time and GPS can coexist`() {
        val result = ReminderTextParser().parse(
            "Tomorrow morning at 40.7128, -74.0060",
            context,
        )

        assertNotNull(result.dateTime)
        assertNotNull(result.gps)
        assertEquals(2, result.detections.size)
    }

    @Test
    fun `keyword after GPS is geo active while keyword before GPS remains reminder trigger`() {
        val afterGps = ReminderTextParser().parse(
            "Arrive at 40.7128, -74.0060 tonight",
            context,
        )
        val beforeGps = ReminderTextParser().parse(
            "Tonight remind me at 40.7128, -74.0060",
            context,
        )

        assertEquals(TemporalRole.GEO_ACTIVE_FROM, afterGps.dateTime?.role)
        assertEquals(TemporalRole.REMINDER_TRIGGER, beforeGps.dateTime?.role)
    }

    @Test
    fun `from introduces geo active preset after GPS`() {
        val detection = ReminderTextParser().parse(
            "Arrive at 40.7128, -74.0060 from tonight",
            context,
        ).dateTime

        assertEquals(TemporalRole.GEO_ACTIVE_FROM, detection?.role)
    }

    @Test
    fun `from introduces geo active absolute date and time before GPS`() {
        val detection = ReminderTextParser().parse(
            "From tomorrow at 09:00, remind me at 40.7128, -74.0060",
            context,
        ).dateTime

        assertEquals(Instant.parse("2026-08-25T09:00:00Z"), detection?.instant)
        assertEquals(TemporalRole.GEO_ACTIVE_FROM, detection?.role)
    }

    @Test
    fun `unrelated from does not change temporal role`() {
        val detection = ReminderTextParser().parse(
            "From New York, tonight remind me at 40.7128, -74.0060",
            context,
        ).dateTime

        assertEquals(TemporalRole.REMINDER_TRIGGER, detection?.role)
    }

    @Test
    fun `absolute date wins a deterministic conflict with relative date`() {
        val result = ReminderTextParser().parse(
            "Tomorrow or 2026-09-10 at 08:00",
            context,
        )

        assertEquals(LocalDate.of(2026, 9, 10), result.dateTime?.date)
    }

    @Test
    fun `edited chip values produce a new result with stable IDs`() {
        val original = ReminderTextParser().parse(
            "Tomorrow at 8 am near 40.7128, -74.0060",
            context,
        )
        val temporalId = requireNotNull(original.dateTime).id
        val gpsId = requireNotNull(original.gps).id

        val edited = original
            .applyEdit(
                DetectionEdit.DateTime(
                    detectionId = temporalId,
                    date = LocalDate.of(2026, 8, 30),
                    time = LocalTime.of(18, 15),
                ),
            )
            .applyEdit(
                DetectionEdit.Gps(
                    detectionId = gpsId,
                    latitude = 40.7580,
                    longitude = -73.9855,
                ),
            )

        assertEquals(temporalId, edited.dateTime?.id)
        assertEquals(Instant.parse("2026-08-30T18:15:00Z"), edited.dateTime?.instant)
        assertEquals(40.7580, edited.gps?.latitude ?: 0.0, 0.0)
        assertEquals(LocalDate.of(2026, 8, 25), original.dateTime?.date)
    }

    @Test
    fun `blank unrelated and invalid input do not produce detections`() {
        val parser = ReminderTextParser()
        val blank = parser.parse("   ", context)
        val unrelated = parser.parse("Buy milk", context)
        val invalid = parser.parse("Meet 2026-02-30 at 27:88 latitude 95 longitude 200", context)

        assertTrue(blank.detections.isEmpty())
        assertTrue(unrelated.detections.isEmpty())
        assertTrue(invalid.detections.isEmpty())
        assertEquals(3, invalid.issues.size)
    }

    @Test
    fun `ambiguous and malformed temporal wording remains unmatched`() {
        val parser = ReminderTextParser()
        val sometimeLater = parser.parse("Do it sometime later", context)
        val invoiceNumber = parser.parse("Invoice 42", context)
        val malformed = parser.parse("Remind me in minutes", context)

        assertTrue(sometimeLater.detections.isEmpty())
        assertTrue(invoiceNumber.detections.isEmpty())
        assertTrue(malformed.detections.isEmpty())
    }

    @Test
    fun `mismatched or unknown edits are rejected`() {
        val result = ReminderTextParser().parse("Tomorrow", context)

        assertThrows(IllegalArgumentException::class.java) {
            result.applyEdit(DetectionEdit.Gps(requireNotNull(result.dateTime).id, 40.7128, -74.0060))
        }
        assertThrows(IllegalArgumentException::class.java) {
            result.applyEdit(DetectionEdit.DateTime("missing", LocalDate.now(), LocalTime.NOON))
        }
    }
}
