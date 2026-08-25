package com.afn478.geominder.alert

import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.repository.ReminderRepository
import java.time.Clock

sealed interface AlertDeliveryResult {
    data class Delivered(val presentation: AlertPresentation) : AlertDeliveryResult
    data object ReminderNotFound : AlertDeliveryResult
    data object ReminderInactive : AlertDeliveryResult
}

/** Trigger integration entry point: persist the lifecycle event before presenting its alert. */
class AlertDeliveryCoordinator(
    private val repository: ReminderRepository,
    private val alertDelivery: AlertDelivery,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun onTriggered(
        reminderId: ReminderId,
        hasExactAlarmAccess: Boolean = true,
    ): AlertDeliveryResult {
        val reminder = repository.get(reminderId) ?: return AlertDeliveryResult.ReminderNotFound
        if (!reminder.isPending) return AlertDeliveryResult.ReminderInactive

        repository.recordTriggered(reminderId, clock.instant())
        return AlertDeliveryResult.Delivered(
            alertDelivery.deliver(reminder, hasExactAlarmAccess),
        )
    }
}
