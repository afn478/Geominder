package com.afn478.geominder.domain.model

/**
 * Durable reminder lifecycle. [Reminder.enabled] is an independent user switch:
 * disabling a reminder pauses scheduling without discarding its lifecycle state.
 */
enum class ReminderStatus {
    /** Waiting for an initial time/geofence event. */
    PENDING,

    /** Waiting for a user-selected snooze instant. */
    SNOOZED,

    /** Explicitly dismissed by the user. Terminal until deliberately re-armed. */
    DISMISSED,

    /** Finished without dismissal, for example after a one-shot reminder is handled. */
    COMPLETED,
}
