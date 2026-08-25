package com.geominder.reminder.boot

import org.junit.Assert.assertEquals
import org.junit.Test

class BootResyncLauncherTest {
    @Test
    fun `finishes once when entry point invokes completion more than once`() {
        var finishCount = 0
        val entryPoint = BootFeatureEntryPoint { completion ->
            completion(successReport())
            completion(successReport())
        }

        BootResyncLauncher.launch(entryPoint) { finishCount += 1 }

        assertEquals(1, finishCount)
    }

    @Test
    fun `finishes when runtime entry point is unavailable`() {
        var finishCount = 0

        BootResyncLauncher.launch(entryPoint = null) { finishCount += 1 }

        assertEquals(1, finishCount)
    }

    @Test
    fun `finishes when entry point throws before accepting work`() {
        var finishCount = 0
        val entryPoint = BootFeatureEntryPoint { throw IllegalStateException("launch failed") }

        BootResyncLauncher.launch(entryPoint) { finishCount += 1 }

        assertEquals(1, finishCount)
    }

    private fun successReport() = BootResyncReport(
        loadedReminderCount = 0,
        ignoredInactiveCount = 0,
        scheduledAlarmCount = 0,
        registeredGeofenceCount = 0,
        failures = emptyList(),
    )
}
