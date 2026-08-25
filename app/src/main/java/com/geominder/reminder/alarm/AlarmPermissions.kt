package com.geominder.reminder.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

enum class ExactAlarmPermissionState {
    /** Android versions before API 31 do not have exact-alarm special access. */
    NOT_REQUIRED,

    GRANTED,

    /** API 31-32 normally grant access automatically, but the user may revoke it. */
    AUTO_GRANTED_ACCESS_REVOKED,

    /** API 33+ requires the user to explicitly grant special access. */
    USER_ACTION_REQUIRED,
    ;

    val canScheduleExactAlarms: Boolean
        get() = this == NOT_REQUIRED || this == GRANTED
}

enum class FullScreenIntentPermissionState {
    /** Before API 34, the manifest permission is the only application-side requirement. */
    MANIFEST_PERMISSION_ONLY,

    GRANTED,
    USER_ACTION_REQUIRED,
    ;

    val canUseFullScreenIntent: Boolean
        get() = this == MANIFEST_PERMISSION_ONLY || this == GRANTED
}

data class AlarmDeliveryCapabilities(
    val exactAlarm: ExactAlarmPermissionState,
    val fullScreenIntent: FullScreenIntentPermissionState,
) {
    val canScheduleExactAlarm: Boolean
        get() = exactAlarm.canScheduleExactAlarms

    val canDeliverFullScreenAlert: Boolean
        get() = canScheduleExactAlarm && fullScreenIntent.canUseFullScreenIntent

    /** Integration must use a normal notification whenever this is true. */
    val requiresPlainNotificationFallback: Boolean
        get() = !canDeliverFullScreenAlert
}

/** Pure SDK policy. Android service queries are supplied as booleans by the caller. */
object AlarmPermissionPolicy {
    fun exactAlarmState(
        sdkInt: Int,
        platformCanSchedule: Boolean,
    ): ExactAlarmPermissionState = when {
        sdkInt < Build.VERSION_CODES.S -> ExactAlarmPermissionState.NOT_REQUIRED
        platformCanSchedule -> ExactAlarmPermissionState.GRANTED
        sdkInt <= Build.VERSION_CODES.S_V2 ->
            ExactAlarmPermissionState.AUTO_GRANTED_ACCESS_REVOKED
        else -> ExactAlarmPermissionState.USER_ACTION_REQUIRED
    }

    fun fullScreenIntentState(
        sdkInt: Int,
        platformCanUse: Boolean,
    ): FullScreenIntentPermissionState = when {
        sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            FullScreenIntentPermissionState.MANIFEST_PERMISSION_ONLY
        platformCanUse -> FullScreenIntentPermissionState.GRANTED
        else -> FullScreenIntentPermissionState.USER_ACTION_REQUIRED
    }

    /** API 31-32 normally auto-grant access, so only API 33+ should proactively prompt. */
    fun shouldPromptForExactAlarmAccess(
        sdkInt: Int,
        state: ExactAlarmPermissionState,
    ): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU &&
        state == ExactAlarmPermissionState.USER_ACTION_REQUIRED

    fun shouldPromptForFullScreenIntentAccess(
        sdkInt: Int,
        state: FullScreenIntentPermissionState,
    ): Boolean = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        state == FullScreenIntentPermissionState.USER_ACTION_REQUIRED
}

interface AlarmPermissionStatusSource {
    val sdkInt: Int

    fun canScheduleExactAlarms(): Boolean

    fun canUseFullScreenIntent(): Boolean
}

class AndroidAlarmPermissionStatusSource(
    context: Context,
    override val sdkInt: Int = Build.VERSION.SDK_INT,
) : AlarmPermissionStatusSource {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    override fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager.canUseFullScreenIntent()
}

/** Real status checks plus public Settings redirects; this does not model a runtime permission. */
class AlarmPermissionController(
    private val context: Context,
    private val statusSource: AlarmPermissionStatusSource =
        AndroidAlarmPermissionStatusSource(context),
) {
    fun exactAlarmState(): ExactAlarmPermissionState =
        AlarmPermissionPolicy.exactAlarmState(
            sdkInt = statusSource.sdkInt,
            platformCanSchedule = statusSource.canScheduleExactAlarms(),
        )

    fun fullScreenIntentState(): FullScreenIntentPermissionState =
        AlarmPermissionPolicy.fullScreenIntentState(
            sdkInt = statusSource.sdkInt,
            platformCanUse = statusSource.canUseFullScreenIntent(),
        )

    fun capabilities(): AlarmDeliveryCapabilities = AlarmDeliveryCapabilities(
        exactAlarm = exactAlarmState(),
        fullScreenIntent = fullScreenIntentState(),
    )

    fun shouldPromptForExactAlarmAccess(): Boolean =
        AlarmPermissionPolicy.shouldPromptForExactAlarmAccess(
            sdkInt = statusSource.sdkInt,
            state = exactAlarmState(),
        )

    fun shouldPromptForFullScreenIntentAccess(): Boolean =
        AlarmPermissionPolicy.shouldPromptForFullScreenIntentAccess(
            sdkInt = statusSource.sdkInt,
            state = fullScreenIntentState(),
        )

    /**
     * Returns null below API 31 because there is no exact-alarm special-access flow. API 31-32
     * normally have automatic access; this redirect remains useful if that access was revoked.
     */
    fun exactAlarmSettingsIntent(): Intent? {
        if (statusSource.sdkInt < Build.VERSION_CODES.S) return null
        return packageSettingsIntent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
    }

    /** Returns null below API 34, where no full-screen special-access screen exists. */
    fun fullScreenIntentSettingsIntent(): Intent? {
        if (statusSource.sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return packageSettingsIntent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
    }

    private fun packageSettingsIntent(action: String): Intent = Intent(
        action,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
