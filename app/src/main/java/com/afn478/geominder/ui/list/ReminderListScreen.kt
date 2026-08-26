package com.afn478.geominder.ui.list

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afn478.geominder.R
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.ReminderTag
import com.afn478.geominder.settings.ReminderSortDirection
import com.afn478.geominder.settings.ReminderSortField
import com.afn478.geominder.settings.ReminderSortOrder
import com.afn478.geominder.ui.tag.ReminderTagChips
import com.afn478.geominder.ui.tag.ReminderTrashChip
import com.afn478.geominder.ui.tag.color
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
        onAddReminder = onAddReminder,
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
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val windowHeight = maxHeight
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(state.message) {
            state.message?.let { message ->
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Short,
                )
                onDismissMessage()
            }
        }
        LaunchedEffect(state.undoDeleteReminderId) {
            val reminderId = state.undoDeleteReminderId ?: return@LaunchedEffect
            val result = snackbarHostState.showSnackbar(
                message = "Reminder deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                onUndoDelete(reminderId)
            }
            onUndoDeleteNoticeConsumed(reminderId)
        }
        val useReachableAppBar = shouldUseReachableAppBar(
            windowHeight = windowHeight,
            windowWidth = maxWidth,
        )
        val appBarState = rememberTopAppBarState()
        val scrollBehavior = if (useReachableAppBar) {
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBarState)
        } else {
            null
        }
        val scaffoldModifier = Modifier
            .fillMaxSize()
            .then(
                scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) }
                    ?: Modifier,
            )

        Scaffold(
            modifier = scaffoldModifier,
            topBar = {
                if (scrollBehavior == null) {
                    ReminderCompactTopAppBar(
                        onOpenSettings = onOpenSettings,
                        sortOrder = state.sortOrder,
                        onSortOrderChange = onSortOrderChange,
                    )
                } else {
                    ReminderReachableTopAppBar(
                        onOpenSettings = onOpenSettings,
                        sortOrder = state.sortOrder,
                        onSortOrderChange = onSortOrderChange,
                        expandedHeight = reachableAppBarExpandedHeight(windowHeight),
                        appBarState = appBarState,
                        scrollBehavior = scrollBehavior,
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
                        state.showTrash && state.selectedTag == null -> "Recycling bin is empty"
                        state.selectedTag != null -> "No reminders found"
                        else -> "Nothing to remember yet"
                    },
                    description = when {
                        state.showTrash && state.selectedTag == null ->
                            "Deleted reminders will appear here."
                        state.selectedTag != null -> "No reminders use this color tag yet."
                        else -> "Create a reminder for a time, a place, or both. " +
                            "It will appear here so you can open, edit, or mark it done."
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
                    inTrash = state.showTrash,
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderCompactTopAppBar(
    onOpenSettings: () -> Unit,
    sortOrder: ReminderSortOrder,
    onSortOrderChange: (ReminderSortOrder) -> Unit,
) {
    TopAppBar(
        title = { Text("Reminders") },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
        actions = {
            ReminderOverflowMenu(
                onOpenSettings = onOpenSettings,
                sortOrder = sortOrder,
                onSortOrderChange = onSortOrderChange,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderReachableTopAppBar(
    onOpenSettings: () -> Unit,
    sortOrder: ReminderSortOrder,
    onSortOrderChange: (ReminderSortOrder) -> Unit,
    expandedHeight: Dp,
    appBarState: TopAppBarState,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    Box {
        LargeTopAppBar(
            title = { Spacer(Modifier) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background,
            ),
            actions = {
                ReminderOverflowMenu(
                    onOpenSettings = onOpenSettings,
                    sortOrder = sortOrder,
                    onSortOrderChange = onSortOrderChange,
                )
            },
            expandedHeight = expandedHeight,
            scrollBehavior = scrollBehavior,
        )
        MorphingTopAppBarTitle(
            collapsedFraction = appBarState.collapsedFraction,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun MorphingTopAppBarTitle(
    collapsedFraction: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        var titleWidthPx by remember { mutableIntStateOf(0) }
        val compactTitleStartPx = with(density) { COMPACT_TITLE_START_PADDING.toPx() }
        val expandedTitleOffsetYPx = with(density) {
            EXPANDED_TITLE_VERTICAL_OFFSET.toPx()
        }
        val compactTitleOffsetYPx = TopAppBarDefaults.windowInsets.getTop(density) / 2f
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val expansionFraction = (1f - collapsedFraction).coerceIn(0f, 1f)
        val expandedTitleStartPx = if (titleWidthPx > 0) {
            (containerWidthPx - titleWidthPx) / 2f
        } else {
            compactTitleStartPx
        }
        val titleTranslationX = compactTitleStartPx +
            (expandedTitleStartPx - compactTitleStartPx) * expansionFraction
        val titleTranslationY = compactTitleOffsetYPx +
            (expandedTitleOffsetYPx - compactTitleOffsetYPx) * expansionFraction
        val titleScale = 1f +
            (EXPANDED_TITLE_SCALE - 1f) * expansionFraction

        Text(
            text = "Reminders",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .onSizeChanged { titleWidthPx = it.width }
                .graphicsLayer {
                    translationX = titleTranslationX
                    translationY = titleTranslationY
                    scaleX = titleScale
                    scaleY = titleScale
                    transformOrigin = TransformOrigin.Center
                },
        )
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

internal fun shouldUseReachableAppBar(
    windowHeight: Dp,
    windowWidth: Dp,
): Boolean =
    windowHeight >= REACHABLE_APP_BAR_MIN_WINDOW_HEIGHT && windowHeight > windowWidth

@OptIn(ExperimentalMaterial3Api::class)
internal fun reachableAppBarExpandedHeight(
    windowHeight: Dp,
): Dp =
    (windowHeight * REACHABLE_APP_BAR_HEIGHT_FRACTION).coerceIn(
        minimumValue = TopAppBarDefaults.LargeAppBarExpandedHeight,
        maximumValue = REACHABLE_APP_BAR_MAX_EXPANDED_HEIGHT,
    )

private const val REACHABLE_APP_BAR_HEIGHT_FRACTION = 0.40f
private const val EXPANDED_TITLE_SCALE = 1.55f
private val COMPACT_TITLE_START_PADDING = 24.dp
private val EXPANDED_TITLE_VERTICAL_OFFSET = 20.dp
private val REACHABLE_APP_BAR_MIN_WINDOW_HEIGHT = 580.dp
private val REACHABLE_APP_BAR_MAX_EXPANDED_HEIGHT = 360.dp

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
    val example = REMINDER_EXAMPLES[exampleIndex]

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "New reminder: $example"
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Create a new reminder",
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
    "Call Mum tomorrow at 18:00",
    "Pick up groceries in one hour",
    "Water the plants Saturday morning",
    "Take an umbrella when I leave home",
)

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics {
                contentDescription = "Loading reminders"
            },
        )
    }
}

@Composable
private fun EmptyReminderContent(
    title: String,
    description: String,
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
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = description,
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
            .clickable(
                enabled = !isBusy,
                onClickLabel = "Open ${item.title}",
                onClick = onOpen,
            ),
        shape = cardShape,
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
                Checkbox(
                    checked = item.isCompleted,
                    onCheckedChange = if (isBusy || inTrash) {
                        null
                    } else {
                        { checked -> onSetCompleted(checked) }
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (item.isCompleted) {
                            "Mark ${item.primaryText} not done"
                        } else {
                            "Mark ${item.primaryText} done"
                        }
                    },
                )
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
                if (!inTrash) {
                    IconButtonWithDescription(
                        description = "Edit ${item.primaryText}",
                        onClick = onEdit,
                        enabled = !isBusy,
                    ) { Icon(Icons.Default.Edit, contentDescription = null) }
                } else {
                    IconButtonWithDescription(
                        description = "Restore ${item.primaryText}",
                        onClick = onRestore,
                        enabled = !isBusy,
                    ) {
                        Icon(Icons.Default.RestoreFromTrash, contentDescription = null)
                    }
                }
                IconButtonWithDescription(
                    description = if (inTrash) {
                        "Delete permanently ${item.primaryText}"
                    } else {
                        "Delete ${item.primaryText}"
                    },
                    onClick = onDelete,
                    enabled = !isBusy,
                ) { Icon(Icons.Default.Delete, contentDescription = null) }
            }

            if (item.timeText != null || item.locationText != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.timeText?.let { time ->
                        Icon(Icons.Default.Schedule, contentDescription = "Scheduled time")
                        Text(time, color = supportingTextColor, textDecoration = textDecoration)
                    }
                    if (item.timeText != null && item.locationText != null) {
                        Text("·", color = supportingTextColor)
                    }
                    item.locationText?.let { location ->
                        Icon(Icons.Default.LocationOn, contentDescription = "Location")
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
