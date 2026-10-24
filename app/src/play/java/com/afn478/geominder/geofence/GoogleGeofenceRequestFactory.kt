package com.afn478.geominder.geofence

import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest

object GoogleGeofenceRequestFactory {
    fun geofence(spec: GeofenceSpec): Geofence = Geofence.Builder()
        .setRequestId(spec.requestId)
        .setCircularRegion(
            spec.latitude,
            spec.longitude,
            spec.radiusMeters.toFloat(),
        )
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
        .build()

    fun request(spec: GeofenceSpec): GeofencingRequest = GeofencingRequest.Builder()
        .setInitialTrigger(if (spec.alertIfInside) GeofencingRequest.INITIAL_TRIGGER_ENTER else 0)
        .addGeofence(geofence(spec))
        .build()
}
