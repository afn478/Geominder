package com.afn478.geominder.data.repository

import android.util.Log
import com.afn478.geominder.data.local.ReminderDao
import com.afn478.geominder.data.local.entity.ReminderWithTriggers
import com.afn478.geominder.data.mapper.toDomain
import com.afn478.geominder.data.mapper.toEntities
import com.afn478.geominder.domain.model.ChecklistItemId
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.ReminderStatus
import com.afn478.geominder.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class RoomReminderRepository(
    private val reminderDao: ReminderDao,
) : ReminderRepository {
    override fun observeAll(): Flow<List<Reminder>> =
        reminderDao.observeAll().map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

    override fun observe(id: ReminderId): Flow<Reminder?> =
        reminderDao.observeById(id.value).map { it?.toDomainOrNull() }

    override suspend fun get(id: ReminderId): Reminder? =
        reminderDao.getById(id.value)?.toDomainOrNull()

    override suspend fun getPending(): List<Reminder> =
        reminderDao.getPending().mapNotNull { it.toDomainOrNull() }

    override suspend fun save(reminder: Reminder) {
        val entities = reminder.toEntities()
        reminderDao.replace(
            entities.reminder,
            entities.timeTrigger,
            entities.geoTrigger,
            entities.checklistItems,
        )
    }

    override suspend fun update(id: ReminderId, transform: (Reminder) -> Reminder?): Reminder? {
        var updated: Reminder? = null
        reminderDao.update(id.value) transformRow@{ row ->
            val current = row.toDomainOrNull() ?: return@transformRow null
            transform(current)?.also { updated = it }?.toEntities()
        }
        return updated
    }

    override suspend fun moveToTrash(id: ReminderId, changedAt: Instant) {
        updateExisting(id) { reminder ->
            val at = reminder.clamp(changedAt)
            reminder.copy(updatedAt = at, deletedAt = at)
        }
    }

    override suspend fun restoreFromTrash(id: ReminderId, changedAt: Instant) {
        updateExisting(id) { reminder ->
            reminder.copy(updatedAt = reminder.clamp(changedAt), deletedAt = null)
        }
    }

    override suspend fun delete(id: ReminderId) {
        reminderDao.deleteById(id.value)
    }

    override suspend fun setChecklistItemDone(
        id: ReminderId,
        itemId: ChecklistItemId,
        done: Boolean,
    ) {
        val changed = reminderDao.setChecklistItemDone(id.value, itemId.value, done)
        check(changed == 1) { "Checklist item ${itemId.value} does not exist" }
    }

    override suspend fun setEnabled(id: ReminderId, enabled: Boolean, changedAt: Instant) {
        updateExisting(id) { reminder ->
            reminder.copy(enabled = enabled, updatedAt = reminder.clamp(changedAt))
        }
    }

    override suspend fun recordTriggered(id: ReminderId, triggeredAt: Instant) {
        updateExisting(id) { reminder ->
            reminder.copy(lastTriggeredAt = triggeredAt, updatedAt = reminder.clamp(triggeredAt))
        }
    }

    override suspend fun snooze(id: ReminderId, until: Instant, changedAt: Instant) {
        require(until.isAfter(changedAt)) { "Snooze deadline must be in the future" }
        updateExisting(id) { reminder ->
            reminder.copy(
                enabled = true,
                status = ReminderStatus.SNOOZED,
                updatedAt = reminder.clamp(changedAt),
                snoozedUntil = until,
                dismissedAt = null,
            )
        }
    }

    override suspend fun dismiss(id: ReminderId, changedAt: Instant) {
        updateExisting(id) { reminder ->
            val at = reminder.clamp(changedAt)
            reminder.copy(
                enabled = false,
                status = ReminderStatus.DISMISSED,
                updatedAt = at,
                snoozedUntil = null,
                dismissedAt = at,
            )
        }
    }

    override suspend fun complete(id: ReminderId, changedAt: Instant) {
        updateExisting(id) { reminder ->
            reminder.copy(
                enabled = false,
                status = ReminderStatus.COMPLETED,
                updatedAt = reminder.clamp(changedAt),
                snoozedUntil = null,
                dismissedAt = null,
            )
        }
    }

    override suspend fun reopen(id: ReminderId, changedAt: Instant) {
        updateExisting(id) { reminder ->
            reminder.copy(
                enabled = true,
                status = ReminderStatus.PENDING,
                updatedAt = reminder.clamp(changedAt),
                snoozedUntil = null,
                dismissedAt = null,
            )
        }
    }

    private suspend fun updateExisting(id: ReminderId, transform: (Reminder) -> Reminder) {
        update(id, transform) ?: error("Reminder ${id.value} does not exist")
    }

    /** A wall-clock change must not produce an update that precedes creation. */
    private fun Reminder.clamp(instant: Instant): Instant = maxOf(instant, createdAt)

    /** One unreadable row must not take the whole list, boot restore, or an alert down with it. */
    private fun ReminderWithTriggers.toDomainOrNull(): Reminder? =
        runCatching { toDomain() }
            .onFailure { Log.w(TAG, "Skipping unreadable reminder ${reminder.id}", it) }
            .getOrNull()

    private companion object {
        const val TAG = "GeominderRepository"
    }
}
