package com.tingxia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * TingXia visual language:
 * warm paper / ink, low saturation, quiet hierarchy.
 * Avoid neon gradients, glass stacks, and "AI product" purple-blue.
 */

// Light — paper & ink
private val Ink = Color(0xFF1C1917)
private val InkMuted = Color(0xFF57534E)
private val Paper = Color(0xFFF7F3EE)
private val PaperElevated = Color(0xFFFFFCF8)
private val Clay = Color(0xFFE7E0D6)
private val Ochre = Color(0xFF9A6B2F)
private val OchreDeep = Color(0xFF7A5220)
private val OchreSoft = Color(0xFFF0E2CC)
private val Forest = Color(0xFF3F5D4A)

// Dark — charcoal & warm amber
private val Night = Color(0xFF141210)
private val NightElevated = Color(0xFF1C1A17)
private val NightVariant = Color(0xFF2A2622)
private val Bone = Color(0xFFE8E2D9)
private val BoneMuted = Color(0xFFA8A29A)
private val Amber = Color(0xFFD4A35C)
private val AmberDim = Color(0xFF3D2E16)
private val AmberOn = Color(0xFF1F1506)

private val LightColors = lightColorScheme(
    primary = Ochre,
    onPrimary = Color.White,
    primaryContainer = OchreSoft,
    onPrimaryContainer = OchreDeep,
    secondary = Forest,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E6DC),
    onSecondaryContainer = Color(0xFF1A2E22),
    tertiary = Color(0xFF7C5A4A),
    onTertiary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = PaperElevated,
    onSurface = Ink,
    surfaceVariant = Clay,
    onSurfaceVariant = InkMuted,
    surfaceTint = Ochre,
    outline = Color(0xFFB0A89C),
    outlineVariant = Color(0xFFD9D1C5),
    error = Color(0xFFB3261E),
    onError = Color.White,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    inversePrimary = Amber,
)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = AmberOn,
    primaryContainer = AmberDim,
    onPrimaryContainer = Color(0xFFF0D9A8),
    secondary = Color(0xFFA3C0B0),
    onSecondary = Color(0xFF0F1F17),
    secondaryContainer = Color(0xFF2A3B32),
    onSecondaryContainer = Color(0xFFC7DDD0),
    tertiary = Color(0xFFD2B3A4),
    onTertiary = Color(0xFF2A1A12),
    background = Night,
    onBackground = Bone,
    surface = NightElevated,
    onSurface = Bone,
    surfaceVariant = NightVariant,
    onSurfaceVariant = BoneMuted,
    surfaceTint = Amber,
    outline = Color(0xFF6F675C),
    outlineVariant = Color(0xFF3F3A34),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    inverseSurface = Bone,
    inverseOnSurface = Night,
    inversePrimary = Ochre,
)

private val TingXiaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.15).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
    ),
)

private val TingXiaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Soft cover fallbacks — muted earth tones, no candy colors. */
val CoverPalette = listOf(
    Color(0xFF6B5A4A),
    Color(0xFF4A5A52),
    Color(0xFF5A4A5C),
    Color(0xFF4A5560),
    Color(0xFF6A5040),
    Color(0xFF3F4F4A),
    Color(0xFF5C5240),
    Color(0xFF4A4A58),
)

@Composable
fun TingXiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = TingXiaTypography,
        shapes = TingXiaShapes,
        content = content,
    )
}
