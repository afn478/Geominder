package com.afn478.geominder.settings

import com.afn478.geominder.domain.model.PresetLocation
import com.afn478.geominder.ui.add.DefaultGeoRadiusProvider
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime

interface SettingsRepository : DefaultGeoRadiusProvider {
    val settings: StateFlow<ReminderSettings>

    override fun getDefaultRadiusMeters(): Double = settings.value.defaultGeofenceRadiusMeters

    suspend fun setDefaultRadiusMeters(radiusMeters: Double)

    suspend fun upsertKeywordTime(keyword: String, time: LocalTime)

    suspend fun removeKeyword(keyword: String)

    suspend fun resetKeywordTimes()

    suspend fun upsertKeywordLocation(keyword: String, location: PresetLocation)

    suspend fun removeKeywordLocation(keyword: String)

    suspend fun resetKeywordLocations()

    suspend fun setRemoveTimeExpressionsFromText(enabled: Boolean) {}

    suspend fun setThemeMode(mode: ThemeMode) {}

    suspend fun setAccentTheme(accent: AccentTheme) {}

    suspend fun setSortOrder(sortOrder: ReminderSortOrder) {}
}
