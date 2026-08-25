package com.geominder.reminder.settings

import com.geominder.reminder.alarm.ExactAlarmPermissionState
import com.geominder.reminder.alarm.FullScreenIntentPermissionState

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
    val title: String,
    val status: String,
    val explanation: String,
    val action: SettingsPermissionAction? = null,
    val actionLabel: String? = null,
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
            title = "Exact alarms",
            status = "Not required",
            explanation = "This Android version allows exact reminder scheduling without special access.",
        )
        snapshot.exactAlarm.canScheduleExactAlarms -> PermissionUiItem(
            id = SettingsPermissionId.EXACT_ALARM,
            title = "Exact alarms",
            status = "Allowed",
            explanation = "Time reminders can be delivered at their scheduled time.",
        )
        snapshot.sdkInt <= 32 -> PermissionUiItem(
            id = SettingsPermissionId.EXACT_ALARM,
            title = "Exact alarms",
            status = "Access unavailable",
            explanation = "Android normally grants this access automatically. Review it in system settings.",
            action = SettingsPermissionAction.OPEN_EXACT_ALARM_SETTINGS,
            actionLabel = "Review access",
            shouldProactivelyRequest = false,
        )
        else -> PermissionUiItem(
            id = SettingsPermissionId.EXACT_ALARM,
            title = "Exact alarms",
            status = "Permission needed",
            explanation = "Allow exact alarms in system settings for on-time reminders.",
            action = SettingsPermissionAction.OPEN_EXACT_ALARM_SETTINGS,
            actionLabel = "Allow exact alarms",
            shouldProactivelyRequest = true,
        )
    }

    private fun fullScreenIntentItem(snapshot: SettingsPermissionSnapshot): PermissionUiItem =
        if (snapshot.fullScreenIntent.canUseFullScreenIntent) {
            PermissionUiItem(
                id = SettingsPermissionId.FULL_SCREEN_INTENT,
                title = "Full-screen alerts",
                status = "Allowed",
                explanation = "Locked-screen reminders may use the full-screen alert.",
            )
        } else {
            PermissionUiItem(
                id = SettingsPermissionId.FULL_SCREEN_INTENT,
                title = "Full-screen alerts",
                status = "Permission needed",
                explanation = "Without this access, reminders use a standard notification.",
                action = SettingsPermissionAction.OPEN_FULL_SCREEN_INTENT_SETTINGS,
                actionLabel = "Allow full-screen alerts",
                shouldProactivelyRequest = true,
            )
        }

    private fun fineLocationItem(snapshot: SettingsPermissionSnapshot): PermissionUiItem =
        if (snapshot.fineLocationGranted) {
            PermissionUiItem(
                id = SettingsPermissionId.FINE_LOCATION,
                title = "Precise location",
                status = "Allowed",
                explanation = "Location coordinates can be detected accurately.",
            )
        } else {
            PermissionUiItem(
                id = SettingsPermissionId.FINE_LOCATION,
                title = "Precise location",
                status = "Permission needed",
                explanation = "Precise location is needed to create reliable arrival reminders.",
                action = SettingsPermissionAction.REQUEST_FINE_LOCATION,
                actionLabel = "Allow location",
                shouldProactivelyRequest = true,
            )
        }

    private fun backgroundLocationItem(snapshot: SettingsPermissionSnapshot): PermissionUiItem = when {
        !snapshot.fineLocationGranted -> PermissionUiItem(
            id = SettingsPermissionId.BACKGROUND_LOCATION,
            title = "Background location",
            status = "Precise location needed first",
            explanation = "Allow precise location before enabling arrival reminders in the background.",
        )
        snapshot.backgroundLocationGranted -> PermissionUiItem(
            id = SettingsPermissionId.BACKGROUND_LOCATION,
            title = "Background location",
            status = "Allowed",
            explanation = "Arrival reminders can be detected while the app is closed.",
        )
        snapshot.sdkInt == 29 -> PermissionUiItem(
            id = SettingsPermissionId.BACKGROUND_LOCATION,
            title = "Background location",
            status = "Permission needed",
            explanation = "Allow background location for arrival reminders while the app is closed.",
            action = SettingsPermissionAction.REQUEST_BACKGROUND_LOCATION,
            actionLabel = "Allow background location",
            shouldProactivelyRequest = true,
        )
        else -> PermissionUiItem(
            id = SettingsPermissionId.BACKGROUND_LOCATION,
            title = "Background location",
            status = "Permission needed",
            explanation = "Choose ‘Allow all the time’ on the app permission screen.",
            action = SettingsPermissionAction.OPEN_APP_PERMISSION_SETTINGS,
            actionLabel = "Open permission settings",
            shouldProactivelyRequest = false,
        )
    }

    private fun notificationItem(snapshot: SettingsPermissionSnapshot): PermissionUiItem =
        if (snapshot.notifications == RuntimePermissionState.GRANTED) {
            PermissionUiItem(
                id = SettingsPermissionId.NOTIFICATIONS,
                title = "Notifications",
                status = "Allowed",
                explanation = "Reminder notifications can be shown.",
            )
        } else {
            PermissionUiItem(
                id = SettingsPermissionId.NOTIFICATIONS,
                title = "Notifications",
                status = "Permission needed",
                explanation = "Allow notifications so reminders can reach you.",
                action = SettingsPermissionAction.REQUEST_NOTIFICATIONS,
                actionLabel = "Allow notifications",
                shouldProactivelyRequest = true,
            )
        }
}
