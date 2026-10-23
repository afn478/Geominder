package com.afn478.geominder.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.afn478.geominder.data.local.entity.ChecklistItemEntity
import com.afn478.geominder.data.local.entity.GeoTriggerEntity
import com.afn478.geominder.data.local.entity.ReminderEntity
import com.afn478.geominder.data.local.entity.ReminderWithTriggers
import com.afn478.geominder.data.local.entity.TimeTriggerEntity
import com.afn478.geominder.data.mapper.ReminderEntities
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
        WHERE enabled = 1 AND status IN ('PENDING', 'SNOOZED') AND deleted_at IS NULL
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

    @Upsert
    protected abstract suspend fun upsertChecklistItems(entities: List<ChecklistItemEntity>)

    @Query("DELETE FROM checklist_items WHERE reminder_id = :reminderId")
    protected abstract suspend fun deleteChecklistItems(reminderId: String)

    /** Returns the number of rows changed so callers can detect a missing item. */
    @Query(
        "UPDATE checklist_items SET done = :done WHERE reminder_id = :reminderId AND id = :itemId",
    )
    abstract suspend fun setChecklistItemDone(reminderId: String, itemId: String, done: Boolean): Int

    @Query("DELETE FROM time_triggers WHERE reminder_id = :reminderId")
    protected abstract suspend fun deleteTimeTrigger(reminderId: String)

    @Query("DELETE FROM geo_triggers WHERE reminder_id = :reminderId")
    protected abstract suspend fun deleteGeoTrigger(reminderId: String)

    @Query("DELETE FROM reminders WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    /** Read-modify-write of one aggregate inside a single transaction. */
    @Transaction
    open suspend fun update(
        id: String,
        transform: (ReminderWithTriggers) -> ReminderEntities?,
    ): ReminderEntities? {
        val current = getById(id) ?: return null
        val updated = transform(current) ?: return null
        replace(updated.reminder, updated.timeTrigger, updated.geoTrigger, updated.checklistItems)
        return updated
    }

    /** Replaces the aggregate so removed or regenerated triggers never leave stale rows. */
    @Transaction
    open suspend fun replace(
        reminder: ReminderEntity,
        timeTrigger: TimeTriggerEntity?,
        geoTrigger: GeoTriggerEntity?,
        checklistItems: List<ChecklistItemEntity> = emptyList(),
    ) {
        upsertReminder(reminder)
        deleteTimeTrigger(reminder.id)
        deleteGeoTrigger(reminder.id)
        deleteChecklistItems(reminder.id)
        timeTrigger?.let { upsertTimeTrigger(it) }
        geoTrigger?.let { upsertGeoTrigger(it) }
        if (checklistItems.isNotEmpty()) upsertChecklistItems(checklistItems)
    }
}
