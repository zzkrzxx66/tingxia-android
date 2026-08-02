package com.tingxia.app.ui.book

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingxia.app.R
import com.tingxia.app.data.model.Bookmark
import com.tingxia.app.data.model.Chapter
import com.tingxia.app.ui.components.AmbientBackground
import com.tingxia.app.ui.components.BookCover
import com.tingxia.app.ui.components.formatDuration
import com.tingxia.app.ui.theme.COVER_RATIO_PORTRAIT
import com.tingxia.app.ui.theme.CoverCorner
import com.tingxia.app.ui.theme.playerScrim

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
    val error by viewModel.error.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirmRemove by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var editBook by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editAuthor by remember { mutableStateOf("") }
    var editChapter by remember { mutableStateOf<Chapter?>(null) }
    var editChapterTitle by remember { mutableStateOf("") }
    var chapterMenuFor by remember { mutableStateOf<Chapter?>(null) }
    var editBookmark by remember { mutableStateOf<Bookmark?>(null) }
    var editBookmarkNote by remember { mutableStateOf("") }
    var bookmarkMenuFor by remember { mutableStateOf<Bookmark?>(null) }
    var editSkipOffsets by remember { mutableStateOf(false) }
    var skipIntroSeconds by remember { mutableStateOf("0") }
    var skipOutroSeconds by remember { mutableStateOf("0") }
    var autoPlayDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
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
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            item {
                // Immersive header: blurred artwork behind, crisp metadata on top.
                Box(modifier = Modifier.fillMaxWidth()) {
                    AmbientBackground(
                        coverPath = book?.coverPath,
                        title = book?.title.orEmpty(),
                        scrim = playerScrim,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.5f to Color.Transparent,
                                    1f to MaterialTheme.colorScheme.background,
                                ),
                            ),
                    )
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HeaderIconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                            Spacer(Modifier.weight(1f))
                            Box {
                                HeaderIconButton(onClick = { menu = true }) {
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
                                        text = { Text(stringResource(R.string.change_cover)) },
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
                                        text = {
                                            Column {
                                                Text(stringResource(R.string.skip_intro_outro))
                                                Text(
                                                    stringResource(
                                                        R.string.skip_intro_outro_summary,
                                                        (book?.skipIntroMs ?: 0L) / 1_000L,
                                                        (book?.skipOutroMs ?: 0L) / 1_000L,
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            menu = false
                                            skipIntroSeconds = ((book?.skipIntroMs ?: 0L) / 1_000L).toString()
                                            skipOutroSeconds = ((book?.skipOutroMs ?: 0L) / 1_000L).toString()
                                            editSkipOffsets = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(stringResource(R.string.auto_play_next))
                                                Text(
                                                    stringResource(
                                                        if (book?.autoPlayNext != false) R.string.on else R.string.off,
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            menu = false
                                            autoPlayDialog = true
                                        },
                                    )
                                    if (book?.isRemote != true) {
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
                                    }
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
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.remove_from_shelf),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            menu = false
                                            confirmRemove = true
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                shadowElevation = 12.dp,
                            ) {
                                BookCover(
                                    title = book?.title.orEmpty(),
                                    coverPath = book?.coverPath,
                                    size = 112.dp,
                                    ratio = COVER_RATIO_PORTRAIT,
                                    corner = CoverCorner.Detail,
                                    realistic = true,
                                )
                            }
                            Spacer(Modifier.width(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    book?.title.orEmpty(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                book?.author?.takeIf { it.isNotBlank() }?.let {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.85f),
                                    )
                                }
                                if (book?.isRemote == true) {
                                    Spacer(Modifier.height(8.dp))
                                    Surface(
                                        shape = MaterialTheme.shapes.extraSmall,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Default.Headphones,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "在线真人有声",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    stringResource(
                                        R.string.book_chapter_duration,
                                        chapters.size,
                                        formatDuration(book?.totalDurationMs ?: 0),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f),
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    if ((book?.lastPlayedAt ?: 0) > 0) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(R.string.listening_progress),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(
                                    R.string.remaining_time,
                                    formatDuration(
                                        ((book?.totalDurationMs ?: 0L) -
                                            (book?.linearPositionMs ?: 0L)).coerceAtLeast(0L),
                                    ),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { book?.progressFraction ?: 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(MaterialTheme.shapes.extraSmall),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = book?.needsReauth != true && !reauthing && !rescanning,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if ((book?.lastPlayedAt ?: 0) > 0) R.string.continue_playback
                                else R.string.start_playback,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    if (book?.needsReauth == true && book?.isRemote != true) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    stringResource(R.string.folder_permission_lost),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
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
                        }
                    }
                    if (rescanning) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.scanning_item, rescanProgress?.currentName.orEmpty()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    SecondaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.background,
                        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text(stringResource(R.string.chapters_with_count, chapters.size)) },
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text(stringResource(R.string.bookmarks_with_count, bookmarks.size)) },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            if (selectedTab == 0) {
                items(chapters, key = { it.id }) { chapter ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ChapterRow(
                            chapter = chapter,
                            isCurrent = chapter.id == book?.currentChapterId,
                            enabled = book?.needsReauth != true,
                            onClick = { onPlayChapter(chapter.id) },
                            onLongClick = { chapterMenuFor = chapter },
                        )
                    }
                }
            } else if (bookmarks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 42.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.no_bookmarks),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(bookmarks, key = { "bm-${it.id}" }) { bm ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        BookmarkRow(
                            bookmark = bm,
                            onClick = { onPlayBookmark(bm.chapterId, bm.positionMs) },
                            onLongClick = { bookmarkMenuFor = bm },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // Long-press menus
    chapterMenuFor?.let { chapter ->
        DropdownMenu(
            expanded = true,
            onDismissRequest = { chapterMenuFor = null },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_chapter_title)) },
                onClick = {
                    chapterMenuFor = null
                    editChapter = chapter
                    editChapterTitle = chapter.displayTitle
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (chapter.completionState == 2) R.string.mark_incomplete else R.string.mark_completed,
                        ),
                    )
                },
                onClick = {
                    chapterMenuFor = null
                    viewModel.setChapterCompleted(chapter.id, chapter.completionState != 2)
                },
            )
        }
    }

    bookmarkMenuFor?.let { bookmark ->
        DropdownMenu(
            expanded = true,
            onDismissRequest = { bookmarkMenuFor = null },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_bookmark_note)) },
                onClick = {
                    bookmarkMenuFor = null
                    editBookmark = bookmark
                    editBookmarkNote = bookmark.note.orEmpty()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.delete_bookmark),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    bookmarkMenuFor = null
                    viewModel.deleteBookmark(bookmark.id)
                },
            )
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

    if (autoPlayDialog) {
        AlertDialog(
            onDismissRequest = { autoPlayDialog = false },
            title = { Text(stringResource(R.string.auto_play_next)) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.auto_play_next_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = book?.autoPlayNext ?: true,
                        onCheckedChange = viewModel::setAutoPlayNext,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { autoPlayDialog = false }) { Text(stringResource(R.string.apply)) }
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

    if (editSkipOffsets) {
        val intro = skipIntroSeconds.toLongOrNull()
        val outro = skipOutroSeconds.toLongOrNull()
        val introValid = intro != null && intro in 0L..300L
        val outroValid = outro != null && outro in 0L..300L
        AlertDialog(
            onDismissRequest = { editSkipOffsets = false },
            title = { Text(stringResource(R.string.skip_intro_outro)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = skipIntroSeconds,
                        onValueChange = { value ->
                            if (value.all(Char::isDigit)) skipIntroSeconds = value
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.skip_intro_seconds)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = skipIntroSeconds.isNotEmpty() && !introValid,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = skipOutroSeconds,
                        onValueChange = { value ->
                            if (value.all(Char::isDigit)) skipOutroSeconds = value
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.skip_outro_seconds)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = skipOutroSeconds.isNotEmpty() && !outroValid,
                    )
                    if ((!introValid && skipIntroSeconds.isNotEmpty()) ||
                        (!outroValid && skipOutroSeconds.isNotEmpty())
                    ) {
                        Text(
                            stringResource(R.string.skip_seconds_range),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = introValid && outroValid,
                    onClick = {
                        viewModel.setSkipOffsets(
                            skipIntroMs = checkNotNull(intro) * 1_000L,
                            skipOutroMs = checkNotNull(outro) * 1_000L,
                        )
                        editSkipOffsets = false
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { editSkipOffsets = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
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
private fun HeaderIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.28f),
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides Color.White,
            ) {
                content()
            }
        }
    }
}

/** Fades the immersive header's lower edge into the page background. */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(
    chapter: Chapter,
    isCurrent: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val completed = chapter.completionState == 2
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .combinedClickable(
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.size(34.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (isCurrent) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(
                            text = "%02d".format(chapter.index + 1),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    chapter.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        isCurrent -> MaterialTheme.colorScheme.primary
                        completed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
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
            Icon(
                if (completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = stringResource(
                    if (completed) R.string.mark_incomplete else R.string.mark_completed,
                ),
                tint = if (completed) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 50.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
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
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 34.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        )
    }
}
