package com.tingxia.app.ui.components

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tingxia.app.R
import com.tingxia.app.ui.theme.BookType
import com.tingxia.app.ui.theme.COVER_RATIO_PORTRAIT
import com.tingxia.app.ui.theme.CoverCorner
import com.tingxia.app.ui.theme.CoverPalette
import com.tingxia.app.ui.theme.LocalAppDarkTheme
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
    framed: Boolean = false,
) {
    val shape = RoundedCornerShape(corner)
    val sized = if (size != null) {
        modifier.width(size).height(size / ratio)
    } else {
        modifier.aspectRatio(ratio)
    }
    val boxMod = sized
    BoxWithConstraints(boxMod.clearAndSetSemantics { }.clip(shape)) {
        // Compact treatment follows the measured width, not the optional size argument: grid tiles
        // lay themselves out with fillMaxWidth, so they used to get the full paperback treatment
        // (6dp spine, page block, cut-page hairlines) on a 118dp cover, which just read as stripes.
        val compact = maxWidth < 104.dp
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
            FallbackCover(title = title, compact = compact)
        }
        if (framed) CoverFinish()
    }
}

/**
 * The whole finish: a tactile book-spine crease on the left, an inner hairline frame,
 * a page-edge sheen on the right, and a gentle vignette at the foot.
 *
 * This gives covers authentic hardcover/paperback physical object presence.
 */
@Composable
private fun CoverFinish(modifier: Modifier = Modifier) {
    val darkTheme = LocalAppDarkTheme.current
    val edge = if (darkTheme) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                val spineWidth = (w * 0.07f).coerceIn(4.dp.toPx(), 9.dp.toPx())

                // 1. Left spine lighting: highlight ridge + crease shadow (精装书脊微凸与折痕)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = if (darkTheme) 0.16f else 0.20f),
                        0.35f to Color.Black.copy(alpha = if (darkTheme) 0.24f else 0.16f),
                        0.75f to Color.Black.copy(alpha = if (darkTheme) 0.10f else 0.06f),
                        1f to Color.Transparent,
                        startX = 0f,
                        endX = spineWidth,
                    ),
                    topLeft = Offset.Zero,
                    size = androidx.compose.ui.geometry.Size(spineWidth, h),
                )

                // 2. Right page edge sheen (书页开合切口微光)
                val rightSheenWidth = 2.dp.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        1f to Color.White.copy(alpha = if (darkTheme) 0.08f else 0.14f),
                        startX = w - rightSheenWidth,
                        endX = w,
                    ),
                    topLeft = Offset(w - rightSheenWidth, 0f),
                    size = androidx.compose.ui.geometry.Size(rightSheenWidth, h),
                )

                // 3. Bottom vignette wash (底角柔和阴影)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.18f),
                    ),
                )

                // 4. Subtle inner hairline frame (细腻装订内边框)
                val stroke = 1.dp.toPx()
                drawRect(
                    color = edge,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = androidx.compose.ui.geometry.Size(
                        w - stroke,
                        h - stroke,
                    ),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
            },
    )
}

/**
 * Extracts the first letter or digit from a book title, skipping leading punctuation
 * such as guillemets (`《三体》` -> `"三"`). Returns `null` when no alphanumeric/CJK character exists.
 */
internal fun extractCoverInitial(title: String): String? =
    title.trim().firstOrNull { it.isLetterOrDigit() }?.toString()

@Composable
private fun FallbackCover(title: String, compact: Boolean, modifier: Modifier = Modifier) {
    val base = CoverPalette[kotlin.math.abs(title.hashCode()) % CoverPalette.size]
    Box(
        modifier = modifier
            .fillMaxSize()
            // A rich cloth-like gradient wash reads as fine linen board stock
            .background(
                Brush.linearGradient(
                    listOf(
                        base.lighten(0.14f),
                        base,
                        base.darken(0.16f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Spine strip
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(if (compact) 6.dp else 10.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.Black.copy(alpha = 0.22f),
                        ),
                    ),
                ),
        )
        // Double debossed hot-stamp frame (双重烫印微凹细边框)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val outerInset = if (compact) 4.dp.toPx() else 7.dp.toPx()
                    val innerInset = outerInset + (if (compact) 2.dp.toPx() else 3.dp.toPx())
                    val frameColor = Color.White.copy(alpha = 0.26f)
                    val frameInnerColor = Color.White.copy(alpha = 0.14f)
                    val stroke = 1.dp.toPx()

                    // Outer border
                    drawRect(
                        color = frameColor,
                        topLeft = Offset(outerInset, outerInset),
                        size = androidx.compose.ui.geometry.Size(
                            size.width - outerInset * 2,
                            size.height - outerInset * 2,
                        ),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                    )

                    // Inner fine accent border
                    if (!compact) {
                        drawRect(
                            color = frameInnerColor,
                            topLeft = Offset(innerInset, innerInset),
                            size = androidx.compose.ui.geometry.Size(
                                size.width - innerInset * 2,
                                size.height - innerInset * 2,
                            ),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.75.dp.toPx()),
                        )
                    }
                },
        )
        val glyph = extractCoverInitial(title)
            ?: stringResource(R.string.cover_fallback_character)
        Text(
            text = glyph,
            color = Color.White.copy(alpha = 0.96f),
            fontSize = if (compact) 22.sp else 36.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.35f),
                    offset = Offset(0f, 2f),
                    blurRadius = 4f,
                ),
            ),
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
    // surfaceContainer, not …Low: against the warm paper background the Low step differs by one
    // value out of 255, which left cards with no visible edge once the shadows came off.
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    /** Only things that genuinely float (sheets, the mini player, snack-like overlays) cast one. */
    floating: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Layering by surface step rather than by shadow: a page where every card is lifted has no
    // hierarchy at all, just a pile of floating boxes.
    val shadow = if (floating) 8.dp else 0.dp
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            shadowElevation = shadow,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            shadowElevation = shadow,
            content = content,
        )
    }
}

/**
 * Compact filter chip: 28dp tall, badge-radius corners, selected state a 12% wash of the primary
 * with primary text. Material's default chip is a 32dp pill with a border, which at four or five
 * across a row dominates whatever it is filtering.
 */
@Composable
fun TxChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraSmall,
        color = background,
        contentColor = content,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(28.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
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

/**
 * The one empty-state look for the whole app: a tinted icon disc, a title, a muted
 * body line, and an optional action. Previously every screen hand-rolled its own
 * and the icon blocks had drifted to 96/42/30dp.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // A small muted mark, not an 88dp circled icon: the sentence is the message here.
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

/**
 * Shared shelf/catalogue grid tile: portrait cover + two-line title slot + one-line
 * subtitle slot. Reserving both text lines keeps tile heights identical whether or
 * not the title wraps, so rows never jiggle between the shelf and online pages.
 */
@Composable
fun BookGridTile(
    title: String,
    coverPath: String?,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
    /** Small coloured tag rendered before [subtitle], e.g. 在线. */
    subtitleTag: String? = null,
    titleStyle: TextStyle = BookType.title,
    coverElevation: Dp = 0.dp,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    Column(
        // No rounded clip on the whole tile: the artwork clips itself, and a 14dp radius at the
        // bottom corners was shaving the first characters off the author line.
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .shadow(coverElevation, RoundedCornerShape(CoverCorner.Grid))
                .clip(RoundedCornerShape(CoverCorner.Grid)),
        ) {
            BookCover(
                title = title,
                coverPath = coverPath,
                modifier = Modifier.fillMaxWidth(),
                ratio = COVER_RATIO_PORTRAIT,
                corner = CoverCorner.Grid,
                framed = framed,
            )
            if (overlay != null) overlay()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = titleStyle,
            // No reserved second line: a grid row already levels its tiles, so single-line
            // titles used to leave a visible blank gap above the subtitle for nothing.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (subtitleTag != null) {
                Text(
                    subtitleTag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    " · ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                // Author sits clearly behind the title instead of competing with it.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Cards a long list (chapters, bookmarks) instead of letting rows float on the
 * background with hand-drawn dividers: rows keep their own layout, but the outer
 * card, the row padding and the inset dividers come from one place.
 */
@Composable
fun ListSectionCard(
    rowCount: Int,
    modifier: Modifier = Modifier,
    dividerStartIndent: Dp = 50.dp,
    rowContent: @Composable (index: Int) -> Unit,
) {
    SectionCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            for (index in 0 until rowCount) {
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = dividerStartIndent),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    )
                }
                rowContent(index)
            }
        }
    }
}

/** 887289 -> "88.7"; values under 10k keep the raw count. */
fun formatWordCount(count: Long): String {
    if (count < 10_000L) return count.toString()
    val wan = count / 10_000.0
    return if (wan >= 100) "%d".format(wan.toLong()) else "%.1f".format(wan)
}

/**
 * Circular play affordance laid on top of cover art. Tapping it starts playback directly instead
 * of routing through the book page, which is the one-tap path a shelf is for.
 */
@Composable
fun CoverPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    size: Dp = 28.dp,
    contentDescription: String? = null,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0xDD1A382B),
        border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.45f)),
        shadowElevation = 4.dp,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(size * 0.58f),
            )
        }
    }
}

/**
 * Cover-shaped placeholder with a travelling sheen, used while a network list is still loading so
 * the online page shows its layout instead of an empty screen.
 */
@Composable
fun ShimmerTile(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerSweep",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val sheen = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_RATIO_PORTRAIT)
                .clip(RoundedCornerShape(CoverCorner.Grid))
                .background(
                    Brush.linearGradient(
                        colors = listOf(base, sheen, base),
                        start = Offset(progress * 600f - 300f, 0f),
                        end = Offset(progress * 600f, 300f),
                    ),
                ),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(12.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(base),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(10.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(base),
        )
    }
}

/**
 * Compact search field, 46dp tall.
 *
 * Material's text field has a 56dp minimum and lays its text out against that; forcing a smaller
 * height on it clips the glyphs (Chinese loses its bottom edge first). Building the row by hand
 * keeps full control of the height without touching the text layout.
 */
@Composable
fun TxSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    searchEnabled: Boolean = true,
    clearContentDescription: String? = null,
    searchContentDescription: String? = null,
    focusRequester: FocusRequester? = null,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Surface(
        shape = shape,
        color = containerColor,
        modifier = modifier.height(46.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = if (onSearch != null) {
                        KeyboardOptions(imeAction = ImeAction.Search)
                    } else {
                        KeyboardOptions.Default
                    },
                    keyboardActions = if (onSearch != null) {
                        KeyboardActions(onSearch = { if (searchEnabled) onSearch() })
                    } else {
                        KeyboardActions.Default
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
                )
            }
            if (value.isNotEmpty() && onClear != null) {
                IconButton(onClick = onClear, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = clearContentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            if (onSearch != null) {
                val active = searchEnabled && value.isNotBlank()
                Surface(
                    onClick = { if (active) onSearch() },
                    enabled = active,
                    shape = CircleShape,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    },
                    contentColor = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = searchContentDescription,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
