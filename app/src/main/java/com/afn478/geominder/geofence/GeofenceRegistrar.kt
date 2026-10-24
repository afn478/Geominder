package com.afn478.geominder.geofence

import com.afn478.geominder.domain.model.TriggerId

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

    /**
     * Makes sure the geofence exists again after the system may have dropped it, for example when
     * location was switched off or the app was force-stopped. Defaults to registering it anew.
     */
    fun refresh(spec: GeofenceSpec, callback: GeofenceOperationCallback) = register(spec, callback)
    fun cancel(triggerId: TriggerId, callback: GeofenceOperationCallback)
}
