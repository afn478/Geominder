package com.afn478.geominder.geofence

import com.afn478.geominder.domain.model.TriggerId

/**
 * Uses [primary] (Google Play services) while it is available and Android's own location APIs
 * otherwise. Availability is checked per call, so installing or removing Play services later
 * needs no restart.
 */
class FallbackGeofenceRegistrar(
    private val primary: () -> GeofenceRegistrar,
    private val fallback: GeofenceRegistrar,
    private val usePrimary: () -> Boolean,
) : GeofenceRegistrar {
    override fun register(spec: GeofenceSpec, callback: GeofenceOperationCallback) {
        if (!usePrimary()) {
            fallback.register(spec, callback)
            return
        }
        // A trigger registered while Play services was missing must not fire twice.
        fallback.cancel(spec.triggerId) { primary().register(spec, callback) }
    }

    override fun cancel(triggerId: TriggerId, callback: GeofenceOperationCallback) {
        fallback.cancel(triggerId) { fallbackResult ->
            if (usePrimary()) primary().cancel(triggerId, callback) else callback.onComplete(fallbackResult)
        }
    }
}

class FallbackCurrentLocationProvider(
    private val primary: () -> CurrentLocationProvider,
    private val fallback: CurrentLocationProvider,
    private val usePrimary: () -> Boolean,
) : CurrentLocationProvider {
    override fun locate(callback: (LocationResult) -> Unit): CancellationHandle =
        if (usePrimary()) primary().locate(callback) else fallback.locate(callback)
}
