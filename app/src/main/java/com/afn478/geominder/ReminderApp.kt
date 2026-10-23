package com.afn478.geominder

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import com.afn478.geominder.ui.appbar.LocalReachableHeaderMemory
import com.afn478.geominder.ui.appbar.ReachableHeaderMemory
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import com.afn478.geominder.ui.motion.SharedAxis
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.afn478.geominder.alert.AlertIntentFactory
import com.afn478.geominder.backup.CalendarDocumentContract
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.localization.AppLanguagePreferences
import com.afn478.geominder.settings.ReminderSettings
import com.afn478.geominder.settings.SettingsPermissionAction
import com.afn478.geominder.ui.add.AddReminderComposerViewModel
import com.afn478.geominder.ui.add.AddReminderRoute
import com.afn478.geominder.ui.add.AddReminderViewModel
import com.afn478.geominder.ui.add.AddReminderViewModelFactory
import com.afn478.geominder.ui.add.QuickAddBar
import com.afn478.geominder.ui.appbar.ReachableScaffold
import com.afn478.geominder.ui.backup.BackupViewModel
import com.afn478.geominder.ui.backup.BackupViewModelFactory
import com.afn478.geominder.ui.detail.ReminderDetailRoute
import com.afn478.geominder.ui.detail.ReminderDetailViewModel
import com.afn478.geominder.ui.detail.ReminderDetailViewModelFactory
import com.afn478.geominder.ui.list.ReminderListRoute
import com.afn478.geominder.ui.map.LocationPickerRoute
import com.afn478.geominder.ui.map.LocationPickerViewModel
import com.afn478.geominder.ui.map.LocationPickerViewModelFactory
import com.afn478.geominder.ui.map.PickedLocation
import com.afn478.geominder.ui.list.ReminderListViewModel
import com.afn478.geominder.ui.list.ReminderListViewModelFactory
import com.afn478.geominder.ui.settings.SettingsRoute
import com.afn478.geominder.ui.settings.SettingsViewModel
import com.afn478.geominder.ui.settings.SettingsViewModelFactory
import java.util.Locale

@Composable
fun ReminderApp(
    container: AppContainer,
    onPermissionAction: (SettingsPermissionAction) -> Unit,
    onLanguageChanged: () -> Unit = {},
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val localeProvider = { AppLanguagePreferences.locale(context) }
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val composerViewModel: AddReminderComposerViewModel = viewModel()
    val draftSession by composerViewModel.session.collectAsStateWithLifecycle()
    // The quick-add bar always has a draft to type into; a save replaces it with a fresh one.
    LaunchedEffect(draftSession) {
        if (draftSession == null) composerViewModel.open()
    }
    val backupViewModel: BackupViewModel = viewModel(
        factory = BackupViewModelFactory(container.safReminderBackup),
    )
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.data?.let(backupViewModel::exportTo)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.data?.let(backupViewModel::importFrom)
    }

    val headerMemory = rememberSaveable(saver = ReachableHeaderMemory.Saver) { ReachableHeaderMemory() }
    CompositionLocalProvider(LocalReachableHeaderMemory provides headerMemory) {
    NavHost(
        navController = navController,
        startDestination = Routes.LIST,
        enterTransition = { SharedAxis.forwardEnter() },
        exitTransition = { SharedAxis.forwardExit() },
        popEnterTransition = { SharedAxis.backwardEnter() },
        popExitTransition = { SharedAxis.backwardExit() },
    ) {
        composable(Routes.LIST) {
            val listViewModel: ReminderListViewModel = viewModel(
                key = "reminder-list-${settings.keywordLanguage.languageTag}",
                factory = ReminderListViewModelFactory(
                    repository = container.reminderRepository,
                    scheduleCommandHandler = container.schedulingCoordinator,
                    settingsRepository = container.settingsRepository,
                    localeProvider = localeProvider,
                ),
            )
            ReminderListRoute(
                viewModel = listViewModel,
                quickAddBar = { idleHeader ->
                    draftSession?.let { session ->
                        QuickAddBar(
                            viewModel = draftViewModel(session, container, settings, localeProvider),
                            onOpenDetails = { focusText ->
                                listViewModel.exitSelectionMode()
                                navController.navigate(Routes.new(focusText))
                            },
                            onSaved = composerViewModel::reset,
                            onDiscardDraft = composerViewModel::reset,
                            idleHeader = idleHeader,
                        )
                    } ?: idleHeader()
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenReminder = { reminderId ->
                    navController.navigate(Routes.detail(reminderId.value))
                },
                onEditReminder = { reminderId ->
                    navController.navigate(Routes.edit(reminderId.value))
                },
            )
        }
        composable(
            route = Routes.NEW,
            arguments = listOf(
                navArgument(Routes.ARG_FOCUS) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val session = draftSession ?: return@composable
            ReminderEditorHost(
                viewModel = draftViewModel(session, container, settings, localeProvider),
                backStackEntry = backStackEntry,
                onPickOnMap = { latitude, longitude, radius ->
                    navController.navigate(Routes.pickLocation(latitude, longitude, radius))
                },
                title = stringResource(R.string.add_reminder),
                autoFocusText = backStackEntry.arguments?.getBoolean(Routes.ARG_FOCUS) == true,
                // Leaving without saving keeps the draft; it is still in the quick-add bar.
                onBack = { navController.popBackStack() },
                onSaved = {
                    navController.popBackStack()
                    composerViewModel.reset()
                },
            )
        }
        composable(Routes.EDIT) { backStackEntry ->
            val reminderId = backStackEntry.reminderIdArgument()
            if (reminderId == null) {
                PopInvalidRoute(navController::popBackStack)
            } else {
                ReminderEditorHost(
                    viewModel = viewModel(
                        key = "edit-${settings.editorKey}",
                        factory = editorViewModelFactory(container, settings, localeProvider, reminderId),
                    ),
                    backStackEntry = backStackEntry,
                    onPickOnMap = { latitude, longitude, radius ->
                        navController.navigate(Routes.pickLocation(latitude, longitude, radius))
                    },
                    title = stringResource(R.string.edit_reminder),
                    onBack = { navController.popBackStack() },
                    // Return to wherever editing started: the list, or the detail page that now
                    // shows the saved changes.
                    onSaved = { navController.popBackStack() },
                )
            }
        }
        composable(Routes.REMINDER_DETAIL) { backStackEntry ->
            val reminderId = backStackEntry.reminderIdArgument()
            if (reminderId == null) {
                PopInvalidRoute(navController::popBackStack)
            } else {
                val detailViewModel: ReminderDetailViewModel = viewModel(
                    key = "reminder-detail-${reminderId.value}-${localeProvider().toLanguageTag()}",
                    factory = ReminderDetailViewModelFactory(
                        repository = container.reminderRepository,
                        reminderId = reminderId,
                        locale = localeProvider(),
                    ),
                )
                ReminderDetailRoute(
                    viewModel = detailViewModel,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.edit(reminderId.value)) },
                )
            }
        }
        composable(Routes.SETTINGS) { backStackEntry ->
            val settingsViewModel: SettingsViewModel = viewModel(
                key = "settings-${settings.keywordLanguage.languageTag}",
                factory = SettingsViewModelFactory(
                    repository = container.settingsRepository,
                    permissionStatusProvider = container.permissionStatusProvider,
                    localeProvider = localeProvider,
                    locationProvider = container.currentLocationProvider,
                    mapLinkResolver = container.mapLinkResolver,
                ),
            )
            ConsumePickedLocation(backStackEntry) { picked ->
                settingsViewModel.applyPickedLocation(picked.latitude, picked.longitude, picked.radiusMeters)
            }
            SettingsRoute(
                viewModel = settingsViewModel,
                onPickLocationOnMap = {
                    val preset = settingsViewModel.uiState.value
                    navController.navigate(
                        Routes.pickLocation(
                            preset.locationLatitudeText.trim().toDoubleOrNull(),
                            preset.locationLongitudeText.trim().toDoubleOrNull(),
                            preset.locationRadiusText.trim().toDoubleOrNull()
                                ?: settings.defaultGeofenceRadiusMeters,
                        ),
                    )
                },
                onKeywordLanguageChanged = onLanguageChanged,
                onPermissionAction = onPermissionAction,
                onBack = { navController.popBackStack() },
                onExportBackup = {
                    backupViewModel.clearStatus()
                    launchDocumentPicker(exportLauncher, CalendarDocumentContract.createDocumentIntent())
                },
                onImportBackup = {
                    backupViewModel.clearStatus()
                    launchDocumentPicker(importLauncher, CalendarDocumentContract.openDocumentIntent())
                },
                backupStatus = backupState.status,
                backupInProgress = backupState.inProgress,
                onShowDebugFullScreenReminder = {
                    runCatching {
                        context.startActivity(AlertIntentFactory.debugFullScreenIntent(context))
                    }
                },
            )
        }
        composable(
            route = Routes.PICK_LOCATION,
            arguments = listOf(
                navArgument(Routes.ARG_LATITUDE) { type = NavType.StringType; nullable = true },
                navArgument(Routes.ARG_LONGITUDE) { type = NavType.StringType; nullable = true },
                navArgument(Routes.ARG_RADIUS) { type = NavType.StringType; nullable = true },
            ),
        ) { backStackEntry ->
            val arguments = backStackEntry.arguments
            val latitude = arguments?.getString(Routes.ARG_LATITUDE)?.toDoubleOrNull()
                ?.takeIf { it in -90.0..90.0 }
            val longitude = arguments?.getString(Routes.ARG_LONGITUDE)?.toDoubleOrNull()
                ?.takeIf { it in -180.0..180.0 }
            val radius = arguments?.getString(Routes.ARG_RADIUS)?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?: settings.defaultGeofenceRadiusMeters
            val pickerViewModel: LocationPickerViewModel = viewModel(
                factory = LocationPickerViewModelFactory(
                    initialLatitude = latitude?.takeIf { longitude != null },
                    initialLongitude = longitude?.takeIf { latitude != null },
                    initialRadiusMeters = radius,
                    placeSearch = container.geocoding,
                    labelResolver = container.geocoding,
                    locationProvider = container.currentLocationProvider,
                ),
            )
            LocationPickerRoute(
                viewModel = pickerViewModel,
                onBack = { navController.popBackStack() },
                onConfirm = { picked ->
                    navController.previousBackStackEntry?.savedStateHandle
                        ?.set(PickedLocation.RESULT_KEY, picked.toBundle())
                    navController.popBackStack()
                },
            )
        }
    }
    }
}

/** The new-reminder draft shared by the quick-add bar and the full editor. */
@Composable
private fun draftViewModel(
    session: ViewModelStoreOwner,
    container: AppContainer,
    settings: ReminderSettings,
    localeProvider: () -> Locale,
): AddReminderViewModel = viewModel(
    viewModelStoreOwner = session,
    key = "draft-${settings.editorKey}",
    factory = editorViewModelFactory(container, settings, localeProvider, editingReminderId = null),
)

@Composable
private fun editorViewModelFactory(
    container: AppContainer,
    settings: ReminderSettings,
    localeProvider: () -> Locale,
    editingReminderId: ReminderId?,
) = AddReminderViewModelFactory(
    repository = container.reminderRepository,
    parser = container.parserFactory.fromCompleteKeywordTable(
        keywordTimes = settings.keywordTimes,
        keywordLocations = settings.keywordLocations,
        language = settings.keywordLanguage,
    ),
    defaultGeoRadiusProvider = container.settingsRepository,
    locationProvider = container.currentLocationProvider,
    geoLabelResolver = container.geoLabelResolver,
    postSaveActions = container.schedulingCoordinator,
    defaultReminderTitle = stringResource(R.string.reminder),
    removeTimeExpressionsFromText = settings.removeTimeExpressionsFromText,
    localeProvider = localeProvider,
    editingReminderId = editingReminderId,
    mapLinkResolver = container.mapLinkResolver,
)

/**
 * Only the settings that change how text is parsed or saved. Unrelated changes such as theme or
 * sort order must not replace the editor's view model and lose a draft in progress.
 */
private val ReminderSettings.editorKey: Int
    get() = listOf(
        keywordTimes,
        keywordLocations,
        keywordLanguage,
        removeTimeExpressionsFromText,
    ).hashCode()

/** Full-page editor shared by "new reminder" and "edit reminder". */
@Composable
private fun ReminderEditorHost(
    viewModel: AddReminderViewModel,
    backStackEntry: NavBackStackEntry,
    onPickOnMap: (latitude: Double?, longitude: Double?, radiusMeters: Double?) -> Unit,
    title: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    autoFocusText: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConsumePickedLocation(backStackEntry) { picked ->
        viewModel.applyPickedLocation(picked.latitude, picked.longitude, picked.radiusMeters, picked.label)
    }
    // An edit can only be saved once the existing reminder has loaded.
    val canSave = !state.isSaving &&
        (viewModel.editingReminderId == null || state.editingReminderId != null)
    ReachableScaffold(
        title = title,
        compactTitleStartPadding = 56.dp,
        initiallyExpanded = false,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        actions = {
            IconButton(onClick = viewModel::save, enabled = canSave) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(12.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.save_changes),
                    )
                }
            }
        },
    ) { contentPadding ->
        AddReminderRoute(
            viewModel = viewModel,
            onReminderSaved = { onSaved() },
            modifier = Modifier.padding(contentPadding),
            autoFocusText = autoFocusText,
            onPickOnMap = {
                val current = viewModel.uiState.value
                onPickOnMap(
                    current.latitudeText.trim().toDoubleOrNull(),
                    current.longitudeText.trim().toDoubleOrNull(),
                    current.radiusText.trim().toDoubleOrNull(),
                )
            },
        )
    }
}

/** Delivers a place confirmed in the map picker to the screen that opened it, once. */
@Composable
private fun ConsumePickedLocation(
    backStackEntry: NavBackStackEntry,
    onPicked: (PickedLocation) -> Unit,
) {
    val handle = backStackEntry.savedStateHandle
    val bundle by handle.getStateFlow<Bundle?>(PickedLocation.RESULT_KEY, null)
        .collectAsStateWithLifecycle()
    val currentOnPicked by rememberUpdatedState(onPicked)
    LaunchedEffect(bundle) {
        val result = bundle ?: return@LaunchedEffect
        handle.remove<Bundle>(PickedLocation.RESULT_KEY)
        PickedLocation.fromBundle(result)?.let(currentOnPicked)
    }
}

/** Navigation must not run during composition; leave a malformed route from an effect. */
@Composable
private fun PopInvalidRoute(popBackStack: () -> Boolean) {
    LaunchedEffect(Unit) { popBackStack() }
}

private fun NavBackStackEntry.reminderIdArgument(): ReminderId? =
    arguments?.getString("reminderId")?.takeIf(String::isNotBlank)?.let(::ReminderId)

private fun launchDocumentPicker(
    launcher: ActivityResultLauncher<Intent>,
    intent: Intent,
) {
    // Some devices ship without a documents UI; failing to open it must not crash the app.
    runCatching { launcher.launch(intent) }
}

private object Routes {
    const val LIST = "reminders"
    const val ARG_FOCUS = "focus"
    const val NEW = "new?$ARG_FOCUS={$ARG_FOCUS}"
    const val EDIT = "edit/{reminderId}"
    const val SETTINGS = "settings"
    const val REMINDER_DETAIL = "reminder/{reminderId}"
    const val ARG_LATITUDE = "lat"
    const val ARG_LONGITUDE = "lon"
    const val ARG_RADIUS = "radius"
    const val PICK_LOCATION =
        "pick-location?$ARG_LATITUDE={$ARG_LATITUDE}&$ARG_LONGITUDE={$ARG_LONGITUDE}&$ARG_RADIUS={$ARG_RADIUS}"

    fun new(focusText: Boolean): String = "new?$ARG_FOCUS=$focusText"
    fun detail(reminderId: String): String = "reminder/$reminderId"
    fun edit(reminderId: String): String = "edit/$reminderId"
    fun pickLocation(latitude: Double?, longitude: Double?, radiusMeters: Double?): String =
        buildString {
            append("pick-location?")
            append(listOfNotNull(
                latitude?.let { "$ARG_LATITUDE=$it" },
                longitude?.let { "$ARG_LONGITUDE=$it" },
                radiusMeters?.let { "$ARG_RADIUS=$it" },
            ).joinToString("&"))
        }
}
