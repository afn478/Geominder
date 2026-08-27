package com.afn478.geominder.parser

import com.afn478.geominder.localization.SupportedLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale

class TimeLanguagePacksTest {
    private val now = Instant.parse("2026-08-24T10:15:00Z")

    private data class ClockExample(
        val language: SupportedLanguage,
        val expression: String,
        val expected: LocalTime,
    )

    private data class RelativeDurationExample(
        val language: SupportedLanguage,
        val expression: String,
        val expected: Instant,
    )

    private data class RelativeDayExample(
        val language: SupportedLanguage,
        val expression: String,
        val days: Long,
    )

    @Test
    fun `hour and minute clock forms are parsed for every language`() {
        val examples = listOf(
            ClockExample(SupportedLanguage.ENGLISH, "at 8:05 pm", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.GERMAN, "um 8:05 Uhr morgens", LocalTime.of(8, 5)),
            ClockExample(SupportedLanguage.FRENCH, "à 8:05 du matin", LocalTime.of(8, 5)),
            ClockExample(SupportedLanguage.ITALIAN, "alle 8:05 di sera", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.SPANISH, "a las 8:05 de la tarde", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.RUSSIAN, "в 8:05 вечера", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.JAPANESE, "午後8時05分", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.CHINESE, "下午3点05分", LocalTime.of(15, 5)),
            ClockExample(SupportedLanguage.KOREAN, "오후 3시 05분", LocalTime.of(15, 5)),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should parse its hour and minute form", example.expected, detection?.time)
            assertEquals(example.expression, detection?.sourceLabel)
        }

    }

    @Test
    fun `localized minute placement and separators are parsed`() {
        val examples = listOf(
            ClockExample(SupportedLanguage.ENGLISH, "at 8:05 p.m.", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.GERMAN, "um 8 Uhr 05 morgens", LocalTime.of(8, 5)),
            ClockExample(SupportedLanguage.GERMAN, "um 8Uhr05 morgens", LocalTime.of(8, 5)),
            ClockExample(SupportedLanguage.FRENCH, "à 8 h 05 du matin", LocalTime.of(8, 5)),
            ClockExample(SupportedLanguage.FRENCH, "à 8h05 du matin", LocalTime.of(8, 5)),
            ClockExample(SupportedLanguage.ITALIAN, "alle 8 ore 05 di sera", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.SPANISH, "a las 8 h 05 de la tarde", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.SPANISH, "a las 8h05 de la tarde", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.RUSSIAN, "в 8 часов 05 вечера", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.RUSSIAN, "в 8ч05 вечера", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.JAPANESE, "午後 8 時 05 分", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.CHINESE, "下午 3 点 05 分", LocalTime.of(15, 5)),
            ClockExample(SupportedLanguage.CHINESE, "下午三点钟五分", LocalTime.of(15, 5)),
            ClockExample(SupportedLanguage.KOREAN, "오후 3 시 05 분", LocalTime.of(15, 5)),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should parse its localized minute placement", example.expected, detection?.time)
            assertEquals(example.expression, detection?.sourceLabel)
        }

        assertEquals(
            LocalTime.of(8, 5),
            parser(SupportedLanguage.GERMAN)
                .parse("8.05", context(SupportedLanguage.GERMAN))
                .dateTime
                ?.time,
        )
        assertEquals(
            "at 8.05",
            parser(SupportedLanguage.ENGLISH)
                .parse("at 8.05", context(SupportedLanguage.ENGLISH))
                .dateTime
                ?.sourceLabel,
        )
    }

    @Test
    fun `standalone localized clock units retain trailing day parts`() {
        val examples = listOf(
            ClockExample(SupportedLanguage.FRENCH, "8 heures du soir", LocalTime.of(20, 0)),
            ClockExample(SupportedLanguage.FRENCH, "8 heures 05 du soir", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.FRENCH, "8 heures le soir", LocalTime.of(20, 0)),
            ClockExample(SupportedLanguage.ITALIAN, "8 ore di sera", LocalTime.of(20, 0)),
            ClockExample(SupportedLanguage.ITALIAN, "8 ore 05 di sera", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.ITALIAN, "8 ore sera", LocalTime.of(20, 0)),
            ClockExample(SupportedLanguage.SPANISH, "8 horas de la tarde", LocalTime.of(20, 0)),
            ClockExample(SupportedLanguage.SPANISH, "8 horas 05 de la tarde", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.SPANISH, "8 horas por la tarde", LocalTime.of(20, 0)),
            ClockExample(SupportedLanguage.RUSSIAN, "8 часов вечера", LocalTime.of(20, 0)),
            ClockExample(SupportedLanguage.RUSSIAN, "8 часов 05 вечера", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.RUSSIAN, "8 часов вечером", LocalTime.of(20, 0)),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should retain its day part", example.expected, detection?.time)
            assertEquals(example.expression, detection?.sourceLabel)
        }
    }

    @Test
    fun `leading European day parts modify complete clocks`() {
        val examples = listOf(
            ClockExample(SupportedLanguage.ENGLISH, "in the evening at 8:05", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.GERMAN, "abends um 8 Uhr 05", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.FRENCH, "le soir à 8 h 05", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.ITALIAN, "di sera alle 8 ore 05", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.SPANISH, "por la tarde a las 8 h 05", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.RUSSIAN, "вечером в 8 часов 05", LocalTime.of(20, 5)),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should apply its leading day part", example.expected, detection?.time)
            assertEquals(example.expression, detection?.sourceLabel)
        }
    }

    @Test
    fun `standalone localized day parts beat a shorter numeric clock`() {
        val examples = listOf(
            ClockExample(SupportedLanguage.GERMAN, "8:05 abends", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.GERMAN, "8：05 abends", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.FRENCH, "8:05 du soir", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.ITALIAN, "8:05 di sera", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.SPANISH, "8:05 de la tarde", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.RUSSIAN, "8:05 вечера", LocalTime.of(20, 5)),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should keep its day part", example.expected, detection?.time)
            assertEquals(example.expression, detection?.sourceLabel)
        }
    }

    @Test
    fun `Spanish morning day part does not become a relative date`() {
        val detection = parser(SupportedLanguage.SPANISH)
            .parse(
                "8 horas de la mañana",
                context(SupportedLanguage.SPANISH).copy(
                    now = Instant.parse("2026-08-24T07:00:00Z"),
                ),
            )
            .dateTime

        assertEquals(LocalDate.of(2026, 8, 24), detection?.date)
        assertEquals(LocalTime.of(8, 0), detection?.time)
        assertEquals(TemporalPrecision.TIME, detection?.precision)
        assertEquals("8 horas de la mañana", detection?.sourceLabel)
    }

    @Test
    fun `highlighted source spans remain complete inside surrounding reminder text`() {
        val examples = listOf(
            SupportedLanguage.ENGLISH to "at 8:05 pm",
            SupportedLanguage.GERMAN to "um 8 Uhr abends",
            SupportedLanguage.FRENCH to "8 heures du soir",
            SupportedLanguage.ITALIAN to "8 ore di sera",
            SupportedLanguage.SPANISH to "8 horas de la tarde",
            SupportedLanguage.RUSSIAN to "8 часов вечера",
            SupportedLanguage.JAPANESE to "三時間後",
            SupportedLanguage.CHINESE to "三小时二十分钟后",
            SupportedLanguage.KOREAN to "3시간 20분 후",
        )

        examples.forEach { (language, expression) ->
            val source = "Reminder: $expression please"
            val detection = parser(language)
                .parse(source, context(language))
                .dateTime

            assertEquals("$language should preserve the complete source span", expression, detection?.sourceLabel)
            assertEquals(
                source.indexOf(expression),
                detection?.span?.start,
            )
            assertEquals(
                source.indexOf(expression) + expression.length,
                detection?.span?.endExclusive,
            )
        }
    }

    @Test
    fun `case-insensitive clock and duration forms retain their language aliases`() {
        val clockExamples = listOf(
            ClockExample(SupportedLanguage.ENGLISH, "AT 8:05 P.M.", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.GERMAN, "UM 8:05 UHR MORGENS", LocalTime.of(8, 5)),
            ClockExample(SupportedLanguage.FRENCH, "À 8:05 DU MATIN", LocalTime.of(8, 5)),
            ClockExample(SupportedLanguage.ITALIAN, "ALLE 8:05 DI SERA", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.SPANISH, "A LAS 8:05 DE LA TARDE", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.RUSSIAN, "В 8:05 ВЕЧЕРА", LocalTime.of(20, 5)),
        )
        val durationExamples = listOf(
            RelativeDurationExample(SupportedLanguage.ENGLISH, "IN 3 HOURS", now.plusSeconds(3 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.GERMAN, "IN 3 STUNDEN", now.plusSeconds(3 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.FRENCH, "DANS 3 HEURES", now.plusSeconds(3 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.ITALIAN, "TRA 3 ORE", now.plusSeconds(3 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.SPANISH, "EN 3 HORAS", now.plusSeconds(3 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.RUSSIAN, "ЧЕРЕЗ 3 ЧАСА", now.plusSeconds(3 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "ATO 3 JIKAN", now.plusSeconds(3 * 60 * 60)),
        )

        clockExamples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should parse upper-case clock text", example.expected, detection?.time)
        }
        durationExamples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should parse upper-case duration text", example.expected, detection?.instant)
        }
    }

    @Test
    fun `clock modifier aliases cover punctuation and accentless spellings`() {
        val examples = listOf(
            ClockExample(SupportedLanguage.ENGLISH, "8 pm.", LocalTime.of(20, 0)),
            ClockExample(SupportedLanguage.ENGLISH, "8 am.", LocalTime.of(8, 0)),
            ClockExample(SupportedLanguage.FRENCH, "8 heures de l’apres-midi", LocalTime.of(20, 0)),
            ClockExample(SupportedLanguage.SPANISH, "8 horas de la manana", LocalTime.of(8, 0)),
            ClockExample(SupportedLanguage.SPANISH, "8:05 de la manana", LocalTime.of(8, 5)),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should keep its modifier", example.expected, detection?.time)
            assertEquals(example.expression, detection?.sourceLabel)
        }
    }

    @Test
    fun `relative hour and minute durations are parsed for every language`() {
        val examples = listOf(
            RelativeDurationExample(
                SupportedLanguage.ENGLISH,
                "in 3 hours",
                now.plusSeconds(3 * 60 * 60),
            ),
            RelativeDurationExample(
                SupportedLanguage.GERMAN,
                "in 3 Stunden",
                now.plusSeconds(3 * 60 * 60),
            ),
            RelativeDurationExample(
                SupportedLanguage.FRENCH,
                "dans 3 heures",
                now.plusSeconds(3 * 60 * 60),
            ),
            RelativeDurationExample(
                SupportedLanguage.ITALIAN,
                "tra 3 ore",
                now.plusSeconds(3 * 60 * 60),
            ),
            RelativeDurationExample(
                SupportedLanguage.SPANISH,
                "en 3 horas",
                now.plusSeconds(3 * 60 * 60),
            ),
            RelativeDurationExample(
                SupportedLanguage.RUSSIAN,
                "через 3 часа",
                now.plusSeconds(3 * 60 * 60),
            ),
            RelativeDurationExample(
                SupportedLanguage.JAPANESE,
                "3時間後",
                now.plusSeconds(3 * 60 * 60),
            ),
            RelativeDurationExample(
                SupportedLanguage.CHINESE,
                "3小时后",
                now.plusSeconds(3 * 60 * 60),
            ),
            RelativeDurationExample(
                SupportedLanguage.KOREAN,
                "3시간 후",
                now.plusSeconds(3 * 60 * 60),
            ),
            RelativeDurationExample(
                SupportedLanguage.ENGLISH,
                "in 30 minutes",
                now.plusSeconds(30 * 60),
            ),
            RelativeDurationExample(
                SupportedLanguage.GERMAN,
                "in 30 Minuten",
                now.plusSeconds(30 * 60),
            ),
            RelativeDurationExample(
                SupportedLanguage.FRENCH,
                "dans 30 minutes",
                now.plusSeconds(30 * 60),
            ),
            RelativeDurationExample(
                SupportedLanguage.ITALIAN,
                "tra 30 minuti",
                now.plusSeconds(30 * 60),
            ),
            RelativeDurationExample(
                SupportedLanguage.SPANISH,
                "en 30 minutos",
                now.plusSeconds(30 * 60),
            ),
            RelativeDurationExample(
                SupportedLanguage.RUSSIAN,
                "через 30 минут",
                now.plusSeconds(30 * 60),
            ),
            RelativeDurationExample(
                SupportedLanguage.JAPANESE,
                "30分後",
                now.plusSeconds(30 * 60),
            ),
            RelativeDurationExample(
                SupportedLanguage.CHINESE,
                "30分钟后",
                now.plusSeconds(30 * 60),
            ),
            RelativeDurationExample(
                SupportedLanguage.KOREAN,
                "30분 후",
                now.plusSeconds(30 * 60),
            ),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should resolve ${example.expression}", example.expected, detection?.instant)
            assertEquals(TemporalPrecision.RELATIVE_DURATION, detection?.precision)
            assertEquals(example.expression, detection?.sourceLabel)
        }

    }

    @Test
    fun `combined hour and minute durations are parsed in every language`() {
        val expected = now.plusSeconds(3 * 60 * 60 + 20 * 60)
        val examples = listOf(
            RelativeDurationExample(SupportedLanguage.ENGLISH, "in 3 hours and 20 minutes", expected),
            RelativeDurationExample(SupportedLanguage.GERMAN, "in 3 Stunden und 20 Minuten", expected),
            RelativeDurationExample(SupportedLanguage.FRENCH, "dans 3 heures et 20 minutes", expected),
            RelativeDurationExample(SupportedLanguage.ITALIAN, "tra 3 ore e 20 minuti", expected),
            RelativeDurationExample(SupportedLanguage.SPANISH, "en 3 horas y 20 minutos", expected),
            RelativeDurationExample(SupportedLanguage.RUSSIAN, "через 3 часа и 20 минут", expected),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "あと3時間20分", expected),
            RelativeDurationExample(SupportedLanguage.CHINESE, "3小时20分钟后", expected),
            RelativeDurationExample(SupportedLanguage.KOREAN, "3시간 20분 후", expected),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should resolve ${example.expression}", example.expected, detection?.instant)
            assertEquals(TemporalPrecision.RELATIVE_DURATION, detection?.precision)
            assertEquals(example.expression, detection?.sourceLabel)
        }
    }

    @Test
    fun `abbreviated and alternate relative durations are parsed`() {
        val expected = now.plusSeconds(3 * 60 * 60 + 20 * 60)
        val examples = listOf(
            RelativeDurationExample(SupportedLanguage.ENGLISH, "in 3h20m", expected),
            RelativeDurationExample(SupportedLanguage.GERMAN, "in 3 Std. 20 Min.", expected),
            RelativeDurationExample(SupportedLanguage.FRENCH, "dans 3 h 20 min", expected),
            RelativeDurationExample(SupportedLanguage.ITALIAN, "tra 3h20min", expected),
            RelativeDurationExample(SupportedLanguage.SPANISH, "en 3h20min", expected),
            RelativeDurationExample(SupportedLanguage.RUSSIAN, "через 3 ч 20 мин", expected),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "ato 3 jikan 20 fun", expected),
            RelativeDurationExample(SupportedLanguage.CHINESE, "再过3小时20分钟", expected),
            RelativeDurationExample(SupportedLanguage.KOREAN, "앞으로 3시간 20분", expected),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should resolve ${example.expression}", example.expected, detection?.instant)
            assertEquals(TemporalPrecision.RELATIVE_DURATION, detection?.precision)
            assertEquals(example.expression, detection?.sourceLabel)
        }
    }

    @Test
    fun `compact hour-minute durations are parsed across supported languages`() {
        val expected = now.plusSeconds(3 * 60 * 60 + 20 * 60)
        val examples = listOf(
            RelativeDurationExample(SupportedLanguage.ENGLISH, "in 3h20", expected),
            RelativeDurationExample(SupportedLanguage.GERMAN, "in 3h20", expected),
            RelativeDurationExample(SupportedLanguage.FRENCH, "dans 3h20", expected),
            RelativeDurationExample(SupportedLanguage.ITALIAN, "tra 3h20", expected),
            RelativeDurationExample(SupportedLanguage.SPANISH, "en 3h20", expected),
            RelativeDurationExample(SupportedLanguage.RUSSIAN, "через 3ч20", expected),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "ato 3 jikan 20", expected),
            RelativeDurationExample(SupportedLanguage.CHINESE, "再过3小时20", expected),
            RelativeDurationExample(SupportedLanguage.KOREAN, "앞으로 3시간 20", expected),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should resolve ${example.expression}", example.expected, detection?.instant)
            assertEquals(TemporalPrecision.RELATIVE_DURATION, detection?.precision)
            assertEquals(example.expression, detection?.sourceLabel)
        }

        val withReminderText = parser(SupportedLanguage.ENGLISH)
            .parse("in 3h20 review the notes", context(SupportedLanguage.ENGLISH))
            .dateTime
        assertEquals(expected, withReminderText?.instant)
        assertEquals("in 3h20", withReminderText?.sourceLabel)

        val wordUnitShorthand = parser(SupportedLanguage.ENGLISH)
            .parse("in 3 hours, and 20 review the notes", context(SupportedLanguage.ENGLISH))
            .dateTime
        assertEquals(expected, wordUnitShorthand?.instant)
        assertEquals("in 3 hours, and 20", wordUnitShorthand?.sourceLabel)
    }

    @Test
    fun `bare numbers after minute units are not mistaken for minutes`() {
        val examples = listOf(
            SupportedLanguage.JAPANESE to "3分20後",
            SupportedLanguage.JAPANESE to "3 fun 20 ato",
            SupportedLanguage.CHINESE to "3分钟20后",
            SupportedLanguage.KOREAN to "3분20후",
        )

        examples.forEach { (language, expression) ->
            assertNull(
                "$language should require a unit for a second minute component",
                parser(language).parse(expression, context(language)).dateTime,
            )
        }
    }

    @Test
    fun `combined durations accept punctuation and reversed unit order`() {
        val expected = now.plusSeconds(3 * 60 * 60 + 20 * 60)
        val examples = listOf(
            RelativeDurationExample(SupportedLanguage.ENGLISH, "in 3 hours, and 20 minutes", expected),
            RelativeDurationExample(SupportedLanguage.GERMAN, "in 3 Stunden, und 20 Minuten", expected),
            RelativeDurationExample(SupportedLanguage.FRENCH, "dans 20 minutes et 3 heures", expected),
            RelativeDurationExample(SupportedLanguage.ITALIAN, "tra 20 minuti, e 3 ore", expected),
            RelativeDurationExample(SupportedLanguage.SPANISH, "en 20 minutos, y 3 horas", expected),
            RelativeDurationExample(SupportedLanguage.RUSSIAN, "через 20 минут, и 3 часа", expected),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "あと20分、3時間", expected),
            RelativeDurationExample(SupportedLanguage.CHINESE, "再过20分钟，和3小时", expected),
            RelativeDurationExample(SupportedLanguage.KOREAN, "앞으로 20분, 과 3시간", expected),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should resolve ${example.expression}", example.expected, detection?.instant)
            assertEquals(TemporalPrecision.RELATIVE_DURATION, detection?.precision)
            assertEquals(example.expression, detection?.sourceLabel)
        }
    }

    @Test
    fun `relative duration prefixes and suffixes cover localized alternatives`() {
        val threeHours = now.plusSeconds(3 * 60 * 60)
        val examples = listOf(
            RelativeDurationExample(SupportedLanguage.ENGLISH, "after 3 hours", threeHours),
            RelativeDurationExample(SupportedLanguage.ENGLISH, "3 hours from now", threeHours),
            RelativeDurationExample(SupportedLanguage.GERMAN, "nach 3 Stunden", threeHours),
            RelativeDurationExample(SupportedLanguage.FRENCH, "d’ici 3 heures", threeHours),
            RelativeDurationExample(SupportedLanguage.ITALIAN, "dopo 3 ore", threeHours),
            RelativeDurationExample(SupportedLanguage.SPANISH, "dentro de 3 horas", threeHours),
            RelativeDurationExample(SupportedLanguage.RUSSIAN, "спустя 3 часа", threeHours),
            RelativeDurationExample(SupportedLanguage.CHINESE, "再過3小時", threeHours),
            RelativeDurationExample(SupportedLanguage.KOREAN, "3시간 뒤에", threeHours),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should resolve ${example.expression}", example.expected, detection?.instant)
            assertEquals(TemporalPrecision.RELATIVE_DURATION, detection?.precision)
            assertEquals(example.expression, detection?.sourceLabel)
        }
    }

    @Test
    fun `singular hour and minute forms are parsed for every language`() {
        val examples = listOf(
            RelativeDurationExample(SupportedLanguage.ENGLISH, "in 1 hour", now.plusSeconds(60 * 60)),
            RelativeDurationExample(SupportedLanguage.GERMAN, "in 1 Stunde", now.plusSeconds(60 * 60)),
            RelativeDurationExample(SupportedLanguage.FRENCH, "dans 1 heure", now.plusSeconds(60 * 60)),
            RelativeDurationExample(SupportedLanguage.ITALIAN, "tra 1 ora", now.plusSeconds(60 * 60)),
            RelativeDurationExample(SupportedLanguage.SPANISH, "en 1 hora", now.plusSeconds(60 * 60)),
            RelativeDurationExample(SupportedLanguage.RUSSIAN, "через 1 час", now.plusSeconds(60 * 60)),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "1時間後", now.plusSeconds(60 * 60)),
            RelativeDurationExample(SupportedLanguage.CHINESE, "1小时后", now.plusSeconds(60 * 60)),
            RelativeDurationExample(SupportedLanguage.KOREAN, "1시간 후", now.plusSeconds(60 * 60)),
            RelativeDurationExample(SupportedLanguage.ENGLISH, "in 1 minute", now.plusSeconds(60)),
            RelativeDurationExample(SupportedLanguage.GERMAN, "in 1 Minute", now.plusSeconds(60)),
            RelativeDurationExample(SupportedLanguage.FRENCH, "dans 1 minute", now.plusSeconds(60)),
            RelativeDurationExample(SupportedLanguage.ITALIAN, "tra 1 minuto", now.plusSeconds(60)),
            RelativeDurationExample(SupportedLanguage.SPANISH, "en 1 minuto", now.plusSeconds(60)),
            RelativeDurationExample(SupportedLanguage.RUSSIAN, "через 1 минуту", now.plusSeconds(60)),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "1分後", now.plusSeconds(60)),
            RelativeDurationExample(SupportedLanguage.CHINESE, "1分钟后", now.plusSeconds(60)),
            RelativeDurationExample(SupportedLanguage.KOREAN, "1분 후", now.plusSeconds(60)),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should resolve ${example.expression}", example.expected, detection?.instant)
            assertEquals(TemporalPrecision.RELATIVE_DURATION, detection?.precision)
        }
    }

    @Test
    fun `Japanese native and romanized relative duration word orders are parsed`() {
        val examples = listOf(
            RelativeDurationExample(SupportedLanguage.JAPANESE, "あと3時間", now.plusSeconds(3 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "あと 30 分", now.plusSeconds(30 * 60)),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "3 時間 後", now.plusSeconds(3 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "3時間あと", now.plusSeconds(3 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "今から3時間後", now.plusSeconds(3 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "3 jikan ato", now.plusSeconds(3 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "3 jikan go", now.plusSeconds(3 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "ato 30 fun", now.plusSeconds(30 * 60)),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("Japanese should resolve ${example.expression}", example.expected, detection?.instant)
            assertEquals(TemporalPrecision.RELATIVE_DURATION, detection?.precision)
            assertEquals(example.expression, detection?.sourceLabel)
        }
    }

    @Test
    fun `CJK numerals are parsed as complete hour and minute amounts`() {
        val expected = now.plusSeconds(3 * 60 * 60 + 20 * 60)
        val examples = listOf(
            RelativeDurationExample(SupportedLanguage.JAPANESE, "三時間後", now.plusSeconds(3 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "三十分後", now.plusSeconds(30 * 60)),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "三時間二十分後", expected),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "二十三時間後", now.plusSeconds(23 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "十時間後", now.plusSeconds(10 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "一百二十分後", now.plusSeconds(120 * 60)),
            RelativeDurationExample(SupportedLanguage.JAPANESE, "あと三時間二十分", expected),
            RelativeDurationExample(SupportedLanguage.CHINESE, "三小时后", now.plusSeconds(3 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.CHINESE, "三十分钟后", now.plusSeconds(30 * 60)),
            RelativeDurationExample(SupportedLanguage.CHINESE, "三小时二十分钟后", expected),
            RelativeDurationExample(SupportedLanguage.CHINESE, "二十三小时后", now.plusSeconds(23 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.CHINESE, "十二小时后", now.plusSeconds(12 * 60 * 60)),
            RelativeDurationExample(SupportedLanguage.CHINESE, "一百二十分钟后", now.plusSeconds(120 * 60)),
            RelativeDurationExample(SupportedLanguage.CHINESE, "再过三小时二十分钟", expected),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should resolve ${example.expression}", example.expected, detection?.instant)
            assertEquals(TemporalPrecision.RELATIVE_DURATION, detection?.precision)
            assertEquals(example.expression, detection?.sourceLabel)
        }
    }

    @Test
    fun `CJK numeral clocks retain their full hour and minute tokens`() {
        val examples = listOf(
            SupportedLanguage.JAPANESE to ("午後三時五分" to LocalTime.of(15, 5)),
            SupportedLanguage.JAPANESE to ("二十三時" to LocalTime.of(23, 0)),
            SupportedLanguage.CHINESE to ("下午三点五分" to LocalTime.of(15, 5)),
            SupportedLanguage.CHINESE to ("二十三点" to LocalTime.of(23, 0)),
        )

        examples.forEach { (language, example) ->
            val detection = parser(language)
                .parse(example.first, context(language))
                .dateTime

            assertEquals("$language should resolve ${example.first}", example.second, detection?.time)
            assertEquals(example.first, detection?.sourceLabel)
        }
    }

    @Test
    fun `CJK day parts also qualify colon clocks`() {
        val examples = listOf(
            ClockExample(SupportedLanguage.JAPANESE, "午後8:05", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.CHINESE, "下午3:05", LocalTime.of(15, 5)),
            ClockExample(SupportedLanguage.KOREAN, "오후 3:05", LocalTime.of(15, 5)),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should keep its day part", example.expected, detection?.time)
            assertEquals(example.expression, detection?.sourceLabel)
        }
    }

    @Test
    fun `CJK numeral day offsets are parsed as complete amounts`() {
        val examples = listOf(
            RelativeDayExample(SupportedLanguage.JAPANESE, "三日後", 3),
            RelativeDayExample(SupportedLanguage.JAPANESE, "3日間後", 3),
            RelativeDayExample(SupportedLanguage.JAPANESE, "三日間後", 3),
            RelativeDayExample(SupportedLanguage.JAPANESE, "三週間後", 21),
            RelativeDayExample(SupportedLanguage.JAPANESE, "3週後", 21),
            RelativeDayExample(SupportedLanguage.CHINESE, "三天后", 3),
            RelativeDayExample(SupportedLanguage.CHINESE, "三周后", 21),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime
            val expectedDate = now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(example.days)

            assertEquals("${example.language} should resolve ${example.expression}", expectedDate, detection?.date)
            assertEquals(TemporalPrecision.DATE, detection?.precision)
            assertEquals(example.expression, detection?.sourceLabel)
        }
    }

    @Test
    fun `relative dates combine with localized hour and minute clocks`() {
        val expectedDate = now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
        val examples = listOf(
            ClockExample(SupportedLanguage.ENGLISH, "tomorrow at 8:05 pm", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.GERMAN, "morgen um 8:05 Uhr", LocalTime.of(8, 5)),
            ClockExample(SupportedLanguage.FRENCH, "demain à 8:05", LocalTime.of(8, 5)),
            ClockExample(SupportedLanguage.ITALIAN, "domani alle 8:05", LocalTime.of(8, 5)),
            ClockExample(SupportedLanguage.SPANISH, "mañana a las 8:05", LocalTime.of(8, 5)),
            ClockExample(SupportedLanguage.RUSSIAN, "завтра в 8:05", LocalTime.of(8, 5)),
            ClockExample(SupportedLanguage.JAPANESE, "明日午後8時05分", LocalTime.of(20, 5)),
            ClockExample(SupportedLanguage.CHINESE, "明天下午3点05分", LocalTime.of(15, 5)),
            ClockExample(SupportedLanguage.KOREAN, "내일 오후 3시 05분", LocalTime.of(15, 5)),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should combine its date and clock", LocalTime.of(
                example.expected.hour,
                example.expected.minute,
            ), detection?.time)
            assertEquals(expectedDate, detection?.date)
            assertEquals(TemporalPrecision.DATE_TIME, detection?.precision)
        }
    }

    @Test
    fun `a complete duration wins over a nested clock fragment and stops before reminder text`() {
        val expression = "dans 3 heures et 20 minutes vérifier"
        val detection = parser(SupportedLanguage.FRENCH)
            .parse(expression, context(SupportedLanguage.FRENCH))
            .dateTime

        assertEquals(now.plusSeconds(3 * 60 * 60 + 20 * 60), detection?.instant)
        assertEquals("dans 3 heures et 20 minutes", detection?.sourceLabel)
        assertEquals(TemporalPrecision.RELATIVE_DURATION, detection?.precision)
    }

    @Test
    fun `a separate explicit clock remains distinct from an unrelated duration`() {
        val detection = parser(SupportedLanguage.FRENCH)
            .parse("dans 3 heures, à 8:05", context(SupportedLanguage.FRENCH))
            .dateTime

        assertEquals(LocalTime.of(8, 5), detection?.time)
        assertEquals(TemporalPrecision.TIME, detection?.precision)
        assertEquals("à 8:05", detection?.sourceLabel)
    }

    @Test
    fun `an outside clock beats a nested unit-shaped clock`() {
        val detection = parser(SupportedLanguage.FRENCH)
            .parse("dans 3 heures, 8:05", context(SupportedLanguage.FRENCH))
            .dateTime

        assertEquals(LocalTime.of(8, 5), detection?.time)
        assertEquals(TemporalPrecision.TIME, detection?.precision)
        assertEquals("8:05", detection?.sourceLabel)
    }

    @Test
    fun `a numeric date is not combined with an overlapping dotted clock`() {
        val detection = parser(SupportedLanguage.GERMAN)
            .parse("08.05.2026", context(SupportedLanguage.GERMAN))
            .dateTime

        assertEquals(LocalDate.of(2026, 5, 8), detection?.date)
        assertEquals(TemporalPrecision.DATE, detection?.precision)
        assertEquals("08.05.2026", detection?.sourceLabel)
    }

    @Test
    fun `a separate dotted clock is not hidden by a date prefix`() {
        val expression = "08.05.2026 8.05"
        val detection = parser(SupportedLanguage.GERMAN)
            .parse(expression, context(SupportedLanguage.GERMAN))
            .dateTime

        assertEquals(LocalDate.of(2026, 5, 8), detection?.date)
        assertEquals(LocalTime.of(8, 5), detection?.time)
        assertEquals(TemporalPrecision.DATE_TIME, detection?.precision)
        assertEquals(expression, detection?.sourceLabel)
        assertEquals(
            listOf("08.05.2026", "8.05"),
            detection?.expressionSpans?.map { it.textFrom(expression) },
        )
    }

    @Test
    fun `yearless dotted dates and clocks can be combined in either order`() {
        val examples = listOf(
            "8.05 25.12" to (LocalDate.of(2026, 12, 25) to LocalTime.of(8, 5)),
            "25.12 8.05" to (LocalDate.of(2026, 12, 25) to LocalTime.of(8, 5)),
            "8.05 12.05" to (LocalDate.of(2027, 5, 8) to LocalTime.of(12, 5)),
        )

        examples.forEach { (expression, expected) ->
            val detection = parser(SupportedLanguage.GERMAN)
                .parse(expression, context(SupportedLanguage.GERMAN))
                .dateTime

            assertEquals(expected.first, detection?.date)
            assertEquals(expected.second, detection?.time)
            assertEquals(TemporalPrecision.DATE_TIME, detection?.precision)
            assertEquals(expression, detection?.sourceLabel)
        }
    }

    @Test
    fun `clock rules do not highlight a minute prefix inside seconds`() {
        val examples = listOf(
            SupportedLanguage.ENGLISH to "Call at 8:05:30",
            SupportedLanguage.JAPANESE to "午後8:05:30",
            SupportedLanguage.CHINESE to "下午3:05:30",
            SupportedLanguage.KOREAN to "오후 3:05:30",
        )

        examples.forEach { (language, source) ->
            val result = ReminderTextParser.fromCompleteKeywordTable(
                keywordTimes = emptyMap(),
                language = language,
            ).parse(source, context(language))

            assertNull("$language should not parse an unsupported seconds clock", result.dateTime)
            assertTrue("$language should not report a prefix as invalid", result.issues.isEmpty())
        }
    }

    @Test
    fun `clock rules do not expose a minute suffix inside unit-separated seconds`() {
        val examples = listOf(
            SupportedLanguage.ENGLISH to "at 8 am 05:30",
            SupportedLanguage.GERMAN to "8 Uhr 05:30",
            SupportedLanguage.FRENCH to "8 heures 05:30",
            SupportedLanguage.ITALIAN to "8 ore 05:30",
            SupportedLanguage.SPANISH to "8 horas 05:30",
            SupportedLanguage.RUSSIAN to "8 часов 05:30",
            SupportedLanguage.JAPANESE to "午後三時05:30",
            SupportedLanguage.CHINESE to "下午三点05:30",
            SupportedLanguage.KOREAN to "오후 3시 05:30",
        )

        examples.forEach { (language, source) ->
            val result = ReminderTextParser.fromCompleteKeywordTable(
                keywordTimes = emptyMap(),
                language = language,
            ).parse(source, context(language))

            assertNull("$language should not parse an unsupported seconds clock: $source", result.dateTime)
            assertTrue("$language should not report a seconds suffix: $source", result.issues.isEmpty())
        }
    }

    @Test
    fun `valid long durations do not report nested invalid clock fragments`() {
        val examples = listOf(
            SupportedLanguage.ENGLISH to "in 27 hours",
            SupportedLanguage.GERMAN to "in 27 Stunden",
            SupportedLanguage.FRENCH to "dans 27 heures",
            SupportedLanguage.ITALIAN to "tra 27 ore",
            SupportedLanguage.SPANISH to "en 27 horas",
            SupportedLanguage.RUSSIAN to "через 27 часов",
            SupportedLanguage.JAPANESE to "27時間後",
            SupportedLanguage.CHINESE to "27小时后",
            SupportedLanguage.KOREAN to "27시간 후",
        )

        examples.forEach { (language, expression) ->
            val result = parser(language).parse(expression, context(language))

            assertEquals(language.toString(), TemporalPrecision.RELATIVE_DURATION, result.dateTime?.precision)
            assertTrue("$language should not report a nested invalid clock", result.issues.isEmpty())
        }
    }

    @Test
    fun `an overflowing relative duration is ignored safely`() {
        val result = parser(SupportedLanguage.ENGLISH)
            .parse("in 9223372036854775807 hours", context(SupportedLanguage.ENGLISH))

        assertNull(result.dateTime)
    }

    @Test
    fun `CJK clock rules do not split longer numeric tokens`() {
        assertNull(
            parser(SupportedLanguage.JAPANESE)
                .parse("123時", context(SupportedLanguage.JAPANESE))
                .dateTime,
        )
        assertNull(
            parser(SupportedLanguage.CHINESE)
                .parse("123点", context(SupportedLanguage.CHINESE))
                .dateTime,
        )
        assertNull(
            parser(SupportedLanguage.KOREAN)
                .parse("123시", context(SupportedLanguage.KOREAN))
                .dateTime,
        )
    }

    @Test
    fun `clock unit prefixes do not steal longer duration units`() {
        assertNull(
            parser(SupportedLanguage.JAPANESE)
                .parse("3時間", context(SupportedLanguage.JAPANESE))
                .dateTime,
        )
        assertNull(
            parser(SupportedLanguage.KOREAN)
                .parse("3시간", context(SupportedLanguage.KOREAN))
                .dateTime,
        )
    }

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
    fun `CJK keyword day parts also modify explicit clocks and stay highlighted`() {
        val examples = listOf(
            ClockExample(SupportedLanguage.JAPANESE, "深夜11時", LocalTime.of(23, 0)),
            ClockExample(SupportedLanguage.JAPANESE, "晩8時", LocalTime.of(20, 0)),
            ClockExample(SupportedLanguage.CHINESE, "早晨3点", LocalTime.of(3, 0)),
            ClockExample(SupportedLanguage.CHINESE, "夜里10点", LocalTime.of(22, 0)),
            ClockExample(SupportedLanguage.CHINESE, "今晚8点", LocalTime.of(20, 0)),
            ClockExample(SupportedLanguage.KOREAN, "새벽 3시", LocalTime.of(3, 0)),
        )

        examples.forEach { example ->
            val detection = parser(example.language)
                .parse(example.expression, context(example.language))
                .dateTime

            assertEquals("${example.language} should apply its keyword day part", example.expected, detection?.time)
            assertEquals(example.expression, detection?.sourceLabel)
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
    fun `Japanese relative hour and minute durations resolve from the fixed instant`() {
        val japanese = parser(SupportedLanguage.JAPANESE)
        val hours = japanese.parse(
            "3時間後に確認する",
            context(SupportedLanguage.JAPANESE),
        ).dateTime
        val minutes = japanese.parse(
            "30分後に出発する",
            context(SupportedLanguage.JAPANESE),
        ).dateTime

        assertEquals(Instant.parse("2026-08-24T13:15:00Z"), hours?.instant)
        assertEquals(TemporalPrecision.RELATIVE_DURATION, hours?.precision)
        assertEquals("3時間後", hours?.sourceLabel)
        assertEquals(Instant.parse("2026-08-24T10:45:00Z"), minutes?.instant)
        assertEquals(TemporalPrecision.RELATIVE_DURATION, minutes?.precision)
        assertEquals("30分後", minutes?.sourceLabel)
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
