package com.afn478.geominder.ui.add

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.afn478.geominder.ui.map.LocationMapPreview
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afn478.geominder.R
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderTag
import com.afn478.geominder.geofence.GeoInputError
import com.afn478.geominder.geofence.GeoInputField
import com.afn478.geominder.localization.AppLanguagePreferences
import com.afn478.geominder.localization.UiText
import com.afn478.geominder.localization.resource
import com.afn478.geominder.parser.ParseIssue
import com.afn478.geominder.parser.ParseIssueCode
import com.afn478.geominder.parser.SourceSpan
import com.afn478.geominder.ui.tag.ReminderTagChips
import com.afn478.geominder.ui.text.resolve
import com.afn478.geominder.ui.text.resolveNearbyLabel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val datePickerMotionScheme = object : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = datePickerTween(180)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = datePickerTween(140)

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = datePickerTween(220)

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = datePickerTween(150)

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = datePickerTween(100)

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = datePickerTween(200)
}

private fun <T> datePickerTween(durationMillis: Int): FiniteAnimationSpec<T> =
    tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)

/** Full-page editor, used for "more options" on a new reminder and for editing an existing one. */
@Composable
fun AddReminderRoute(
    viewModel: AddReminderViewModel,
    onReminderSaved: (Reminder) -> Unit,
    modifier: Modifier = Modifier,
    autoFocusText: Boolean = false,
    onPickOnMap: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedReminder = state.savedReminder
    LaunchedEffect(savedReminder?.id) {
        if (savedReminder != null) {
            onReminderSaved(savedReminder)
            viewModel.consumeSavedReminder()
        }
    }

    AddReminderScreen(
        state = state,
        onSourceTextChange = viewModel::onSourceTextChange,
        onTagClick = viewModel::onTagClick,
        onDateTimeChipClick = viewModel::beginDateTimeEdit,
        onDateEditChange = viewModel::onDateEditChange,
        onTimeEditChange = viewModel::onTimeEditChange,
        onCommitDateTimeEdit = viewModel::commitDateTimeEdit,
        onClearDateTime = viewModel::clearTimeTrigger,
        onLocationTriggerEnabledChange = { enabled ->
            if (enabled) viewModel.showGeoEditor() else viewModel.hideGeoEditor()
        },
        onLatitudeChange = viewModel::onLatitudeChange,
        onLongitudeChange = viewModel::onLongitudeChange,
        onRadiusChange = viewModel::onRadiusChange,
        onPasteLocation = viewModel::pasteLocation,
        onLocate = viewModel::locate,
        onPickOnMap = onPickOnMap,
        onActiveFromEnabledChange = viewModel::onActiveFromEnabledChange,
        onActiveFromDateChange = viewModel::onActiveFromDateChange,
        onActiveFromTimeChange = viewModel::onActiveFromTimeChange,
        checklistActions = ChecklistActions(
            onChecklistClick = viewModel::onChecklistClick,
            onItemTextChange = viewModel::onChecklistItemTextChange,
            onItemDoneChange = viewModel::onChecklistItemDoneChange,
            onAddItem = viewModel::addChecklistItem,
            onRemoveItem = viewModel::removeChecklistItem,
            onFocusConsumed = viewModel::consumeChecklistFocus,
        ),
        modifier = modifier,
        autoFocusText = autoFocusText,
    )
}

@Composable
fun AddReminderScreen(
    state: AddReminderUiState,
    onSourceTextChange: (String) -> Unit,
    onTagClick: (ReminderTag) -> Unit,
    onDateTimeChipClick: () -> Unit,
    onDateEditChange: (String) -> Unit,
    onTimeEditChange: (String) -> Unit,
    onCommitDateTimeEdit: () -> Unit,
    onClearDateTime: () -> Unit,
    onLocationTriggerEnabledChange: (Boolean) -> Unit,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onRadiusChange: (String) -> Unit,
    onPasteLocation: (String) -> Unit,
    onLocate: () -> Unit,
    onPickOnMap: () -> Unit = {},
    onActiveFromEnabledChange: (Boolean) -> Unit,
    onActiveFromDateChange: (String) -> Unit,
    onActiveFromTimeChange: (String) -> Unit,
    checklistActions: ChecklistActions = ChecklistActions(),
    modifier: Modifier = Modifier,
    autoFocusText: Boolean = false,
) {
    EditReminderContent(
        state = state,
        onSourceTextChange = onSourceTextChange,
        onTagClick = onTagClick,
        onDateTimeChipClick = onDateTimeChipClick,
        onDateEditChange = onDateEditChange,
        onTimeEditChange = onTimeEditChange,
        onCommitDateTimeEdit = onCommitDateTimeEdit,
        onClearDateTime = onClearDateTime,
        onLocationTriggerEnabledChange = onLocationTriggerEnabledChange,
        onLatitudeChange = onLatitudeChange,
        onLongitudeChange = onLongitudeChange,
        onRadiusChange = onRadiusChange,
        onPasteLocation = onPasteLocation,
        onLocate = onLocate,
        onPickOnMap = onPickOnMap,
        onActiveFromEnabledChange = onActiveFromEnabledChange,
        onActiveFromDateChange = onActiveFromDateChange,
        onActiveFromTimeChange = onActiveFromTimeChange,
        checklistActions = checklistActions,
        modifier = modifier,
        autoFocusText = autoFocusText,
    )
}

@Composable
private fun EditReminderContent(
    state: AddReminderUiState,
    onSourceTextChange: (String) -> Unit,
    onTagClick: (ReminderTag) -> Unit,
    onDateTimeChipClick: () -> Unit,
    onDateEditChange: (String) -> Unit,
    onTimeEditChange: (String) -> Unit,
    onCommitDateTimeEdit: () -> Unit,
    onClearDateTime: () -> Unit,
    onLocationTriggerEnabledChange: (Boolean) -> Unit,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onRadiusChange: (String) -> Unit,
    onPasteLocation: (String) -> Unit,
    onLocate: () -> Unit,
    onPickOnMap: () -> Unit,
    onActiveFromEnabledChange: (Boolean) -> Unit,
    onActiveFromDateChange: (String) -> Unit,
    onActiveFromTimeChange: (String) -> Unit,
    checklistActions: ChecklistActions,
    modifier: Modifier = Modifier,
    autoFocusText: Boolean = false,
) {
    val textFocusRequester = remember { FocusRequester() }
    // Once per visit: coming back from the map picker must not pop the keyboard up again.
    var autoFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoFocusText) {
        if (autoFocusText && !autoFocusDone) {
            textFocusRequester.requestFocus()
            autoFocusDone = true
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                value = state.sourceText,
                onValueChange = onSourceTextChange,
                enabled = !state.isSaving,
                label = { Text(stringResource(R.string.reminder_text)) },
                placeholder = { Text(stringResource(R.string.reminder_text_placeholder)) },
                supportingText = { Text(stringResource(R.string.natural_language_supporting)) },
                singleLine = false,
                minLines = 1,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(textFocusRequester),
            )

            Column {
                Text(
                    stringResource(R.string.checklist),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.checklist_supporting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChecklistEditor(
                    items = state.checklist,
                    focusItemId = state.checklistFocusItemId,
                    enabled = !state.isSaving,
                    actions = checklistActions,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Text(
                stringResource(R.string.triggers),
                style = MaterialTheme.typography.headlineSmall,
            )
            TriggerControls(
                state = state,
                onDateTimeChipClick = onDateTimeChipClick,
                onDateEditChange = onDateEditChange,
                onTimeEditChange = onTimeEditChange,
                onCommitDateTimeEdit = onCommitDateTimeEdit,
                onClearDateTime = onClearDateTime,
                onLocationTriggerEnabledChange = onLocationTriggerEnabledChange,
                onLatitudeChange = onLatitudeChange,
                onLongitudeChange = onLongitudeChange,
                onRadiusChange = onRadiusChange,
                onPasteLocation = onPasteLocation,
                onLocate = onLocate,
                onPickOnMap = onPickOnMap,
                onActiveFromEnabledChange = onActiveFromEnabledChange,
                onActiveFromDateChange = onActiveFromDateChange,
                onActiveFromTimeChange = onActiveFromTimeChange,
            )
            TagSelector(state = state, onTagClick = onTagClick)

            state.parseResult?.issues?.forEach { issue ->
                SupportingError(issue.asUiText())
            }
            state.saveError?.let { SupportingError(it) }
        }
    }
}

@Composable
private fun TriggerToggle(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
        )
    }
}

@Composable
private fun TriggerControls(
    state: AddReminderUiState,
    onDateTimeChipClick: () -> Unit,
    onDateEditChange: (String) -> Unit,
    onTimeEditChange: (String) -> Unit,
    onCommitDateTimeEdit: () -> Unit,
    onClearDateTime: () -> Unit,
    onLocationTriggerEnabledChange: (Boolean) -> Unit,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onRadiusChange: (String) -> Unit,
    onPasteLocation: (String) -> Unit,
    onLocate: () -> Unit,
    onPickOnMap: () -> Unit,
    onActiveFromEnabledChange: (Boolean) -> Unit,
    onActiveFromDateChange: (String) -> Unit,
    onActiveFromTimeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TriggerToggle(
            title = stringResource(R.string.schedule_time),
            summary = if (state.hasDateTimeDetection) {
                stringResource(R.string.specific_date_time)
            } else {
                stringResource(R.string.no_time_notification)
            },
            checked = state.hasDateTimeDetection,
            enabled = !state.isSaving,
            onCheckedChange = { enabled ->
                if (enabled) onDateTimeChipClick() else onClearDateTime()
            },
        )
        if (state.hasDateTimeDetection) {
            DateTimeEditor(
                state = state,
                onDateEditChange = onDateEditChange,
                onTimeEditChange = onTimeEditChange,
                onCommit = onCommitDateTimeEdit,
            )
        }

        HorizontalDivider()

        TriggerToggle(
            title = stringResource(R.string.location_trigger),
            summary = if (state.geoEditorVisible) {
                state.geoLabel ?: stringResource(R.string.enter_location)
            } else {
                stringResource(R.string.no_location_notification)
            },
            checked = state.geoEditorVisible,
            enabled = !state.isSaving,
            onCheckedChange = onLocationTriggerEnabledChange,
        )
        if (state.geoEditorVisible) {
            GeoEditor(
                state = state,
                onLatitudeChange = onLatitudeChange,
                onLongitudeChange = onLongitudeChange,
                onRadiusChange = onRadiusChange,
                onPasteLocation = onPasteLocation,
                onLocate = onLocate,
                onPickOnMap = onPickOnMap,
                onActiveFromEnabledChange = onActiveFromEnabledChange,
                onActiveFromDateChange = onActiveFromDateChange,
                onActiveFromTimeChange = onActiveFromTimeChange,
            )
        }
    }
}

@Composable
private fun TagSelector(
    state: AddReminderUiState,
    onTagClick: (ReminderTag) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.color_tag), style = MaterialTheme.typography.titleMedium)
        ReminderTagChips(
            selectedTag = state.tag,
            onTagClick = onTagClick,
            enabled = !state.isSaving,
        )
    }
}

internal class DateTimeHighlightTransformation(
    private val spans: List<SourceSpan>,
    private val source: String,
    private val background: Color,
    private val foreground: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val validSpans = spans
            .filter {
                it.start >= 0 &&
                    it.endExclusive > it.start &&
                    it.endExclusive <= source.length &&
                    it.endExclusive <= text.length
            }
            .distinct()
            .sortedBy(SourceSpan::start)
        if (validSpans.isEmpty() || text.text != source) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val highlighted = AnnotatedString.Builder(source).apply {
            validSpans.forEach { span ->
                addStyle(
                    SpanStyle(
                        background = background,
                        color = foreground,
                    ),
                    span.start,
                    span.endExclusive,
                )
            }
        }.toAnnotatedString()
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

@Composable
internal fun DetectionChips(
    state: AddReminderUiState,
    onDateTimeChipClick: () -> Unit,
    onGeoChipClick: () -> Unit,
    onChecklistClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val timeDetection = state.parseResult?.reminderTime
    val coordinateSummary = state.geoLabel?.let { it.resolveNearbyLabel() }
        ?: state.parseResult?.gps?.displayLabel
        ?: listOf(state.latitudeText, state.longitudeText)
            .takeIf { values -> values.all(String::isNotBlank) }
            ?.joinToString(", ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = onDateTimeChipClick,
            enabled = enabled,
            leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
            label = { ChipLabel(timeDetection?.displayLabel ?: "+") },
        )
        AssistChip(
            onClick = onGeoChipClick,
            enabled = enabled,
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
            label = { ChipLabel(coordinateSummary ?: "+") },
            modifier = Modifier.widthIn(max = 280.dp),
        )
        val checklistCount = state.checklist.count { it.text.isNotBlank() }
        AssistChip(
            onClick = onChecklistClick,
            enabled = enabled,
            leadingIcon = {
                Icon(
                    Icons.Default.Checklist,
                    contentDescription = stringResource(R.string.add_checklist),
                )
            },
            label = {
                Text(
                    if (checklistCount > 0) {
                        stringResource(
                            R.string.checklist_progress,
                            state.checklist.count { it.done && it.text.isNotBlank() },
                            checklistCount,
                        )
                    } else {
                        "+"
                    },
                )
            },
        )
    }
}

@Composable
private fun ChipLabel(text: String) {
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun DateTimeEditor(
    state: AddReminderUiState,
    onDateEditChange: (String) -> Unit,
    onTimeEditChange: (String) -> Unit,
    onCommit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.time_trigger), style = MaterialTheme.typography.titleMedium)
        DateTimePickerFields(
            dateText = state.dateEditText,
            timeText = state.timeEditText,
            fallbackDate = state.parseResult?.reminderTime?.date,
            fallbackTime = state.parseResult?.reminderTime?.time,
            dateLabel = stringResource(R.string.date),
            timeLabel = stringResource(R.string.time),
            onDateChange = onDateEditChange,
            onTimeChange = onTimeEditChange,
            isError = state.dateTimeEditError != null,
        )
        state.dateTimeEditError?.let { SupportingError(it) }
        TextButton(onClick = onCommit) {
            Text(
                stringResource(
                    if (state.hasDateTimeDetection) R.string.update_time else R.string.next_hour,
                ),
            )
        }
    }
}

/**
 * Read-only date and time fields backed by the Material pickers. Values are exchanged as the same
 * localized short strings the view model formats and parses.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DateTimePickerFields(
    dateText: String,
    timeText: String,
    fallbackDate: LocalDate?,
    fallbackTime: LocalTime?,
    dateLabel: String,
    timeLabel: String,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    isError: Boolean,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val locale = AppLanguagePreferences.locale(context)
    val selectedDate = parseDisplayedDate(dateText, locale)
        ?: fallbackDate
        ?: LocalDate.now().plusDays(1)
    val selectedTime = parseDisplayedTime(timeText, locale)
        ?: fallbackTime
        ?: LocalTime.now().plusHours(1).withSecond(0).withNano(0)
    var datePickerVisible by rememberSaveable { mutableStateOf(false) }
    var timePickerVisible by rememberSaveable { mutableStateOf(false) }
    val uses24HourTime = android.text.format.DateFormat.is24HourFormat(context)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PickerField(
            value = dateText,
            label = dateLabel,
            actionDescription = stringResource(R.string.select_date),
            icon = Icons.Default.CalendarMonth,
            onClick = { datePickerVisible = true },
            modifier = Modifier.weight(1.5f),
            isError = isError,
            enabled = enabled,
        )
        PickerField(
            value = timeText,
            label = timeLabel,
            actionDescription = stringResource(R.string.select_time),
            icon = Icons.Default.Schedule,
            onClick = { timePickerVisible = true },
            modifier = Modifier.weight(1f),
            isError = isError,
            enabled = enabled,
        )
    }

    if (datePickerVisible) {
        // The picker works in UTC midnight millis regardless of the device zone.
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedMillis ->
                            val date = java.time.Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            onDateChange(formatDisplayedDate(date, locale))
                        }
                        datePickerVisible = false
                    },
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { datePickerVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            MaterialTheme(motionScheme = datePickerMotionScheme) {
                DatePicker(state = datePickerState)
            }
        }
    }

    if (timePickerVisible) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute,
            is24Hour = uses24HourTime,
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { timePickerVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeChange(
                            formatDisplayedTime(
                                LocalTime.of(timePickerState.hour, timePickerState.minute),
                                locale,
                            ),
                        )
                        timePickerVisible = false
                    },
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { timePickerVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            text = { TimePicker(state = timePickerState) },
        )
    }
}

@Composable
private fun PickerField(
    value: String,
    label: String,
    actionDescription: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            // Marks the field as a picker rather than something to type into.
            trailingIcon = { Icon(icon, contentDescription = null) },
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = isError,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .semantics {
                    contentDescription = actionDescription
                },
        )
    }
}

private fun parseDisplayedDate(value: String, locale: Locale): LocalDate? =
    try {
        val input = value.trim()
        runCatching {
            LocalDate.parse(
                input,
                DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale),
            )
        }.getOrElse {
            LocalDate.parse(input, DateTimeFormatter.ISO_LOCAL_DATE)
        }
    } catch (_: DateTimeParseException) {
        null
    }

private fun parseDisplayedTime(value: String, locale: Locale): LocalTime? =
    try {
        val input = value.trim()
        runCatching {
            LocalTime.parse(
                input,
                DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale),
            )
        }.getOrElse {
            LocalTime.parse(input, DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
        }
    } catch (_: DateTimeParseException) {
        null
    }

private fun formatDisplayedDate(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale))

private fun formatDisplayedTime(time: LocalTime, locale: Locale): String =
    time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

@Composable
@Suppress("DEPRECATION")
private fun GeoEditor(
    state: AddReminderUiState,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onRadiusChange: (String) -> Unit,
    onPasteLocation: (String) -> Unit,
    onLocate: () -> Unit,
    onPickOnMap: () -> Unit,
    onActiveFromEnabledChange: (Boolean) -> Unit,
    onActiveFromDateChange: (String) -> Unit,
    onActiveFromTimeChange: (String) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val locatingDescription = stringResource(R.string.locating_current_position)
    val latitude = state.latitudeText.trim().toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val longitude = state.longitudeText.trim().toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    // Manual entry is a fallback behind the map; it opens by itself when something needs fixing.
    var coordinatesExpanded by rememberSaveable { mutableStateOf(false) }
    val showCoordinates = coordinatesExpanded || state.geoInputErrors.isNotEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.location_details),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onPasteLocation(clipboardManager.getText()?.text.orEmpty()) },
                enabled = !state.isLocating,
            ) {
                Icon(
                    Icons.Default.ContentPaste,
                    contentDescription = stringResource(R.string.paste_location),
                )
            }
            IconButton(
                onClick = onLocate,
                enabled = !state.isLocating,
            ) {
                if (state.isLocating) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .semantics {
                                contentDescription = locatingDescription
                            },
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = stringResource(R.string.locate_current_position),
                    )
                }
            }
        }
        LocationMapPreview(
            latitude = latitude,
            longitude = longitude,
            radiusMeters = state.radiusText.trim().toDoubleOrNull(),
            onClick = onPickOnMap.takeIf { !state.isSaving },
        )
        state.geoLabel?.let {
            Text(text = it.resolveNearbyLabel(), style = MaterialTheme.typography.bodyMedium)
        }
        state.locationError?.let { SupportingError(it) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = showCoordinates,
                    role = Role.Button,
                    onValueChange = { coordinatesExpanded = it },
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.coordinates),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (showCoordinates) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showCoordinates) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GeoNumberField(
                    value = state.latitudeText,
                    onValueChange = onLatitudeChange,
                    label = stringResource(R.string.latitude),
                    error = state.geoInputErrors[GeoInputField.LATITUDE],
                    modifier = Modifier.weight(1f),
                )
                GeoNumberField(
                    value = state.longitudeText,
                    onValueChange = onLongitudeChange,
                    label = stringResource(R.string.longitude),
                    error = state.geoInputErrors[GeoInputField.LONGITUDE],
                    modifier = Modifier.weight(1f),
                )
            }
            GeoNumberField(
                value = state.radiusText,
                onValueChange = onRadiusChange,
                label = stringResource(R.string.radius_metres),
                error = state.geoInputErrors[GeoInputField.RADIUS],
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = state.activeFromEnabled,
                    enabled = !state.isSaving,
                    role = Role.Checkbox,
                    onValueChange = onActiveFromEnabledChange,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(
                checked = state.activeFromEnabled,
                onCheckedChange = null,
                enabled = !state.isSaving,
                modifier = Modifier.minimumInteractiveComponentSize(),
            )
            Text(
                text = stringResource(R.string.only_activate_location_from),
                modifier = Modifier.weight(1f),
            )
        }
        if (state.activeFromEnabled) {
            DateTimePickerFields(
                dateText = state.activeFromDateText,
                timeText = state.activeFromTimeText,
                fallbackDate = null,
                fallbackTime = null,
                dateLabel = stringResource(R.string.active_date),
                timeLabel = stringResource(R.string.active_time),
                onDateChange = onActiveFromDateChange,
                onTimeChange = onActiveFromTimeChange,
                isError = state.activeFromError != null,
                enabled = !state.isSaving,
            )
            state.activeFromError?.let { SupportingError(it) }
            Text(
                text = stringResource(R.string.time_trigger_independent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

    }
}

@Composable
private fun GeoNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: GeoInputError?,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = error != null,
        supportingText = error?.let { { Text(it.userMessage().resolve()) } },
    )
}

@Composable
internal fun SupportingError(message: UiText, modifier: Modifier = Modifier) {
    Text(
        text = message.resolve(),
        modifier = modifier,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun GeoInputError.userMessage(): UiText = UiText.resource(
    when (this) {
        GeoInputError.REQUIRED -> R.string.required
        GeoInputError.NOT_A_NUMBER -> R.string.enter_a_number
        GeoInputError.OUT_OF_RANGE -> R.string.out_of_range
        GeoInputError.NOT_POSITIVE -> R.string.must_be_positive
    },
)

private fun ParseIssue.asUiText(): UiText = when (code) {
    ParseIssueCode.INVALID_COORDINATES -> UiText.resource(R.string.invalid_coordinates)
    ParseIssueCode.INVALID_TIME -> UiText.resource(R.string.invalid_time)
    ParseIssueCode.INVALID_DATE -> UiText.resource(R.string.invalid_date)
    ParseIssueCode.UNKNOWN -> UiText.Plain(message)
}
