package com.example.generatorapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E9E6),
    onPrimaryContainer = PrimaryDark,
    secondary = AccentAmber,
    onSecondary = Color(0xFF2A1D06),
    secondaryContainer = Color(0xFFF6E4C6),
    onSecondaryContainer = AccentAmberDark,
    background = BackgroundLight,
    onBackground = InkText,
    surface = SurfaceWhite,
    onSurface = InkText,
    surfaceVariant = Color(0xFFEFE9DA),
    onSurfaceVariant = MutedText,
    outline = OutlineLine,
    error = PaidRed,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = AccentAmber,
    onPrimary = Color(0xFF2A1D06),
    secondary = AccentAmber,
    background = PrimaryDark,
    surface = Color(0xFF12201F),
    error = PaidRed,
    onError = Color.White
)

@Composable
fun GeneratorAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
