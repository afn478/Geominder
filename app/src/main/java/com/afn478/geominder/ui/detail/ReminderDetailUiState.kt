package com.afn478.geominder.ui.detail

import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.text.NumberFormat
import java.util.Locale

/** Read-only, Android-free presentation state for a single reminder. */
data class ReminderDetailUiState(
    val isLoading: Boolean = true,
    val isNotFound: Boolean = false,
    val reminder: Reminder? = null,
    val sourceText: String? = null,
    val title: String? = null,
    val text: String? = null,
    val lifecycleLabel: String? = null,
    val timeText: String? = null,
    val geoCoordinates: String? = null,
    val geoLabel: String? = null,
    val geoRadius: String? = null,
    val geoActiveFrom: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val triggeredAt: String? = null,
    val snoozedUntil: String? = null,
    val dismissedAt: String? = null,
) {
    companion object {
        fun loading() = ReminderDetailUiState()

        fun notFound() = ReminderDetailUiState(isLoading = false, isNotFound = true)

        fun present(
            reminder: Reminder,
            zoneId: ZoneId,
            locale: Locale,
        ): ReminderDetailUiState {
            val formatter = DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(zoneId)
            val geo = reminder.geoTrigger
            val status = when (reminder.status) {
                ReminderStatus.PENDING -> if (reminder.enabled) "Active" else "Paused"
                ReminderStatus.SNOOZED -> "Snoozed"
                ReminderStatus.DISMISSED -> "Dismissed"
                ReminderStatus.COMPLETED -> "Completed"
            }
            return ReminderDetailUiState(
                isLoading = false,
                reminder = reminder,
                sourceText = reminder.sourceText,
                title = reminder.title,
                text = reminder.text,
                lifecycleLabel = if (reminder.enabled) status else "$status · disabled",
                timeText = reminder.timeTrigger?.let { formatter.format(it.exactAt) },
                geoCoordinates = geo?.let { "%.5f, %.5f".format(Locale.ROOT, it.latitude, it.longitude) },
                geoLabel = geo?.label,
                geoRadius = geo?.let { formatDistance(it.radiusMeters, locale) },
                geoActiveFrom = geo?.activeFrom?.let(formatter::format),
                createdAt = formatter.format(reminder.createdAt),
                updatedAt = formatter.format(reminder.updatedAt),
                triggeredAt = reminder.lastTriggeredAt?.let(formatter::format),
                snoozedUntil = reminder.snoozedUntil?.let(formatter::format),
                dismissedAt = reminder.dismissedAt?.let(formatter::format),
            )
        }

        private fun formatDistance(meters: Double, locale: Locale): String {
            val value = if (meters >= 1_000.0) meters / 1_000.0 else meters
            val unit = if (meters >= 1_000.0) "km" else "m"
            val number = NumberFormat.getNumberInstance(locale).apply {
                maximumFractionDigits = 1
                minimumFractionDigits = 0
            }.format(value)
            return "$number $unit"
        }
    }
}
