package com.tingxia.app.ui.components

import com.tingxia.app.data.remote.FqTimelineSentence
import com.tingxia.app.data.remote.FqTimelineSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveSentenceIndexTest {

    private fun sentence(startMs: Long, endMs: Long) = FqTimelineSentence(
        startMs = startMs,
        endMs = endMs,
        text = "s",
        spans = listOf(FqTimelineSpan(0, 0, 1)),
    )

    private val sentences = listOf(
        sentence(0, 3295),
        sentence(3295, 4960),
        sentence(4960, 6025),
        sentence(6025, 20554),
    )

    @Test
    fun findsSentenceCoveringPosition() {
        assertEquals(0, activeSentenceIndex(sentences, 0))
        assertEquals(0, activeSentenceIndex(sentences, 3294))
        assertEquals(1, activeSentenceIndex(sentences, 3295))
        assertEquals(3, activeSentenceIndex(sentences, 20553))
    }

    @Test
    fun keepsLastSentenceLitAfterItEnds() {
        // Gaps between sentences (breaths, sound effects) should not blank the highlight.
        assertEquals(3, activeSentenceIndex(sentences, 20554))
        assertEquals(3, activeSentenceIndex(sentences, 999_999))
    }

    @Test
    fun handlesGapsAndEmptyInput() {
        val withGap = listOf(sentence(0, 1000), sentence(5000, 6000))
        assertEquals(0, activeSentenceIndex(withGap, 2500))
        assertEquals(1, activeSentenceIndex(withGap, 5500))
        assertEquals(-1, activeSentenceIndex(emptyList(), 42))
    }
}
