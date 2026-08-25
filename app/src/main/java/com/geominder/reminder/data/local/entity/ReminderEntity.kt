package com.geominder.reminder.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.geominder.reminder.domain.model.ReminderStatus
import java.time.Instant

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "source_text") val sourceText: String,
    val title: String,
    val text: String,
    val enabled: Boolean,
    val status: ReminderStatus,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "last_triggered_at") val lastTriggeredAt: Instant?,
    @ColumnInfo(name = "snoozed_until") val snoozedUntil: Instant?,
    @ColumnInfo(name = "dismissed_at") val dismissedAt: Instant?,
)

@Entity(
    tableName = "time_triggers",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminder_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["reminder_id"], unique = true)],
)
data class TimeTriggerEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "reminder_id") val reminderId: String,
    @ColumnInfo(name = "exact_at") val exactAt: Instant,
)

@Entity(
    tableName = "geo_triggers",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminder_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["reminder_id"], unique = true)],
)
data class GeoTriggerEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "reminder_id") val reminderId: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "radius_meters") val radiusMeters: Double,
    val label: String?,
    @ColumnInfo(name = "active_from") val activeFrom: Instant?,
)
