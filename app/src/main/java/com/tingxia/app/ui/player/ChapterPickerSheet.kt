package com.tingxia.app.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tingxia.app.R
import com.tingxia.app.data.model.Chapter
import com.tingxia.app.data.model.ChapterFilter
import com.tingxia.app.data.model.ChapterPicker
import com.tingxia.app.ui.chapters.ChapterSelectionBar
import com.tingxia.app.ui.chapters.ChapterToolbar
import com.tingxia.app.ui.chapters.chapterGroupItems
import kotlinx.coroutines.launch

/**
 * The player's 选集 drawer: pick another chapter without leaving playback.
 *
 * The toolbar stays put while the list scrolls, and every chapter is its own lazy item so a
 * 1000-chapter book only composes what is on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterPickerSheet(
    state: ChapterPickerUiState,
    currentChapterId: Long?,
    currentProgressFraction: Float?,
    currentIsLoading: Boolean = false,
    cachingChapterIds: Set<Long>,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onToggleOrder: () -> Unit,
    onFilterChange: (ChapterFilter) -> Unit,
    onPlayChapter: (Long) -> Unit,
    onStartSelection: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAllVisible: (List<Long>) -> Unit,
    onCacheSelection: () -> Unit,
    onClearCacheSelection: () -> Unit,
    onMarkSelection: (Boolean) -> Unit,
    onCacheChapter: (Chapter) -> Unit,
    onClearChapterCache: (Chapter) -> Unit,
) {
    if (!state.visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val controls = state.controls

    // Grouping is derived, not stored: recomputing 1000 rows on every recomposition would be
    // wasteful, so it is keyed on the inputs that actually change it.
    val groups = remember(state.chapters, controls.query, controls.filter, controls.order) {
        ChapterPicker.group(
            visible = ChapterPicker.visible(
                chapters = state.chapters,
                query = controls.query,
                filter = controls.filter,
                order = controls.order,
            ),
            grouped = ChapterPicker.shouldGroup(controls.query, controls.filter),
        )
    }
    val visibleChapters = remember(groups) { groups.flatMap { it.chapters } }
    // Which 选集 chip reads as active: the block the list is actually parked on.
    val activeGroupIndex by remember(groups) {
        derivedStateOf {
            var offset = 0
            groups.indexOfFirst { group ->
                val size = (if (group.label != null) 1 else 0) + group.chapters.size
                val contains = listState.firstVisibleItemIndex < offset + size
                offset += size
                contains
            }.coerceAtLeast(0)
        }
    }

    var locateRequested by remember { mutableStateOf(true) }
    LaunchedEffect(groups, currentChapterId, locateRequested) {
        if (!locateRequested) return@LaunchedEffect
        val target = currentChapterId?.let { ChapterPicker.flatIndexOf(groups, it) } ?: return@LaunchedEffect
        listState.scrollToItem(target)
        locateRequested = false
    }

    fun locate() {
        val target = currentChapterId?.let { ChapterPicker.flatIndexOf(groups, it) } ?: return
        scope.launch { listState.animateScrollToItem(target) }
    }

    val headerColor = MaterialTheme.colorScheme.surface

    androidx.activity.compose.BackHandler(enabled = controls.selectionMode) { onClearSelection() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxHeight(0.92f)) {
            Text(
                stringResource(R.string.chapter_picker_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, bottom = 2.dp),
            )
            if (controls.selectionMode) {
                ChapterSelectionBar(
                    selectedCount = controls.selection.size,
                    supportsCache = state.isRemote,
                    onSelectAllVisible = { onSelectAllVisible(visibleChapters.map { it.id }) },
                    onCache = onCacheSelection,
                    onClearCache = onClearCacheSelection,
                    onMark = onMarkSelection,
                    onCancel = onClearSelection,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                ChapterToolbar(
                    controls = controls,
                    visibleCount = visibleChapters.size,
                    totalCount = state.chapters.size,
                    cachedCount = if (state.isRemote) state.chapters.count { it.isCached } else null,
                    groups = groups,
                    onQueryChange = onQueryChange,
                    onToggleSearch = onToggleSearch,
                    onToggleOrder = onToggleOrder,
                    onFilterChange = onFilterChange,
                    onLocateCurrent = ::locate,
                    onJumpToGroup = { index ->
                        val target = ChapterPicker.flatIndexOf(
                            groups,
                            groups[index].chapters.first().id,
                        ) ?: return@ChapterToolbar
                        scope.launch { listState.animateScrollToItem((target - 1).coerceAtLeast(0)) }
                    },
                    activeGroupIndex = activeGroupIndex,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            if (visibleChapters.isEmpty()) {
                Text(
                    stringResource(R.string.chapter_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
                )
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                chapterGroupItems(
                    groups = groups,
                    currentChapterId = currentChapterId,
                    controls = controls,
                    enabled = true,
                    showCacheAction = state.isRemote,
                    cachingChapterIds = cachingChapterIds,
                    currentProgressFraction = currentProgressFraction,
                    currentIsLoading = currentIsLoading,
                    headerColor = headerColor,
                    onChapterClick = { chapter ->
                        if (controls.selectionMode) {
                            onToggleSelection(chapter.id)
                        } else {
                            onPlayChapter(chapter.id)
                        }
                    },
                    onChapterLongClick = { onStartSelection(it.id) },
                    onCacheClick = { chapter ->
                        if (chapter.isCached) onClearChapterCache(chapter) else onCacheChapter(chapter)
                    },
                    rowPadding = PaddingValues(horizontal = 12.dp),
                )
            }
        }
    }
}
