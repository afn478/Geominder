package com.afn478.geominder.ui.list

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afn478.geominder.domain.model.ReminderId

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
        onOpenReminder = onOpenReminder,
        onEditReminder = onEditReminder,
        onSetCompleted = viewModel::setCompleted,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
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
    onOpenReminder: (ReminderId) -> Unit,
    onEditReminder: (ReminderId) -> Unit,
    onSetCompleted: (ReminderId, Boolean) -> Unit,
    onRequestDelete: (ReminderId) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Reminders") },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Settings")
                    }
                },
            )
        },
        bottomBar = {
            NewReminderLauncher(onClick = onAddReminder)
        },
        snackbarHost = {
            state.message?.let { message ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = onDismissMessage) {
                            Text("Dismiss")
                        }
                    },
                ) {
                    Text(message)
                }
            }
        },
    ) { contentPadding ->
        when {
            state.isLoading -> LoadingContent(
                modifier = Modifier.padding(contentPadding),
            )

            state.isEmpty -> EmptyReminderContent(
                modifier = Modifier.padding(contentPadding),
            )

            else -> ReminderItems(
                items = state.items,
                busyReminderIds = state.busyReminderIds,
                onOpenReminder = onOpenReminder,
                onEditReminder = onEditReminder,
                onSetCompleted = onSetCompleted,
                onRequestDelete = onRequestDelete,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }

    state.deleteCandidate?.let { reminder ->
        DeleteReminderDialog(
            reminder = reminder,
            onConfirm = onConfirmDelete,
            onDismiss = onCancelDelete,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NewReminderLauncher(
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "New reminder"
            }
            .clickable(
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
                text = "New reminder",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
                text = "Nothing to remember yet",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Create a reminder for a time, a place, or both. " +
                    "It will appear here so you can open, edit, or mark it done.",
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
    onRequestDelete: (ReminderId) -> Unit,
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
                onDelete = { onRequestDelete(item.id) },
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
) {
    val motionSpec: FiniteAnimationSpec<IntSize> = MaterialTheme.motionScheme.defaultSpatialSpec()
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
            .clickable(
                enabled = !isBusy,
                onClickLabel = "Open ${item.title}",
                onClick = onOpen,
            ),
        shape = MaterialTheme.shapes.extraLarge,
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
                    onCheckedChange = if (isBusy) {
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
                IconButtonWithDescription(
                    description = "Edit ${item.primaryText}",
                    onClick = onEdit,
                    enabled = !isBusy,
                ) { Icon(Icons.Default.Edit, contentDescription = null) }
                IconButtonWithDescription(
                    description = "Delete ${item.primaryText}",
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

@Composable
private fun DeleteReminderDialog(
    reminder: ReminderListItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete reminder?") },
        text = {
            Text("“${reminder.title}” and its scheduled alarm or location trigger will be removed.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep reminder")
            }
        },
    )
}
