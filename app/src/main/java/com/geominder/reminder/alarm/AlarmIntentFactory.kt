package com.geominder.reminder.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

internal class AlarmIntentFactory(
    private val context: Context,
) {
    fun pendingIntent(
        identity: AlarmIdentity,
        create: Boolean = true,
        deliveryMode: AlarmDeliveryMode = AlarmDeliveryMode.PLAIN_NOTIFICATION,
    ): PendingIntent? {
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        return PendingIntent.getBroadcast(
            context,
            identity.requestCode,
            intent(identity, deliveryMode),
            flags,
        )
    }

    fun intent(
        identity: AlarmIdentity,
        deliveryMode: AlarmDeliveryMode = AlarmDeliveryMode.PLAIN_NOTIFICATION,
    ): Intent = Intent(
        identity.kind.action,
        identity.toUri(),
        context,
        identity.kind.receiverClass,
    ).apply {
        setPackage(context.packageName)
        putExtra(AlarmContract.EXTRA_REMINDER_ID, identity.reminderId.value)
        identity.triggerId?.let { putExtra(AlarmContract.EXTRA_TRIGGER_ID, it.value) }
        putExtra(AlarmContract.EXTRA_DELIVERY_MODE, deliveryMode.wireValue)
    }

    private fun AlarmIdentity.toUri(): Uri = Uri.Builder()
        .scheme(AlarmContract.URI_SCHEME)
        .authority(AlarmContract.URI_AUTHORITY)
        .appendPath(kind.uriPath)
        .appendPath(reminderId.value)
        .apply { triggerId?.let { appendPath(it.value) } }
        .build()

    private val AlarmKind.receiverClass: Class<out BaseAlarmReceiver>
        get() = when (this) {
            AlarmKind.TIME -> TimeAlarmReceiver::class.java
            AlarmKind.GATED_CHECK -> GatedAlarmReceiver::class.java
            AlarmKind.SNOOZE -> SnoozeAlarmReceiver::class.java
        }
}
