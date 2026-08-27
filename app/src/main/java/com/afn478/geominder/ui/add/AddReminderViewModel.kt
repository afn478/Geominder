package com.afn478.geominder.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.afn478.geominder.R
import com.afn478.geominder.domain.model.GeoTrigger
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.ReminderStatus
import com.afn478.geominder.domain.model.ReminderTag
import com.afn478.geominder.domain.model.TimeTrigger
import com.afn478.geominder.domain.repository.ReminderRepository
import com.afn478.geominder.geofence.CancellationHandle
import com.afn478.geominder.geofence.ClipboardGeoInputParser
import com.afn478.geominder.geofence.CurrentLocationProvider
import com.afn478.geominder.geofence.GeoInputError
import com.afn478.geominder.geofence.GeoInputField
import com.afn478.geominder.geofence.GeoLabelResolver
import com.afn478.geominder.geofence.LocationFailure
import com.afn478.geominder.geofence.LocationResult
import com.afn478.geominder.geofence.NumericGeoInputParser
import com.afn478.geominder.geofence.NumericGeoInputResult
import com.afn478.geominder.localization.UiText
import com.afn478.geominder.localization.resource
import com.afn478.geominder.parser.DateTimeDetection
import com.afn478.geominder.parser.DetectionEdit
import com.afn478.geominder.parser.GpsDetection
import com.afn478.geominder.parser.ParseContext
import com.afn478.geominder.parser.ParseResult
import com.afn478.geominder.parser.ReminderTextParser
import com.afn478.geominder.parser.SourceSpan
import com.afn478.geominder.parser.TemporalPrecision
import com.afn478.geominder.parser.TemporalRole
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
    private val defaultReminderTitle: String = DEFAULT_REMINDER_TEXT,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val localeProvider: () -> Locale = Locale::getDefault,
    private val injectedScope: CoroutineScope? = null,
    val editingReminderId: ReminderId? = null,
) : ViewModel() {
    private val defaultRadiusMeters = defaultGeoRadiusProvider
        .getDefaultRadiusMeters()
        .also { require(it.isFinite() && it > 0.0) { "Default radius must be positive" } }
    private val _uiState = MutableStateFlow(
        defaultDateTime().let { defaultDateTime ->
            AddReminderUiState(
                radiusText = defaultRadiusText(),
                dateEditText = formatDate(defaultDateTime.toLocalDate()),
                timeEditText = formatTime(defaultDateTime.toLocalTime()),
            )
        },
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
                } ?: _uiState.update {
                    it.copy(
                        editingReminderId = id,
                        expanded = true,
                        saveError = UiText.resource(R.string.reminder_could_not_be_found),
                    )
                }
            }
        }
    }

    private fun prefill(reminder: Reminder) {
        val localTimeTrigger = reminder.timeTrigger?.exactAt?.atZone(clock.zone)
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
                tag = reminder.tag,
                dateEditText = localTimeTrigger?.toLocalDate()?.let(::formatDate) ?: it.dateEditText,
                timeEditText = localTimeTrigger?.toLocalTime()?.let(::formatTime) ?: it.timeEditText,
                dateTimeEditDirty = false,
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

    fun onTagClick(tag: ReminderTag) {
        _uiState.update { state ->
            state.copy(
                tag = if (state.tag == tag) null else tag,
                saveError = null,
                savedReminder = null,
            )
        }
    }

    fun onDetailsExpandedChange(expanded: Boolean) {
        if (!expanded) commitPendingDateTimeEdit()
        val wasExpanded = _uiState.value.detailsExpanded
        _uiState.update { state ->
            state.copy(
                detailsExpanded = expanded,
                editingDateTimeDetectionId = if (expanded) {
                    state.editingDateTimeDetectionId
                } else {
                    null
                },
                dateTimeEditError = if (expanded) state.dateTimeEditError else null,
            )
        }
        if (expanded && !wasExpanded && _uiState.value.parseResult?.dateTime != null) {
            beginDateTimeEdit()
        }
    }

    fun onSourceTextChange(sourceText: String) {
        val previousManualTime = _uiState.value.parseResult?.dateTime
            ?.takeIf { it.id == MANUAL_TIME_DETECTION_ID }
        val parsedSource = parser.parse(sourceText, parseContext())
        val parsed = if (parsedSource.dateTime == null && previousManualTime != null) {
            parsedSource.copy(detections = parsedSource.detections + previousManualTime)
        } else {
            parsedSource
        }
        val gps = parsed.gps
        val parsedActiveFrom = parsed.dateTime?.takeIf { it.role == TemporalRole.GEO_ACTIVE_FROM }
        val previousGps = _uiState.value.parseResult?.gps
        val gpsChanged = gps != null &&
            (gps.latitude != previousGps?.latitude || gps.longitude != previousGps.longitude)

        _uiState.update { state ->
            state.copy(
                sourceText = sourceText,
                parseResult = parsed,
                dateEditText = parsed.dateTime?.let { formatDate(it.date) } ?: state.dateEditText,
                timeEditText = parsed.dateTime?.let { formatTime(it.time) } ?: state.timeEditText,
                expanded = state.expanded || sourceText.isNotBlank(),
                editingDateTimeDetectionId = null,
                dateTimeEditDirty = false,
                timeTriggerCleared = if (parsed.dateTime != null) false else state.timeTriggerCleared,
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
        val detection = _uiState.value.parseResult?.dateTime
        if (detection == null) {
            addDefaultTimeTrigger()
            return
        }
        _uiState.update {
            it.copy(
                expanded = true,
                detailsExpanded = true,
                editingDateTimeDetectionId = detection.id,
                dateTimeEditDirty = false,
                dateEditText = formatDate(detection.date),
                timeEditText = formatTime(detection.time),
                dateTimeEditError = null,
            )
        }
    }

    fun onDateEditChange(value: String) {
        _uiState.update {
            it.copy(
                dateEditText = value,
                dateTimeEditDirty = true,
                dateTimeEditError = null,
            )
        }
    }

    fun onTimeEditChange(value: String) {
        _uiState.update {
            it.copy(
                timeEditText = value,
                dateTimeEditDirty = true,
                dateTimeEditError = null,
            )
        }
    }

    private fun commitPendingDateTimeEdit(): Boolean {
        if (!_uiState.value.dateTimeEditDirty) return true
        commitDateTimeEdit()
        return _uiState.value.dateTimeEditError == null
    }

    fun commitDateTimeEdit() {
        val state = _uiState.value
        val date = parseDate(state.dateEditText)
        val time = parseTime(state.timeEditText)
        if (date == null || time == null) {
            _uiState.update { it.copy(dateTimeEditError = UiText.resource(R.string.invalid_date_time)) }
            return
        }

        val id = state.editingDateTimeDetectionId ?: state.parseResult?.dateTime?.id
        if (id == null) {
            addManualTimeTrigger(date, time)
            return
        }
        val result = state.parseResult ?: return

        val editedResult = result.applyEdit(DetectionEdit.DateTime(id, date, time))
        val editedDetection = editedResult.dateTime
        _uiState.update {
            it.copy(
                parseResult = editedResult,
                editingDateTimeDetectionId = if (it.detailsExpanded) id else null,
                dateTimeEditDirty = false,
                timeTriggerCleared = false,
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

    fun clearTimeTrigger() {
        _uiState.update { state ->
            state.copy(
                parseResult = state.parseResult?.copy(
                    detections = state.parseResult.detections.filterNot { detection ->
                        detection is DateTimeDetection
                    },
                ),
                editingDateTimeDetectionId = null,
                dateTimeEditDirty = false,
                timeTriggerCleared = true,
                dateEditText = "",
                timeEditText = "",
                dateTimeEditError = null,
                saveError = null,
            )
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

    fun onGeoChipClick() {
        if (_uiState.value.geoEditorVisible) {
            onDetailsExpandedChange(true)
        } else {
            showGeoEditor()
            locate()
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
                expanded = true,
                geoEditorVisible = true,
                latitudeText = coordinates.latitude.toPlainString(),
                longitudeText = coordinates.longitude.toPlainString(),
                radiusText = it.radiusText.ifBlank(::defaultRadiusText),
                geoInputErrors = emptyMap(),
                geoLabel = null,
                locationError = null,
                saveError = null,
            )
        }
        applyValidGeoInput(coordinates.latitude, coordinates.longitude)
    }

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

    private fun addDefaultTimeTrigger() {
        val local = defaultDateTime()
        addManualTimeTrigger(local.toLocalDate(), local.toLocalTime())
    }

    private fun addManualTimeTrigger(date: LocalDate, time: LocalTime) {
        val context = parseContext()
        val local = LocalDateTime.of(date, time).atZone(context.zoneId)
        val detection = DateTimeDetection(
            id = MANUAL_TIME_DETECTION_ID,
            span = SourceSpan(0, 0),
            sourceLabel = "",
            displayLabel = formatDateTime(local.toLocalDateTime()),
            confidence = 1.0,
            date = local.toLocalDate(),
            time = local.toLocalTime(),
            instant = local.toInstant(),
            zoneId = context.zoneId,
            precision = TemporalPrecision.DATE_TIME,
        )
        _uiState.update { state ->
            val parsed = state.parseResult ?: ParseResult(
                sourceText = state.sourceText,
                context = context,
                detections = emptyList(),
            )
            val detectionsWithoutPreviousTime = parsed.detections.filterNot {
                it is DateTimeDetection && it.role == TemporalRole.REMINDER_TRIGGER
            }
            state.copy(
                expanded = true,
                parseResult = parsed.copy(detections = detectionsWithoutPreviousTime + detection),
                editingDateTimeDetectionId = if (state.detailsExpanded) detection.id else null,
                dateTimeEditDirty = false,
                timeTriggerCleared = false,
                dateEditText = formatDate(detection.date),
                timeEditText = formatTime(detection.time),
                dateTimeEditError = null,
                saveError = null,
            )
        }
    }

    private fun defaultDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(
            clock.instant().plusSeconds(DEFAULT_TRIGGER_DELAY_SECONDS),
            clock.zone,
        ).withSecond(0).withNano(0)

    fun save() {
        if (_uiState.value.isSaving) return
        if (!commitPendingDateTimeEdit()) return
        if (_uiState.value.parseResult?.dateTime == null &&
            !_uiState.value.geoEditorVisible &&
            !_uiState.value.timeTriggerCleared
        ) {
            addDefaultTimeTrigger()
        }
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
            _uiState.update {
                it.copy(saveError = UiText.resource(R.string.reminder_could_not_be_found))
            }
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
                        saveError = error.message?.let(UiText::Plain)
                            ?: UiText.resource(R.string.reminder_save_failed),
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
        val dateTimeDetection = state.parseResult?.dateTime
        val timeTrigger = dateTimeDetection
            ?.let { TimeTrigger(exactAt = it.instant) }
        val geoInputResult = if (state.geoEditorVisible) parseGeoInput(state) else null
        val geoInput = (geoInputResult as? NumericGeoInputResult.Valid)?.value
        val geoErrors = (geoInputResult as? NumericGeoInputResult.Invalid)?.errors.orEmpty()
        val activeFrom = if (state.geoEditorVisible) {
            if (state.activeFromEnabled) {
                parseActiveFrom(state.activeFromDateText, state.activeFromTimeText)
                    ?: return ReminderBuildResult.Invalid(
                        message = UiText.resource(R.string.check_active_from),
                        geoErrors = geoErrors,
                        activeFromError = UiText.resource(R.string.invalid_active_from),
                    )
            } else {
                null
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
                message = UiText.resource(R.string.check_location_details),
                geoErrors = geoErrors,
            )
        }
        if (timeTrigger == null && geoTrigger == null) {
            return ReminderBuildResult.Invalid(
                UiText.resource(R.string.add_time_or_location_trigger),
            )
        }

        val now = clock.instant()
        val trimmedText = state.sourceText.trim()
        val displayText = trimmedText.ifBlank { defaultReminderTitle }
        return ReminderBuildResult.Valid(
            Reminder(
                sourceText = state.sourceText,
                title = displayText,
                text = displayText,
                tag = state.tag,
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
                val resolved = label.takeIf(String::isNotBlank)
                    ?: fallbackGeoLabel(latitude, longitude)
                _uiState.update { it.copy(geoLabel = resolved) }
            }
        }
    }

    private fun fallbackGeoLabel(latitude: Double, longitude: Double): String =
        "${latitude.toPlainString()}, ${longitude.toPlainString()}"

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

    private sealed interface ReminderBuildResult {
        data class Valid(val reminder: Reminder) : ReminderBuildResult

        data class Invalid(
            val message: UiText,
            val geoErrors: Map<GeoInputField, GeoInputError> = emptyMap(),
            val activeFromError: UiText? = null,
        ) : ReminderBuildResult
    }

    private class ReminderActivationException(causes: List<Throwable>) :
        IllegalStateException(
            "The reminder was saved, but one or more triggers could not be activated",
            causes.first(),
        )

    private companion object {
        const val MANUAL_TIME_DETECTION_ID = "manual-time"
        const val DEFAULT_TRIGGER_DELAY_SECONDS = 60L * 60L
        const val DEFAULT_REMINDER_TEXT = "Reminder"
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
    private val defaultReminderTitle: String = "Reminder",
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
            defaultReminderTitle = defaultReminderTitle,
            clock = clock,
            editingReminderId = editingReminderId,
        ) as T
    }
}
