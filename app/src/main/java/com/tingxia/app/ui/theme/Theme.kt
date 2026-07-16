package com.tingxia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF7C9CFF)
private val BlueDark = Color(0xFFA8C0FF)
private val SurfaceDark = Color(0xFF121212)
private val SurfaceLight = Color(0xFFF7F7FA)

private val DarkColors = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color(0xFF0A1A40),
    primaryContainer = Color(0xFF2A3F7A),
    onPrimaryContainer = Color(0xFFD6E2FF),
    secondary = Color(0xFFB8C4E0),
    onSecondary = Color(0xFF22304A),
    background = SurfaceDark,
    onBackground = Color(0xFFE8E8ED),
    surface = Color(0xFF1B1B1F),
    onSurface = Color(0xFFE8E8ED),
    surfaceVariant = Color(0xFF2A2A30),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E909A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D5A9E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF555F71),
    onSecondary = Color.White,
    background = SurfaceLight,
    onBackground = Color(0xFF1B1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE2E2EA),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF767680),
)

@Composable
fun TingXiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
