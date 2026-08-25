package com.geominder.reminder.domain.model

import java.util.UUID

/** Stable identifier persisted across scheduling, export, and process restarts. */
@JvmInline
value class ReminderId(val value: String) {
    init {
        require(value.isNotBlank()) { "Reminder ID must not be blank" }
    }

    companion object {
        fun create(): ReminderId = ReminderId(UUID.randomUUID().toString())
    }
}

/** Stable identifier for an individual alarm or geofence registration. */
@JvmInline
value class TriggerId(val value: String) {
    init {
        require(value.isNotBlank()) { "Trigger ID must not be blank" }
    }

    companion object {
        fun create(): TriggerId = TriggerId(UUID.randomUUID().toString())
    }
}
