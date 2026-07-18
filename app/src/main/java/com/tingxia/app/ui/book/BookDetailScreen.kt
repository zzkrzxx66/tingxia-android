package com.tingxia.app.ui.book

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingxia.app.R
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
    val decisionVersion by viewModel.decisionVersion.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirmRemove by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var editBook by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editAuthor by remember { mutableStateOf("") }
    var editChapter by remember { mutableStateOf<Chapter?>(null) }
    var editChapterTitle by remember { mutableStateOf("") }
    var editBookmark by remember { mutableStateOf<Bookmark?>(null) }
    var editBookmarkNote by remember { mutableStateOf("") }
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
    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.updateBookCover(uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        book?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { menu = true }) {
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
                            text = { Text(stringResource(R.string.choose_cover)) },
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
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remove_from_shelf)) },
                            onClick = {
                                menu = false
                                confirmRemove = true
                            },
                        )
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
                        size = 118.dp,
                        corner = 16.dp,
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
                            stringResource(
                                R.string.book_chapter_duration,
                                chapters.size,
                                formatDuration(book?.totalDurationMs ?: 0),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if ((book?.lastPlayedAt ?: 0) > 0) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { book?.progressFraction ?: 0f },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                            )
                            Text(
                                stringResource(
                                    R.string.remaining_time,
                                    formatDuration(((book?.totalDurationMs ?: 0L) - (book?.linearPositionMs ?: 0L)).coerceAtLeast(0L)),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.auto_play_next), style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = book?.autoPlayNext ?: true,
                                onCheckedChange = viewModel::setAutoPlayNext,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onContinue,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = book?.needsReauth != true && !reauthing && !rescanning,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(
                                    if ((book?.lastPlayedAt ?: 0) > 0) R.string.continue_playback
                                    else R.string.start_playback,
                                ),
                            )
                        }
                    }
                }
                if (book?.needsReauth == true) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.folder_permission_lost),
                        color = MaterialTheme.colorScheme.error,
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
                if (rescanning) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.scanning_item, rescanProgress?.currentName.orEmpty()))
                }
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.chapters), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
            }
            items(chapters, key = { it.id }) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    isCurrent = chapter.id == book?.currentChapterId,
                    enabled = book?.needsReauth != true,
                    onClick = { onPlayChapter(chapter.id) },
                    onToggleCompleted = {
                        viewModel.setChapterCompleted(chapter.id, chapter.completionState != 2)
                    },
                    onEditTitle = {
                        editChapter = chapter
                        editChapterTitle = chapter.displayTitle
                    },
                )
            }
            if (bookmarks.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.bookmarks), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                }
                items(bookmarks, key = { "bm-${it.id}" }) { bm ->
                    BookmarkRow(
                        bookmark = bm,
                        onClick = { onPlayBookmark(bm.chapterId, bm.positionMs) },
                        onDelete = { viewModel.deleteBookmark(bm.id) },
                        onEdit = {
                            editBookmark = bm
                            editBookmarkNote = bm.note.orEmpty()
                        },
                    )
                }
            }
        }
    }

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
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.updateChapterTitle(chapter.id, null)
                    editChapter = null
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
private fun ChapterRow(
    chapter: Chapter,
    isCurrent: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onToggleCompleted: () -> Unit,
    onEditTitle: () -> Unit,
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
            style = MaterialTheme.typography.labelMedium,
            color = if (isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.width(34.dp),
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
        IconButton(onClick = onEditTitle) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_chapter_title))
        }
        IconButton(onClick = onToggleCompleted) {
            Icon(
                if (chapter.completionState == 2) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = stringResource(
                    if (chapter.completionState == 2) R.string.mark_incomplete else R.string.mark_completed,
                ),
                tint = if (chapter.completionState == 2) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_bookmark_note))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_bookmark))
        }
    }
}
