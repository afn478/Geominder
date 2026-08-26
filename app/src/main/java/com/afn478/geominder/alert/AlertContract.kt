package com.afn478.geominder.alert

import com.afn478.geominder.domain.model.ReminderId

object AlertContract {
    const val CHANNEL_ID = "reminder_alerts"
    const val CHANNEL_NAME = "Reminder alerts"

    const val ACTION_SNOOZE = "com.afn478.geominder.alert.action.SNOOZE"
    const val ACTION_DISMISS = "com.afn478.geominder.alert.action.DISMISS"
    const val ACTION_DONE = "com.afn478.geominder.alert.action.DONE"

    const val EXTRA_REMINDER_ID = "com.afn478.geominder.alert.extra.REMINDER_ID"
    const val EXTRA_TITLE = "com.afn478.geominder.alert.extra.TITLE"
    const val EXTRA_TEXT = "com.afn478.geominder.alert.extra.TEXT"
    const val EXTRA_TIME_TEXT = "com.afn478.geominder.alert.extra.TIME_TEXT"
    const val EXTRA_LOCATION_TEXT = "com.afn478.geominder.alert.extra.LOCATION_TEXT"
    const val EXTRA_DEBUG_ALERT = "com.afn478.geominder.alert.extra.DEBUG_ALERT"
    const val EXTRA_SNOOZE_MILLIS = "com.afn478.geominder.alert.extra.SNOOZE_MILLIS"

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
