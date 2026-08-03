package com.tingxia.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Paper & Ink: warm paper surfaces, one deep ink accent, and a single cinnabar
 * mark reserved for personal traces (bookmarks, completions). No copper family.
 */
private val Ink = Color(0xFF1E2B25)
private val InkDeep = Color(0xFF14201A)
private val InkMuted = Color(0xFF6B6A60)
private val Paper = Color(0xFFF7F4ED)
private val PaperCard = Color(0xFFFFFFFF)
private val Mist = Color(0xFFEDE9DE)
private val InkSoft = Color(0xFFD9E0D6)
private val Mark = Color(0xFFB4432E)
private val MarkSoft = Color(0xFFF3DDD6)

private val Night = Color(0xFF0B0D0C)
private val NightElevated = Color(0xFF171917)
private val NightVariant = Color(0xFF23261F)
private val Bone = Color(0xFFEBE9E0)
private val BoneMuted = Color(0xFFA8A79B)
private val InkNight = Color(0xFFC9DCD0)
private val InkNightDim = Color(0xFF2A3B32)
private val MarkLight = Color(0xFFE08A73)
private val MarkNightDim = Color(0xFF4A241C)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color(0xFFF5F3EC),
    primaryContainer = InkSoft,
    onPrimaryContainer = InkDeep,
    secondary = Mark,
    onSecondary = Color.White,
    secondaryContainer = MarkSoft,
    onSecondaryContainer = Color(0xFF5A1D10),
    tertiary = Color(0xFF4A5A50),
    onTertiary = Color.White,
    background = Paper,
    onBackground = InkDeep,
    surface = PaperCard,
    onSurface = InkDeep,
    surfaceVariant = Mist,
    onSurfaceVariant = InkMuted,
    surfaceTint = Ink,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF8F2),
    surfaceContainer = Color(0xFFF3F0E8),
    surfaceContainerHigh = Color(0xFFECE8DD),
    surfaceContainerHighest = Color(0xFFE4DFD2),
    outline = Color(0xFF9A978A),
    outlineVariant = Color(0xFFE3DED2),
    error = Color(0xFFAB2F1B),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF6E1608),
    inverseSurface = Color(0xFF2A2E28),
    inverseOnSurface = Color(0xFFF3F0E8),
    inversePrimary = InkNight,
)

private val DarkColors = darkColorScheme(
    primary = InkNight,
    onPrimary = Color(0xFF10231A),
    primaryContainer = InkNightDim,
    onPrimaryContainer = Color(0xFFDCEAE0),
    secondary = MarkLight,
    onSecondary = Color(0xFF3A0F06),
    secondaryContainer = MarkNightDim,
    onSecondaryContainer = Color(0xFFF5CFC2),
    tertiary = Color(0xFFA3B8AC),
    onTertiary = Color(0xFF122019),
    background = Night,
    onBackground = Bone,
    surface = NightElevated,
    onSurface = Bone,
    surfaceVariant = NightVariant,
    onSurfaceVariant = BoneMuted,
    surfaceTint = InkNight,
    surfaceContainerLowest = Color(0xFF070908),
    surfaceContainerLow = Color(0xFF111311),
    surfaceContainer = NightElevated,
    surfaceContainerHigh = Color(0xFF1D201C),
    surfaceContainerHighest = NightVariant,
    outline = Color(0xFF6F7268),
    outlineVariant = Color(0xFF33362E),
    error = Color(0xFFFFB4A5),
    onError = Color(0xFF5C1003),
    errorContainer = Color(0xFF8A2110),
    onErrorContainer = Color(0xFFFFDAD2),
    inverseSurface = Bone,
    inverseOnSurface = Night,
    inversePrimary = Ink,
)

private val TingXiaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = 0.sp,
    ),
    // Book and chapter titles are serif: the single strongest cue that this is a
    // reading app, not a podcast utility.
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.2.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.2.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    // Chinese body copy reads better with a taller leading than the Latin defaults.
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.3.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.3.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp,
    ),
)

/**
 * A restrained corner ladder: print-like rectangles with just enough rounding
 * to soften edges. Badges sit below buttons, buttons below cards.
 */
private val TingXiaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Detail-page book title: larger and looser than headlineSmall. */
val displayTitleStyle: TextStyle
    @Composable get() = MaterialTheme.typography.headlineSmall.copy(
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = 1.sp,
    )

/** Chapter index rendered like a folio page number: large, light, serif, no tile. */
val folioNumberStyle: TextStyle
    @Composable get() = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.Light,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        fontFamily = FontFamily.Serif,
    )

/** Corner radii for artwork, which tracks the container it sits in. */
object CoverCorner {
    val Mini = 6.dp
    val Grid = 6.dp
    val Card = 8.dp
    val Detail = 10.dp
    val Hero = 12.dp
}

/** Portrait book artwork is 3:4; square is reserved for chrome-sized thumbnails. */
const val COVER_RATIO_PORTRAIT = 0.75f

/** Muted cover fallbacks with enough hue variety to keep the shelf scannable. */
val CoverPalette = listOf(
    Color(0xFF1E2B25),
    Color(0xFF4A5A50),
    Color(0xFF6B4A3E),
    Color(0xFF75612F),
    Color(0xFF3E4A5A),
    Color(0xFF4A3E52),
    Color(0xFF2E4A44),
    Color(0xFF5A4A2E),
)

/** Material You wallpaper colours only exist from Android 12; older devices keep the forest palette. */
val dynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Scrim laid over the blurred cover artwork behind immersive headers and the
 * player, so foreground text keeps its contrast regardless of the artwork.
 */
val playerScrim: Color
    @Composable get() = if (isSystemInDarkTheme()) {
        Color.Black.copy(alpha = 0.55f)
    } else {
        Color(0xFF0E1512).copy(alpha = 0.42f)
    }

@Composable
fun TingXiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && dynamicColorSupported ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TingXiaTypography,
        shapes = TingXiaShapes,
        content = content,
    )
}
