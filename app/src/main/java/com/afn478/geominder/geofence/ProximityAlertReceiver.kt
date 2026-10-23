package com.afn478.geominder.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import java.time.Instant

/** Receives platform proximity alerts registered by [PlatformGeofenceRegistrar]. */
class ProximityAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PlatformGeofenceRegistrar.ACTION_PROXIMITY_ALERT) return
        if (!intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)) return
        val triggerId = intent.data?.lastPathSegment?.let(StableGeofenceId::parse) ?: return
        val entryPoint = GeoFeatureRuntime.entryPoint ?: return

        val pendingResult = goAsync()
        runCatching {
            entryPoint.onGeofenceEnter(setOf(triggerId), Instant.now(), pendingResult::finish)
        }.onFailure {
            pendingResult.finish()
        }
    }
}
