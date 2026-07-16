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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val reauthing by viewModel.reauthing.collectAsStateWithLifecycle()
    val reauthProgress by viewModel.reauthProgress.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var confirmRemove by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
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
                    IconButton(onClick = { confirmRemove = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "从书架移除")
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
                            enabled = book?.needsReauth != true && !reauthing,
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
                        Text(if (reauthing) "正在重新关联…" else "重新授权目录")
                    }
                    if (reauthing) {
                        Spacer(Modifier.height(8.dp))
                        reauthProgress?.let {
                            Text(
                                "已扫描 ${it.scannedFiles} · ${it.currentName}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
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
        }
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
                chapter.title,
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
