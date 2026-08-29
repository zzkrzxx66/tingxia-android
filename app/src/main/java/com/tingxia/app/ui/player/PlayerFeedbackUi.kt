package com.tingxia.app.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tingxia.app.R
import com.tingxia.app.player.PlaybackFeedback

/**
 * Turns the 500 ms position polls into a continuously moving value.
 *
 * Without this the scrub bar and the mini-player ring advance in visible 500 ms steps,
 * which reads as the app lagging behind the audio. Each poll animates linearly over one
 * poll interval; anything larger than [PlaybackFeedback.SNAP_THRESHOLD_MS] is a seek or
 * a chapter change and snaps instead of sliding across the bar.
 *
 * Returned as an [Animatable] so callers can read `value` inside a draw lambda and
 * invalidate only the draw phase instead of recomposing at 60 fps.
 */
@Composable
fun rememberSmoothPositionMs(
    targetMs: Long,
    animate: Boolean,
): Animatable<Float, AnimationVector1D> {
    val position = remember { Animatable(targetMs.toFloat()) }
    LaunchedEffect(targetMs, animate) {
        val target = targetMs.toFloat()
        if (!animate || PlaybackFeedback.shouldSnap(position.value, target)) {
            position.snapTo(target)
        } else {
            position.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = PlaybackFeedback.POLL_INTERVAL_MS.toInt(),
                    easing = LinearEasing,
                ),
            )
        }
    }
    return position
}

/**
 * The player's progress bar: inactive track, buffered head, played track, thumb.
 *
 * Replaces a disabled Material [androidx.compose.material3.Slider] used as a visual only.
 * Drawing it here buys the two things streaming needs and the Slider cannot show: how far
 * the download has run ahead of the playhead, and a travelling highlight on the unbuffered
 * remainder while the player is stalled, so a silent gap looks like work in progress rather
 * than a frozen screen.
 *
 * @param position current playhead in ms, read inside the draw phase.
 * @param buffered buffered head in ms, read inside the draw phase.
 * @param indeterminate true while buffering or before the duration is known.
 */
@Composable
fun PlayerScrubTrack(
    position: () -> Float,
    buffered: () -> Float,
    durationMs: Float,
    scrubbing: Boolean,
    indeterminate: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = Color.White,
) {
    val trackHeight by animateDpAsState(
        targetValue = if (scrubbing) 6.dp else 4.dp,
        animationSpec = tween(180),
        label = "trackHeight",
    )
    val thumbRadius by animateDpAsState(
        targetValue = if (scrubbing) 9.dp else 6.dp,
        animationSpec = tween(180),
        label = "thumbRadius",
    )
    // The sweep only exists while stalled: an always-on infinite transition would keep the
    // frame clock awake for the whole listening session.
    val sweep = if (indeterminate) {
        val transition = rememberInfiniteTransition(label = "bufferSweep")
        val value by transition.animateFloat(
            initialValue = -0.25f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                tween(1_500, easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "bufferSweepX",
        )
        value
    } else {
        null
    }

    Canvas(modifier = modifier.fillMaxWidth().height(24.dp)) {
        val stroke = trackHeight.toPx()
        val radius = thumbRadius.toPx()
        val centerY = size.height / 2f
        // Inset by the thumb radius so the thumb never gets clipped at either end.
        val left = radius
        val usable = (size.width - radius * 2f).coerceAtLeast(1f)
        val playedFraction = if (durationMs > 0f) (position() / durationMs).coerceIn(0f, 1f) else 0f
        val bufferedFraction = PlaybackFeedback.bufferedFraction(
            bufferedMs = buffered().toLong(),
            positionMs = position().toLong(),
            durationMs = durationMs.toLong(),
        )

        drawLine(
            color = accent.copy(alpha = 0.22f),
            start = Offset(left, centerY),
            end = Offset(left + usable, centerY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        if (bufferedFraction != null) {
            drawLine(
                color = accent.copy(alpha = 0.40f),
                start = Offset(left, centerY),
                end = Offset(left + usable * bufferedFraction, centerY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        if (sweep != null) {
            // Highlight travels only over the part that is not playable yet.
            val bandWidth = usable * 0.28f
            val bandStart = left + usable * sweep - bandWidth / 2f
            clipRect(
                left = left + usable * playedFraction,
                top = centerY - stroke,
                right = left + usable,
                bottom = centerY + stroke,
            ) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, accent.copy(alpha = 0.55f), Color.Transparent),
                        startX = bandStart,
                        endX = bandStart + bandWidth,
                    ),
                    topLeft = Offset(left, centerY - stroke / 2f),
                    size = Size(usable, stroke),
                )
            }
        }
        if (playedFraction > 0f) {
            drawLine(
                color = accent,
                start = Offset(left, centerY),
                end = Offset(left + usable * playedFraction, centerY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(
            color = accent,
            radius = radius,
            center = Offset(left + usable * playedFraction, centerY),
        )
    }
}

/** Three dots breathing in sequence — the "audio is on its way" mark used next to labels. */
@Composable
fun PulsingDots(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    dotRadius: Dp = 2.5.dp,
) {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart),
        label = "dotPhase",
    )
    Canvas(modifier = modifier.size(width = dotRadius * 9, height = dotRadius * 2)) {
        val r = dotRadius.toPx()
        val gap = r * 3f
        repeat(3) { index ->
            val raw = (phase - index + 3f) % 3f
            val distance = if (raw > 1.5f) 3f - raw else raw
            val alpha = 0.28f + 0.72f * (1f - (distance / 1.2f).coerceIn(0f, 1f))
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = r,
                center = Offset(r + index * gap, size.height / 2f),
            )
        }
    }
}

/** Small translucent capsule reading e.g. "缓冲中", used on top of the player artwork. */
@Composable
fun BufferingPill(
    text: String,
    modifier: Modifier = Modifier,
    onArtwork: Boolean = true,
) {
    val content = if (onArtwork) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = CircleShape,
        color = content.copy(alpha = 0.16f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulsingDots(color = content)
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.92f),
            )
        }
    }
}

/**
 * Play/pause glyph that cross-fades and scales between the two states instead of
 * swapping instantly, which is what made the primary control feel unresponsive.
 */
@Composable
fun PlayPauseGlyph(
    isPlaying: Boolean,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            (fadeIn(tween(140)) + scaleIn(tween(220), initialScale = 0.7f)) togetherWith
                (fadeOut(tween(110)) + scaleOut(tween(180), targetScale = 0.7f))
        },
        label = "playPause",
        modifier = modifier,
    ) { playing ->
        Icon(
            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}

/** Indeterminate ring drawn around a transport button while the chapter loads. */
@Composable
fun LoadingRing(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    strokeWidth: Dp = 3.dp,
) {
    Box(modifier = modifier) {
        CircularProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = color.copy(alpha = 0.22f),
            strokeWidth = strokeWidth,
            strokeCap = StrokeCap.Round,
        )
    }
}
