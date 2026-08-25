package com.afn478.geominder.ui.list

import com.afn478.geominder.domain.model.Reminder

/**
 * Scheduling work that must accompany a list mutation.
 *
 * The integration layer handles both the exact alarm and geofence represented by the reminder.
 * Keeping this command explicit prevents a list action from becoming a persistence-only change.
 */
sealed interface ReminderScheduleCommand {
    data class Register(val reminder: Reminder) : ReminderScheduleCommand

    data class Cancel(val reminder: Reminder) : ReminderScheduleCommand
}

fun interface ReminderScheduleCommandHandler {
    suspend fun handle(command: ReminderScheduleCommand)
}
