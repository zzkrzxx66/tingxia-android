package com.tingxia.app.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tingxia.app.R
import com.tingxia.app.data.remote.FqChapterTimeline

/**
 * Read-along drawer: the chapter's text beside the audio.
 *
 * When the chapter carries trustworthy sentence timings ([FqChapterTimeline.synced]) the
 * sentence being spoken is highlighted, the list follows it, and tapping a sentence seeks
 * the audio there. Otherwise this is plain reading — highlighting the wrong sentence is
 * worse than highlighting none.
 *
 * Font size is deliberately local state: a reading comfort knob, not a setting worth
 * persisting across books.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterReadAlongSheet(
    chapterTitle: String,
    timeline: FqChapterTimeline?,
    loading: Boolean,
    error: String?,
    positionMs: Long?,
    onSeek: ((Long) -> Unit)?,
    onDismiss: () -> Unit,
    isPlaying: Boolean = false,
) {
    var fontSize by remember { mutableFloatStateOf(17f) }
    var follow by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    val synced = timeline?.synced == true && positionMs != null
    // The player polls its position every 500 ms; a sentence lasts a few seconds, so the
    // highlight is interpolated between polls or it would visibly lag at each boundary.
    val smoothPosition = com.tingxia.app.ui.player.rememberSmoothPositionMs(
        targetMs = positionMs ?: 0L,
        animate = isPlaying && synced,
    )
    val effectivePosition = if (synced) smoothPosition.value.toLong() else positionMs
    val activeSentence by remember(timeline, effectivePosition, synced) {
        derivedStateOf {
            val sentences = timeline?.sentences ?: return@derivedStateOf -1
            val position = effectivePosition ?: return@derivedStateOf -1
            if (!synced) -1 else activeSentenceIndex(sentences, position)
        }
    }

    // Per-paragraph index of the sentence ranges, so highlighting and hit testing are
    // both O(sentences in this paragraph) instead of a scan of the whole chapter.
    val rangesByParagraph = remember(timeline) {
        val map = HashMap<Int, MutableList<Triple<Int, Int, Int>>>()
        timeline?.sentences?.forEachIndexed { index, sentence ->
            sentence.spans.forEach { span ->
                map.getOrPut(span.paragraph) { mutableListOf() }
                    .add(Triple(index, span.start, span.end))
            }
        }
        map
    }

    // A manual drag means the reader took over; stop yanking the list back.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) follow = false
        }
    }
    LaunchedEffect(activeSentence, follow) {
        if (!follow || activeSentence < 0) return@LaunchedEffect
        val paragraph = timeline?.sentences?.getOrNull(activeSentence)?.spans?.firstOrNull()?.paragraph
            ?: return@LaunchedEffect
        listState.animateScrollToItem(paragraph.coerceAtLeast(0))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            if (synced) R.string.chapter_text_synced else R.string.chapter_text_title,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (synced) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        timeline?.title?.takeIf { it.isNotBlank() } ?: chapterTitle,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (synced) {
                    IconButton(onClick = { follow = !follow }) {
                        Icon(
                            if (follow) Icons.Default.MyLocation else Icons.Default.LocationSearching,
                            contentDescription = stringResource(
                                if (follow) R.string.chapter_text_following else R.string.chapter_text_follow,
                            ),
                            tint = if (follow) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
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
            if (synced && onSeek != null) {
                Text(
                    stringResource(R.string.chapter_text_tap_hint),
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
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(timeline.paragraphs, key = { _, item -> item.order }) { _, paragraph ->
                        ParagraphText(
                            text = paragraph.text,
                            isTitle = paragraph.isTitle,
                            fontSize = fontSize,
                            ranges = rangesByParagraph[paragraph.order].orEmpty(),
                            activeSentence = activeSentence,
                            onTapSentence = if (onSeek == null || timeline.sentences.isEmpty()) {
                                null
                            } else {
                                { sentenceIndex ->
                                    timeline.sentences.getOrNull(sentenceIndex)?.let { onSeek(it.startMs) }
                                    follow = true
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParagraphText(
    text: String,
    isTitle: Boolean,
    fontSize: Float,
    ranges: List<Triple<Int, Int, Int>>,
    activeSentence: Int,
    onTapSentence: ((Int) -> Unit)?,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val highlightColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, ranges, activeSentence, highlightColor) {
        val active = ranges.filter { it.first == activeSentence }
        if (active.isEmpty()) {
            AnnotatedString(text)
        } else {
            AnnotatedString.Builder(text).apply {
                active.forEach { (_, start, end) ->
                    val from = start.coerceIn(0, text.length)
                    val to = end.coerceIn(from, text.length)
                    if (to > from) {
                        addStyle(
                            SpanStyle(color = highlightColor, fontWeight = FontWeight.SemiBold),
                            from,
                            to,
                        )
                    }
                }
            }.toAnnotatedString()
        }
    }
    Text(
        annotated,
        style = if (isTitle) {
            MaterialTheme.typography.titleMedium.copy(
                fontSize = (fontSize + 2f).sp,
                lineHeight = ((fontSize + 2f) * 1.6f).sp,
            )
        } else {
            MaterialTheme.typography.bodyLarge.copy(
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.75f).sp,
            )
        },
        onTextLayout = { layout = it },
        modifier = if (onTapSentence == null) {
            Modifier.fillMaxWidth()
        } else {
            Modifier
                .fillMaxWidth()
                .pointerInput(ranges) {
                    detectTapGestures { position ->
                        val offset = layout?.getOffsetForPosition(position) ?: return@detectTapGestures
                        ranges.firstOrNull { (_, start, end) -> offset in start until end }
                            ?.let { onTapSentence(it.first) }
                    }
                }
        },
    )
}

/**
 * Index of the sentence covering [positionMs], or the one about to be spoken.
 * Binary search: a chapter can carry several hundred sentences and this runs on every poll.
 */
internal fun activeSentenceIndex(
    sentences: List<com.tingxia.app.data.remote.FqTimelineSentence>,
    positionMs: Long,
): Int {
    if (sentences.isEmpty()) return -1
    var low = 0
    var high = sentences.size - 1
    var candidate = -1
    while (low <= high) {
        val mid = (low + high) / 2
        val sentence = sentences[mid]
        when {
            positionMs < sentence.startMs -> high = mid - 1
            positionMs >= sentence.endMs -> {
                candidate = mid
                low = mid + 1
            }
            else -> return mid
        }
    }
    // Between two sentences: keep the previous one lit rather than flashing nothing.
    return candidate
}
