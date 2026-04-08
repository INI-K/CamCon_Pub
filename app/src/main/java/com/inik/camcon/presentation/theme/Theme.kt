package com.inik.camcon.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.inik.camcon.domain.model.ThemeMode

// 통일된 다크 색상 스킴
private val UnifiedDarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Primary.copy(alpha = 0.15f),
    onPrimaryContainer = Primary,

    secondary = PrimaryLight,
    onSecondary = TextPrimary,
    secondaryContainer = Primary.copy(alpha = 0.1f),
    onSecondaryContainer = Primary,

    tertiary = PrimaryLight,
    onTertiary = TextPrimary,

    background = Background,
    onBackground = TextPrimary,

    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,

    error = Error,
    onError = TextPrimary,

    outline = Border,
    outlineVariant = Border.copy(alpha = 0.5f),
    scrim = Overlay,

    inverseSurface = TextPrimary,
    inverseOnSurface = Background,
    inversePrimary = Primary,

    surfaceBright = SurfaceElevated,
    surfaceDim = Background,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceElevated,
    surfaceContainerHighest = SurfaceElevated,
    surfaceContainerLow = BackgroundSurface,
    surfaceContainerLowest = Background,

    surfaceTint = Primary.copy(alpha = 0.05f)
)

@Composable
fun CamConTheme(
    themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // 항상 다크 테마 사용 (라이트 모드도 다크로 강제)
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
