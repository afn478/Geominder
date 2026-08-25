package com.geominder.reminder.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSchedulingPolicyTest {
    @Test
    fun `denied exact access schedules an inexact plain notification fallback`() {
        val decision = AlarmSchedulingPolicy.forCapabilities(
            AlarmDeliveryCapabilities(
                exactAlarm = ExactAlarmPermissionState.USER_ACTION_REQUIRED,
                fullScreenIntent = FullScreenIntentPermissionState.GRANTED,
            ),
        )

        assertTrue(decision is AlarmSchedulingDecision.InexactFallback)
        decision as AlarmSchedulingDecision.InexactFallback
        assertEquals(AlarmFallbackReason.EXACT_ALARM_ACCESS_DENIED, decision.reason)
        assertEquals(AlarmDeliveryMode.PLAIN_NOTIFICATION, decision.deliveryMode)
    }

    @Test
    fun `full screen denial keeps exact scheduling full screen eligible`() {
        val decision = AlarmSchedulingPolicy.forCapabilities(
            AlarmDeliveryCapabilities(
                exactAlarm = ExactAlarmPermissionState.GRANTED,
                fullScreenIntent = FullScreenIntentPermissionState.USER_ACTION_REQUIRED,
            ),
        )

        assertEquals(
            AlarmSchedulingDecision.Exact(AlarmDeliveryMode.FULL_SCREEN_ELIGIBLE),
            decision,
        )
    }

    @Test
    fun `all capabilities allow an exact full screen eligible event`() {
        val decision = AlarmSchedulingPolicy.forCapabilities(
            AlarmDeliveryCapabilities(
                exactAlarm = ExactAlarmPermissionState.GRANTED,
                fullScreenIntent = FullScreenIntentPermissionState.GRANTED,
            ),
        )

        assertEquals(
            AlarmSchedulingDecision.Exact(AlarmDeliveryMode.FULL_SCREEN_ELIGIBLE),
            decision,
        )
    }

    @Test
    fun `exact scheduling security exception falls back to inexact plain delivery`() {
        val decision = AlarmSchedulingPolicy.afterExactSchedulingSecurityException()

        assertEquals(
            AlarmFallbackReason.EXACT_SCHEDULING_SECURITY_EXCEPTION,
            decision.reason,
        )
        assertEquals(AlarmDeliveryMode.PLAIN_NOTIFICATION, decision.deliveryMode)
    }
}
