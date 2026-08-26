package com.afn478.geominder.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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
import com.afn478.geominder.settings.AccentTheme
import com.afn478.geominder.settings.ThemeMode

private data class AccentPalette(
    val lightPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val darkPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
)

private val accents = mapOf(
    AccentTheme.SUNSET to AccentPalette(
        lightPrimary = Color(0xFF9C3F18),
        lightContainer = Color(0xFFFFDAD0),
        lightOnContainer = Color(0xFF3B0A00),
        darkPrimary = Color(0xFFFFB59A),
        darkContainer = Color(0xFF7A2E0A),
        darkOnContainer = Color(0xFFFFDAD0),
    ),
    AccentTheme.OCEAN to AccentPalette(
        lightPrimary = Color(0xFF006874),
        lightContainer = Color(0xFF9CF0FF),
        lightOnContainer = Color(0xFF001F24),
        darkPrimary = Color(0xFF56D7E8),
        darkContainer = Color(0xFF004F58),
        darkOnContainer = Color(0xFF9CF0FF),
    ),
    AccentTheme.FOREST to AccentPalette(
        lightPrimary = Color(0xFF486A2A),
        lightContainer = Color(0xFFC9EE9B),
        lightOnContainer = Color(0xFF112000),
        darkPrimary = Color(0xFFA8D67D),
        darkContainer = Color(0xFF2F5115),
        darkOnContainer = Color(0xFFC9EE9B),
    ),
    AccentTheme.PLUM to AccentPalette(
        lightPrimary = Color(0xFF7A3A75),
        lightContainer = Color(0xFFFFD7F2),
        lightOnContainer = Color(0xFF2F0A2D),
        darkPrimary = Color(0xFFF2A9E8),
        darkContainer = Color(0xFF60245C),
        darkOnContainer = Color(0xFFFFD7F2),
    ),
    AccentTheme.CITRUS to AccentPalette(
        lightPrimary = Color(0xFF765B00),
        lightContainer = Color(0xFFFFE08B),
        lightOnContainer = Color(0xFF251A00),
        darkPrimary = Color(0xFFE9C349),
        darkContainer = Color(0xFF584400),
        darkOnContainer = Color(0xFFFFE08B),
    ),
    AccentTheme.ROSE to AccentPalette(
        lightPrimary = Color(0xFF9A4055),
        lightContainer = Color(0xFFFFD9DF),
        lightOnContainer = Color(0xFF3F0012),
        darkPrimary = Color(0xFFFFB1C1),
        darkContainer = Color(0xFF7D2940),
        darkOnContainer = Color(0xFFFFD9DF),
    ),
    AccentTheme.SKY to AccentPalette(
        lightPrimary = Color(0xFF3D5F90),
        lightContainer = Color(0xFFD6E3FF),
        lightOnContainer = Color(0xFF001B3F),
        darkPrimary = Color(0xFFB0C9F9),
        darkContainer = Color(0xFF254777),
        darkOnContainer = Color(0xFFD6E3FF),
    ),
    AccentTheme.SLATE to AccentPalette(
        lightPrimary = Color(0xFF4D5D70),
        lightContainer = Color(0xFFD3E4FA),
        lightOnContainer = Color(0xFF0A1C2D),
        darkPrimary = Color(0xFFB9C8DA),
        darkContainer = Color(0xFF35475A),
        darkOnContainer = Color(0xFFD3E4FA),
    ),
)

private val fallbackAccent = AccentPalette(
    lightPrimary = Color(0xFF49627A),
    lightContainer = Color(0xFFD3E4FA),
    lightOnContainer = Color(0xFF0A1C2D),
    darkPrimary = Color(0xFFB7D0EA),
    darkContainer = Color(0xFF35475A),
    darkOnContainer = Color(0xFFD3E4FA),
)

private val lightBackground = Color(0xFFF9F9FA)
private val lightOnBackground = Color(0xFF1A1C1E)
private val lightSurfaceVariant = Color(0xFFE2E3E5)
private val lightOnSurfaceVariant = Color(0xFF45474D)
private val lightOutline = Color(0xFF75777D)
private val lightOutlineVariant = Color(0xFFC5C7CC)
private val lightInverseSurface = Color(0xFF2F3033)
private val lightInverseOnSurface = Color(0xFFF1F0F4)
private val lightSurfaceBright = Color(0xFFF9F9FA)
private val lightSurfaceDim = Color(0xFFDADADD)
private val lightSurfaceContainerLowest = Color.White
private val lightSurfaceContainerLow = Color(0xFFF3F3F4)
private val lightSurfaceContainer = Color(0xFFEDEDEF)
private val lightSurfaceContainerHigh = Color(0xFFE7E7E9)
private val lightSurfaceContainerHighest = Color(0xFFE1E1E3)

private val darkBackground = Color(0xFF111417)
private val darkOnBackground = Color(0xFFE5E2E6)
private val darkSurfaceVariant = Color(0xFF45474D)
private val darkOnSurfaceVariant = Color(0xFFC5C6CB)
private val darkOutline = Color(0xFF8F9197)
private val darkOutlineVariant = Color(0xFF45474D)
private val darkInverseSurface = Color(0xFFE5E2E6)
private val darkInverseOnSurface = Color(0xFF2F3033)
private val darkSurfaceBright = Color(0xFF383A3E)
private val darkSurfaceDim = Color(0xFF111417)
private val darkSurfaceContainerLowest = Color(0xFF0C0F12)
private val darkSurfaceContainerLow = Color(0xFF191B1F)
private val darkSurfaceContainer = Color(0xFF1D1F23)
private val darkSurfaceContainerHigh = Color(0xFF282A2E)
private val darkSurfaceContainerHighest = Color(0xFF33353A)

private fun accentPalette(accentTheme: AccentTheme): AccentPalette =
    accents[accentTheme] ?: fallbackAccent

/**
 * Builds a complete fixed-color scheme so Material3's default purple container roles are not
 * mixed into a user-selected accent.
 */
internal fun accentColorScheme(
    accentTheme: AccentTheme,
    darkTheme: Boolean,
): ColorScheme {
    val accent = accentPalette(accentTheme)
    return if (darkTheme) {
        darkColorScheme(
            primary = accent.darkPrimary,
            onPrimary = accent.lightOnContainer,
            primaryContainer = accent.darkContainer,
            onPrimaryContainer = accent.darkOnContainer,
            inversePrimary = accent.lightPrimary,
            secondary = accent.darkPrimary,
            onSecondary = accent.lightOnContainer,
            secondaryContainer = accent.darkContainer,
            onSecondaryContainer = accent.darkOnContainer,
            tertiary = accent.darkPrimary,
            onTertiary = accent.lightOnContainer,
            tertiaryContainer = accent.darkContainer,
            onTertiaryContainer = accent.darkOnContainer,
            background = darkBackground,
            onBackground = darkOnBackground,
            surface = darkBackground,
            onSurface = darkOnBackground,
            surfaceVariant = darkSurfaceVariant,
            onSurfaceVariant = darkOnSurfaceVariant,
            inverseSurface = darkInverseSurface,
            inverseOnSurface = darkInverseOnSurface,
            outline = darkOutline,
            outlineVariant = darkOutlineVariant,
            surfaceBright = darkSurfaceBright,
            surfaceDim = darkSurfaceDim,
            surfaceContainer = darkSurfaceContainer,
            surfaceContainerHigh = darkSurfaceContainerHigh,
            surfaceContainerHighest = darkSurfaceContainerHighest,
            surfaceContainerLow = darkSurfaceContainerLow,
            surfaceContainerLowest = darkSurfaceContainerLowest,
            primaryFixed = accent.lightContainer,
            primaryFixedDim = accent.darkPrimary,
            onPrimaryFixed = accent.lightOnContainer,
            onPrimaryFixedVariant = accent.darkContainer,
            secondaryFixed = accent.lightContainer,
            secondaryFixedDim = accent.darkPrimary,
            onSecondaryFixed = accent.lightOnContainer,
            onSecondaryFixedVariant = accent.darkContainer,
            tertiaryFixed = accent.lightContainer,
            tertiaryFixedDim = accent.darkPrimary,
            onTertiaryFixed = accent.lightOnContainer,
            onTertiaryFixedVariant = accent.darkContainer,
        )
    } else {
        lightColorScheme(
            primary = accent.lightPrimary,
            onPrimary = Color.White,
            primaryContainer = accent.lightContainer,
            onPrimaryContainer = accent.lightOnContainer,
            inversePrimary = accent.darkPrimary,
            secondary = accent.lightPrimary,
            onSecondary = Color.White,
            secondaryContainer = accent.lightContainer,
            onSecondaryContainer = accent.lightOnContainer,
            tertiary = accent.lightPrimary,
            onTertiary = Color.White,
            tertiaryContainer = accent.lightContainer,
            onTertiaryContainer = accent.lightOnContainer,
            background = lightBackground,
            onBackground = lightOnBackground,
            surface = lightBackground,
            onSurface = lightOnBackground,
            surfaceVariant = lightSurfaceVariant,
            onSurfaceVariant = lightOnSurfaceVariant,
            inverseSurface = lightInverseSurface,
            inverseOnSurface = lightInverseOnSurface,
            outline = lightOutline,
            outlineVariant = lightOutlineVariant,
            surfaceBright = lightSurfaceBright,
            surfaceDim = lightSurfaceDim,
            surfaceContainer = lightSurfaceContainer,
            surfaceContainerHigh = lightSurfaceContainerHigh,
            surfaceContainerHighest = lightSurfaceContainerHighest,
            surfaceContainerLow = lightSurfaceContainerLow,
            surfaceContainerLowest = lightSurfaceContainerLowest,
            primaryFixed = accent.lightContainer,
            primaryFixedDim = accent.darkPrimary,
            onPrimaryFixed = accent.lightOnContainer,
            onPrimaryFixedVariant = accent.darkContainer,
            secondaryFixed = accent.lightContainer,
            secondaryFixedDim = accent.darkPrimary,
            onSecondaryFixed = accent.lightOnContainer,
            onSecondaryFixedVariant = accent.darkContainer,
            tertiaryFixed = accent.lightContainer,
            tertiaryFixedDim = accent.darkPrimary,
            onTertiaryFixed = accent.lightOnContainer,
            onTertiaryFixedVariant = accent.darkContainer,
        )
    }
}

internal fun accentSwatchColor(
    accentTheme: AccentTheme,
    darkTheme: Boolean,
    dynamicColor: Color,
): Color = if (accentTheme == AccentTheme.DYNAMIC) {
    dynamicColor
} else {
    val accent = accentPalette(accentTheme)
    if (darkTheme) accent.darkPrimary else accent.lightPrimary
}

internal fun resolveDarkTheme(systemIsDark: Boolean, themeMode: ThemeMode): Boolean =
    when (themeMode) {
        ThemeMode.SYSTEM -> systemIsDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }

internal fun applyBlackSurfaces(
    colorScheme: ColorScheme,
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
        accentColorScheme(accentTheme = accentTheme, darkTheme = dark)
    }.let { applyBlackSurfaces(it, themeMode) }
    MaterialTheme(colorScheme = colorScheme, motionScheme = MotionScheme.expressive(), content = content)
}
