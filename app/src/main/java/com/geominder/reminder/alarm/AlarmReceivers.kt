package com.geominder.reminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.geominder.reminder.domain.model.ReminderId
import com.geominder.reminder.domain.model.TriggerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class AlarmEvent(
    val kind: AlarmKind,
    val reminderId: ReminderId,
    val triggerId: TriggerId?,
    val deliveryMode: AlarmDeliveryMode = AlarmDeliveryMode.PLAIN_NOTIFICATION,
)

/** Framework-free event parsing so the PendingIntent wire contract can be tested on the JVM. */
object AlarmEventParser {
    fun parse(
        expectedKind: AlarmKind,
        action: String?,
        reminderIdValue: String?,
        triggerIdValue: String?,
        deliveryModeValue: String?,
    ): AlarmEvent? {
        if (action != expectedKind.action) return null
        val reminderId = reminderIdValue?.takeIf(String::isNotBlank) ?: return null
        val triggerId = triggerIdValue
            ?.takeIf(String::isNotBlank)
            ?.let(::TriggerId)
        return AlarmEvent(
            kind = expectedKind,
            reminderId = ReminderId(reminderId),
            triggerId = triggerId,
            deliveryMode = AlarmDeliveryMode.fromWireValue(deliveryModeValue),
        )
    }
}

fun interface AlarmEventHandler {
    /** Runs under BroadcastReceiver.goAsync(); implementations may safely query Room. */
    suspend fun onAlarm(context: Context, event: AlarmEvent)
}

/**
 * Process-start-safe hook: Android constructs the Application before a manifest receiver. The
 * integration layer must install its handler from Application.onCreate().
 */
object AlarmEventDispatcher {
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var handler: AlarmEventHandler? = null

    fun install(handler: AlarmEventHandler) {
        this.handler = handler
    }

    fun dispatch(
        context: Context,
        event: AlarmEvent,
        onComplete: () -> Unit,
    ): Boolean {
        val currentHandler = handler ?: return false
        receiverScope.launch {
            try {
                currentHandler.onAlarm(context.applicationContext, event)
            } catch (error: Throwable) {
                Log.e(TAG, "Alarm handler failed; event=$event", error)
            } finally {
                onComplete()
            }
        }
        return true
    }

    private const val TAG = "GeominderAlarm"
}

abstract class BaseAlarmReceiver(
    private val expectedKind: AlarmKind,
) : BroadcastReceiver() {
    final override fun onReceive(context: Context, intent: Intent) {
        val event = AlarmEventParser.parse(
            expectedKind = expectedKind,
            action = intent.action,
            reminderIdValue = intent.getStringExtra(AlarmContract.EXTRA_REMINDER_ID),
            triggerIdValue = intent.getStringExtra(AlarmContract.EXTRA_TRIGGER_ID),
            deliveryModeValue = intent.getStringExtra(AlarmContract.EXTRA_DELIVERY_MODE),
        ) ?: return
        val pendingResult = goAsync()
        if (!AlarmEventDispatcher.dispatch(context, event, pendingResult::finish)) {
            pendingResult.finish()
            Log.e(TAG, "Alarm handler is not installed; event=$event")
        }
    }

    private companion object {
        const val TAG = "GeominderAlarm"
    }
}

class TimeAlarmReceiver : BaseAlarmReceiver(AlarmKind.TIME)

class GatedAlarmReceiver : BaseAlarmReceiver(AlarmKind.GATED_CHECK)

class SnoozeAlarmReceiver : BaseAlarmReceiver(AlarmKind.SNOOZE)
