package com.afn478.geominder.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the new-reminder draft shared by the quick-add bar and the full editor. The draft survives
 * activity recreation and moving between the two, and is released when the composer closes.
 */
class AddReminderComposerViewModel : ViewModel() {
    private val _session = MutableStateFlow<ViewModelStoreOwner?>(null)
    val session: StateFlow<ViewModelStoreOwner?> = _session.asStateFlow()

    fun open() {
        if (_session.value != null) return
        _session.value = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    fun close() {
        val current = _session.value ?: return
        _session.value = null
        current.viewModelStore.clear()
    }

    /** Discards the current draft and starts an empty one, for example after a save. */
    fun reset() {
        close()
        open()
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }
}
