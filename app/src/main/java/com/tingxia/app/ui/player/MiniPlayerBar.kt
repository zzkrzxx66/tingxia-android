package com.tingxia.app.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tingxia.app.R
import com.tingxia.app.player.PlayerUiState
import com.tingxia.app.ui.components.BookCover
import com.tingxia.app.ui.theme.BookType
import com.tingxia.app.ui.theme.rememberCoverAccent
import com.tingxia.app.ui.theme.CoverCorner

@Composable
fun MiniPlayerBar(
    state: PlayerUiState,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onNext: (() -> Unit)? = null,
) {
    // Floating capsule rather than a full-width bar: it reads as a control sitting above the
    // content, and the rounded card language now matches the rest of the app.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        // Card radius, not a stadium: at 28dp the capsule read as a pill stuck to the screen edge.
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onOpen),
    ) {
        val accent = rememberCoverAccent(state.coverPath)
        val loading = state.isPreparing || state.isBuffering
        // Same smoothing as the full player: the ring polls at 500ms, and stepping it looked
        // like the bar was lagging the audio.
        val smoothPosition = rememberSmoothPositionMs(
            targetMs = state.positionMs,
            animate = state.isPlaying,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                title = state.bookTitle.orEmpty(),
                coverPath = state.coverPath,
                size = 42.dp,
                corner = CoverCorner.Mini,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.bookTitle.orEmpty(),
                    style = BookType.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The second line carries the loading state: when audio stops the listener looks
                // here first, and a silent bar with a stale chapter title explains nothing.
                AnimatedContent(
                    targetState = when {
                        state.isPreparing -> stringResource(R.string.loading_chapter)
                        state.isBuffering -> stringResource(R.string.buffering)
                        else -> state.chapterTitle.orEmpty()
                    },
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 3 })
                            .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(180)) { -it / 3 })
                    },
                    label = "miniSubtitle",
                    modifier = Modifier.fillMaxWidth(),
                ) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (loading) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Progress became a ring around the play button: a hairline across a rounded capsule
            // read as a crack, and the ring puts position where the thumb already is.
            Box(contentAlignment = Alignment.Center) {
                // While loading the same ring spins instead of holding a frozen arc, so the
                // control never looks stuck at a position that is not advancing.
                Crossfade(targetState = loading, animationSpec = tween(220), label = "miniRing") { busy ->
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(38.dp),
                            strokeWidth = 2.dp,
                            color = accent,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            strokeCap = StrokeCap.Round,
                        )
                    } else {
                        // A full track ring, no gap: at 3% the lone arc read as a broken stroke
                        // rather than progress.
                        CircularProgressIndicator(
                            progress = {
                                if (state.durationMs > 0) {
                                    (smoothPosition.value / state.durationMs).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                            },
                            // 38dp hugs the 34dp button: at 42dp the arc floated a ring away.
                            modifier = Modifier.size(38.dp),
                            strokeWidth = 2.dp,
                            color = accent,
                            // outlineVariant sits within 10% of surfaceContainerHigh, so the track
                            // was invisible and the arc read as a stray stroke.
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                        )
                    }
                }
                Surface(
                    onClick = onToggle,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PlayPauseGlyph(
                            isPlaying = state.isPlaying,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            size = 20.dp,
                        )
                    }
                }
            }
            if (onNext != null) {
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.next_chapter),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
