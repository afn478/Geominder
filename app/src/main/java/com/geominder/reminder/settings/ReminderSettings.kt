package com.geominder.reminder.settings

import com.geominder.reminder.parser.KeywordTimeDictionary
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class ReminderSettings(
    val defaultGeofenceRadiusMeters: Double = SettingsValidation.DEFAULT_RADIUS_METERS,
    val keywordTimes: Map<String, LocalTime> = KeywordTimeDictionary.DEFAULTS.toMap(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentTheme: AccentTheme = AccentTheme.DYNAMIC,
)

sealed interface ValidationResult<out T> {
    data class Valid<T>(val value: T) : ValidationResult<T>

    data class Invalid(val message: String) : ValidationResult<Nothing>
}

object SettingsValidation {
    const val DEFAULT_RADIUS_METERS = 100.0
    const val MIN_RADIUS_METERS = 1.0
    const val MAX_RADIUS_METERS = 100_000.0
    const val MAX_KEYWORD_LENGTH = 40

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun radius(input: String): ValidationResult<Double> {
        val value = input.trim().toDoubleOrNull()
            ?: return ValidationResult.Invalid("Enter a radius in metres")
        return when {
            !value.isFinite() -> ValidationResult.Invalid("Enter a finite radius")
            value < MIN_RADIUS_METERS || value > MAX_RADIUS_METERS ->
                ValidationResult.Invalid("Use a radius from 1 to 100,000 metres")
            else -> ValidationResult.Valid(value)
        }
    }

    fun keyword(input: String): ValidationResult<String> {
        val normalized = input
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
        return when {
            normalized.isEmpty() -> ValidationResult.Invalid("Enter a keyword")
            normalized.length > MAX_KEYWORD_LENGTH ->
                ValidationResult.Invalid("Keep the keyword to 40 characters or fewer")
            normalized.any(Char::isISOControl) ->
                ValidationResult.Invalid("The keyword contains an unsupported character")
            else -> ValidationResult.Valid(normalized)
        }
    }

    fun time(input: String): ValidationResult<LocalTime> {
        val normalized = input.trim()
        if (!Regex("\\d{2}:\\d{2}").matches(normalized)) {
            return ValidationResult.Invalid("Use a 24-hour time such as 08:30")
        }
        val hour = normalized.substring(0, 2).toInt()
        val minute = normalized.substring(3, 5).toInt()
        return if (hour in 0..23 && minute in 0..59) {
            ValidationResult.Valid(LocalTime.of(hour, minute))
        } else {
            ValidationResult.Invalid("Use a 24-hour time such as 08:30")
        }
    }

    /** Parses the locale's short time form, retaining the invariant form as a fallback. */
    fun time(input: String, locale: Locale): ValidationResult<LocalTime> {
        val normalized = input.trim()
        val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        listOf(normalized, normalized.replace(" ", "\u00a0"), normalized.replace(" ", "\u202f"))
            .asSequence()
            .mapNotNull { candidate ->
                runCatching { LocalTime.from(formatter.parse(candidate)) }.getOrNull()
            }
            .firstOrNull()
            ?.let { return ValidationResult.Valid(it) }
        return time(input)
    }

    fun formatRadius(value: Double): String = when {
        value == value.toLong().toDouble() -> value.toLong().toString()
        else -> value.toString()
    }

    fun formatTime(value: LocalTime): String = value.format(timeFormatter)

    fun formatTime(value: LocalTime, locale: Locale): String = value.format(
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale),
    )
}
