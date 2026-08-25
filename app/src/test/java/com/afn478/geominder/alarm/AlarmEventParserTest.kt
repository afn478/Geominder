package com.afn478.geominder.alarm

import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.TriggerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmEventParserTest {
    @Test
    fun `parses full screen eligible delivery mode`() {
        val event = AlarmEventParser.parse(
            expectedKind = AlarmKind.TIME,
            action = AlarmContract.ACTION_TIME_REMINDER,
            reminderIdValue = "reminder-17",
            triggerIdValue = "trigger-4",
            deliveryModeValue = AlarmDeliveryMode.FULL_SCREEN_ELIGIBLE.wireValue,
        )

        assertEquals(
            AlarmEvent(
                kind = AlarmKind.TIME,
                reminderId = ReminderId("reminder-17"),
                triggerId = TriggerId("trigger-4"),
                deliveryMode = AlarmDeliveryMode.FULL_SCREEN_ELIGIBLE,
            ),
            event,
        )
    }

    @Test
    fun `missing or unknown delivery mode defaults to plain notification`() {
        for (wireValue in listOf(null, "", "unexpected")) {
            val event = AlarmEventParser.parse(
                expectedKind = AlarmKind.SNOOZE,
                action = AlarmContract.ACTION_SNOOZE,
                reminderIdValue = "reminder-17",
                triggerIdValue = null,
                deliveryModeValue = wireValue,
            )

            assertEquals(AlarmDeliveryMode.PLAIN_NOTIFICATION, event?.deliveryMode)
        }
    }

    @Test
    fun `rejects mismatched action or missing reminder identity`() {
        assertNull(
            AlarmEventParser.parse(
                expectedKind = AlarmKind.TIME,
                action = AlarmContract.ACTION_SNOOZE,
                reminderIdValue = "reminder-17",
                triggerIdValue = null,
                deliveryModeValue = AlarmDeliveryMode.PLAIN_NOTIFICATION.wireValue,
            ),
        )
        assertNull(
            AlarmEventParser.parse(
                expectedKind = AlarmKind.TIME,
                action = AlarmContract.ACTION_TIME_REMINDER,
                reminderIdValue = " ",
                triggerIdValue = null,
                deliveryModeValue = AlarmDeliveryMode.PLAIN_NOTIFICATION.wireValue,
            ),
        )
    }
}
