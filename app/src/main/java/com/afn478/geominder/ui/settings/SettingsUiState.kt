package com.afn478.geominder.ui.settings

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

data class SettingsUiState(
    val settings: ReminderSettings = ReminderSettings(),
    val locale: Locale = Locale.getDefault(),
    val radiusText: String = SettingsValidation.formatRadius(
        settings.defaultGeofenceRadiusMeters,
    ),
    val radiusError: String? = null,
    val keywordEditorVisible: Boolean = false,
    val editingKeyword: String? = null,
    val keywordText: String = "",
    val keywordTimeText: String = "",
    val keywordError: String? = null,
    val keywordTimeError: String? = null,
    val permissionItems: List<PermissionUiItem> = emptyList(),
    val persistenceError: String? = null,
) {
    val keywordTimes: List<KeywordTimeUiItem>
        get() = settings.keywordTimes
            .map { (keyword, time) -> KeywordTimeUiItem(keyword, time, locale) }
            .sortedBy(KeywordTimeUiItem::keyword)

    val isEditingKeyword: Boolean
        get() = editingKeyword != null
}
