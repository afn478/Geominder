package com.afn478.geominder.data.repository

import com.afn478.geominder.data.local.ReminderDao
import com.afn478.geominder.data.mapper.toDomain
import com.afn478.geominder.data.mapper.toEntities
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
        reminderDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observe(id: ReminderId): Flow<Reminder?> =
        reminderDao.observeById(id.value).map { it?.toDomain() }

    override suspend fun get(id: ReminderId): Reminder? =
        reminderDao.getById(id.value)?.toDomain()

    override suspend fun getPending(): List<Reminder> =
        reminderDao.getPending().map { it.toDomain() }

    override suspend fun save(reminder: Reminder) {
        val entities = reminder.toEntities()
        reminderDao.replace(entities.reminder, entities.timeTrigger, entities.geoTrigger)
    }

    override suspend fun moveToTrash(id: ReminderId, changedAt: Instant) {
        update(id) { reminder ->
            reminder.copy(updatedAt = changedAt, deletedAt = changedAt)
        }
    }

    override suspend fun restoreFromTrash(id: ReminderId, changedAt: Instant) {
        update(id) { reminder ->
            reminder.copy(updatedAt = changedAt, deletedAt = null)
        }
    }

    override suspend fun delete(id: ReminderId) {
        reminderDao.deleteById(id.value)
    }

    override suspend fun setEnabled(id: ReminderId, enabled: Boolean, changedAt: Instant) {
        update(id) { reminder -> reminder.copy(enabled = enabled, updatedAt = changedAt) }
    }

    override suspend fun recordTriggered(id: ReminderId, triggeredAt: Instant) {
        update(id) { reminder ->
            reminder.copy(lastTriggeredAt = triggeredAt, updatedAt = triggeredAt)
        }
    }

    override suspend fun snooze(id: ReminderId, until: Instant, changedAt: Instant) {
        require(until.isAfter(changedAt)) { "Snooze deadline must be in the future" }
        update(id) { reminder ->
            reminder.copy(
                enabled = true,
                status = ReminderStatus.SNOOZED,
                updatedAt = changedAt,
                snoozedUntil = until,
                dismissedAt = null,
            )
        }
    }

    override suspend fun dismiss(id: ReminderId, changedAt: Instant) {
        update(id) { reminder ->
            reminder.copy(
                enabled = false,
                status = ReminderStatus.DISMISSED,
                updatedAt = changedAt,
                snoozedUntil = null,
                dismissedAt = changedAt,
            )
        }
    }

    override suspend fun complete(id: ReminderId, changedAt: Instant) {
        update(id) { reminder ->
            reminder.copy(
                enabled = false,
                status = ReminderStatus.COMPLETED,
                updatedAt = changedAt,
                snoozedUntil = null,
                dismissedAt = null,
            )
        }
    }

    override suspend fun reopen(id: ReminderId, changedAt: Instant) {
        update(id) { reminder ->
            reminder.copy(
                enabled = true,
                status = ReminderStatus.PENDING,
                updatedAt = changedAt,
                snoozedUntil = null,
                dismissedAt = null,
            )
        }
    }

    private suspend fun update(id: ReminderId, transform: (Reminder) -> Reminder) {
        val current = get(id) ?: error("Reminder ${id.value} does not exist")
        save(transform(current))
    }
}
