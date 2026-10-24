package com.afn478.geominder.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.afn478.geominder.alarm.AlarmPermissionController

/**
 * Resolves settings-only actions. Runtime permission actions intentionally return null so the
 * host Activity can dispatch them through Activity Result launchers.
 */
class SettingsPermissionIntentProvider(
    context: Context,
    private val alarmPermissionController: AlarmPermissionController =
        AlarmPermissionController(context.applicationContext),
) {
    private val applicationContext = context.applicationContext

    fun intentFor(action: SettingsPermissionAction): Intent? = when (action) {
        SettingsPermissionAction.OPEN_EXACT_ALARM_SETTINGS ->
            alarmPermissionController.exactAlarmSettingsIntent()
        SettingsPermissionAction.OPEN_FULL_SCREEN_INTENT_SETTINGS ->
            alarmPermissionController.fullScreenIntentSettingsIntent()
        SettingsPermissionAction.OPEN_APP_PERMISSION_SETTINGS -> Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${applicationContext.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // The app's own battery page (Optimized / Unrestricted). Not a public action, so the
        // host falls back to the app details page where a device lacks it.
        SettingsPermissionAction.OPEN_BATTERY_SETTINGS -> Intent(
            ACTION_APP_BATTERY_SETTINGS,
            Uri.parse("package:${applicationContext.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        SettingsPermissionAction.REQUEST_FINE_LOCATION,
        SettingsPermissionAction.REQUEST_BACKGROUND_LOCATION,
        SettingsPermissionAction.REQUEST_NOTIFICATIONS,
        -> null
    }

    private companion object {
        const val ACTION_APP_BATTERY_SETTINGS = "android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL"
    }
}
