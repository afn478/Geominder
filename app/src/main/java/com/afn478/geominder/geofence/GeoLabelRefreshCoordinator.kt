package com.afn478.geominder.geofence

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** A small lifecycle boundary around ConnectivityManager's process-wide callback. */
interface NetworkAvailabilityMonitor {
    fun start(onAvailable: () -> Unit)
    fun stop()
}

class AndroidNetworkAvailabilityMonitor(context: Context) : NetworkAvailabilityMonitor {
    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    @Synchronized
    override fun start(onAvailable: () -> Unit) {
        if (callback != null) return
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onAvailable()
        }
        callback = networkCallback
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
            .onFailure { callback = null }
        if (isAvailable()) onAvailable()
    }

    @Synchronized
    override fun stop() {
        callback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        callback = null
    }

    private fun isAvailable(): Boolean = connectivityManager.activeNetwork
        ?.let { connectivityManager.getNetworkCapabilities(it) }
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}

/** Refreshes presentation-only labels; it never calls a scheduler. */
class GeoLabelRefreshCoordinator(
    private val repository: ReminderRepository,
    private val resolver: GeoLabelResolver,
    private val networkMonitor: NetworkAvailabilityMonitor,
    private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)
    private val refreshing = AtomicBoolean(false)
    private var refreshJob: Job? = null

    fun start() {
        if (!started.compareAndSet(false, true)) return
        networkMonitor.start { refresh() }
        // The monitor also checks the already-connected state. This explicit startup refresh
        // makes startup semantics clear for monitor implementations that only report transitions.
        refresh()
    }

    fun stop() {
        if (started.compareAndSet(true, false)) {
            networkMonitor.stop()
            refreshJob?.cancel()
        }
    }

    private fun refresh() {
        if (!started.get() || !refreshing.compareAndSet(false, true)) return
        refreshJob = scope.launch {
            try {
                repository.observeAll().first().forEach { reminder ->
                    runCatching { refreshReminder(reminder) }
                }
            } finally {
                refreshing.set(false)
            }
        }
    }

    private suspend fun refreshReminder(reminder: Reminder) {
        val trigger = reminder.geoTrigger ?: return
        if (!isFallbackLabel(trigger.label)) return
        val resolved = runCatching { resolve(trigger.latitude, trigger.longitude) }.getOrNull()
            ?.takeIf { it.isNotBlank() && !isFallbackLabel(it) }
            ?: return
        runCatching {
            repository.save(reminder.copy(geoTrigger = trigger.copy(label = resolved)))
        }
    }

    private suspend fun resolve(latitude: Double, longitude: Double): String =
        suspendCancellableCoroutine { continuation ->
            runCatching {
                resolver.resolve(latitude, longitude) { label ->
                    if (continuation.isActive) continuation.resume(label)
                }
            }.onFailure { if (continuation.isActive) continuation.resume("") }
        }

    private fun isFallbackLabel(label: String?): Boolean =
        label.isNullOrBlank() || FALLBACK_LABEL.matches(label.trim())

    private companion object {
        val FALLBACK_LABEL = Regex(
            "(?i)^near\\s+[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)\\s*,\\s*[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)$",
        )
    }
}
