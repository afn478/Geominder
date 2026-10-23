package com.afn478.geominder.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afn478.geominder.R
import com.afn478.geominder.ui.map.LocationMapPreview
import com.afn478.geominder.domain.model.ChecklistItemId
import com.afn478.geominder.domain.model.GeoTrigger
import com.afn478.geominder.ui.appbar.ReachableScaffold
import com.afn478.geominder.ui.tag.color
import com.afn478.geominder.ui.text.resolve
import com.afn478.geominder.ui.text.resolveNearbyLabel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDetailRoute(
    viewModel: ReminderDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReminderDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onSetChecklistItemDone = viewModel::setChecklistItemDone,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDetailScreen(
    state: ReminderDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onSetChecklistItemDone: (ChecklistItemId, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    ReachableScaffold(
        // The reminder text is the page's headline below, so the bar names the screen instead.
        title = stringResource(R.string.reminder),
        modifier = modifier,
        compactTitleStartPadding = 56.dp,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        actions = {
            // Trashed reminders are restored from the list first, like the list's own edit button.
            if (state.reminder?.isDeleted == false) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_reminder),
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.isNotFound -> DetailMessage(
                stringResource(R.string.reminder_not_found),
                Modifier.padding(padding),
            )
            else -> DetailContent(state, onSetChecklistItemDone, Modifier.padding(padding))
        }
    }
}

@Composable
private fun DetailMessage(message: String, modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(32.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailContent(
    state: ReminderDetailUiState,
    onSetChecklistItemDone: (ChecklistItemId, Boolean) -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DetailHeader(state)
            if (state.checklist.isNotEmpty()) {
                ChecklistCard(state, onSetChecklistItemDone)
            }
            if (state.timeText != null || state.snoozedUntil != null) {
                TimeCard(state)
            }
            state.reminder?.geoTrigger?.let { trigger -> LocationCard(state, trigger) }
            HistoryCard(state)
        }
    }
}

@Composable
private fun DetailHeader(state: ReminderDetailUiState) {
    val title = state.title ?: stringResource(R.string.reminder)
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.lifecycleLabel?.let { StatusPill(it.resolve(), state.tone) }
            state.reminder?.tag?.let { tag ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(tag.color),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Only worth showing when parsing removed something, such as "tomorrow at 8".
        state.sourceText
            ?.takeIf { it.isNotBlank() && it.trim() != title.trim() }
            ?.let { source ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.you_wrote),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = source,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
    }
}

@Composable
private fun StatusPill(label: String, tone: ReminderDetailTone) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        ReminderDetailTone.ACTIVE -> colors.primaryContainer to colors.onPrimaryContainer
        ReminderDetailTone.WAITING -> colors.tertiaryContainer to colors.onTertiaryContainer
        ReminderDetailTone.INACTIVE -> colors.surfaceContainerHighest to colors.onSurfaceVariant
        ReminderDetailTone.DELETED -> colors.errorContainer to colors.onErrorContainer
    }
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun TimeCard(state: ReminderDetailUiState) {
    DetailCard(icon = Icons.Default.Schedule, label = stringResource(R.string.time)) {
        state.timeClockText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        state.timeDateText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.snoozedUntil?.let { until ->
            InfoPill(
                icon = Icons.Default.Snooze,
                text = "${stringResource(R.string.snoozed_until)} $until",
            )
        }
    }
}

@Composable
private fun LocationCard(state: ReminderDetailUiState, trigger: GeoTrigger) {
    val context = LocalContext.current
    val placeName = state.geoLabel?.resolveNearbyLabel() ?: state.geoCoordinates.orEmpty()
    DetailCard(icon = Icons.Default.LocationOn, label = stringResource(R.string.location)) {
        LocationMapPreview(
            latitude = trigger.latitude,
            longitude = trigger.longitude,
            radiusMeters = trigger.radiusMeters,
            onClick = null,
            height = 152.dp,
        )
        Text(
            text = placeName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        state.geoCoordinates?.takeIf { it != placeName }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.geoRadius?.let {
                InfoPill(text = "${stringResource(R.string.radius)} · $it")
            }
            state.geoActiveFrom?.let {
                InfoPill(text = "${stringResource(R.string.active_from)} · $it")
            }
        }
        FilledTonalButton(
            onClick = {
                // Maps apps are optional; a missing handler must not crash the screen.
                runCatching { context.startActivity(mapIntent(trigger, state.geoLabel)) }
            },
        ) {
            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(R.string.show_on_map),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ChecklistCard(
    state: ReminderDetailUiState,
    onSetChecklistItemDone: (ChecklistItemId, Boolean) -> Unit,
) {
    DetailCard(
        icon = Icons.Default.Checklist,
        label = stringResource(
            R.string.checklist_with_progress,
            state.checklist.count { it.done },
            state.checklist.size,
        ),
    ) {
        state.checklist.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = item.done,
                        // Trashed reminders stay read-only until restored.
                        enabled = state.canEditChecklist,
                        role = Role.Checkbox,
                        onValueChange = { done -> onSetChecklistItemDone(item.id, done) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Checkbox(
                    checked = item.done,
                    onCheckedChange = null,
                    enabled = state.canEditChecklist,
                )
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (item.done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(state: ReminderDetailUiState) {
    val rows = listOfNotNull(
        state.createdAt?.let { R.string.created to it },
        state.updatedAt?.takeIf { it != state.createdAt }?.let { R.string.updated to it },
        state.triggeredAt?.let { R.string.triggered to it },
        state.dismissedAt?.let { R.string.dismissed to it },
    )
    if (rows.isEmpty()) return
    DetailCard(icon = Icons.Default.History, label = stringResource(R.string.history)) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(label),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** Card with a tinted icon badge and a small section label, matching the settings rows. */
@Composable
private fun DetailCard(
    icon: ImageVector,
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun InfoPill(text: String, icon: ImageVector? = null) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp)) }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun mapIntent(trigger: GeoTrigger, label: String?): Intent {
    val coordinates = String.format(Locale.ROOT, "%.6f,%.6f", trigger.latitude, trigger.longitude)
    val query = label?.takeIf(String::isNotBlank)
        ?.let { "$coordinates(${Uri.encode(it.removePrefix("near ").trim())})" }
        ?: coordinates
    return Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coordinates?q=$query"))
}
