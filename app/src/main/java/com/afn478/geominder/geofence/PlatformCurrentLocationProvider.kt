package com.afn478.geominder.geofence

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Current location through Android's own LocationManager, so it works without Google Play
 * services (for example on GrapheneOS or other de-Googled systems).
 */
class PlatformCurrentLocationProvider(
    context: Context,
    private val capabilityChecker: LocationCapabilityChecker = LocationCapabilityChecker(context),
) : CurrentLocationProvider {
    private val applicationContext = context.applicationContext
    private val locationManager = applicationContext.getSystemService(LocationManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission") // Guarded by LocationCapabilityChecker.
    override fun locate(callback: (LocationResult) -> Unit): CancellationHandle {
        val capability = capabilityChecker.check(requireBackgroundLocation = false)
        if (capability != LocationCapability.Available) {
            callback(LocationResult.Unavailable(capability.toLocationFailure()))
            return CancellationHandle {}
        }
        val manager = locationManager ?: run {
            callback(LocationResult.Unavailable(LocationFailure.REQUEST_FAILED))
            return CancellationHandle {}
        }
        val providers = enabledProviders(manager)
        if (providers.isEmpty()) {
            callback(LocationResult.Unavailable(LocationFailure.LOCATION_DISABLED))
            return CancellationHandle {}
        }

        val lastKnown = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos }
        if (lastKnown != null && lastKnown.ageMillis() <= MAX_LOCATION_AGE_MILLIS) {
            callback(LocationResult.Available(lastKnown.toFix()))
            return CancellationHandle {}
        }

        val completed = AtomicBoolean(false)
        val signals = providers.map { CancellationSignal() }
        var pending = providers.size
        lateinit var timeout: Runnable

        fun finish(result: LocationResult?) {
            if (!completed.compareAndSet(false, true)) return
            mainHandler.removeCallbacks(timeout)
            signals.forEach { runCatching { it.cancel() } }
            // Nothing fresh arrived: an older fix from the last few minutes still beats no answer.
            callback(
                result ?: lastKnown?.takeIf { it.ageMillis() <= STALE_FALLBACK_AGE_MILLIS }
                    ?.let { LocationResult.Available(it.toFix()) }
                    ?: LocationResult.Unavailable(LocationFailure.NO_LOCATION),
            )
        }
        timeout = Runnable { finish(null) }
        mainHandler.postDelayed(timeout, REQUEST_DURATION_MILLIS)

        // Every enabled provider races; the first real fix wins.
        providers.forEachIndexed { index, provider ->
            runCatching {
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    signals[index],
                    ContextCompat.getMainExecutor(applicationContext),
                ) { location ->
                    if (location != null) {
                        finish(LocationResult.Available(location.toFix()))
                    } else if (--pending == 0) {
                        finish(null)
                    }
                }
            }.onFailure {
                mainHandler.post { if (--pending == 0) finish(null) }
            }
        }

        return CancellationHandle {
            if (completed.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeout)
                signals.forEach { runCatching { it.cancel() } }
            }
        }
    }

    /** The AOSP fused provider when present; otherwise satellite and network providers. */
    private fun enabledProviders(manager: LocationManager): List<String> {
        val enabled = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        if (Build.VERSION.SDK_INT >= 31 && LocationManager.FUSED_PROVIDER in enabled) {
            return listOf(LocationManager.FUSED_PROVIDER)
        }
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { it in enabled }
    }

    private fun Location.ageMillis(): Long =
        (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L

    private fun Location.toFix(): LocationFix = LocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy.takeIf { hasAccuracy() },
        measuredAt = Instant.ofEpochMilli(time),
    )

    private companion object {
        const val MAX_LOCATION_AGE_MILLIS = 5_000L
        const val STALE_FALLBACK_AGE_MILLIS = 2 * 60_000L
        // Without Play services there is no Wi-Fi assisted fix, so a cold GPS start needs longer.
        const val REQUEST_DURATION_MILLIS = 20_000L
    }
}
