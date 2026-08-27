package com.afn478.geominder.ui.settings

import com.afn478.geominder.R
import com.afn478.geominder.alarm.ExactAlarmPermissionState
import com.afn478.geominder.alarm.FullScreenIntentPermissionState
import com.afn478.geominder.domain.model.PresetLocation
import com.afn478.geominder.geofence.CancellationHandle
import com.afn478.geominder.geofence.CurrentLocationProvider
import com.afn478.geominder.geofence.LocationFix
import com.afn478.geominder.geofence.LocationResult
import com.afn478.geominder.settings.ReminderSettings
import com.afn478.geominder.settings.RuntimePermissionState
import com.afn478.geominder.settings.SettingsPermissionSnapshot
import com.afn478.geominder.settings.SettingsRepository
import com.afn478.geominder.localization.resourceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.util.Locale

class SettingsViewModelTest {
    private lateinit var job: Job
    private lateinit var repository: FakeSettingsRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        job = Job()
        repository = FakeSettingsRepository()
        viewModel = SettingsViewModel(
            repository = repository,
            permissionStatusProvider = { permissionSnapshot(fineGranted = false) },
            injectedScope = CoroutineScope(Dispatchers.Unconfined + job),
        )
    }

    @After
    fun tearDown() {
        job.cancel()
    }

    @Test
    fun `invalid radius is not persisted and error is exposed`() {
        viewModel.onRadiusChange("0")
        viewModel.saveRadius()

        assertEquals(100.0, repository.settings.value.defaultGeofenceRadiusMeters, 0.0)
        assertNotNull(viewModel.uiState.value.radiusError)
    }

    @Test
    fun `valid radius is persisted and reflected in state`() {
        viewModel.onRadiusChange("275.5")
        viewModel.saveRadius()

        assertEquals(275.5, repository.settings.value.defaultGeofenceRadiusMeters, 0.0)
        assertEquals("275.5", viewModel.uiState.value.radiusText)
    }

    @Test
    fun `time expression text filtering setting is persisted and reflected in state`() {
        assertTrue(repository.settings.value.removeTimeExpressionsFromText)

        viewModel.onRemoveTimeExpressionsFromTextChange(false)

        assertFalse(repository.settings.value.removeTimeExpressionsFromText)
        assertFalse(viewModel.uiState.value.settings.removeTimeExpressionsFromText)
    }

    @Test
    fun `keyword can be added edited renamed and removed`() {
        viewModel.beginAddKeyword()
        viewModel.onKeywordChange("After Work")
        viewModel.onKeywordTimeChange("17:30")
        viewModel.saveKeyword()
        assertEquals(LocalTime.of(17, 30), repository.settings.value.keywordTimes["after work"])

        viewModel.beginEditKeyword("after work")
        viewModel.onKeywordChange("home time")
        viewModel.onKeywordTimeChange("18:15")
        viewModel.saveKeyword()
        assertFalse(repository.settings.value.keywordTimes.containsKey("after work"))
        assertEquals(LocalTime.of(18, 15), repository.settings.value.keywordTimes["home time"])

        viewModel.removeKeyword("home time")
        assertFalse(repository.settings.value.keywordTimes.containsKey("home time"))
    }

    @Test
    fun `keyword editor uses injected locale for display and save`() {
        val localViewModel = SettingsViewModel(
            repository = repository,
            permissionStatusProvider = { permissionSnapshot(fineGranted = false) },
            injectedScope = CoroutineScope(Dispatchers.Unconfined + job),
            localeProvider = { Locale.US },
        )

        localViewModel.beginAddKeyword()
        localViewModel.onKeywordChange("After Work")
        localViewModel.onKeywordTimeChange("5:30 PM")
        localViewModel.saveKeyword()

        assertEquals(LocalTime.of(17, 30), repository.settings.value.keywordTimes["after work"])
    }

    @Test
    fun `location preset can be added renamed and removed`() {
        viewModel.beginAddLocation()
        viewModel.onLocationKeywordChange("At Home")
        viewModel.onLocationLatitudeChange("40.7128")
        viewModel.onLocationLongitudeChange("-74.0060")
        viewModel.onLocationRadiusChange("275")
        viewModel.saveLocation()

        assertEquals(
            PresetLocation(40.7128, -74.0060, 275.0),
            repository.settings.value.keywordLocations["at home"],
        )

        viewModel.beginEditLocation("at home")
        viewModel.onLocationKeywordChange("Home")
        viewModel.onLocationRadiusChange("300")
        viewModel.saveLocation()

        assertTrue(repository.settings.value.keywordLocations["at home"] == null)
        assertEquals(
            300.0,
            requireNotNull(repository.settings.value.keywordLocations["home"]).radiusMeters,
            0.0,
        )

        viewModel.removeLocation("home")
        assertTrue(repository.settings.value.keywordLocations.isEmpty())
    }

    @Test
    fun `location preset current location button fills coordinates`() {
        val localViewModel = SettingsViewModel(
            repository = repository,
            permissionStatusProvider = { permissionSnapshot(fineGranted = true) },
            injectedScope = CoroutineScope(Dispatchers.Unconfined + job),
            locationProvider = CurrentLocationProvider { callback ->
                callback(
                    LocationResult.Available(
                        LocationFix(
                            latitude = 47.3769,
                            longitude = 8.5417,
                            accuracyMeters = 12f,
                            measuredAt = Instant.parse("2026-08-24T10:15:00Z"),
                        ),
                    ),
                )
                CancellationHandle {}
            },
        )

        localViewModel.beginAddLocation()
        localViewModel.locateLocation()

        assertEquals("47.3769", localViewModel.uiState.value.locationLatitudeText)
        assertEquals("8.5417", localViewModel.uiState.value.locationLongitudeText)
        assertFalse(localViewModel.uiState.value.isLocatingLocation)
    }

    @Test
    fun `reset restores parser defaults`() {
        repository.state.value = ReminderSettings(keywordTimes = emptyMap())

        viewModel.resetKeywordTimes()

        assertTrue(repository.settings.value.keywordTimes.containsKey("morning"))
        assertEquals(LocalTime.of(20, 0), repository.settings.value.keywordTimes["tonight"])
    }

    @Test
    fun `permission status refreshes from injected provider`() {
        var fineGranted = false
        val localViewModel = SettingsViewModel(
            repository = repository,
            permissionStatusProvider = { permissionSnapshot(fineGranted) },
            injectedScope = CoroutineScope(Dispatchers.Unconfined + job),
        )
        fineGranted = true

        localViewModel.refreshPermissionStatus()

        val fineItem = localViewModel.uiState.value.permissionItems
            .first { it.title.resourceId() == R.string.precise_location }
        assertEquals(R.string.permission_allowed, fineItem.status.resourceId())
    }

    private fun permissionSnapshot(fineGranted: Boolean) = SettingsPermissionSnapshot(
        sdkInt = 34,
        exactAlarm = ExactAlarmPermissionState.GRANTED,
        fullScreenIntent = FullScreenIntentPermissionState.GRANTED,
        fineLocationGranted = fineGranted,
        backgroundLocationGranted = false,
        notifications = RuntimePermissionState.GRANTED,
    )

    private class FakeSettingsRepository : SettingsRepository {
        val state = MutableStateFlow(ReminderSettings())
        override val settings: StateFlow<ReminderSettings> = state

        override suspend fun setDefaultRadiusMeters(radiusMeters: Double) {
            state.value = state.value.copy(defaultGeofenceRadiusMeters = radiusMeters)
        }

        override suspend fun upsertKeywordTime(keyword: String, time: LocalTime) {
            state.value = state.value.copy(keywordTimes = state.value.keywordTimes + (keyword to time))
        }

        override suspend fun removeKeyword(keyword: String) {
            state.value = state.value.copy(keywordTimes = state.value.keywordTimes - keyword)
        }

        override suspend fun resetKeywordTimes() {
            state.value = ReminderSettings(
                defaultGeofenceRadiusMeters = state.value.defaultGeofenceRadiusMeters,
            )
        }

        override suspend fun upsertKeywordLocation(keyword: String, location: PresetLocation) {
            state.value = state.value.copy(
                keywordLocations = state.value.keywordLocations + (keyword to location),
            )
        }

        override suspend fun removeKeywordLocation(keyword: String) {
            state.value = state.value.copy(
                keywordLocations = state.value.keywordLocations - keyword,
            )
        }

        override suspend fun resetKeywordLocations() {
            state.value = state.value.copy(keywordLocations = emptyMap())
        }

        override suspend fun setRemoveTimeExpressionsFromText(enabled: Boolean) {
            state.value = state.value.copy(removeTimeExpressionsFromText = enabled)
        }
    }
}
