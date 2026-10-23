package com.afn478.geominder.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.afn478.geominder.R
import com.afn478.geominder.backup.CalendarImportResult
import com.afn478.geominder.backup.SafReminderBackup
import com.afn478.geominder.localization.UiText
import com.afn478.geominder.localization.resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val inProgress: Boolean = false,
    val status: UiText? = null,
)

/**
 * Owns backup work at activity scope so a rotation or theme change while a document is being
 * written or read neither cancels the operation halfway nor loses its result message.
 */
class BackupViewModel(
    private val backup: SafReminderBackup,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun clearStatus() {
        _uiState.update { it.copy(status = null) }
    }

    fun exportTo(uri: Uri) = launchOperation {
        runCatching { backup.exportTo(uri) }.fold(
            onSuccess = { UiText.resource(R.string.backup_exported, it.exported) },
            onFailure = { UiText.resource(R.string.backup_export_failed) },
        )
    }

    fun importFrom(uri: Uri) = launchOperation {
        runCatching { backup.importFrom(uri) }.fold(
            onSuccess = ::importSummary,
            onFailure = { UiText.resource(R.string.backup_import_failed) },
        )
    }

    private fun launchOperation(operation: suspend () -> UiText) {
        if (_uiState.value.inProgress) return
        _uiState.value = BackupUiState(inProgress = true)
        viewModelScope.launch {
            val status = operation()
            _uiState.value = BackupUiState(inProgress = false, status = status)
        }
    }

    private fun importSummary(result: CalendarImportResult): UiText = if (result.succeeded) {
        UiText.resource(
            R.string.backup_imported,
            result.inserted,
            result.updated,
            result.scheduled,
            result.schedulingFailed,
            result.skipped,
            result.issues.size,
        )
    } else {
        result.fatalError?.let(UiText::Plain) ?: UiText.resource(R.string.backup_import_failed)
    }
}

class BackupViewModelFactory(
    private val backup: SafReminderBackup,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(BackupViewModel::class.java)) {
            "Unsupported ViewModel class: ${modelClass.name}"
        }
        return BackupViewModel(backup) as T
    }
}
