package com.tingxia.app.ui.shelf

import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.graphics.Brush
import com.tingxia.app.ui.components.TxChip
import com.tingxia.app.ui.components.TxSearchField
import com.tingxia.app.ui.theme.BookType
import com.tingxia.app.ui.theme.rememberCoverAccent
import com.tingxia.app.ui.components.SectionCard
import com.tingxia.app.ui.components.BookCover
import com.tingxia.app.ui.theme.COVER_RATIO_PORTRAIT
import com.tingxia.app.ui.theme.CoverCorner
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingxia.app.R
import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.ShelfFilter
import com.tingxia.app.data.model.ShelfSort
import com.tingxia.app.ui.components.BookGridTile
import com.tingxia.app.ui.components.EmptyState
import com.tingxia.app.ui.components.formatDuration
import com.tingxia.app.ui.theme.LocalAppDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onGoOnline: () -> Unit,
    onPlayBook: (Long) -> Unit = {},
    playingBookId: Long? = null,
    isPlaying: Boolean = false,
    viewModel: ShelfViewModel = hiltViewModel(),
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val remainingByBook by viewModel.remainingByBook.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val hasReauthBooks by viewModel.hasReauthBooks.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var sortMenu by remember { mutableStateOf(false) }
    var importMenu by remember { mutableStateOf(false) }
    val isListView by viewModel.isListView.collectAsStateWithLifecycle()
    val topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val openTree = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.importFolder(uri)
    }
    val openFiles = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        viewModel.importFiles(uris)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                scrollBehavior = topBarScrollBehavior,
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            stringResource(R.string.shelf_book_count, books.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    Box {
                        Surface(
                            onClick = { importMenu = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.height(32.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.import_short),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = importMenu,
                            onDismissRequest = { importMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_folder)) },
                                leadingIcon = {
                                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                                },
                                onClick = {
                                    importMenu = false
                                    openTree.launch(null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_multiple_audio)) },
                                leadingIcon = {
                                    Icon(Icons.Default.AudioFile, contentDescription = null)
                                },
                                onClick = {
                                    importMenu = false
                                    openFiles.launch(arrayOf("audio/*", "application/octet-stream"))
                                },
                            )
                        }
                    }
                    Spacer(Modifier.width(2.dp))
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.sort_books),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf(
                                ShelfSort.RECENT to stringResource(R.string.sort_recent_playback),
                                ShelfSort.IMPORTED to stringResource(R.string.sort_recent_import),
                                ShelfSort.TITLE to stringResource(R.string.book_title),
                                ShelfSort.PROGRESS to stringResource(R.string.progress),
                            ).forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = {
                                        if (sort == value) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        viewModel.setSort(value)
                                        sortMenu = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // A filter or search that matches nothing must never take the filter row with
                    // it: tapping 已完成 on a shelf with nothing finished used to swap the whole grid
                    // for an empty state, leaving no way back to 全部. The "no match" message is a
                    // row inside the grid instead, underneath the chips.
                    if (books.isEmpty() && !importing && recent == null &&
                        query.isBlank() && filter == ShelfFilter.ALL
                    ) {
                        EmptyShelf(
                            filtered = false,
                            onImport = { openTree.launch(null) },
                            onGoOnline = onGoOnline,
                        )
                    } else if (isListView) {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 6.dp,
                                bottom = 24.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            item(key = "search_bar") {
                                TxSearchField(
                                    value = query,
                                    onValueChange = viewModel::setQuery,
                                    placeholder = stringResource(R.string.search_books_hint),
                                    onClear = { viewModel.setQuery("") },
                                    clearContentDescription = stringResource(R.string.clear_search),
                                    shape = RoundedCornerShape(16.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            recent
                                ?.takeIf { query.isBlank() && filter == ShelfFilter.ALL }
                                ?.takeIf { it.id != playingBookId && books.size >= 3 }
                                ?.let { book ->
                                    item(key = "continue_bar") {
                                        ContinueListeningBar(
                                            book = book,
                                            remainingMs = remainingByBook[book.id],
                                            onOpen = { onOpenBook(book.id) },
                                            onPlay = { onPlayBook(book.id) },
                                        )
                                    }
                                }
                            item(key = "filter_row") {
                                ShelfFilterRow(
                                    filter = filter,
                                    showReauthFilter = hasReauthBooks,
                                    onFilterChange = viewModel::setFilter,
                                    isListView = isListView,
                                    onToggleView = { viewModel.setListView(!isListView) },
                                )
                            }
                            items(books, key = { it.id }) { book ->
                                BookListItem(
                                    book = book,
                                    remainingMs = remainingByBook[book.id],
                                    isCurrent = book.id == playingBookId,
                                    isPlaying = isPlaying && book.id == playingBookId,
                                    onClick = { onOpenBook(book.id) },
                                    onPlay = { onPlayBook(book.id) },
                                )
                            }
                            if (books.isEmpty() && !importing) {
                                item(key = "empty_filtered") {
                                    EmptyShelf(
                                        filtered = true,
                                        onImport = {},
                                        onGoOnline = onGoOnline,
                                    )
                                }
                            }
                        }
                    } else {
                        // Search, hero card and filter row scroll with the grid, so the shelf reads as
                        // one continuous surface instead of a fixed toolbar stack.
                        // Explicit column counts: GridCells.Adaptive divides with integer
                        // truncation, so a 108dp minimum silently produced two half-screen tiles
                        // on a normal phone instead of three.
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val columns = when {
                            maxWidth < 360.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f -> 2
                            maxWidth < 600.dp -> 3
                            maxWidth < 840.dp -> 4
                            else -> 6
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 6.dp,
                                bottom = 24.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            item(key = "search_bar", span = { GridItemSpan(maxLineSpan) }) {
                                TxSearchField(
                                    value = query,
                                    onValueChange = viewModel::setQuery,
                                    placeholder = stringResource(R.string.search_books_hint),
                                    onClear = { viewModel.setQuery("") },
                                    clearContentDescription = stringResource(R.string.clear_search),
                                    shape = RoundedCornerShape(16.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            // Skipped when the bottom capsule is already showing this book (a
                            // loaded session means the mini player is on screen), and on a shelf
                            // small enough to see the book itself without a shortcut.
                            recent
                                ?.takeIf { query.isBlank() && filter == ShelfFilter.ALL }
                                ?.takeIf { it.id != playingBookId && books.size >= 3 }
                                ?.let { book ->
                                    item(key = "continue_bar", span = { GridItemSpan(maxLineSpan) }) {
                                        ContinueListeningBar(
                                            book = book,
                                            remainingMs = remainingByBook[book.id],
                                            onOpen = { onOpenBook(book.id) },
                                            onPlay = { onPlayBook(book.id) },
                                        )
                                    }
                                }
                            item(key = "filter_row", span = { GridItemSpan(maxLineSpan) }) {
                                ShelfFilterRow(
                                    filter = filter,
                                    showReauthFilter = hasReauthBooks,
                                    onFilterChange = viewModel::setFilter,
                                    isListView = isListView,
                                    onToggleView = { viewModel.setListView(!isListView) },
                                )
                            }
                            items(books, key = { it.id }) { book ->
                                BookGridItem(
                                    book = book,
                                    isCurrent = book.id == playingBookId,
                                    isPlaying = isPlaying && book.id == playingBookId,
                                    onClick = { onOpenBook(book.id) },
                                    onPlay = { onPlayBook(book.id) },
                                )
                            }
                            if (books.isEmpty() && !importing) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    EmptyShelf(
                                        filtered = true,
                                        onImport = {},
                                        onGoOnline = onGoOnline,
                                    )
                                }
                            }
                        }
                        }
                    }
                }

                if (importing) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 2.dp,
                        shadowElevation = 6.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.importing), style = MaterialTheme.typography.titleSmall)
                                importProgress?.let {
                                    Text(
                                        buildString {
                                            append(it.scannedFiles)
                                            if (it.totalFiles > 0) append(" / ${it.totalFiles}")
                                            append(" · ${it.currentName}")
                                        },
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
            }
        },
    )
}

@Composable
private fun EmptyShelf(
    filtered: Boolean,
    onImport: () -> Unit,
    onGoOnline: () -> Unit,
) {
    EmptyState(
        icon = Icons.AutoMirrored.Filled.LibraryBooks,
        title = stringResource(if (filtered) R.string.no_matching_books else R.string.empty_shelf),
        body = stringResource(
            if (filtered) R.string.adjust_search_or_filter else R.string.empty_shelf_hint,
        ),
        // Filtered: a row inside the grid, so it takes only the height it needs.
        modifier = if (filtered) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
        action = if (filtered) null else {
            {
                Button(onClick = onImport, shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.empty_shelf_action))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onGoOnline, shape = MaterialTheme.shapes.medium) {
                    Text(stringResource(R.string.empty_shelf_online_action))
                }
            }
        },
    )
}

@Composable
private fun BookGridItem(
    book: Book,
    /** Loaded in the player (marks the tile), which is not the same as actually playing. */
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onPlay: () -> Unit = {},
) {
    // Fixed 28dp: scaling it with the tile put a 44dp disc over the focal point of the artwork.
    val playSize = 28.dp
    val accent = rememberCoverAccent(book.coverPath)
    BookGridTile(
        title = book.title,
        coverPath = book.coverPath,
        subtitle = book.author?.takeIf { it.isNotBlank() }
            ?: book.totalDurationMs.takeIf { it > 0 }?.let(::formatDuration).orEmpty(),
        // The tag rides with the author line now: cover art is the one thing a shelf is for, and
        // it was carrying a badge, a play button and a progress line at once.
        subtitleTag = if (book.isRemote) stringResource(R.string.online_badge) else null,
        onClick = onClick,
        framed = true,
        titleStyle = BookType.title.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        coverElevation = 2.dp,
        overlay = {
            if (book.needsReauth && !book.isRemote) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = stringResource(R.string.needs_reauthorization),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(14.dp),
                    )
                }
            }
            // Artwork is unpredictable, so the bottom band gets a slim scrim: enough to keep the
            // progress hairline and play disc legible without darkening half the cover.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(
                        RoundedCornerShape(
                            bottomStart = CoverCorner.Grid,
                            bottomEnd = CoverCorner.Grid,
                        ),
                    )
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.30f),
                        ),
                    ),
            )
            if (!book.needsReauth) {
                // One-tap continue straight from the shelf; when this book is the active session,
                // the disc takes the cover's accent colour instead of adding a second badge at TopStart.
                ShelfPlayButton(
                    onClick = onPlay,
                    isPlaying = isPlaying,
                    isCurrent = isCurrent,
                    size = playSize,
                    hitTargetSize = 38.dp,
                    accent = accent,
                    onArtwork = true,
                    contentDescription = stringResource(R.string.shelf_play_book, book.title),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 3.dp, bottom = 8.dp),
                )
            }
            if (book.lastPlayedAt > 0 && book.totalDurationMs > 0) {
                // Inset from the curved edge so the cover keeps a clean silhouette.
                LinearProgressIndicator(
                    progress = { book.progressFraction },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(CircleShape),
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.28f),
                    strokeCap = StrokeCap.Butt,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        },
    )
}

@Composable
private fun BookListItem(
    book: Book,
    remainingMs: Long?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = rememberCoverAccent(book.coverPath)
    SectionCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                BookCover(
                    title = book.title,
                    coverPath = book.coverPath,
                    size = 56.dp,
                    ratio = COVER_RATIO_PORTRAIT,
                    corner = CoverCorner.Card,
                    framed = true,
                )
                if (isCurrent) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.42f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                    ) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = stringResource(R.string.now_playing_badge),
                            tint = Color.White,
                            modifier = Modifier
                                .padding(3.dp)
                                .size(11.dp),
                        )
                    }
                }
                if (book.needsReauth && !book.isRemote) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = stringResource(R.string.needs_reauthorization),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .padding(3.dp)
                                .size(12.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = BookType.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                val meta = buildList {
                    book.author?.takeIf { it.isNotBlank() }?.let(::add)
                    if (book.isRemote) add(stringResource(R.string.online_badge))
                    book.category?.takeIf { it.isNotBlank() }?.let(::add)
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                }
                val progressText = if (book.lastPlayedAt > 0 && book.totalDurationMs > 0) {
                    stringResource(
                        R.string.shelf_continue_inline,
                        (book.progressFraction * 100).toInt(),
                        remainingMs?.let(::formatDuration) ?: "--:--",
                    )
                } else if (book.totalDurationMs > 0) {
                    formatDuration(book.totalDurationMs)
                } else {
                    stringResource(R.string.shelf_continue_title)
                }
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
                if (book.lastPlayedAt > 0 && book.totalDurationMs > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { book.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape),
                        color = accent,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Butt,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
            if (!book.needsReauth) {
                Spacer(Modifier.width(10.dp))
                ShelfPlayButton(
                    onClick = onPlay,
                    isPlaying = isPlaying,
                    size = 36.dp,
                    accent = accent,
                    contentDescription = stringResource(R.string.shelf_play_book, book.title),
                )
            }
        }
    }
}

@Composable
private fun sortLabel(sort: ShelfSort): String = when (sort) {
    ShelfSort.RECENT -> stringResource(R.string.sort_recent_playback)
    ShelfSort.IMPORTED -> stringResource(R.string.sort_recent_import)
    ShelfSort.TITLE -> stringResource(R.string.book_title)
    ShelfSort.PROGRESS -> stringResource(R.string.progress)
}

/** A compact, cover-tinted resume card with progress grouped under the book title. */
@Composable
private fun ContinueListeningBar(
    book: Book,
    remainingMs: Long?,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    val accent = rememberCoverAccent(book.coverPath)
    val surface = MaterialTheme.colorScheme.surface
    val tint = if (LocalAppDarkTheme.current) 0.12f else 0.07f
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(0.5.dp, accent.copy(alpha = 0.16f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(lerp(surface, accent, tint), lerp(surface, accent, tint / 3f)),
                    ),
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                title = book.title,
                coverPath = book.coverPath,
                size = 50.dp,
                ratio = COVER_RATIO_PORTRAIT,
                corner = CoverCorner.Mini,
                framed = true,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.shelf_continue_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    book.title,
                    style = BookType.title.copy(fontSize = 17.sp, lineHeight = 23.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(
                        R.string.shelf_continue_progress,
                        (book.progressFraction * 100).toInt(),
                        remainingMs?.let(::formatDuration) ?: "--:--",
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.lastPlayedAt > 0 && book.totalDurationMs > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { book.progressFraction },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                        color = accent,
                        trackColor = accent.copy(alpha = 0.16f),
                        strokeCap = StrokeCap.Butt,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            ShelfPlayButton(
                onClick = onPlay,
                accent = accent,
                contentDescription = stringResource(R.string.shelf_play_book, book.title),
            )
        }
    }
}

/** Lightweight filters and the layout switch, kept visible even for an empty result. */
@Composable
private fun ShelfFilterRow(
    filter: ShelfFilter,
    showReauthFilter: Boolean,
    onFilterChange: (ShelfFilter) -> Unit,
    isListView: Boolean,
    onToggleView: () -> Unit,
) {
    val filterOptions = buildList {
        add(ShelfFilter.ALL to stringResource(R.string.filter_all))
        add(ShelfFilter.NOT_STARTED to stringResource(R.string.filter_not_started))
        add(ShelfFilter.IN_PROGRESS to stringResource(R.string.filter_in_progress))
        add(ShelfFilter.COMPLETED to stringResource(R.string.filter_completed))
        // Only offered when something actually lost its folder permission; otherwise it was a
        // permanent fifth chip hanging half off the screen edge.
        if (showReauthFilter || filter == ShelfFilter.NEEDS_REAUTH) {
            add(ShelfFilter.NEEDS_REAUTH to stringResource(R.string.needs_reauthorization))
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            items(filterOptions, key = { it.first.name }) { (value, label) ->
                FilterChip(
                    selected = filter == value,
                    onClick = { onFilterChange(value) },
                    shape = CircleShape,
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    label = {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (filter == value) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    },
                )
            }
        }
        IconButton(onClick = onToggleView) {
            Icon(
                if (isListView) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                contentDescription = stringResource(if (isListView) R.string.view_grid else R.string.view_list),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ShelfPlayButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isCurrent: Boolean = false,
    size: Dp = 40.dp,
    hitTargetSize: Dp = size,
    accent: Color = MaterialTheme.colorScheme.primary,
    onArtwork: Boolean = false,
) {
    val useAccent = !onArtwork || isCurrent
    val background = if (useAccent) accent else Color.Black.copy(alpha = 0.50f)
    val foreground = if (!useAccent || accent.luminance() <= 0.22f) Color.White else Color(0xFF141816)
    val border = when {
        onArtwork && isCurrent -> BorderStroke(1.dp, Color.White.copy(alpha = 0.65f))
        onArtwork -> BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f))
        else -> null
    }
    Box(
        modifier = modifier
            .size(hitTargetSize)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = background,
            contentColor = foreground,
            border = border,
            shadowElevation = if (onArtwork && isCurrent) 3.dp else 0.dp,
            modifier = Modifier.size(size),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.pause) else contentDescription,
                    modifier = Modifier.size(size * 0.56f),
                )
            }
        }
    }
}
