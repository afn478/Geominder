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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
                title = { Text(state.title ?: "Reminder") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
            state.isNotFound -> DetailMessage("Reminder not found.", Modifier.padding(padding))
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
        state.sourceText?.let { DetailField("Source", it) }
        state.text?.takeIf { it.isNotBlank() }?.let { DetailField("Text", it) }
        state.lifecycleLabel?.let { DetailField("Status", it) }
        state.timeText?.let { DetailField("Time", it) }
        if (state.geoCoordinates != null) {
            DetailField("Location", state.geoLabel ?: state.geoCoordinates)
            DetailField("Coordinates", state.geoCoordinates)
            state.geoRadius?.let { DetailField("Radius", it) }
            state.geoActiveFrom?.let { DetailField("Active from", it) }
        }
        DetailField("Created", state.createdAt ?: "")
        state.updatedAt?.let { DetailField("Updated", it) }
        state.triggeredAt?.let { DetailField("Triggered", it) }
        state.snoozedUntil?.let { DetailField("Snoozed until", it) }
        state.dismissedAt?.let { DetailField("Dismissed", it) }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
