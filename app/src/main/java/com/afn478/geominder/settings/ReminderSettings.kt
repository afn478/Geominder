package com.afn478.geominder.settings

import com.afn478.geominder.localization.SupportedLanguage
import com.afn478.geominder.parser.KeywordTimeDictionary
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class ReminderSettings(
    val defaultGeofenceRadiusMeters: Double = SettingsValidation.DEFAULT_RADIUS_METERS,
    val keywordTimes: Map<String, LocalTime> = KeywordTimeDictionary.DEFAULTS.toMap(),
    val keywordLanguage: SupportedLanguage = SupportedLanguage.ENGLISH,
    val removeTimeExpressionsFromText: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentTheme: AccentTheme = AccentTheme.DYNAMIC,
    val sortOrder: ReminderSortOrder = ReminderSortOrder.DEFAULT,
)

enum class ValidationError(val defaultMessage: String) {
    RADIUS_NUMBER("Enter a radius in metres"),
    RADIUS_FINITE("Enter a finite radius"),
    RADIUS_RANGE("Use a radius from 1 to 100,000 metres"),
    KEYWORD_EMPTY("Enter a keyword"),
    KEYWORD_TOO_LONG("Keep the keyword to 40 characters or fewer"),
    KEYWORD_CHARACTER("The keyword contains an unsupported character"),
    TIME_FORMAT("Use a 24-hour time such as 08:30"),
}

sealed interface ValidationResult<out T> {
    data class Valid<T>(val value: T) : ValidationResult<T>

    data class Invalid(val error: ValidationError) : ValidationResult<Nothing> {
        val message: String
            get() = error.defaultMessage
    }
}

object SettingsValidation {
    const val DEFAULT_RADIUS_METERS = 100.0
    const val MIN_RADIUS_METERS = 1.0
    const val MAX_RADIUS_METERS = 100_000.0
    const val MAX_KEYWORD_LENGTH = 40

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun radius(input: String): ValidationResult<Double> {
        val value = input.trim().toDoubleOrNull()
            ?: return ValidationResult.Invalid(ValidationError.RADIUS_NUMBER)
        return when {
            !value.isFinite() -> ValidationResult.Invalid(ValidationError.RADIUS_FINITE)
            value < MIN_RADIUS_METERS || value > MAX_RADIUS_METERS ->
                ValidationResult.Invalid(ValidationError.RADIUS_RANGE)
            else -> ValidationResult.Valid(value)
        }
    }

    fun keyword(input: String): ValidationResult<String> {
        val normalized = input
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
        return when {
            normalized.isEmpty() -> ValidationResult.Invalid(ValidationError.KEYWORD_EMPTY)
            normalized.length > MAX_KEYWORD_LENGTH ->
                ValidationResult.Invalid(ValidationError.KEYWORD_TOO_LONG)
            normalized.any(Char::isISOControl) ->
                ValidationResult.Invalid(ValidationError.KEYWORD_CHARACTER)
            else -> ValidationResult.Valid(normalized)
        }
    }

    fun time(input: String): ValidationResult<LocalTime> {
        val normalized = input.trim()
        if (!Regex("\\d{2}:\\d{2}").matches(normalized)) {
            return ValidationResult.Invalid(ValidationError.TIME_FORMAT)
        }
        val hour = normalized.substring(0, 2).toInt()
        val minute = normalized.substring(3, 5).toInt()
        return if (hour in 0..23 && minute in 0..59) {
            ValidationResult.Valid(LocalTime.of(hour, minute))
        } else {
            ValidationResult.Invalid(ValidationError.TIME_FORMAT)
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
