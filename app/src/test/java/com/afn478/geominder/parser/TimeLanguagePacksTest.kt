package com.afn478.geominder.parser

import com.afn478.geominder.localization.SupportedLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale

class TimeLanguagePacksTest {
    private val now = Instant.parse("2026-08-24T10:15:00Z")

    @Test
    fun `European day parts after the hour are parsed as modifiers`() {
        val examples = listOf(
            SupportedLanguage.GERMAN to ("Termin um 8 Uhr morgens" to LocalTime.of(8, 0)),
            SupportedLanguage.FRENCH to ("Rendez-vous à 8 heures du matin" to LocalTime.of(8, 0)),
            SupportedLanguage.ITALIAN to ("Appuntamento alle 8 di sera" to LocalTime.of(20, 0)),
            SupportedLanguage.SPANISH to ("Llamar a las 8 de la noche" to LocalTime.of(20, 0)),
            SupportedLanguage.RUSSIAN to ("Позвонить в 8 вечера" to LocalTime.of(20, 0)),
        )

        examples.forEach { (language, example) ->
            assertEquals(
                "$language should parse its after-hour day part",
                example.second,
                parser(language).parse(example.first, context(language)).dateTime?.time,
            )
        }
    }

    @Test
    fun `CJK day parts before the hour are parsed as modifiers`() {
        val examples = listOf(
            SupportedLanguage.JAPANESE to ("午前8時30分" to LocalTime.of(8, 30)),
            SupportedLanguage.CHINESE to ("下午3点20分" to LocalTime.of(15, 20)),
            SupportedLanguage.KOREAN to ("오후 3시 20분" to LocalTime.of(15, 20)),
        )

        examples.forEach { (language, example) ->
            assertEquals(
                "$language should parse its before-hour day part",
                example.second,
                parser(language).parse(example.first, context(language)).dateTime?.time,
            )
        }
    }

    @Test
    fun `Japanese and Korean half-hour forms retain the leading modifier`() {
        assertEquals(
            LocalTime.of(20, 30),
            parser(SupportedLanguage.JAPANESE)
                .parse("午後8時半", context(SupportedLanguage.JAPANESE))
                .dateTime
                ?.time,
        )
        assertEquals(
            LocalTime.of(15, 30),
            parser(SupportedLanguage.KOREAN)
                .parse("오후 3시 반", context(SupportedLanguage.KOREAN))
                .dateTime
                ?.time,
        )
    }

    @Test
    fun `English day parts after the hour are parsed as modifiers`() {
        assertEquals(
            LocalTime.of(20, 30),
            parser(SupportedLanguage.ENGLISH)
                .parse("Call at 8:30 in the evening", context(SupportedLanguage.ENGLISH))
                .dateTime
                ?.time,
        )
        assertEquals(
            LocalTime.of(5, 0),
            parser(SupportedLanguage.ENGLISH)
                .parse("Wake at 5 in the morning", context(SupportedLanguage.ENGLISH))
                .dateTime
                ?.time,
        )
    }

    @Test
    fun `explicit parser language controls lexical date names`() {
        val german = parser(SupportedLanguage.GERMAN).parse(
            "Termin nächsten Dienstag",
            context(SupportedLanguage.ENGLISH),
        )

        assertEquals(LocalTime.of(9, 0), german.dateTime?.time)
        assertEquals("2026-08-25", german.dateTime?.date.toString())
    }

    private fun parser(language: SupportedLanguage) = ReminderTextParser(language = language)

    private fun context(language: SupportedLanguage) = ParseContext(
        now = now,
        zoneId = ZoneOffset.UTC,
        locale = language.locale,
    )
}
