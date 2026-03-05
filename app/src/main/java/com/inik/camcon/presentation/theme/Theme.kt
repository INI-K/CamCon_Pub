package com.inik.camcon.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.inik.camcon.data.datasource.local.ThemeMode

private val DarkColorPalette = darkColors(
    primary = Color(0xFFFF9A3D),
    primaryVariant = Color(0xFFD97A1D),
    secondary = Color(0xFF5ED1B8),
    background = Color(0xFF171B22),
    surface = Color(0xFF202735),
    onPrimary = Color.White,
    onSecondary = Color(0xFF0F1B1B),
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightColorPalette = lightColors(
    primary = Color(0xFFCB5B15),
    primaryVariant = Color(0xFFA84910),
    secondary = Color(0xFF187A66),
    background = Color(0xFFFAF7F1),
    surface = Color(0xFFFFFCF6),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1F232B),
    onSurface = Color(0xFF1F232B),
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

    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}