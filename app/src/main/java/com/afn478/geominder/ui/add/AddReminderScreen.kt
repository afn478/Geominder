package com.afn478.geominder.ui.add

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
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
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.geofence.GeoInputError
import com.afn478.geominder.geofence.GeoInputField
import com.afn478.geominder.parser.SourceSpan
import com.afn478.geominder.parser.TemporalRole

@Composable
fun AddReminderRoute(
    viewModel: AddReminderViewModel,
    onReminderSaved: (Reminder) -> Unit,
    modifier: Modifier = Modifier,
    autoFocusSource: Boolean = false,
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
        onExpandedChange = viewModel::onExpandedChange,
        onDateTimeChipClick = viewModel::beginDateTimeEdit,
        onDateEditChange = viewModel::onDateEditChange,
        onTimeEditChange = viewModel::onTimeEditChange,
        onCommitDateTimeEdit = viewModel::commitDateTimeEdit,
        onCancelDateTimeEdit = viewModel::cancelDateTimeEdit,
        onShowGeoEditor = viewModel::showGeoEditor,
        onHideGeoEditor = viewModel::hideGeoEditor,
        onLatitudeChange = viewModel::onLatitudeChange,
        onLongitudeChange = viewModel::onLongitudeChange,
        onRadiusChange = viewModel::onRadiusChange,
        onLocate = viewModel::locate,
        onActiveFromEnabledChange = viewModel::onActiveFromEnabledChange,
        onActiveFromDateChange = viewModel::onActiveFromDateChange,
        onActiveFromTimeChange = viewModel::onActiveFromTimeChange,
        onSave = viewModel::save,
        modifier = modifier,
        autoFocusSource = autoFocusSource,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("DEPRECATION")
@Composable
fun AddReminderScreen(
    state: AddReminderUiState,
    onSourceTextChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onDateTimeChipClick: () -> Unit,
    onDateEditChange: (String) -> Unit,
    onTimeEditChange: (String) -> Unit,
    onCommitDateTimeEdit: () -> Unit,
    onCancelDateTimeEdit: () -> Unit,
    onShowGeoEditor: () -> Unit,
    onHideGeoEditor: () -> Unit,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onRadiusChange: (String) -> Unit,
    onLocate: () -> Unit,
    onActiveFromEnabledChange: (Boolean) -> Unit,
    onActiveFromDateChange: (String) -> Unit,
    onActiveFromTimeChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocusSource: Boolean = false,
) {
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
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, top = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = if (state.editingReminderId == null) "New reminder" else "Edit reminder",
                            style = MaterialTheme.typography.headlineSmall,
                        )

                        if (state.editingDateTimeDetectionId != null) {
                            DateTimeEditor(
                                state = state,
                                onDateEditChange = onDateEditChange,
                                onTimeEditChange = onTimeEditChange,
                                onCommit = onCommitDateTimeEdit,
                                onCancel = onCancelDateTimeEdit,
                            )
                        }

                        if (state.geoEditorVisible) {
                            GeoEditor(
                                state = state,
                                onLatitudeChange = onLatitudeChange,
                                onLongitudeChange = onLongitudeChange,
                                onRadiusChange = onRadiusChange,
                                onLocate = onLocate,
                                onRemove = onHideGeoEditor,
                                onActiveFromEnabledChange = onActiveFromEnabledChange,
                                onActiveFromDateChange = onActiveFromDateChange,
                                onActiveFromTimeChange = onActiveFromTimeChange,
                            )
                        } else {
                            TextButton(onClick = onShowGeoEditor) {
                                Icon(Icons.Default.AddLocationAlt, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Add location trigger")
                            }
                        }

                        state.parseResult?.issues?.forEach { issue ->
                            SupportingError(issue.message)
                        }
                        state.saveError?.let { SupportingError(it) }

                        Button(
                            onClick = onSave,
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val label = when {
                                state.isSaving -> "Saving…"
                                state.editingReminderId == null -> "Save reminder"
                                else -> "Save changes"
                            }
                            Text(label)
                        }

                        HorizontalDivider()
                    }

                    DetectionChips(
                        state = state,
                        onDateTimeChipClick = onDateTimeChipClick,
                        onGeoChipClick = onShowGeoEditor,
                    )
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
                    placeholder = { Text("Remind me tomorrow at 8…") },
                    trailingIcon = {
                        if (state.sourceText.isNotBlank()) {
                            TextButton(
                                onClick = onSave,
                                enabled = !state.isSaving,
                            ) {
                                Text("Save")
                            }
                        }
                    },
                )
            }
        }
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
    val coordinateSummary = state.geoLabel
        ?: state.parseResult?.gps?.displayLabel
        ?: listOf(state.latitudeText, state.longitudeText)
            .takeIf { values -> values.all(String::isNotBlank) }
            ?.joinToString(", ")
    if (timeDetection == null && coordinateSummary == null) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        timeDetection?.let {
            AssistChip(
                onClick = onDateTimeChipClick,
                label = {
                    val prefix = if (it.role == TemporalRole.GEO_ACTIVE_FROM) {
                        "Active from"
                    } else {
                        "Time"
                    }
                    Text("$prefix · ${it.displayLabel}")
                },
            )
        }
        coordinateSummary?.let {
            AssistChip(
                onClick = onGeoChipClick,
                label = { Text("Location · $it") },
            )
        }
    }
}

@Composable
private fun DateTimeEditor(
    state: AddReminderUiState,
    onDateEditChange: (String) -> Unit,
    onTimeEditChange: (String) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Edit time trigger", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.dateEditText,
                onValueChange = onDateEditChange,
                label = { Text("Date") },
                singleLine = true,
                modifier = Modifier.weight(1.5f),
                isError = state.dateTimeEditError != null,
            )
            OutlinedTextField(
                value = state.timeEditText,
                onValueChange = onTimeEditChange,
                label = { Text("Time") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                isError = state.dateTimeEditError != null,
            )
        }
        state.dateTimeEditError?.let { SupportingError(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCommit) { Text("Update time") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun GeoEditor(
    state: AddReminderUiState,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onRadiusChange: (String) -> Unit,
    onLocate: () -> Unit,
    onRemove: () -> Unit,
    onActiveFromEnabledChange: (Boolean) -> Unit,
    onActiveFromDateChange: (String) -> Unit,
    onActiveFromTimeChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Location trigger", style = MaterialTheme.typography.titleMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GeoNumberField(
                value = state.latitudeText,
                onValueChange = onLatitudeChange,
                label = "Latitude",
                error = state.geoInputErrors[GeoInputField.LATITUDE],
                modifier = Modifier.weight(1f),
            )
            GeoNumberField(
                value = state.longitudeText,
                onValueChange = onLongitudeChange,
                label = "Longitude",
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
                            .semantics { contentDescription = "Locating current position" },
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Locate current position",
                    )
                }
            }
        }
        GeoNumberField(
            value = state.radiusText,
            onValueChange = onRadiusChange,
            label = "Radius (metres)",
            error = state.geoInputErrors[GeoInputField.RADIUS],
            modifier = Modifier.fillMaxWidth(),
        )

        state.geoLabel?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium)
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
            Text("Only active from a specific time")
        }
        if (state.activeFromEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.activeFromDateText,
                    onValueChange = onActiveFromDateChange,
                    label = { Text("Active date") },
                    singleLine = true,
                    isError = state.activeFromError != null,
                    modifier = Modifier.weight(1.5f),
                )
                OutlinedTextField(
                    value = state.activeFromTimeText,
                    onValueChange = onActiveFromTimeChange,
                    label = { Text("Active time") },
                    singleLine = true,
                    isError = state.activeFromError != null,
                    modifier = Modifier.weight(1f),
                )
            }
            state.activeFromError?.let { SupportingError(it) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove location trigger")
            }
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
        supportingText = error?.let { { Text(it.userMessage()) } },
    )
}

@Composable
private fun SupportingError(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun GeoInputError.userMessage(): String = when (this) {
    GeoInputError.REQUIRED -> "Required"
    GeoInputError.NOT_A_NUMBER -> "Enter a number"
    GeoInputError.OUT_OF_RANGE -> "Out of range"
    GeoInputError.NOT_POSITIVE -> "Must be positive"
}
