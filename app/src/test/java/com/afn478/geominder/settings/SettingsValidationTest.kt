package com.afn478.geominder.settings

import com.afn478.geominder.parser.ReminderTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.util.Locale

class SettingsValidationTest {
    @Test
    fun `radius accepts bounded positive finite values`() {
        assertEquals(ValidationResult.Valid(1.0), SettingsValidation.radius("1"))
        assertEquals(ValidationResult.Valid(125.5), SettingsValidation.radius(" 125.5 "))
        assertEquals(ValidationResult.Valid(100_000.0), SettingsValidation.radius("100000"))
    }

    @Test
    fun `radius rejects missing nonfinite and out of bounds values`() {
        listOf("", "nearby", "NaN", "Infinity", "0", "-4", "100001").forEach { input ->
            assertTrue("Expected '$input' to be invalid", SettingsValidation.radius(input) is ValidationResult.Invalid)
        }
    }

    @Test
    fun `keyword is normalized consistently for parser overrides`() {
        assertEquals(
            ValidationResult.Valid("after work"),
            SettingsValidation.keyword("  AFTER   WORK "),
        )
    }

    @Test
    fun `time uses strict 24 hour hour and minute format`() {
        assertEquals(ValidationResult.Valid(LocalTime.of(8, 5)), SettingsValidation.time("08:05"))
        listOf("8:05", "08:5", "24:00", "noon").forEach { input ->
            assertTrue("Expected '$input' to be invalid", SettingsValidation.time(input) is ValidationResult.Invalid)
        }
    }

    @Test
    fun `localized time accepts locale short form with invariant fallback`() {
        val afternoon = LocalTime.of(17, 30)
        val us = SettingsValidation.formatTime(afternoon, Locale.US)
        val germany = SettingsValidation.formatTime(afternoon, Locale.GERMANY)

        assertEquals(ValidationResult.Valid(afternoon), SettingsValidation.time(us, Locale.US))
        assertEquals(ValidationResult.Valid(afternoon), SettingsValidation.time(germany, Locale.GERMANY))
        assertEquals(ValidationResult.Valid(afternoon), SettingsValidation.time("17:30", Locale.US))
        assertEquals("17:30", SettingsValidation.formatTime(afternoon))
    }

    @Test
    fun `parser defaults provider follows the injected parser configuration`() {
        val parser = ReminderTextParser(
            keywordOverrides = mapOf("morning" to LocalTime.of(7, 15)),
        )

        val defaults = ParserKeywordTimeDefaultsProvider(parser).get()

        assertEquals(LocalTime.of(7, 15), defaults["morning"])
        assertEquals(LocalTime.of(20, 0), defaults["tonight"])
    }
}
