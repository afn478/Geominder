package com.geominder.reminder.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceSettingsTest {
    @Test
    fun `invalid or missing theme mode uses system`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("not-a-mode"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("DARK"))
    }

    @Test
    fun `invalid or missing accent uses dynamic`() {
        assertEquals(AccentTheme.DYNAMIC, AccentTheme.fromStorage(null))
        assertEquals(AccentTheme.DYNAMIC, AccentTheme.fromStorage("not-an-accent"))
        assertEquals(AccentTheme.OCEAN, AccentTheme.fromStorage("OCEAN"))
    }
}
