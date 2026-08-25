package com.afn478.geominder.boot

import com.afn478.geominder.alarm.ExactAlarmScheduler
import com.afn478.geominder.domain.repository.ReminderRepository
import com.afn478.geominder.geofence.GeofenceRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

fun interface BootFeatureEntryPoint {
    /** Starts restoration and invokes [completion] exactly once with its aggregate result. */
    fun resynchronize(completion: (BootResyncReport) -> Unit)
}

/**
 * Application-owned bridge for manifest-created receivers. Android creates Application before a
 * process-starting broadcast receiver, so Application.onCreate can install the current graph here.
 */
object BootFeatureRuntime {
    @Volatile
    var entryPoint: BootFeatureEntryPoint? = null
}

class DefaultBootFeatureEntryPoint(
    repository: ReminderRepository,
    alarmScheduler: ExactAlarmScheduler,
    geofenceRegistrar: GeofenceRegistrar,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : BootFeatureEntryPoint {
    private val coordinator = BootResyncCoordinator(
        repository = repository,
        alarmScheduler = alarmScheduler,
        geofenceRegistrar = geofenceRegistrar,
    )

    override fun resynchronize(completion: (BootResyncReport) -> Unit) {
        val completeOnce = CompleteOnce(completion)
        try {
            val job = scope.launch {
                val report = try {
                    coordinator.resynchronize()
                } catch (error: Throwable) {
                    BootResyncReport.fatal(error)
                }
                completeOnce(report)
            }
            job.invokeOnCompletion { error ->
                if (error != null) {
                    completeOnce(BootResyncReport.fatal(error))
                }
            }
        } catch (error: Throwable) {
            completeOnce(BootResyncReport.fatal(error))
        }
    }
}

/** Keeps BroadcastReceiver.PendingResult completion safe across all runtime failure paths. */
internal object BootResyncLauncher {
    fun launch(entryPoint: BootFeatureEntryPoint?, finished: () -> Unit) {
        val finishOnce = CompleteOnce<Unit> { finished() }
        if (entryPoint == null) {
            finishOnce(Unit)
            return
        }

        try {
            entryPoint.resynchronize { finishOnce(Unit) }
        } catch (_: Throwable) {
            finishOnce(Unit)
        }
    }
}

private class CompleteOnce<T>(
    private val delegate: (T) -> Unit,
) {
    private val completed = AtomicBoolean(false)

    operator fun invoke(value: T) {
        if (completed.compareAndSet(false, true)) {
            delegate(value)
        }
    }
}
