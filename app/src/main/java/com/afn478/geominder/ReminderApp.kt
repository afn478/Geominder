package com.afn478.geominder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import com.afn478.geominder.backup.CalendarDocumentContract
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.parser.ReminderTextParser
import com.afn478.geominder.settings.SettingsPermissionAction
import com.afn478.geominder.ui.add.AddReminderRoute
import com.afn478.geominder.ui.add.AddReminderViewModel
import com.afn478.geominder.ui.add.AddReminderViewModelFactory
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
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    var backupStatus by remember { mutableStateOf<String?>(null) }
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
                        onSuccess = { "Exported ${it.exported} reminder(s)." },
                        onFailure = { it.message ?: "The backup could not be exported." },
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
                                "Imported ${import.inserted} new and ${import.updated} updated reminder(s); " +
                                    "${import.scheduled} scheduled, ${import.schedulingFailed} scheduling " +
                                    "failure(s), ${import.skipped} skipped, ${import.issues.size} issue(s)."
                            } else {
                                import.fatalError ?: "The backup could not be imported."
                            }
                        },
                        onFailure = { it.message ?: "The backup could not be imported." },
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
            )
        }
    }

    addComposerSession?.let { session ->
        AddReminderDialog(
            container = container,
            settingsSnapshotKey = settings.hashCode(),
            keywordTimes = settings.keywordTimes,
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
    session: Int,
    onDismiss: () -> Unit,
) {
    var showDiscardConfirmation by remember { mutableStateOf(false) }
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
            parser = ReminderTextParser.fromCompleteKeywordTable(keywordTimes),
            defaultGeoRadiusProvider = container.settingsRepository,
            locationProvider = container.currentLocationProvider,
            geoLabelResolver = container.geoLabelResolver,
            postSaveActions = container.schedulingCoordinator,
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
                        contentDescription = "Dismiss reminder composer"
                    },
            )

            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetContent = {
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
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = safeTop + 12.dp)
                    .imePadding(),
                sheetPeekHeight = 192.dp + collapsedSheetExtraHeight,
                sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                sheetDragHandle = { BottomSheetDefaults.DragHandle() },
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
                title = { Text("Discard reminder?") },
                text = { Text("Your reminder text will not be saved.") },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("Discard") }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirmation = false }) {
                        Text("Keep")
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
    editingReminderId: ReminderId? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val addViewModel: AddReminderViewModel = viewModel(
        key = "add-$settingsSnapshotKey",
        factory = AddReminderViewModelFactory(
            repository = container.reminderRepository,
            parser = ReminderTextParser.fromCompleteKeywordTable(keywordTimes),
            defaultGeoRadiusProvider = container.settingsRepository,
            locationProvider = container.currentLocationProvider,
            geoLabelResolver = container.geoLabelResolver,
            postSaveActions = container.schedulingCoordinator,
            editingReminderId = editingReminderId,
        ),
    )
    val state by addViewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (editingReminderId == null) "Add reminder" else "Edit reminder")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
                                    contentDescription = "Save changes",
                                )
                            }
                        }
                    }
                },
            )
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
