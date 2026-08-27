package com.afn478.geominder.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.afn478.geominder.R
import com.afn478.geominder.domain.model.PresetLocation
import com.afn478.geominder.geofence.CancellationHandle
import com.afn478.geominder.geofence.ClipboardGeoInputParser
import com.afn478.geominder.geofence.CurrentLocationProvider
import com.afn478.geominder.geofence.GeoInputError
import com.afn478.geominder.geofence.GeoInputField
import com.afn478.geominder.geofence.LocationFailure
import com.afn478.geominder.geofence.LocationResult
import com.afn478.geominder.geofence.NumericGeoInputParser
import com.afn478.geominder.geofence.NumericGeoInputResult
import com.afn478.geominder.localization.SupportedLanguage
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
    private val locationProvider: CurrentLocationProvider? = null,
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

    private var locationRequest: CancellationHandle? = null
    private var locationRequestVersion = 0L

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

    fun onKeywordLanguageChange(
        language: SupportedLanguage?,
        onPersisted: () -> Unit = {},
    ) = persist {
        repository.setKeywordLanguage(language)
        onPersisted()
    }

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

    fun beginAddLocation() {
        cancelLocationRequest()
        _uiState.update {
            it.copy(
                locationEditorVisible = true,
                editingLocationKeyword = null,
                locationKeywordText = "",
                locationLatitudeText = "",
                locationLongitudeText = "",
                locationRadiusText = SettingsValidation.formatRadius(
                    it.settings.defaultGeofenceRadiusMeters,
                ),
                locationKeywordError = null,
                locationInputErrors = emptyMap(),
                locationError = null,
                isLocatingLocation = false,
                persistenceError = null,
            )
        }
    }

    fun beginEditLocation(keyword: String) {
        val location = _uiState.value.settings.keywordLocations[keyword] ?: return
        cancelLocationRequest()
        _uiState.update {
            it.copy(
                locationEditorVisible = true,
                editingLocationKeyword = keyword,
                locationKeywordText = keyword,
                locationLatitudeText = location.latitude.toPlainString(),
                locationLongitudeText = location.longitude.toPlainString(),
                locationRadiusText = SettingsValidation.formatRadius(location.radiusMeters),
                locationKeywordError = null,
                locationInputErrors = emptyMap(),
                locationError = null,
                isLocatingLocation = false,
                persistenceError = null,
            )
        }
    }

    fun onLocationKeywordChange(value: String) {
        _uiState.update {
            it.copy(
                locationKeywordText = value,
                locationKeywordError = null,
                locationError = null,
                persistenceError = null,
            )
        }
    }

    fun onLocationLatitudeChange(value: String) {
        updateLocationText(latitude = value)
    }

    fun onLocationLongitudeChange(value: String) {
        updateLocationText(longitude = value)
    }

    fun onLocationRadiusChange(value: String) {
        updateLocationText(radius = value)
    }

    fun pasteLocation(clipboardText: String) {
        val coordinates = ClipboardGeoInputParser.parse(clipboardText)
        if (coordinates == null) {
            _uiState.update {
                it.copy(locationError = UiText.resource(R.string.invalid_clipboard_coordinates))
            }
            return
        }
        _uiState.update {
            it.copy(
                locationEditorVisible = true,
                locationLatitudeText = coordinates.latitude.toPlainString(),
                locationLongitudeText = coordinates.longitude.toPlainString(),
                locationInputErrors = emptyMap(),
                locationError = null,
                persistenceError = null,
            )
        }
    }

    fun locateLocation() {
        locationRequest?.cancel()
        val provider = locationProvider
        if (provider == null) {
            _uiState.update {
                it.copy(
                    isLocatingLocation = false,
                    locationError = LocationFailure.NO_LOCATION.userMessage(),
                )
            }
            return
        }

        val version = ++locationRequestVersion
        _uiState.update {
            it.copy(
                locationEditorVisible = true,
                isLocatingLocation = true,
                locationError = null,
            )
        }
        locationRequest = provider.locate { result ->
            if (version != locationRequestVersion) return@locate
            locationRequest = null
            when (result) {
                is LocationResult.Available -> {
                    val fix = result.fix
                    _uiState.update {
                        it.copy(
                            locationLatitudeText = fix.latitude.toPlainString(),
                            locationLongitudeText = fix.longitude.toPlainString(),
                            locationInputErrors = emptyMap(),
                            locationError = null,
                            isLocatingLocation = false,
                        )
                    }
                }

                is LocationResult.Unavailable -> {
                    _uiState.update {
                        it.copy(
                            isLocatingLocation = false,
                            locationError = result.reason.userMessage(),
                        )
                    }
                }
            }
        }
    }

    fun saveLocation() {
        val state = _uiState.value
        val keyword = SettingsValidation.keyword(state.locationKeywordText)
        val geo = NumericGeoInputParser(
            defaultRadiusMeters = state.settings.defaultGeofenceRadiusMeters,
        ).parse(
            latitudeText = state.locationLatitudeText,
            longitudeText = state.locationLongitudeText,
            radiusText = state.locationRadiusText,
        )
        if (keyword is ValidationResult.Invalid || geo is NumericGeoInputResult.Invalid) {
            _uiState.update {
                it.copy(
                    locationKeywordError = (keyword as? ValidationResult.Invalid)?.error?.toUiText(),
                    locationInputErrors = (geo as? NumericGeoInputResult.Invalid)?.errors.orEmpty(),
                )
            }
            return
        }

        keyword as ValidationResult.Valid
        geo as NumericGeoInputResult.Valid
        persist {
            val originalKeyword = state.editingLocationKeyword
            if (originalKeyword != null && originalKeyword != keyword.value) {
                repository.removeKeywordLocation(originalKeyword)
            }
            repository.upsertKeywordLocation(
                keyword.value,
                PresetLocation(
                    latitude = geo.value.latitude,
                    longitude = geo.value.longitude,
                    radiusMeters = geo.value.radiusMeters,
                ),
            )
            clearLocationEditor()
        }
    }

    fun cancelLocationEdit() {
        clearLocationEditor()
    }

    fun removeLocation(keyword: String) {
        persist {
            repository.removeKeywordLocation(keyword)
            if (_uiState.value.editingLocationKeyword == keyword) clearLocationEditor()
        }
    }

    fun resetKeywordLocations() {
        persist {
            repository.resetKeywordLocations()
            clearLocationEditor()
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

    private fun updateLocationText(
        latitude: String? = null,
        longitude: String? = null,
        radius: String? = null,
    ) {
        _uiState.update {
            it.copy(
                locationLatitudeText = latitude ?: it.locationLatitudeText,
                locationLongitudeText = longitude ?: it.locationLongitudeText,
                locationRadiusText = radius ?: it.locationRadiusText,
                locationInputErrors = emptyMap(),
                locationError = null,
                persistenceError = null,
            )
        }
    }

    private fun clearLocationEditor() {
        cancelLocationRequest()
        _uiState.update {
            it.copy(
                locationEditorVisible = false,
                editingLocationKeyword = null,
                locationKeywordText = "",
                locationLatitudeText = "",
                locationLongitudeText = "",
                locationRadiusText = SettingsValidation.formatRadius(
                    it.settings.defaultGeofenceRadiusMeters,
                ),
                locationKeywordError = null,
                locationInputErrors = emptyMap(),
                locationError = null,
                isLocatingLocation = false,
            )
        }
    }

    private fun cancelLocationRequest() {
        locationRequest?.cancel()
        locationRequest = null
        locationRequestVersion++
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

    override fun onCleared() {
        locationRequest?.cancel()
        locationRequest = null
        super.onCleared()
    }

    private fun Double.toPlainString(): String = when {
        this == toLong().toDouble() -> toLong().toString()
        else -> toString()
    }

    private fun LocationFailure.userMessage(): UiText = UiText.resource(
        when (this) {
            LocationFailure.PERMISSION_REQUIRED -> R.string.location_permission_required
            LocationFailure.BACKGROUND_PERMISSION_REQUIRED -> R.string.background_location_permission_required
            LocationFailure.LOCATION_DISABLED -> R.string.location_services_disabled
            LocationFailure.PLAY_SERVICES_UNAVAILABLE -> R.string.play_services_unavailable
            LocationFailure.NO_LOCATION -> R.string.no_current_location
            LocationFailure.REQUEST_FAILED -> R.string.location_request_failed
        },
    )
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
    private val locationProvider: CurrentLocationProvider? = null,
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
            locationProvider = locationProvider,
        ) as T
    }
}
