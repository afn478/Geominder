package com.afn478.geominder.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.afn478.geominder.R
import com.afn478.geominder.domain.model.ChecklistItemId

/** Checklist callbacks bundled so each composer layout forwards them as one value. */
data class ChecklistActions(
    val onChecklistClick: () -> Unit = {},
    val onItemTextChange: (ChecklistItemId, String) -> Unit = { _, _ -> },
    val onItemDoneChange: (ChecklistItemId, Boolean) -> Unit = { _, _ -> },
    val onAddItem: (after: ChecklistItemId?) -> Unit = {},
    val onRemoveItem: (ChecklistItemId) -> Unit = {},
    val onFocusConsumed: () -> Unit = {},
)

/**
 * Samsung Reminder-style checklist: Enter opens a row below, Backspace on an empty row removes
 * it, and Enter on an empty row closes the list editing. Rows are plain text by design.
 */
@Composable
fun ChecklistEditor(
    items: List<ChecklistDraftItem>,
    focusItemId: ChecklistItemId?,
    enabled: Boolean,
    actions: ChecklistActions,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Column(modifier = modifier.fillMaxWidth()) {
        items.forEach { item ->
            key(item.id.value) {
                ChecklistEditorRow(
                    item = item,
                    requestFocus = item.id == focusItemId,
                    enabled = enabled,
                    onTextChange = { actions.onItemTextChange(item.id, it) },
                    onDoneChange = { actions.onItemDoneChange(item.id, it) },
                    onEnter = {
                        if (item.text.isBlank()) {
                            actions.onRemoveItem(item.id)
                            focusManager.clearFocus()
                        } else {
                            actions.onAddItem(item.id)
                        }
                    },
                    onRemove = { actions.onRemoveItem(item.id) },
                    onFocusConsumed = actions.onFocusConsumed,
                )
            }
        }
        TextButton(
            onClick = { actions.onAddItem(items.lastOrNull()?.id) },
            enabled = enabled,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.add_checklist_item),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ChecklistEditorRow(
    item: ChecklistDraftItem,
    requestFocus: Boolean,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onDoneChange: (Boolean) -> Unit,
    onEnter: () -> Unit,
    onRemove: () -> Unit,
    onFocusConsumed: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(item.text, TextRange(item.text.length)))
    }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            fieldValue = fieldValue.copy(selection = TextRange(fieldValue.text.length))
            focusRequester.requestFocus()
            onFocusConsumed()
        }
    }
    val placeholder = stringResource(R.string.checklist_item_placeholder)
    val label = item.text.ifBlank { placeholder }
    val doneDescription = stringResource(
        if (item.done) R.string.mark_checklist_item_not_done else R.string.mark_checklist_item_done,
        label,
    )
    val removeDescription = stringResource(R.string.remove_checklist_item, label)
    val textColor = if (item.done) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.done,
            onCheckedChange = if (enabled) onDoneChange else null,
            modifier = Modifier.semantics { contentDescription = doneDescription },
        )
        BasicTextField(
            value = fieldValue,
            onValueChange = { value ->
                // The row owns its text; pasted extra lines are split into new rows by the
                // view model, so only the first line stays here. Never resync from state, which
                // lags behind fast typing.
                val firstLine = value.text.substringBefore('\n').substringBefore('\r')
                val textChanged = value.text != fieldValue.text
                fieldValue = if (firstLine == value.text) {
                    value
                } else {
                    TextFieldValue(firstLine, TextRange(firstLine.length))
                }
                if (textChanged) onTextChange(value.text)
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (
                        event.type == KeyEventType.KeyDown &&
                        event.key == Key.Backspace &&
                        fieldValue.text.isEmpty()
                    ) {
                        onRemove()
                        true
                    } else {
                        false
                    }
                },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = textColor,
                textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { onEnter() }),
            decorationBox = { innerTextField ->
                if (fieldValue.text.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )
        IconButton(onClick = onRemove, enabled = enabled) {
            Icon(
                Icons.Default.Close,
                contentDescription = removeDescription,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
