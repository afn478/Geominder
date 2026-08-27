package com.afn478.geominder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.afn478.geominder.alert.AlertIntentFactory
import com.afn478.geominder.backup.CalendarDocumentContract
import com.afn478.geominder.domain.model.PresetLocation
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.localization.UiText
import com.afn478.geominder.localization.resource
import com.afn478.geominder.settings.SettingsPermissionAction
import com.afn478.geominder.ui.add.AddReminderRoute
import com.afn478.geominder.ui.add.AddReminderViewModel
import com.afn478.geominder.ui.add.AddReminderViewModelFactory
import com.afn478.geominder.ui.appbar.ReachableScaffold
import com.afn478.geominder.ui.detail.ReminderDetailRoute
import com.afn478.geominder.ui.detail.ReminderDetailViewModel
import com.afn478.geominder.ui.detail.ReminderDetailViewModelFactory
import com.afn478.geominder.ui.list.ReminderListRoute
import com.afn478.geominder.ui.list.ReminderListViewModel
import com.afn478.geominder.ui.list.ReminderListViewModelFactory
import com.afn478.geominder.ui.settings.SettingsRoute
import com.afn478.geominder.ui.settings.SettingsViewModel
import com.afn478.geominder.ui.settings.SettingsViewModelFactory
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.time.LocalTime

@Composable
fun ReminderApp(
    container: AppContainer,
    onPermissionAction: (SettingsPermissionAction) -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    var backupStatus by remember { mutableStateOf<UiText?>(null) }
    var backupInProgress by remember { mutableStateOf(false) }
    var nextAddComposerSession by remember { mutableIntStateOf(0) }
    var addComposerSession by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.data?.let { uri ->
            scope.launch {
                backupInProgress = true
                backupStatus = runCatching { container.safReminderBackup.exportTo(uri) }
                    .fold(
                        onSuccess = { UiText.resource(R.string.backup_exported, it.exported) },
                        onFailure = { UiText.resource(R.string.backup_export_failed) },
                    )
                backupInProgress = false
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.data?.let { uri ->
            scope.launch {
                backupInProgress = true
                backupStatus = runCatching { container.safReminderBackup.importFrom(uri) }
                    .fold(
                        onSuccess = { import ->
                            if (import.succeeded) {
                                UiText.resource(
                                    R.string.backup_imported,
                                    import.inserted,
                                    import.updated,
                                    import.scheduled,
                                    import.schedulingFailed,
                                    import.skipped,
                                    import.issues.size,
                                )
                            } else {
                                import.fatalError?.let(UiText::Plain)
                                    ?: UiText.resource(R.string.backup_import_failed)
                            }
                        },
                        onFailure = { UiText.resource(R.string.backup_import_failed) },
                    )
                backupInProgress = false
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.LIST,
    ) {
        composable(Routes.LIST) {
            val listViewModel: ReminderListViewModel = viewModel(
                factory = ReminderListViewModelFactory(
                    repository = container.reminderRepository,
                    scheduleCommandHandler = container.schedulingCoordinator,
                    settingsRepository = container.settingsRepository,
                ),
            )
            ReminderListRoute(
                viewModel = listViewModel,
                onAddReminder = {
                    nextAddComposerSession += 1
                    addComposerSession = nextAddComposerSession
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
        composable(Routes.EDIT) { backStackEntry ->
            val reminderId = backStackEntry.arguments?.getString("reminderId")?.let(::ReminderId)
            if (reminderId == null) navController.popBackStack() else AddReminderHost(
                container = container,
                settingsSnapshotKey = settings.hashCode(),
                keywordTimes = settings.keywordTimes,
                keywordLocations = settings.keywordLocations,
                removeTimeExpressionsFromText = settings.removeTimeExpressionsFromText,
                editingReminderId = reminderId,
                onBack = navController::popBackStack,
                onSaved = {
                    navController.popBackStack(Routes.LIST, inclusive = false)
                },
            )
        }
        composable(Routes.REMINDER_DETAIL) { backStackEntry ->
            val reminderId = backStackEntry.arguments?.getString("reminderId")
                ?.let(::ReminderId)
            if (reminderId == null) {
                navController.popBackStack()
            } else {
                val detailViewModel: ReminderDetailViewModel = viewModel(
                    factory = ReminderDetailViewModelFactory(
                        repository = container.reminderRepository,
                        reminderId = reminderId,
                    ),
                )
                ReminderDetailRoute(
                    viewModel = detailViewModel,
                    onBack = navController::popBackStack,
                )
            }
        }
        composable(Routes.SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(
                    repository = container.settingsRepository,
                    permissionStatusProvider = container.permissionStatusProvider,
                    locationProvider = container.currentLocationProvider,
                ),
            )
            SettingsRoute(
                viewModel = settingsViewModel,
                onPermissionAction = onPermissionAction,
                onBack = navController::popBackStack,
                onExportBackup = {
                    backupStatus = null
                    exportLauncher.launch(CalendarDocumentContract.createDocumentIntent())
                },
                onImportBackup = {
                    backupStatus = null
                    importLauncher.launch(CalendarDocumentContract.openDocumentIntent())
                },
                backupStatus = backupStatus,
                backupInProgress = backupInProgress,
                onShowDebugFullScreenReminder = {
                    context.startActivity(AlertIntentFactory.debugFullScreenIntent(context))
                },
            )
        }
    }

    addComposerSession?.let { session ->
        AddReminderDialog(
            container = container,
            settingsSnapshotKey = settings.hashCode(),
            keywordTimes = settings.keywordTimes,
            keywordLocations = settings.keywordLocations,
            removeTimeExpressionsFromText = settings.removeTimeExpressionsFromText,
            session = session,
            onDismiss = { addComposerSession = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReminderDialog(
    container: AppContainer,
    settingsSnapshotKey: Int,
    keywordTimes: Map<String, LocalTime>,
    keywordLocations: Map<String, PresetLocation>,
    removeTimeExpressionsFromText: Boolean,
    session: Int,
    onDismiss: () -> Unit,
) {
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    val dismissComposerDescription = stringResource(R.string.dismiss_reminder_composer)
    val density = LocalDensity.current
    val rootView = LocalView.current
    val safeTop = with(density) {
        val insetTypes = WindowInsetsCompat.Type.statusBars() or
            WindowInsetsCompat.Type.displayCutout()
        val appliedInsetPixels = ViewCompat.getRootWindowInsets(rootView)
            ?.getInsets(insetTypes)
            ?.top
            ?: WindowInsets.safeDrawing.getTop(this)
        val statusBarResource = rootView.resources.getIdentifier(
            "status_bar_height",
            "dimen",
            "android",
        )
        val statusBarPixels = if (statusBarResource != 0) {
            rootView.resources.getDimensionPixelSize(statusBarResource)
        } else {
            0
        }
        val safeTopPixels = maxOf(appliedInsetPixels, statusBarPixels)
        safeTopPixels.toDp()
    }
    val addViewModel: AddReminderViewModel = viewModel(
        key = "add-composer-$settingsSnapshotKey-$session",
        factory = AddReminderViewModelFactory(
            repository = container.reminderRepository,
            parser = container.parserFactory.fromCompleteKeywordTable(
                keywordTimes = keywordTimes,
                keywordLocations = keywordLocations,
            ),
            defaultGeoRadiusProvider = container.settingsRepository,
            locationProvider = container.currentLocationProvider,
            geoLabelResolver = container.geoLabelResolver,
            postSaveActions = container.schedulingCoordinator,
            defaultReminderTitle = stringResource(R.string.reminder),
            removeTimeExpressionsFromText = removeTimeExpressionsFromText,
        ),
    )
    val state by addViewModel.uiState.collectAsStateWithLifecycle()
    val requestDismiss = {
        if (state.sourceText.isNotBlank()) showDiscardConfirmation = true else onDismiss()
    }
    val currentRequestDismiss = rememberUpdatedState(requestDismiss)
    val sheetScope = rememberCoroutineScope()
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = false,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)
    val collapsedSheetExtraHeight = if (WindowInsets.ime.getBottom(density) == 0) {
        16.dp
    } else {
        0.dp
    }
    LaunchedEffect(sheetState) {
        snapshotFlow {
            sheetState.hasPartiallyExpandedState &&
                sheetState.currentValue != SheetValue.Hidden
        }.first { it }
        snapshotFlow { sheetState.currentValue }
            .distinctUntilChanged()
            .collect { settledValue ->
                if (settledValue == SheetValue.Hidden) {
                    addViewModel.onDetailsExpandedChange(false)
                    currentRequestDismiss.value()
                    sheetState.partialExpand()
                } else {
                    addViewModel.onDetailsExpandedChange(settledValue == SheetValue.Expanded)
                }
        }
    }
    val expandSheet = {
        if (
            sheetState.currentValue != SheetValue.Expanded &&
            sheetState.targetValue != SheetValue.Expanded
        ) {
            sheetScope.launch { sheetState.expand() }
        }
    }
    Dialog(
        onDismissRequest = requestDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val maximumSheetHeight = (maxHeight - safeTop - 12.dp).coerceAtLeast(0.dp)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(-1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = requestDismiss,
                    )
                    .semantics {
                        contentDescription = dismissComposerDescription
                    },
            )

            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetContent = {
                    Column {
                        // Keep the visual handle without Material 3's tooltip wrapper.
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            BottomSheetDefaults.DragHandle()
                        }
                        AddReminderRoute(
                            viewModel = addViewModel,
                            onReminderSaved = { onDismiss() },
                            modifier = Modifier.heightIn(max = maximumSheetHeight),
                            autoFocusSource = true,
                            bottomSheetMode = true,
                            sheetContentScrollEnabled =
                                sheetState.currentValue == SheetValue.Expanded &&
                                    sheetState.targetValue == SheetValue.Expanded,
                            onExpandBottomSheet = expandSheet,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = safeTop + 12.dp)
                    .imePadding(),
                sheetPeekHeight = 192.dp + collapsedSheetExtraHeight,
                sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                sheetDragHandle = null,
                sheetSwipeEnabled = true,
                containerColor = Color.Transparent,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (showDiscardConfirmation) {
            AlertDialog(
                onDismissRequest = { showDiscardConfirmation = false },
                title = { Text(stringResource(R.string.discard_reminder_title)) },
                text = { Text(stringResource(R.string.discard_reminder_message)) },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.discard)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirmation = false }) {
                        Text(stringResource(R.string.keep))
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReminderHost(
    container: AppContainer,
    settingsSnapshotKey: Int,
    keywordTimes: Map<String, LocalTime>,
    keywordLocations: Map<String, PresetLocation>,
    removeTimeExpressionsFromText: Boolean,
    editingReminderId: ReminderId? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val addViewModel: AddReminderViewModel = viewModel(
        key = "add-$settingsSnapshotKey",
        factory = AddReminderViewModelFactory(
            repository = container.reminderRepository,
            parser = container.parserFactory.fromCompleteKeywordTable(
                keywordTimes = keywordTimes,
                keywordLocations = keywordLocations,
            ),
            defaultGeoRadiusProvider = container.settingsRepository,
            locationProvider = container.currentLocationProvider,
            geoLabelResolver = container.geoLabelResolver,
            postSaveActions = container.schedulingCoordinator,
            defaultReminderTitle = stringResource(R.string.reminder),
            removeTimeExpressionsFromText = removeTimeExpressionsFromText,
            editingReminderId = editingReminderId,
        ),
    )
    val state by addViewModel.uiState.collectAsStateWithLifecycle()
    val screenTitle = stringResource(
        if (editingReminderId == null) R.string.add_reminder else R.string.edit_reminder,
    )
    val backDescription = stringResource(R.string.back)
    val saveChangesDescription = stringResource(R.string.save_changes)
    ReachableScaffold(
        title = screenTitle,
        compactTitleStartPadding = 56.dp,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backDescription,
                )
            }
        },
        actions = {
            if (editingReminderId != null) {
                IconButton(
                    onClick = addViewModel::save,
                    enabled = state.editingReminderId != null && !state.isSaving,
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(12.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = saveChangesDescription,
                        )
                    }
                }
            }
        },
    ) { contentPadding ->
        AddReminderRoute(
            viewModel = addViewModel,
            onReminderSaved = { onSaved() },
            modifier = Modifier.padding(contentPadding),
            fullPageMode = editingReminderId != null,
        )
    }
}

private object Routes {
    const val LIST = "reminders"
    const val EDIT = "edit/{reminderId}"
    const val SETTINGS = "settings"
    const val REMINDER_DETAIL = "reminder/{reminderId}"

    fun detail(reminderId: String): String = "reminder/$reminderId"
    fun edit(reminderId: String): String = "edit/$reminderId"
}
