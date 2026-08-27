package com.afn478.geominder.ui.settings

import com.afn478.geominder.domain.model.PresetLocation
import com.afn478.geominder.geofence.GeoInputError
import com.afn478.geominder.geofence.GeoInputField
import com.afn478.geominder.localization.UiText
import com.afn478.geominder.settings.PermissionUiItem
import com.afn478.geominder.settings.ReminderSettings
import com.afn478.geominder.settings.SettingsValidation
import java.time.LocalTime
import java.util.Locale

data class KeywordTimeUiItem(
    val keyword: String,
    val time: LocalTime,
    val locale: Locale = Locale.getDefault(),
) {
    val formattedTime: String
        get() = SettingsValidation.formatTime(time, locale)
}

data class KeywordLocationUiItem(
    val keyword: String,
    val location: PresetLocation,
) {
    val formattedCoordinates: String
        get() = String.format(
            Locale.ROOT,
            "%.6f, %.6f",
            location.latitude,
            location.longitude,
        )

    val formattedRadius: String
        get() = SettingsValidation.formatRadius(location.radiusMeters)
}

data class SettingsUiState(
    val settings: ReminderSettings = ReminderSettings(),
    val locale: Locale = Locale.getDefault(),
    val radiusText: String = SettingsValidation.formatRadius(
        settings.defaultGeofenceRadiusMeters,
    ),
    val radiusError: UiText? = null,
    val keywordEditorVisible: Boolean = false,
    val editingKeyword: String? = null,
    val keywordText: String = "",
    val keywordTimeText: String = "",
    val keywordError: UiText? = null,
    val keywordTimeError: UiText? = null,
    val locationEditorVisible: Boolean = false,
    val editingLocationKeyword: String? = null,
    val locationKeywordText: String = "",
    val locationLatitudeText: String = "",
    val locationLongitudeText: String = "",
    val locationRadiusText: String = SettingsValidation.formatRadius(
        settings.defaultGeofenceRadiusMeters,
    ),
    val locationKeywordError: UiText? = null,
    val locationInputErrors: Map<GeoInputField, GeoInputError> = emptyMap(),
    val locationError: UiText? = null,
    val isLocatingLocation: Boolean = false,
    val permissionItems: List<PermissionUiItem> = emptyList(),
    val persistenceError: UiText? = null,
) {
    val keywordTimes: List<KeywordTimeUiItem>
        get() = settings.keywordTimes
            .map { (keyword, time) -> KeywordTimeUiItem(keyword, time, locale) }
            .sortedBy(KeywordTimeUiItem::keyword)

    val isEditingKeyword: Boolean
        get() = editingKeyword != null

    val keywordLocations: List<KeywordLocationUiItem>
        get() = settings.keywordLocations
            .map { (keyword, location) -> KeywordLocationUiItem(keyword, location) }
            .sortedBy(KeywordLocationUiItem::keyword)

    val isEditingLocation: Boolean
        get() = editingLocationKeyword != null
}
