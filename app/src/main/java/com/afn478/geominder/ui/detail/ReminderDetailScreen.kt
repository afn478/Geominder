package com.afn478.geominder.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afn478.geominder.R
import com.afn478.geominder.ui.text.resolve
import com.afn478.geominder.ui.text.resolveNearbyLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDetailRoute(
    viewModel: ReminderDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReminderDetailScreen(state, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDetailScreen(
    state: ReminderDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state.title ?: stringResource(R.string.reminder)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
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
            else -> DetailContent(state, Modifier.padding(padding))
        }
    }
}

@Composable
private fun DetailMessage(message: String, modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(message)
    }
}

@Composable
private fun DetailContent(state: ReminderDetailUiState, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.sourceText?.let { DetailField(stringResource(R.string.source), it) }
        state.text?.takeIf { it.isNotBlank() }?.let { DetailField(stringResource(R.string.text), it) }
        state.lifecycleLabel?.let {
            DetailField(stringResource(R.string.status), it.resolve())
        }
        state.timeText?.let { DetailField(stringResource(R.string.time), it) }
        if (state.geoCoordinates != null) {
            DetailField(
                stringResource(R.string.location),
                state.geoLabel?.resolveNearbyLabel() ?: state.geoCoordinates,
            )
            DetailField(stringResource(R.string.coordinates), state.geoCoordinates)
            state.geoRadius?.let { DetailField(stringResource(R.string.radius), it) }
            state.geoActiveFrom?.let { DetailField(stringResource(R.string.active_from), it) }
        }
        DetailField(stringResource(R.string.created), state.createdAt ?: "")
        state.updatedAt?.let { DetailField(stringResource(R.string.updated), it) }
        state.triggeredAt?.let { DetailField(stringResource(R.string.triggered), it) }
        state.snoozedUntil?.let { DetailField(stringResource(R.string.snoozed_until), it) }
        state.dismissedAt?.let { DetailField(stringResource(R.string.dismissed), it) }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
