package com.afn478.geominder.data.mapper

import com.afn478.geominder.data.local.entity.GeoTriggerEntity
import com.afn478.geominder.data.local.entity.ReminderEntity
import com.afn478.geominder.data.local.entity.ReminderWithTriggers
import com.afn478.geominder.data.local.entity.TimeTriggerEntity
import com.afn478.geominder.domain.model.GeoTrigger
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.TimeTrigger
import com.afn478.geominder.domain.model.TriggerId

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
        tag = tag,
        enabled = enabled,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastTriggeredAt = lastTriggeredAt,
        snoozedUntil = snoozedUntil,
        dismissedAt = dismissedAt,
        deletedAt = deletedAt,
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
    tag = reminder.tag,
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
    deletedAt = reminder.deletedAt,
)
