package com.tingxia.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tingxia.app.player.PlaybackSpeeds
import com.tingxia.app.player.PlayerUiState
import com.tingxia.app.player.SeekOffsets
import com.tingxia.app.player.SleepOptions
import com.tingxia.app.ui.components.BookCover
import com.tingxia.app.ui.components.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    state: PlayerUiState,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSpeed: (Float) -> Unit,
    onSleep: (Int) -> Unit,
    onSleepEndOfChapter: () -> Unit = {},
    onAddBookmark: () -> Unit = {},
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    var speedMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }

    val duration = state.durationMs.coerceAtLeast(0L).toFloat()
    val position = if (scrubbing) scrubValue else state.positionMs.toFloat().coerceAtMost(duration)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.bookTitle.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            BookCover(
                title = state.bookTitle.orEmpty(),
                coverPath = state.coverPath,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .padding(8.dp),
                corner = 16.dp,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = state.chapterTitle.orEmpty().ifEmpty { "未在播放" },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.chapterCount > 0) {
                Text(
                    text = "第 ${state.chapterIndex + 1} / ${state.chapterCount} 章",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))

            Slider(
                value = if (duration > 0f) position.coerceIn(0f, duration) else 0f,
                onValueChange = {
                    scrubbing = true
                    scrubValue = it
                },
                onValueChangeFinished = {
                    onSeek(scrubValue.toLong())
                    scrubbing = false
                },
                valueRange = 0f..(duration.takeIf { it > 0f } ?: 1f),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDuration(position.toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatDuration(state.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrev, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一章", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { onSeekBy(-SeekOffsets.LONG_MS) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Replay30, contentDescription = "后退 30 秒", modifier = Modifier.size(32.dp))
                }
                FilledIconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { onSeekBy(SeekOffsets.LONG_MS) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Forward30, contentDescription = "前进 30 秒", modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一章", modifier = Modifier.size(36.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = { onSeekBy(-SeekOffsets.SHORT_MS) }) {
                    Text(
                        text = "-15s",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { onSeekBy(SeekOffsets.SHORT_MS) }) {
                    Text(
                        text = "+15s",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                AssistChip(
                    onClick = onAddBookmark,
                    label = { Text("书签") },
                    leadingIcon = { Icon(Icons.Default.BookmarkAdd, contentDescription = "书签") },
                )
                Box {
                    AssistChip(
                        onClick = { speedMenu = true },
                        label = { Text(PlaybackSpeeds.label(state.speed)) },
                        leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                    )
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        PlaybackSpeeds.ALL.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(PlaybackSpeeds.label(s)) },
                                onClick = {
                                    onSpeed(s)
                                    speedMenu = false
                                },
                            )
                        }
                    }
                }
                Box {
                    val sleepLabel = when {
                        state.sleepRemainingMs != null -> {
                            val ms = state.sleepRemainingMs!!
                            val m = (ms / 60_000).toInt()
                            val s = ((ms % 60_000) / 1000).toInt()
                            "%d:%02d".format(m, s)
                        }
                        state.sleepMode.toString().contains("EndOfChapter") -> "本章结束"
                        else -> "睡眠"
                    }
                    AssistChip(
                        onClick = { sleepMenu = true },
                        label = { Text(sleepLabel) },
                        leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                    )
                    DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                        SleepOptions.MINUTES.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(SleepOptions.label(m)) },
                                onClick = {
                                    onSleep(m)
                                    sleepMenu = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("本章结束") },
                            onClick = {
                                onSleepEndOfChapter()
                                sleepMenu = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
