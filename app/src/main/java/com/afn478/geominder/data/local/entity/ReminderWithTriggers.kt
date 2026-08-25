package com.afn478.geominder.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/** Room projection for the complete reminder aggregate. */
data class ReminderWithTriggers(
    @Embedded val reminder: ReminderEntity,
    @Relation(parentColumn = "id", entityColumn = "reminder_id")
    val timeTrigger: TimeTriggerEntity?,
    @Relation(parentColumn = "id", entityColumn = "reminder_id")
    val geoTrigger: GeoTriggerEntity?,
)
