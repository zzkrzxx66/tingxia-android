package com.tingxia.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
 * @param realistic when true, paperback light/shadow overlays (spine crease, page edges, a drop
 *   shadow) sit on top of the artwork so tiles read as physical books on the shelf.
 */
@Composable
fun BookCover(
    title: String,
    coverPath: String?,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    corner: Dp = CoverCorner.Card,
    ratio: Float = 1f,
    realistic: Boolean = false,
) {
    val shape = RoundedCornerShape(corner)
    val sized = if (size != null) {
        modifier.width(size).height(size / ratio)
    } else {
        modifier.aspectRatio(ratio)
    }
    val boxMod = if (realistic) {
        // spotColor warms the contact shadow so books sit on the shelf instead of floating.
        sized.shadow(elevation = 5.dp, shape = shape, clip = false, spotColor = Color(0xFF4A3B2C))
    } else {
        sized
    }
    Box(boxMod.clearAndSetSemantics { }.clip(shape)) {
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
            FallbackCover(title = title, compact = size != null && size < 80.dp)
        }
        if (realistic) {
            RealisticBookOverlay(compact = size != null && size < 80.dp)
        }
    }
}

/**
 * Paperback finish painted over the artwork:
 *  - left: the dark crease and catch-light where cover wraps around the spine,
 *  - right: a strip of page edges in paper tone,
 *  - top: cut-page hairlines (skipped when compact, they turn to mush),
 *  - overall: the debossed hairline frame a printed hardcover carries.
 * All tones follow the light/dark theme so the effect stays subtle at night.
 */
@Composable
private fun RealisticBookOverlay(compact: Boolean, modifier: Modifier = Modifier) {
    val darkTheme = isSystemInDarkTheme()
    val pageEdge = if (darkTheme) Color(0xFF4A453D) else Color(0xFFEFE7D8)
    val pageEdgeShade = if (darkTheme) Color(0xFF3A362F) else Color(0xFFE0D5C2)
    val pageLine = if (darkTheme) Color(0xFF5A544A) else Color(0xFFCFC3AE)
    val frame = if (darkTheme) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.22f)
    Box(modifier.fillMaxSize()) {
        // Spine crease: dark fold then a thin highlight, like light raking across the hinge.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(if (compact) 4.dp else 6.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.30f),
                            Color.Black.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        // Page block peeking past the right edge of the cover board.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(if (compact) 2.dp else 3.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(pageEdge, pageEdgeShade, pageEdge),
                    ),
                ),
        )
        if (!compact) {
            // Cut-page hairlines along the head of the book.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxSize()
                    .drawBehind {
                        val line = 0.6.dp.toPx()
                        val step = 1.6.dp.toPx()
                        val inset = 1.2.dp.toPx()
                        for (i in 0..3) {
                            val y = inset + i * step
                            drawLine(
                                color = pageLine,
                                start = Offset(inset, y),
                                end = Offset(size.width - inset, y),
                                strokeWidth = line,
                            )
                        }
                    },
            )
        }
        // Debossed inner frame, uniform across real covers and fallbacks.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val inset = if (compact) 3.dp.toPx() else 6.dp.toPx()
                    drawRect(
                        color = frame,
                        topLeft = Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(
                            size.width - inset * 2,
                            size.height - inset * 2,
                        ),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                    )
                },
        )
    }
}
@Composable
private fun FallbackCover(title: String, compact: Boolean, modifier: Modifier = Modifier) {
    val base = CoverPalette[kotlin.math.abs(title.hashCode()) % CoverPalette.size]
    Box(
        modifier = modifier
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
                .background(Color.Black.copy(alpha = 0.12f)),
        )
        // Hairline inner frame, like the debossed border on a hardcover.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val inset = if (compact) 4.dp.toPx() else 7.dp.toPx()
                    drawRect(
                        color = Color.White.copy(alpha = 0.22f),
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(
                            size.width - inset * 2,
                            size.height - inset * 2,
                        ),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                    )
                },
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

/** Full-bleed blurred artwork backdrop with a scrim, for immersive headers and the player. */
@Composable
fun AmbientBackground(
    coverPath: String?,
    title: String,
    modifier: Modifier = Modifier,
    scrim: Color = Color.Black.copy(alpha = 0.45f),
    blurRadius: Dp = 28.dp,
) {
    val base = CoverPalette[kotlin.math.abs(title.hashCode()) % CoverPalette.size]
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(base.darken(0.35f), base.darken(0.55f))),
        ),
    ) {
        val model: Any? = when {
            coverPath.isNullOrBlank() -> null
            coverPath.startsWith("content:") || coverPath.startsWith("file:") ||
                coverPath.startsWith("http") -> coverPath
            else -> File(coverPath)
        }
        if (model != null) {
            Image(
                painter = coil.compose.rememberAsyncImagePainter(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(model)
                        .size(96) // tiny source: it is only a colour field once blurred
                        .build(),
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(blurRadius),
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(scrim))
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
    // Light mode: hairline outline + whisper of shadow reads crisper on the near-white
    // canvas than shadow alone. Dark mode keeps pure shadow; outlines go muddy there.
    val border = if (!isSystemInDarkTheme()) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    } else {
        null
    }
    val shadow = if (border != null) 1.dp else 2.dp
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            shadowElevation = shadow,
            border = border,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            shadowElevation = shadow,
            border = border,
            content = content,
        )
    }
}

/**
 * Skip intro/outro (seconds) editor, shared by the book-detail menu and the
 * full player so both surfaces stay in sync. Values are clamped to 0–300s.
 */
@Composable
fun SkipOffsetsDialog(
    initialIntroMs: Long,
    initialOutroMs: Long,
    onDismiss: () -> Unit,
    onSave: (skipIntroMs: Long, skipOutroMs: Long) -> Unit,
) {
    var introSeconds by remember { mutableStateOf((initialIntroMs / 1_000L).toString()) }
    var outroSeconds by remember { mutableStateOf((initialOutroMs / 1_000L).toString()) }
    val intro = introSeconds.toLongOrNull()
    val outro = outroSeconds.toLongOrNull()
    val introValid = intro != null && intro in 0L..300L
    val outroValid = outro != null && outro in 0L..300L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.skip_intro_outro)) },
        text = {
            Column {
                OutlinedTextField(
                    value = introSeconds,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit)) introSeconds = value
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.skip_intro_seconds)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = introSeconds.isNotEmpty() && !introValid,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = outroSeconds,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit)) outroSeconds = value
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.skip_outro_seconds)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = outroSeconds.isNotEmpty() && !outroValid,
                )
                if ((!introValid && introSeconds.isNotEmpty()) ||
                    (!outroValid && outroSeconds.isNotEmpty())
                ) {
                    Text(
                        stringResource(R.string.skip_seconds_range),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = introValid && outroValid,
                onClick = {
                    onSave(checkNotNull(intro) * 1_000L, checkNotNull(outro) * 1_000L)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
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

/** 887289 -> "88.7"; values under 10k keep the raw count. */
fun formatWordCount(count: Long): String {
    if (count < 10_000L) return count.toString()
    val wan = count / 10_000.0
    return if (wan >= 100) "%d".format(wan.toLong()) else "%.1f".format(wan)
}
