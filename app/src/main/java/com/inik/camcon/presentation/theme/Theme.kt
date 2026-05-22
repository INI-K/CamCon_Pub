package com.inik.camcon.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    content: @Composable () -> Unit
) {
    // CamCon은 다크 테마 고정. 라이트 분기 시그니처 자체를 제거해 향후 확장 차단.
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
