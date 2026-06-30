package com.inik.camcon.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * CamCon Design System V2 — Theme
 *
 * Editorial Pro Tool 톤. 다크 고정.
 * ColorScheme V2 매핑은 docs/DESIGN_SYSTEM_V2.md §1.4 참조.
 */

private val UnifiedDarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentMuted,
    onPrimaryContainer = Accent,

    secondary = AccentStrong,
    onSecondary = OnAccent,
    secondaryContainer = AccentMuted,
    onSecondaryContainer = AccentStrong,

    tertiary = Info,
    onTertiary = TextPrimaryV2,

    background = Surface0,
    onBackground = TextPrimaryV2,

    surface = Surface1,
    onSurface = TextPrimaryV2,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondaryV2,

    error = ErrorV2,
    onError = TextPrimaryV2,

    outline = DividerLine,
    outlineVariant = Surface3,
    scrim = Surface0.copy(alpha = 0.7f),

    inverseSurface = TextPrimaryV2,
    inverseOnSurface = Surface0,
    inversePrimary = Accent,

    surfaceBright = Surface3,
    surfaceDim = Surface0,
    surfaceContainer = Surface2,
    surfaceContainerHigh = Surface3,
    surfaceContainerHighest = Surface4,
    surfaceContainerLow = Surface1,
    surfaceContainerLowest = Surface0,

    surfaceTint = Accent
)

@Composable
fun CamConTheme(
    content: @Composable () -> Unit
) {
    // CamCon V2 — 다크 테마 고정.
    val colorScheme = UnifiedDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            window?.let {
                val insetsController = WindowCompat.getInsetsController(it, view)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
