package com.afn478.geominder.alert

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.afn478.geominder.R
import com.afn478.geominder.domain.model.Reminder

fun interface AlertDelivery {
    fun deliver(reminder: Reminder, hasExactAlarmAccess: Boolean): AlertPresentation
}

/**
 * Routes a triggered reminder using device state sampled immediately before posting. A full-screen
 * alert is always launched through the OS full-screen notification mechanism, never a service.
 */
class AndroidAlertDelivery(
    context: Context,
    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java),
    private val keyguardManager: KeyguardManager =
        context.getSystemService(KeyguardManager::class.java),
    private val powerManager: PowerManager = context.getSystemService(PowerManager::class.java),
    private val fullScreenCapability: FullScreenIntentCapability =
        FullScreenIntentCapability(context),
) : AlertDelivery {
    private val applicationContext = context.applicationContext

    override fun deliver(reminder: Reminder, hasExactAlarmAccess: Boolean): AlertPresentation {
        createNotificationChannel()
        val isLockedAndNonInteractive =
            keyguardManager.isKeyguardLocked && !powerManager.isInteractive
        val presentation = AlertDeliveryPolicy.choosePresentation(
            isLockedAndNonInteractive = isLockedAndNonInteractive,
            capabilities = AlertCapabilities(
                canUseFullScreenIntent = fullScreenCapability.canUseFullScreenIntent(),
                hasExactAlarmAccess = hasExactAlarmAccess,
            ),
        )
        val notification = buildNotification(reminder, presentation)
        notificationManager.notify(StableAlertId.notificationId(reminder.id), notification)
        return presentation
    }

    fun cancel(reminder: Reminder) {
        notificationManager.cancel(StableAlertId.notificationId(reminder.id))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            AlertContract.CHANNEL_ID,
            AlertContract.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = applicationContext.getString(R.string.alert_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        reminder: Reminder,
        presentation: AlertPresentation,
    ): Notification {
        val body = reminder.text.ifBlank { reminder.sourceText }
        val builder = Notification.Builder(applicationContext, AlertContract.CHANNEL_ID)
            .setSmallIcon(R.drawable.alert_notification_icon)
            .setContentTitle(reminder.title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .addAction(action(reminder, AlertAction.SNOOZE, R.string.alert_snooze))
            .addAction(action(reminder, AlertAction.DISMISS, R.string.alert_dismiss))
            .addAction(action(reminder, AlertAction.DONE, R.string.alert_done))

        contentPendingIntent(reminder)?.let(builder::setContentIntent)

        if (presentation == AlertPresentation.FULL_SCREEN) {
            builder.setFullScreenIntent(fullScreenPendingIntent(reminder), true)
        }
        return builder.build()
    }

    private fun action(
        reminder: Reminder,
        action: AlertAction,
        titleResource: Int,
    ): Notification.Action = Notification.Action.Builder(
        null,
        applicationContext.getString(titleResource),
        AlertIntentFactory.actionPendingIntent(applicationContext, reminder, action),
    ).build()

    private fun fullScreenPendingIntent(reminder: Reminder): PendingIntent {
        val intent = AlertIntentFactory.fullScreenIntent(applicationContext, reminder)
        return PendingIntent.getActivity(
            applicationContext,
            StableAlertId.fullScreenRequestCode(reminder.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun contentPendingIntent(reminder: Reminder): PendingIntent? {
        val launchIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: return null
        return PendingIntent.getActivity(
            applicationContext,
            StableAlertId.contentRequestCode(reminder.id),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

object AlertIntentFactory {
    fun fullScreenIntent(context: Context, reminder: Reminder): Intent =
        Intent(context, FullScreenAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlertContract.EXTRA_REMINDER_ID, reminder.id.value)
            putExtra(AlertContract.EXTRA_TITLE, reminder.title)
            putExtra(AlertContract.EXTRA_TEXT, reminder.text.ifBlank { reminder.sourceText })
        }

    fun debugFullScreenIntent(context: Context): Intent =
        Intent(context, FullScreenAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlertContract.EXTRA_REMINDER_ID, DEBUG_REMINDER_ID)
            putExtra(AlertContract.EXTRA_TITLE, "Debug full-screen reminder")
            putExtra(
                AlertContract.EXTRA_TEXT,
                "The display should follow the normal timeout and return over the lock screen when woken.",
            )
            putExtra(AlertContract.EXTRA_DEBUG_ALERT, true)
        }

    fun actionPendingIntent(
        context: Context,
        reminder: Reminder,
        action: AlertAction,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        StableAlertId.actionRequestCode(reminder.id, action),
        actionIntent(context, reminder.id.value, action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun actionIntent(
        context: Context,
        reminderId: String,
        action: AlertAction,
        snoozeMillis: Long = AlertContract.DEFAULT_SNOOZE_MILLIS,
    ): Intent = Intent(context, AlertActionReceiver::class.java).apply {
        this.action = action.intentAction
        putExtra(AlertContract.EXTRA_REMINDER_ID, reminderId)
        if (action == AlertAction.SNOOZE) {
            putExtra(AlertContract.EXTRA_SNOOZE_MILLIS, snoozeMillis)
        }
    }

    private const val DEBUG_REMINDER_ID = "debug-full-screen-reminder"
}
