package com.tingxia.app.ui.shelf

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.ShelfFilter
import com.tingxia.app.data.model.ShelfSort
import com.tingxia.app.ui.components.BookCover
import com.tingxia.app.ui.components.formatDuration

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
    val snackbar = remember { SnackbarHostState() }
    var sortMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val openTree = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) viewModel.importFolder(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("听匣") },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "排序")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf(
                                ShelfSort.RECENT to "最近播放",
                                ShelfSort.IMPORTED to "最近导入",
                                ShelfSort.TITLE to "书名",
                                ShelfSort.PROGRESS to "进度",
                            ).forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label + if (sort == value) " ✓" else "") },
                                    onClick = {
                                        viewModel.setSort(value)
                                        sortMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { filterMenu = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "筛选")
                        }
                        DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                            listOf(
                                ShelfFilter.ALL to "全部",
                                ShelfFilter.NOT_STARTED to "未开始",
                                ShelfFilter.IN_PROGRESS to "收听中",
                                ShelfFilter.NEEDS_REAUTH to "需重新授权",
                            ).forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label + if (filter == value) " ✓" else "") },
                                    onClick = {
                                        viewModel.setFilter(value)
                                        filterMenu = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { openTree.launch(null) }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "导入文件夹")
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
                    if (showSearch) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            singleLine = true,
                            placeholder = { Text("搜索书名或作者") },
                        )
                    }
                    if (books.isEmpty() && !importing) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("还没有书", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (query.isNotBlank() || filter != ShelfFilter.ALL) {
                                    "没有符合条件的书籍"
                                } else {
                                    "点击右下角，选择一个包含音频文件的文件夹导入"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            recent?.let { r ->
                                if (r.lastPlayedAt > 0 && query.isBlank() && filter == ShelfFilter.ALL) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        ContinueCard(book = r, onClick = { onContinue(r.id) })
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
                }

                if (importing) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("正在导入…", style = MaterialTheme.typography.titleSmall)
                                importProgress?.let {
                                    Text(
                                        "已扫描 ${it.scannedFiles} · ${it.currentName}",
                                        style = MaterialTheme.typography.bodySmall,
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
private fun ContinueCard(book: Book, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(title = book.title, coverPath = book.coverPath, size = 72.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("继续收听", style = MaterialTheme.typography.labelLarge)
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { book.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
            }
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun BookGridItem(book: Book, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box {
            BookCover(title = book.title, coverPath = book.coverPath, modifier = Modifier.fillMaxWidth())
            if (book.needsReauth) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "需重新授权",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            book.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (book.totalDurationMs > 0) {
            Text(
                formatDuration(book.totalDurationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (book.lastPlayedAt > 0 && book.totalDurationMs > 0) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { book.progressFraction },
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
        }
    }
}
