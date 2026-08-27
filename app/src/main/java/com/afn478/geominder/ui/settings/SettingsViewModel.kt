package com.afn478.geominder.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.afn478.geominder.R
import com.afn478.geominder.localization.UiText
import com.afn478.geominder.localization.resource
import com.afn478.geominder.settings.AccentTheme
import com.afn478.geominder.settings.SettingsPermissionPolicy
import com.afn478.geominder.settings.ThemeMode
import com.afn478.geominder.settings.SettingsPermissionStatusProvider
import com.afn478.geominder.settings.SettingsRepository
import com.afn478.geominder.settings.SettingsValidation
import com.afn478.geominder.settings.ValidationError
import com.afn478.geominder.settings.ValidationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val permissionStatusProvider: SettingsPermissionStatusProvider,
    private val injectedScope: CoroutineScope? = null,
    private val localeProvider: () -> Locale = Locale::getDefault,
) : ViewModel() {
    private val initialSettings = repository.settings.value
    private val locale = localeProvider()
    private val _uiState = MutableStateFlow(
        SettingsUiState(
            settings = initialSettings,
            locale = locale,
            radiusText = SettingsValidation.formatRadius(
                initialSettings.defaultGeofenceRadiusMeters,
            ),
            permissionItems = permissionItems(),
        ),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        workScope().launch {
            repository.settings.collectLatest { settings ->
                _uiState.update { state ->
                    state.copy(
                        settings = settings,
                        radiusText = if (state.radiusError == null) {
                            SettingsValidation.formatRadius(
                                settings.defaultGeofenceRadiusMeters,
                            )
                        } else {
                            state.radiusText
                        },
                    )
                }
            }
        }
    }

    fun onRadiusChange(value: String) {
        _uiState.update {
            it.copy(radiusText = value, radiusError = null, persistenceError = null)
        }
    }

    fun onThemeModeChange(mode: ThemeMode) = persist { repository.setThemeMode(mode) }

    fun onAccentThemeChange(accent: AccentTheme) = persist { repository.setAccentTheme(accent) }

    fun onRemoveTimeExpressionsFromTextChange(enabled: Boolean) = persist {
        repository.setRemoveTimeExpressionsFromText(enabled)
    }

    fun saveRadius() {
        when (val validation = SettingsValidation.radius(_uiState.value.radiusText)) {
            is ValidationResult.Invalid -> {
                _uiState.update { it.copy(radiusError = validation.error.toUiText()) }
            }
            is ValidationResult.Valid -> persist {
                repository.setDefaultRadiusMeters(validation.value)
                _uiState.update { it.copy(radiusError = null) }
            }
        }
    }

    fun beginAddKeyword() {
        _uiState.update {
            it.copy(
                keywordEditorVisible = true,
                editingKeyword = null,
                keywordText = "",
                keywordTimeText = "",
                keywordError = null,
                keywordTimeError = null,
                persistenceError = null,
            )
        }
    }

    fun beginEditKeyword(keyword: String) {
        val time = _uiState.value.settings.keywordTimes[keyword] ?: return
        _uiState.update {
            it.copy(
                keywordEditorVisible = true,
                editingKeyword = keyword,
                keywordText = keyword,
                keywordTimeText = SettingsValidation.formatTime(time, locale),
                keywordError = null,
                keywordTimeError = null,
                persistenceError = null,
            )
        }
    }

    fun onKeywordChange(value: String) {
        _uiState.update {
            it.copy(keywordText = value, keywordError = null, persistenceError = null)
        }
    }

    fun onKeywordTimeChange(value: String) {
        _uiState.update {
            it.copy(keywordTimeText = value, keywordTimeError = null, persistenceError = null)
        }
    }

    fun saveKeyword() {
        val state = _uiState.value
        val keyword = SettingsValidation.keyword(state.keywordText)
        val time = SettingsValidation.time(state.keywordTimeText, locale)
        if (keyword is ValidationResult.Invalid || time is ValidationResult.Invalid) {
            _uiState.update {
                it.copy(
                    keywordError = (keyword as? ValidationResult.Invalid)?.error?.toUiText(),
                    keywordTimeError = (time as? ValidationResult.Invalid)?.error?.toUiText(),
                )
            }
            return
        }

        keyword as ValidationResult.Valid
        time as ValidationResult.Valid
        persist {
            val originalKeyword = state.editingKeyword
            if (originalKeyword != null && originalKeyword != keyword.value) {
                repository.removeKeyword(originalKeyword)
            }
            repository.upsertKeywordTime(keyword.value, time.value)
            clearKeywordEditor()
        }
    }

    fun cancelKeywordEdit() {
        clearKeywordEditor()
    }

    fun removeKeyword(keyword: String) {
        persist {
            repository.removeKeyword(keyword)
            if (_uiState.value.editingKeyword == keyword) clearKeywordEditor()
        }
    }

    fun resetKeywordTimes() {
        persist {
            repository.resetKeywordTimes()
            clearKeywordEditor()
        }
    }

    fun refreshPermissionStatus() {
        _uiState.update { it.copy(permissionItems = permissionItems()) }
    }

    private fun clearKeywordEditor() {
        _uiState.update {
            it.copy(
                keywordEditorVisible = false,
                editingKeyword = null,
                keywordText = "",
                keywordTimeText = "",
                keywordError = null,
                keywordTimeError = null,
            )
        }
    }

    private fun permissionItems() = SettingsPermissionPolicy.items(
        permissionStatusProvider.snapshot(),
    )

    private fun persist(block: suspend () -> Unit) {
        workScope().launch {
            runCatching { block() }
                .onSuccess { _uiState.update { it.copy(persistenceError = null) } }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            persistenceError = error.message?.let(UiText::Plain)
                                ?: UiText.resource(R.string.setting_save_failed),
                        )
                    }
                }
        }
    }

    private fun workScope(): CoroutineScope = injectedScope ?: viewModelScope
}

private fun ValidationError.toUiText(): UiText = UiText.resource(
    when (this) {
        ValidationError.RADIUS_NUMBER -> R.string.validation_radius_number
        ValidationError.RADIUS_FINITE -> R.string.validation_radius_finite
        ValidationError.RADIUS_RANGE -> R.string.validation_radius_range
        ValidationError.KEYWORD_EMPTY -> R.string.validation_keyword_empty
        ValidationError.KEYWORD_TOO_LONG -> R.string.validation_keyword_too_long
        ValidationError.KEYWORD_CHARACTER -> R.string.validation_keyword_character
        ValidationError.TIME_FORMAT -> R.string.validation_time_format
    },
)

class SettingsViewModelFactory(
    private val repository: SettingsRepository,
    private val permissionStatusProvider: SettingsPermissionStatusProvider,
    private val localeProvider: () -> Locale = Locale::getDefault,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            "Unsupported ViewModel class: ${modelClass.name}"
        }
        return SettingsViewModel(
            repository = repository,
            permissionStatusProvider = permissionStatusProvider,
            localeProvider = localeProvider,
        ) as T
    }
}
