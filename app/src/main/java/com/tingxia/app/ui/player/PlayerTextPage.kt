package com.tingxia.app.ui.player

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tingxia.app.R
import com.tingxia.app.data.remote.FqChapterTimeline
import com.tingxia.app.player.SeekOffsets
import com.tingxia.app.ui.components.ReadAlongList
import com.tingxia.app.ui.components.activeSentenceIndex
import com.tingxia.app.ui.components.formatDuration
import com.tingxia.app.ui.components.rememberReadAlongRanges

/**
 * The player's second page: the chapter text, read like lyrics.
 *
 * When the chapter carries trustworthy sentence timings the spoken sentence is lit, the
 * rest recedes, the list keeps it centred, and a tap seeks the audio there. Without
 * timings (narrated editions, whose time points belong to an older transcript) this is
 * plain reading — highlighting the wrong sentence is worse than highlighting none.
 */
@Composable
fun PlayerTextPage(
    timeline: FqChapterTimeline?,
    loading: Boolean,
    error: String?,
    chapterTitle: String,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    playbackPositionMs: Long = positionMs,
    onSeekBy: (Long) -> Unit = {},
    fontSize: Float = 17f,
    onFontSizeChange: (Float) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    var follow by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val ranges = rememberReadAlongRanges(timeline)
    val synced = timeline?.synced == true

    // The player reports its position every 500 ms; a sentence lasts a few seconds, so the
    // highlight is interpolated between polls or every boundary would visibly lag.
    val smoothPosition = rememberSmoothPositionMs(targetMs = positionMs, animate = isPlaying && synced)
    val activeSentence by remember(timeline, synced) {
        derivedStateOf {
            val sentences = timeline?.sentences ?: return@derivedStateOf -1
            if (!synced) -1 else activeSentenceIndex(sentences, smoothPosition.value.toLong())
        }
    }

    // A manual drag hands control to the reader; the follow button takes it back.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) follow = false
        }
    }
    LaunchedEffect(timeline) { follow = true; listState.scrollToItem(0) }
    LaunchedEffect(activeSentence, follow) {
        if (!follow || activeSentence < 0) return@LaunchedEffect
        val paragraph = timeline?.sentences?.getOrNull(activeSentence)?.spans?.firstOrNull()?.paragraph
            ?: return@LaunchedEffect
        // Park the spoken line a third of the way down instead of at the very top.
        val offset = -(listState.layoutInfo.viewportSize.height * 0.35f).toInt()
        runCatching { listState.animateScrollToItem(paragraph.coerceAtLeast(0), offset) }
    }

    val activeAccent = Color(0xFFA6CDB8)
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(if (synced) R.string.chapter_text_synced else R.string.chapter_text_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (synced) {
                        activeAccent
                    } else {
                        Color.White.copy(alpha = 0.6f)
                    },
                )
                Text(
                    timeline?.title?.takeIf { it.isNotBlank() } ?: chapterTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (synced) {
                IconButton(onClick = { follow = !follow }) {
                    Icon(
                        if (follow) Icons.Default.MyLocation else Icons.Default.LocationSearching,
                        contentDescription = stringResource(
                            if (follow) R.string.chapter_text_following else R.string.chapter_text_follow,
                        ),
                        tint = if (follow) activeAccent else Color.White.copy(alpha = 0.7f),
                    )
                }
            }
            IconButton(
                onClick = { onFontSizeChange((fontSize - 1f).coerceAtLeast(12f)) },
                enabled = fontSize > 12f,
            ) {
                Icon(
                    Icons.Default.TextDecrease,
                    contentDescription = stringResource(R.string.chapter_text_font_smaller),
                    tint = Color.White.copy(alpha = if (fontSize > 12f) 0.85f else 0.35f),
                )
            }
            IconButton(
                onClick = { onFontSizeChange((fontSize + 1f).coerceAtMost(26f)) },
                enabled = fontSize < 26f,
            ) {
                Icon(
                    Icons.Default.TextIncrease,
                    contentDescription = stringResource(R.string.chapter_text_font_larger),
                    tint = Color.White.copy(alpha = if (fontSize < 26f) 0.85f else 0.35f),
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        stringResource(R.string.chapter_text_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
                error != null -> Column(Modifier.padding(horizontal = 24.dp, vertical = 32.dp)) {
                    Text(error, color = Color.White.copy(alpha = 0.8f))
                    androidx.compose.material3.TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                timeline == null || timeline.paragraphs.isEmpty() -> Text(
                    stringResource(R.string.chapter_text_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 48.dp),
                )
                else -> ReadAlongList(
                    timeline = timeline,
                    ranges = ranges,
                    activeSentence = activeSentence,
                    fontSize = fontSize,
                    modifier = Modifier.fillMaxSize(),
                    listState = listState,
                    dimInactive = synced,
                    onColor = Color.White.copy(alpha = 0.92f),
                    highlightColor = Color(0xFFFFF8E8),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                    onTapSentence = if (!synced) {
                        null
                    } else {
                        { index ->
                            timeline.sentences.getOrNull(index)?.let { onSeek(it.startMs) }
                            follow = true
                        }
                    },
                )
            }
        }

        // Separate time/progress row from the 5 transport buttons so hour-long timestamps
        // (e.g. 1:23:45) never collide with buttons on 320dp screens or large font scales.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
        ) {
            if (durationMs > 0L) {
                val chapterFraction = (playbackPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { chapterFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(CircleShape),
                    color = Color.White.copy(alpha = 0.78f),
                    trackColor = Color.White.copy(alpha = 0.14f),
                    strokeCap = StrokeCap.Butt,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(4.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatDuration(playbackPositionMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                )
                Text(
                    if (durationMs > 0L) formatDuration(durationMs) else "--:--",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = Color.White.copy(alpha = 0.48f),
                    maxLines = 1,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.previous_chapter),
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = { onSeekBy(-SeekOffsets.LONG_MS) }, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.Default.Replay30,
                    contentDescription = stringResource(R.string.rewind_30_seconds),
                    tint = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(52.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.pause else R.string.play,
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(onClick = { onSeekBy(SeekOffsets.LONG_MS) }, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.Default.Forward30,
                    contentDescription = stringResource(R.string.forward_30_seconds),
                    tint = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.next_chapter),
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
