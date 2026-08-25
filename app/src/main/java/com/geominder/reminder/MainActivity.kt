package com.geominder.reminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geominder.reminder.settings.SettingsPermissionAction
import com.geominder.reminder.settings.ThemeMode
import com.geominder.reminder.ui.theme.ReminderTheme

class MainActivity : ComponentActivity() {
    private lateinit var fineLocationLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var backgroundLocationLauncher: ActivityResultLauncher<String>
    private lateinit var notificationLauncher: ActivityResultLauncher<String>
    private lateinit var settingsLauncher: ActivityResultLauncher<Intent>
    private var offeredExactAlarmAccess = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        offeredExactAlarmAccess = savedInstanceState?.getBoolean(KEY_OFFERED_EXACT_ACCESS) == true
        registerPermissionLaunchers()
        setContent {
            val settings by (application as ReminderApplication).appContainer.settingsRepository.settings
                .collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK,
                ThemeMode.BLACK -> true
            }
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
            }
            ReminderTheme(themeMode = settings.themeMode, accentTheme = settings.accentTheme) {
                ReminderApp(
                    container = (application as ReminderApplication).appContainer,
                    onPermissionAction = ::handlePermissionAction,
                )
            }
        }
        requestInitialNotificationAccess()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_OFFERED_EXACT_ACCESS, offeredExactAlarmAccess)
        super.onSaveInstanceState(outState)
    }

    private fun registerPermissionLaunchers() {
        fineLocationLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { }
        backgroundLocationLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { }
        notificationLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { offerExactAlarmAccessIfRequired() }
        settingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { }
    }

    private fun requestInitialNotificationAccess() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            offerExactAlarmAccessIfRequired()
        }
    }

    private fun offerExactAlarmAccessIfRequired() {
        val container = (application as ReminderApplication).appContainer
        if (!offeredExactAlarmAccess && container.alarmPermissionController.shouldPromptForExactAlarmAccess()) {
            offeredExactAlarmAccess = true
            container.alarmPermissionController.exactAlarmSettingsIntent()?.let(settingsLauncher::launch)
        }
    }

    private fun handlePermissionAction(action: SettingsPermissionAction) {
        val container = (application as ReminderApplication).appContainer
        when (action) {
            SettingsPermissionAction.REQUEST_FINE_LOCATION ->
                requestFineLocation()
            SettingsPermissionAction.REQUEST_BACKGROUND_LOCATION -> requestBackgroundLocation()
            SettingsPermissionAction.REQUEST_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            SettingsPermissionAction.OPEN_EXACT_ALARM_SETTINGS,
            SettingsPermissionAction.OPEN_FULL_SCREEN_INTENT_SETTINGS,
            SettingsPermissionAction.OPEN_APP_PERMISSION_SETTINGS,
            -> container.permissionIntentProvider.intentFor(action)?.let(settingsLauncher::launch)
        }
    }

    private fun requestBackgroundLocation() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestFineLocation()
            return
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            val container = (application as ReminderApplication).appContainer
            container.permissionIntentProvider
                .intentFor(SettingsPermissionAction.OPEN_APP_PERMISSION_SETTINGS)
                ?.let(settingsLauncher::launch)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requestFineLocation() {
        fineLocationLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
        )
    }

    private companion object {
        const val KEY_OFFERED_EXACT_ACCESS = "offered_exact_alarm_access"
    }
}
