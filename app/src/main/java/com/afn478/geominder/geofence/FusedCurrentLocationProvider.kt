package com.afn478.geominder.geofence

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

class FusedCurrentLocationProvider(
    context: Context,
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext),
    private val capabilityChecker: LocationCapabilityChecker = LocationCapabilityChecker(context),
) : CurrentLocationProvider {
    @SuppressLint("MissingPermission") // Guarded by LocationCapabilityChecker.
    override fun locate(callback: (LocationResult) -> Unit): CancellationHandle {
        val capability = capabilityChecker.check(requireBackgroundLocation = false)
        if (capability != LocationCapability.Available) {
            callback(LocationResult.Unavailable(capability.toLocationFailure()))
            return CancellationHandle {}
        }

        val cancellationTokenSource = CancellationTokenSource()
        val completed = AtomicBoolean(false)
        runCatching {
            client.getCurrentLocation(
                CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(MAX_LOCATION_AGE_MILLIS)
                    .setDurationMillis(REQUEST_DURATION_MILLIS)
                    .build(),
                cancellationTokenSource.token,
            )
        }.onSuccess { task ->
            task.addOnSuccessListener { location ->
                if (!completed.compareAndSet(false, true)) return@addOnSuccessListener
                if (location == null) {
                    callback(LocationResult.Unavailable(LocationFailure.NO_LOCATION))
                } else {
                    callback(
                        LocationResult.Available(
                            LocationFix(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
                                measuredAt = Instant.ofEpochMilli(location.time),
                            ),
                        ),
                    )
                }
            }.addOnFailureListener {
                if (completed.compareAndSet(false, true)) {
                    callback(LocationResult.Unavailable(LocationFailure.REQUEST_FAILED))
                }
            }.addOnCanceledListener {
                completed.set(true)
            }
        }.onFailure {
            if (completed.compareAndSet(false, true)) {
                callback(LocationResult.Unavailable(LocationFailure.REQUEST_FAILED))
            }
        }

        return CancellationHandle {
            if (completed.compareAndSet(false, true)) {
                cancellationTokenSource.cancel()
            }
        }
    }

    private fun LocationCapability.toLocationFailure(): LocationFailure = when (this) {
        LocationCapability.Available -> error("Available is not a failure")
        LocationCapability.FineLocationPermissionRequired -> LocationFailure.PERMISSION_REQUIRED
        LocationCapability.BackgroundLocationPermissionRequired ->
            LocationFailure.BACKGROUND_PERMISSION_REQUIRED
        LocationCapability.LocationServicesDisabled -> LocationFailure.LOCATION_DISABLED
        is LocationCapability.PlayServicesUnavailable -> LocationFailure.PLAY_SERVICES_UNAVAILABLE
    }

    private companion object {
        const val MAX_LOCATION_AGE_MILLIS = 5_000L
        const val REQUEST_DURATION_MILLIS = 8_000L
    }
}
