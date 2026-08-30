package com.tingxia.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tingxia.app.R
import com.tingxia.app.data.remote.FqChapterTimeline

/**
 * Read-along drawer for the book page: the chapter as plain reading, with a tap on a
 * sentence starting playback there. The player has its own full-page version of this,
 * where the audio position is live and the text follows it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterReadAlongSheet(
    chapterTitle: String,
    timeline: FqChapterTimeline?,
    loading: Boolean,
    error: String?,
    onSeek: ((Long) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var fontSize by remember { mutableFloatStateOf(17f) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val ranges = rememberReadAlongRanges(timeline)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.chapter_text_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        timeline?.title?.takeIf { it.isNotBlank() } ?: chapterTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.size(8.dp))
                IconButton(
                    onClick = { fontSize = (fontSize - 1f).coerceAtLeast(12f) },
                    enabled = fontSize > 12f,
                ) {
                    Icon(
                        Icons.Default.TextDecrease,
                        contentDescription = stringResource(R.string.chapter_text_font_smaller),
                    )
                }
                IconButton(
                    onClick = { fontSize = (fontSize + 1f).coerceAtMost(26f) },
                    enabled = fontSize < 26f,
                ) {
                    Icon(
                        Icons.Default.TextIncrease,
                        contentDescription = stringResource(R.string.chapter_text_font_larger),
                    )
                }
            }
            if (timeline?.synced == true && onSeek != null) {
                Text(
                    stringResource(R.string.chapter_text_tap_play_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(10.dp))
            when {
                loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        stringResource(R.string.chapter_text_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error != null -> Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                timeline == null || timeline.paragraphs.isEmpty() -> Text(
                    stringResource(R.string.chapter_text_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                else -> ReadAlongList(
                    timeline = timeline,
                    ranges = ranges,
                    activeSentence = -1,
                    fontSize = fontSize,
                    modifier = Modifier.heightIn(max = 560.dp),
                    listState = listState,
                    onTapSentence = if (onSeek == null || timeline.sentences.isEmpty()) {
                        null
                    } else {
                        { index -> timeline.sentences.getOrNull(index)?.let { onSeek(it.startMs) } }
                    },
                )
            }
        }
    }
}
