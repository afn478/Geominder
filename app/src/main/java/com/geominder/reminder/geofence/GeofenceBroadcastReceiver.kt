package com.geominder.reminder.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import java.time.Instant

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val entryPoint = GeoFeatureRuntime.entryPoint ?: return
        if (event.hasError()) {
            entryPoint.onGeofencingError(event.errorCode)
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val triggerIds = event.triggeringGeofences.orEmpty()
            .mapNotNull { StableGeofenceId.parse(it.requestId) }
            .toSet()
        if (triggerIds.isEmpty()) return

        val pendingResult = goAsync()
        runCatching {
            entryPoint.onGeofenceEnter(triggerIds, Instant.now(), pendingResult::finish)
        }.onFailure {
            pendingResult.finish()
        }
    }
}
