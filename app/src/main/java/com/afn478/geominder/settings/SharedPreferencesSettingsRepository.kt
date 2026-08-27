package com.afn478.geominder.settings

import android.content.Context
import android.content.SharedPreferences
import com.afn478.geominder.localization.SupportedLanguage
import com.afn478.geominder.parser.ReminderTextParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

class SharedPreferencesSettingsRepository(
    context: Context,
    keywordTimeDefaultsProvider: KeywordTimeDefaultsProvider =
        ParserKeywordTimeDefaultsProvider(ReminderTextParser()),
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    ),
) : SettingsRepository {
    private val defaultLanguage = keywordTimeDefaultsProvider.language()
    private val defaultKeywordTimes = keywordTimeDefaultsProvider.get().toMap()
    private val _settings = MutableStateFlow(readSettings())
    override val settings: StateFlow<ReminderSettings> = _settings.asStateFlow()

    override suspend fun setDefaultRadiusMeters(radiusMeters: Double) {
        require(radiusMeters.isFinite() && radiusMeters in SettingsValidation.MIN_RADIUS_METERS..SettingsValidation.MAX_RADIUS_METERS) {
            "Default geofence radius is out of range"
        }
        preferences.edit()
            .putString(KEY_DEFAULT_RADIUS, radiusMeters.toString())
            .apply()
        _settings.value = _settings.value.copy(defaultGeofenceRadiusMeters = radiusMeters)
    }

    override suspend fun upsertKeywordTime(keyword: String, time: LocalTime) {
        val normalized = (SettingsValidation.keyword(keyword) as? ValidationResult.Valid)?.value
            ?: throw IllegalArgumentException("Invalid preset keyword")
        updateKeywordTimes(_settings.value.keywordTimes + (normalized to time))
    }

    override suspend fun removeKeyword(keyword: String) {
        val normalized = (SettingsValidation.keyword(keyword) as? ValidationResult.Valid)?.value
            ?: return
        updateKeywordTimes(_settings.value.keywordTimes - normalized)
    }

    override suspend fun resetKeywordTimes() {
        updateKeywordTimes(defaultKeywordTimes)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    override suspend fun setAccentTheme(accent: AccentTheme) {
        preferences.edit().putString(KEY_ACCENT_THEME, accent.name).apply()
        _settings.value = _settings.value.copy(accentTheme = accent)
    }

    override suspend fun setSortOrder(sortOrder: ReminderSortOrder) {
        preferences.edit()
            .putString(KEY_SORT_FIELD, sortOrder.field.name)
            .putString(KEY_SORT_DIRECTION, sortOrder.direction.name)
            .apply()
        _settings.value = _settings.value.copy(sortOrder = sortOrder)
    }

    private fun updateKeywordTimes(entries: Map<String, LocalTime>) {
        val snapshot = entries.toMap()
        preferences.edit()
            .putString(KEY_KEYWORD_TIMES, SettingsCodec.encodeKeywordTimes(snapshot))
            .putString(KEY_KEYWORD_LANGUAGE, defaultLanguage.languageTag)
            .apply()
        _settings.value = _settings.value.copy(
            keywordTimes = snapshot,
            keywordLanguage = defaultLanguage,
        )
    }

    private fun readSettings(): ReminderSettings {
        val storedKeywords = preferences.getString(KEY_KEYWORD_TIMES, null)
        val storedLanguage = preferences.getString(KEY_KEYWORD_LANGUAGE, null)
            ?.let(SupportedLanguage::fromLanguageTag)
            ?: SupportedLanguage.ENGLISH
        val keywordTimes = storedKeywords
            ?.let(SettingsCodec::decodeKeywordTimes)
            ?.let { stored ->
                if (storedLanguage == defaultLanguage) {
                    stored
                } else {
                    migrateKeywordTimes(
                        stored = stored,
                        storedLanguage = storedLanguage,
                        activeDefaults = defaultKeywordTimes,
                    )
                }
            }
            ?: defaultKeywordTimes
        return ReminderSettings(
            defaultGeofenceRadiusMeters = SettingsCodec.decodeRadius(
                preferences.getString(KEY_DEFAULT_RADIUS, null),
            ),
            keywordTimes = keywordTimes,
            keywordLanguage = defaultLanguage,
            themeMode = ThemeMode.fromStorage(preferences.getString(KEY_THEME_MODE, null)),
            accentTheme = AccentTheme.fromStorage(preferences.getString(KEY_ACCENT_THEME, null)),
            sortOrder = SettingsCodec.decodeSortOrder(
                field = preferences.getString(KEY_SORT_FIELD, null),
                direction = preferences.getString(KEY_SORT_DIRECTION, null),
            ),
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "reminder_settings"
        const val KEY_DEFAULT_RADIUS = "default_geofence_radius_meters"
        const val KEY_KEYWORD_TIMES = "keyword_time_presets"
        const val KEY_KEYWORD_LANGUAGE = "keyword_time_language"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ACCENT_THEME = "accent_theme"
        const val KEY_SORT_FIELD = "sort_field"
        const val KEY_SORT_DIRECTION = "sort_direction"
    }
}

private fun migrateKeywordTimes(
    stored: Map<String, LocalTime>,
    storedLanguage: SupportedLanguage,
    activeDefaults: Map<String, LocalTime>,
): Map<String, LocalTime> {
    val storedDefaults = com.afn478.geominder.parser.TimeLanguagePacks.defaultsFor(storedLanguage)
    val customEntries = stored.filter { (keyword, time) ->
        storedDefaults[keyword] != time
    }
    return (activeDefaults + customEntries).toSortedMap()
}
