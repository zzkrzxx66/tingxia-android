package com.tingxia.app.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Speed
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
import com.tingxia.app.ui.theme.COVER_RATIO_PORTRAIT
import com.tingxia.app.ui.theme.CoverCorner

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
                        text = stringResource(R.string.now_playing),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // Portrait artwork is taller than it is wide, so cap on width more tightly
                // than the old square did or it eats the controls below.
                val coverSize = minOf(maxWidth * 0.62f, 250.dp)
                BookCover(
                    title = state.bookTitle.orEmpty(),
                    coverPath = state.coverPath,
                    size = coverSize,
                    ratio = COVER_RATIO_PORTRAIT,
                    corner = CoverCorner.Hero,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = state.bookTitle.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.chapterTitle.orEmpty().ifEmpty { stringResource(R.string.nothing_playing) },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.chapterCount > 0) {
                Text(
                    text = stringResource(
                        R.string.chapter_progress,
                        state.chapterIndex + 1,
                        state.chapterCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(18.dp))

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
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
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
            if (state.skipIntroMs > 0L || state.skipOutroMs > 0L) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.size(5.dp))
                    Text(
                        text = stringResource(
                            R.string.skip_active_summary,
                            state.skipIntroMs / 1_000L,
                            state.skipOutroMs / 1_000L,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { onSeekBy(-SeekOffsets.SHORT_MS) }) {
                    Text(stringResource(R.string.rewind_15_seconds_short))
                }
                TextButton(onClick = { onSeekBy(SeekOffsets.SHORT_MS) }) {
                    Text(stringResource(R.string.forward_15_seconds_short))
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.previous_chapter), modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = { onSeekBy(-SeekOffsets.LONG_MS) }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.Replay30, contentDescription = stringResource(R.string.rewind_30_seconds), modifier = Modifier.size(30.dp))
                }
                FilledIconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(68.dp),
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.pause else R.string.play,
                        ),
                        modifier = Modifier.size(38.dp),
                    )
                }
                IconButton(onClick = { onSeekBy(SeekOffsets.LONG_MS) }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.Forward30, contentDescription = stringResource(R.string.forward_30_seconds), modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next_chapter), modifier = Modifier.size(28.dp))
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerToolButton(
                    onClick = onAddBookmark,
                    icon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) },
                    label = stringResource(R.string.bookmark),
                    modifier = Modifier.weight(1f),
                )
                Box(modifier = Modifier.weight(1f)) {
                    PlayerToolButton(
                        onClick = { speedMenu = true },
                        icon = { Icon(Icons.Default.Speed, contentDescription = null) },
                        label = PlaybackSpeeds.label(state.speed),
                        modifier = Modifier.fillMaxWidth(),
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
                Box(modifier = Modifier.weight(1f)) {
                    val sleepLabel = when {
                        state.sleepRemainingMs != null -> {
                            val ms = state.sleepRemainingMs!!
                            val m = (ms / 60_000).toInt()
                            val s = ((ms % 60_000) / 1000).toInt()
                            "%d:%02d".format(m, s)
                        }
                        state.sleepMode is com.tingxia.app.player.SleepTimerMode.EndOfChapter ->
                            stringResource(R.string.end_of_chapter)
                        else -> stringResource(R.string.sleep)
                    }
                    PlayerToolButton(
                        onClick = { sleepMenu = true },
                        icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                        label = sleepLabel,
                        modifier = Modifier.fillMaxWidth(),
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
                            text = { Text(stringResource(R.string.end_of_chapter)) },
                            onClick = {
                                onSleepEndOfChapter()
                                sleepMenu = false
                            },
                        )
                        if (state.sleepRemainingMs != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.extend_15_minutes)) },
                                onClick = {
                                    onExtendSleep()
                                    sleepMenu = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.custom_duration)) },
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
            title = { Text(stringResource(R.string.custom_sleep_timer)) },
            text = {
                OutlinedTextField(
                    value = customSleepMinutes,
                    onValueChange = { value ->
                        if (value.length <= 4 && value.all(Char::isDigit)) customSleepMinutes = value
                    },
                    label = { Text(stringResource(R.string.minutes)) },
                    supportingText = { Text(stringResource(R.string.sleep_minutes_range)) },
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
                ) { Text(stringResource(R.string.start)) }
            },
            dismissButton = {
                TextButton(onClick = { customSleepDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun PlayerToolButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(62.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            icon()
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
