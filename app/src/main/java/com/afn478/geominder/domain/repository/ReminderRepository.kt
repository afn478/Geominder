package com.afn478.geominder.domain.repository

import com.afn478.geominder.domain.model.ChecklistItemId
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.ReminderStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ReminderRepository {
    fun observeAll(): Flow<List<Reminder>>

    fun observe(id: ReminderId): Flow<Reminder?>

    suspend fun get(id: ReminderId): Reminder?

    /** Snapshot used by boot restoration and schedulers. */
    suspend fun getPending(): List<Reminder>

    suspend fun save(reminder: Reminder)

    /**
     * Re-reads [id] and persists [transform]'s result. Returning null from [transform] leaves the
     * reminder untouched. Implementations backed by a database run this in one transaction so a
     * slow caller cannot overwrite (or resurrect) a reminder that changed in the meantime.
     */
    suspend fun update(id: ReminderId, transform: (Reminder) -> Reminder?): Reminder? {
        val current = get(id) ?: return null
        val updated = transform(current) ?: return null
        save(updated)
        return updated
    }

    suspend fun moveToTrash(id: ReminderId, changedAt: Instant) {
        val current = get(id) ?: error("Reminder ${id.value} does not exist")
        save(current.copy(updatedAt = changedAt, deletedAt = changedAt))
    }

    suspend fun restoreFromTrash(id: ReminderId, changedAt: Instant) {
        val current = get(id) ?: error("Reminder ${id.value} does not exist")
        save(current.copy(updatedAt = changedAt, deletedAt = null))
    }

    suspend fun delete(id: ReminderId)

    /**
     * Ticks or restores one checklist item. Deliberately leaves [Reminder.updatedAt], the
     * lifecycle, and every trigger untouched so list ordering and scheduling stay stable.
     */
    suspend fun setChecklistItemDone(id: ReminderId, itemId: ChecklistItemId, done: Boolean) {
        val current = get(id) ?: error("Reminder ${id.value} does not exist")
        require(current.checklist.any { it.id == itemId }) {
            "Checklist item ${itemId.value} does not exist"
        }
        save(
            current.copy(
                checklist = current.checklist.map { item ->
                    if (item.id == itemId) item.copy(done = done) else item
                },
            ),
        )
    }

    suspend fun setEnabled(id: ReminderId, enabled: Boolean, changedAt: Instant)

    suspend fun recordTriggered(id: ReminderId, triggeredAt: Instant)

    suspend fun snooze(id: ReminderId, until: Instant, changedAt: Instant)

    suspend fun dismiss(id: ReminderId, changedAt: Instant)

    suspend fun complete(id: ReminderId, changedAt: Instant)

    suspend fun reopen(id: ReminderId, changedAt: Instant) {
        val current = get(id) ?: error("Reminder ${id.value} does not exist")
        save(
            current.copy(
                enabled = true,
                status = ReminderStatus.PENDING,
                updatedAt = changedAt,
                snoozedUntil = null,
                dismissedAt = null,
            ),
        )
    }
}
