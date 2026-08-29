package com.tingxia.app.ui.chapters

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tingxia.app.R
import com.tingxia.app.data.model.Chapter
import com.tingxia.app.ui.components.formatDuration

/**
 * One chapter row, shared by the book-detail list and the player's chapter picker.
 *
 * The leading tile carries three states: unplayed (grey number), in progress (half ring),
 * finished (check). [progressFraction] is only known for the chapter that is actually loaded in
 * the player, so it stays null elsewhere — per-chapter positions are not persisted.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChapterRow(
    chapter: Chapter,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    progressFraction: Float? = null,
    showCacheAction: Boolean = false,
    cacheInProgress: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onCacheClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val completed = chapter.completionState == 2
    val inProgress = chapter.completionState == 1 || (isCurrent && !completed)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            // The current chapter gets a full-row tint, not just a coloured number tile,
            // so it stays findable while scrolling a long list.
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else -> Color.Transparent
                },
            )
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 10.dp, horizontal = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
                Spacer(Modifier.width(4.dp))
            } else {
                ChapterStateTile(
                    number = chapter.index + 1,
                    isCurrent = isCurrent,
                    inProgress = inProgress,
                    completed = completed,
                )
                Spacer(Modifier.width(12.dp))
            }
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
            if (showCacheAction && !chapter.remoteItemId.isNullOrBlank() && !selectionMode) {
                Spacer(Modifier.width(4.dp))
                if (cacheInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(
                        onClick = { onCacheClick?.invoke() },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            if (chapter.isCached) Icons.Default.CloudDone else Icons.Default.CloudDownload,
                            contentDescription = stringResource(
                                if (chapter.isCached) R.string.cached_badge else R.string.cache_menu,
                            ),
                            tint = if (chapter.isCached) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            if (!selectionMode) {
                Icon(
                    when {
                        completed -> Icons.Default.CheckCircle
                        inProgress -> Icons.Default.DonutLarge
                        else -> Icons.Default.RadioButtonUnchecked
                    },
                    contentDescription = stringResource(
                        if (completed) R.string.mark_incomplete else R.string.mark_completed,
                    ),
                    tint = when {
                        completed -> MaterialTheme.colorScheme.secondary
                        inProgress -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // Only the live chapter can show a real percentage; other in-progress chapters just
        // carry the half-ring tile.
        progressFraction?.takeIf { isCurrent && it > 0f }?.let { fraction ->
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(MaterialTheme.shapes.extraSmall),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun ChapterStateTile(
    number: Int,
    isCurrent: Boolean,
    inProgress: Boolean,
    completed: Boolean,
) {
    Surface(
        color = when {
            isCurrent -> MaterialTheme.colorScheme.primary
            completed -> MaterialTheme.colorScheme.secondaryContainer
            // Started-but-unfinished chapters get their own tile colour; before this they were
            // indistinguishable from never-played ones.
            inProgress -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
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
                    text = "%02d".format(number),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        completed -> MaterialTheme.colorScheme.onSecondaryContainer
                        inProgress -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
