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
    fun cancel(triggerId: TriggerId, callback: GeofenceOperationCallback)
}
