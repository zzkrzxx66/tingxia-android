package com.tingxia.app.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tingxia.app.R
import com.tingxia.app.data.remote.FqSearchBook
import com.tingxia.app.ui.components.BookCover
import com.tingxia.app.ui.components.SectionCard
import com.tingxia.app.ui.components.formatWordCount
import com.tingxia.app.ui.theme.COVER_RATIO_PORTRAIT
import com.tingxia.app.ui.theme.CoverCorner

/** A candidate whose online chapter count does not match the local one. */
data class ChapterCountMismatch(
    val candidate: FqSearchBook,
    val localCount: Int,
    val remoteCount: Int,
    val remoteTitles: List<String>,
)

data class OnlineMetaSyncUiState(
    val visible: Boolean = false,
    val query: String = "",
    val candidates: List<FqSearchBook> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val applying: Boolean = false,
    val syncCover: Boolean = true,
    val syncChapterTitles: Boolean = true,
    val mismatch: ChapterCountMismatch? = null,
)

/**
 * Search the online catalogue and pick the entry whose blurb, author, cover and chapter titles get
 * copied onto this local book. The pick is always explicit: nothing is written until a candidate's
 * 使用此结果 is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineMetaSyncSheet(
    state: OnlineMetaSyncUiState,
    localChapterCount: Int,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onToggleCover: (Boolean) -> Unit,
    onToggleChapterTitles: (Boolean) -> Unit,
    onApply: (FqSearchBook) -> Unit,
    onConfirmMismatch: (alignAnyway: Boolean) -> Unit,
    onDismissMismatch: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.meta_sync_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.meta_sync_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.online_search_hint)) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.online_search_clear),
                                )
                            }
                        }
                        IconButton(
                            onClick = { onSearch(state.query) },
                            enabled = state.query.isNotBlank() && !state.loading,
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.online_search_submit),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(state.query) }),
                shape = MaterialTheme.shapes.large,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.syncCover,
                    onClick = { onToggleCover(!state.syncCover) },
                    label = { Text(stringResource(R.string.meta_sync_cover)) },
                )
                FilterChip(
                    selected = state.syncChapterTitles,
                    onClick = { onToggleChapterTitles(!state.syncChapterTitles) },
                    label = { Text(stringResource(R.string.meta_sync_chapter_titles)) },
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 8.dp)) {
                if (state.loading || state.applying) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            if (state.searched && state.candidates.isEmpty() && !state.loading) {
                Text(
                    stringResource(R.string.online_empty_hint, state.query),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.candidates, key = { it.bookId }) { candidate ->
                    MetaCandidateCard(
                        candidate = candidate,
                        localChapterCount = localChapterCount,
                        enabled = !state.applying,
                        onApply = { onApply(candidate) },
                    )
                }
            }
        }
    }

    state.mismatch?.let { mismatch ->
        // Aligning a different number of chapters is the one lossy-looking step, so it needs a
        // decision instead of a silent best effort.
        AlertDialog(
            onDismissRequest = onDismissMismatch,
            title = { Text(stringResource(R.string.meta_sync_mismatch_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.meta_sync_mismatch_body,
                        mismatch.localCount,
                        mismatch.remoteCount,
                        minOf(mismatch.localCount, mismatch.remoteCount),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { onConfirmMismatch(true) }) {
                    Text(stringResource(R.string.meta_sync_mismatch_align))
                }
            },
            dismissButton = {
                TextButton(onClick = { onConfirmMismatch(false) }) {
                    Text(stringResource(R.string.meta_sync_mismatch_book_only))
                }
            },
        )
    }
}

@Composable
private fun MetaCandidateCard(
    candidate: FqSearchBook,
    localChapterCount: Int,
    enabled: Boolean,
    onApply: () -> Unit,
) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            BookCover(
                title = candidate.title,
                coverPath = candidate.coverUrl,
                size = 64.dp,
                ratio = COVER_RATIO_PORTRAIT,
                corner = CoverCorner.Card,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    candidate.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildList {
                    add(candidate.author?.takeIf { it.isNotBlank() } ?: stringResource(R.string.unknown_author))
                    candidate.category?.takeIf { it.isNotBlank() }?.let(::add)
                    if (candidate.wordCount > 0) {
                        add(stringResource(R.string.word_count_wan, formatWordCount(candidate.wordCount)))
                    }
                }
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                candidate.description?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it.replace('\n', ' '),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.meta_sync_local_chapters, localChapterCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    if (enabled) {
                        TextButton(onClick = onApply) {
                            Text(stringResource(R.string.meta_sync_use_result))
                        }
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp).padding(end = 4.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}
