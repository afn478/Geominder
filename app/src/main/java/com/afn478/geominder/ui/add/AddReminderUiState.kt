package com.afn478.geominder.ui.add

import com.afn478.geominder.domain.model.ChecklistItemId
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.ReminderTag
import com.afn478.geominder.geofence.GeoInputError
import com.afn478.geominder.geofence.GeoInputField
import com.afn478.geominder.localization.UiText
import com.afn478.geominder.parser.ParseResult

data class AddReminderUiState(
    val editingReminderId: ReminderId? = null,
    val sourceText: String = "",
    val tag: ReminderTag? = null,
    val parseResult: ParseResult? = null,
    val expanded: Boolean = false,
    val detailsExpanded: Boolean = false,
    val editingDateTimeDetectionId: String? = null,
    val dateTimeEditDirty: Boolean = false,
    val timeTriggerCleared: Boolean = false,
    val dateEditText: String = "",
    val timeEditText: String = "",
    val dateTimeEditError: UiText? = null,
    val geoEditorVisible: Boolean = false,
    val latitudeText: String = "",
    val longitudeText: String = "",
    val radiusText: String = "",
    val geoInputErrors: Map<GeoInputField, GeoInputError> = emptyMap(),
    val geoLabel: String? = null,
    val activeFromEnabled: Boolean = false,
    val activeFromDateText: String = "",
    val activeFromTimeText: String = "",
    val activeFromError: UiText? = null,
    val isLocating: Boolean = false,
    val locationError: UiText? = null,
    val isSaving: Boolean = false,
    val saveError: UiText? = null,
    val savedReminder: Reminder? = null,
    val checklistVisible: Boolean = false,
    val checklist: List<ChecklistDraftItem> = emptyList(),
    /** One-shot focus request consumed by the screen after moving focus to that row. */
    val checklistFocusItemId: ChecklistItemId? = null,
) {
    val hasUnsavedInput: Boolean
        get() = sourceText.isNotBlank() || checklist.any { it.text.isNotBlank() }

    /** Choices that only make sense together with some text: a time, place, tag or empty list. */
    val hasDraftSelections: Boolean
        get() = parseResult?.dateTime != null || geoEditorVisible || tag != null || checklistVisible

    val hasDateTimeDetection: Boolean
        get() = parseResult?.reminderTime != null

    val hasGpsDetection: Boolean
        get() = parseResult?.gps != null
}

/** Editable checklist row. Its text is kept verbatim and is never parsed for triggers. */
data class ChecklistDraftItem(
    val id: ChecklistItemId = ChecklistItemId.create(),
    val text: String = "",
    val done: Boolean = false,
)
