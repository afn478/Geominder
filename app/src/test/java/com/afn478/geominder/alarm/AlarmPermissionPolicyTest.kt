package com.afn478.geominder.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmPermissionPolicyTest {
    @Test
    fun `exact alarms require no permission flow below API 31`() {
        assertEquals(
            ExactAlarmPermissionState.NOT_REQUIRED,
            AlarmPermissionPolicy.exactAlarmState(
                sdkInt = 30,
                platformCanSchedule = false,
            ),
        )
    }

    @Test
    fun `API 31 and 32 report granted automatic access`() {
        for (sdkInt in 31..32) {
            assertEquals(
                ExactAlarmPermissionState.GRANTED,
                AlarmPermissionPolicy.exactAlarmState(
                    sdkInt = sdkInt,
                    platformCanSchedule = true,
                ),
            )
        }
    }

    @Test
    fun `API 31 and 32 distinguish unexpectedly revoked automatic access`() {
        for (sdkInt in 31..32) {
            assertEquals(
                ExactAlarmPermissionState.AUTO_GRANTED_ACCESS_REVOKED,
                AlarmPermissionPolicy.exactAlarmState(
                    sdkInt = sdkInt,
                    platformCanSchedule = false,
                ),
            )
        }
    }

    @Test
    fun `API 33 and newer require explicit user action when denied`() {
        assertEquals(
            ExactAlarmPermissionState.USER_ACTION_REQUIRED,
            AlarmPermissionPolicy.exactAlarmState(
                sdkInt = 33,
                platformCanSchedule = false,
            ),
        )
        assertEquals(
            ExactAlarmPermissionState.GRANTED,
            AlarmPermissionPolicy.exactAlarmState(
                sdkInt = 36,
                platformCanSchedule = true,
            ),
        )
    }

    @Test
    fun `only API 33 and newer proactively prompt for exact alarm access`() {
        assertFalse(
            AlarmPermissionPolicy.shouldPromptForExactAlarmAccess(
                sdkInt = 32,
                state = ExactAlarmPermissionState.AUTO_GRANTED_ACCESS_REVOKED,
            ),
        )
        assertTrue(
            AlarmPermissionPolicy.shouldPromptForExactAlarmAccess(
                sdkInt = 33,
                state = ExactAlarmPermissionState.USER_ACTION_REQUIRED,
            ),
        )
        assertFalse(
            AlarmPermissionPolicy.shouldPromptForExactAlarmAccess(
                sdkInt = 36,
                state = ExactAlarmPermissionState.GRANTED,
            ),
        )
    }

    @Test
    fun `full screen special access starts on API 34`() {
        assertEquals(
            FullScreenIntentPermissionState.MANIFEST_PERMISSION_ONLY,
            AlarmPermissionPolicy.fullScreenIntentState(
                sdkInt = 33,
                platformCanUse = false,
            ),
        )
        assertEquals(
            FullScreenIntentPermissionState.USER_ACTION_REQUIRED,
            AlarmPermissionPolicy.fullScreenIntentState(
                sdkInt = 34,
                platformCanUse = false,
            ),
        )
        assertEquals(
            FullScreenIntentPermissionState.GRANTED,
            AlarmPermissionPolicy.fullScreenIntentState(
                sdkInt = 34,
                platformCanUse = true,
            ),
        )
    }

    @Test
    fun `either denied capability requires plain notification fallback`() {
        val exactDenied = AlarmDeliveryCapabilities(
            exactAlarm = ExactAlarmPermissionState.USER_ACTION_REQUIRED,
            fullScreenIntent = FullScreenIntentPermissionState.GRANTED,
        )
        val fullScreenDenied = AlarmDeliveryCapabilities(
            exactAlarm = ExactAlarmPermissionState.GRANTED,
            fullScreenIntent = FullScreenIntentPermissionState.USER_ACTION_REQUIRED,
        )
        val allGranted = AlarmDeliveryCapabilities(
            exactAlarm = ExactAlarmPermissionState.GRANTED,
            fullScreenIntent = FullScreenIntentPermissionState.GRANTED,
        )

        assertTrue(exactDenied.requiresPlainNotificationFallback)
        assertTrue(fullScreenDenied.requiresPlainNotificationFallback)
        assertFalse(allGranted.requiresPlainNotificationFallback)
    }
}
