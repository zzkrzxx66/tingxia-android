package com.tingxia.app.ui.book

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingxia.app.R
import com.tingxia.app.data.model.Bookmark
import com.tingxia.app.data.model.Chapter
import com.tingxia.app.data.model.ChapterPicker
import com.tingxia.app.ui.chapters.ChapterSelectionBar
import com.tingxia.app.ui.chapters.ChapterToolbar
import com.tingxia.app.ui.chapters.chapterGroupItems
import com.tingxia.app.ui.components.AmbientBackground
import com.tingxia.app.ui.components.BookCover
import com.tingxia.app.ui.components.EmptyState
import com.tingxia.app.ui.components.ListSectionCard
import com.tingxia.app.ui.components.SkipOffsetsDialog
import com.tingxia.app.ui.components.formatDuration
import com.tingxia.app.ui.components.formatWordCount
import com.tingxia.app.ui.theme.COVER_RATIO_PORTRAIT
import com.tingxia.app.ui.theme.CoverCorner
import com.tingxia.app.ui.theme.playerScrim
import com.tingxia.app.ui.theme.rememberCoverAccent
import kotlinx.coroutines.launch

// Lazy items placed before the chapter rows: the book header and the sticky tab/toolbar block.
private const val CHAPTER_ITEMS_OFFSET = 2

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
@androidx.annotation.OptIn(UnstableApi::class)
fun BookDetailScreen(
    bookId: Long,
    onBack: () -> Unit,
    onPlayChapter: (Long) -> Unit,
    onContinue: () -> Unit,
    onPlayBookmark: (chapterId: Long, positionMs: Long) -> Unit = { _, _ -> },
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val prefetchState by viewModel.prefetchState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val reauthing by viewModel.reauthing.collectAsStateWithLifecycle()
    val reauthProgress by viewModel.reauthProgress.collectAsStateWithLifecycle()
    val rescanning by viewModel.rescanning.collectAsStateWithLifecycle()
    val rescanProgress by viewModel.rescanProgress.collectAsStateWithLifecycle()
    val rescanPreview by viewModel.rescanPreview.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val metaSync by viewModel.metaSync.collectAsStateWithLifecycle()
    val controls by viewModel.chapterControls.collectAsStateWithLifecycle()
    var confirmRemove by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var editBook by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editAuthor by remember { mutableStateOf("") }
    var editChapter by remember { mutableStateOf<Chapter?>(null) }
    var editChapterTitle by remember { mutableStateOf("") }
    var editBookmark by remember { mutableStateOf<Bookmark?>(null) }
    var editBookmarkNote by remember { mutableStateOf("") }
    var bookmarkMenuFor by remember { mutableStateOf<Bookmark?>(null) }
    var editSkipOffsets by remember { mutableStateOf(false) }
    var autoPlayDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val reauthTree = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.reauthFolder(uri)
    }
    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.updateBookCover(uri)
    }

    // Chapter rows are one lazy item each (真惰性), grouped into sticky 100-chapter blocks.
    // The 选集 strip and the jump-to-current scroll both address the flat lazy index.
    val chapterGroups = remember(chapters, controls.query, controls.filter, controls.order) {
        ChapterPicker.group(
            visible = ChapterPicker.visible(
                chapters = chapters,
                query = controls.query,
                filter = controls.filter,
                order = controls.order,
            ),
            grouped = ChapterPicker.shouldGroup(controls.query, controls.filter),
        )
    }
    val visibleChapters = remember(chapterGroups) { chapterGroups.flatMap { it.chapters } }

    // Jump straight to the chapter in progress on first load; long books otherwise
    // open at chapter 1 and force a manual hunt. One item per chapter means the exact
    // lazy index is known, so no row-height estimate is involved any more.
    // Runs once per book, never again, so the user's own scrolling is never yanked back.
    val listState = rememberLazyListState()
    // scrollToItem parks an item at the very top of the viewport, which here sits *under* the
    // pinned tab/toolbar block and the status bar — so the chapter it aimed at ended up hidden
    // and the first readable row was some later chapter. A negative offset leaves room for both.
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    // Estimate first, measure second: the pinned block may not be composed yet when the jump runs
    // (a tall book header can fill the viewport on its own), and blocking on the measurement would
    // mean no jump at all.
    var controlsHeightPx by remember { mutableIntStateOf(with(density) { 136.dp.roundToPx() }) }
    var controlsMeasured by remember { mutableStateOf(false) }
    val stickyOffsetPx = { -(controlsHeightPx + statusBarPx) }
    var scrolledToCurrent by remember { mutableStateOf(false) }
    var offsetCorrected by remember { mutableStateOf(false) }
    LaunchedEffect(chapterGroups, book?.currentChapterId, controlsMeasured) {
        if (chapters.isEmpty()) return@LaunchedEffect
        // Wait for the book flow before declaring failure: marking the jump as done
        // while book is still null would permanently skip it when the book arrives late.
        val currentChapterId = book?.currentChapterId ?: return@LaunchedEffect
        val target = ChapterPicker.flatIndexOf(chapterGroups, currentChapterId)
            ?: return@LaunchedEffect // chapter may be mid-reshuffle; retry on next emission
        val item = CHAPTER_ITEMS_OFFSET + target
        if (!scrolledToCurrent) {
            listState.scrollToItem(item, stickyOffsetPx())
            scrolledToCurrent = true
            return@LaunchedEffect
        }
        // One correction once the real height is known, and only while the list is still parked
        // where the jump left it, so the user's own scrolling is never yanked back.
        if (controlsMeasured && !offsetCorrected) {
            offsetCorrected = true
            // A negative offset leaves the previous row (or the group header) partly visible, so
            // the parked position is a small window around the target, not the exact index.
            if (!listState.isScrollInProgress && listState.firstVisibleItemIndex in (item - 2)..item) {
                listState.scrollToItem(item, stickyOffsetPx())
            }
        }
    }

    // Which 选集 chip reads as active: the block the list is parked on.
    val activeGroupIndex by remember(chapterGroups) {
        derivedStateOf {
            val position = (listState.firstVisibleItemIndex - CHAPTER_ITEMS_OFFSET).coerceAtLeast(0)
            var offset = 0
            chapterGroups.indexOfFirst { group ->
                val size = (if (group.label != null) 1 else 0) + group.chapters.size
                val contains = position < offset + size
                offset += size
                contains
            }.coerceAtLeast(0)
        }
    }

    val chapterHeaderColor = MaterialTheme.colorScheme.background

    // Back leaves multi-select first; it should not drop the whole screen mid-selection.
    androidx.activity.compose.BackHandler(enabled = controls.selectionMode) {
        viewModel.clearChapterSelection()
    }

    fun scrollToChapterIndex(target: Int) {
        scope.launch {
            listState.animateScrollToItem(CHAPTER_ITEMS_OFFSET + target, stickyOffsetPx())
        }
    }

    // Long books meant flicking upward for a dozen swipes to reach the header again.
    val showScrollTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 6 }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            AnimatedVisibility(
                visible = showScrollTop,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
            ) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.back_to_top),
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            item {
                // Immersive header: blurred artwork behind, crisp metadata on top.
                // The backdrop stays a fixed 340dp shell. heightIn(min=...) was tempting
                // but lazy-list items inherit a viewport-sized maxHeight, so the
                // backdrop's greedy Box stretched to fill the whole screen above the
                // fold, leaving a huge empty blurred band below the cover row.
                Box(modifier = Modifier.fillMaxWidth()) {
                    AmbientBackground(
                        coverPath = book?.coverPath,
                        title = book?.title.orEmpty(),
                        scrim = playerScrim,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.5f to Color.Transparent,
                                    1f to MaterialTheme.colorScheme.background,
                                ),
                            ),
                    )
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HeaderIconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                            Spacer(Modifier.weight(1f))
                            Box {
                                HeaderIconButton(onClick = { menu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.edit_book_info)) },
                                        onClick = {
                                            menu = false
                                            editTitle = book?.title.orEmpty()
                                            editAuthor = book?.author.orEmpty()
                                            editBook = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.change_cover)) },
                                        onClick = {
                                            menu = false
                                            coverPicker.launch("image/*")
                                        },
                                    )
                                    if (!book?.coverPath.isNullOrBlank()) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.remove_cover)) },
                                            onClick = {
                                                menu = false
                                                viewModel.updateBookCover(null)
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(stringResource(R.string.skip_intro_outro))
                                                Text(
                                                    stringResource(
                                                        R.string.skip_intro_outro_summary,
                                                        (book?.skipIntroMs ?: 0L) / 1_000L,
                                                        (book?.skipOutroMs ?: 0L) / 1_000L,
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            menu = false
                                            editSkipOffsets = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(stringResource(R.string.auto_play_next))
                                                Text(
                                                    stringResource(
                                                        if (book?.autoPlayNext != false) R.string.on else R.string.off,
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            menu = false
                                            autoPlayDialog = true
                                        },
                                    )
                                    if (book?.isRemote == true) {
                                        val pf = prefetchState
                                        val runningForThis = pf.running && pf.bookId == bookId
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(stringResource(R.string.cache_all_chapters))
                                                    if (runningForThis) {
                                                        Text(
                                                            stringResource(
                                                                R.string.cache_status_running,
                                                                pf.doneCount, pf.totalCount,
                                                            ),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                menu = false
                                                viewModel.prefetch()
                                            },
                                            enabled = !runningForThis,
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.cache_next_20)) },
                                            onClick = {
                                                menu = false
                                                val fromIndex = chapters.firstOrNull {
                                                    it.completionState != 2
                                                }?.index ?: 0
                                                viewModel.prefetch(fromIndex = fromIndex, count = 20)
                                            },
                                            enabled = !runningForThis,
                                        )
                                        if (runningForThis) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.cancel)) },
                                                onClick = {
                                                    menu = false
                                                    viewModel.cancelPrefetch()
                                                },
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.cache_clear_book)) },
                                            onClick = {
                                                menu = false
                                                viewModel.clearBookCache()
                                            },
                                        )
                                    }
                                    if (book?.isRemote != true) {
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(stringResource(R.string.meta_sync_menu))
                                                    if (book?.hasSyncedOnlineMeta == true) {
                                                        Text(
                                                            stringResource(R.string.meta_sync_menu_synced),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                menu = false
                                                viewModel.openMetaSync()
                                            },
                                        )
                                        if (book?.hasSyncedOnlineMeta == true) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.meta_sync_clear)) },
                                                onClick = {
                                                    menu = false
                                                    viewModel.clearOnlineMeta()
                                                },
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.rescan_folder)) },
                                            onClick = {
                                                menu = false
                                                viewModel.startRescan()
                                            },
                                            enabled = !rescanning && book?.needsReauth != true,
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.reauthorize_folder)) },
                                            onClick = {
                                                menu = false
                                                reauthTree.launch(null)
                                            },
                                            enabled = !reauthing,
                                        )
                                    }
                                    val allCompleted = chapters.isNotEmpty() && chapters.all { it.completionState == 2 }
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (allCompleted) R.string.clear_book_completion
                                                    else R.string.mark_book_completed,
                                                ),
                                            )
                                        },
                                        onClick = {
                                            menu = false
                                            viewModel.setAllChaptersCompleted(!allCompleted)
                                        },
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.remove_from_shelf),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            menu = false
                                            confirmRemove = true
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                shadowElevation = 12.dp,
                            ) {
                                BookCover(
                                    title = book?.title.orEmpty(),
                                    coverPath = book?.coverPath,
                                    size = 112.dp,
                                    ratio = COVER_RATIO_PORTRAIT,
                                    corner = CoverCorner.Detail,
                                    framed = true,
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    book?.title.orEmpty(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                book?.author?.takeIf { it.isNotBlank() }?.let {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.85f),
                                    )
                                }
                                if (book?.isRemote == true) {
                                    Spacer(Modifier.height(8.dp))
                                    Surface(
                                        shape = MaterialTheme.shapes.extraSmall,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Default.Headphones,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                stringResource(R.string.online_narrated),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(
                                        R.string.book_chapter_duration,
                                        chapters.size,
                                        formatDuration(book?.totalDurationMs ?: 0),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f),
                                )
                                book?.wordCount?.takeIf { it > 0 }?.let { words ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        stringResource(R.string.word_count_wan, formatWordCount(words)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.75f),
                                    )
                                }
                                book?.category?.takeIf { it.isNotBlank() }?.let { category ->
                                    Spacer(Modifier.height(8.dp))
                                    Surface(
                                        shape = MaterialTheme.shapes.extraSmall,
                                        color = Color.White.copy(alpha = 0.16f),
                                    ) {
                                        Text(
                                            category,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    book?.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Spacer(Modifier.height(16.dp))
                        var expanded by remember { mutableStateOf(false) }
                        Text(
                            stringResource(R.string.book_description),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            description.trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(if (expanded) R.string.collapse else R.string.expand),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable { expanded = !expanded }
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                        )
                    }
                    if ((book?.lastPlayedAt ?: 0) > 0) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(R.string.listening_progress),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(
                                    R.string.remaining_time,
                                    formatDuration(
                                        ((book?.totalDurationMs ?: 0L) -
                                            (book?.linearPositionMs ?: 0L)).coerceAtLeast(0L),
                                    ),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { book?.progressFraction ?: 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(MaterialTheme.shapes.extraSmall),
                            color = rememberCoverAccent(book?.coverPath),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Butt,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    val continueLabel = book?.let { b ->
                        if (b.lastPlayedAt > 0 && b.currentChapterId != null) {
                            chapters.firstOrNull { it.id == b.currentChapterId }?.let { ch ->
                                stringResource(
                                    R.string.continue_chapter_at,
                                    ch.displayTitle,
                                    formatDuration(b.currentPositionMs),
                                )
                            }
                        } else {
                            null
                        }
                    }
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = book?.needsReauth != true && !reauthing && !rescanning,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(
                                    if ((book?.lastPlayedAt ?: 0) > 0) R.string.continue_playback
                                    else R.string.start_playback,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            continueLabel?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (book?.needsReauth == true && book?.isRemote != true) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    stringResource(R.string.folder_permission_lost),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { reauthTree.launch(null) },
                                    enabled = !reauthing,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        stringResource(
                                            if (reauthing) R.string.reauthorizing else R.string.reauthorize_folder,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    if (rescanning) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.scanning_item, rescanProgress?.currentName.orEmpty()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
            // Tabs + chapter toolbar stay pinned: on a 400-chapter book the old in-header strip
            // scrolled away and switching blocks meant scrolling all the way back up.
            stickyHeader(key = "chapter-controls") {
                // Pinned at the top the block would otherwise slide under the status bar, which is
                // exactly where the immersive header wants to draw while it is still in flow.
                val pinned by remember {
                    derivedStateOf { listState.firstVisibleItemIndex >= 1 }
                }
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (pinned) Modifier.statusBarsPadding() else Modifier),
                ) {
                    Column(
                        modifier = Modifier.onSizeChanged { size ->
                            if (size.height > 0 && size.height != controlsHeightPx) {
                                controlsHeightPx = size.height
                                controlsMeasured = true
                            }
                        },
                    ) {
                        SecondaryTabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.background,
                            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text(stringResource(R.string.chapters_with_count, chapters.size)) },
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text(stringResource(R.string.bookmarks_with_count, bookmarks.size)) },
                            )
                        }
                        if (selectedTab == 0) {
                            if (controls.selectionMode) {
                                ChapterSelectionBar(
                                    selectedCount = controls.selection.size,
                                    supportsCache = book?.isRemote == true,
                                    onSelectAllVisible = {
                                        viewModel.selectAllVisibleChapters(visibleChapters.map { it.id })
                                    },
                                    onCache = { viewModel.cacheSelectedChapters() },
                                    onClearCache = { viewModel.clearCacheForSelectedChapters() },
                                    onMark = { viewModel.markSelectedChapters(it) },
                                    onCancel = { viewModel.clearChapterSelection() },
                                    onRenameSingle = {
                                        val only = chapters.firstOrNull { it.id == controls.selection.first() }
                                        if (only != null) {
                                            editChapter = only
                                            editChapterTitle = only.displayTitle
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            } else {
                                ChapterToolbar(
                                    controls = controls,
                                    visibleCount = visibleChapters.size,
                                    totalCount = chapters.size,
                                    cachedCount = if (book?.isRemote == true) {
                                        chapters.count { it.isCached }
                                    } else {
                                        null
                                    },
                                    groups = chapterGroups,
                                    onQueryChange = { viewModel.setChapterQuery(it) },
                                    onToggleSearch = { viewModel.toggleChapterSearch() },
                                    onToggleOrder = { viewModel.toggleChapterOrder() },
                                    onFilterChange = { viewModel.setChapterFilter(it) },
                                    onLocateCurrent = {
                                        val id = book?.currentChapterId
                                        val target = id?.let { ChapterPicker.flatIndexOf(chapterGroups, it) }
                                        if (target != null) scrollToChapterIndex(target)
                                    },
                                    onJumpToGroup = { index ->
                                        val first = chapterGroups[index].chapters.firstOrNull()?.id
                                        val target = first?.let { ChapterPicker.flatIndexOf(chapterGroups, it) }
                                        if (target != null) {
                                            scrollToChapterIndex((target - 1).coerceAtLeast(0))
                                        }
                                    },
                                    activeGroupIndex = activeGroupIndex,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (selectedTab == 0) {
                chapterGroupItems(
                    groups = chapterGroups,
                    currentChapterId = book?.currentChapterId,
                    controls = controls,
                    enabled = book?.needsReauth != true,
                    showCacheAction = book?.isRemote == true,
                    cachingChapterIds = prefetchState.singleChapterIds,
                    currentProgressFraction = null,
                    headerColor = chapterHeaderColor,
                    onChapterClick = { chapter ->
                        if (controls.selectionMode) {
                            viewModel.toggleChapterSelection(chapter.id)
                        } else {
                            onPlayChapter(chapter.id)
                        }
                    },
                    onChapterLongClick = { viewModel.startChapterSelection(it.id) },
                    onCacheClick = { chapter ->
                        if (chapter.isCached) {
                            viewModel.clearChapterCache(chapter)
                        } else {
                            viewModel.cacheChapter(chapter)
                        }
                    },
                    rowPadding = PaddingValues(horizontal = 16.dp),
                )
                if (visibleChapters.isEmpty()) {
                    item(key = "chapters-empty") {
                        Text(
                            stringResource(R.string.chapter_picker_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                        )
                    }
                }
            } else if (bookmarks.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.BookmarkBorder,
                        title = stringResource(R.string.no_bookmarks),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    )
                }
            } else {
                item(key = "bookmark-card") {
                    ListSectionCard(
                        rowCount = bookmarks.size,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        dividerStartIndent = 34.dp,
                    ) { index ->
                        val bm = bookmarks[index]
                        BookmarkRow(
                            bookmark = bm,
                            onClick = { onPlayBookmark(bm.chapterId, bm.positionMs) },
                            onLongClick = { bookmarkMenuFor = bm },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // Long-press menus
    bookmarkMenuFor?.let { bookmark ->
        DropdownMenu(
            expanded = true,
            onDismissRequest = { bookmarkMenuFor = null },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_bookmark_note)) },
                onClick = {
                    bookmarkMenuFor = null
                    editBookmark = bookmark
                    editBookmarkNote = bookmark.note.orEmpty()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.delete_bookmark),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    bookmarkMenuFor = null
                    viewModel.deleteBookmark(bookmark.id)
                },
            )
        }
    }

    OnlineMetaSyncSheet(
        state = metaSync,
        localChapterCount = chapters.size,
        onQueryChange = viewModel::setMetaSyncQuery,
        onSearch = viewModel::searchMetaCandidates,
        onToggleCover = viewModel::setMetaSyncCover,
        onToggleChapterTitles = viewModel::setMetaSyncChapterTitles,
        onApply = viewModel::applyMetaCandidate,
        onAlignmentModeChange = viewModel::setAlignmentMode,
        onAlignmentOffsetChange = viewModel::setAlignmentOffset,
        onConfirmAlignment = viewModel::confirmAlignment,
        onBookFieldsOnly = viewModel::confirmBookFieldsOnly,
        onDismissAlignment = viewModel::dismissAlignment,
        onDismiss = viewModel::closeMetaSync,
    )

    rescanPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRescanPreview() },
            title = { Text(stringResource(R.string.scan_results)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.scan_added, preview.plan.addedCount))
                    Text(stringResource(R.string.scan_removed, preview.plan.removedCount))
                    Text(stringResource(R.string.scan_changed, preview.plan.renamedCount))
                    preview.plan.weakMatches.forEach { (oldId, scanned) ->
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.weak_match, scanned.fileName), fontWeight = FontWeight.SemiBold)
                        Row {
                            TextButton(onClick = { viewModel.decideWeak(oldId, true) }) {
                                Text(
                                    stringResource(
                                        if (viewModel.weakAccepted(oldId)) R.string.keep_original_selected
                                        else R.string.keep_original,
                                    ),
                                )
                            }
                            TextButton(onClick = { viewModel.decideWeak(oldId, false) }) {
                                Text(
                                    stringResource(
                                        if (viewModel.isWeakDecided(oldId) && !viewModel.weakAccepted(oldId)) {
                                            R.string.treat_as_new_selected
                                        } else {
                                            R.string.treat_as_new
                                        },
                                    ),
                                )
                            }
                        }
                    }
                    preview.plan.ambiguous.forEach { ambiguous ->
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.unable_to_confirm, ambiguous.scanned.fileName), fontWeight = FontWeight.SemiBold)
                        ambiguous.candidates.forEach { candidate ->
                            TextButton(onClick = {
                                viewModel.decideAmbiguous(ambiguous.scanned.uri, candidate.oldChapter.id)
                            }) {
                                val chosen = viewModel.ambiguousChoice(ambiguous.scanned.uri) == candidate.oldChapter.id
                                Text(
                                    stringResource(
                                        if (chosen) R.string.match_chapter_selected else R.string.match_chapter,
                                        candidate.oldChapter.displayTitle,
                                    ),
                                )
                            }
                        }
                        TextButton(onClick = { viewModel.decideAmbiguous(ambiguous.scanned.uri, null) }) {
                            val rejected = viewModel.isAmbiguousDecided(ambiguous.scanned.uri) &&
                                viewModel.ambiguousChoice(ambiguous.scanned.uri) == null
                            Text(
                                stringResource(
                                    if (rejected) R.string.treat_as_new_chapter_selected
                                    else R.string.treat_as_new_chapter,
                                ),
                            )
                        }
                    }
                    if (preview.affectedBookmarkCount > 0) {
                        Text(
                            stringResource(R.string.delete_related_bookmarks, preview.affectedBookmarkCount),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmRescan()
                    },
                    enabled = viewModel.canConfirmRescan(),
                ) { Text(stringResource(R.string.apply)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRescanPreview() }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove_from_shelf)) },
            text = { Text(stringResource(R.string.remove_from_shelf_summary)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        viewModel.removeBook { onBack() }
                    },
                ) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (autoPlayDialog) {
        AlertDialog(
            onDismissRequest = { autoPlayDialog = false },
            title = { Text(stringResource(R.string.auto_play_next)) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.auto_play_next_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = book?.autoPlayNext ?: true,
                        onCheckedChange = viewModel::setAutoPlayNext,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { autoPlayDialog = false }) { Text(stringResource(R.string.apply)) }
            },
        )
    }

    if (editBook) {
        AlertDialog(
            onDismissRequest = { editBook = false },
            title = { Text(stringResource(R.string.edit_book_info)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text(stringResource(R.string.book_title)) },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editAuthor,
                        onValueChange = { editAuthor = it },
                        label = { Text(stringResource(R.string.author_optional)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editTitle.isNotBlank(),
                    onClick = {
                        viewModel.updateBookMetadata(editTitle, editAuthor)
                        editBook = false
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editBook = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (editSkipOffsets) {
        SkipOffsetsDialog(
            initialIntroMs = book?.skipIntroMs ?: 0L,
            initialOutroMs = book?.skipOutroMs ?: 0L,
            onDismiss = { editSkipOffsets = false },
            onSave = viewModel::setSkipOffsets,
        )
    }

    editChapter?.let { chapter ->
        AlertDialog(
            onDismissRequest = { editChapter = null },
            title = { Text(stringResource(R.string.edit_chapter_title)) },
            text = {
                OutlinedTextField(
                    value = editChapterTitle,
                    onValueChange = { editChapterTitle = it },
                    label = { Text(stringResource(R.string.chapter_title)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateChapterTitle(chapter.id, editChapterTitle)
                    editChapter = null
                    viewModel.clearChapterSelection()
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.updateChapterTitle(chapter.id, null)
                    editChapter = null
                    viewModel.clearChapterSelection()
                }) { Text(stringResource(R.string.restore_filename)) }
            },
        )
    }

    editBookmark?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { editBookmark = null },
            title = { Text(stringResource(R.string.edit_bookmark_note)) },
            text = {
                OutlinedTextField(
                    value = editBookmarkNote,
                    onValueChange = { editBookmarkNote = it },
                    label = { Text(stringResource(R.string.note_optional)) },
                    minLines = 2,
                    maxLines = 5,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateBookmarkNote(bookmark.id, editBookmarkNote)
                    editBookmark = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { editBookmark = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun HeaderIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.28f),
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides Color.White,
            ) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val chapterLabel = bookmark.chapterIndex?.let {
                    stringResource(R.string.chapter_number, it + 1)
                } ?: stringResource(R.string.chapter)
                Text(
                    stringResource(R.string.bookmark_position, chapterLabel, formatDuration(bookmark.positionMs)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                bookmark.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                bookmark.chapterTitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
