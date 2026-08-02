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

/** Quiet listening surfaces with forest controls and sparing copper accents. */
private val Ink = Color(0xFF17201B)
private val InkMuted = Color(0xFF5B665F)
private val Canvas = Color(0xFFF4F7F5)
private val Paper = Color(0xFFFFFFFF)
private val Mist = Color(0xFFE7ECE9)
private val Forest = Color(0xFF315E4B)
private val ForestDeep = Color(0xFF1E4636)
private val ForestSoft = Color(0xFFDCEAE2)
private val Copper = Color(0xFFA96335)

private val Night = Color(0xFF101412)
private val NightElevated = Color(0xFF181D1A)
private val NightVariant = Color(0xFF252C28)
private val Bone = Color(0xFFE8EEE9)
private val BoneMuted = Color(0xFFA9B3AC)
private val Mint = Color(0xFFA9D0BC)
private val MintDim = Color(0xFF28483A)
private val CopperLight = Color(0xFFE4A46E)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = ForestSoft,
    onPrimaryContainer = ForestDeep,
    secondary = Copper,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E3D7),
    onSecondaryContainer = Color(0xFF532E17),
    tertiary = Color(0xFF4D6875),
    onTertiary = Color.White,
    background = Canvas,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = InkMuted,
    surfaceTint = Forest,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9F8),
    surfaceContainer = Color(0xFFF0F4F1),
    surfaceContainerHigh = Color(0xFFE9EFEB),
    surfaceContainerHighest = Color(0xFFE1E8E3),
    outline = Color(0xFF9AA69F),
    outlineVariant = Color(0xFFD4DCD7),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF8C1D18),
    inverseSurface = Color(0xFF29312D),
    inverseOnSurface = Color(0xFFF0F4F1),
    inversePrimary = Mint,
)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF10271D),
    primaryContainer = MintDim,
    onPrimaryContainer = Color(0xFFD5EADF),
    secondary = CopperLight,
    onSecondary = Color(0xFF3B1D0B),
    secondaryContainer = Color(0xFF56351F),
    onSecondaryContainer = Color(0xFFF7D6BE),
    tertiary = Color(0xFFA8C8D7),
    onTertiary = Color(0xFF17313C),
    background = Night,
    onBackground = Bone,
    surface = NightElevated,
    onSurface = Bone,
    surfaceVariant = NightVariant,
    onSurfaceVariant = BoneMuted,
    surfaceTint = Mint,
    surfaceContainerLowest = Color(0xFF0C100E),
    surfaceContainerLow = Color(0xFF141917),
    surfaceContainer = NightElevated,
    surfaceContainerHigh = Color(0xFF1D2320),
    surfaceContainerHighest = NightVariant,
    outline = Color(0xFF707B74),
    outlineVariant = Color(0xFF38413C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Bone,
    inverseOnSurface = Night,
    inversePrimary = Forest,
)

private val TingXiaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.2.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
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
        fontWeight = FontWeight.Medium,
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
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
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
