package com.tingxia.app.ui.chapters

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tingxia.app.R
import com.tingxia.app.data.model.Chapter
import com.tingxia.app.data.model.ChapterFilter
import com.tingxia.app.data.model.ChapterGroup
import com.tingxia.app.data.model.ChapterOrder
import com.tingxia.app.ui.components.TxChip

/**
 * Search / order / filter / locate bar plus the 选集 jump strip, shared by the book-detail list
 * and the player's picker sheet so both offer the same controls.
 */
@Composable
fun ChapterToolbar(
    controls: ChapterListControls,
    visibleCount: Int,
    totalCount: Int,
    cachedCount: Int?,
    groups: List<ChapterGroup>,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onToggleOrder: () -> Unit,
    onFilterChange: (ChapterFilter) -> Unit,
    onLocateCurrent: () -> Unit,
    onJumpToGroup: (Int) -> Unit,
    modifier: Modifier = Modifier,
    activeGroupIndex: Int = 0,
) {
    var filterMenu by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val summary = buildList {
                add(
                    if (controls.hasNarrowing) {
                        stringResource(R.string.chapter_count_filtered, visibleCount, totalCount)
                    } else {
                        stringResource(R.string.chapter_count_total, totalCount)
                    },
                )
                cachedCount?.let { add(stringResource(R.string.chapter_cached_count, it)) }
            }
            Text(
                summary.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = onToggleSearch) {
                Icon(
                    if (controls.searchOpen) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = stringResource(R.string.chapter_search),
                    tint = if (controls.query.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onToggleOrder) {
                Icon(
                    Icons.AutoMirrored.Filled.Sort,
                    contentDescription = stringResource(R.string.chapter_order_toggle),
                    tint = if (controls.order == ChapterOrder.DESC) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Box {
                IconButton(onClick = { filterMenu = true }) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = stringResource(R.string.chapter_filter),
                        tint = if (controls.filter != ChapterFilter.ALL) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                    val options = buildList {
                        add(ChapterFilter.ALL to R.string.chapter_filter_all)
                        add(ChapterFilter.UNFINISHED to R.string.chapter_filter_unfinished)
                        if (cachedCount != null) add(ChapterFilter.CACHED to R.string.chapter_filter_cached)
                    }
                    options.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            leadingIcon = {
                                if (controls.filter == value) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                filterMenu = false
                                onFilterChange(value)
                            },
                        )
                    }
                }
            }
            IconButton(onClick = onLocateCurrent) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = stringResource(R.string.chapter_locate_current),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (controls.searchOpen) {
            OutlinedTextField(
                value = controls.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.chapter_search_hint)) },
                trailingIcon = {
                    if (controls.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.online_search_clear),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = MaterialTheme.shapes.large,
            )
        }
        // 选集 strip: jump between 100-chapter blocks instead of scrolling through hundreds of
        // rows. Hidden while narrowed, where blocks would be partial and misleading.
        if (groups.size > 1 && groups.all { it.label != null }) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 2.dp),
            ) {
                items(groups.size) { index ->
                    val label = groups[index].label ?: return@items
                    TxChip(
                        label = stringResource(R.string.chapter_group_range, label.first, label.last),
                        selected = index == activeGroupIndex,
                        onClick = { onJumpToGroup(index) },
                    )
                }
            }
        }
    }
}

/** Multi-select action bar, shown in place of [ChapterToolbar] while chapters are selected. */
@Composable
fun ChapterSelectionBar(
    selectedCount: Int,
    supportsCache: Boolean,
    onSelectAllVisible: () -> Unit,
    onCache: () -> Unit,
    onClearCache: () -> Unit,
    onMark: (Boolean) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** Single-chapter rename, offered only when exactly one chapter is selected. */
    onRenameSingle: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
            }
            Text(
                stringResource(R.string.chapters_selected, selectedCount),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSelectAllVisible) {
                Text(stringResource(R.string.select_all_visible))
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (supportsCache) {
                item {
                    TextButton(onClick = onCache) { Text(stringResource(R.string.cache_selected)) }
                }
                item {
                    TextButton(onClick = onClearCache) {
                        Text(stringResource(R.string.cache_clear_selected))
                    }
                }
            }
            item {
                TextButton(onClick = { onMark(true) }) { Text(stringResource(R.string.mark_completed)) }
            }
            item {
                TextButton(onClick = { onMark(false) }) { Text(stringResource(R.string.mark_incomplete)) }
            }
            if (onRenameSingle != null && selectedCount == 1) {
                item {
                    TextButton(onClick = onRenameSingle) {
                        Text(stringResource(R.string.edit_chapter_title))
                    }
                }
            }
        }
    }
}

/**
 * Emits sticky group headers and one lazy item per chapter. Both call sites share this so the
 * detail page and the player sheet cannot drift apart.
 *
 * Lazy indices line up with [com.tingxia.app.data.model.ChapterPicker.flatIndexOf] plus
 * [indexOffset], which accounts for whatever items the caller placed before the list.
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.chapterGroupItems(
    groups: List<ChapterGroup>,
    currentChapterId: Long?,
    controls: ChapterListControls,
    enabled: Boolean,
    showCacheAction: Boolean,
    cachingChapterIds: Set<Long>,
    currentProgressFraction: Float?,
    /** The player is loading the current chapter; its row spins instead of showing a play glyph. */
    currentIsLoading: Boolean = false,
    headerColor: Color,
    onChapterClick: (Chapter) -> Unit,
    onChapterLongClick: (Chapter) -> Unit,
    onCacheClick: (Chapter) -> Unit,
    rowPadding: PaddingValues = PaddingValues(horizontal = 8.dp),
) {
    groups.forEach { group ->
        val label = group.label
        if (label != null) {
            stickyHeader(key = "chapter-header-${label.first}") {
                Surface(color = headerColor, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            stringResource(R.string.chapter_group_block, label.first, label.last),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
        items(group.chapters, key = { "chapter-${it.id}" }) { chapter ->
            ChapterRow(
                chapter = chapter,
                isCurrent = chapter.id == currentChapterId,
                modifier = Modifier.padding(rowPadding),
                enabled = enabled,
                progressFraction = currentProgressFraction,
                loading = currentIsLoading,
                showCacheAction = showCacheAction,
                cacheInProgress = chapter.id in cachingChapterIds,
                selectionMode = controls.selectionMode,
                selected = chapter.id in controls.selection,
                onCacheClick = { onCacheClick(chapter) },
                onClick = { onChapterClick(chapter) },
                onLongClick = { onChapterLongClick(chapter) },
            )
        }
    }
}
