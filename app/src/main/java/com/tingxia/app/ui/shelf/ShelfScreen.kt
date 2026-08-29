package com.tingxia.app.ui.shelf

import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.graphics.Brush
import com.tingxia.app.ui.components.CoverPlayButton
import com.tingxia.app.ui.components.TxChip
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingxia.app.R
import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.ShelfFilter
import com.tingxia.app.data.model.ShelfSort
import com.tingxia.app.ui.components.BookGridTile
import com.tingxia.app.ui.components.EmptyState
import com.tingxia.app.ui.components.formatDuration

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
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.shelf_book_count, books.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.sort_books),
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
                    Box {
                        IconButton(onClick = { importMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.import_audio))
                        }
                        DropdownMenu(
                            expanded = importMenu,
                            onDismissRequest = { importMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_folder)) },
                                onClick = {
                                    importMenu = false
                                    openTree.launch(null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_multiple_audio)) },
                                onClick = {
                                    importMenu = false
                                    openFiles.launch(arrayOf("audio/*", "application/octet-stream"))
                                },
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
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
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            // A 56dp field plus the hero card and the chip row left room for a
                            // single book on the first screen; 46dp keeps the shelf the subject.
                            .height(46.dp),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        placeholder = {
                            Text(
                                stringResource(R.string.search_books_hint),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                                }
                            }
                        } else {
                            null
                        },
                        shape = MaterialTheme.shapes.large,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                    if (books.isEmpty() && !importing && (query.isNotBlank() || filter != ShelfFilter.ALL)) {
                        EmptyShelf(filtered = true, onImport = {}, onGoOnline = onGoOnline)
                    } else if (books.isEmpty() && !importing && recent == null) {
                        EmptyShelf(
                            filtered = false,
                            onImport = { openTree.launch(null) },
                            onGoOnline = onGoOnline,
                        )
                    } else {
                        // Hero card and the filter row scroll with the grid, so the shelf reads as
                        // one continuous surface instead of a fixed toolbar stack.
                        // Explicit column counts: GridCells.Adaptive divides with integer
                        // truncation, so a 108dp minimum silently produced two half-screen tiles
                        // on a normal phone instead of three.
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val columns = when {
                            maxWidth < 600.dp -> 3
                            maxWidth < 840.dp -> 4
                            else -> 6
                        }
                        // Tile width drives the on-cover play button, which otherwise stayed a
                        // 30dp dot no matter how large the artwork got.
                        val tileWidth = (maxWidth - 32.dp - 12.dp * (columns - 1)) / columns
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 24.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // Skipped when the bottom capsule is already showing this book (a
                            // loaded session means the mini player is on screen), and on a shelf
                            // small enough to see the book itself without a shortcut.
                            recent
                                ?.takeIf { query.isBlank() && filter == ShelfFilter.ALL }
                                ?.takeIf { it.id != playingBookId && books.size >= 3 }
                                ?.let { book ->
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        ContinueListeningBar(
                                            book = book,
                                            onOpen = { onOpenBook(book.id) },
                                            onPlay = { onPlayBook(book.id) },
                                        )
                                    }
                                }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ShelfFilterRow(
                                    filter = filter,
                                    showReauthFilter = hasReauthBooks,
                                    onFilterChange = viewModel::setFilter,
                                )
                            }
                            items(books, key = { it.id }) { book ->
                                BookGridItem(
                                    book = book,
                                    isCurrent = book.id == playingBookId,
                                    isPlaying = isPlaying && book.id == playingBookId,
                                    tileWidth = tileWidth,
                                    onClick = { onOpenBook(book.id) },
                                    onPlay = { onPlayBook(book.id) },
                                )
                            }
                            // A short shelf otherwise ends in half a screen of nothing; point that
                            // space at the one thing that fills a shelf.
                            if (books.size <= columns * 2 && query.isBlank()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    DiscoverMoreCard(onClick = onGoOnline)
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
        modifier = Modifier.fillMaxSize(),
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
    tileWidth: Dp = 110.dp,
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
            ?: formatDuration(book.totalDurationMs),
        // The tag rides with the author line now: cover art is the one thing a shelf is for, and
        // it was carrying a badge, a play button and a progress line at once.
        subtitleTag = if (book.isRemote) stringResource(R.string.online_badge) else null,
        onClick = onClick,
        framed = true,
        overlay = {
            if (isCurrent) {
                // Play dot marks the book loaded in the player, so the shelf answers
                // "what am I listening to" without reading titles. Remote tiles carry
                // the online badge at TopStart, so the dot shifts right to sit beside it.
                // Equaliser bars, not a second play triangle: this marks "loaded in the player",
                // while the corner button is the action.
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = stringResource(R.string.now_playing_badge),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(12.dp),
                    )
                }
            }
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
            // Artwork is unpredictable, so the bottom band gets a scrim: without it the progress
            // line and the play button disappeared into busy covers.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(playSize + 20.dp)
                    .clip(
                        RoundedCornerShape(
                            bottomStart = CoverCorner.Grid,
                            bottomEnd = CoverCorner.Grid,
                        ),
                    )
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f),
                        ),
                    ),
            )
            if (!book.needsReauth) {
                // One-tap continue straight from the shelf; the rest of the tile still opens the
                // book page.
                CoverPlayButton(
                    onClick = onPlay,
                    isPlaying = isPlaying,
                    size = playSize,
                    contentDescription = stringResource(R.string.shelf_play_book, book.title),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 8.dp),
                )
            }
            if (book.lastPlayedAt > 0 && book.totalDurationMs > 0) {
                // Progress rides the artwork itself so every tile keeps the same height.
                // The track needs real contrast against dark covers; theme scrim at low
                // alpha vanished entirely there, so a fixed dark wash is used instead.
                // One unbroken line hugging the bottom edge; the old segment stopped short of the
                // play button and read as a rendering glitch.
                LinearProgressIndicator(
                    progress = { book.progressFraction },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.30f),
                    // Material 1.3 draws a dot at the track end and a gap before it; on a 3dp
                    // hairline both read as dirt on the artwork.
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        },
    )
}

@Composable
private fun sortLabel(sort: ShelfSort): String = when (sort) {
    ShelfSort.RECENT -> stringResource(R.string.sort_recent_playback)
    ShelfSort.IMPORTED -> stringResource(R.string.sort_recent_import)
    ShelfSort.TITLE -> stringResource(R.string.book_title)
    ShelfSort.PROGRESS -> stringResource(R.string.progress)
}

/**
 * One-line resume strip: cover thumb, title, position, play button, and a hairline of progress
 * along the bottom edge. The tall hero card it replaced ate 128dp of the first screen and, on a
 * short shelf, simply repeated the tile right below it.
 */
@Composable
private fun ContinueListeningBar(
    book: Book,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    val accent = rememberCoverAccent(book.coverPath)
    SectionCard(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Square thumb, not a 3:4 one: a portrait cover at this height reached the card's
                // top and bottom edges and collided with the progress line.
                BookCover(
                    title = book.title,
                    coverPath = book.coverPath,
                    size = 38.dp,
                    corner = CoverCorner.Mini,
                    framed = false,
                )
                Spacer(Modifier.width(12.dp))
                // Title over position, with the button flush right: the old single line left the
                // percentage floating between the title and a button that sat nowhere in
                // particular.
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        book.title,
                        style = BookType.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val remainingMs = (book.totalDurationMs - book.linearPositionMs).coerceAtLeast(0L)
                    Text(
                        if (book.lastPlayedAt > 0) {
                            stringResource(
                                R.string.shelf_continue_inline,
                                (book.progressFraction * 100).toInt(),
                                formatDuration(remainingMs),
                            )
                        } else {
                            stringResource(R.string.shelf_continue_title)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                CoverPlayButton(
                    onClick = onPlay,
                    size = 36.dp,
                    contentDescription = stringResource(R.string.shelf_play_book, book.title),
                )
            }
            if (book.lastPlayedAt > 0 && book.totalDurationMs > 0) {
                LinearProgressIndicator(
                    progress = { book.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

/**
 * Filter chips only. Sorting moved to the app bar: sitting at the end of this row it read as a
 * fifth filter, and it pushed the real filters off-screen.
 */
@Composable
private fun ShelfFilterRow(
    filter: ShelfFilter,
    showReauthFilter: Boolean,
    onFilterChange: (ShelfFilter) -> Unit,
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
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        items(filterOptions, key = { it.first.name }) { (value, label) ->
            FilterChip(
                selected = filter == value,
                onClick = { onFilterChange(value) },
                label = { Text(label) },
            )
        }
    }
}

/** Tail card on a sparse shelf: an invitation rather than empty space. */
@Composable
private fun DiscoverMoreCard(onClick: () -> Unit) {
    SectionCard(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.TravelExplore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.empty_shelf_online_action),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.shelf_discover_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
