package com.afn478.geominder.ui.settings

import com.afn478.geominder.settings.PermissionUiItem
import com.afn478.geominder.settings.ReminderSettings
import com.afn478.geominder.settings.SettingsValidation
import com.afn478.geominder.localization.UiText
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
    val radiusError: UiText? = null,
    val keywordEditorVisible: Boolean = false,
    val editingKeyword: String? = null,
    val keywordText: String = "",
    val keywordTimeText: String = "",
    val keywordError: UiText? = null,
    val keywordTimeError: UiText? = null,
    val permissionItems: List<PermissionUiItem> = emptyList(),
    val persistenceError: UiText? = null,
) {
    val keywordTimes: List<KeywordTimeUiItem>
        get() = settings.keywordTimes
            .map { (keyword, time) -> KeywordTimeUiItem(keyword, time, locale) }
            .sortedBy(KeywordTimeUiItem::keyword)

    val isEditingKeyword: Boolean
        get() = editingKeyword != null
}
