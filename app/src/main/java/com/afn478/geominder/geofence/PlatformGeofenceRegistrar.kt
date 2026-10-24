package com.afn478.geominder.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import com.afn478.geominder.domain.model.TriggerId

/**
 * Geofencing through Android's own proximity alerts, so location reminders work without Google
 * Play services. Like Play services geofences, alerts do not survive a reboot; boot resync
 * registers them again.
 */
class PlatformGeofenceRegistrar(
    context: Context,
    private val capabilityChecker: LocationCapabilityChecker = LocationCapabilityChecker(context),
) : GeofenceRegistrar {
    private val applicationContext = context.applicationContext
    private val locationManager = applicationContext.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission") // Guarded by LocationCapabilityChecker.
    override fun register(spec: GeofenceSpec, callback: GeofenceOperationCallback) {
        val capability = capabilityChecker.check(requireBackgroundLocation = true)
        if (capability != LocationCapability.Available) {
            callback.onComplete(GeofenceOperationResult.CapabilityUnavailable(capability))
            return
        }
        if (spec.radiusMeters > Float.MAX_VALUE) {
            callback.onComplete(GeofenceOperationResult.RadiusTooLarge)
            return
        }
        val manager = locationManager ?: run {
            callback.onComplete(GeofenceOperationResult.Failed(IllegalStateException("No LocationManager")))
            return
        }
        runCatching {
            val intent = pendingIntent(spec.requestId, PendingIntent.FLAG_UPDATE_CURRENT)
                ?: error("Proximity alert intent could not be created")
            // Re-registering replaces an older alert for the same trigger instead of adding a twin.
            runCatching { manager.removeProximityAlert(intent) }
            manager.addProximityAlert(
                spec.latitude,
                spec.longitude,
                spec.radiusMeters.toFloat(),
                NO_EXPIRATION,
                intent,
            )
        }.onSuccess {
            callback.onComplete(GeofenceOperationResult.Success)
        }.onFailure { error ->
            callback.onComplete(GeofenceOperationResult.Failed(error))
        }
    }

    /**
     * Proximity alerts live as long as their PendingIntent: both go together on a reboot or a
     * force stop. While the intent still exists the alert does too, and re-adding it would only
     * fire again for a device already inside.
     */
    override fun refresh(spec: GeofenceSpec, callback: GeofenceOperationCallback) {
        if (pendingIntent(spec.requestId, PendingIntent.FLAG_NO_CREATE) != null) {
            callback.onComplete(GeofenceOperationResult.Success)
        } else {
            register(spec, callback)
        }
    }

    override fun cancel(triggerId: TriggerId, callback: GeofenceOperationCallback) {
        val intent = pendingIntent(StableGeofenceId.from(triggerId), PendingIntent.FLAG_NO_CREATE)
        if (intent == null) {
            // Never registered here (or already removed): the cancellation's goal is met.
            callback.onComplete(GeofenceOperationResult.Success)
            return
        }
        runCatching {
            locationManager?.removeProximityAlert(intent)
            intent.cancel()
        }.onSuccess {
            callback.onComplete(GeofenceOperationResult.Success)
        }.onFailure { error ->
            callback.onComplete(GeofenceOperationResult.Failed(error))
        }
    }

    private fun pendingIntent(requestId: String, flag: Int): PendingIntent? {
        val intent = Intent(applicationContext, ProximityAlertReceiver::class.java).apply {
            action = ACTION_PROXIMITY_ALERT
            // One intent per trigger: the data URI makes each alert's PendingIntent distinct.
            data = Uri.Builder()
                .scheme(URI_SCHEME)
                .authority(URI_AUTHORITY)
                .appendPath(requestId)
                .build()
        }
        // The platform adds the entering/exiting extra, so the intent must stay mutable.
        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(applicationContext, REQUEST_CODE, intent, flag or mutable)
    }

    internal companion object {
        const val ACTION_PROXIMITY_ALERT = "com.afn478.geominder.geofence.PROXIMITY"
        const val URI_SCHEME = "geominder"
        const val URI_AUTHORITY = "proximity"
        const val REQUEST_CODE = 7200
        const val NO_EXPIRATION = -1L
    }
}
