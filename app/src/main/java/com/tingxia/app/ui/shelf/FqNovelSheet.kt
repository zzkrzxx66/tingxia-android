package com.tingxia.app.ui.shelf

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.tingxia.app.data.remote.FqSearchBook
import com.tingxia.app.ui.components.BookCover
import com.tingxia.app.ui.components.SectionCard
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
    val fqTones by viewModel.fqTones.collectAsStateWithLifecycle()
    val fqSelectedBook by viewModel.fqSelectedBook.collectAsStateWithLifecycle()
    val fqImporting by viewModel.fqImporting.collectAsStateWithLifecycle()
    val fqHotBooks by viewModel.fqHotBooks.collectAsStateWithLifecycle()

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
                tones = fqTones,
                loading = fqLoading,
                importing = fqImporting,
                hasSearched = fqHasSearched,
                hotBooks = fqHotBooks,
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

@Composable
fun FqNovelCatalog(
    query: String,
    searchResults: List<FqSearchBook>,
    selectedBook: FqSearchBook?,
    tones: List<FqAudioTone>,
    loading: Boolean,
    importing: Boolean,
    hasSearched: Boolean,
    hotBooks: List<FqSearchBook> = emptyList(),
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSelectBook: (FqSearchBook) -> Unit,
    onBack: () -> Unit,
    onImport: (FqSearchBook, FqAudioTone) -> Unit,
) {
    if (selectedBook != null) {
        FqEditionPicker(
            book = selectedBook,
            tones = tones,
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
            // explicit submit (unlike the shelf field, which filters as you type), so the
            // decorative leading icon was the one to go.
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
            !hasSearched && searchResults.isEmpty() -> OnlineWelcome(hotBooks, onSelectBook)
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
            }
        }
    }
}

@Composable
private fun OnlineWelcome(
    hotBooks: List<FqSearchBook>,
    onSelectBook: (FqSearchBook) -> Unit,
) {
    // Adaptive grid matches the shelf's reflow behaviour on rotation and tablets;
    // the old hand-chunked 3-up rows stayed 3-up forever.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 88.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (hotBooks.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.online_hot_books),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            gridItems(hotBooks.take(9), key = { it.bookId }) { book ->
                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onSelectBook(book) },
                ) {
                    BookCover(
                        title = book.title,
                        coverPath = book.coverUrl,
                        modifier = Modifier.fillMaxWidth(),
                        ratio = COVER_RATIO_PORTRAIT,
                        corner = CoverCorner.Grid,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        book.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        book.author?.takeIf { it.isNotBlank() } ?: stringResource(R.string.unknown_author),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.online_shelf_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun OnlineEmpty(query: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.online_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.online_empty_hint, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnlineBookCard(book: FqSearchBook, onClick: () -> Unit) {
    SectionCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            BookCover(
                title = book.title,
                coverPath = book.coverUrl,
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
                // Author and format collapse into one metadata line; the old full-width pill
                // pushed the blurb off the card.
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
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.live_narration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                    )
                    book.category?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    book.wordCount.takeIf { it > 0 }?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.word_count_wan, formatWordCount(it)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
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

@Composable
private fun FqEditionPicker(
    book: FqSearchBook,
    tones: List<FqAudioTone>,
    loading: Boolean,
    importing: Boolean,
    onBack: () -> Unit,
    onImport: (FqSearchBook, FqAudioTone) -> Unit,
) {
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
                            coverPath = book.coverUrl,
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
                            if (tones.isEmpty() && !loading) R.string.online_tones_empty else R.string.online_tones_hint,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(tones, key = { it.audioBookId }) { tone ->
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
                                Icons.Default.Headphones,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(8.dp).size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.live_narrator),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                tone.title.removePrefix("主播:").trim().ifEmpty {
                                    stringResource(R.string.tone_info_missing)
                                },
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
                            androidx.compose.material3.TextButton(
                                onClick = { onImport(book, tone) },
                                enabled = !loading,
                            ) {
                                Text(stringResource(R.string.add_to_shelf))
                            }
                        }
                    }
                }
            }
        }
    }
}
