package com.tingxia.app.ui.shelf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingxia.app.R
import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.ShelfFilter
import com.tingxia.app.data.model.ShelfSort
import com.tingxia.app.ui.components.BookCover
import com.tingxia.app.ui.components.SectionCard
import com.tingxia.app.ui.components.formatDuration
import com.tingxia.app.ui.theme.COVER_RATIO_PORTRAIT
import com.tingxia.app.ui.theme.CoverCorner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onContinue: (Long) -> Unit,
    viewModel: ShelfViewModel = hiltViewModel(),
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val fqSearch by viewModel.fqSearch.collectAsStateWithLifecycle()
    val fqQuery by viewModel.fqQuery.collectAsStateWithLifecycle()
    val fqHasSearched by viewModel.fqHasSearched.collectAsStateWithLifecycle()
    val fqLoading by viewModel.fqLoading.collectAsStateWithLifecycle()
    val fqTones by viewModel.fqTones.collectAsStateWithLifecycle()
    val fqSelectedBook by viewModel.fqSelectedBook.collectAsStateWithLifecycle()
    val fqImporting by viewModel.fqImporting.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var sortMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    var importMenu by remember { mutableStateOf(false) }
    var homeSection by rememberSaveable { mutableStateOf(HomeSection.SHELF) }

    BackHandler(enabled = homeSection == HomeSection.ONLINE && fqSelectedBook != null) {
        viewModel.clearFqSelection()
    }

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            if (homeSection == HomeSection.SHELF) {
                                stringResource(R.string.shelf_book_count, books.size)
                            } else {
                                // The online section's own hero already says "发现真人演播好书".
                                "在线搜索 · 真人演播"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            if (homeSection == HomeSection.SHELF) Box {
                FloatingActionButton(
                    onClick = { importMenu = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.medium,
                ) {
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
        },
        snackbarHost = { SnackbarHost(snackbar) },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    HomeSectionTabs(
                        selected = homeSection,
                        onSelected = {
                            homeSection = it
                            importMenu = false
                        },
                    )
                    AnimatedContent(
                        targetState = homeSection,
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(140))
                        },
                        label = "homeSection",
                        modifier = Modifier.fillMaxSize(),
                    ) { section ->
                    if (section == HomeSection.SHELF) Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_books_hint)) },
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
                            // Filled when idle, outlined only on focus — the old pair of states
                            // looked like two different controls.
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { filterMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    filterLabel(filter),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                                listOf(
                                    ShelfFilter.ALL to stringResource(R.string.filter_all),
                                    ShelfFilter.NOT_STARTED to stringResource(R.string.filter_not_started),
                                    ShelfFilter.IN_PROGRESS to stringResource(R.string.filter_in_progress),
                                    ShelfFilter.COMPLETED to stringResource(R.string.filter_completed),
                                    ShelfFilter.NEEDS_REAUTH to stringResource(R.string.needs_reauthorization),
                                ).forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            viewModel.setFilter(value)
                                            filterMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { sortMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    sortLabel(sort),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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
                                        onClick = {
                                            viewModel.setSort(value)
                                            sortMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (books.isEmpty() && !importing) {
                        EmptyShelf(
                            filtered = query.isNotBlank() || filter != ShelfFilter.ALL,
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 112.dp),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 96.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            recent?.let { r ->
                                if (r.lastPlayedAt > 0 && query.isBlank() && filter == ShelfFilter.ALL) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        ContinueCard(book = r, onClick = { onContinue(r.id) })
                                    }
                                }
                            }
                            if (books.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, bottom = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            stringResource(R.string.my_shelf),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            stringResource(R.string.book_count, books.size),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            items(books, key = { it.id }) { book ->
                                BookGridItem(
                                    book = book,
                                    onClick = { onOpenBook(book.id) },
                                )
                            }
                        }
                    }
                    } else {
                        FqNovelCatalog(
                            query = fqQuery,
                            searchResults = fqSearch,
                            selectedBook = fqSelectedBook,
                            tones = fqTones,
                            loading = fqLoading,
                            importing = fqImporting,
                            hasSearched = fqHasSearched,
                            onQueryChange = viewModel::setFqQuery,
                            onSearch = viewModel::searchFqNovel,
                            onSelectBook = viewModel::selectFqBook,
                            onBack = viewModel::clearFqSelection,
                            onImport = { book, tone ->
                                viewModel.importFqNovel(book, tone) { bookId ->
                                    onOpenBook(bookId)
                                }
                            },
                        )
                    }
                    }
                }

                if (importing && homeSection == HomeSection.SHELF) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp,
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
                            Spacer(Modifier.width(14.dp))
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

private enum class HomeSection { SHELF, ONLINE }

@Composable
private fun HomeSectionTabs(selected: HomeSection, onSelected: (HomeSection) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(4.dp)) {
            listOf(HomeSection.SHELF to "我的书架", HomeSection.ONLINE to "在线找书").forEach { (section, label) ->
                val active = selected == section
                Surface(
                    onClick = { onSelected(section) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    // The idle tab stays transparent; matching the track made it vanish into it.
                    color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (active) 2.dp else 0.dp,
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(vertical = 11.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyShelf(filtered: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.LibraryBooks,
            contentDescription = null,
            modifier = Modifier.size(38.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(
                if (filtered) R.string.no_matching_books else R.string.empty_shelf,
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (filtered) {
                stringResource(R.string.adjust_search_or_filter)
            } else {
                stringResource(R.string.empty_shelf_hint)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContinueCard(book: Book, onClick: () -> Unit) {
    SectionCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                title = book.title,
                coverPath = book.coverPath,
                size = 66.dp,
                ratio = COVER_RATIO_PORTRAIT,
                corner = CoverCorner.Card,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.continue_listening),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    if (book.isRemote) {
                        Spacer(Modifier.width(8.dp))
                        OnlineBadge()
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.totalDurationMs > 0L) {
                    Text(
                        stringResource(
                            R.string.remaining_time,
                            formatDuration((book.totalDurationMs - book.linearPositionMs).coerceAtLeast(0L)),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { book.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.continue_playback),
                )
            }
        }
    }
}

@Composable
private fun BookGridItem(book: Book, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        Box {
            BookCover(
                title = book.title,
                coverPath = book.coverPath,
                modifier = Modifier.fillMaxWidth(),
                ratio = COVER_RATIO_PORTRAIT,
                corner = CoverCorner.Grid,
            )
            if (book.isRemote) {
                OnlineBadge(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                )
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
            if (book.lastPlayedAt > 0 && book.totalDurationMs > 0) {
                // Progress rides the artwork itself so every tile keeps the same height.
                LinearProgressIndicator(
                    progress = { book.progressFraction },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .padding(bottom = 6.dp)
                        .height(3.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Black.copy(alpha = 0.35f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            book.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            minLines = 2,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            book.author?.takeIf { it.isNotBlank() } ?: if (book.isRemote) "在线真人有声" else formatDuration(book.totalDurationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OnlineBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            "在线",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun filterLabel(filter: ShelfFilter): String = when (filter) {
    ShelfFilter.ALL -> stringResource(R.string.filter_all)
    ShelfFilter.NOT_STARTED -> stringResource(R.string.filter_not_started)
    ShelfFilter.IN_PROGRESS -> stringResource(R.string.filter_in_progress)
    ShelfFilter.COMPLETED -> stringResource(R.string.filter_completed)
    ShelfFilter.NEEDS_REAUTH -> stringResource(R.string.needs_reauthorization)
}

@Composable
private fun sortLabel(sort: ShelfSort): String = when (sort) {
    ShelfSort.RECENT -> stringResource(R.string.sort_recent_playback)
    ShelfSort.IMPORTED -> stringResource(R.string.sort_recent_import)
    ShelfSort.TITLE -> stringResource(R.string.book_title)
    ShelfSort.PROGRESS -> stringResource(R.string.progress)
}
