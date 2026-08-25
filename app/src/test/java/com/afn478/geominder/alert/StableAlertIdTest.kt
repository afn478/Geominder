package com.afn478.geominder.alert

import com.afn478.geominder.domain.model.ReminderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StableAlertIdTest {
    private val reminderId = ReminderId("persistent-reminder-id")

    @Test
    fun `identifiers remain stable for the same reminder`() {
        assertEquals(
            StableAlertId.notificationId(reminderId),
            StableAlertId.notificationId(ReminderId(reminderId.value)),
        )
        assertEquals(
            StableAlertId.fullScreenRequestCode(reminderId),
            StableAlertId.fullScreenRequestCode(ReminderId(reminderId.value)),
        )
        assertEquals(
            StableAlertId.contentRequestCode(reminderId),
            StableAlertId.contentRequestCode(ReminderId(reminderId.value)),
        )
    }

    @Test
    fun `actions use independent pending intent identities`() {
        val snooze = StableAlertId.actionRequestCode(reminderId, AlertAction.SNOOZE)
        val dismiss = StableAlertId.actionRequestCode(reminderId, AlertAction.DISMISS)
        val done = StableAlertId.actionRequestCode(reminderId, AlertAction.DONE)

        assertNotEquals(snooze, dismiss)
        assertNotEquals(snooze, done)
        assertNotEquals(dismiss, done)
        assertTrue(snooze >= 0)
        assertTrue(dismiss >= 0)
        assertTrue(done >= 0)
    }

    @Test
    fun `done intent action round trips`() {
        assertEquals(AlertAction.DONE, AlertAction.fromIntentAction(AlertContract.ACTION_DONE))
    }
}
