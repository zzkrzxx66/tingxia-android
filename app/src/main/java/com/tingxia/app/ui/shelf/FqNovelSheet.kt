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
import com.tingxia.app.R
import com.tingxia.app.data.remote.FqAudioTone
import com.tingxia.app.data.remote.FqSearchBook
import com.tingxia.app.ui.components.BookCover
import com.tingxia.app.ui.components.SectionCard
import com.tingxia.app.ui.theme.COVER_RATIO_PORTRAIT
import com.tingxia.app.ui.theme.CoverCorner

/** Search shortcuts, not a curated shelf — these are keywords, no catalogue data is implied. */
private val PopularSearches = listOf("斩神", "三体", "诡秘之主", "盗墓笔记", "鬼吹灯", "庆余年")

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

    androidx.activity.compose.BackHandler(enabled = fqSelectedBook != null) {
        viewModel.clearFqSelection()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.nav_online),
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
            placeholder = { Text("搜索书名或作者") },
            // Exactly one magnifier, and it is the actionable one. Online search needs an
            // explicit submit (unlike the shelf field, which filters as you type), so the
            // decorative leading icon was the one to go.
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "清除搜索内容",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { onSearch(query) }, enabled = query.isNotBlank() && !loading) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "搜索在线书籍",
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
            !hasSearched && searchResults.isEmpty() -> OnlineWelcome(onSearch)
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
                        Text("搜索结果", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${searchResults.size} 本",
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
private fun OnlineWelcome(onSearch: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { OnlineHero() }
        item {
            Column {
                Text("热门搜索", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                // A tile grid instead of three chips: fills the fold that used to be dead space
                // and gives the eye something to land on.
                PopularSearches.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { keyword ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onSearch(keyword) },
                            ) {
                                BookCover(
                                    title = keyword,
                                    coverPath = null,
                                    modifier = Modifier.fillMaxWidth(),
                                    ratio = COVER_RATIO_PORTRAIT,
                                    corner = CoverCorner.Grid,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    keyword,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item {
            Text(
                "在线书籍加入书架后，与本地有声书共用播放进度、书签、倍速和睡眠定时。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnlineHero() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Icon(
                    Icons.Default.Headphones,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(13.dp).size(26.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    "发现真人演播好书",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "搜索小说并选择喜欢的真人演播版本，加入书架后即可收听。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
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
        Text("没有找到相关书籍", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "没有找到“$query”，可以尝试更短的书名或作者名。",
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
                        book.author?.takeIf { it.isNotBlank() } ?: "未知作者",
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
                        "真人有声",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回搜索结果")
            }
            Text("选择演播版本", style = MaterialTheme.typography.titleLarge)
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
                            Text(book.author ?: "未知作者", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("真人演播版本", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (tones.isEmpty() && !loading) "暂未发现可用的真人演播版本" else "选择一个版本加入书架",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(tones, key = { it.audioBookId }) { tone ->
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Icon(
                                    Icons.Default.Headphones,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(10.dp).size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("真人演播", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    tone.title.removePrefix("主播：").ifBlank { "演播信息暂无" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { onImport(book, tone) },
                            enabled = !importing && !loading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            if (importing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("正在获取目录…")
                            } else {
                                Text("加入书架")
                            }
                        }
                    }
                }
            }
        }
    }
}
