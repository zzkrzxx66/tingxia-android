package com.tingxia.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.tingxia.app.R
import com.tingxia.app.ui.theme.CoverCorner
import com.tingxia.app.ui.theme.CoverPalette
import java.io.File

/**
 * Book artwork.
 *
 * @param size fixed width. Height follows [ratio]; omit to fill the available width instead.
 * @param ratio width / height. 1f is a square thumbnail, [com.tingxia.app.ui.theme.COVER_RATIO_PORTRAIT]
 *   is the 3:4 shape real book covers are authored at — cropping those to a square cuts off faces.
 */
@Composable
fun BookCover(
    title: String,
    coverPath: String?,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    corner: Dp = CoverCorner.Card,
    ratio: Float = 1f,
) {
    val shape = RoundedCornerShape(corner)
    val boxMod = if (size != null) {
        modifier.width(size).height(size / ratio).clip(shape)
    } else {
        modifier.aspectRatio(ratio).clip(shape)
    }
    Box(
        boxMod.clearAndSetSemantics { }.then(
            Modifier.border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = shape,
            ),
        ),
    ) {
        val model: Any? = when {
            coverPath.isNullOrBlank() -> null
            coverPath.startsWith("content:") || coverPath.startsWith("file:") ||
                coverPath.startsWith("http") -> coverPath
            else -> File(coverPath)
        }
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            val base = CoverPalette[kotlin.math.abs(title.hashCode()) % CoverPalette.size]
            val compact = size != null && size < 80.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // A soft diagonal wash reads as printed board stock rather than a flat swatch.
                    .background(
                        Brush.linearGradient(
                            listOf(
                                base.lighten(0.12f),
                                base,
                                base.darken(0.14f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(if (compact) 5.dp else 8.dp)
                        .background(Color.Black.copy(alpha = 0.10f)),
                )
                Text(
                    text = title.take(1).ifEmpty { stringResource(R.string.cover_fallback_character) },
                    color = Color.White.copy(alpha = 0.96f),
                    fontSize = if (compact) 20.sp else 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                )
            }
        }
    }
}

/**
 * The single card container for the app. Replaces the hand-rolled
 * `Surface(border = …)` blocks that had drifted apart across screens.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    val border = androidx.compose.foundation.BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            border = border,
            shadowElevation = 1.dp,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            border = border,
            shadowElevation = 1.dp,
            content = content,
        )
    }
}

private fun Color.lighten(amount: Float): Color = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)

private fun Color.darken(amount: Float): Color = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = alpha,
)

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatProgressLabel(positionMs: Long, durationMs: Long): String {
    return "${formatDuration(positionMs)} / ${formatDuration(durationMs)}"
}
