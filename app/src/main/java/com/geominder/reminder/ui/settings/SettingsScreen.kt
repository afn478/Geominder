package com.geominder.reminder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geominder.reminder.settings.PermissionUiItem
import com.geominder.reminder.settings.SettingsPermissionAction
import com.geominder.reminder.settings.AccentTheme
import com.geominder.reminder.settings.ThemeMode

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onPermissionAction: (SettingsPermissionAction) -> Unit,
    onBack: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    backupStatus: String?,
    backupInProgress: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissionStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsScreen(
        state = state,
        onRadiusChange = viewModel::onRadiusChange,
        onThemeModeChange = viewModel::onThemeModeChange,
        onAccentThemeChange = viewModel::onAccentThemeChange,
        onSaveRadius = viewModel::saveRadius,
        onAddKeyword = viewModel::beginAddKeyword,
        onEditKeyword = viewModel::beginEditKeyword,
        onKeywordChange = viewModel::onKeywordChange,
        onKeywordTimeChange = viewModel::onKeywordTimeChange,
        onSaveKeyword = viewModel::saveKeyword,
        onCancelKeywordEdit = viewModel::cancelKeywordEdit,
        onRemoveKeyword = viewModel::removeKeyword,
        onResetKeywords = viewModel::resetKeywordTimes,
        onPermissionAction = onPermissionAction,
        onBack = onBack,
        onExportBackup = onExportBackup,
        onImportBackup = onImportBackup,
        backupStatus = backupStatus,
        backupInProgress = backupInProgress,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onRadiusChange: (String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentThemeChange: (AccentTheme) -> Unit,
    onSaveRadius: () -> Unit,
    onAddKeyword: () -> Unit,
    onEditKeyword: (String) -> Unit,
    onKeywordChange: (String) -> Unit,
    onKeywordTimeChange: (String) -> Unit,
    onSaveKeyword: () -> Unit,
    onCancelKeywordEdit: () -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onResetKeywords: () -> Unit,
    onPermissionAction: (SettingsPermissionAction) -> Unit,
    onBack: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    backupStatus: String?,
    backupInProgress: Boolean,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            AppearanceSection(state, onThemeModeChange, onAccentThemeChange)
            RadiusSection(
                state = state,
                onRadiusChange = onRadiusChange,
                onSaveRadius = onSaveRadius,
            )
            KeywordSection(
                state = state,
                onAddKeyword = onAddKeyword,
                onEditKeyword = onEditKeyword,
                onKeywordChange = onKeywordChange,
                onKeywordTimeChange = onKeywordTimeChange,
                onSaveKeyword = onSaveKeyword,
                onCancelKeywordEdit = onCancelKeywordEdit,
                onRemoveKeyword = onRemoveKeyword,
                onResetKeywords = onResetKeywords,
            )
            PermissionSection(
                items = state.permissionItems,
                onPermissionAction = onPermissionAction,
            )
            BackupSection(
                onExportBackup = onExportBackup,
                onImportBackup = onImportBackup,
                status = backupStatus,
                inProgress = backupInProgress,
            )
            state.persistenceError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AppearanceSection(
    state: SettingsUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentThemeChange: (AccentTheme) -> Unit,
) {
    SettingsSection(title = "Appearance") {
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.values().forEach { mode ->
                androidx.compose.material3.FilterChip(
                    selected = state.settings.themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    label = { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
        }
        Text("Accent", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentTheme.values().forEach { accent ->
                androidx.compose.material3.AssistChip(
                    onClick = { onAccentThemeChange(accent) },
                    label = { Text(accent.name.lowercase().replaceFirstChar(Char::uppercase)) },
                    leadingIcon = { androidx.compose.material3.Surface(
                        modifier = Modifier.padding(2.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                    ) { androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp)) } },
                )
            }
        }
    }
}

@Composable
private fun BackupSection(
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    status: String?,
    inProgress: Boolean,
) {
    SettingsSection(title = "Backup") {
        Text(
            text = "Export or restore reminders as an iCalendar (.ics) document.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExportBackup, enabled = !inProgress) {
                Text("Export .ics")
            }
            Button(onClick = onImportBackup, enabled = !inProgress) {
                Text("Import .ics")
            }
        }
        if (inProgress) {
            Text("Working…", style = MaterialTheme.typography.bodyMedium)
        }
        status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RadiusSection(
    state: SettingsUiState,
    onRadiusChange: (String) -> Unit,
    onSaveRadius: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    SettingsSection(title = "Location defaults") {
        Text(
            text = "Used for new arrival reminders. You can still change the radius on each reminder.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.radiusText,
            onValueChange = onRadiusChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Default geofence radius") },
            suffix = { Text("metres") },
            singleLine = true,
            isError = state.radiusError != null,
            supportingText = state.radiusError?.let { error -> { Text(error) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onSaveRadius()
                    focusManager.clearFocus()
                },
            ),
        )
        Button(onClick = onSaveRadius) { Text("Save radius") }
    }
}

@Composable
private fun KeywordSection(
    state: SettingsUiState,
    onAddKeyword: () -> Unit,
    onEditKeyword: (String) -> Unit,
    onKeywordChange: (String) -> Unit,
    onKeywordTimeChange: (String) -> Unit,
    onSaveKeyword: () -> Unit,
    onCancelKeywordEdit: () -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onResetKeywords: () -> Unit,
) {
    SettingsSection(title = "Preset times") {
        Text(
            text = "Keywords such as “morning” are recognized while you type a reminder.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.keywordEditorVisible) {
            KeywordEditor(
                state = state,
                onKeywordChange = onKeywordChange,
                onKeywordTimeChange = onKeywordTimeChange,
                onSaveKeyword = onSaveKeyword,
                onCancel = onCancelKeywordEdit,
            )
        } else {
            Button(onClick = onAddKeyword) { Text("Add preset") }
        }

        state.keywordTimes.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                ) {
                    Text(item.keyword, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = item.formattedTime,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    TextButton(onClick = { onEditKeyword(item.keyword) }) { Text("Edit") }
                    TextButton(onClick = { onRemoveKeyword(item.keyword) }) { Text("Remove") }
                }
            }
        }
        if (state.keywordTimes.isEmpty()) {
            Text(
                text = "No preset keywords. Add one to recognize a named time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onResetKeywords) { Text("Reset preset times") }
    }
}

@Composable
private fun KeywordEditor(
    state: SettingsUiState,
    onKeywordChange: (String) -> Unit,
    onKeywordTimeChange: (String) -> Unit,
    onSaveKeyword: () -> Unit,
    onCancel: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (state.isEditingKeyword) "Edit preset" else "Add preset",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = state.keywordText,
                onValueChange = onKeywordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Keyword") },
                placeholder = { Text("after work") },
                singleLine = true,
                isError = state.keywordError != null,
                supportingText = state.keywordError?.let { error -> { Text(error) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )
            OutlinedTextField(
                value = state.keywordTimeText,
                onValueChange = onKeywordTimeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Time") },
                placeholder = {
                    Text(com.geominder.reminder.settings.SettingsValidation.formatTime(
                        java.time.LocalTime.of(17, 30),
                        state.locale,
                    ))
                },
                singleLine = true,
                isError = state.keywordTimeError != null,
                supportingText = state.keywordTimeError?.let { error -> { Text(error) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onSaveKeyword()
                        focusManager.clearFocus()
                    },
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSaveKeyword) {
                    Text(if (state.isEditingKeyword) "Save preset" else "Add preset")
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun PermissionSection(
    items: List<PermissionUiItem>,
    onPermissionAction: (SettingsPermissionAction) -> Unit,
) {
    SettingsSection(title = "Permissions") {
        Text(
            text = "Statuses refresh when you return from Android settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider()
            PermissionRow(item, onPermissionAction)
        }
    }
}

@Composable
private fun PermissionRow(
    item: PermissionUiItem,
    onPermissionAction: (SettingsPermissionAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(item.title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = item.status,
            style = MaterialTheme.typography.labelLarge,
            color = if (item.action == null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Text(
            text = item.explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val action = item.action
        val label = item.actionLabel
        if (action != null && label != null) {
            TextButton(onClick = { onPermissionAction(action) }) { Text(label) }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}
