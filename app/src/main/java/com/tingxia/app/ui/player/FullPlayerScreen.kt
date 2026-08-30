package com.tingxia.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import com.tingxia.app.R
import kotlinx.coroutines.launch
import com.tingxia.app.player.PlaybackSpeeds
import com.tingxia.app.player.PlayerUiState
import com.tingxia.app.player.SeekOffsets
import com.tingxia.app.player.SleepOptions
import com.tingxia.app.ui.components.AmbientBackground
import com.tingxia.app.ui.components.BookCover
import com.tingxia.app.ui.components.SkipOffsetsDialog
import com.tingxia.app.ui.components.formatDuration
import com.tingxia.app.ui.theme.COVER_RATIO_PORTRAIT
import com.tingxia.app.ui.theme.CoverCorner
import com.tingxia.app.ui.theme.playerScrim

/** Soft drop shadow for white text sitting on blurred artwork of unknown brightness. */
private val onArtworkTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.45f),
    offset = Offset(0f, 1f),
    blurRadius = 8f,
)

/** Stand-in for a timestamp that is not known yet (online chapter still opening). */
private const val TIME_PLACEHOLDER = "--:--"

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
    onSaveSkipOffsets: (Long, Long) -> Unit = { _, _ -> },
    onOpenChapters: () -> Unit = {},
    /** Online books with a linked text edition get a second page beside the cover. */
    textAvailable: Boolean = false,
    timeline: com.tingxia.app.data.remote.FqChapterTimeline? = null,
    timelineLoading: Boolean = false,
    timelineError: String? = null,
    onEnsureTimeline: () -> Unit = {},
) {
    val pagerState = rememberPagerState(initialPage = PAGE_COVER, pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    // Load the text the first time the page is reached, not on every player open.
    LaunchedEffect(pagerState.currentPage, textAvailable, state.chapterId) {
        if (textAvailable && pagerState.currentPage == PAGE_TEXT) onEnsureTimeline()
    }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    var fineScrub by remember { mutableStateOf(false) }
    var showBookRemaining by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    var customSleepDialog by remember { mutableStateOf(false) }
    var customSleepMinutes by remember { mutableStateOf("10") }
    var skipOffsetsDialog by remember { mutableStateOf(false) }

    val duration = state.durationMs.coerceAtLeast(0L).toFloat()
    val position = if (scrubbing) scrubValue else state.positionMs.toFloat().coerceAtMost(duration)
    // One flag for "audio is not flowing yet": a first load and a mid-stream stall get the
    // same visual language, they just differ in how fast they are allowed to appear.
    val loading = state.isPreparing || state.isBuffering
    // Smoothed playhead for the bar only; the timestamps keep the polled value so they
    // never read a fraction ahead of the audio.
    val smoothPosition = rememberSmoothPositionMs(
        targetMs = state.positionMs,
        animate = state.isPlaying && !scrubbing,
    )
    val bufferedMs = state.bufferedMs

    val haptics = LocalHapticFeedback.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AmbientBackground(
                coverPath = state.coverPath,
                title = state.bookTitle.orEmpty(),
                scrim = playerScrim,
                modifier = Modifier.fillMaxSize(),
            )
            // Extra darkening concentrated behind the controls: bright covers otherwise
            // swallow the white slider and timestamps.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.32f),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.collapse_player),
                            tint = Color.White,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(
                            if (textAvailable && pagerState.currentPage == PAGE_TEXT) {
                                R.string.chapter_text_title
                            } else {
                                R.string.now_playing
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Spacer(Modifier.weight(1f))
                    // Shortcut to the other page; swiping does the same thing.
                    if (textAvailable) {
                        val onText = pagerState.currentPage == PAGE_TEXT
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(if (onText) PAGE_COVER else PAGE_TEXT)
                                }
                            },
                        ) {
                            Icon(
                                if (onText) Icons.Default.Album else Icons.Default.MenuBook,
                                contentDescription = stringResource(
                                    if (onText) R.string.player_page_cover else R.string.chapter_text_open,
                                ),
                                tint = Color.White,
                            )
                        }
                    } else {
                        // Balance the leading button so the title stays centred.
                        Spacer(Modifier.size(48.dp))
                    }
                }

                if (textAvailable) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(PAGE_COUNT) { page ->
                            val selected = pagerState.currentPage == page
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 7.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Color.White.copy(alpha = if (selected) 0.9f else 0.35f),
                                    ),
                            )
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    userScrollEnabled = textAvailable,
                    // The text page owns a vertical list; letting the pager win only on
                    // clearly horizontal drags keeps both gestures usable.
                    beyondViewportPageCount = 1,
                ) { page ->
                    if (page == PAGE_TEXT) {
                        PlayerTextPage(
                            timeline = timeline,
                            loading = timelineLoading,
                            error = timelineError,
                            chapterTitle = state.chapterTitle.orEmpty(),
                            positionMs = state.positionMs,
                            durationMs = state.durationMs,
                            isPlaying = state.isPlaying,
                            onSeek = onSeek,
                            onToggle = onToggle,
                            onPrev = onPrev,
                            onNext = onNext,
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                        Spacer(Modifier.height(12.dp))
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val coverSize = minOf(maxWidth * 0.74f, 280.dp)
                            // A slow, barely-there swell keeps the artwork alive while playing.
                            val breathing = rememberInfiniteTransition(label = "coverBreath")
                            val breathScale by breathing.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.02f,
                                animationSpec = infiniteRepeatable(
                                    tween(2600, easing = FastOutSlowInEasing),
                                    RepeatMode.Reverse,
                                ),
                                label = "coverScale",
                            )
                            // Ease back to rest on pause instead of snapping from mid-breath. Loading
                            // keeps breathing: a frozen cover is what made a stall feel like a crash.
                            val coverScale by animateFloatAsState(
                                targetValue = if (state.isPlaying || loading) breathScale else 1f,
                                animationSpec = tween(450, easing = FastOutSlowInEasing),
                                label = "coverSettle",
                            )
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                shadowElevation = 24.dp,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .scale(coverScale),
                            ) {
                                BookCover(
                                    title = state.bookTitle.orEmpty(),
                                    coverPath = state.coverPath,
                                    size = coverSize,
                                    ratio = COVER_RATIO_PORTRAIT,
                                    corner = CoverCorner.Hero,
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = state.bookTitle.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        // Chapter changes cross-fade: swapping two lines of text instantly is what made
                        // 上一章/下一章 feel like a jump cut.
                        AnimatedContent(
                            targetState = state.chapterTitle.orEmpty()
                                .ifEmpty { stringResource(R.string.nothing_playing) },
                            transitionSpec = {
                                (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 6 })
                                    .togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 6 })
                            },
                            label = "chapterTitle",
                            modifier = Modifier.fillMaxWidth(),
                        ) { title ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall.copy(shadow = onArtworkTextShadow),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (state.chapterCount > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(
                                    R.string.chapter_progress,
                                    state.chapterIndex + 1,
                                    state.chapterCount,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                        // Reserved by the animation, not by a fixed spacer: the pill expands the column
                        // instead of overlapping the artwork or the bar.
                        AnimatedVisibility(
                            visible = loading,
                            enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                            exit = fadeOut(tween(160)) + shrinkVertically(tween(200)),
                        ) {
                            BufferingPill(
                                text = stringResource(
                                    if (state.isPreparing) R.string.loading_chapter else R.string.buffering,
                                ),
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        Spacer(Modifier.height(20.dp))

                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            if (scrubbing) {
                                // Floating bubble tracking the thumb so long chapters can be
                                // scrubbed to a precise spot without guessing.
                                val fraction = if (duration > 0f) (scrubValue / duration).coerceIn(0f, 1f) else 0f
                                val bubbleWidth = 64.dp
                                val x = (maxWidth - bubbleWidth) * fraction
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = Color.White,
                                    shadowElevation = 6.dp,
                                    modifier = Modifier
                                        .offset(x = x, y = (-30).dp)
                                        .width(bubbleWidth),
                                ) {
                                    Text(
                                        text = formatDuration(scrubValue.toLong()),
                                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 5.dp),
                                    )
                                }
                                // Fine-scrub hint: pulling down while scrubbing switches to
                                // quarter sensitivity for long chapters.
                                Text(
                                    text = stringResource(
                                        if (fineScrub) R.string.fine_scrub_active else R.string.fine_scrub_hint,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = if (fineScrub) 1f else 0.65f),
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .offset(y = 26.dp),
                                )
                            }
                            // Custom scrub area: a transparent drag surface over the Slider
                            // visual. Horizontal drag scrubs; dragging downward past a
                            // threshold drops sensitivity to 1/4 (fine mode), anchored at the
                            // point fine mode was entered so the thumb doesn't jump.
                            var dragAnchorX by remember { mutableFloatStateOf(0f) }
                            var fineAnchorValue by remember { mutableFloatStateOf(0f) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .pointerInput(duration) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            if (duration <= 0f) return@awaitEachGesture
                                            scrubbing = true
                                            fineScrub = false
                                            dragAnchorX = down.position.x
                                            val widthPx = size.width.coerceAtLeast(1)
                                            scrubValue = (down.position.x / widthPx * duration).coerceIn(0f, duration)
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val fineThresholdPx = 40.dp.toPx()
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                    ?: break
                                                if (!change.pressed) break
                                                val pos = change.position
                                                val dy = pos.y - down.position.y
                                                val dx = pos.x - dragAnchorX
                                                val nowFine = dy > fineThresholdPx
                                                if (nowFine != fineScrub) {
                                                    // Re-anchor when entering fine mode so the
                                                    // value continues smoothly at 1/4 speed.
                                                    fineScrub = nowFine
                                                    dragAnchorX = pos.x
                                                    fineAnchorValue = scrubValue
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                                val effectiveDx = pos.x - dragAnchorX
                                                val sensitivity = if (fineScrub) 0.25f else 1f
                                                val base = if (fineScrub) fineAnchorValue else 0f
                                                val delta = effectiveDx / widthPx * duration * sensitivity
                                                scrubValue = if (fineScrub) {
                                                    (fineAnchorValue + delta).coerceIn(0f, duration)
                                                } else {
                                                    (down.position.x / widthPx * duration +
                                                        (pos.x - down.position.x) / widthPx * duration * sensitivity
                                                        ).coerceIn(0f, duration)
                                                }
                                                change.consume()
                                            }
                                            onSeek(scrubValue.toLong())
                                            scrubbing = false
                                            fineScrub = false
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                PlayerScrubTrack(
                                    position = { if (scrubbing) scrubValue else smoothPosition.value },
                                    buffered = { bufferedMs.toFloat() },
                                    durationMs = duration,
                                    scrubbing = scrubbing,
                                    // Also indeterminate before the duration lands: an online chapter
                                    // reports 0 until the first bytes arrive, and an empty bar with no
                                    // motion is indistinguishable from a broken one.
                                    indeterminate = loading || duration <= 0f,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            // Before the duration is known there is no honest number to show; a
                            // confident 0:00 / 本章剩余 0:00 reads as a broken chapter.
                            val timeUnknown = duration <= 0f
                            Text(
                                text = if (timeUnknown) TIME_PLACEHOLDER else formatDuration(position.toLong()),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFeatureSettings = "tnum",
                                    shadow = onArtworkTextShadow,
                                ),
                                color = Color.White.copy(alpha = 0.9f),
                            )
                            val speed = state.speed.coerceAtLeast(0.25f)
                            val chapterRemainingMs = ((duration - position) / speed).toLong().coerceAtLeast(0L)
                            val bookRemainingMs = ((state.bookDurationMs - state.bookPositionMs) / speed)
                                .toLong().coerceAtLeast(0L)
                            Text(
                                text = when {
                                    showBookRemaining && state.bookDurationMs > 0L ->
                                        stringResource(R.string.remaining_book, formatDuration(bookRemainingMs))
                                    timeUnknown ->
                                        stringResource(R.string.remaining_chapter, TIME_PLACEHOLDER)
                                    else ->
                                        stringResource(R.string.remaining_chapter, formatDuration(chapterRemainingMs))
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFeatureSettings = "tnum",
                                    shadow = onArtworkTextShadow,
                                ),
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { if (state.bookDurationMs > 0L) showBookRemaining = !showBookRemaining },
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    Icons.Default.SkipPrevious,
                                    contentDescription = stringResource(R.string.previous_chapter),
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                            IconButton(onClick = { onSeekBy(-SeekOffsets.LONG_MS) }, modifier = Modifier.size(52.dp)) {
                                Icon(
                                    Icons.Default.Replay30,
                                    contentDescription = stringResource(R.string.rewind_30_seconds),
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                            PlayerPrimaryButton(
                                isPlaying = state.isPlaying,
                                loading = loading,
                                onClick = onToggle,
                            )
                            IconButton(onClick = { onSeekBy(SeekOffsets.LONG_MS) }, modifier = Modifier.size(52.dp)) {
                                Icon(
                                    Icons.Default.Forward30,
                                    contentDescription = stringResource(R.string.forward_30_seconds),
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                            IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = stringResource(R.string.next_chapter),
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(28.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            PlayerToolButton(
                                onClick = onOpenChapters,
                                icon = Icons.Default.FormatListNumbered,
                                label = if (state.chapterCount > 0) {
                                    stringResource(
                                        R.string.chapter_picker_button,
                                        state.chapterIndex + 1,
                                        state.chapterCount,
                                    )
                                } else {
                                    stringResource(R.string.chapter_picker_title)
                                },
                                modifier = Modifier.weight(1f),
                                enabled = state.chapterCount > 0,
                            )
                            PlayerToolButton(
                                onClick = onAddBookmark,
                                icon = Icons.Default.BookmarkAdd,
                                label = stringResource(R.string.bookmark),
                                modifier = Modifier.weight(1f),
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                val speedActive = state.speed != 1.0f
                                PlayerToolButton(
                                    onClick = { speedMenu = true },
                                    icon = Icons.Default.Speed,
                                    label = if (speedActive) {
                                        PlaybackSpeeds.label(state.speed)
                                    } else {
                                        stringResource(R.string.playback_speed)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    active = speedActive,
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
                                val sleepActive = state.sleepRemainingMs != null ||
                                    state.sleepMode is com.tingxia.app.player.SleepTimerMode.EndOfChapter
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
                                    icon = if (sleepActive) Icons.Default.TimerOff else Icons.Default.Timer,
                                    label = sleepLabel,
                                    modifier = Modifier.fillMaxWidth(),
                                    active = sleepActive,
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
                            val skipActive = state.skipIntroMs > 0L || state.skipOutroMs > 0L
                            PlayerToolButton(
                                onClick = { skipOffsetsDialog = true },
                                icon = Icons.Default.ContentCut,
                                label = if (skipActive) {
                                    stringResource(
                                        R.string.skip_active_summary,
                                        state.skipIntroMs / 1_000L,
                                        state.skipOutroMs / 1_000L,
                                    )
                                } else {
                                    stringResource(R.string.skip_offsets)
                                },
                                modifier = Modifier.weight(1f),
                                active = skipActive,
                            )
                        }
                        Spacer(Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }

    if (skipOffsetsDialog) {
        SkipOffsetsDialog(
            initialIntroMs = state.skipIntroMs,
            initialOutroMs = state.skipOutroMs,
            onDismiss = { skipOffsetsDialog = false },
            onSave = onSaveSkipOffsets,
        )
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

/**
 * The 76dp transport button. While a chapter loads, an indeterminate ring runs around it and
 * the glyph fades to the loading tint, so the one control the listener is looking at answers
 * "did my tap register?" without a separate overlay.
 */
@Composable
private fun PlayerPrimaryButton(
    isPlaying: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "primaryPress",
    )
    val glyphTint by animateColorAsState(
        targetValue = if (loading) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(220),
        label = "primaryGlyphTint",
    )
    Box(
        modifier = modifier.size(90.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = loading,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
        ) {
            LoadingRing(modifier = Modifier.size(90.dp), strokeWidth = 3.dp)
        }
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            modifier = Modifier
                .size(76.dp)
                .scale(pressScale),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 10.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                PlayPauseGlyph(isPlaying = isPlaying, tint = glyphTint, size = 40.dp)
            }
        }
    }
}

@Composable
private fun PlayerToolButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    val tint = when {
        !enabled -> Color.White.copy(alpha = 0.35f)
        active -> Color.White
        else -> Color.White.copy(alpha = 0.75f)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // Width comes from the caller (a weight in the 5-up tool row); a fixed 72dp made the
        // row overflow on narrow phones once 选集 joined it.
        modifier = modifier.height(64.dp),
    ) {
        // Active tools sit on a soft halo; on the blurred artwork backdrop a plain
        // colour shift alone was too subtle to read as "on".
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = if (active) Color.White.copy(alpha = 0.24f) else Color.Transparent,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Player pages: the artwork with the full controls, and the chapter text. */
internal const val PAGE_COVER = 0
internal const val PAGE_TEXT = 1
internal const val PAGE_COUNT = 2
