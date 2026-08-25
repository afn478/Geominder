package com.geominder.reminder.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.geominder.reminder.settings.AccentTheme
import com.geominder.reminder.settings.ThemeMode

private data class Accent(val light: Color, val dark: Color)
private val accents = mapOf(
    AccentTheme.SUNSET to Accent(Color(0xFF9C3F18), Color(0xFFFFB59A)),
    AccentTheme.OCEAN to Accent(Color(0xFF006874), Color(0xFF56D7E8)),
    AccentTheme.FOREST to Accent(Color(0xFF486A2A), Color(0xFFA8D67D)),
    AccentTheme.PLUM to Accent(Color(0xFF7A3A75), Color(0xFFF2A9E8)),
    AccentTheme.CITRUS to Accent(Color(0xFF765B00), Color(0xFFE9C349)),
    AccentTheme.ROSE to Accent(Color(0xFF9A4055), Color(0xFFFFB1C1)),
    AccentTheme.SKY to Accent(Color(0xFF3D5F90), Color(0xFFB0C9F9)),
    AccentTheme.SLATE to Accent(Color(0xFF4D5D70), Color(0xFFB9C8DA)),
)

internal fun resolveDarkTheme(systemIsDark: Boolean, themeMode: ThemeMode): Boolean =
    when (themeMode) {
        ThemeMode.SYSTEM -> systemIsDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }

internal fun applyBlackSurfaces(
    colorScheme: androidx.compose.material3.ColorScheme,
    themeMode: ThemeMode,
) =
    if (themeMode == ThemeMode.BLACK) {
        colorScheme.copy(background = Color.Black, surface = Color.Black)
    } else {
        colorScheme
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentTheme: AccentTheme = AccentTheme.DYNAMIC,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = resolveDarkTheme(darkTheme, themeMode)
    val colorScheme = if (
        accentTheme == AccentTheme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val accent = accents[accentTheme] ?: Accent(Color(0xFF49627A), Color(0xFFB7D0EA))
        if (dark) darkColorScheme(
            primary = accent.dark,
            onPrimary = Color(0xFF102027),
            secondary = accent.dark.copy(alpha = .82f),
            tertiary = accent.dark.copy(alpha = .72f),
            background = if (themeMode == ThemeMode.BLACK) Color.Black else Color(0xFF111417),
            surface = if (themeMode == ThemeMode.BLACK) Color.Black else Color(0xFF111417),
        ) else lightColorScheme(
            primary = accent.light,
            secondary = accent.light.copy(alpha = .82f),
            tertiary = accent.light.copy(alpha = .72f),
        )
    }.let { applyBlackSurfaces(it, themeMode) }
    MaterialTheme(colorScheme = colorScheme, motionScheme = MotionScheme.expressive(), content = content)
}
