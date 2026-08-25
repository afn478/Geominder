package com.geominder.reminder.alert

import com.geominder.reminder.domain.model.ReminderId

object AlertContract {
    const val CHANNEL_ID = "reminder_alerts"
    const val CHANNEL_NAME = "Reminder alerts"

    const val ACTION_SNOOZE = "com.geominder.reminder.alert.action.SNOOZE"
    const val ACTION_DISMISS = "com.geominder.reminder.alert.action.DISMISS"
    const val ACTION_DONE = "com.geominder.reminder.alert.action.DONE"

    const val EXTRA_REMINDER_ID = "com.geominder.reminder.alert.extra.REMINDER_ID"
    const val EXTRA_TITLE = "com.geominder.reminder.alert.extra.TITLE"
    const val EXTRA_TEXT = "com.geominder.reminder.alert.extra.TEXT"
    const val EXTRA_SNOOZE_MILLIS = "com.geominder.reminder.alert.extra.SNOOZE_MILLIS"

    const val DEFAULT_SNOOZE_MILLIS = 10 * 60 * 1_000L
}

/** Deterministic identifiers keep notification and PendingIntent identity stable across restarts. */
object StableAlertId {
    fun notificationId(reminderId: ReminderId): Int = stablePositiveHash("notification:${reminderId.value}")

    fun fullScreenRequestCode(reminderId: ReminderId): Int =
        stablePositiveHash("full-screen:${reminderId.value}")

    fun contentRequestCode(reminderId: ReminderId): Int =
        stablePositiveHash("content:${reminderId.value}")

    fun actionRequestCode(reminderId: ReminderId, action: AlertAction): Int =
        stablePositiveHash("${action.intentAction}:${reminderId.value}")

    private fun stablePositiveHash(value: String): Int {
        var hash = FNV_OFFSET_BASIS
        value.forEach { character ->
            hash = hash xor character.code
            hash *= FNV_PRIME
        }
        return hash and Int.MAX_VALUE
    }

    private const val FNV_OFFSET_BASIS = -0x7ee3623b
    private const val FNV_PRIME = 0x01000193
}

enum class AlertAction(val intentAction: String) {
    SNOOZE(AlertContract.ACTION_SNOOZE),
    DISMISS(AlertContract.ACTION_DISMISS),
    DONE(AlertContract.ACTION_DONE),
    ;

    companion object {
        fun fromIntentAction(action: String?): AlertAction? = entries.firstOrNull {
            it.intentAction == action
        }
    }
}
