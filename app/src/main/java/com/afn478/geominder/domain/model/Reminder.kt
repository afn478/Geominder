package com.afn478.geominder.domain.model

import java.time.Instant

/**
 * Domain aggregate shared by every feature. A reminder may carry a time trigger, a geo
 * trigger, or both; at least one is required. All timestamps are absolute UTC instants.
 */
data class Reminder(
    val id: ReminderId = ReminderId.create(),
    val sourceText: String,
    val title: String,
    val text: String,
    val enabled: Boolean = true,
    val status: ReminderStatus = ReminderStatus.PENDING,
    val timeTrigger: TimeTrigger? = null,
    val geoTrigger: GeoTrigger? = null,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    val lastTriggeredAt: Instant? = null,
    val snoozedUntil: Instant? = null,
    val dismissedAt: Instant? = null,
) {
    init {
        require(title.isNotBlank()) { "Title must not be blank" }
        require(timeTrigger != null || geoTrigger != null) {
            "A reminder must have at least one trigger"
        }
        require(!updatedAt.isBefore(createdAt)) { "Updated time cannot precede creation time" }
        require(status != ReminderStatus.SNOOZED || snoozedUntil != null) {
            "A snoozed reminder must have a snooze deadline"
        }
        require(status != ReminderStatus.DISMISSED || dismissedAt != null) {
            "A dismissed reminder must have a dismissal timestamp"
        }
    }

    /** True when downstream schedulers should keep registrations for this reminder. */
    val isPending: Boolean
        get() = enabled && status in setOf(ReminderStatus.PENDING, ReminderStatus.SNOOZED)
}
