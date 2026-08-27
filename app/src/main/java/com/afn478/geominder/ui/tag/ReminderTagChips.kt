package com.afn478.geominder.ui.tag

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.afn478.geominder.R
import com.afn478.geominder.domain.model.ReminderTag

internal val ReminderTag.color: Color
    get() = when (this) {
        ReminderTag.RED -> Color(0xFFE57373)
        ReminderTag.ORANGE -> Color(0xFFFFB74D)
        ReminderTag.GREEN -> Color(0xFF81C784)
        ReminderTag.BLUE -> Color(0xFF64B5F6)
        ReminderTag.PURPLE -> Color(0xFFBA68C8)
    }

@Composable
private fun ReminderTag.localizedLabel(): String = stringResource(
    when (this) {
        ReminderTag.RED -> R.string.tag_red
        ReminderTag.ORANGE -> R.string.tag_orange
        ReminderTag.GREEN -> R.string.tag_green
        ReminderTag.BLUE -> R.string.tag_blue
        ReminderTag.PURPLE -> R.string.tag_purple
    },
)

@Composable
internal fun ReminderTagChips(
    selectedTag: ReminderTag?,
    onTagClick: (ReminderTag) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    enabled: Boolean = true,
    compact: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val chipHeight = if (compact) 32.dp else 36.dp
    val swatchSize = if (compact) 14.dp else 16.dp
    Row(
        modifier = modifier
            .padding(contentPadding)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReminderTag.entries.forEach { tag ->
            val selected = tag == selectedTag
            val tagDescription = stringResource(
                if (selected) R.string.tag_selected else R.string.tag,
                tag.localizedLabel(),
            )
            AssistChip(
                onClick = { onTagClick(tag) },
                enabled = enabled,
                label = { TagSwatch(tag, swatchSize) },
                border = BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                ),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ),
                modifier = Modifier
                    .height(chipHeight)
                    .semantics {
                        contentDescription = tagDescription
                    },
            )
        }
        trailingContent?.invoke()
    }
}

@Composable
internal fun ReminderTrashChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val description = stringResource(
        if (selected) R.string.recycling_bin_selected else R.string.recycling_bin,
    )
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        modifier = modifier
            .height(32.dp)
            .semantics {
                contentDescription = description
            },
    )
}

@Composable
private fun TagSwatch(
    tag: ReminderTag,
    size: Dp = 16.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(tag.color),
    )
}
