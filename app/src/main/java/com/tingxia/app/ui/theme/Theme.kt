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
 * Warm paper, deep ink, forest controls, copper reserved for "in progress".
 *
 * The light scheme is a printed-page white rather than the cool grey Material starts from: this is
 * a listening app for books, and every surface it shows sits next to cover art.
 */
private val Ink = Color(0xFF1A1A18)
private val InkMuted = Color(0xFF6B665C)
private val Canvas = Color(0xFFFAF7F2)
private val Paper = Color(0xFFFFFDF9)
private val Mist = Color(0xFFEFE9DF)
private val Forest = Color(0xFF2C5545)
private val ForestDeep = Color(0xFF163A2C)
private val ForestSoft = Color(0xFFDCE8E0)
private val Copper = Color(0xFFA2603A)

private val Night = Color(0xFF121311)
private val NightElevated = Color(0xFF1A1B18)
private val NightVariant = Color(0xFF262823)
private val Bone = Color(0xFFE9E5DC)
private val BoneMuted = Color(0xFFA8A49A)
private val Mint = Color(0xFFA6CDB8)
private val MintDim = Color(0xFF23422F)
private val CopperLight = Color(0xFFDBA070)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color(0xFFFDFCF8),
    primaryContainer = ForestSoft,
    onPrimaryContainer = ForestDeep,
    secondary = Copper,
    onSecondary = Color(0xFFFDFCF8),
    secondaryContainer = Color(0xFFF3E4D6),
    onSecondaryContainer = Color(0xFF4E2C15),
    tertiary = Color(0xFF4A6672),
    onTertiary = Color(0xFFFDFCF8),
    background = Canvas,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = InkMuted,
    surfaceTint = Forest,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF8F3),
    surfaceContainer = Color(0xFFF5F0E8),
    surfaceContainerHigh = Color(0xFFEFE9DF),
    surfaceContainerHighest = Color(0xFFE8E1D5),
    outline = Color(0xFFB9B2A6),
    outlineVariant = Color(0xFFE2DBCF),
    error = Color(0xFF9F3B32),
    onError = Color(0xFFFDFCF8),
    errorContainer = Color(0xFFF7DFDA),
    onErrorContainer = Color(0xFF6E241D),
    inverseSurface = Color(0xFF2E2E2A),
    inverseOnSurface = Color(0xFFF5F0E8),
    inversePrimary = Mint,
)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF0F2419),
    primaryContainer = MintDim,
    onPrimaryContainer = Color(0xFFD3E8DB),
    secondary = CopperLight,
    onSecondary = Color(0xFF38190A),
    secondaryContainer = Color(0xFF52321D),
    onSecondaryContainer = Color(0xFFF6D5BB),
    tertiary = Color(0xFFA4C6D4),
    onTertiary = Color(0xFF152F3A),
    background = Night,
    onBackground = Bone,
    surface = NightElevated,
    onSurface = Bone,
    surfaceVariant = NightVariant,
    onSurfaceVariant = BoneMuted,
    surfaceTint = Mint,
    surfaceContainerLowest = Color(0xFF0D0E0C),
    surfaceContainerLow = Color(0xFF161816),
    surfaceContainer = NightElevated,
    surfaceContainerHigh = Color(0xFF1F211D),
    surfaceContainerHighest = NightVariant,
    outline = Color(0xFF6E6A61),
    outlineVariant = Color(0xFF35372F),
    error = Color(0xFFF2B8B2),
    onError = Color(0xFF5F1611),
    errorContainer = Color(0xFF8C2C24),
    onErrorContainer = Color(0xFFFBDDD9),
    inverseSurface = Bone,
    inverseOnSurface = Night,
    inversePrimary = Forest,
)

// Chinese text carries its own side bearings, so extra letter spacing (which Material's Latin
// defaults add) just makes it look loose and machine-set. Everything here sits at 0 or tighter.
private const val UI_SANS_FEATURES = "tnum"

private val TingXiaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    // Durations, counters and percentages live in the small slots, so they get tabular figures:
    // otherwise 1 is narrower than 8 and every ticking number nudges the layout.
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = UI_SANS_FEATURES,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = UI_SANS_FEATURES,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = UI_SANS_FEATURES,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = UI_SANS_FEATURES,
    ),
)

/**
 * Type reserved for the books themselves. A serif for titles is the cheapest honest signal that
 * this app is about books, and it keeps titles from blending into the surrounding UI labels.
 */
object BookType {
    val title = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    )
    val titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
    )
}

/**
 * Three radii, not five: 8 for anything badge-sized, 14 for cards and artwork, 28 for things that
 * float over the page. A ladder with a step every 4dp reads as indecision, not craft.
 */
private val TingXiaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Corner radii for artwork, which tracks the container it sits in. */
object CoverCorner {
    val Mini = 8.dp
    val Grid = 12.dp
    val Card = 12.dp
    val Detail = 14.dp
    val Hero = 14.dp
}

/** Portrait book artwork is 3:4; square is reserved for chrome-sized thumbnails. */
const val COVER_RATIO_PORTRAIT = 0.75f

/** Muted cover fallbacks with enough hue variety to keep the shelf scannable. */
val CoverPalette = listOf(
    Color(0xFF315E4B),
    Color(0xFF526C78),
    Color(0xFF745866),
    Color(0xFF75612F),
    Color(0xFF74513E),
    Color(0xFF3F6261),
    Color(0xFF555B75),
    Color(0xFF53613F),
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
