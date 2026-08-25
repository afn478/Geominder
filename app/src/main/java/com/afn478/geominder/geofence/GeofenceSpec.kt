package com.afn478.geominder.geofence

import com.afn478.geominder.domain.model.GeoTrigger
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.TriggerId
import java.time.Instant

/** Platform-independent registration data, suitable for persistence and testing. */
data class GeofenceSpec(
    val reminderId: ReminderId,
    val triggerId: TriggerId,
    val requestId: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val activeFrom: Instant?,
) {
    companion object {
        fun from(reminderId: ReminderId, trigger: GeoTrigger): GeofenceSpec = GeofenceSpec(
            reminderId = reminderId,
            triggerId = trigger.id,
            requestId = StableGeofenceId.from(trigger.id),
            latitude = trigger.latitude,
            longitude = trigger.longitude,
            radiusMeters = trigger.radiusMeters,
            activeFrom = trigger.activeFrom,
        )
    }
}
