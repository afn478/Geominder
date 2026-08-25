package com.geominder.reminder.settings

import com.geominder.reminder.ui.add.DefaultGeoRadiusProvider
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime

interface SettingsRepository : DefaultGeoRadiusProvider {
    val settings: StateFlow<ReminderSettings>

    override fun getDefaultRadiusMeters(): Double = settings.value.defaultGeofenceRadiusMeters

    suspend fun setDefaultRadiusMeters(radiusMeters: Double)

    suspend fun upsertKeywordTime(keyword: String, time: LocalTime)

    suspend fun removeKeyword(keyword: String)

    suspend fun resetKeywordTimes()

    suspend fun setThemeMode(mode: ThemeMode) {}

    suspend fun setAccentTheme(accent: AccentTheme) {}
}
