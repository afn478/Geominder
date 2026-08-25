package com.afn478.geominder.ui.add

import com.afn478.geominder.domain.model.Reminder

/** Settings-owned defaults consumed by the add-reminder feature. */
fun interface DefaultGeoRadiusProvider {
    fun getDefaultRadiusMeters(): Double
}

/**
 * Integration seam invoked only after a reminder has been persisted. Implementations should
 * schedule the exact alarm and register the geofence represented by [reminder].
 */
interface ReminderPostSaveActions {
    suspend fun scheduleTimeTrigger(reminder: Reminder)

    suspend fun registerGeoTrigger(reminder: Reminder)

    suspend fun cancelReminder(reminder: Reminder) = Unit
}
