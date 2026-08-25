package com.geominder.reminder.ui.settings

import com.geominder.reminder.alarm.ExactAlarmPermissionState
import com.geominder.reminder.alarm.FullScreenIntentPermissionState
import com.geominder.reminder.settings.ReminderSettings
import com.geominder.reminder.settings.RuntimePermissionState
import com.geominder.reminder.settings.SettingsPermissionSnapshot
import com.geominder.reminder.settings.SettingsRepository
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
            .first { it.title == "Precise location" }
        assertEquals("Allowed", fineItem.status)
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
    }
}
