package com.geominder.reminder.geofence

import com.geominder.reminder.domain.model.TriggerId

/** Stable request IDs shared by registration and enter-event handling. */
object StableGeofenceId {
    private const val PREFIX = "geominder:geo:"

    fun from(triggerId: TriggerId): String = PREFIX + triggerId.value

    fun parse(requestId: String): TriggerId? {
        if (!requestId.startsWith(PREFIX)) return null
        val value = requestId.removePrefix(PREFIX)
        return value.takeIf(String::isNotBlank)?.let(::TriggerId)
    }
}
