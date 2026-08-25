package com.geominder.reminder.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.geominder.reminder.domain.model.ReminderId
import com.geominder.reminder.domain.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale

class ReminderDetailViewModel(
    private val repository: ReminderRepository,
    private val reminderId: ReminderId,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
    private val injectedScope: CoroutineScope? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReminderDetailUiState.loading())
    val uiState: StateFlow<ReminderDetailUiState> = _uiState.asStateFlow()

    init {
        workScope().launch {
            repository.observe(reminderId).collectLatest { reminder ->
                _uiState.value = reminder?.let {
                    ReminderDetailUiState.present(it, zoneId, locale)
                } ?: ReminderDetailUiState.notFound()
            }
        }
    }

    private fun workScope(): CoroutineScope = injectedScope ?: viewModelScope
}

class ReminderDetailViewModelFactory(
    private val repository: ReminderRepository,
    private val reminderId: ReminderId,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ReminderDetailViewModel::class.java)) {
            "Unsupported ViewModel class: ${modelClass.name}"
        }
        return ReminderDetailViewModel(repository, reminderId) as T
    }
}
