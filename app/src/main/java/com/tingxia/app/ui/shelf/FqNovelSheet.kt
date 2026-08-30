package com.tingxia.app.ui.shelf

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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.tingxia.app.R
import com.tingxia.app.data.remote.FqAudioTone
import com.tingxia.app.data.remote.FqDiscoverSection
import com.tingxia.app.data.remote.FqSearchBook
import com.tingxia.app.data.remote.FqTtsTone
import com.tingxia.app.data.remote.FqVoiceChoice
import com.tingxia.app.data.remote.FqVoices
import com.tingxia.app.ui.components.BookCover
import com.tingxia.app.ui.components.BookGridTile
import com.tingxia.app.ui.components.EmptyState
import com.tingxia.app.ui.components.SectionCard
import com.tingxia.app.ui.components.ShimmerTile
import com.tingxia.app.ui.components.formatWordCount
import com.tingxia.app.ui.theme.COVER_RATIO_PORTRAIT
import com.tingxia.app.ui.theme.CoverCorner

/** Top-level online catalogue destination, sharing the shelf's view model. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FqNovelCatalogScreen(
    onOpenBook: (Long) -> Unit,
    viewModel: ShelfViewModel = hiltViewModel(),
) {
    val fqSearch by viewModel.fqSearch.collectAsStateWithLifecycle()
    val fqQuery by viewModel.fqQuery.collectAsStateWithLifecycle()
    val fqHasSearched by viewModel.fqHasSearched.collectAsStateWithLifecycle()
    val fqLoading by viewModel.fqLoading.collectAsStateWithLifecycle()
    val fqLoadingMore by viewModel.fqLoadingMore.collectAsStateWithLifecycle()
    val fqHasMore by viewModel.fqHasMore.collectAsStateWithLifecycle()
    val fqVoices by viewModel.fqVoices.collectAsStateWithLifecycle()
    val fqSelectedBook by viewModel.fqSelectedBook.collectAsStateWithLifecycle()
    val fqImporting by viewModel.fqImporting.collectAsStateWithLifecycle()
    val fqHotBooks by viewModel.fqHotBooks.collectAsStateWithLifecycle()
    val fqSections by viewModel.fqSections.collectAsStateWithLifecycle()
    val fqHistory by viewModel.fqHistory.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loadFqDiscover()
    }

    androidx.activity.compose.BackHandler(enabled = fqSelectedBook != null) {
        viewModel.clearFqSelection()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.nav_online),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            FqNovelCatalog(
                query = fqQuery,
                searchResults = fqSearch,
                selectedBook = fqSelectedBook,
                voices = fqVoices,
                loading = fqLoading,
                loadingMore = fqLoadingMore,
                hasMore = fqHasMore,
                importing = fqImporting,
                hasSearched = fqHasSearched,
                hotBooks = fqHotBooks,
                sections = fqSections,
                history = fqHistory,
                onQueryChange = viewModel::setFqQuery,
                onSearch = viewModel::searchFqNovel,
                onLoadMore = viewModel::loadMoreFqNovel,
                onClearHistory = viewModel::clearFqHistory,
                onSelectBook = viewModel::selectFqBook,
                onBack = viewModel::clearFqSelection,
                onImport = { book, choice ->
                    viewModel.importFqNovel(book, choice) { bookId ->
                        onOpenBook(bookId)
                    }
                },
            )
        }
    }
}

@Composable
fun FqNovelCatalog(
    query: String,
    searchResults: List<FqSearchBook>,
    selectedBook: FqSearchBook?,
    voices: FqVoices?,
    loading: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    importing: Boolean,
    hasSearched: Boolean,
    hotBooks: List<FqSearchBook> = emptyList(),
    sections: List<FqDiscoverSection> = emptyList(),
    history: List<String> = emptyList(),
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onClearHistory: () -> Unit,
    onSelectBook: (FqSearchBook) -> Unit,
    onBack: () -> Unit,
    onImport: (FqSearchBook, FqVoiceChoice) -> Unit,
) {
    if (selectedBook != null) {
        FqVoicePicker(
            book = selectedBook,
            voices = voices,
            loading = loading,
            importing = importing,
            onBack = onBack,
            onImport = onImport,
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.online_search_hint)) },
            // Exactly one magnifier, and it is the actionable one. Online search needs an
            // explicit submit (unlike the shelf field, which filters as you type).
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.online_search_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { onSearch(query) }, enabled = query.isNotBlank() && !loading) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.online_search_submit),
                            tint = if (query.isNotBlank() && !loading) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            shape = MaterialTheme.shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        // Fixed-height slot so appearing progress never nudges the list down.
        Box(modifier = Modifier.fillMaxWidth().height(3.dp)) {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp))
            }
        }

        when {
            !hasSearched && searchResults.isEmpty() -> OnlineWelcome(
                hotBooks = hotBooks,
                sections = sections,
                history = history,
                onSelectBook = onSelectBook,
                onSearch = onSearch,
                onClearHistory = onClearHistory,
            )
            hasSearched && searchResults.isEmpty() && !loading -> OnlineEmpty(query)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.online_search_results), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.online_result_count, searchResults.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(searchResults, key = { it.bookId }) { book ->
                    OnlineBookCard(book = book, onClick = { onSelectBook(book) })
                }
                item {
                    when {
                        loadingMore -> Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                        hasMore -> OutlinedButton(
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.online_load_more))
                        }
                        else -> Text(
                            stringResource(R.string.online_no_more),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineWelcome(
    hotBooks: List<FqSearchBook>,
    sections: List<FqDiscoverSection>,
    history: List<String>,
    onSelectBook: (FqSearchBook) -> Unit,
    onSearch: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    // Sections come from the service; the flat hot list is the fallback when they fail.
    if (sections.isEmpty() && hotBooks.isEmpty()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 88.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (history.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchHistoryRow(history, onSearch, onClearHistory)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.online_hot_books), style = MaterialTheme.typography.titleMedium)
            }
            items(6) { ShimmerTile() }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (history.isNotEmpty()) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SearchHistoryRow(history, onSearch, onClearHistory)
                }
            }
        }
        if (sections.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.online_hot_books), style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                DiscoverRow(hotBooks, onSelectBook)
            }
        } else {
            items(sections, key = { it.title }) { section ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(section.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onSearch(section.query) }) {
                            Text(stringResource(R.string.online_section_more))
                        }
                    }
                    DiscoverRow(section.books, onSelectBook)
                }
            }
        }
        item {
            Text(
                stringResource(R.string.online_shelf_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun DiscoverRow(books: List<FqSearchBook>, onSelectBook: (FqSearchBook) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(books, key = { it.bookId }) { book ->
            Box(Modifier.width(96.dp)) {
                BookGridTile(
                    title = book.title,
                    coverPath = book.displayCoverUrl,
                    subtitle = book.author?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.unknown_author),
                    onClick = { onSelectBook(book) },
                    // Online artwork already reads as cover art; the paperback
                    // overlay is reserved for owned shelf items.
                    framed = false,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchHistoryRow(
    history: List<String>,
    onSearch: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.online_search_history), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClearHistory) {
                Text(stringResource(R.string.online_history_clear))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history, key = { it }) { term ->
                AssistChip(onClick = { onSearch(term) }, label = { Text(term) })
            }
        }
    }
}

@Composable
private fun OnlineEmpty(query: String) {
    EmptyState(
        icon = Icons.Default.Search,
        title = stringResource(R.string.online_empty_title),
        body = stringResource(R.string.online_empty_hint, query),
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun OnlineBookCard(book: FqSearchBook, onClick: () -> Unit) {
    SectionCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            BookCover(
                title = book.title,
                coverPath = book.displayCoverUrl,
                size = 74.dp,
                ratio = COVER_RATIO_PORTRAIT,
                corner = CoverCorner.Card,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        book.author?.takeIf { it.isNotBlank() } ?: stringResource(R.string.unknown_author),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    // Which of the two listening modes this book supports is the single
                    // most useful thing to know before opening it.
                    Icon(
                        if (book.hasRealAudio) Icons.Default.Headphones else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(
                            if (book.hasRealAudio) R.string.live_narration else R.string.online_badge_tts_only,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(4.dp))
                val metaParts = buildList {
                    book.score?.let { add(stringResource(R.string.online_score, it)) }
                    book.listenCount.takeIf { it > 0 }?.let {
                        add(stringResource(R.string.online_listen_count, formatCount(it)))
                    }
                    book.category?.takeIf { it.isNotBlank() }?.let(::add)
                    book.wordCount.takeIf { it > 0 }?.let {
                        add(stringResource(R.string.word_count_wan, formatWordCount(it)))
                    }
                    book.finished?.let {
                        add(stringResource(if (it) R.string.book_finished else R.string.book_serial))
                    }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    book.description?.replace('\n', ' ').orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Reserved whether or not a blurb exists, so rows line up.
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 万 / 亿 formatting for listen counts, which upstream reports raw. */
private fun formatCount(value: Long): String = when {
    value >= 100_000_000L -> String.format("%.1f亿", value / 100_000_000.0)
    value >= 10_000L -> String.format("%.1f万", value / 10_000.0)
    else -> value.toString()
}

@Composable
private fun FqVoicePicker(
    book: FqSearchBook,
    voices: FqVoices?,
    loading: Boolean,
    importing: Boolean,
    onBack: () -> Unit,
    onImport: (FqSearchBook, FqVoiceChoice) -> Unit,
) {
    val audioBooks = voices?.audioBooks.orEmpty()
    val ttsTones = voices?.ttsTones.orEmpty()
    val recommended = voices?.recommendToneId

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = !importing) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_to_results))
            }
            Text(stringResource(R.string.online_pick_edition), style = MaterialTheme.typography.titleLarge)
        }
        Box(modifier = Modifier.fillMaxWidth().height(3.dp)) {
            if (loading || importing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp))
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        BookCover(
                            title = book.title,
                            coverPath = book.displayCoverUrl ?: voices?.coverUrl,
                            size = 96.dp,
                            ratio = COVER_RATIO_PORTRAIT,
                            corner = CoverCorner.Detail,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(book.author ?: stringResource(R.string.unknown_author), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val metaLine = buildList {
                                book.category?.takeIf { it.isNotBlank() }?.let(::add)
                                if (book.wordCount > 0) add(stringResource(R.string.word_count_wan, formatWordCount(book.wordCount)))
                                book.score?.let { add(stringResource(R.string.online_score, it)) }
                            }
                            if (metaLine.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    metaLine.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            book.description?.let {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    it.replace('\n', ' '),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.online_tones_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            if (audioBooks.isEmpty() && !loading) {
                                R.string.online_tones_empty
                            } else {
                                R.string.online_tones_hint
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(audioBooks, key = { it.audioBookId }) { tone ->
                VoiceRow(
                    icon = Icons.Default.Headphones,
                    title = stringResource(R.string.live_narrator),
                    subtitle = tone.title.removePrefix("主播:").trim().ifEmpty {
                        stringResource(R.string.tone_info_missing)
                    },
                    badge = null,
                    importing = importing,
                    enabled = !loading,
                    onImport = { onImport(book, FqVoiceChoice.Real(tone)) },
                )
            }

            if (ttsTones.isNotEmpty()) {
                item {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.online_tts_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.online_tts_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(ttsTones, key = { it.toneId }) { tone ->
                    VoiceRow(
                        icon = if (tone.multiRole) Icons.Default.RecordVoiceOver else Icons.Default.AutoAwesome,
                        title = tone.title,
                        subtitle = tone.description ?: stringResource(R.string.online_tts_generic),
                        badge = if (tone.toneId == recommended) stringResource(R.string.online_tts_recommended) else null,
                        importing = importing,
                        enabled = !loading,
                        onImport = { onImport(book, FqVoiceChoice.Tts(book.bookId, tone)) },
                    )
                }
            }

            if (audioBooks.isEmpty() && ttsTones.isEmpty() && !loading) {
                item {
                    Text(
                        stringResource(R.string.online_voices_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    badge: String?,
    importing: Boolean,
    enabled: Boolean,
    onImport: () -> Unit,
) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(8.dp).size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (badge != null) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (importing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).padding(end = 2.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                TextButton(onClick = onImport, enabled = enabled) {
                    Text(stringResource(R.string.add_to_shelf))
                }
            }
        }
    }
}
