package com.afn478.geominder.alert

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.repository.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class AlertActionEvent(
    val reminderId: ReminderId,
    val action: AlertAction,
    val snoozeDuration: Duration = Duration.ofMillis(AlertContract.DEFAULT_SNOOZE_MILLIS),
)

fun interface AlertActionHandler {
    suspend fun handle(event: AlertActionEvent)
}

fun interface AlertSnoozeScheduler {
    fun schedule(reminderId: ReminderId, at: Instant)
}

class RepositoryAlertActionHandler(
    private val repository: ReminderRepository,
    private val snoozeScheduler: AlertSnoozeScheduler,
    private val clock: Clock = Clock.systemUTC(),
) : AlertActionHandler {
    override suspend fun handle(event: AlertActionEvent) {
        val now = clock.instant()
        when (event.action) {
            AlertAction.SNOOZE -> {
                val until = now.plus(event.snoozeDuration)
                repository.snooze(event.reminderId, until, now)
                snoozeScheduler.schedule(event.reminderId, until)
            }
            AlertAction.DISMISS -> repository.dismiss(event.reminderId, now)
            AlertAction.DONE -> repository.complete(event.reminderId, now)
        }
    }
}

/** Installed from Application.onCreate so manifest-created receivers are process-start safe. */
object AlertActionDispatcher {
    @Volatile
    private var handler: AlertActionHandler? = null

    fun install(handler: AlertActionHandler) {
        this.handler = handler
    }

    internal fun currentHandler(): AlertActionHandler? = handler
}

class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = intent.toAlertActionEvent() ?: return
        val handler = AlertActionDispatcher.currentHandler()
        if (handler == null) {
            Log.e(TAG, "Alert action handler is not installed; event=$event")
            return
        }

        context.getSystemService(NotificationManager::class.java)
            .cancel(StableAlertId.notificationId(event.reminderId))
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                handler.handle(event)
            } catch (exception: Exception) {
                Log.e(TAG, "Unable to handle alert action $event", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GeominderAlert"
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

internal fun Intent.toAlertActionEvent(): AlertActionEvent? {
    val parsedAction = AlertAction.fromIntentAction(action) ?: return null
    val reminderId = getStringExtra(AlertContract.EXTRA_REMINDER_ID)
        ?.takeIf(String::isNotBlank)
        ?.let(::ReminderId)
        ?: return null
    val snoozeMillis = getLongExtra(
        AlertContract.EXTRA_SNOOZE_MILLIS,
        AlertContract.DEFAULT_SNOOZE_MILLIS,
    ).coerceAtLeast(1L)
    return AlertActionEvent(
        reminderId = reminderId,
        action = parsedAction,
        snoozeDuration = Duration.ofMillis(snoozeMillis),
    )
}
