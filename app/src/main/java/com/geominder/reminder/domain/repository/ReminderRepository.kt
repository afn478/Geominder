package com.geominder.reminder.domain.repository

import com.geominder.reminder.domain.model.Reminder
import com.geominder.reminder.domain.model.ReminderId
import com.geominder.reminder.domain.model.ReminderStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ReminderRepository {
    fun observeAll(): Flow<List<Reminder>>

    fun observe(id: ReminderId): Flow<Reminder?>

    suspend fun get(id: ReminderId): Reminder?

    /** Snapshot used by boot restoration and schedulers. */
    suspend fun getPending(): List<Reminder>

    suspend fun save(reminder: Reminder)

    suspend fun delete(id: ReminderId)

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
