package com.afn478.geominder.ui.list

import com.afn478.geominder.domain.model.GeoTrigger
import com.afn478.geominder.domain.model.Reminder
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.ReminderStatus
import com.afn478.geominder.domain.model.TimeTrigger
import com.afn478.geominder.settings.ReminderSortDirection
import com.afn478.geominder.settings.ReminderSortField
import com.afn478.geominder.settings.ReminderSortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class ReminderListPresenterTest {
    @Test
    fun `default sort uses due date ascending and keeps undated reminders last`() {
        val dueLater = reminder(
            id = ReminderId("later"),
            title = "Later",
            timeTrigger = TimeTrigger(exactAt = NOW.plusSeconds(2_000)),
        )
        val dueSooner = reminder(
            id = ReminderId("sooner"),
            title = "Sooner",
            timeTrigger = TimeTrigger(exactAt = NOW.plusSeconds(1_000)),
        )
        val undated = reminder(
            id = ReminderId("undated"),
            title = "Undated",
            timeTrigger = null,
            geoTrigger = GeoTrigger(
                latitude = 40.7128,
                longitude = -74.0060,
                radiusMeters = 100.0,
            ),
        )

        val ids = ReminderListPresenter.present(
            reminders = listOf(undated, dueLater, dueSooner),
            zoneId = ZoneOffset.UTC,
            locale = Locale.US,
        ).map(ReminderListItem::id)

        assertEquals(listOf(dueSooner.id, dueLater.id, undated.id), ids)

        val descendingIds = ReminderListPresenter.present(
            reminders = listOf(undated, dueLater, dueSooner),
            zoneId = ZoneOffset.UTC,
            locale = Locale.US,
            sortOrder = ReminderSortOrder(
                field = ReminderSortField.DUE_DATE,
                direction = ReminderSortDirection.DESCENDING,
            ),
        ).map(ReminderListItem::id)

        assertEquals(listOf(dueLater.id, dueSooner.id, undated.id), descendingIds)
    }

    @Test
    fun `title sort honors ascending and descending directions`() {
        val alpha = reminder(id = ReminderId("alpha"), title = "Alpha")
        val beta = reminder(id = ReminderId("beta"), title = "Beta")
        val gamma = reminder(id = ReminderId("gamma"), title = "Gamma")
        val reminders = listOf(gamma, alpha, beta)

        fun ids(direction: ReminderSortDirection) = ReminderListPresenter.present(
            reminders = reminders,
            zoneId = ZoneOffset.UTC,
            locale = Locale.US,
            sortOrder = ReminderSortOrder(ReminderSortField.TITLE, direction),
        ).map(ReminderListItem::id)

        assertEquals(listOf(alpha.id, beta.id, gamma.id), ids(ReminderSortDirection.ASCENDING))
        assertEquals(listOf(gamma.id, beta.id, alpha.id), ids(ReminderSortDirection.DESCENDING))
    }

    @Test
    fun `creation and modification date sorts honor both directions`() {
        val oldest = reminder(
            id = ReminderId("oldest"),
            title = "Oldest",
            createdAt = NOW.minusSeconds(9_000),
            updatedAt = NOW.minusSeconds(1_000),
        )
        val middle = reminder(
            id = ReminderId("middle"),
            title = "Middle",
            createdAt = NOW.minusSeconds(8_000),
            updatedAt = NOW.minusSeconds(2_000),
        )
        val newest = reminder(
            id = ReminderId("newest"),
            title = "Newest",
            createdAt = NOW.minusSeconds(7_000),
            updatedAt = NOW.minusSeconds(3_000),
        )
        val reminders = listOf(newest, oldest, middle)

        fun ids(
            field: ReminderSortField,
            direction: ReminderSortDirection,
        ) = ReminderListPresenter.present(
            reminders = reminders,
            zoneId = ZoneOffset.UTC,
            locale = Locale.US,
            sortOrder = ReminderSortOrder(field, direction),
        ).map(ReminderListItem::id)

        assertEquals(
            listOf(oldest.id, middle.id, newest.id),
            ids(ReminderSortField.CREATION_DATE, ReminderSortDirection.ASCENDING),
        )
        assertEquals(
            listOf(newest.id, middle.id, oldest.id),
            ids(ReminderSortField.CREATION_DATE, ReminderSortDirection.DESCENDING),
        )
        assertEquals(
            listOf(newest.id, middle.id, oldest.id),
            ids(ReminderSortField.MODIFICATION_DATE, ReminderSortDirection.ASCENDING),
        )
        assertEquals(
            listOf(oldest.id, middle.id, newest.id),
            ids(ReminderSortField.MODIFICATION_DATE, ReminderSortDirection.DESCENDING),
        )
    }

    @Test
    fun `maps combined trigger metadata and active lifecycle`() {
        val reminder = reminder(
            timeTrigger = TimeTrigger(exactAt = Instant.parse("2026-08-25T08:30:00Z")),
            geoTrigger = GeoTrigger(
                latitude = 40.7505,
                longitude = -73.9934,
                radiusMeters = 250.0,
                label = "Penn Station",
                activeFrom = Instant.parse("2026-08-25T07:00:00Z"),
            ),
        )

        val item = ReminderListPresenter.present(
            reminders = listOf(reminder),
            zoneId = ZoneOffset.UTC,
            locale = Locale.US,
        ).single()

        assertEquals(ReminderTriggerKind.TIME_AND_LOCATION, item.triggerKind)
        assertEquals("Time and location reminder", item.triggerKind.label)
        assertEquals(
            "Aug 25, 2026, 8:30 AM",
            item.timeText
                ?.replace('\u00A0', ' ')
                ?.replace('\u202F', ' '),
        )
        assertEquals("Penn Station", item.locationText)
        assertEquals("Active", item.lifecycle.label)
        assertFalse(item.lifecycle.isTerminal)
        assertFalse(item.isCompleted)
    }

    @Test
    fun `maps coordinate fallback and kilometers for an unlabeled location`() {
        val item = ReminderListPresenter.present(
            reminders = listOf(
                reminder(
                    timeTrigger = null,
                    geoTrigger = GeoTrigger(
                        latitude = 40.7128,
                        longitude = -74.0060,
                        radiusMeters = 1_500.0,
                    ),
                ),
            ),
            zoneId = ZoneOffset.UTC,
            locale = Locale.US,
        ).single()

        assertEquals(ReminderTriggerKind.LOCATION, item.triggerKind)
        assertEquals("40.71280, -74.00600", item.locationText)
    }

    @Test
    fun `distance is localized while coordinates remain invariant`() {
        val reminder = reminder(
            timeTrigger = null,
            geoTrigger = GeoTrigger(
                latitude = 40.7128,
                longitude = -74.0060,
                radiusMeters = 1_500.0,
            ),
        )
        val us = ReminderListPresenter.present(listOf(reminder), ZoneOffset.UTC, Locale.US).single()
        val german = ReminderListPresenter.present(listOf(reminder), ZoneOffset.UTC, Locale.GERMANY).single()

        assertEquals("40.71280, -74.00600", us.locationText)
        assertEquals("40.71280, -74.00600", german.locationText)
    }

    @Test
    fun `disabled and terminal lifecycle states stay distinct`() {
        val paused = reminder(enabled = false)
        val dismissed = reminder(
            id = ReminderId("dismissed"),
            enabled = false,
            status = ReminderStatus.DISMISSED,
            dismissedAt = NOW,
        )
        val completed = reminder(
            id = ReminderId("completed"),
            enabled = false,
            status = ReminderStatus.COMPLETED,
        )

        val items = ReminderListPresenter.present(
            reminders = listOf(paused, dismissed, completed),
            zoneId = ZoneOffset.UTC,
            locale = Locale.US,
        ).associateBy(ReminderListItem::id)

        assertEquals("Paused", items.getValue(paused.id).lifecycle.label)
        assertFalse(items.getValue(paused.id).lifecycle.isTerminal)
        assertTrue(items.getValue(paused.id).canChangeEnabled)
        assertEquals("Dismissed", items.getValue(dismissed.id).lifecycle.label)
        assertTrue(items.getValue(dismissed.id).lifecycle.isTerminal)
        assertFalse(items.getValue(dismissed.id).canChangeEnabled)
        assertEquals("Completed", items.getValue(completed.id).lifecycle.label)
        assertTrue(items.getValue(completed.id).lifecycle.isTerminal)
        assertFalse(items.getValue(completed.id).canChangeEnabled)
        assertTrue(items.getValue(completed.id).isCompleted)
    }

    @Test
    fun `snoozed lifecycle exposes its resume time`() {
        val item = ReminderListPresenter.present(
            reminders = listOf(
                reminder(
                    status = ReminderStatus.SNOOZED,
                    snoozedUntil = Instant.parse("2026-08-25T09:45:00Z"),
                ),
            ),
            zoneId = ZoneOffset.UTC,
            locale = Locale.US,
        ).single()

        assertEquals("Snoozed", item.lifecycle.label)
        assertTrue(item.lifecycle.supportingText.startsWith("Resumes "))
    }

    private fun reminder(
        id: ReminderId = ReminderId("reminder"),
        title: String = "Pick up groceries",
        enabled: Boolean = true,
        status: ReminderStatus = ReminderStatus.PENDING,
        timeTrigger: TimeTrigger? = TimeTrigger(exactAt = Instant.parse("2026-08-25T08:30:00Z")),
        geoTrigger: GeoTrigger? = null,
        createdAt: Instant = NOW,
        updatedAt: Instant = NOW,
        snoozedUntil: Instant? = null,
        dismissedAt: Instant? = null,
    ) = Reminder(
        id = id,
        sourceText = title,
        title = title,
        text = "Milk and bread",
        enabled = enabled,
        status = status,
        timeTrigger = timeTrigger,
        geoTrigger = geoTrigger,
        createdAt = createdAt,
        updatedAt = updatedAt,
        snoozedUntil = snoozedUntil,
        dismissedAt = dismissedAt,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-24T12:00:00Z")
    }
}
