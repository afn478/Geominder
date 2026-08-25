package com.geominder.reminder.ui.list

import com.geominder.reminder.domain.model.Reminder
import com.geominder.reminder.domain.model.ReminderId
import com.geominder.reminder.domain.model.ReminderStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class ReminderListUiState(
    val isLoading: Boolean = true,
    val items: List<ReminderListItem> = emptyList(),
    val busyReminderIds: Set<ReminderId> = emptySet(),
    val deleteCandidate: ReminderListItem? = null,
    val message: String? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && items.isEmpty()
}

data class ReminderListItem(
    val id: ReminderId,
    val primaryText: String,
    val timeText: String?,
    val locationText: String?,
    // Kept for the existing detail/delete copy and callback contract.
    val title: String,
    val body: String,
    val enabled: Boolean,
    val canChangeEnabled: Boolean,
    val triggerKind: ReminderTriggerKind,
    val lifecycle: ReminderLifecyclePresentation,
    val isCompleted: Boolean,
)

enum class ReminderTriggerKind(val label: String) {
    TIME("Time reminder"),
    LOCATION("Location reminder"),
    TIME_AND_LOCATION("Time and location reminder"),
}

data class ReminderLifecyclePresentation(
    val label: String,
    val supportingText: String,
    val isTerminal: Boolean,
)

/** Android-free presentation mapping so list behavior can be unit tested on the JVM. */
object ReminderListPresenter {
    fun present(
        reminders: List<Reminder>,
        zoneId: ZoneId,
        locale: Locale,
    ): List<ReminderListItem> {
        val dateTimeFormatter = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zoneId)

        return reminders
            .sortedWith(
                compareByDescending<Reminder> { it.isPending }
                    .thenByDescending { it.updatedAt },
            )
            .map { reminder -> reminder.toListItem(dateTimeFormatter) }
    }

    private fun Reminder.toListItem(formatter: DateTimeFormatter): ReminderListItem {
        val place = geoTrigger?.let { trigger ->
            (trigger.label ?: formatCoordinates(trigger.latitude, trigger.longitude))
                .removePrefix("near ")
        }
        val kind = when {
            timeTrigger != null && geoTrigger != null -> ReminderTriggerKind.TIME_AND_LOCATION
            timeTrigger != null -> ReminderTriggerKind.TIME
            else -> ReminderTriggerKind.LOCATION
        }

        val lifecycle = lifecyclePresentation(formatter)
        return ReminderListItem(
            id = id,
            primaryText = sourceText.lineSequence().firstOrNull()?.trim().orEmpty()
                .ifBlank { title },
            timeText = timeTrigger?.let { formatter.format(it.exactAt) },
            locationText = place,
            title = title,
            body = text,
            enabled = enabled,
            canChangeEnabled = !lifecycle.isTerminal,
            triggerKind = kind,
            lifecycle = lifecycle,
            isCompleted = status == ReminderStatus.COMPLETED,
        )
    }

    private fun Reminder.lifecyclePresentation(
        formatter: DateTimeFormatter,
    ): ReminderLifecyclePresentation {
        if (!enabled) {
            val reason = when (status) {
                ReminderStatus.DISMISSED -> "Dismissed and no longer scheduled"
                ReminderStatus.COMPLETED -> "Completed and no longer scheduled"
                else -> "Paused; alarms and locations are not registered"
            }
            return ReminderLifecyclePresentation(
                label = when (status) {
                    ReminderStatus.DISMISSED -> "Dismissed"
                    ReminderStatus.COMPLETED -> "Completed"
                    else -> "Paused"
                },
                supportingText = reason,
                isTerminal = status.isTerminal,
            )
        }

        return when (status) {
            ReminderStatus.PENDING -> ReminderLifecyclePresentation(
                label = "Active",
                supportingText = "Waiting for its trigger",
                isTerminal = false,
            )

            ReminderStatus.SNOOZED -> ReminderLifecyclePresentation(
                label = "Snoozed",
                supportingText = snoozedUntil
                    ?.let { "Resumes ${formatter.format(it)}" }
                    ?: "Waiting for its snooze time",
                isTerminal = false,
            )

            ReminderStatus.DISMISSED -> ReminderLifecyclePresentation(
                label = "Dismissed",
                supportingText = "No longer scheduled",
                isTerminal = true,
            )

            ReminderStatus.COMPLETED -> ReminderLifecyclePresentation(
                label = "Completed",
                supportingText = "No longer scheduled",
                isTerminal = true,
            )
        }
    }

    private val ReminderStatus.isTerminal: Boolean
        get() = this == ReminderStatus.DISMISSED || this == ReminderStatus.COMPLETED

    private fun formatCoordinates(latitude: Double, longitude: Double): String =
        String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude)

}
