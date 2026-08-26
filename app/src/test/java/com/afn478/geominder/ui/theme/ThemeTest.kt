package com.afn478.geominder.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.afn478.geominder.settings.AccentTheme
import com.afn478.geominder.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemeTest {
    @Test
    fun `black mode forces true black surfaces for dynamic and fixed schemes`() {
        val dynamicLikeScheme = darkColorScheme(
            background = Color(0xFF202124),
            surface = Color(0xFF202124),
            primary = Color(0xFFAACCEE),
        )
        val fixedScheme = darkColorScheme(
            background = Color(0xFF111417),
            surface = Color(0xFF111417),
            primary = Color(0xFFFFB59A),
        )

        assertEquals(Color.Black, applyBlackSurfaces(dynamicLikeScheme, ThemeMode.BLACK).background)
        assertEquals(Color.Black, applyBlackSurfaces(dynamicLikeScheme, ThemeMode.BLACK).surface)
        assertEquals(Color.Black, applyBlackSurfaces(fixedScheme, ThemeMode.BLACK).background)
        assertEquals(Color.Black, applyBlackSurfaces(fixedScheme, ThemeMode.BLACK).surface)
        assertEquals(dynamicLikeScheme.primary, applyBlackSurfaces(dynamicLikeScheme, ThemeMode.BLACK).primary)
    }

    @Test
    fun `mode resolution preserves system dark state`() {
        assertEquals(true, resolveDarkTheme(true, ThemeMode.SYSTEM))
        assertEquals(false, resolveDarkTheme(false, ThemeMode.SYSTEM))
        assertEquals(false, resolveDarkTheme(true, ThemeMode.LIGHT))
        assertEquals(true, resolveDarkTheme(false, ThemeMode.DARK))
        assertEquals(true, resolveDarkTheme(false, ThemeMode.BLACK))
    }

    @Test
    fun `fixed accents populate accent roles instead of material defaults`() {
        AccentTheme.values()
            .filterNot { it == AccentTheme.DYNAMIC }
            .forEach { accent ->
                val light = accentColorScheme(accentTheme = accent, darkTheme = false)
                val dark = accentColorScheme(accentTheme = accent, darkTheme = true)

                assertEquals(
                    accentSwatchColor(accent, darkTheme = false, dynamicColor = Color.Magenta),
                    light.primary,
                )
                assertEquals(
                    accentSwatchColor(accent, darkTheme = true, dynamicColor = Color.Magenta),
                    dark.primary,
                )
                assertNotEquals(Color(0xFFEADDFF), light.primaryContainer)
                assertNotEquals(Color(0xFF4A4458), dark.primaryContainer)
                assertEquals(light.primaryContainer, light.secondaryContainer)
                assertEquals(dark.primaryContainer, dark.secondaryContainer)
                assertOpaque(
                    listOf(
                        light.primary,
                        light.primaryContainer,
                        light.secondary,
                        light.secondaryContainer,
                        light.tertiary,
                        light.tertiaryContainer,
                        dark.primary,
                        dark.primaryContainer,
                        dark.secondary,
                        dark.secondaryContainer,
                        dark.tertiary,
                        dark.tertiaryContainer,
                    ),
                )
            }
    }

    private fun assertOpaque(colors: List<Color>) {
        colors.forEach { color -> assertEquals(1f, color.alpha, 0f) }
    }
}
