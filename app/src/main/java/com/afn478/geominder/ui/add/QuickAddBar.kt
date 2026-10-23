package com.afn478.geominder.ui.add

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afn478.geominder.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first

/**
 * The list's quick-add bar: a real text field pinned above the keyboard by window insets alone,
 * so nothing springs or settles while typing. Detected time/place chips appear above it while
 * composing; tapping one, or the empty bar's `+`, opens the full editor on the same draft.
 *
 * @param idleHeader shown above the bar while nobody is composing (the list's tag filters).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddBar(
    viewModel: AddReminderViewModel,
    /** Opens the full editor on this draft; `focusText` puts the cursor in its text field. */
    onOpenDetails: (focusText: Boolean) -> Unit,
    onSaved: () -> Unit,
    idleHeader: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val hasText = state.sourceText.isNotBlank()
    // Chips replace the tag filters only while typing; an unfocused draft keeps the filters visible.
    val composing = focused
    val currentOnSaved by rememberUpdatedState(onSaved)

    LaunchedEffect(viewModel, state.savedReminder?.id) {
        if (state.savedReminder != null) {
            viewModel.consumeSavedReminder()
            focusManager.clearFocus()
            currentOnSaved()
        }
    }
    // Leave compose mode whenever the software keyboard goes away (back gesture, keyboard's own
    // hide key), so the cursor never keeps blinking in a field nobody is typing into. Only a
    // shown-then-hidden transition counts: the keyboard is still hidden for a moment after focusing.
    val imeVisible by rememberUpdatedState(WindowInsets.isImeVisible)
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        snapshotFlow { imeVisible }
            .dropWhile { visible -> !visible }
            .first { visible -> !visible }
        focusManager.clearFocus()
    }
    BackHandler(enabled = focused) { focusManager.clearFocus() }

    val save = {
        if (hasText && !state.isSaving) viewModel.save()
    }

    Column(modifier = modifier.imePadding()) {
        if (composing) {
            DetectionChips(
                state = state,
                onDateTimeChipClick = {
                    viewModel.beginDateTimeEdit()
                    onOpenDetails(false)
                },
                onGeoChipClick = {
                    viewModel.onGeoChipClick()
                    onOpenDetails(false)
                },
                onChecklistClick = {
                    viewModel.onChecklistClick()
                    onOpenDetails(false)
                },
                enabled = !state.isSaving,
            )
            state.saveError?.let {
                SupportingError(it, Modifier.padding(horizontal = 24.dp))
            }
        } else {
            idleHeader()
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = SearchBarDefaults.inputFieldShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = SearchBarDefaults.TonalElevation,
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 16.dp),
                ) {
                    BasicTextField(
                        value = state.sourceText,
                        onValueChange = viewModel::onSourceTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused },
                        enabled = !state.isSaving,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        visualTransformation = DateTimeHighlightTransformation(
                            spans = state.parseResult?.dateTime?.expressionSpans.orEmpty(),
                            source = state.sourceText,
                            background = MaterialTheme.colorScheme.secondaryContainer,
                            foreground = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                    if (state.sourceText.isEmpty()) {
                        RotatingExamplePlaceholder()
                    }
                }
                QuickAddAction(
                    hasText = hasText,
                    isSaving = state.isSaving,
                    onSave = save,
                    onOpenDetails = { onOpenDetails(true) },
                )
            }
        }
    }
}

/** `+` opens the full editor while the bar is empty; once there is text it saves instead. */
@Composable
private fun QuickAddAction(
    hasText: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    IconButton(
        onClick = if (hasText) onSave else onOpenDetails,
        enabled = !isSaving,
    ) {
        when {
            isSaving -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            hasText -> Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.save_reminder),
                tint = MaterialTheme.colorScheme.primary,
            )
            else -> Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.add_reminder),
            )
        }
    }
}

@Composable
private fun RotatingExamplePlaceholder() {
    var exampleIndex by remember { mutableIntStateOf(0) }
    val exampleAlpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(EXAMPLE_ROTATION_MILLIS)
            exampleAlpha.animateTo(0f, tween(durationMillis = EXAMPLE_FADE_MILLIS))
            exampleIndex = (exampleIndex + 1) % REMINDER_EXAMPLES.size
            exampleAlpha.animateTo(1f, tween(durationMillis = EXAMPLE_FADE_MILLIS))
        }
    }
    Text(
        text = stringResource(REMINDER_EXAMPLES[exampleIndex]),
        modifier = Modifier.graphicsLayer { alpha = exampleAlpha.value },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val EXAMPLE_ROTATION_MILLIS = 6_000L
private const val EXAMPLE_FADE_MILLIS = 180

private val REMINDER_EXAMPLES = listOf(
    R.string.example_call_mum,
    R.string.example_pick_up_groceries,
    R.string.example_water_plants,
    R.string.example_take_umbrella,
)
