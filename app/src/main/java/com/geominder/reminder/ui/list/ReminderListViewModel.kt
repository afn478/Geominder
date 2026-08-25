package com.geominder.reminder.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.geominder.reminder.domain.model.Reminder
import com.geominder.reminder.domain.model.ReminderId
import com.geominder.reminder.domain.model.ReminderStatus
import com.geominder.reminder.domain.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.Locale

class ReminderListViewModel(
    private val repository: ReminderRepository,
    private val scheduleCommandHandler: ReminderScheduleCommandHandler,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val localeProvider: () -> Locale = Locale::getDefault,
    private val injectedScope: CoroutineScope? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReminderListUiState())
    val uiState: StateFlow<ReminderListUiState> = _uiState.asStateFlow()

    private var remindersById: Map<ReminderId, Reminder> = emptyMap()

    init {
        workScope().launch {
            repository.observeAll().collect { reminders ->
                remindersById = reminders.associateBy(Reminder::id)
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        items = ReminderListPresenter.present(
                            reminders = reminders,
                            zoneId = clock.zone,
                            locale = localeProvider(),
                        ),
                        deleteCandidate = current.deleteCandidate?.takeIf {
                            remindersById.containsKey(it.id)
                        },
                    )
                }
            }
        }
    }

    fun setEnabled(id: ReminderId, enabled: Boolean) {
        val reminder = remindersById[id] ?: return
        if (reminder.enabled == enabled || id in _uiState.value.busyReminderIds) return
        if (enabled && reminder.status.isTerminal) {
            _uiState.update {
                it.copy(message = "Edit this reminder to re-arm it")
            }
            return
        }

        launchMutation(id) {
            val changedAt = clock.instant()
            val changedReminder = reminder.copy(enabled = enabled, updatedAt = changedAt)
            val command = if (enabled) {
                ReminderScheduleCommand.Register(changedReminder)
            } else {
                ReminderScheduleCommand.Cancel(changedReminder)
            }
            scheduleCommandHandler.handle(command)
            try {
                repository.setEnabled(id, enabled, changedAt)
            } catch (error: Throwable) {
                val compensation = if (enabled) {
                    ReminderScheduleCommand.Cancel(changedReminder)
                } else {
                    ReminderScheduleCommand.Register(reminder)
                }
                compensate(error, compensation)
            }
        }
    }

    fun setCompleted(id: ReminderId, completed: Boolean) {
        val reminder = remindersById[id] ?: return
        if (id in _uiState.value.busyReminderIds) return

        if (!completed) {
            if (reminder.status != ReminderStatus.COMPLETED) return
            launchMutation(id) {
                val changedAt = clock.instant()
                val reopenedReminder = reminder.copy(
                    enabled = true,
                    status = ReminderStatus.PENDING,
                    updatedAt = changedAt,
                    snoozedUntil = null,
                    dismissedAt = null,
                )
                // Register the speculative pending copy before reopening persistence.
                scheduleCommandHandler.handle(ReminderScheduleCommand.Register(reopenedReminder))
                try {
                    repository.reopen(id, changedAt)
                } catch (error: Throwable) {
                    compensate(error, ReminderScheduleCommand.Cancel(reopenedReminder))
                }
            }
            return
        }

        if (reminder.status.isTerminal) return

        launchMutation(id) {
            // Remove the external trigger before recording completion in persistence.
            scheduleCommandHandler.handle(ReminderScheduleCommand.Cancel(reminder))
            try {
                repository.complete(id, clock.instant())
            } catch (error: Throwable) {
                if (reminder.isPending) {
                    compensate(error, ReminderScheduleCommand.Register(reminder))
                }
                throw error
            }
        }
    }

    /** Compatibility entry point for callers that only support marking a reminder done. */
    fun markDone(id: ReminderId) = setCompleted(id, completed = true)

    fun requestDelete(id: ReminderId) {
        if (id in _uiState.value.busyReminderIds) return
        val item = _uiState.value.items.firstOrNull { it.id == id } ?: return
        _uiState.update { it.copy(deleteCandidate = item, message = null) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(deleteCandidate = null) }
    }

    fun confirmDelete() {
        val candidate = _uiState.value.deleteCandidate ?: return
        val reminder = remindersById[candidate.id] ?: run {
            cancelDelete()
            return
        }
        if (candidate.id in _uiState.value.busyReminderIds) return

        _uiState.update { it.copy(deleteCandidate = null) }
        launchMutation(candidate.id) {
            // Cancellation comes first: a failed scheduler integration must not leave an
            // untracked alarm or geofence after persistence is removed.
            scheduleCommandHandler.handle(ReminderScheduleCommand.Cancel(reminder))
            try {
                repository.delete(reminder.id)
            } catch (error: Throwable) {
                if (reminder.isPending) {
                    compensate(error, ReminderScheduleCommand.Register(reminder))
                }
                throw error
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun launchMutation(id: ReminderId, mutation: suspend () -> Unit) {
        _uiState.update {
            it.copy(
                busyReminderIds = it.busyReminderIds + id,
                message = null,
            )
        }
        workScope().launch {
            runCatching { mutation() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            message = error.message ?: "The reminder could not be updated",
                        )
                    }
                }
            _uiState.update { it.copy(busyReminderIds = it.busyReminderIds - id) }
        }
    }

    private suspend fun compensate(
        originalError: Throwable,
        command: ReminderScheduleCommand,
    ): Nothing {
        runCatching { scheduleCommandHandler.handle(command) }
            .exceptionOrNull()
            ?.let(originalError::addSuppressed)
        throw originalError
    }

    private val ReminderStatus.isTerminal: Boolean
        get() = this == ReminderStatus.DISMISSED || this == ReminderStatus.COMPLETED

    private fun workScope(): CoroutineScope = injectedScope ?: viewModelScope
}

class ReminderListViewModelFactory(
    private val repository: ReminderRepository,
    private val scheduleCommandHandler: ReminderScheduleCommandHandler,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ReminderListViewModel::class.java)) {
            "Unsupported ViewModel class: ${modelClass.name}"
        }
        return ReminderListViewModel(
            repository = repository,
            scheduleCommandHandler = scheduleCommandHandler,
            clock = clock,
        ) as T
    }
}
