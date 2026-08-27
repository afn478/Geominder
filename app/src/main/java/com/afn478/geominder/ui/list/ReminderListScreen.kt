package com.afn478.geominder.ui.list

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afn478.geominder.R
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.ReminderTag
import com.afn478.geominder.localization.UiText
import com.afn478.geominder.localization.resource
import com.afn478.geominder.settings.ReminderSortDirection
import com.afn478.geominder.settings.ReminderSortField
import com.afn478.geominder.settings.ReminderSortOrder
import com.afn478.geominder.ui.appbar.ReachableScaffold
import com.afn478.geominder.ui.tag.ReminderTagChips
import com.afn478.geominder.ui.tag.ReminderTrashChip
import com.afn478.geominder.ui.tag.color
import com.afn478.geominder.ui.text.resolve
import kotlinx.coroutines.delay

@Composable
fun ReminderListRoute(
    viewModel: ReminderListViewModel,
    onAddReminder: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReminder: (ReminderId) -> Unit,
    onEditReminder: (ReminderId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ReminderListScreen(
        state = state,
        onAddReminder = {
            viewModel.exitSelectionMode()
            onAddReminder()
        },
        onOpenSettings = onOpenSettings,
        onSortOrderChange = viewModel::setSortOrder,
        onTagFilterClick = viewModel::toggleTagFilter,
        onOpenReminder = onOpenReminder,
        onEditReminder = onEditReminder,
        onSetCompleted = viewModel::setCompleted,
        onToggleTrash = viewModel::toggleTrash,
        onDeleteReminder = viewModel::deleteReminder,
        onRestoreReminder = viewModel::restoreReminder,
        onUndoDelete = viewModel::undoDelete,
        onUndoDeleteNoticeConsumed = viewModel::consumeUndoDeleteNotice,
        onDismissMessage = viewModel::clearMessage,
        onStartSelection = viewModel::startSelection,
        onToggleSelection = viewModel::toggleSelection,
        onSelectAllReminders = viewModel::selectAllReminders,
        onInvertSelection = viewModel::invertSelection,
        onDeleteSelectedReminders = viewModel::deleteSelectedReminders,
        onRestoreSelectedReminders = viewModel::restoreSelectedReminders,
        onExitSelectionMode = viewModel::exitSelectionMode,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReminderListScreen(
    state: ReminderListUiState,
    onAddReminder: () -> Unit,
    onOpenSettings: () -> Unit,
    onSortOrderChange: (ReminderSortOrder) -> Unit,
    onTagFilterClick: (ReminderTag) -> Unit,
    onOpenReminder: (ReminderId) -> Unit,
    onEditReminder: (ReminderId) -> Unit,
    onSetCompleted: (ReminderId, Boolean) -> Unit,
    onToggleTrash: () -> Unit,
    onDeleteReminder: (ReminderId) -> Unit,
    onRestoreReminder: (ReminderId) -> Unit,
    onUndoDelete: (ReminderId) -> Unit,
    onUndoDeleteNoticeConsumed: (ReminderId) -> Unit,
    onDismissMessage: () -> Unit,
    onStartSelection: (ReminderId) -> Unit = {},
    onToggleSelection: (ReminderId) -> Unit = {},
    onSelectAllReminders: () -> Unit = {},
    onInvertSelection: () -> Unit = {},
    onDeleteSelectedReminders: () -> Unit = {},
    onRestoreSelectedReminders: () -> Unit = {},
    onExitSelectionMode: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val dismissLabel = stringResource(R.string.dismiss)
    val deletedMessage = stringResource(R.string.reminder_deleted)
    val undoLabel = stringResource(R.string.undo)
    val exitSelectionLabel = stringResource(R.string.exit_selection_mode)
    val messageText = state.message?.resolve()
    LaunchedEffect(state.message) {
        messageText?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = dismissLabel,
                duration = SnackbarDuration.Short,
            )
            onDismissMessage()
        }
    }
    LaunchedEffect(state.undoDeleteReminderId) {
        val reminderId = state.undoDeleteReminderId ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onUndoDelete(reminderId)
        }
        onUndoDeleteNoticeConsumed(reminderId)
    }

    BackHandler(
        enabled = state.isSelectionMode,
        onBack = onExitSelectionMode,
    )

    ReachableScaffold(
        title = stringResource(R.string.reminders),
        modifier = modifier,
        navigationIcon = if (state.isSelectionMode) {
            {
                IconButton(
                    onClick = onExitSelectionMode,
                    modifier = Modifier.semantics {
                        contentDescription = exitSelectionLabel
                    },
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
        } else {
            null
        },
        compactTitleStartPadding = if (state.isSelectionMode) 72.dp else 24.dp,
        actions = {
            if (state.isSelectionMode) {
                ReminderSelectionActions(
                    inTrash = state.showTrash,
                    hasSelection = state.selectedReminderIds.isNotEmpty(),
                    onDeleteSelected = onDeleteSelectedReminders,
                    onRestoreSelected = onRestoreSelectedReminders,
                    onSelectAll = onSelectAllReminders,
                    onInvertSelection = onInvertSelection,
                )
            } else {
                ReminderOverflowMenu(
                    onOpenSettings = onOpenSettings,
                    sortOrder = state.sortOrder,
                    onSortOrderChange = onSortOrderChange,
                )
            }
        },
        bottomBar = {
            Column {
                ReminderTagChips(
                    selectedTag = state.selectedTag,
                    onTagClick = onTagFilterClick,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    compact = true,
                    trailingContent = {
                        ReminderTrashChip(
                            selected = state.showTrash,
                            onClick = onToggleTrash,
                        )
                    },
                )
                NewReminderLauncher(onClick = onAddReminder)
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        },
    ) { contentPadding ->
        when {
            state.isLoading -> LoadingContent(
                modifier = Modifier.padding(contentPadding),
            )

            state.isEmpty -> EmptyReminderContent(
                title = when {
                    state.showTrash && state.selectedTag == null ->
                        UiText.resource(R.string.empty_recycling_bin)
                    state.selectedTag != null ->
                        UiText.resource(R.string.no_reminders_found)
                    else -> UiText.resource(R.string.nothing_to_remember)
                },
                description = when {
                    state.showTrash && state.selectedTag == null ->
                        UiText.resource(R.string.deleted_reminders_will_appear)
                    state.selectedTag != null ->
                        UiText.resource(R.string.no_tagged_reminders)
                    else -> UiText.resource(R.string.empty_reminders_description)
                },
                modifier = Modifier.padding(contentPadding),
            )

            else -> ReminderItems(
                items = state.items,
                busyReminderIds = state.busyReminderIds,
                onOpenReminder = onOpenReminder,
                onEditReminder = onEditReminder,
                onSetCompleted = onSetCompleted,
                onDeleteReminder = onDeleteReminder,
                onRestoreReminder = onRestoreReminder,
                selectionMode = state.isSelectionMode,
                selectedReminderIds = state.selectedReminderIds,
                onStartSelection = onStartSelection,
                onToggleSelection = onToggleSelection,
                inTrash = state.showTrash,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }
}

@Composable
private fun ReminderSelectionActions(
    inTrash: Boolean,
    hasSelection: Boolean,
    onDeleteSelected: () -> Unit,
    onRestoreSelected: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
) {
    val deleteLabel = stringResource(
        if (inTrash) {
            R.string.delete_selected_reminders_permanently
        } else {
            R.string.delete_selected_reminders
        },
    )
    if (inTrash) {
        IconButtonWithDescription(
            description = stringResource(R.string.restore_selected_reminders),
            onClick = onRestoreSelected,
            enabled = hasSelection,
        ) {
            Icon(Icons.Default.RestoreFromTrash, contentDescription = null)
        }
    }
    IconButtonWithDescription(
        description = deleteLabel,
        onClick = onDeleteSelected,
        enabled = hasSelection,
    ) {
        Icon(Icons.Default.Delete, contentDescription = null)
    }
    IconButtonWithDescription(
        description = stringResource(R.string.select_all_reminders),
        onClick = onSelectAll,
        enabled = true,
    ) {
        Icon(Icons.Outlined.SelectAll, contentDescription = null)
    }
    IconButtonWithDescription(
        description = stringResource(R.string.invert_reminder_selection),
        onClick = onInvertSelection,
        enabled = true,
    ) {
        Icon(Icons.Outlined.FlipToBack, contentDescription = null)
    }
}

@Composable
private fun ReminderOverflowMenu(
    onOpenSettings: () -> Unit,
    sortOrder: ReminderSortOrder,
    onSortOrderChange: (ReminderSortOrder) -> Unit,
) {
    var overflowExpanded by rememberSaveable { mutableStateOf(false) }
    var sortExpanded by rememberSaveable { mutableStateOf(false) }
    val moreOptionsLabel = stringResource(R.string.more_options)

    Box {
        IconButton(
            onClick = {
                overflowExpanded = !overflowExpanded
                sortExpanded = false
            },
            modifier = Modifier.semantics {
                contentDescription = moreOptionsLabel
            },
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = null)
        }

        DropdownMenu(
            expanded = overflowExpanded,
            onDismissRequest = { overflowExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings)) },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = {
                    overflowExpanded = false
                    onOpenSettings()
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_by)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                trailingIcon = {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = stringResource(R.string.open_sort_options),
                    )
                },
                onClick = {
                    overflowExpanded = false
                    sortExpanded = true
                },
            )
        }

        DropdownMenu(
            expanded = sortExpanded,
            onDismissRequest = { sortExpanded = false },
        ) {
            Text(
                text = stringResource(R.string.sort_menu_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            SORT_MENU_OPTIONS.forEach { option ->
                val isSelected = option.sortOrder == sortOrder
                val label = stringResource(option.labelRes)
                val accessibilityLabel = if (isSelected) {
                    stringResource(R.string.sort_option_selected, label)
                } else {
                    label
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    modifier = Modifier.semantics {
                        contentDescription = accessibilityLabel
                    },
                    onClick = {
                        sortExpanded = false
                        onSortOrderChange(option.sortOrder)
                    },
                )
            }
        }
    }
}

private data class SortMenuOption(
    val sortOrder: ReminderSortOrder,
    @field:StringRes val labelRes: Int,
)

private val SORT_MENU_OPTIONS = listOf(
    SortMenuOption(
        sortOrder = ReminderSortOrder(
            field = ReminderSortField.TITLE,
            direction = ReminderSortDirection.ASCENDING,
        ),
        labelRes = R.string.sort_title_ascending,
    ),
    SortMenuOption(
        sortOrder = ReminderSortOrder(
            field = ReminderSortField.TITLE,
            direction = ReminderSortDirection.DESCENDING,
        ),
        labelRes = R.string.sort_title_descending,
    ),
    SortMenuOption(
        sortOrder = ReminderSortOrder(
            field = ReminderSortField.CREATION_DATE,
            direction = ReminderSortDirection.ASCENDING,
        ),
        labelRes = R.string.sort_creation_date_ascending,
    ),
    SortMenuOption(
        sortOrder = ReminderSortOrder(
            field = ReminderSortField.CREATION_DATE,
            direction = ReminderSortDirection.DESCENDING,
        ),
        labelRes = R.string.sort_creation_date_descending,
    ),
    SortMenuOption(
        sortOrder = ReminderSortOrder(
            field = ReminderSortField.MODIFICATION_DATE,
            direction = ReminderSortDirection.ASCENDING,
        ),
        labelRes = R.string.sort_modification_date_ascending,
    ),
    SortMenuOption(
        sortOrder = ReminderSortOrder(
            field = ReminderSortField.MODIFICATION_DATE,
            direction = ReminderSortDirection.DESCENDING,
        ),
        labelRes = R.string.sort_modification_date_descending,
    ),
    SortMenuOption(
        sortOrder = ReminderSortOrder(
            field = ReminderSortField.DUE_DATE,
            direction = ReminderSortDirection.ASCENDING,
        ),
        labelRes = R.string.sort_due_date_ascending,
    ),
    SortMenuOption(
        sortOrder = ReminderSortOrder(
            field = ReminderSortField.DUE_DATE,
            direction = ReminderSortDirection.DESCENDING,
        ),
        labelRes = R.string.sort_due_date_descending,
    ),
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NewReminderLauncher(
    onClick: () -> Unit,
) {
    var exampleIndex by remember { mutableIntStateOf(0) }
    val exampleAlpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(EXAMPLE_ROTATION_MILLIS)
            exampleAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = EXAMPLE_FADE_MILLIS),
            )
            exampleIndex = (exampleIndex + 1) % REMINDER_EXAMPLES.size
            exampleAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = EXAMPLE_FADE_MILLIS),
            )
        }
    }
    val example = stringResource(REMINDER_EXAMPLES[exampleIndex])
    val exampleDescription = stringResource(R.string.new_reminder, example)
    val createReminderLabel = stringResource(R.string.create_new_reminder)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = exampleDescription
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = createReminderLabel,
                onClick = onClick,
            ),
        shape = SearchBarDefaults.inputFieldShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = SearchBarDefaults.TonalElevation,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(
                text = example,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = exampleAlpha.value },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val EXAMPLE_ROTATION_MILLIS = 6_000L
private const val EXAMPLE_FADE_MILLIS = 180

private val REMINDER_EXAMPLES = listOf(
    R.string.example_call_mum,
    R.string.example_pick_up_groceries,
    R.string.example_water_plants,
    R.string.example_take_umbrella,
)

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    val loadingDescription = stringResource(R.string.loading_reminders)
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics {
                contentDescription = loadingDescription
            },
        )
    }
}

@Composable
private fun EmptyReminderContent(
    title: UiText,
    description: UiText,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title.resolve(),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = description.resolve(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReminderItems(
    items: List<ReminderListItem>,
    busyReminderIds: Set<ReminderId>,
    onOpenReminder: (ReminderId) -> Unit,
    onEditReminder: (ReminderId) -> Unit,
    onSetCompleted: (ReminderId, Boolean) -> Unit,
    onDeleteReminder: (ReminderId) -> Unit,
    onRestoreReminder: (ReminderId) -> Unit,
    selectionMode: Boolean,
    selectedReminderIds: Set<ReminderId>,
    onStartSelection: (ReminderId) -> Unit,
    onToggleSelection: (ReminderId) -> Unit,
    inTrash: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 112.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = items,
            key = { item -> item.id.value },
        ) { item ->
            ReminderCard(
                item = item,
                isBusy = item.id in busyReminderIds,
                onOpen = { onOpenReminder(item.id) },
                onEdit = { onEditReminder(item.id) },
                onSetCompleted = { completed -> onSetCompleted(item.id, completed) },
                onDelete = { onDeleteReminder(item.id) },
                onRestore = { onRestoreReminder(item.id) },
                selectionMode = selectionMode,
                isSelected = item.id in selectedReminderIds,
                onStartSelection = { onStartSelection(item.id) },
                onToggleSelection = { onToggleSelection(item.id) },
                inTrash = inTrash,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReminderCard(
    item: ReminderListItem,
    isBusy: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onSetCompleted: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    selectionMode: Boolean,
    isSelected: Boolean,
    onStartSelection: () -> Unit,
    onToggleSelection: () -> Unit,
    inTrash: Boolean,
) {
    val motionSpec: FiniteAnimationSpec<IntSize> = MaterialTheme.motionScheme.defaultSpatialSpec()
    val cardShape = MaterialTheme.shapes.extraLarge
    val primaryTextColor = if (item.isCompleted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val supportingTextColor = if (item.isCompleted) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
    val openLabel = stringResource(R.string.open_reminder, item.title)
    val completionLabel = stringResource(
        if (item.isCompleted) R.string.mark_reminder_not_done else R.string.mark_reminder_done,
        item.primaryText,
    )
    val editLabel = stringResource(R.string.edit_reminder_description, item.primaryText)
    val restoreLabel = stringResource(R.string.restore_reminder_description, item.primaryText)
    val deleteLabel = stringResource(
        if (inTrash) R.string.delete_reminder_permanently else R.string.delete_reminder,
        item.primaryText,
    )
    val selectionLabel = stringResource(
        if (isSelected) R.string.deselect_reminder else R.string.select_reminder,
        item.primaryText,
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = motionSpec)
            .clip(cardShape)
            .drawWithContent {
                drawContent()
                item.tag?.let { tag ->
                    drawRect(
                        color = tag.color,
                        size = Size(width = 4.dp.toPx(), height = size.height),
                    )
                }
            }
            .combinedClickable(
                enabled = !isBusy,
                onClickLabel = if (selectionMode) selectionLabel else openLabel,
                onLongClickLabel = selectionLabel,
                onLongClick = {
                    if (selectionMode) onToggleSelection() else onStartSelection()
                },
                onClick = {
                    if (selectionMode) onToggleSelection() else onOpen()
                },
            ),
        shape = cardShape,
        colors = if (isSelected) {
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        } else {
            CardDefaults.elevatedCardColors()
        },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = if (isBusy) null else { _ -> onToggleSelection() },
                        modifier = Modifier.semantics {
                            contentDescription = selectionLabel
                        },
                    )
                } else {
                    Checkbox(
                        checked = item.isCompleted,
                        onCheckedChange = if (isBusy || inTrash) {
                            null
                        } else {
                            { checked -> onSetCompleted(checked) }
                        },
                        modifier = Modifier.semantics {
                            contentDescription = completionLabel
                        },
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.primaryText,
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryTextColor,
                        textDecoration = textDecoration,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!selectionMode) {
                    if (!inTrash) {
                        IconButtonWithDescription(
                            description = editLabel,
                            onClick = onEdit,
                            enabled = !isBusy,
                        ) { Icon(Icons.Default.Edit, contentDescription = null) }
                    } else {
                        IconButtonWithDescription(
                            description = restoreLabel,
                            onClick = onRestore,
                            enabled = !isBusy,
                        ) {
                            Icon(Icons.Default.RestoreFromTrash, contentDescription = null)
                        }
                    }
                    IconButtonWithDescription(
                        description = deleteLabel,
                        onClick = onDelete,
                        enabled = !isBusy,
                    ) { Icon(Icons.Default.Delete, contentDescription = null) }
                }
            }

            if (item.timeText != null || item.locationText != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.timeText?.let { time ->
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = stringResource(R.string.scheduled_time),
                        )
                        Text(time, color = supportingTextColor, textDecoration = textDecoration)
                    }
                    if (item.timeText != null && item.locationText != null) {
                        Text("·", color = supportingTextColor)
                    }
                    item.locationText?.let { location ->
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = stringResource(R.string.location),
                        )
                        Text(
                            text = location,
                            color = supportingTextColor,
                            textDecoration = textDecoration,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconButtonWithDescription(
    description: String,
    onClick: () -> Unit,
    enabled: Boolean,
    icon: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description },
        content = icon,
    )
}
