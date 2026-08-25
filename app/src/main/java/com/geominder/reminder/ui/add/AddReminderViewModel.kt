package com.geominder.reminder.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.geominder.reminder.domain.model.GeoTrigger
import com.geominder.reminder.domain.model.Reminder
import com.geominder.reminder.domain.model.TimeTrigger
import com.geominder.reminder.domain.model.ReminderId
import com.geominder.reminder.domain.model.ReminderStatus
import com.geominder.reminder.domain.repository.ReminderRepository
import com.geominder.reminder.geofence.CancellationHandle
import com.geominder.reminder.geofence.CurrentLocationProvider
import com.geominder.reminder.geofence.GeoInputError
import com.geominder.reminder.geofence.GeoInputField
import com.geominder.reminder.geofence.GeoLabelResolver
import com.geominder.reminder.geofence.LocationFailure
import com.geominder.reminder.geofence.LocationResult
import com.geominder.reminder.geofence.NumericGeoInputParser
import com.geominder.reminder.geofence.NumericGeoInputResult
import com.geominder.reminder.parser.DetectionEdit
import com.geominder.reminder.parser.GpsDetection
import com.geominder.reminder.parser.ParseContext
import com.geominder.reminder.parser.ReminderTextParser
import com.geominder.reminder.parser.TemporalRole
import com.geominder.reminder.parser.DateTimeDetection
import com.geominder.reminder.parser.TemporalPrecision
import com.geominder.reminder.parser.SourceSpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

class AddReminderViewModel(
    private val repository: ReminderRepository,
    private val parser: ReminderTextParser,
    private val defaultGeoRadiusProvider: DefaultGeoRadiusProvider,
    private val locationProvider: CurrentLocationProvider,
    private val geoLabelResolver: GeoLabelResolver,
    private val postSaveActions: ReminderPostSaveActions,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val localeProvider: () -> Locale = Locale::getDefault,
    private val injectedScope: CoroutineScope? = null,
    val editingReminderId: ReminderId? = null,
) : ViewModel() {
    private val defaultRadiusMeters = defaultGeoRadiusProvider
        .getDefaultRadiusMeters()
        .also { require(it.isFinite() && it > 0.0) { "Default radius must be positive" } }
    private val _uiState = MutableStateFlow(
        AddReminderUiState(radiusText = defaultRadiusText()),
    )
    val uiState: StateFlow<AddReminderUiState> = _uiState.asStateFlow()

    private var locationRequest: CancellationHandle? = null
    private var locationRequestVersion = 0L
    private var originalReminder: Reminder? = null

    init {
        editingReminderId?.let { id ->
            workScope().launch {
                repository.get(id)?.let { reminder ->
                    originalReminder = reminder
                    prefill(reminder)
                } ?: _uiState.update { it.copy(editingReminderId = id, expanded = true, saveError = "That reminder could not be found") }
            }
        }
    }

    private fun prefill(reminder: Reminder) {
        var parsed = parser.parse(reminder.sourceText, parseContext())
        reminder.timeTrigger?.let { trigger ->
            val local = trigger.exactAt.atZone(clock.zone)
            parsed = parsed.dateTime?.let {
                val edited = parsed.applyEdit(DetectionEdit.DateTime(it.id, local.toLocalDate(), local.toLocalTime()))
                edited.copy(detections = edited.detections.map { detection ->
                    if (detection.id == it.id && detection is DateTimeDetection) {
                        detection.copy(role = TemporalRole.REMINDER_TRIGGER)
                    } else detection
                })
            }
                ?: parsed.copy(detections = parsed.detections + DateTimeDetection(
                    id = "existing-time",
                    span = SourceSpan(0, 0),
                    sourceLabel = "",
                    displayLabel = formatDateTime(local.toLocalDateTime()),
                    confidence = 1.0, date = local.toLocalDate(), time = local.toLocalTime(), instant = trigger.exactAt,
                    zoneId = clock.zone, precision = TemporalPrecision.DATE_TIME,
                ))
        }
        val geo = reminder.geoTrigger
        _uiState.update {
            it.copy(
                editingReminderId = reminder.id, sourceText = reminder.sourceText, parseResult = parsed, expanded = true,
                geoEditorVisible = geo != null, latitudeText = geo?.latitude?.toPlainString() ?: "",
                longitudeText = geo?.longitude?.toPlainString() ?: "", radiusText = geo?.radiusMeters?.toPlainString() ?: defaultRadiusText(),
                geoLabel = geo?.label, activeFromEnabled = geo?.activeFrom != null,
                activeFromDateText = geo?.activeFrom?.atZone(clock.zone)?.toLocalDate()?.let(::formatDate) ?: "",
                activeFromTimeText = geo?.activeFrom?.atZone(clock.zone)?.toLocalTime()?.let(::formatTime) ?: "",
            )
        }
    }

    fun onExpandedChange(expanded: Boolean) {
        _uiState.update { it.copy(expanded = expanded) }
    }

    fun onSourceTextChange(sourceText: String) {
        val parsed = parser.parse(sourceText, parseContext())
        val gps = parsed.gps
        val parsedActiveFrom = parsed.dateTime?.takeIf { it.role == TemporalRole.GEO_ACTIVE_FROM }
        val previousGps = _uiState.value.parseResult?.gps
        val gpsChanged = gps != null &&
            (gps.latitude != previousGps?.latitude || gps.longitude != previousGps.longitude)

        _uiState.update { state ->
            state.copy(
                sourceText = sourceText,
                parseResult = parsed,
                expanded = state.expanded || sourceText.isNotBlank(),
                editingDateTimeDetectionId = null,
                dateTimeEditError = null,
                geoEditorVisible = state.geoEditorVisible || gps != null,
                latitudeText = gps?.latitude?.toPlainString() ?: state.latitudeText,
                longitudeText = gps?.longitude?.toPlainString() ?: state.longitudeText,
                geoLabel = if (gpsChanged) null else state.geoLabel,
                geoInputErrors = emptyMap(),
                saveError = null,
                savedReminder = null,
                activeFromEnabled = parsedActiveFrom != null || state.activeFromEnabled,
                activeFromDateText = parsedActiveFrom?.date
                    ?.let(::formatDate)
                    ?: state.activeFromDateText,
                activeFromTimeText = parsedActiveFrom?.time
                    ?.let(::formatTime)
                    ?: state.activeFromTimeText,
                activeFromError = null,
            )
        }
        if (gpsChanged) resolveGeoLabel(gps.latitude, gps.longitude)
    }

    fun beginDateTimeEdit() {
        val detection = _uiState.value.parseResult?.dateTime ?: return
        _uiState.update {
            it.copy(
                expanded = true,
                editingDateTimeDetectionId = detection.id,
                dateEditText = formatDate(detection.date),
                timeEditText = formatTime(detection.time),
                dateTimeEditError = null,
            )
        }
    }

    fun onDateEditChange(value: String) {
        _uiState.update { it.copy(dateEditText = value, dateTimeEditError = null) }
    }

    fun onTimeEditChange(value: String) {
        _uiState.update { it.copy(timeEditText = value, dateTimeEditError = null) }
    }

    fun commitDateTimeEdit() {
        val state = _uiState.value
        val id = state.editingDateTimeDetectionId ?: return
        val result = state.parseResult ?: return
        val date = parseDate(state.dateEditText)
        val time = parseTime(state.timeEditText)
        if (date == null || time == null) {
            _uiState.update { it.copy(dateTimeEditError = "Enter a valid date and time") }
            return
        }

        val editedResult = result.applyEdit(DetectionEdit.DateTime(id, date, time))
        val editedDetection = editedResult.dateTime
        _uiState.update {
            it.copy(
                parseResult = editedResult,
                editingDateTimeDetectionId = null,
                dateTimeEditError = null,
                saveError = null,
                activeFromDateText = if (editedDetection?.role == TemporalRole.GEO_ACTIVE_FROM) {
                    formatDate(editedDetection.date)
                } else it.activeFromDateText,
                activeFromTimeText = if (editedDetection?.role == TemporalRole.GEO_ACTIVE_FROM) {
                    formatTime(editedDetection.time)
                } else it.activeFromTimeText,
            )
        }
    }

    fun cancelDateTimeEdit() {
        _uiState.update {
            it.copy(editingDateTimeDetectionId = null, dateTimeEditError = null)
        }
    }

    fun showGeoEditor() {
        _uiState.update {
            it.copy(
                expanded = true,
                geoEditorVisible = true,
                radiusText = it.radiusText.ifBlank(::defaultRadiusText),
                locationError = null,
            )
        }
    }

    fun hideGeoEditor() {
        locationRequest?.cancel()
        locationRequest = null
        locationRequestVersion++
        _uiState.update {
            it.copy(
                geoEditorVisible = false,
                parseResult = it.parseResult?.copy(
                    detections = it.parseResult.detections.filterNot { detection ->
                        detection is GpsDetection
                    },
                ),
                latitudeText = "",
                longitudeText = "",
                geoInputErrors = emptyMap(),
                geoLabel = null,
                activeFromEnabled = false,
                activeFromError = null,
                isLocating = false,
                locationError = null,
            )
        }
    }

    fun onLatitudeChange(value: String) = updateGeoText(latitude = value)

    fun onLongitudeChange(value: String) = updateGeoText(longitude = value)

    fun onRadiusChange(value: String) = updateGeoText(radius = value)

    private fun updateGeoText(
        latitude: String? = null,
        longitude: String? = null,
        radius: String? = null,
    ) {
        _uiState.update {
            it.copy(
                latitudeText = latitude ?: it.latitudeText,
                longitudeText = longitude ?: it.longitudeText,
                radiusText = radius ?: it.radiusText,
                geoInputErrors = emptyMap(),
                geoLabel = if (latitude != null || longitude != null) null else it.geoLabel,
                locationError = null,
                saveError = null,
            )
        }
        if (latitude != null || longitude != null) {
            applyValidGeoInput()
        }
    }

    fun commitGeoEdit() {
        when (val result = parseGeoInput(_uiState.value)) {
            is NumericGeoInputResult.Invalid -> {
                _uiState.update { it.copy(geoInputErrors = result.errors) }
            }

            is NumericGeoInputResult.Valid -> {
                applyValidGeoInput(result.value.latitude, result.value.longitude)
            }
        }
    }

    /** Applies complete coordinate edits while leaving incomplete text untouched and error-free. */
    private fun applyValidGeoInput() {
        val result = parseGeoInput(_uiState.value)
        if (result is NumericGeoInputResult.Valid) {
            applyValidGeoInput(result.value.latitude, result.value.longitude)
        }
    }

    private fun applyValidGeoInput(latitude: Double, longitude: Double) {
        val current = _uiState.value
        val gpsDetection = current.parseResult?.gps
        val editedParseResult = if (gpsDetection != null) {
            current.parseResult.applyEdit(
                DetectionEdit.Gps(
                    detectionId = gpsDetection.id,
                    latitude = latitude,
                    longitude = longitude,
                ),
            )
        } else {
            current.parseResult
        }
        _uiState.update {
            it.copy(
                parseResult = editedParseResult,
                geoInputErrors = emptyMap(),
                saveError = null,
            )
        }
        resolveGeoLabel(latitude, longitude)
    }

    fun onActiveFromEnabledChange(enabled: Boolean) {
        val localNow = LocalDateTime.ofInstant(clock.instant(), clock.zone)
        _uiState.update {
            it.copy(
                activeFromEnabled = enabled,
                activeFromDateText = if (enabled && it.activeFromDateText.isBlank()) {
                    formatDate(localNow.toLocalDate())
                } else {
                    it.activeFromDateText
                },
                activeFromTimeText = if (enabled && it.activeFromTimeText.isBlank()) {
                    formatTime(localNow.toLocalTime().withSecond(0).withNano(0))
                } else {
                    it.activeFromTimeText
                },
                activeFromError = null,
            )
        }
    }

    fun onActiveFromDateChange(value: String) {
        _uiState.update { it.copy(activeFromDateText = value, activeFromError = null) }
    }

    fun onActiveFromTimeChange(value: String) {
        _uiState.update { it.copy(activeFromTimeText = value, activeFromError = null) }
    }

    fun locate() {
        locationRequest?.cancel()
        val version = ++locationRequestVersion
        _uiState.update {
            it.copy(
                expanded = true,
                geoEditorVisible = true,
                isLocating = true,
                locationError = null,
                radiusText = it.radiusText.ifBlank(::defaultRadiusText),
            )
        }
        locationRequest = locationProvider.locate { result ->
            if (version != locationRequestVersion) return@locate
            locationRequest = null
            when (result) {
                is LocationResult.Available -> {
                    val fix = result.fix
                    _uiState.update {
                        it.copy(
                            latitudeText = fix.latitude.toPlainString(),
                            longitudeText = fix.longitude.toPlainString(),
                            geoInputErrors = emptyMap(),
                            geoLabel = null,
                            isLocating = false,
                            locationError = null,
                        )
                    }
                    commitGeoEdit()
                }

                is LocationResult.Unavailable -> {
                    _uiState.update {
                        it.copy(
                            isLocating = false,
                            locationError = result.reason.userMessage(),
                        )
                    }
                }
            }
        }
    }

    fun save() {
        if (_uiState.value.isSaving) return
        val built = buildReminder(_uiState.value)
        if (built is ReminderBuildResult.Invalid) {
            _uiState.update {
                it.copy(
                    geoInputErrors = built.geoErrors,
                    activeFromError = built.activeFromError,
                    saveError = built.message,
                )
            }
            return
        }
        var reminder = (built as ReminderBuildResult.Valid).reminder
        val old = originalReminder
        if (editingReminderId != null && old == null) {
            _uiState.update { it.copy(saveError = "That reminder could not be found") }
            return
        }
        if (old != null) {
            reminder = reminder.copy(
                id = old.id, createdAt = old.createdAt, updatedAt = clock.instant(), enabled = true,
                status = ReminderStatus.PENDING, lastTriggeredAt = null, snoozedUntil = null, dismissedAt = null,
                timeTrigger = reminder.timeTrigger?.copy(id = old.timeTrigger?.id ?: reminder.timeTrigger.id),
                geoTrigger = reminder.geoTrigger?.copy(id = old.geoTrigger?.id ?: reminder.geoTrigger.id),
            )
        }
        _uiState.update { it.copy(isSaving = true, saveError = null, savedReminder = null) }
        workScope().launch {
            runCatching {
                if (old != null) postSaveActions.cancelReminder(old)
                try {
                    repository.save(reminder)
                } catch (error: Throwable) {
                    if (old?.timeTrigger != null) {
                        runCatching { postSaveActions.scheduleTimeTrigger(old) }
                    }
                    if (old?.geoTrigger != null) runCatching { postSaveActions.registerGeoTrigger(old) }
                    throw error
                }
                val activationFailures = buildList {
                    if (reminder.timeTrigger != null) {
                        runCatching { postSaveActions.scheduleTimeTrigger(reminder) }
                            .exceptionOrNull()
                            ?.let(::add)
                    }
                    if (reminder.geoTrigger != null) {
                        runCatching { postSaveActions.registerGeoTrigger(reminder) }
                            .exceptionOrNull()
                            ?.let(::add)
                    }
                }
                if (activationFailures.isNotEmpty()) {
                    throw ReminderActivationException(activationFailures)
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(isSaving = false, savedReminder = reminder, saveError = null)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = error.message ?: "The reminder could not be saved",
                    )
                }
            }
        }
    }

    fun consumeSavedReminder() {
        _uiState.update { it.copy(savedReminder = null) }
    }

    override fun onCleared() {
        locationRequest?.cancel()
        locationRequest = null
        super.onCleared()
    }

    private fun buildReminder(state: AddReminderUiState): ReminderBuildResult {
        if (state.sourceText.isBlank()) {
            return ReminderBuildResult.Invalid("Describe what you want to remember")
        }

        val dateTimeDetection = state.parseResult?.dateTime
        val timeTrigger = dateTimeDetection
            ?.takeIf { it.role == TemporalRole.REMINDER_TRIGGER }
            ?.let { TimeTrigger(exactAt = it.instant) }
        val geoInputResult = if (state.geoEditorVisible) parseGeoInput(state) else null
        val geoInput = (geoInputResult as? NumericGeoInputResult.Valid)?.value
        val geoErrors = (geoInputResult as? NumericGeoInputResult.Invalid)?.errors.orEmpty()
        val activeFrom = if (state.geoEditorVisible) {
            if (state.activeFromEnabled) {
                parseActiveFrom(state.activeFromDateText, state.activeFromTimeText)
                    ?: return ReminderBuildResult.Invalid(
                        message = "Check the active-from date and time",
                        geoErrors = geoErrors,
                        activeFromError = "Enter a valid active date and time",
                    )
            } else {
                dateTimeDetection
                    ?.takeIf { it.role == TemporalRole.GEO_ACTIVE_FROM }
                    ?.instant
            }
        } else {
            null
        }
        val geoTrigger = geoInput?.let {
            GeoTrigger(
                latitude = it.latitude,
                longitude = it.longitude,
                radiusMeters = it.radiusMeters,
                label = state.geoLabel ?: fallbackGeoLabel(it.latitude, it.longitude),
                activeFrom = activeFrom,
            )
        }

        if (state.geoEditorVisible && geoInput == null) {
            return ReminderBuildResult.Invalid(
                message = "Check the location details",
                geoErrors = geoErrors,
            )
        }
        if (timeTrigger == null && geoTrigger == null) {
            return ReminderBuildResult.Invalid("Add at least one time or location trigger")
        }

        val now = clock.instant()
        val trimmedText = state.sourceText.trim()
        return ReminderBuildResult.Valid(
            Reminder(
                sourceText = state.sourceText,
                title = trimmedText,
                text = trimmedText,
                timeTrigger = timeTrigger,
                geoTrigger = geoTrigger,
                createdAt = now,
            ),
        )
    }

    private fun parseGeoInput(state: AddReminderUiState): NumericGeoInputResult =
        NumericGeoInputParser(validDefaultRadius()).parse(
            latitudeText = state.latitudeText,
            longitudeText = state.longitudeText,
            radiusText = state.radiusText,
        )

    private fun resolveGeoLabel(latitude: Double, longitude: Double) {
        _uiState.update { it.copy(geoLabel = fallbackGeoLabel(latitude, longitude)) }
        geoLabelResolver.resolve(latitude, longitude) { label ->
            val state = _uiState.value
            val currentLatitude = state.latitudeText.trim().toDoubleOrNull()
            val currentLongitude = state.longitudeText.trim().toDoubleOrNull()
            if (currentLatitude == latitude && currentLongitude == longitude) {
                val resolved = label.takeIf(String::isNotBlank)?.asNearLabel()
                    ?: fallbackGeoLabel(latitude, longitude)
                _uiState.update { it.copy(geoLabel = resolved) }
            }
        }
    }

    private fun fallbackGeoLabel(latitude: Double, longitude: Double): String =
        "near ${latitude.toPlainString()}, ${longitude.toPlainString()}"

    private fun String.asNearLabel(): String = trim().let { label ->
        if (label.startsWith("near ", ignoreCase = true)) label else "near $label"
    }

    private fun parseActiveFrom(dateText: String, timeText: String): Instant? {
        val date = parseDate(dateText) ?: return null
        val time = parseTime(timeText) ?: return null
        return try {
            LocalDateTime.of(date, time).atZone(clock.zone).toInstant()
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun parseDate(value: String): LocalDate? = try {
        val input = value.trim()
        runCatching { LocalDate.parse(input, dateFormatter()) }.getOrElse {
            LocalDate.parse(input, DateTimeFormatter.ISO_LOCAL_DATE)
        }
    } catch (_: DateTimeParseException) {
        null
    }

    private fun parseTime(value: String): LocalTime? = try {
        val input = value.trim()
        runCatching { LocalTime.parse(input, timeFormatter()) }.getOrElse {
            LocalTime.parse(input, FALLBACK_TIME_FORMATTER)
        }
    } catch (_: DateTimeParseException) {
        null
    }

    private fun parseContext(): ParseContext = ParseContext(
        now = clock.instant(),
        zoneId = clock.zone,
        locale = localeProvider(),
    )

    private fun defaultRadiusText(): String = defaultRadiusMeters.toPlainString()

    private fun formatDate(date: LocalDate): String = date.format(dateFormatter())

    private fun formatTime(time: LocalTime): String = time.format(timeFormatter())

    private fun formatDateTime(dateTime: LocalDateTime): String =
        dateTime.format(dateTimeFormatter())

    private fun dateFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(localeProvider())

    private fun timeFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(localeProvider())

    private fun dateTimeFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(localeProvider())

    private fun validDefaultRadius(): Double = defaultRadiusMeters

    private fun workScope(): CoroutineScope = injectedScope ?: viewModelScope

    private fun Double.toPlainString(): String = when {
        this == toLong().toDouble() -> toLong().toString()
        else -> toString()
    }

    private fun LocationFailure.userMessage(): String = when (this) {
        LocationFailure.PERMISSION_REQUIRED -> "Location permission is required"
        LocationFailure.BACKGROUND_PERMISSION_REQUIRED -> "Background location permission is required"
        LocationFailure.LOCATION_DISABLED -> "Turn on location services to locate this device"
        LocationFailure.PLAY_SERVICES_UNAVAILABLE -> "Google Play services is unavailable"
        LocationFailure.NO_LOCATION -> "No current location is available"
        LocationFailure.REQUEST_FAILED -> "The location request failed"
    }

    private sealed interface ReminderBuildResult {
        data class Valid(val reminder: Reminder) : ReminderBuildResult

        data class Invalid(
            val message: String,
            val geoErrors: Map<GeoInputField, GeoInputError> = emptyMap(),
            val activeFromError: String? = null,
        ) : ReminderBuildResult
    }

    private class ReminderActivationException(causes: List<Throwable>) :
        IllegalStateException(
            "The reminder was saved, but one or more triggers could not be activated",
            causes.first(),
        )

    private companion object {
        val FALLBACK_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    }
}

class AddReminderViewModelFactory(
    private val repository: ReminderRepository,
    private val parser: ReminderTextParser,
    private val defaultGeoRadiusProvider: DefaultGeoRadiusProvider,
    private val locationProvider: CurrentLocationProvider,
    private val geoLabelResolver: GeoLabelResolver,
    private val postSaveActions: ReminderPostSaveActions,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val editingReminderId: ReminderId? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AddReminderViewModel::class.java)) {
            "Unsupported ViewModel class: ${modelClass.name}"
        }
        return AddReminderViewModel(
            repository = repository,
            parser = parser,
            defaultGeoRadiusProvider = defaultGeoRadiusProvider,
            locationProvider = locationProvider,
            geoLabelResolver = geoLabelResolver,
            postSaveActions = postSaveActions,
            clock = clock,
            editingReminderId = editingReminderId,
        ) as T
    }
}
