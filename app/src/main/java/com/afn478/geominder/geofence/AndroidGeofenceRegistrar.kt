package com.afn478.geominder.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.afn478.geominder.domain.model.TriggerId
import com.google.android.gms.location.GeofencingClient

sealed interface GeofenceOperationResult {
    data object Success : GeofenceOperationResult
    data class CapabilityUnavailable(val capability: LocationCapability) : GeofenceOperationResult
    data object RadiusTooLarge : GeofenceOperationResult
    data class Failed(val cause: Throwable) : GeofenceOperationResult
}

fun interface GeofenceOperationCallback {
    fun onComplete(result: GeofenceOperationResult)
}

interface GeofenceRegistrar {
    fun register(spec: GeofenceSpec, callback: GeofenceOperationCallback)
    fun cancel(triggerId: TriggerId, callback: GeofenceOperationCallback)
}

class AndroidGeofenceRegistrar(
    context: Context,
    private val geofencingClient: GeofencingClient,
    private val capabilityChecker: LocationCapabilityChecker = LocationCapabilityChecker(context),
) : GeofenceRegistrar {
    private val applicationContext = context.applicationContext

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

        runCatching {
            geofencingClient.addGeofences(
                GoogleGeofenceRequestFactory.request(spec),
                geofencePendingIntent(applicationContext),
            )
        }.onSuccess { task ->
            task.addOnSuccessListener {
                callback.onComplete(GeofenceOperationResult.Success)
            }.addOnFailureListener { error ->
                callback.onComplete(GeofenceOperationResult.Failed(error))
            }
        }.onFailure { error ->
            callback.onComplete(GeofenceOperationResult.Failed(error))
        }
    }

    override fun cancel(
        triggerId: TriggerId,
        callback: GeofenceOperationCallback,
    ) {
        runCatching {
            geofencingClient.removeGeofences(listOf(StableGeofenceId.from(triggerId)))
        }.onSuccess { task ->
            task.addOnSuccessListener {
                callback.onComplete(GeofenceOperationResult.Success)
            }.addOnFailureListener { error ->
                callback.onComplete(GeofenceOperationResult.Failed(error))
            }
        }.onFailure { error ->
            callback.onComplete(GeofenceOperationResult.Failed(error))
        }
    }

    private fun geofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = ACTION_GEOFENCE_EVENT
            data = Uri.parse(GEOFENCE_EVENT_URI)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.getBroadcast(context, GEOFENCE_REQUEST_CODE, intent, flags)
    }

    private companion object {
        const val ACTION_GEOFENCE_EVENT = "com.afn478.geominder.geofence.ENTER"
        const val GEOFENCE_EVENT_URI = "geominder://geofence/enter"
        const val GEOFENCE_REQUEST_CODE = 7100
    }
}
