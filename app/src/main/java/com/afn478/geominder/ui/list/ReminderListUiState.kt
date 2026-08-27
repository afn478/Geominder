package com.afn478.geominder.ui.list

import com.afn478.geominder.R
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.ReminderStatus
import com.afn478.geominder.domain.model.ReminderTag
import com.afn478.geominder.localization.UiText
import com.afn478.geominder.localization.resource
import com.afn478.geominder.settings.ReminderSortDirection
import com.afn478.geominder.settings.ReminderSortField
import com.afn478.geominder.settings.ReminderSortOrder
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class ReminderListUiState(
    val isLoading: Boolean = true,
    val items: List<ReminderListItem> = emptyList(),
    val busyReminderIds: Set<ReminderId> = emptySet(),
    val message: UiText? = null,
    val sortOrder: ReminderSortOrder = ReminderSortOrder.DEFAULT,
    val selectedTag: ReminderTag? = null,
    val showTrash: Boolean = false,
    val undoDeleteReminderId: ReminderId? = null,
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
    val tag: ReminderTag?,
    val enabled: Boolean,
    val canChangeEnabled: Boolean,
    val triggerKind: ReminderTriggerKind,
    val lifecycle: ReminderLifecyclePresentation,
    val isCompleted: Boolean,
)

enum class ReminderTriggerKind(val label: UiText) {
    TIME(UiText.resource(R.string.time_reminder)),
    LOCATION(UiText.resource(R.string.location_reminder)),
    TIME_AND_LOCATION(UiText.resource(R.string.time_and_location_reminder)),
}

data class ReminderLifecyclePresentation(
    val label: UiText,
    val supportingText: UiText,
    val isTerminal: Boolean,
)

/** Android-free presentation mapping so list behavior can be unit tested on the JVM. */
object ReminderListPresenter {
    fun present(
        reminders: List<Reminder>,
        zoneId: ZoneId,
        locale: Locale,
        sortOrder: ReminderSortOrder = ReminderSortOrder.DEFAULT,
        selectedTag: ReminderTag? = null,
        showDeleted: Boolean = false,
    ): List<ReminderListItem> {
        val dateTimeFormatter = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zoneId)

        return reminders
            .filter { reminder -> reminder.isDeleted == showDeleted }
            .filter { reminder -> selectedTag == null || reminder.tag == selectedTag }
            .sortedWith(reminderComparator(sortOrder, locale))
            .map { reminder -> reminder.toListItem(dateTimeFormatter) }
    }

    private fun reminderComparator(
        sortOrder: ReminderSortOrder,
        locale: Locale,
    ): Comparator<Reminder> {
        val titleCollator = Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
        }

        return Comparator { left, right ->
            comparePrimaryValue(left, right, sortOrder, titleCollator)
                .takeIf { it != 0 }
                ?: compareLifecycle(left, right)
                .takeIf { it != 0 }
                ?: compareTitles(left, right, titleCollator)
                .takeIf { it != 0 }
                ?: right.updatedAt.compareTo(left.updatedAt)
                .takeIf { it != 0 }
                ?: left.id.value.compareTo(right.id.value)
        }
    }

    private fun comparePrimaryValue(
        left: Reminder,
        right: Reminder,
        sortOrder: ReminderSortOrder,
        titleCollator: Collator,
    ): Int = when (sortOrder.field) {
        ReminderSortField.TITLE -> directed(
            titleCollator.compare(left.title, right.title),
            sortOrder.direction,
        )

        ReminderSortField.CREATION_DATE -> directed(
            left.createdAt.compareTo(right.createdAt),
            sortOrder.direction,
        )

        ReminderSortField.MODIFICATION_DATE -> directed(
            left.updatedAt.compareTo(right.updatedAt),
            sortOrder.direction,
        )

        ReminderSortField.DUE_DATE -> compareDueDates(
            left.timeTrigger?.exactAt,
            right.timeTrigger?.exactAt,
            sortOrder.direction,
        )
    }

    private fun compareDueDates(
        left: Instant?,
        right: Instant?,
        direction: ReminderSortDirection,
    ): Int {
        if (left == null || right == null) {
            return when {
                left == null && right == null -> 0
                left == null -> 1
                else -> -1
            }
        }
        return directed(left.compareTo(right), direction)
    }

    private fun directed(
        comparison: Int,
        direction: ReminderSortDirection,
    ): Int {
        val normalized = comparison.compareTo(0)
        return if (direction == ReminderSortDirection.ASCENDING) normalized else -normalized
    }

    // The selected sort remains primary; lifecycle only breaks ties to keep active items useful.
    private fun compareLifecycle(left: Reminder, right: Reminder): Int = when {
        left.isPending && !right.isPending -> -1
        !left.isPending && right.isPending -> 1
        else -> 0
    }

    private fun compareTitles(
        left: Reminder,
        right: Reminder,
        titleCollator: Collator,
    ): Int = titleCollator.compare(left.title, right.title).compareTo(0)

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
            tag = tag,
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
        if (isDeleted) {
            return ReminderLifecyclePresentation(
                label = UiText.resource(R.string.status_deleted),
                supportingText = UiText.resource(R.string.in_recycling_bin),
                isTerminal = true,
            )
        }
        if (!enabled) {
            val reason = when (status) {
                ReminderStatus.DISMISSED -> UiText.resource(R.string.dismissed_no_longer_scheduled)
                ReminderStatus.COMPLETED -> UiText.resource(R.string.completed_no_longer_scheduled)
                else -> UiText.resource(R.string.paused_triggers_not_registered)
            }
            return ReminderLifecyclePresentation(
                label = when (status) {
                    ReminderStatus.DISMISSED -> UiText.resource(R.string.status_dismissed)
                    ReminderStatus.COMPLETED -> UiText.resource(R.string.status_completed)
                    else -> UiText.resource(R.string.status_paused)
                },
                supportingText = reason,
                isTerminal = status.isTerminal,
            )
        }

        return when (status) {
            ReminderStatus.PENDING -> ReminderLifecyclePresentation(
                label = UiText.resource(R.string.status_active),
                supportingText = UiText.resource(R.string.waiting_for_trigger),
                isTerminal = false,
            )

            ReminderStatus.SNOOZED -> ReminderLifecyclePresentation(
                label = UiText.resource(R.string.status_snoozed),
                supportingText = snoozedUntil
                    ?.let { UiText.resource(R.string.resumes_at, formatter.format(it)) }
                    ?: UiText.resource(R.string.waiting_for_snooze_time),
                isTerminal = false,
            )

            ReminderStatus.DISMISSED -> ReminderLifecyclePresentation(
                label = UiText.resource(R.string.status_dismissed),
                supportingText = UiText.resource(R.string.no_longer_scheduled),
                isTerminal = true,
            )

            ReminderStatus.COMPLETED -> ReminderLifecyclePresentation(
                label = UiText.resource(R.string.status_completed),
                supportingText = UiText.resource(R.string.no_longer_scheduled),
                isTerminal = true,
            )
        }
    }

    private val ReminderStatus.isTerminal: Boolean
        get() = this == ReminderStatus.DISMISSED || this == ReminderStatus.COMPLETED

    private fun formatCoordinates(latitude: Double, longitude: Double): String =
        String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude)

}
