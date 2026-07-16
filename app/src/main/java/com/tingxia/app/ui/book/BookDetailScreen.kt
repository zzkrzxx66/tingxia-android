package com.tingxia.app.ui.book

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingxia.app.data.model.Bookmark
import com.tingxia.app.data.model.Chapter
import com.tingxia.app.ui.components.BookCover
import com.tingxia.app.ui.components.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: Long,
    onBack: () -> Unit,
    onPlayChapter: (Long) -> Unit,
    onContinue: () -> Unit,
    onPlayBookmark: (chapterId: Long, positionMs: Long) -> Unit = { _, _ -> },
    onRescanApplied: (bookId: Long, chapterId: Long?, positionMs: Long) -> Unit = { _, _, _ -> },
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val reauthing by viewModel.reauthing.collectAsStateWithLifecycle()
    val reauthProgress by viewModel.reauthProgress.collectAsStateWithLifecycle()
    val rescanning by viewModel.rescanning.collectAsStateWithLifecycle()
    val rescanProgress by viewModel.rescanProgress.collectAsStateWithLifecycle()
    val rescanPreview by viewModel.rescanPreview.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirmRemove by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        book?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("重新扫描目录") },
                            onClick = {
                                menu = false
                                viewModel.startRescan()
                            },
                            enabled = !rescanning && book?.needsReauth != true,
                        )
                        DropdownMenuItem(
                            text = { Text("重新授权目录") },
                            onClick = {
                                menu = false
                                reauthTree.launch(null)
                            },
                            enabled = !reauthing,
                        )
                        DropdownMenuItem(
                            text = { Text("从书架移除") },
                            onClick = {
                                menu = false
                                confirmRemove = true
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BookCover(
                        title = book?.title.orEmpty(),
                        coverPath = book?.coverPath,
                        size = 120.dp,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            book?.title.orEmpty(),
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        book?.author?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${chapters.size} 章 · ${formatDuration(book?.totalDurationMs ?: 0)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if ((book?.lastPlayedAt ?: 0) > 0) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { book?.progressFraction ?: 0f },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onContinue,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = book?.needsReauth != true && !reauthing && !rescanning,
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if ((book?.lastPlayedAt ?: 0) > 0) "继续播放" else "开始播放")
                        }
                    }
                }
                if (book?.needsReauth == true) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "目录权限失效，请重新授权后才能播放",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { reauthTree.launch(null) },
                        enabled = !reauthing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (reauthing) "正在重新授权…" else "重新授权目录")
                    }
                }
                if (rescanning) {
                    Spacer(Modifier.height(12.dp))
                    Text("正在扫描… ${rescanProgress?.currentName.orEmpty()}")
                }
                Spacer(Modifier.height(20.dp))
                Text("章节", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }
            items(chapters, key = { it.id }) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    isCurrent = chapter.id == book?.currentChapterId,
                    enabled = book?.needsReauth != true,
                    onClick = { onPlayChapter(chapter.id) },
                )
            }
            if (bookmarks.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("书签", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                }
                items(bookmarks, key = { "bm-${it.id}" }) { bm ->
                    BookmarkRow(
                        bookmark = bm,
                        onClick = { onPlayBookmark(bm.chapterId, bm.positionMs) },
                        onDelete = { viewModel.deleteBookmark(bm.id) },
                    )
                }
            }
        }
    }

    rescanPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRescanPreview() },
            title = { Text("扫描结果") },
            text = {
                Column {
                    Text("新增 ${preview.plan.addedCount} 章")
                    Text("删除 ${preview.plan.removedCount} 章")
                    Text("变更 ${preview.plan.renamedCount} 章")
                    if (preview.plan.ambiguousCount > 0) {
                        Text("歧义 ${preview.plan.ambiguousCount} 章（确认后将弱匹配自动纳入）")
                    }
                    if (preview.affectedBookmarkCount > 0) {
                        Text(
                            "将删除 ${preview.affectedBookmarkCount} 个关联书签",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmRescan { id, ch, pos ->
                            onRescanApplied(id, ch, pos)
                        }
                    },
                ) { Text("应用") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRescanPreview() }) { Text("取消") }
            },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("从书架移除") },
            text = { Text("仅从书架移除，不会删除手机上的原文件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        viewModel.removeBook { onBack() }
                    },
                ) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ChapterRow(
    chapter: Chapter,
    isCurrent: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "%02d".format(chapter.index + 1),
            style = MaterialTheme.typography.labelLarge,
            color = if (isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                chapter.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (chapter.durationMs > 0) {
                Text(
                    formatDuration(chapter.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isCurrent) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val chapterLabel = bookmark.chapterIndex?.let { "第 ${it + 1} 章" } ?: "章节"
            Text(
                "$chapterLabel · ${formatDuration(bookmark.positionMs)}",
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
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除书签")
        }
    }
}
