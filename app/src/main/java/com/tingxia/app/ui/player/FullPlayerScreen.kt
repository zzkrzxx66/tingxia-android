package com.tingxia.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.material3.Surface
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.tingxia.app.player.PlaybackSpeeds
import com.tingxia.app.player.PlayerUiState
import com.tingxia.app.R
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
    onUseGlobalSpeed: () -> Unit = {},
    onSleep: (Int) -> Unit,
    onSleepEndOfChapter: () -> Unit = {},
    onExtendSleep: () -> Unit = {},
    onAddBookmark: () -> Unit = {},
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    var speedMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    var customSleepDialog by remember { mutableStateOf(false) }
    var customSleepMinutes by remember { mutableStateOf("10") }

    val duration = state.durationMs.coerceAtLeast(0L).toFloat()
    val position = if (scrubbing) scrubValue else state.positionMs.toFloat().coerceAtMost(duration)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
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
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (state.usesBookSpeedOverride) R.string.use_global_speed
                                        else R.string.using_global_speed,
                                    ),
                                )
                            },
                            onClick = {
                                onUseGlobalSpeed()
                                speedMenu = false
                            },
                        )
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
                        state.sleepMode is com.tingxia.app.player.SleepTimerMode.EndOfChapter -> "本章结束"
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
                        if (state.sleepRemainingMs != null) {
                            DropdownMenuItem(
                                text = { Text("延长 15 分钟") },
                                onClick = {
                                    onExtendSleep()
                                    sleepMenu = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("自定义…") },
                            onClick = {
                                sleepMenu = false
                                customSleepDialog = true
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (customSleepDialog) {
        val minutes = customSleepMinutes.toIntOrNull()
        val valid = minutes != null && minutes in 1..1_440
        AlertDialog(
            onDismissRequest = { customSleepDialog = false },
            title = { Text("自定义睡眠定时") },
            text = {
                OutlinedTextField(
                    value = customSleepMinutes,
                    onValueChange = { value ->
                        if (value.length <= 4 && value.all(Char::isDigit)) customSleepMinutes = value
                    },
                    label = { Text("分钟") },
                    supportingText = { Text("可设置 1–1440 分钟") },
                    isError = customSleepMinutes.isNotEmpty() && !valid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        onSleep(minutes!!)
                        customSleepDialog = false
                    },
                ) { Text("开始") }
            },
            dismissButton = {
                TextButton(onClick = { customSleepDialog = false }) { Text("取消") }
            },
        )
    }
}
