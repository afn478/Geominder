package com.afn478.geominder.settings

import com.afn478.geominder.R
import com.afn478.geominder.alarm.ExactAlarmPermissionState
import com.afn478.geominder.alarm.FullScreenIntentPermissionState
import com.afn478.geominder.localization.resourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPermissionPolicyTest {
    @Test
    fun `exact alarm has no action below API 31`() {
        val item = items(30).first { it.id == SettingsPermissionId.EXACT_ALARM }

        assertEquals(R.string.permission_not_required, item.status.resourceId())
        assertNull(item.action)
    }

    @Test
    fun `API 31 and 32 provide access shortcut without proactive request`() {
        for (sdk in 31..32) {
            val item = items(
                sdk,
                exactAlarm = ExactAlarmPermissionState.AUTO_GRANTED_ACCESS_REVOKED,
            ).first { it.id == SettingsPermissionId.EXACT_ALARM }

            assertEquals(SettingsPermissionAction.OPEN_EXACT_ALARM_SETTINGS, item.action)
            assertFalse(item.shouldProactivelyRequest)
        }
    }

    @Test
    fun `API 33 and newer explicitly request exact alarm access`() {
        val item = items(
            33,
            exactAlarm = ExactAlarmPermissionState.USER_ACTION_REQUIRED,
        ).first { it.id == SettingsPermissionId.EXACT_ALARM }

        assertEquals(SettingsPermissionAction.OPEN_EXACT_ALARM_SETTINGS, item.action)
        assertTrue(item.shouldProactivelyRequest)
    }

    @Test
    fun `full screen intent status and shortcut only appear from API 34`() {
        assertFalse(items(33).any { it.id == SettingsPermissionId.FULL_SCREEN_INTENT })

        val denied = items(
            34,
            fullScreenIntent = FullScreenIntentPermissionState.USER_ACTION_REQUIRED,
        ).first { it.id == SettingsPermissionId.FULL_SCREEN_INTENT }
        assertEquals(SettingsPermissionAction.OPEN_FULL_SCREEN_INTENT_SETTINGS, denied.action)
    }

    @Test
    fun `background location uses runtime request on 29 and app settings afterwards`() {
        val api29 = items(29, fineLocationGranted = true)
            .first { it.id == SettingsPermissionId.BACKGROUND_LOCATION }
        val api30 = items(30, fineLocationGranted = true)
            .first { it.id == SettingsPermissionId.BACKGROUND_LOCATION }

        assertEquals(SettingsPermissionAction.REQUEST_BACKGROUND_LOCATION, api29.action)
        assertEquals(SettingsPermissionAction.OPEN_APP_PERMISSION_SETTINGS, api30.action)
    }

    @Test
    fun `notification runtime permission only appears where applicable`() {
        assertFalse(items(32).any { it.id == SettingsPermissionId.NOTIFICATIONS })
        assertEquals(
            SettingsPermissionAction.REQUEST_NOTIFICATIONS,
            items(33).first { it.id == SettingsPermissionId.NOTIFICATIONS }.action,
        )
    }

    private fun items(
        sdkInt: Int,
        exactAlarm: ExactAlarmPermissionState = ExactAlarmPermissionState.GRANTED,
        fullScreenIntent: FullScreenIntentPermissionState =
            FullScreenIntentPermissionState.GRANTED,
        fineLocationGranted: Boolean = false,
    ) = SettingsPermissionPolicy.items(
        SettingsPermissionSnapshot(
            sdkInt = sdkInt,
            exactAlarm = exactAlarm,
            fullScreenIntent = fullScreenIntent,
            fineLocationGranted = fineLocationGranted,
            backgroundLocationGranted = false,
            notifications = RuntimePermissionState.USER_ACTION_REQUIRED,
        ),
    )
}
