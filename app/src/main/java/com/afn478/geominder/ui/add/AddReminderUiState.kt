package com.afn478.geominder.ui.add

import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.geofence.GeoInputError
import com.afn478.geominder.geofence.GeoInputField
import com.afn478.geominder.parser.ParseResult

data class AddReminderUiState(
    val editingReminderId: ReminderId? = null,
    val sourceText: String = "",
    val parseResult: ParseResult? = null,
    val expanded: Boolean = false,
    val editingDateTimeDetectionId: String? = null,
    val dateEditText: String = "",
    val timeEditText: String = "",
    val dateTimeEditError: String? = null,
    val geoEditorVisible: Boolean = false,
    val latitudeText: String = "",
    val longitudeText: String = "",
    val radiusText: String = "",
    val geoInputErrors: Map<GeoInputField, GeoInputError> = emptyMap(),
    val geoLabel: String? = null,
    val activeFromEnabled: Boolean = false,
    val activeFromDateText: String = "",
    val activeFromTimeText: String = "",
    val activeFromError: String? = null,
    val isLocating: Boolean = false,
    val locationError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val savedReminder: Reminder? = null,
) {
    val hasDateTimeDetection: Boolean
        get() = parseResult?.dateTime != null

    val hasGpsDetection: Boolean
        get() = parseResult?.gps != null
}
