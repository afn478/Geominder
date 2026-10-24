package com.afn478.geominder.boot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Brings alarms and geofences back after the system dropped them without telling the app.
 *
 * Play services deletes every geofence when location is switched off, and a force stop (or an
 * aggressive OEM battery manager) cancels alarms and geofences until the next reboot. None of this
 * is announced to a manifest receiver, so registrations are restored instead when:
 * - the process starts and the twice-daily refresh alarm is missing, which is what a reboot or a
 *   force stop leaves behind;
 * - that refresh alarm fires, at most twice a day and only when the device is awake anyway;
 * - location is switched back on while the process is alive;
 * - the app is opened.
 * A refresh only re-registers what the database already holds; it never requests a location.
 */
object RegistrationRefresh {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun install(context: Context) {
        val applicationContext = context.applicationContext
        if (RefreshAlarm.ensureScheduled(applicationContext)) request("refresh alarm missing")
        LocationSwitchWatcher.register(applicationContext)
    }

    fun request(reason: String) {
        Log.i(TAG, "Refreshing registrations: $reason")
        BootFeatureRuntime.entryPoint?.resynchronize { report ->
            if (!report.isFullySuccessful) Log.w(TAG, "Refresh incomplete: ${report.failures}")
        }
    }

    /** Resynchronizes once location is back, retrying once while location services settle. */
    internal fun afterLocationEnabled() {
        scope.launch {
            delay(SETTLE_DELAY_MILLIS)
            if (resynchronize()?.isFullySuccessful == false) {
                delay(RETRY_DELAY_MILLIS)
                resynchronize()
            }
        }
    }

    private suspend fun resynchronize(): BootResyncReport? {
        val entryPoint = BootFeatureRuntime.entryPoint ?: return null
        Log.i(TAG, "Refreshing registrations: location switched on")
        return suspendCancellableCoroutine { continuation ->
            entryPoint.resynchronize { report -> if (continuation.isActive) continuation.resume(report) }
        }
    }

    internal const val TAG = "GeominderRefresh"
    private const val SETTLE_DELAY_MILLIS = 3_000L
    private const val RETRY_DELAY_MILLIS = 30_000L
}

/** An inexact, non-waking alarm every 12 hours; its absence also reveals a reboot or force stop. */
internal object RefreshAlarm {
    /** Schedules the alarm unless it already exists; true means it had been lost. */
    fun ensureScheduled(context: Context): Boolean {
        if (pendingIntent(context, PendingIntent.FLAG_NO_CREATE) != null) return false
        val operation = pendingIntent(context, 0) ?: return false
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        runCatching {
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HALF_DAY,
                AlarmManager.INTERVAL_HALF_DAY,
                operation,
            )
        }.onFailure { Log.w(RegistrationRefresh.TAG, "Refresh alarm not scheduled", it) }
        return true
    }

    private fun pendingIntent(context: Context, flag: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, RefreshAlarmReceiver::class.java).setAction(ACTION_REFRESH),
        flag or PendingIntent.FLAG_IMMUTABLE,
    )

    const val ACTION_REFRESH = "com.afn478.geominder.boot.REFRESH"
    private const val REQUEST_CODE = 7300
}

class RefreshAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RefreshAlarm.ACTION_REFRESH) return
        val pendingResult = goAsync()
        BootResyncLauncher.launch(entryPoint = BootFeatureRuntime.entryPoint) { pendingResult.finish() }
    }
}

/**
 * Location on/off is not delivered to manifest receivers, so this listens only while the process
 * lives. The periodic refresh covers a switch made while the app was not running.
 */
private object LocationSwitchWatcher {
    @Volatile
    private var registered = false

    fun register(context: Context) {
        if (registered) return
        registered = true
        val locationManager = context.getSystemService(LocationManager::class.java) ?: return
        var enabled = locationManager.isLocationEnabled
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Sent once per provider; only the switch from off to on matters.
                val nowEnabled = locationManager.isLocationEnabled
                if (nowEnabled && !enabled) RegistrationRefresh.afterLocationEnabled()
                enabled = nowEnabled
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
