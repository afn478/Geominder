package com.afn478.geominder.parser

import com.afn478.geominder.localization.SupportedLanguage
import com.afn478.geominder.localization.SystemLanguageProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale

class ReminderTextParserFactoryTest {
    @Test
    fun `factory selects the first supported system language`() {
        val factory = ReminderTextParserFactory(
            object : SystemLanguageProvider {
                override fun locales(): List<Locale> = listOf(
                    Locale.forLanguageTag("xx"),
                    Locale.FRANCE,
                    Locale.JAPAN,
                )
            },
        )

        val parser = factory.create()

        assertEquals(SupportedLanguage.FRENCH, factory.activeLanguage)
        assertEquals(SupportedLanguage.FRENCH, parser.language)
        assertEquals(LocalTime.of(8, 0), parser.keywordTimes["matin"])
        assertFalse(parser.keywordTimes.containsKey("morning"))
    }

    @Test
    fun `factory parser keeps localized modifier ordering`() {
        val parser = ReminderTextParserFactory(
            object : SystemLanguageProvider {
                override fun locales(): List<Locale> = listOf(Locale.JAPAN)
            },
        ).create()

        val result = parser.parse(
            "午前8時30分",
            ParseContext(
                now = Instant.parse("2026-08-24T10:15:00Z"),
                zoneId = ZoneOffset.UTC,
                locale = Locale.JAPAN,
            ),
        )

        assertEquals(LocalTime.of(8, 30), result.dateTime?.time)
    }
}
