package com.afn478.geominder

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afn478.geominder.localization.AppLanguagePreferences
import com.afn478.geominder.settings.SettingsPermissionAction
import com.afn478.geominder.ui.theme.ReminderTheme
import com.afn478.geominder.ui.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {
    private lateinit var fineLocationLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var backgroundLocationLauncher: ActivityResultLauncher<String>
    private lateinit var notificationLauncher: ActivityResultLauncher<String>
    private lateinit var settingsLauncher: ActivityResultLauncher<Intent>
    private var offeredExactAlarmAccess = false

    private val container: AppContainer
        get() = (application as ReminderApplication).appContainer

    private val permissionRequests by lazy {
        getSharedPreferences(PERMISSION_REQUESTS_PREFERENCES, Context.MODE_PRIVATE)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguagePreferences.localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        offeredExactAlarmAccess = savedInstanceState?.getBoolean(KEY_OFFERED_EXACT_ACCESS) == true
        registerPermissionLaunchers()
        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
            val dark = resolveDarkTheme(isSystemInDarkTheme(), settings.themeMode)
            SideEffect {
                val barStyle = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            ReminderTheme(themeMode = settings.themeMode, accentTheme = settings.accentTheme) {
                // Page transitions fade through an empty frame; paint the app's own background
                // there, and in the window behind it, rather than the system theme's.
                val background = MaterialTheme.colorScheme.background
                SideEffect { window.decorView.setBackgroundColor(background.toArgb()) }
                Surface(color = background) {
                    ReminderApp(
                        container = container,
                        onPermissionAction = ::handlePermissionAction,
                        onLanguageChanged = { recreate() },
                    )
                }
            }
        }
        // Recreation (rotation, language change) must not re-open permission prompts.
        if (savedInstanceState == null) requestInitialNotificationAccess()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (!hasPermission(permission) && !isPermanentlyDenied(permission)) {
                markRequested(permission)
                notificationLauncher.launch(permission)
                return
            }
        }
        offerExactAlarmAccessIfRequired()
    }

    private fun offerExactAlarmAccessIfRequired() {
        if (!offeredExactAlarmAccess && container.alarmPermissionController.shouldPromptForExactAlarmAccess()) {
            offeredExactAlarmAccess = true
            launchSettings(container.alarmPermissionController.exactAlarmSettingsIntent())
        }
    }

    private fun handlePermissionAction(action: SettingsPermissionAction) {
        when (action) {
            SettingsPermissionAction.REQUEST_FINE_LOCATION -> requestFineLocation()
            SettingsPermissionAction.REQUEST_BACKGROUND_LOCATION -> requestBackgroundLocation()
            SettingsPermissionAction.REQUEST_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestOrOpenSettings(Manifest.permission.POST_NOTIFICATIONS) {
                        notificationLauncher.launch(it)
                    }
                }
            }
            SettingsPermissionAction.OPEN_EXACT_ALARM_SETTINGS,
            SettingsPermissionAction.OPEN_FULL_SCREEN_INTENT_SETTINGS,
            SettingsPermissionAction.OPEN_APP_PERMISSION_SETTINGS,
            -> launchSettings(container.permissionIntentProvider.intentFor(action))
        }
    }

    private fun requestBackgroundLocation() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestFineLocation()
            return
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            requestOrOpenSettings(Manifest.permission.ACCESS_BACKGROUND_LOCATION) {
                backgroundLocationLauncher.launch(it)
            }
        } else {
            openAppPermissionSettings()
        }
    }

    private fun requestFineLocation() {
        requestOrOpenSettings(Manifest.permission.ACCESS_FINE_LOCATION) {
            fineLocationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }
    }

    /**
     * Once the user has chosen "don't ask again", the system silently rejects new requests. Send
     * them to the app's settings page instead so the button never appears to do nothing.
     */
    private inline fun requestOrOpenSettings(permission: String, request: (String) -> Unit) {
        if (isPermanentlyDenied(permission)) {
            openAppPermissionSettings()
        } else {
            markRequested(permission)
            request(permission)
        }
    }

    private fun openAppPermissionSettings() {
        launchSettings(
            container.permissionIntentProvider.intentFor(SettingsPermissionAction.OPEN_APP_PERMISSION_SETTINGS),
        )
    }

    /** Special-access screens are missing on some OEM builds; fall back to the app details page. */
    private fun launchSettings(intent: Intent?) {
        intent ?: return
        try {
            settingsLauncher.launch(intent)
        } catch (error: ActivityNotFoundException) {
            Log.w(TAG, "No activity for ${intent.action}", error)
            val fallback = container.permissionIntentProvider
                .intentFor(SettingsPermissionAction.OPEN_APP_PERMISSION_SETTINGS)
                ?.takeIf { it.action != intent.action }
                ?: return
            runCatching { settingsLauncher.launch(fallback) }
                .onFailure { Log.w(TAG, "No app settings activity", it) }
        }
    }

    private fun isPermanentlyDenied(permission: String): Boolean =
        !hasPermission(permission) &&
            permissionRequests.getBoolean(permission, false) &&
            !shouldShowRequestPermissionRationale(permission)

    private fun markRequested(permission: String) {
        permissionRequests.edit { putBoolean(permission, true) }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "GeominderMain"
        const val KEY_OFFERED_EXACT_ACCESS = "offered_exact_alarm_access"
        const val PERMISSION_REQUESTS_PREFERENCES = "permission_requests"
    }
}
