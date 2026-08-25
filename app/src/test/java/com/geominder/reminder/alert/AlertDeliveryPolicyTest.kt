package com.geominder.reminder.alert

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertDeliveryPolicyTest {
    @Test
    fun `uses full screen only when locked screen off and all capabilities are available`() {
        assertEquals(
            AlertPresentation.FULL_SCREEN,
            choose(
                locked = true,
                interactive = false,
                fullScreen = true,
                exactAlarm = true,
            ),
        )
    }

    @Test
    fun `locked but interactive device receives normal notification`() {
        assertEquals(
            AlertPresentation.NORMAL_NOTIFICATION,
            choose(locked = true, interactive = true),
        )
    }

    @Test
    fun `screen off but unlocked device receives normal notification`() {
        assertEquals(
            AlertPresentation.NORMAL_NOTIFICATION,
            choose(locked = false, interactive = false),
        )
    }

    @Test
    fun `unlocked interactive device receives normal notification`() {
        assertEquals(
            AlertPresentation.NORMAL_NOTIFICATION,
            choose(locked = false, interactive = true),
        )
    }

    @Test
    fun `full screen denial falls back to normal notification`() {
        assertEquals(
            AlertPresentation.NORMAL_NOTIFICATION,
            choose(locked = true, interactive = false, fullScreen = false),
        )
    }

    @Test
    fun `exact alarm denial falls back to normal notification`() {
        assertEquals(
            AlertPresentation.NORMAL_NOTIFICATION,
            choose(locked = true, interactive = false, exactAlarm = false),
        )
    }

    private fun choose(
        locked: Boolean,
        interactive: Boolean,
        fullScreen: Boolean = true,
        exactAlarm: Boolean = true,
    ): AlertPresentation = AlertDeliveryPolicy.choosePresentation(
        isLockedAndNonInteractive = locked && !interactive,
        capabilities = AlertCapabilities(fullScreen, exactAlarm),
    )
}
