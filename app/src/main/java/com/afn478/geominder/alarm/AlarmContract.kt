package com.afn478.geominder.alarm

import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.TriggerId

/** Stable wire contract shared by alarm scheduling and manifest receivers. */
object AlarmContract {
    const val ACTION_TIME_REMINDER = "com.afn478.geominder.action.TIME_REMINDER"
    const val ACTION_GATED_CHECK = "com.afn478.geominder.action.GATED_CHECK"
    const val ACTION_SNOOZE = "com.afn478.geominder.action.SNOOZE"

    const val EXTRA_REMINDER_ID = "com.afn478.geominder.extra.REMINDER_ID"
    const val EXTRA_TRIGGER_ID = "com.afn478.geominder.extra.TRIGGER_ID"
    const val EXTRA_DELIVERY_MODE = "com.afn478.geominder.extra.DELIVERY_MODE"

    const val URI_SCHEME = "geominder"
    const val URI_AUTHORITY = "alarm"
}

/**
 * Delivery constraint carried with every alarm event. [FULL_SCREEN_ELIGIBLE] is deliberately not
 * an instruction to launch: alert routing must still check the lock state and current full-screen
 * intent access when the alarm fires.
 */
enum class AlarmDeliveryMode(
    val wireValue: String,
) {
    PLAIN_NOTIFICATION("plain-notification"),
    FULL_SCREEN_ELIGIBLE("full-screen-eligible"),
    ;

    companion object {
        /** Missing or unknown values fail closed so old/tampered Intents cannot go full-screen. */
        fun fromWireValue(value: String?): AlarmDeliveryMode =
            entries.firstOrNull { it.wireValue == value } ?: PLAIN_NOTIFICATION
    }
}

enum class AlarmKind(
    val action: String,
    val uriPath: String,
) {
    TIME(AlarmContract.ACTION_TIME_REMINDER, "time"),
    GATED_CHECK(AlarmContract.ACTION_GATED_CHECK, "gated-check"),
    SNOOZE(AlarmContract.ACTION_SNOOZE, "snooze"),
}

/**
 * Android matches PendingIntents using action, component, data, and request code rather than
 * extras. Every field that identifies an alarm is therefore represented in [stableKey].
 */
data class AlarmIdentity(
    val kind: AlarmKind,
    val reminderId: ReminderId,
    val triggerId: TriggerId? = null,
) {
    val stableKey: String
        get() = buildString {
            append(kind.uriPath)
            append('|')
            append(reminderId.value)
            append('|')
            append(triggerId?.value.orEmpty())
        }

    val requestCode: Int
        get() = stableKey.hashCode() and Int.MAX_VALUE
}
