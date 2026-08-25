package com.afn478.geominder.ui.add

import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.ReminderStatus
import com.afn478.geominder.domain.model.GeoTrigger
import com.afn478.geominder.domain.model.TimeTrigger
import com.afn478.geominder.domain.model.TriggerId
import com.afn478.geominder.domain.repository.ReminderRepository
import com.afn478.geominder.geofence.CancellationHandle
import com.afn478.geominder.geofence.CurrentLocationProvider
import com.afn478.geominder.geofence.GeoLabelResolver
import com.afn478.geominder.geofence.LocationFix
import com.afn478.geominder.geofence.LocationResult
import com.afn478.geominder.parser.ReminderTextParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class AddReminderViewModelTest {
    private val clock = Clock.fixed(
        Instant.parse("2026-08-24T10:15:00Z"),
        ZoneId.of("UTC"),
    )

    @Test
    fun `typing parses offline and an edited time is persisted with source text intact`() {
        val repository = FakeReminderRepository()
        val actions = RecordingPostSaveActions()
        val viewModel = viewModel(repository = repository, actions = actions)
        val source = "  Call home tomorrow at 8:00  "

        viewModel.onSourceTextChange(source)
        assertEquals(LocalDate.of(2026, 8, 25), viewModel.uiState.value.parseResult?.dateTime?.date)
        viewModel.beginDateTimeEdit()
        viewModel.onDateEditChange("2026-08-30")
        viewModel.onTimeEditChange("18:15")
        viewModel.commitDateTimeEdit()
        viewModel.save()

        val saved = repository.saved.single()
        assertEquals(source, saved.sourceText)
        assertEquals("Call home tomorrow at 8:00", saved.title)
        assertEquals(Instant.parse("2026-08-30T18:15:00Z"), saved.timeTrigger?.exactAt)
        assertEquals(listOf(saved), actions.scheduled)
        assertTrue(actions.registered.isEmpty())
        assertEquals(saved, viewModel.uiState.value.savedReminder)
    }

    @Test
    fun `numeric location uses settings radius label and active-from gate`() {
        val repository = FakeReminderRepository()
        val actions = RecordingPostSaveActions()
        val viewModel = viewModel(
            repository = repository,
            actions = actions,
            radiusMeters = 275.0,
            labelResolver = GeoLabelResolver { _, _, callback -> callback("near Penn Station") },
        )

        viewModel.onSourceTextChange("Pick up parcel")
        viewModel.showGeoEditor()
        assertEquals("275", viewModel.uiState.value.radiusText)
        viewModel.onLatitudeChange("40.7505")
        viewModel.onLongitudeChange("-73.9934")
        viewModel.commitGeoEdit()
        viewModel.onActiveFromEnabledChange(true)
        viewModel.onActiveFromDateChange("2026-08-25")
        viewModel.onActiveFromTimeChange("09:30")
        viewModel.save()

        val saved = repository.saved.single()
        val geo = requireNotNull(saved.geoTrigger)
        assertEquals(40.7505, geo.latitude, 0.0)
        assertEquals(-73.9934, geo.longitude, 0.0)
        assertEquals(275.0, geo.radiusMeters, 0.0)
        assertEquals("near Penn Station", geo.label)
        assertEquals(Instant.parse("2026-08-25T09:30:00Z"), geo.activeFrom)
        assertEquals(listOf(saved), actions.registered)
        assertTrue(actions.scheduled.isEmpty())
    }

    @Test
    fun `complete manual coordinate edits update GPS and resolve label automatically`() {
        val viewModel = viewModel(
            labelResolver = GeoLabelResolver { _, _, callback -> callback("near edited place") },
        )

        viewModel.onSourceTextChange("Meet at 40.7128, -74.0060")
        viewModel.onLatitudeChange("40.7484")
        viewModel.onLongitudeChange("-73.9857")

        val state = viewModel.uiState.value
        assertEquals(40.7484, state.parseResult?.gps?.latitude ?: 0.0, 0.0)
        assertEquals(-73.9857, state.parseResult?.gps?.longitude ?: 0.0, 0.0)
        assertEquals("near edited place", state.geoLabel)
        assertTrue(state.geoInputErrors.isEmpty())
    }

    @Test
    fun `incomplete manual coordinate typing stays editable without errors`() {
        val viewModel = viewModel()

        viewModel.onSourceTextChange("Meet at 40.7128, -74.0060")
        viewModel.onLatitudeChange("-")

        val state = viewModel.uiState.value
        assertEquals("-", state.latitudeText)
        assertTrue(state.geoInputErrors.isEmpty())
    }

    @Test
    fun `save requires source text and at least one valid trigger`() {
        val repository = FakeReminderRepository()
        val viewModel = viewModel(repository = repository)

        viewModel.save()
        assertEquals("Describe what you want to remember", viewModel.uiState.value.saveError)

        viewModel.onSourceTextChange("Buy milk")
        viewModel.save()
        assertEquals(
            "Add at least one time or location trigger",
            viewModel.uiState.value.saveError,
        )
        assertTrue(repository.saved.isEmpty())

        viewModel.showGeoEditor()
        viewModel.onLatitudeChange("95")
        viewModel.onLongitudeChange("8")
        viewModel.save()
        assertEquals("Check the location details", viewModel.uiState.value.saveError)
        assertFalse(viewModel.uiState.value.geoInputErrors.isEmpty())
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun `locate fills fields and resolves reverse label`() {
        val fix = LocationFix(
            latitude = 40.7484,
            longitude = -73.9857,
            accuracyMeters = 5f,
            measuredAt = clock.instant(),
        )
        val viewModel = viewModel(
            locationProvider = CurrentLocationProvider { callback ->
                callback(LocationResult.Available(fix))
                CancellationHandle {}
            },
            labelResolver = GeoLabelResolver { _, _, callback -> callback("near the Empire State Building") },
        )

        viewModel.locate()

        val state = viewModel.uiState.value
        assertEquals("40.7484", state.latitudeText)
        assertEquals("-73.9857", state.longitudeText)
        assertEquals("near the Empire State Building", state.geoLabel)
        assertFalse(state.isLocating)
        assertNull(state.locationError)
    }

    @Test
    fun `detected time and edited GPS invoke both integration callbacks`() {
        val repository = FakeReminderRepository()
        val actions = RecordingPostSaveActions()
        val viewModel = viewModel(repository = repository, actions = actions)

        viewModel.onSourceTextChange("Meet tomorrow at 8 near 40.7128, -74.0060")
        viewModel.onLatitudeChange("40.7484")
        viewModel.onLongitudeChange("-73.9857")
        viewModel.commitGeoEdit()
        assertEquals(40.7484, viewModel.uiState.value.parseResult?.gps?.latitude ?: 0.0, 0.0)
        viewModel.save()

        val saved = repository.saved.single()
        assertNotNull(saved.timeTrigger)
        assertEquals(40.7484, saved.geoTrigger?.latitude ?: 0.0, 0.0)
        assertEquals(listOf(saved), actions.scheduled)
        assertEquals(listOf(saved), actions.registered)
    }

    @Test
    fun `preset keyword before coordinates persists both triggers without geo active-from`() {
        val repository = FakeReminderRepository()
        val actions = RecordingPostSaveActions()
        val viewModel = viewModel(repository = repository, actions = actions)

        viewModel.onSourceTextChange("Tonight remind me at 40.7128, -74.0060")
        viewModel.save()

        val saved = repository.saved.single()
        assertNotNull(saved.timeTrigger)
        assertNotNull(saved.geoTrigger)
        assertNull(saved.geoTrigger?.activeFrom)
        assertEquals(1, actions.scheduled.count { it == saved })
        assertEquals(1, actions.registered.count { it == saved })
    }

    @Test
    fun `time after coordinates becomes geo-only active-from gate`() {
        val repository = FakeReminderRepository()
        val actions = RecordingPostSaveActions()
        val viewModel = viewModel(repository = repository, actions = actions)

        viewModel.onSourceTextChange("Arrive at 40.7128, -74.0060 tonight")
        viewModel.save()

        val saved = repository.saved.single()
        assertNull(saved.timeTrigger)
        assertEquals(
            viewModel.uiState.value.parseResult?.dateTime?.instant,
            saved.geoTrigger?.activeFrom,
        )
        assertTrue(actions.scheduled.isEmpty())
        assertEquals(listOf(saved), actions.registered)
    }

    @Test
    fun `post-GPS preset automatically populates active-from editor`() {
        val viewModel = viewModel()

        viewModel.onSourceTextChange("Arrive at 40.7128, -74.0060 tonight")

        val state = viewModel.uiState.value
        assertTrue(state.activeFromEnabled)
        assertEquals(
            LocalDate.of(2026, 8, 24).format(usShortDateFormatter()),
            state.activeFromDateText,
        )
        assertEquals(
            LocalTime.of(20, 0).format(usShortTimeFormatter()),
            state.activeFromTimeText,
        )
    }

    @Test
    fun `editing geo active-from chip changes gate and stays geo-only`() {
        val repository = FakeReminderRepository()
        val actions = RecordingPostSaveActions()
        val viewModel = viewModel(repository = repository, actions = actions)

        viewModel.onSourceTextChange("Arrive at 40.7128, -74.0060 tonight")
        viewModel.beginDateTimeEdit()
        viewModel.onDateEditChange("2026-08-30")
        viewModel.onTimeEditChange("18:15")
        viewModel.commitDateTimeEdit()
        viewModel.save()

        val saved = repository.saved.single()
        assertNull(saved.timeTrigger)
        assertEquals(Instant.parse("2026-08-30T18:15:00Z"), saved.geoTrigger?.activeFrom)
        assertTrue(actions.scheduled.isEmpty())
        assertEquals(listOf(saved), actions.registered)
    }

    @Test
    fun `invalid chip edit stays open and does not replace detected time`() {
        val viewModel = viewModel()
        viewModel.onSourceTextChange("Tomorrow at 8")
        val original = viewModel.uiState.value.parseResult?.dateTime
        assertNotNull(original)

        viewModel.beginDateTimeEdit()
        viewModel.onDateEditChange("not-a-date")
        viewModel.commitDateTimeEdit()

        val state = viewModel.uiState.value
        assertNotNull(state.editingDateTimeDetectionId)
        assertNotNull(state.dateTimeEditError)
        assertEquals(original, state.parseResult?.dateTime)
    }

    @Test
    fun `editing preloads source text and exact trigger fields`() {
        val old = existingReminder()
        val repository = FakeReminderRepository(old)
        val viewModel = viewModel(repository = repository, editingReminderId = old.id)

        val state = viewModel.uiState.value
        assertEquals(old.sourceText, state.sourceText)
        assertEquals(old.timeTrigger?.exactAt, state.parseResult?.dateTime?.instant)
        assertEquals("40.7128", state.latitudeText)
        assertEquals("-74.0060", state.longitudeText)
        assertEquals("250", state.radiusText)
        assertEquals("near New York City Hall", state.geoLabel)
        assertTrue(state.activeFromEnabled)
        assertEquals(
            LocalDate.of(2026, 8, 25).format(usShortDateFormatter()),
            state.activeFromDateText,
        )
        assertEquals(
            LocalTime.of(9, 30).format(usShortTimeFormatter()),
            state.activeFromTimeText,
        )
    }

    @Test
    fun `saving edits preserves identity and trigger ids and orders replacement`() {
        val old = existingReminder()
        val events = mutableListOf<String>()
        val repository = FakeReminderRepository(old, eventLog = events)
        val actions = RecordingPostSaveActions(events)
        val viewModel = viewModel(repository = repository, actions = actions, editingReminderId = old.id)

        viewModel.beginDateTimeEdit()
        viewModel.onDateEditChange("2026-08-30")
        viewModel.onTimeEditChange("18:15")
        viewModel.commitDateTimeEdit()
        viewModel.onLatitudeChange("40.7580")
        viewModel.onLongitudeChange("-73.9855")
        viewModel.onRadiusChange("300")
        viewModel.commitGeoEdit()
        viewModel.save()

        val saved = repository.saved.single()
        assertEquals(old.id, saved.id)
        assertEquals(old.createdAt, saved.createdAt)
        assertEquals(clock.instant(), saved.updatedAt)
        assertEquals(old.timeTrigger?.id, saved.timeTrigger?.id)
        assertEquals(old.geoTrigger?.id, saved.geoTrigger?.id)
        assertTrue(saved.enabled)
        assertEquals(ReminderStatus.PENDING, saved.status)
        assertNull(saved.lastTriggeredAt)
        assertNull(saved.snoozedUntil)
        assertNull(saved.dismissedAt)
        assertEquals(listOf("cancel", "save", "schedule", "register"), actions.events)
    }

    @Test
    fun `failed edit save restores only old applicable registrations`() {
        val old = existingReminder().copy(timeTrigger = null)
        val events = mutableListOf<String>()
        val repository = FakeReminderRepository(old, failSaves = true, eventLog = events)
        val actions = RecordingPostSaveActions(events)
        val viewModel = viewModel(repository = repository, actions = actions, editingReminderId = old.id)

        viewModel.save()

        assertEquals("persistence failed", viewModel.uiState.value.saveError)
        assertEquals(listOf("cancel", "save", "register"), actions.events)
    }

    @Test
    fun `missing edit id reports not found and never creates`() {
        val missing = ReminderId("missing")
        val repository = FakeReminderRepository()
        val viewModel = viewModel(repository = repository, editingReminderId = missing)

        viewModel.onSourceTextChange("Buy milk tomorrow at 8")
        viewModel.save()

        assertEquals("That reminder could not be found", viewModel.uiState.value.saveError)
        assertTrue(repository.saved.isEmpty())
    }

    private fun usShortDateFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.US)

    private fun usShortTimeFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.US)

    private fun existingReminder(): Reminder {
        val created = Instant.parse("2026-08-20T10:00:00Z")
        return Reminder(
            id = ReminderId("existing"), sourceText = "Meet tomorrow at 8 near 40.7128, -74.0060",
            title = "Meet", text = "Meet", enabled = false, status = ReminderStatus.DISMISSED,
            timeTrigger = TimeTrigger(TriggerId("old-time"), Instant.parse("2026-08-25T08:00:00Z")),
            geoTrigger = GeoTrigger(TriggerId("old-geo"), 40.7128, -74.0060, 250.0, "near New York City Hall", Instant.parse("2026-08-25T09:30:00Z")),
            createdAt = created, updatedAt = Instant.parse("2026-08-23T10:00:00Z"),
            lastTriggeredAt = Instant.parse("2026-08-22T10:00:00Z"), snoozedUntil = null,
            dismissedAt = Instant.parse("2026-08-23T10:00:00Z"),
        )
    }

    private fun viewModel(
        repository: FakeReminderRepository = FakeReminderRepository(),
        actions: RecordingPostSaveActions = RecordingPostSaveActions(),
        radiusMeters: Double = 150.0,
        locationProvider: CurrentLocationProvider = CurrentLocationProvider {
            CancellationHandle {}
        },
        labelResolver: GeoLabelResolver = GeoLabelResolver { latitude, longitude, callback ->
            callback("near $latitude, $longitude")
        },
        editingReminderId: ReminderId? = null,
    ): AddReminderViewModel = AddReminderViewModel(
        repository = repository,
        parser = ReminderTextParser(),
        defaultGeoRadiusProvider = DefaultGeoRadiusProvider { radiusMeters },
        locationProvider = locationProvider,
        geoLabelResolver = labelResolver,
        postSaveActions = actions,
        clock = clock,
        localeProvider = { Locale.US },
        injectedScope = CoroutineScope(Dispatchers.Unconfined),
        editingReminderId = editingReminderId,
    )

    private class RecordingPostSaveActions(val events: MutableList<String> = mutableListOf()) : ReminderPostSaveActions {
        val scheduled = mutableListOf<Reminder>()
        val registered = mutableListOf<Reminder>()
        override suspend fun cancelReminder(reminder: Reminder) { events += "cancel" }

        override suspend fun scheduleTimeTrigger(reminder: Reminder) {
            events += "schedule"
            scheduled += reminder
        }

        override suspend fun registerGeoTrigger(reminder: Reminder) {
            events += "register"
            registered += reminder
        }
    }

    private class FakeReminderRepository(
        initial: Reminder? = null,
        private val failSaves: Boolean = false,
        private val eventLog: MutableList<String>? = null,
    ) : ReminderRepository {
        val saved = mutableListOf<Reminder>()
        private val values = MutableStateFlow<List<Reminder>>(emptyList())

        init { initial?.let { saved += it; values.value = saved.toList() } }

        override fun observeAll(): Flow<List<Reminder>> = values

        override fun observe(id: ReminderId): Flow<Reminder?> =
            MutableStateFlow(saved.firstOrNull { it.id == id })

        override suspend fun get(id: ReminderId): Reminder? = saved.firstOrNull { it.id == id }

        override suspend fun getPending(): List<Reminder> = saved.filter(Reminder::isPending)

        override suspend fun save(reminder: Reminder) {
            eventLog?.add("save")
            if (failSaves) throw IllegalStateException("persistence failed")
            saved.removeAll { it.id == reminder.id }
            saved += reminder
            values.value = saved.toList()
        }

        override suspend fun delete(id: ReminderId) {
            saved.removeAll { it.id == id }
            values.value = saved.toList()
        }

        override suspend fun setEnabled(id: ReminderId, enabled: Boolean, changedAt: Instant) {
            update(id) { it.copy(enabled = enabled, updatedAt = changedAt) }
        }

        override suspend fun recordTriggered(id: ReminderId, triggeredAt: Instant) {
            update(id) { it.copy(lastTriggeredAt = triggeredAt, updatedAt = triggeredAt) }
        }

        override suspend fun snooze(id: ReminderId, until: Instant, changedAt: Instant) = Unit

        override suspend fun dismiss(id: ReminderId, changedAt: Instant) = Unit

        override suspend fun complete(id: ReminderId, changedAt: Instant) = Unit

        private suspend fun update(id: ReminderId, block: (Reminder) -> Reminder) {
            val reminder = requireNotNull(get(id))
            save(block(reminder))
        }
    }
}
