package com.afn478.geominder.settings

import com.afn478.geominder.R
import com.afn478.geominder.alarm.ExactAlarmPermissionState
import com.afn478.geominder.alarm.FullScreenIntentPermissionState
import com.afn478.geominder.localization.UiText
import com.afn478.geominder.localization.resource

enum class RuntimePermissionState {
    NOT_REQUIRED,
    GRANTED,
    USER_ACTION_REQUIRED,
}

data class SettingsPermissionSnapshot(
    val sdkInt: Int,
    val exactAlarm: ExactAlarmPermissionState,
    val fullScreenIntent: FullScreenIntentPermissionState,
    val fineLocationGranted: Boolean,
    val backgroundLocationGranted: Boolean,
    val notifications: RuntimePermissionState,
)

enum class SettingsPermissionAction {
    OPEN_EXACT_ALARM_SETTINGS,
    OPEN_FULL_SCREEN_INTENT_SETTINGS,
    REQUEST_FINE_LOCATION,
    REQUEST_BACKGROUND_LOCATION,
    OPEN_APP_PERMISSION_SETTINGS,
    REQUEST_NOTIFICATIONS,
}

enum class SettingsPermissionId {
    EXACT_ALARM,
    FULL_SCREEN_INTENT,
    FINE_LOCATION,
    BACKGROUND_LOCATION,
    NOTIFICATIONS,
}

data class PermissionUiItem(
    val id: SettingsPermissionId,
    val title: UiText,
    val status: UiText,
    val explanation: UiText,
    val action: SettingsPermissionAction? = null,
    val actionLabel: UiText? = null,
    val shouldProactivelyRequest: Boolean = false,
)

/** Keeps version-specific permission behavior testable without an Android runtime. */
object SettingsPermissionPolicy {
    fun items(snapshot: SettingsPermissionSnapshot): List<PermissionUiItem> = buildList {
        add(exactAlarmItem(snapshot))
        if (snapshot.sdkInt >= 34) add(fullScreenIntentItem(snapshot))
        add(fineLocationItem(snapshot))
        add(backgroundLocationItem(snapshot))
        if (snapshot.sdkInt >= 33) add(notificationItem(snapshot))
    }

    private fun exactAlarmItem(snapshot: SettingsPermissionSnapshot): PermissionUiItem = when {
        snapshot.sdkInt < 31 -> PermissionUiItem(
            id = SettingsPermissionId.EXACT_ALARM,
            title = UiText.resource(R.string.exact_alarms),
            status = UiText.resource(R.string.permission_not_required),
            explanation = UiText.resource(R.string.exact_alarms_not_required_explanation),
        )
        snapshot.exactAlarm.canScheduleExactAlarms -> PermissionUiItem(
            id = SettingsPermissionId.EXACT_ALARM,
            title = UiText.resource(R.string.exact_alarms),
            status = UiText.resource(R.string.permission_allowed),
            explanation = UiText.resource(R.string.exact_alarms_allowed_explanation),
        )
        snapshot.sdkInt <= 32 -> PermissionUiItem(
            id = SettingsPermissionId.EXACT_ALARM,
            title = UiText.resource(R.string.exact_alarms),
            status = UiText.resource(R.string.access_unavailable),
            explanation = UiText.resource(R.string.exact_alarms_unavailable_explanation),
            action = SettingsPermissionAction.OPEN_EXACT_ALARM_SETTINGS,
            actionLabel = UiText.resource(R.string.review_access),
            shouldProactivelyRequest = false,
        )
        else -> PermissionUiItem(
            id = SettingsPermissionId.EXACT_ALARM,
            title = UiText.resource(R.string.exact_alarms),
            status = UiText.resource(R.string.permission_needed),
            explanation = UiText.resource(R.string.exact_alarms_needed_explanation),
            action = SettingsPermissionAction.OPEN_EXACT_ALARM_SETTINGS,
            actionLabel = UiText.resource(R.string.allow_exact_alarms),
            shouldProactivelyRequest = true,
        )
    }

    private fun fullScreenIntentItem(snapshot: SettingsPermissionSnapshot): PermissionUiItem =
        if (snapshot.fullScreenIntent.canUseFullScreenIntent) {
            PermissionUiItem(
                id = SettingsPermissionId.FULL_SCREEN_INTENT,
                title = UiText.resource(R.string.full_screen_alerts),
                status = UiText.resource(R.string.permission_allowed),
                explanation = UiText.resource(R.string.full_screen_alerts_allowed_explanation),
            )
        } else {
            PermissionUiItem(
                id = SettingsPermissionId.FULL_SCREEN_INTENT,
                title = UiText.resource(R.string.full_screen_alerts),
                status = UiText.resource(R.string.permission_needed),
                explanation = UiText.resource(R.string.full_screen_alerts_needed_explanation),
                action = SettingsPermissionAction.OPEN_FULL_SCREEN_INTENT_SETTINGS,
                actionLabel = UiText.resource(R.string.allow_full_screen_alerts),
                shouldProactivelyRequest = true,
            )
        }

    private fun fineLocationItem(snapshot: SettingsPermissionSnapshot): PermissionUiItem =
        if (snapshot.fineLocationGranted) {
            PermissionUiItem(
                id = SettingsPermissionId.FINE_LOCATION,
                title = UiText.resource(R.string.precise_location),
                status = UiText.resource(R.string.permission_allowed),
                explanation = UiText.resource(R.string.precise_location_allowed_explanation),
            )
        } else {
            PermissionUiItem(
                id = SettingsPermissionId.FINE_LOCATION,
                title = UiText.resource(R.string.precise_location),
                status = UiText.resource(R.string.permission_needed),
                explanation = UiText.resource(R.string.precise_location_needed_explanation),
                action = SettingsPermissionAction.REQUEST_FINE_LOCATION,
                actionLabel = UiText.resource(R.string.allow_location),
                shouldProactivelyRequest = true,
            )
        }

    private fun backgroundLocationItem(snapshot: SettingsPermissionSnapshot): PermissionUiItem = when {
        !snapshot.fineLocationGranted -> PermissionUiItem(
            id = SettingsPermissionId.BACKGROUND_LOCATION,
            title = UiText.resource(R.string.background_location),
            status = UiText.resource(R.string.precise_location_needed_first),
            explanation = UiText.resource(R.string.background_location_precise_first_explanation),
        )
        snapshot.backgroundLocationGranted -> PermissionUiItem(
            id = SettingsPermissionId.BACKGROUND_LOCATION,
            title = UiText.resource(R.string.background_location),
            status = UiText.resource(R.string.permission_allowed),
            explanation = UiText.resource(R.string.background_location_allowed_explanation),
        )
        snapshot.sdkInt == 29 -> PermissionUiItem(
            id = SettingsPermissionId.BACKGROUND_LOCATION,
            title = UiText.resource(R.string.background_location),
            status = UiText.resource(R.string.permission_needed),
            explanation = UiText.resource(R.string.background_location_needed_explanation),
            action = SettingsPermissionAction.REQUEST_BACKGROUND_LOCATION,
            actionLabel = UiText.resource(R.string.allow_background_location),
            shouldProactivelyRequest = true,
        )
        else -> PermissionUiItem(
            id = SettingsPermissionId.BACKGROUND_LOCATION,
            title = UiText.resource(R.string.background_location),
            status = UiText.resource(R.string.permission_needed),
            explanation = UiText.resource(R.string.background_location_settings_explanation),
            action = SettingsPermissionAction.OPEN_APP_PERMISSION_SETTINGS,
            actionLabel = UiText.resource(R.string.open_permission_settings),
            shouldProactivelyRequest = false,
        )
    }

    private fun notificationItem(snapshot: SettingsPermissionSnapshot): PermissionUiItem =
        if (snapshot.notifications == RuntimePermissionState.GRANTED) {
            PermissionUiItem(
                id = SettingsPermissionId.NOTIFICATIONS,
                title = UiText.resource(R.string.notifications),
                status = UiText.resource(R.string.permission_allowed),
                explanation = UiText.resource(R.string.notifications_allowed_explanation),
            )
        } else {
            PermissionUiItem(
                id = SettingsPermissionId.NOTIFICATIONS,
                title = UiText.resource(R.string.notifications),
                status = UiText.resource(R.string.permission_needed),
                explanation = UiText.resource(R.string.notifications_needed_explanation),
                action = SettingsPermissionAction.REQUEST_NOTIFICATIONS,
                actionLabel = UiText.resource(R.string.allow_notifications),
                shouldProactivelyRequest = true,
            )
        }
}
