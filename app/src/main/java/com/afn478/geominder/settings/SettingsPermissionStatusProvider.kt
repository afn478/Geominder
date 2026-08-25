package com.afn478.geominder.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.afn478.geominder.alarm.AlarmPermissionController
import com.afn478.geominder.geofence.LocationCapability
import com.afn478.geominder.geofence.LocationCapabilityChecker

fun interface SettingsPermissionStatusProvider {
    fun snapshot(): SettingsPermissionSnapshot
}

class AndroidSettingsPermissionStatusProvider(
    context: Context,
    private val alarmPermissionController: AlarmPermissionController =
        AlarmPermissionController(context.applicationContext),
    private val locationCapabilityChecker: LocationCapabilityChecker =
        LocationCapabilityChecker(context.applicationContext),
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : SettingsPermissionStatusProvider {
    private val applicationContext = context.applicationContext

    override fun snapshot(): SettingsPermissionSnapshot {
        val foregroundCapability = locationCapabilityChecker.check(requireBackgroundLocation = false)
        val fineLocationGranted =
            foregroundCapability !is LocationCapability.FineLocationPermissionRequired
        val backgroundLocationGranted = fineLocationGranted &&
            locationCapabilityChecker.check(requireBackgroundLocation = true) !is
            LocationCapability.BackgroundLocationPermissionRequired

        return SettingsPermissionSnapshot(
            sdkInt = sdkInt,
            exactAlarm = alarmPermissionController.exactAlarmState(),
            fullScreenIntent = alarmPermissionController.fullScreenIntentState(),
            fineLocationGranted = fineLocationGranted,
            backgroundLocationGranted = backgroundLocationGranted,
            notifications = notificationState(),
        )
    }

    private fun notificationState(): RuntimePermissionState = when {
        sdkInt < 33 -> RuntimePermissionState.NOT_REQUIRED
        ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED -> RuntimePermissionState.GRANTED
        else -> RuntimePermissionState.USER_ACTION_REQUIRED
    }
}
