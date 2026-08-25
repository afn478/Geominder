package com.geominder.reminder.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.geominder.reminder.data.local.entity.GeoTriggerEntity
import com.geominder.reminder.data.local.entity.ReminderEntity
import com.geominder.reminder.data.local.entity.ReminderWithTriggers
import com.geominder.reminder.data.local.entity.TimeTriggerEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ReminderDao {
    @Transaction
    @Query("SELECT * FROM reminders ORDER BY updated_at DESC")
    abstract fun observeAll(): Flow<List<ReminderWithTriggers>>

    @Transaction
    @Query("SELECT * FROM reminders WHERE id = :id")
    abstract fun observeById(id: String): Flow<ReminderWithTriggers?>

    @Transaction
    @Query("SELECT * FROM reminders WHERE id = :id")
    abstract suspend fun getById(id: String): ReminderWithTriggers?

    @Transaction
    @Query(
        """
        SELECT * FROM reminders
        WHERE enabled = 1 AND status IN ('PENDING', 'SNOOZED')
        ORDER BY updated_at DESC
        """,
    )
    abstract suspend fun getPending(): List<ReminderWithTriggers>

    @Upsert
    protected abstract suspend fun upsertReminder(entity: ReminderEntity)

    @Upsert
    protected abstract suspend fun upsertTimeTrigger(entity: TimeTriggerEntity)

    @Upsert
    protected abstract suspend fun upsertGeoTrigger(entity: GeoTriggerEntity)

    @Query("DELETE FROM time_triggers WHERE reminder_id = :reminderId")
    protected abstract suspend fun deleteTimeTrigger(reminderId: String)

    @Query("DELETE FROM geo_triggers WHERE reminder_id = :reminderId")
    protected abstract suspend fun deleteGeoTrigger(reminderId: String)

    @Query("DELETE FROM reminders WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    /** Replaces the aggregate so removed or regenerated triggers never leave stale rows. */
    @Transaction
    open suspend fun replace(
        reminder: ReminderEntity,
        timeTrigger: TimeTriggerEntity?,
        geoTrigger: GeoTriggerEntity?,
    ) {
        upsertReminder(reminder)
        deleteTimeTrigger(reminder.id)
        deleteGeoTrigger(reminder.id)
        timeTrigger?.let { upsertTimeTrigger(it) }
        geoTrigger?.let { upsertGeoTrigger(it) }
    }
}
