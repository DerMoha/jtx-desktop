package com.jtx.desktop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = JtxYellow,
    onPrimary = Color.Black,
    primaryContainer = JtxAmber,
    onPrimaryContainer = Color.Black,
    secondary = JtxYellowDark,
    onSecondary = Color.White,
    secondaryContainer = JtxYellow,
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF625B71),
    onTertiary = Color.White,
    background = JtxBackground,
    onBackground = JtxOnSurface,
    surface = JtxSurface,
    onSurface = JtxOnSurface,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = JtxOnSurfaceVariant,
    outline = JtxOutline,
    error = JtxError,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColorScheme = darkColorScheme(
    primary = JtxYellow,
    onPrimary = Color.Black,
    primaryContainer = JtxYellowDark,
    onPrimaryContainer = Color.White,
    secondary = JtxAmber,
    onSecondary = Color.Black,
    tertiary = Color(0xFFCCC2DC),
    onTertiary = Color.Black,
    background = Color(0xFF1C1B1F),
    onBackground = Color.White,
    surface = Color(0xFF2B2930),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8)
)

@Composable
fun JtxBoardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}