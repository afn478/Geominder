package com.geominder.reminder.data.mapper

import com.geominder.reminder.data.local.entity.GeoTriggerEntity
import com.geominder.reminder.data.local.entity.ReminderEntity
import com.geominder.reminder.data.local.entity.ReminderWithTriggers
import com.geominder.reminder.data.local.entity.TimeTriggerEntity
import com.geominder.reminder.domain.model.GeoTrigger
import com.geominder.reminder.domain.model.Reminder
import com.geominder.reminder.domain.model.ReminderId
import com.geominder.reminder.domain.model.TimeTrigger
import com.geominder.reminder.domain.model.TriggerId

data class ReminderEntities(
    val reminder: ReminderEntity,
    val timeTrigger: TimeTriggerEntity?,
    val geoTrigger: GeoTriggerEntity?,
)

fun Reminder.toEntities(): ReminderEntities = ReminderEntities(
    reminder = ReminderEntity(
        id = id.value,
        sourceText = sourceText,
        title = title,
        text = text,
        enabled = enabled,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastTriggeredAt = lastTriggeredAt,
        snoozedUntil = snoozedUntil,
        dismissedAt = dismissedAt,
    ),
    timeTrigger = timeTrigger?.let { trigger ->
        TimeTriggerEntity(
            id = trigger.id.value,
            reminderId = id.value,
            exactAt = trigger.exactAt,
        )
    },
    geoTrigger = geoTrigger?.let { trigger ->
        GeoTriggerEntity(
            id = trigger.id.value,
            reminderId = id.value,
            latitude = trigger.latitude,
            longitude = trigger.longitude,
            radiusMeters = trigger.radiusMeters,
            label = trigger.label,
            activeFrom = trigger.activeFrom,
        )
    },
)

fun ReminderWithTriggers.toDomain(): Reminder = Reminder(
    id = ReminderId(reminder.id),
    sourceText = reminder.sourceText,
    title = reminder.title,
    text = reminder.text,
    enabled = reminder.enabled,
    status = reminder.status,
    timeTrigger = timeTrigger?.let { trigger ->
        TimeTrigger(
            id = TriggerId(trigger.id),
            exactAt = trigger.exactAt,
        )
    },
    geoTrigger = geoTrigger?.let { trigger ->
        GeoTrigger(
            id = TriggerId(trigger.id),
            latitude = trigger.latitude,
            longitude = trigger.longitude,
            radiusMeters = trigger.radiusMeters,
            label = trigger.label,
            activeFrom = trigger.activeFrom,
        )
    },
    createdAt = reminder.createdAt,
    updatedAt = reminder.updatedAt,
    lastTriggeredAt = reminder.lastTriggeredAt,
    snoozedUntil = reminder.snoozedUntil,
    dismissedAt = reminder.dismissedAt,
)
