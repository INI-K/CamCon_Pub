package com.inik.camcon.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.inik.camcon.data.datasource.local.ThemeMode

// 앱 브랜드 컬러 정의
private val BrandBlue = Color(0xFF0064FF)          // colorPrimary
private val BrandBlueDark = Color(0xFF0057E7)      // colorPrimaryVariant
private val BrandSecondary = Color(0xFF202632)     // colorSecondary
private val BrandSecondaryDark = Color(0xFF1B1E2B) // colorSecondaryVariant

private val DarkColorScheme = darkColorScheme(
    // 주 색상
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueDark,
    onPrimaryContainer = Color(0xFFD0E4FF),

    // 보조 색상
    secondary = BrandSecondary,
    onSecondary = Color.White,
    secondaryContainer = BrandSecondaryDark,
    onSecondaryContainer = Color(0xFFD0E4FF),

    // 3차 색상
    tertiary = Color(0xFF7FBAFF),
    onTertiary = Color(0xFF003258),
    tertiaryContainer = Color(0xFF004A77),
    onTertiaryContainer = Color(0xFFD0E4FF),

    // 배경 & 표면 - 더 밝게 조정
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),

    // 에러
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    // 기타
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = BrandBlue,
    surfaceTint = BrandBlue,

    // Surface 변형들 - 다크 모드 전용
    surfaceBright = Color(0xFF3B383E),
    surfaceDim = Color(0xFF141316),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    surfaceContainerLow = Color(0xFF1C1B1F),
    surfaceContainerLowest = Color(0xFF0F0D13),
)

private val LightColorScheme = lightColorScheme(
    // 주 색상
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E4FF),
    onPrimaryContainer = Color(0xFF001D36),

    // 보조 색상  
    secondary = BrandSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0E4FF),
    onSecondaryContainer = Color(0xFF101C2B),

    // 3차 색상
    tertiary = Color(0xFF006399),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCE5FF),
    onTertiaryContainer = Color(0xFF001D32),

    // 배경 & 표면
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1B1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44464F),

    // 에러
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    // 기타
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF303034),
    inverseOnSurface = Color(0xFFF2F0F4),
    inversePrimary = Color(0xFF9ECAFF),
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

    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    // 시스템 바 아이콘 색상 조정
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            window?.let {
                val insetsController = WindowCompat.getInsetsController(it, view)
                // 라이트 테마: 다크 아이콘 (true), 다크 테마: 라이트 아이콘 (false)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
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