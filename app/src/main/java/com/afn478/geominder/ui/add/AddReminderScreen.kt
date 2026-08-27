package com.afn478.geominder.ui.add

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afn478.geominder.R
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderTag
import com.afn478.geominder.geofence.GeoInputError
import com.afn478.geominder.geofence.GeoInputField
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

@Composable
fun AddReminderRoute(
    viewModel: AddReminderViewModel,
    onReminderSaved: (Reminder) -> Unit,
    modifier: Modifier = Modifier,
    autoFocusSource: Boolean = false,
    bottomSheetMode: Boolean = false,
    fullPageMode: Boolean = false,
    sheetContentScrollEnabled: Boolean = true,
    onExpandBottomSheet: () -> Unit = {},
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
        onExpandedChange = viewModel::onExpandedChange,
        onDetailsExpandedChange = viewModel::onDetailsExpandedChange,
        onDateTimeChipClick = viewModel::beginDateTimeEdit,
        onDateEditChange = viewModel::onDateEditChange,
        onTimeEditChange = viewModel::onTimeEditChange,
        onCommitDateTimeEdit = viewModel::commitDateTimeEdit,
        onClearDateTime = viewModel::clearTimeTrigger,
        onGeoChipClick = viewModel::onGeoChipClick,
        onLocationTriggerEnabledChange = { enabled ->
            if (enabled) viewModel.showGeoEditor() else viewModel.hideGeoEditor()
        },
        onLatitudeChange = viewModel::onLatitudeChange,
        onLongitudeChange = viewModel::onLongitudeChange,
        onRadiusChange = viewModel::onRadiusChange,
        onPasteLocation = viewModel::pasteLocation,
        onLocate = viewModel::locate,
        onActiveFromEnabledChange = viewModel::onActiveFromEnabledChange,
        onActiveFromDateChange = viewModel::onActiveFromDateChange,
        onActiveFromTimeChange = viewModel::onActiveFromTimeChange,
        onSave = viewModel::save,
        modifier = modifier,
        autoFocusSource = autoFocusSource,
        bottomSheetMode = bottomSheetMode,
        fullPageMode = fullPageMode || viewModel.editingReminderId != null,
        sheetContentScrollEnabled = sheetContentScrollEnabled,
        onExpandBottomSheet = onExpandBottomSheet,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("DEPRECATION")
@Composable
fun AddReminderScreen(
    state: AddReminderUiState,
    onSourceTextChange: (String) -> Unit,
    onTagClick: (ReminderTag) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onDetailsExpandedChange: (Boolean) -> Unit,
    onDateTimeChipClick: () -> Unit,
    onDateEditChange: (String) -> Unit,
    onTimeEditChange: (String) -> Unit,
    onCommitDateTimeEdit: () -> Unit,
    onClearDateTime: () -> Unit,
    onGeoChipClick: () -> Unit,
    onLocationTriggerEnabledChange: (Boolean) -> Unit,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onRadiusChange: (String) -> Unit,
    onPasteLocation: (String) -> Unit,
    onLocate: () -> Unit,
    onActiveFromEnabledChange: (Boolean) -> Unit,
    onActiveFromDateChange: (String) -> Unit,
    onActiveFromTimeChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocusSource: Boolean = false,
    bottomSheetMode: Boolean = false,
    fullPageMode: Boolean = false,
    sheetContentScrollEnabled: Boolean = true,
    onExpandBottomSheet: () -> Unit = {},
) {
    if (bottomSheetMode) {
        BottomSheetComposerContent(
            state = state,
            onSourceTextChange = onSourceTextChange,
            onTagClick = onTagClick,
            onExpandedChange = onExpandedChange,
            onDateTimeChipClick = {
                onExpandBottomSheet()
                onDateTimeChipClick()
            },
            onDateEditChange = onDateEditChange,
            onTimeEditChange = onTimeEditChange,
            onCommitDateTimeEdit = onCommitDateTimeEdit,
            onClearDateTime = onClearDateTime,
            onGeoChipClick = {
                onExpandBottomSheet()
                onGeoChipClick()
            },
            onLocationTriggerEnabledChange = onLocationTriggerEnabledChange,
            onLatitudeChange = onLatitudeChange,
            onLongitudeChange = onLongitudeChange,
            onRadiusChange = onRadiusChange,
            onPasteLocation = onPasteLocation,
            onLocate = onLocate,
            onActiveFromEnabledChange = onActiveFromEnabledChange,
            onActiveFromDateChange = onActiveFromDateChange,
            onActiveFromTimeChange = onActiveFromTimeChange,
            onSave = onSave,
            autoFocusSource = autoFocusSource,
            scrollEnabled = sheetContentScrollEnabled,
            modifier = modifier,
        )
        return
    }

    if (fullPageMode || state.editingReminderId != null) {
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
            onActiveFromEnabledChange = onActiveFromEnabledChange,
            onActiveFromDateChange = onActiveFromDateChange,
            onActiveFromTimeChange = onActiveFromTimeChange,
            onSave = onSave,
            modifier = modifier,
        )
        return
    }

    val expansionSpec: FiniteAnimationSpec<IntSize> =
        MaterialTheme.motionScheme.defaultSpatialSpec()

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .heightIn(max = 640.dp)
                .animateContentSize(animationSpec = expansionSpec),
            shape = if (state.expanded) {
                MaterialTheme.shapes.extraLarge
            } else {
                SearchBarDefaults.inputFieldShape
            },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = SearchBarDefaults.TonalElevation,
        ) {
            Column {
                if (state.expanded) {
                    if (state.detailsExpanded) {
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                stringResource(R.string.reminder_details),
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
                                onActiveFromEnabledChange = onActiveFromEnabledChange,
                                onActiveFromDateChange = onActiveFromDateChange,
                                onActiveFromTimeChange = onActiveFromTimeChange,
                            )
                            TagSelector(state = state, onTagClick = onTagClick)

                            state.parseResult?.issues?.forEach { issue ->
                                SupportingError(issue.asUiText())
                            }
                        }
                    }

                    DetailsHandle(
                        expanded = state.detailsExpanded,
                        onClick = { onDetailsExpandedChange(!state.detailsExpanded) },
                    )
                    HorizontalDivider()

                    DetectionChips(
                        state = state,
                        onDateTimeChipClick = onDateTimeChipClick,
                        onGeoChipClick = onGeoChipClick,
                    )
                    state.saveError?.let {
                        SupportingError(it, Modifier.padding(horizontal = 16.dp))
                    }
                }

                HighlightedInputField(
                    highlightSpan = state.parseResult?.dateTime?.span,
                    query = state.sourceText,
                    onQueryChange = onSourceTextChange,
                    onSearch = { onSave() },
                    expanded = state.expanded,
                    onExpandedChange = onExpandedChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSaving,
                    autoFocusSource = autoFocusSource,
                    placeholder = { Text(stringResource(R.string.reminder_input_placeholder)) },
                    trailingIcon = {
                        IconButton(
                            onClick = onSave,
                            enabled = !state.isSaving,
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.save_reminder),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
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
    onActiveFromEnabledChange: (Boolean) -> Unit,
    onActiveFromDateChange: (String) -> Unit,
    onActiveFromTimeChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                modifier = Modifier.fillMaxWidth(),
            )

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
                onActiveFromEnabledChange = onActiveFromEnabledChange,
                onActiveFromDateChange = onActiveFromDateChange,
                onActiveFromTimeChange = onActiveFromTimeChange,
            )
        }
    }
}

@Composable
private fun BottomSheetComposerContent(
    state: AddReminderUiState,
    onSourceTextChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onTagClick: (ReminderTag) -> Unit,
    onDateTimeChipClick: () -> Unit,
    onDateEditChange: (String) -> Unit,
    onTimeEditChange: (String) -> Unit,
    onCommitDateTimeEdit: () -> Unit,
    onClearDateTime: () -> Unit,
    onGeoChipClick: () -> Unit,
    onLocationTriggerEnabledChange: (Boolean) -> Unit,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onRadiusChange: (String) -> Unit,
    onPasteLocation: (String) -> Unit,
    onLocate: () -> Unit,
    onActiveFromEnabledChange: (Boolean) -> Unit,
    onActiveFromDateChange: (String) -> Unit,
    onActiveFromTimeChange: (String) -> Unit,
    onSave: () -> Unit,
    autoFocusSource: Boolean,
    scrollEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(state.detailsExpanded) {
        if (!state.detailsExpanded) scrollState.scrollTo(0)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(
                state = scrollState,
                enabled = scrollEnabled,
                overscrollEffect = null,
            ),
    ) {
        DetectionChips(
            state = state,
            onDateTimeChipClick = onDateTimeChipClick,
            onGeoChipClick = onGeoChipClick,
        )
        state.saveError?.let {
            SupportingError(it, Modifier.padding(horizontal = 16.dp))
        }
        HighlightedInputField(
            highlightSpan = state.parseResult?.dateTime?.span,
            query = state.sourceText,
            onQueryChange = onSourceTextChange,
            onSearch = onSave,
            expanded = true,
            onExpandedChange = onExpandedChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            enabled = !state.isSaving,
            autoFocusSource = autoFocusSource,
            placeholder = { Text(stringResource(R.string.reminder_input_placeholder)) },
            trailingIcon = {
                SaveReminderIconButton(state = state, onSave = onSave)
            },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 680.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.reminder_details),
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
                    onActiveFromEnabledChange = onActiveFromEnabledChange,
                    onActiveFromDateChange = onActiveFromDateChange,
                    onActiveFromTimeChange = onActiveFromTimeChange,
                )
                TagSelector(state = state, onTagClick = onTagClick)
                state.parseResult?.issues?.forEach { issue ->
                    SupportingError(issue.asUiText())
                }
            }
        }
        Spacer(modifier = Modifier.navigationBarsPadding())
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

@Composable
private fun SaveReminderIconButton(
    state: AddReminderUiState,
    onSave: () -> Unit,
) {
    IconButton(
        onClick = onSave,
        enabled = !state.isSaving,
    ) {
        if (state.isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.save_reminder),
            )
        }
    }
}

@Composable
private fun DetailsHandle(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
            contentDescription = stringResource(
                if (expanded) R.string.hide_reminder_details else R.string.show_reminder_details,
            ),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighlightedInputField(
    query: String,
    highlightSpan: SourceSpan?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    autoFocusSource: Boolean = false,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val colors = SearchBarDefaults.inputFieldColors()
    LaunchedEffect(autoFocusSource) {
        if (autoFocusSource) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused && !expanded) onExpandedChange(true)
            },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        visualTransformation = DateTimeHighlightTransformation(
            span = highlightSpan,
            source = query,
            background = MaterialTheme.colorScheme.secondaryContainer,
            foreground = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = query,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = placeholder,
                trailingIcon = trailingIcon,
                shape = SearchBarDefaults.inputFieldShape,
                colors = colors,
                contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(),
                container = {},
            )
        },
    )
}

internal class DateTimeHighlightTransformation(
    private val span: SourceSpan?,
    private val source: String,
    private val background: Color,
    private val foreground: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val validSpan = span?.takeIf {
            it.start >= 0 &&
                it.endExclusive > it.start &&
                it.endExclusive <= source.length &&
                it.endExclusive <= text.length
        }
        if (validSpan == null || text.text != source) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val highlighted = AnnotatedString.Builder(source).apply {
            addStyle(
                SpanStyle(
                    background = background,
                    color = foreground,
                ),
                validSpan.start,
                validSpan.endExclusive,
            )
        }.toAnnotatedString()
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

@Composable
private fun DetectionChips(
    state: AddReminderUiState,
    onDateTimeChipClick: () -> Unit,
    onGeoChipClick: () -> Unit,
) {
    val timeDetection = state.parseResult?.dateTime
    val coordinateSummary = state.geoLabel?.let { it.resolveNearbyLabel() }
        ?: state.parseResult?.gps?.displayLabel
        ?: listOf(state.latitudeText, state.longitudeText)
            .takeIf { values -> values.all(String::isNotBlank) }
            ?.joinToString(", ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = onDateTimeChipClick,
            leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
            label = { Text(timeDetection?.displayLabel ?: "+") },
        )
        AssistChip(
            onClick = onGeoChipClick,
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
            label = { Text(coordinateSummary ?: "+") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DateTimeEditor(
    state: AddReminderUiState,
    onDateEditChange: (String) -> Unit,
    onTimeEditChange: (String) -> Unit,
    onCommit: () -> Unit,
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val fallbackDate = state.parseResult?.dateTime?.date ?: LocalDate.now().plusDays(1)
    val fallbackTime = state.parseResult?.dateTime?.time ?: LocalTime.now().plusHours(1)
    val selectedDate = parseDisplayedDate(state.dateEditText, locale) ?: fallbackDate
    val selectedTime = parseDisplayedTime(state.timeEditText, locale) ?: fallbackTime
    var datePickerVisible by remember { mutableStateOf(false) }
    var timePickerVisible by remember { mutableStateOf(false) }
    val datePickerState = key(selectedDate) {
        rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
    }
    val uses24HourTime = android.text.format.DateFormat.is24HourFormat(context)
    val timePickerState = key(selectedTime, uses24HourTime) {
        rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute,
            is24Hour = uses24HourTime,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.time_trigger), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickerField(
                value = state.dateEditText,
                label = stringResource(R.string.date),
                actionDescription = stringResource(R.string.select_date),
                onClick = { datePickerVisible = true },
                modifier = Modifier.weight(1.5f),
                isError = state.dateTimeEditError != null,
            )
            PickerField(
                value = state.timeEditText,
                label = stringResource(R.string.time),
                actionDescription = stringResource(R.string.select_time),
                onClick = { timePickerVisible = true },
                modifier = Modifier.weight(1f),
                isError = state.dateTimeEditError != null,
            )
        }
        state.dateTimeEditError?.let { SupportingError(it) }
        TextButton(onClick = onCommit) {
            Text(
                stringResource(
                    if (state.hasDateTimeDetection) R.string.update_time else R.string.next_hour,
                ),
            )
        }
    }

    if (datePickerVisible) {
        DatePickerDialog(
            onDismissRequest = { datePickerVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedMillis ->
                            val date = java.time.Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            onDateEditChange(formatDisplayedDate(date, locale))
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
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { timePickerVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeEditChange(
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = isError,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
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
    onActiveFromEnabledChange: (Boolean) -> Unit,
    onActiveFromDateChange: (String) -> Unit,
    onActiveFromTimeChange: (String) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val locatingDescription = stringResource(R.string.locating_current_position)
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
            ) {
                Icon(
                    Icons.Default.ContentPaste,
                    contentDescription = stringResource(R.string.paste_location),
                )
            }
        }
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
            IconButton(
                onClick = onLocate,
                enabled = !state.isLocating,
                modifier = Modifier.size(48.dp),
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
        GeoNumberField(
            value = state.radiusText,
            onValueChange = onRadiusChange,
            label = stringResource(R.string.radius_metres),
            error = state.geoInputErrors[GeoInputField.RADIUS],
            modifier = Modifier.fillMaxWidth(),
        )

        state.geoLabel?.let {
            Text(text = it.resolveNearbyLabel(), style = MaterialTheme.typography.bodyMedium)
        }
        state.locationError?.let { SupportingError(it) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(
                checked = state.activeFromEnabled,
                onCheckedChange = onActiveFromEnabledChange,
            )
            Text(stringResource(R.string.only_activate_location_from))
        }
        if (state.activeFromEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.activeFromDateText,
                    onValueChange = onActiveFromDateChange,
                    label = { Text(stringResource(R.string.active_date)) },
                    singleLine = true,
                    isError = state.activeFromError != null,
                    modifier = Modifier.weight(1.5f),
                )
                OutlinedTextField(
                    value = state.activeFromTimeText,
                    onValueChange = onActiveFromTimeChange,
                    label = { Text(stringResource(R.string.active_time)) },
                    singleLine = true,
                    isError = state.activeFromError != null,
                    modifier = Modifier.weight(1f),
                )
            }
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
private fun SupportingError(message: UiText, modifier: Modifier = Modifier) {
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
