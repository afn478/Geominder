package com.afn478.geominder.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.afn478.geominder.R
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.ReminderStatus
import com.afn478.geominder.domain.model.ReminderTag
import com.afn478.geominder.domain.repository.ReminderRepository
import com.afn478.geominder.settings.ReminderSortOrder
import com.afn478.geominder.settings.SettingsRepository
import com.afn478.geominder.localization.UiText
import com.afn478.geominder.localization.resource
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
    private val settingsRepository: SettingsRepository? = null,
) : ViewModel() {
    private val initialSortOrder = settingsRepository?.settings?.value?.sortOrder
        ?: ReminderSortOrder.DEFAULT
    private val _uiState = MutableStateFlow(
        ReminderListUiState(sortOrder = initialSortOrder),
    )
    val uiState: StateFlow<ReminderListUiState> = _uiState.asStateFlow()

    private var remindersById: Map<ReminderId, Reminder> = emptyMap()
    private var reminders: List<Reminder> = emptyList()

    init {
        settingsRepository?.let { repository ->
            workScope().launch {
                repository.settings.collect { settings ->
                    val sortOrder = settings.sortOrder
                    _uiState.update { current ->
                        if (current.sortOrder == sortOrder) {
                            current
                        } else {
                            current.copy(
                                sortOrder = sortOrder,
                                items = present(
                                    reminders = reminders,
                                    sortOrder = sortOrder,
                                    selectedTag = current.selectedTag,
                                    showDeleted = current.showTrash,
                                ),
                            )
                        }
                    }
                }
            }
        }
        workScope().launch {
            repository.observeAll().collect { observedReminders ->
                reminders = observedReminders
                remindersById = observedReminders.associateBy(Reminder::id)
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        items = present(
                            reminders = observedReminders,
                            sortOrder = current.sortOrder,
                            selectedTag = current.selectedTag,
                            showDeleted = current.showTrash,
                        ),
                    )
                }
            }
        }
    }

    fun setSortOrder(sortOrder: ReminderSortOrder) {
        if (sortOrder == _uiState.value.sortOrder) return

        _uiState.update {
            it.copy(
                sortOrder = sortOrder,
                items = present(
                    reminders = reminders,
                    sortOrder = sortOrder,
                    selectedTag = it.selectedTag,
                    showDeleted = it.showTrash,
                ),
                message = null,
            )
        }
        settingsRepository?.let { repository ->
            workScope().launch {
                runCatching { repository.setSortOrder(sortOrder) }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                message = error.message?.let(UiText::Plain)
                                    ?: UiText.resource(R.string.sort_order_save_failed),
                            )
                        }
                    }
            }
        }
    }

    fun toggleTagFilter(tag: ReminderTag) {
        _uiState.update { state ->
            val selectedTag = if (state.selectedTag == tag) null else tag
            state.copy(
                selectedTag = selectedTag,
                items = present(
                    reminders = reminders,
                    sortOrder = state.sortOrder,
                    selectedTag = selectedTag,
                    showDeleted = state.showTrash,
                ),
                message = null,
            )
        }
    }

    fun toggleTrash() {
        _uiState.update { state ->
            val showTrash = !state.showTrash
            state.copy(
                showTrash = showTrash,
                items = present(
                    reminders = reminders,
                    sortOrder = state.sortOrder,
                    selectedTag = state.selectedTag,
                    showDeleted = showTrash,
                ),
                message = null,
            )
        }
    }

    fun setEnabled(id: ReminderId, enabled: Boolean) {
        val reminder = remindersById[id] ?: return
        if (
            reminder.isDeleted ||
            reminder.enabled == enabled ||
            id in _uiState.value.busyReminderIds
        ) return
        if (enabled && reminder.status.isTerminal) {
            _uiState.update {
                it.copy(message = UiText.resource(R.string.edit_to_rearm_reminder))
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
        if (reminder.isDeleted || id in _uiState.value.busyReminderIds) return

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

    fun deleteReminder(id: ReminderId) {
        val state = _uiState.value
        val reminder = remindersById[id] ?: return
        if (id in state.busyReminderIds || reminder.isDeleted != state.showTrash) return

        if (state.showTrash) {
            permanentlyDelete(id, reminder)
        } else {
            moveToTrash(id, reminder)
        }
    }

    private fun moveToTrash(id: ReminderId, reminder: Reminder) {
        launchMutation(id) {
            // Cancellation comes first: a failed scheduler integration must not leave an
            // untracked alarm or geofence after a reminder is moved to trash.
            scheduleCommandHandler.handle(ReminderScheduleCommand.Cancel(reminder))
            try {
                repository.moveToTrash(id, clock.instant())
            } catch (error: Throwable) {
                if (reminder.isPending) {
                    compensate(error, ReminderScheduleCommand.Register(reminder))
                }
                throw error
            }
            _uiState.update { it.copy(undoDeleteReminderId = id) }
        }
    }

    private fun permanentlyDelete(id: ReminderId, reminder: Reminder) {
        launchMutation(id) {
            // The reminder has already been unscheduled when it entered trash.
            scheduleCommandHandler.handle(ReminderScheduleCommand.Cancel(reminder))
            repository.delete(id)
            _uiState.update { state ->
                if (state.undoDeleteReminderId == id) {
                    state.copy(undoDeleteReminderId = null)
                } else {
                    state
                }
            }
        }
    }

    fun undoDelete(id: ReminderId) {
        val state = _uiState.value
        val reminder = remindersById[id] ?: return consumeUndoDeleteNotice(id)
        if (
            state.undoDeleteReminderId != id ||
            !reminder.isDeleted ||
            id in state.busyReminderIds
        ) {
            return
        }

        restoreDeletedReminder(id, reminder)
    }

    fun restoreReminder(id: ReminderId) {
        val state = _uiState.value
        val reminder = remindersById[id] ?: return
        if (
            !state.showTrash ||
            !reminder.isDeleted ||
            id in state.busyReminderIds
        ) {
            return
        }

        restoreDeletedReminder(id, reminder)
    }

    private fun restoreDeletedReminder(id: ReminderId, reminder: Reminder) {
        _uiState.update { state ->
            if (state.undoDeleteReminderId == id) {
                state.copy(undoDeleteReminderId = null)
            } else {
                state
            }
        }
        launchMutation(id) {
            val restoredReminder = reminder.copy(
                updatedAt = clock.instant(),
                deletedAt = null,
            )
            if (restoredReminder.isPending) {
                scheduleCommandHandler.handle(ReminderScheduleCommand.Register(restoredReminder))
                try {
                    repository.restoreFromTrash(id, restoredReminder.updatedAt)
                } catch (error: Throwable) {
                    compensate(error, ReminderScheduleCommand.Cancel(restoredReminder))
                }
            } else {
                repository.restoreFromTrash(id, restoredReminder.updatedAt)
            }
        }
    }

    fun consumeUndoDeleteNotice(id: ReminderId) {
        _uiState.update { state ->
            if (state.undoDeleteReminderId == id) {
                state.copy(undoDeleteReminderId = null)
            } else {
                state
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
                                message = error.message?.let(UiText::Plain)
                                    ?: UiText.resource(R.string.reminder_update_failed),
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

    private fun present(
        reminders: List<Reminder>,
        sortOrder: ReminderSortOrder,
        selectedTag: ReminderTag?,
        showDeleted: Boolean,
    ) = ReminderListPresenter.present(
        reminders = reminders,
        zoneId = clock.zone,
        locale = localeProvider(),
        sortOrder = sortOrder,
        selectedTag = selectedTag,
        showDeleted = showDeleted,
    )

    private fun workScope(): CoroutineScope = injectedScope ?: viewModelScope
}

class ReminderListViewModelFactory(
    private val repository: ReminderRepository,
    private val scheduleCommandHandler: ReminderScheduleCommandHandler,
    private val settingsRepository: SettingsRepository? = null,
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
            settingsRepository = settingsRepository,
        ) as T
    }
}
