package com.afn478.geominder.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afn478.geominder.BuildConfig
import com.afn478.geominder.R
import com.afn478.geominder.geofence.GeoInputError
import com.afn478.geominder.geofence.GeoInputField
import com.afn478.geominder.localization.UiText
import com.afn478.geominder.localization.resource
import com.afn478.geominder.settings.AccentTheme
import com.afn478.geominder.settings.PermissionUiItem
import com.afn478.geominder.settings.SettingsPermissionAction
import com.afn478.geominder.settings.ThemeMode
import com.afn478.geominder.ui.appbar.ReachableScaffold
import com.afn478.geominder.ui.theme.accentSwatchColor
import com.afn478.geominder.ui.theme.resolveDarkTheme
import com.afn478.geominder.ui.text.resolve

private enum class SettingsSubsection(@androidx.annotation.StringRes val titleRes: Int) {
    APPEARANCE(R.string.appearance),
    LOCATION(R.string.location_defaults),
    PRESET_LOCATIONS(R.string.preset_locations),
    PRESET_TIMES(R.string.preset_times),
    PERMISSIONS(R.string.permissions),
    BACKUP(R.string.backup),
    DEBUG(R.string.debug),
}

private data class SettingsSubsectionEntry(
    val subsection: SettingsSubsection,
    @androidx.annotation.StringRes val summaryRes: Int,
    val icon: ImageVector,
)

private val settingsSubsectionEntries = listOf(
    SettingsSubsectionEntry(
        subsection = SettingsSubsection.APPEARANCE,
        summaryRes = R.string.theme_and_accent_color,
        icon = Icons.Default.Palette,
    ),
    SettingsSubsectionEntry(
        subsection = SettingsSubsection.LOCATION,
        summaryRes = R.string.default_geofence_radius_summary,
        icon = Icons.Default.LocationOn,
    ),
    SettingsSubsectionEntry(
        subsection = SettingsSubsection.PRESET_LOCATIONS,
        summaryRes = R.string.recognized_keyword_locations,
        icon = Icons.Default.LocationOn,
    ),
    SettingsSubsectionEntry(
        subsection = SettingsSubsection.PRESET_TIMES,
        summaryRes = R.string.recognized_keyword_times,
        icon = Icons.Default.Schedule,
    ),
    SettingsSubsectionEntry(
        subsection = SettingsSubsection.PERMISSIONS,
        summaryRes = R.string.permission_access_summary,
        icon = Icons.Default.Security,
    ),
    SettingsSubsectionEntry(
        subsection = SettingsSubsection.BACKUP,
        summaryRes = R.string.export_restore_reminders,
        icon = Icons.Default.Folder,
    ),
).let { entries ->
    if (BuildConfig.DEBUG) {
        entries + SettingsSubsectionEntry(
            subsection = SettingsSubsection.DEBUG,
            summaryRes = R.string.test_lock_screen_alert,
            icon = Icons.Default.BugReport,
        )
    } else {
        entries
    }
}

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onPermissionAction: (SettingsPermissionAction) -> Unit,
    onBack: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    backupStatus: UiText?,
    backupInProgress: Boolean,
    onShowDebugFullScreenReminder: () -> Unit,
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
        onRemoveTimeExpressionsFromTextChange = viewModel::onRemoveTimeExpressionsFromTextChange,
        onSaveRadius = viewModel::saveRadius,
        onAddKeyword = viewModel::beginAddKeyword,
        onEditKeyword = viewModel::beginEditKeyword,
        onKeywordChange = viewModel::onKeywordChange,
        onKeywordTimeChange = viewModel::onKeywordTimeChange,
        onSaveKeyword = viewModel::saveKeyword,
        onCancelKeywordEdit = viewModel::cancelKeywordEdit,
        onRemoveKeyword = viewModel::removeKeyword,
        onResetKeywords = viewModel::resetKeywordTimes,
        onAddLocation = viewModel::beginAddLocation,
        onEditLocation = viewModel::beginEditLocation,
        onLocationKeywordChange = viewModel::onLocationKeywordChange,
        onLocationLatitudeChange = viewModel::onLocationLatitudeChange,
        onLocationLongitudeChange = viewModel::onLocationLongitudeChange,
        onLocationRadiusChange = viewModel::onLocationRadiusChange,
        onPasteLocation = viewModel::pasteLocation,
        onLocateLocation = viewModel::locateLocation,
        onSaveLocation = viewModel::saveLocation,
        onCancelLocationEdit = viewModel::cancelLocationEdit,
        onRemoveLocation = viewModel::removeLocation,
        onResetLocations = viewModel::resetKeywordLocations,
        onPermissionAction = onPermissionAction,
        onBack = onBack,
        onExportBackup = onExportBackup,
        onImportBackup = onImportBackup,
        backupStatus = backupStatus,
        backupInProgress = backupInProgress,
        onShowDebugFullScreenReminder = onShowDebugFullScreenReminder,
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
    onRemoveTimeExpressionsFromTextChange: (Boolean) -> Unit,
    onSaveRadius: () -> Unit,
    onAddKeyword: () -> Unit,
    onEditKeyword: (String) -> Unit,
    onKeywordChange: (String) -> Unit,
    onKeywordTimeChange: (String) -> Unit,
    onSaveKeyword: () -> Unit,
    onCancelKeywordEdit: () -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onResetKeywords: () -> Unit,
    onAddLocation: () -> Unit,
    onEditLocation: (String) -> Unit,
    onLocationKeywordChange: (String) -> Unit,
    onLocationLatitudeChange: (String) -> Unit,
    onLocationLongitudeChange: (String) -> Unit,
    onLocationRadiusChange: (String) -> Unit,
    onPasteLocation: (String) -> Unit,
    onLocateLocation: () -> Unit,
    onSaveLocation: () -> Unit,
    onCancelLocationEdit: () -> Unit,
    onRemoveLocation: (String) -> Unit,
    onResetLocations: () -> Unit,
    onPermissionAction: (SettingsPermissionAction) -> Unit,
    onBack: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    backupStatus: UiText?,
    backupInProgress: Boolean,
    onShowDebugFullScreenReminder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedSubsection by rememberSaveable {
        mutableStateOf<SettingsSubsection?>(null)
    }
    BackHandler(enabled = selectedSubsection != null) {
        selectedSubsection = null
    }

    val pageScrollState = rememberScrollState()
    LaunchedEffect(selectedSubsection) {
        pageScrollState.scrollTo(0)
    }

    Crossfade(
        targetState = selectedSubsection,
        modifier = modifier,
        label = "Settings subsection",
    ) { subsection ->
        ReachableScaffold(
            title = stringResource(subsection?.titleRes ?: R.string.settings),
            compactTitleStartPadding = 56.dp,
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (subsection == null) onBack() else selectedSubsection = null
                    },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            },
        ) { contentPadding ->
            val pageModifier = Modifier
                .fillMaxSize()
                .verticalScroll(pageScrollState)
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()

            when (subsection) {
                null -> SettingsSubsectionList(
                    modifier = pageModifier,
                    persistenceError = state.persistenceError,
                    onSelect = { selectedSubsection = it },
                )

                SettingsSubsection.APPEARANCE -> SettingsSubpage(pageModifier) {
                    AppearanceSection(state, onThemeModeChange, onAccentThemeChange)
                }

                SettingsSubsection.LOCATION -> SettingsSubpage(pageModifier) {
                    RadiusSection(
                        state = state,
                        onRadiusChange = onRadiusChange,
                        onSaveRadius = onSaveRadius,
                    )
                }

                SettingsSubsection.PRESET_LOCATIONS -> SettingsSubpage(pageModifier) {
                    LocationPresetSection(
                        state = state,
                        onAddLocation = onAddLocation,
                        onEditLocation = onEditLocation,
                        onLocationKeywordChange = onLocationKeywordChange,
                        onLocationLatitudeChange = onLocationLatitudeChange,
                        onLocationLongitudeChange = onLocationLongitudeChange,
                        onLocationRadiusChange = onLocationRadiusChange,
                        onPasteLocation = onPasteLocation,
                        onLocateLocation = onLocateLocation,
                        onSaveLocation = onSaveLocation,
                        onCancelLocationEdit = onCancelLocationEdit,
                        onRemoveLocation = onRemoveLocation,
                        onResetLocations = onResetLocations,
                    )
                }

                SettingsSubsection.PRESET_TIMES -> SettingsSubpage(pageModifier) {
                    KeywordSection(
                        state = state,
                        onAddKeyword = onAddKeyword,
                        onEditKeyword = onEditKeyword,
                        onKeywordChange = onKeywordChange,
                        onKeywordTimeChange = onKeywordTimeChange,
                        onRemoveTimeExpressionsFromTextChange =
                            onRemoveTimeExpressionsFromTextChange,
                        onSaveKeyword = onSaveKeyword,
                        onCancelKeywordEdit = onCancelKeywordEdit,
                        onRemoveKeyword = onRemoveKeyword,
                        onResetKeywords = onResetKeywords,
                    )
                }

                SettingsSubsection.PERMISSIONS -> SettingsSubpage(pageModifier) {
                    PermissionSection(
                        items = state.permissionItems,
                        onPermissionAction = onPermissionAction,
                    )
                }

                SettingsSubsection.BACKUP -> SettingsSubpage(pageModifier) {
                    BackupSection(
                        onExportBackup = onExportBackup,
                        onImportBackup = onImportBackup,
                        status = backupStatus,
                        inProgress = backupInProgress,
                    )
                }

                SettingsSubsection.DEBUG -> if (BuildConfig.DEBUG) {
                    SettingsSubpage(pageModifier) {
                        DebugAlertSection(onShowDebugFullScreenReminder)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSubsectionList(
    modifier: Modifier,
    persistenceError: UiText?,
    onSelect: (SettingsSubsection) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        settingsSubsectionEntries.forEach { entry ->
            SettingsSubsectionRow(entry = entry, onClick = { onSelect(entry.subsection) })
        }
        persistenceError?.let {
            Text(
                text = it.resolve(),
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SettingsSubsectionRow(
    entry: SettingsSubsectionEntry,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(
                    R.string.open_settings_section,
                    stringResource(entry.subsection.titleRes),
                ),
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                if (entry.subsection == SettingsSubsection.PRESET_LOCATIONS) {
                    PresetLocationSettingsIcon()
                } else {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(entry.subsection.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(entry.summaryRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PresetLocationSettingsIcon() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(14.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(1.dp),
            )
        }
    }
}

@Composable
private fun SettingsSubpage(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun AppearanceSection(
    state: SettingsUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentThemeChange: (AccentTheme) -> Unit,
) {
    val darkTheme = resolveDarkTheme(
        systemIsDark = isSystemInDarkTheme(),
        themeMode = state.settings.themeMode,
    )

    SettingsSection(title = stringResource(R.string.appearance)) {
        Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.values().forEach { mode ->
                androidx.compose.material3.FilterChip(
                    selected = state.settings.themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    label = { Text(mode.localizedLabel()) },
                )
            }
        }
        Text(stringResource(R.string.accent), style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AccentTheme.values().toList(), key = { accent -> accent.name }) { accent ->
                val selected = state.settings.accentTheme == accent
                val label = accent.localizedLabel()
                val accessibilityLabel = stringResource(
                    if (selected) R.string.accent_color_selected else R.string.accent_color,
                    label,
                )
                IconButton(
                    onClick = { onAccentThemeChange(accent) },
                    modifier = Modifier.semantics {
                        contentDescription = accessibilityLabel
                    },
                ) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = accentSwatchColor(
                            accentTheme = accent,
                            darkTheme = darkTheme,
                            dynamicColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(7.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupSection(
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    status: UiText?,
    inProgress: Boolean,
) {
    SettingsSection(title = stringResource(R.string.backup)) {
        Text(
            text = stringResource(R.string.backup_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExportBackup, enabled = !inProgress) {
                Text(stringResource(R.string.export_ics))
            }
            Button(onClick = onImportBackup, enabled = !inProgress) {
                Text(stringResource(R.string.import_ics))
            }
        }
        if (inProgress) {
            Text(stringResource(R.string.working), style = MaterialTheme.typography.bodyMedium)
        }
        status?.let {
            Text(
                text = it.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DebugAlertSection(
    onShowDebugFullScreenReminder: () -> Unit,
) {
    SettingsSection(title = stringResource(R.string.debug)) {
        Text(
            text = stringResource(R.string.debug_alert_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onShowDebugFullScreenReminder) {
            Text(stringResource(R.string.show_full_screen_reminder))
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
    SettingsSection(title = stringResource(R.string.location_defaults)) {
        Text(
            text = stringResource(R.string.location_defaults_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.radiusText,
            onValueChange = onRadiusChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.default_geofence_radius)) },
            suffix = { Text(stringResource(R.string.metres)) },
            singleLine = true,
            isError = state.radiusError != null,
            supportingText = state.radiusError?.let { error -> { Text(error.resolve()) } },
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
        Button(onClick = onSaveRadius) { Text(stringResource(R.string.save_radius)) }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun LocationPresetSection(
    state: SettingsUiState,
    onAddLocation: () -> Unit,
    onEditLocation: (String) -> Unit,
    onLocationKeywordChange: (String) -> Unit,
    onLocationLatitudeChange: (String) -> Unit,
    onLocationLongitudeChange: (String) -> Unit,
    onLocationRadiusChange: (String) -> Unit,
    onPasteLocation: (String) -> Unit,
    onLocateLocation: () -> Unit,
    onSaveLocation: () -> Unit,
    onCancelLocationEdit: () -> Unit,
    onRemoveLocation: (String) -> Unit,
    onResetLocations: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    SettingsSection(title = stringResource(R.string.preset_locations)) {
        Text(
            text = stringResource(R.string.preset_locations_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.locationEditorVisible) {
            LocationPresetEditor(
                state = state,
                onKeywordChange = onLocationKeywordChange,
                onLatitudeChange = onLocationLatitudeChange,
                onLongitudeChange = onLocationLongitudeChange,
                onRadiusChange = onLocationRadiusChange,
                onPasteLocation = {
                    onPasteLocation(clipboardManager.getText()?.text.orEmpty())
                },
                onLocate = onLocateLocation,
                onSave = onSaveLocation,
                onCancel = onCancelLocationEdit,
            )
        } else {
            Button(onClick = onAddLocation) { Text(stringResource(R.string.add_preset_location)) }
        }

        state.keywordLocations.forEachIndexed { index, item ->
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
                        text = stringResource(
                            R.string.preset_location_summary,
                            item.formattedCoordinates,
                            item.formattedRadius,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    TextButton(onClick = { onEditLocation(item.keyword) }) {
                        Text(stringResource(R.string.edit))
                    }
                    TextButton(onClick = { onRemoveLocation(item.keyword) }) {
                        Text(stringResource(R.string.remove))
                    }
                }
            }
        }
        if (state.keywordLocations.isEmpty()) {
            Text(
                text = stringResource(R.string.no_preset_locations),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onResetLocations) {
            Text(stringResource(R.string.reset_preset_locations))
        }
    }
}

@Composable
private fun LocationPresetEditor(
    state: SettingsUiState,
    onKeywordChange: (String) -> Unit,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onRadiusChange: (String) -> Unit,
    onPasteLocation: () -> Unit,
    onLocate: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val locatingDescription = stringResource(R.string.locating_current_position)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (state.isEditingLocation) R.string.edit_preset_location
                        else R.string.add_preset_location,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onPasteLocation) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = stringResource(R.string.paste_location),
                    )
                }
            }
            OutlinedTextField(
                value = state.locationKeywordText,
                onValueChange = onKeywordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.keyword)) },
                placeholder = { Text(stringResource(R.string.location_keyword_placeholder)) },
                singleLine = true,
                isError = state.locationKeywordError != null,
                supportingText = state.locationKeywordError?.let { error -> { Text(error.resolve()) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PresetLocationNumberField(
                    value = state.locationLatitudeText,
                    onValueChange = onLatitudeChange,
                    label = stringResource(R.string.latitude),
                    error = state.locationInputErrors[GeoInputField.LATITUDE],
                    modifier = Modifier.weight(1f),
                )
                PresetLocationNumberField(
                    value = state.locationLongitudeText,
                    onValueChange = onLongitudeChange,
                    label = stringResource(R.string.longitude),
                    error = state.locationInputErrors[GeoInputField.LONGITUDE],
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onLocate,
                    enabled = !state.isLocatingLocation,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (state.isLocatingLocation) {
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
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = stringResource(R.string.locate_current_position),
                        )
                    }
                }
            }
            PresetLocationNumberField(
                value = state.locationRadiusText,
                onValueChange = onRadiusChange,
                label = stringResource(R.string.radius_metres),
                error = state.locationInputErrors[GeoInputField.RADIUS],
                modifier = Modifier.fillMaxWidth(),
            )
            state.locationError?.let {
                Text(
                    text = it.resolve(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) {
                    Text(
                        stringResource(
                            if (state.isEditingLocation) R.string.save_preset_location
                            else R.string.add_preset_location,
                        ),
                    )
                }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
private fun PresetLocationNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: GeoInputError?,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it.userMessage().resolve()) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

@Composable
private fun KeywordSection(
    state: SettingsUiState,
    onAddKeyword: () -> Unit,
    onEditKeyword: (String) -> Unit,
    onKeywordChange: (String) -> Unit,
    onKeywordTimeChange: (String) -> Unit,
    onRemoveTimeExpressionsFromTextChange: (Boolean) -> Unit,
    onSaveKeyword: () -> Unit,
    onCancelKeywordEdit: () -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onResetKeywords: () -> Unit,
) {
    SettingsSection(title = stringResource(R.string.preset_times)) {
        Text(
            text = stringResource(R.string.preset_times_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.remove_time_expressions_from_text),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.remove_time_expressions_from_text_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.settings.removeTimeExpressionsFromText,
                onCheckedChange = onRemoveTimeExpressionsFromTextChange,
            )
        }

        if (state.keywordEditorVisible) {
            KeywordEditor(
                state = state,
                onKeywordChange = onKeywordChange,
                onKeywordTimeChange = onKeywordTimeChange,
                onSaveKeyword = onSaveKeyword,
                onCancel = onCancelKeywordEdit,
            )
        } else {
            Button(onClick = onAddKeyword) { Text(stringResource(R.string.add_preset)) }
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
                    TextButton(onClick = { onEditKeyword(item.keyword) }) {
                        Text(stringResource(R.string.edit))
                    }
                    TextButton(onClick = { onRemoveKeyword(item.keyword) }) {
                        Text(stringResource(R.string.remove))
                    }
                }
            }
        }
        if (state.keywordTimes.isEmpty()) {
            Text(
                text = stringResource(R.string.no_preset_keywords),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onResetKeywords) {
            Text(stringResource(R.string.reset_preset_times))
        }
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
                text = stringResource(
                    if (state.isEditingKeyword) R.string.edit_preset else R.string.add_preset,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = state.keywordText,
                onValueChange = onKeywordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.keyword)) },
                placeholder = { Text(stringResource(R.string.keyword_placeholder)) },
                singleLine = true,
                isError = state.keywordError != null,
                supportingText = state.keywordError?.let { error -> { Text(error.resolve()) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )
            OutlinedTextField(
                value = state.keywordTimeText,
                onValueChange = onKeywordTimeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.time)) },
                placeholder = {
                    Text(com.afn478.geominder.settings.SettingsValidation.formatTime(
                        java.time.LocalTime.of(17, 30),
                        state.locale,
                    ))
                },
                singleLine = true,
                isError = state.keywordTimeError != null,
                supportingText = state.keywordTimeError?.let { error -> { Text(error.resolve()) } },
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
                    Text(
                        stringResource(
                            if (state.isEditingKeyword) R.string.save_preset else R.string.add_preset,
                        ),
                    )
                }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
private fun PermissionSection(
    items: List<PermissionUiItem>,
    onPermissionAction: (SettingsPermissionAction) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.permissions)) {
        Text(
            text = stringResource(R.string.permissions_description),
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
        Text(item.title.resolve(), style = MaterialTheme.typography.titleMedium)
        Text(
            text = item.status.resolve(),
            style = MaterialTheme.typography.labelLarge,
            color = if (item.action == null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Text(
            text = item.explanation.resolve(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val action = item.action
        val label = item.actionLabel
        if (action != null && label != null) {
            TextButton(onClick = { onPermissionAction(action) }) { Text(label.resolve()) }
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

@Composable
private fun ThemeMode.localizedLabel(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.BLACK -> R.string.theme_black
    },
)

@Composable
private fun AccentTheme.localizedLabel(): String = stringResource(
    when (this) {
        AccentTheme.DYNAMIC -> R.string.accent_dynamic
        AccentTheme.SUNSET -> R.string.accent_sunset
        AccentTheme.OCEAN -> R.string.accent_ocean
        AccentTheme.FOREST -> R.string.accent_forest
        AccentTheme.PLUM -> R.string.accent_plum
        AccentTheme.CITRUS -> R.string.accent_citrus
        AccentTheme.ROSE -> R.string.accent_rose
        AccentTheme.SKY -> R.string.accent_sky
        AccentTheme.SLATE -> R.string.accent_slate
    },
)
