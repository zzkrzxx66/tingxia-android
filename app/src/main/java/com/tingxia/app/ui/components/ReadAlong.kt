package com.tingxia.app.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.tingxia.app.data.remote.FqChapterTimeline
import com.tingxia.app.data.remote.FqTimelineSentence

/** Sentence ranges grouped by paragraph: (sentence index, start, end). */
typealias ReadAlongRanges = Map<Int, List<Triple<Int, Int, Int>>>

/**
 * Index of sentence ranges per paragraph, so highlighting and tap hit-testing cost
 * O(sentences in this paragraph) instead of a scan of the whole chapter on every frame.
 */
@Composable
fun rememberReadAlongRanges(timeline: FqChapterTimeline?): ReadAlongRanges = remember(timeline) {
    val map = HashMap<Int, MutableList<Triple<Int, Int, Int>>>()
    timeline?.sentences?.forEachIndexed { index, sentence ->
        sentence.spans.forEach { span ->
            map.getOrPut(span.paragraph) { mutableListOf() }.add(Triple(index, span.start, span.end))
        }
    }
    map
}

/**
 * The chapter text itself: one lazy item per paragraph, the spoken sentence highlighted.
 *
 * [dimInactive] is what makes it read like a lyrics screen — everything but the current
 * sentence recedes. The reading sheet keeps full contrast instead.
 */
@Composable
fun ReadAlongList(
    timeline: FqChapterTimeline,
    ranges: ReadAlongRanges,
    activeSentence: Int,
    fontSize: Float,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    dimInactive: Boolean = false,
    onColor: Color = Color.Unspecified,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onTapSentence: ((Int) -> Unit)? = null,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(timeline.paragraphs, key = { it.order }) { paragraph ->
            val paragraphRanges = ranges[paragraph.order].orEmpty()
            ReadAlongParagraph(
                text = paragraph.text,
                isTitle = paragraph.isTitle,
                fontSize = fontSize,
                ranges = paragraphRanges,
                activeSentence = activeSentence,
                dimmed = dimInactive && paragraphRanges.none { it.first == activeSentence },
                onColor = onColor,
                highlightColor = highlightColor,
                onTapSentence = onTapSentence,
            )
        }
    }
}

@Composable
private fun ReadAlongParagraph(
    text: String,
    isTitle: Boolean,
    fontSize: Float,
    ranges: List<Triple<Int, Int, Int>>,
    activeSentence: Int,
    dimmed: Boolean,
    onColor: Color,
    highlightColor: Color,
    onTapSentence: ((Int) -> Unit)?,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
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
    val baseStyle = if (isTitle) {
        MaterialTheme.typography.titleMedium.copy(
            fontSize = (fontSize + 2f).sp,
            lineHeight = ((fontSize + 2f) * 1.6f).sp,
        )
    } else {
        MaterialTheme.typography.bodyLarge.copy(
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.75f).sp,
        )
    }
    Text(
        annotated,
        style = baseStyle,
        color = when {
            onColor == Color.Unspecified -> Color.Unspecified
            dimmed -> onColor.copy(alpha = 0.45f)
            else -> onColor
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
 * Index of the sentence covering [positionMs], or the one just spoken.
 * Binary search: a chapter can carry several hundred sentences and this runs on every frame.
 */
internal fun activeSentenceIndex(sentences: List<FqTimelineSentence>, positionMs: Long): Int {
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
