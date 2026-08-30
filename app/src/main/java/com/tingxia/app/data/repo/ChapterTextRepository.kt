package com.tingxia.app.data.repo

import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.Chapter
import com.tingxia.app.data.policy.ChapterTitleAligner
import com.tingxia.app.data.remote.FqAudioChapter
import com.tingxia.app.data.remote.FqChapterTimeline
import com.tingxia.app.data.remote.FqEndpoints
import com.tingxia.app.data.remote.FqNovelApi
import com.tingxia.app.data.remote.FqTimelineParagraph
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chapter text for the read-along drawer, with sentence timings where they can be trusted.
 *
 * Three sources, in order of preference:
 *
 * 1. **Online book, timeline** — the service returns the chapter's paragraphs plus the
 *    sentence time points. TTS books read the novel itself, so the timings line up
 *    exactly and the text can be highlighted in sync with the audio. Narrated editions
 *    return `synced = false`: their time points are computed against an older transcript
 *    revision than the one the service can fetch, so only the text is used.
 * 2. **Online book, plain text** — if the timeline request fails, the chapter text alone.
 *    Narrated editions have their own transcript under the same item id, so no chapter
 *    matching is involved.
 * 3. **Local book synced to the catalogue** — the novel's text, matched by chapter number.
 */
@Singleton
class ChapterTextRepository @Inject constructor(
    private val fqNovelApi: FqNovelApi,
) {

    private val tocCache = ConcurrentHashMap<String, List<FqAudioChapter>>()
    private val timelineCache = ConcurrentHashMap<String, FqChapterTimeline>()
    private val tocLock = Mutex()

    class TextUnavailableException(message: String) : Exception(message)

    /** Novel book id this book's text lives under, or null when there is nothing to link to. */
    fun novelBookIdFor(book: Book): String? = when {
        book.isRemote && book.isTtsVoice -> book.remoteAudioBookId
        book.isRemote -> book.remoteBookId
        else -> book.metaSyncSourceId
    }?.takeIf { it.isNotBlank() }

    fun canShowText(book: Book): Boolean =
        novelBookIdFor(book) != null || (book.isRemote && !book.remoteAudioBookId.isNullOrBlank())

    /**
     * Text plus timings for one chapter. Always returns something renderable, or throws
     * [TextUnavailableException] when this book has no text at all.
     */
    suspend fun timelineFor(
        book: Book,
        chapter: Chapter,
        chapters: List<Chapter>,
    ): FqChapterTimeline {
        val itemId = chapter.remoteItemId?.takeIf { it.isNotBlank() }
        val audioBookId = book.remoteAudioBookId?.takeIf { it.isNotBlank() }
        if (book.isRemote && itemId != null && audioBookId != null) {
            val tone = FqEndpoints.normalizeTone(book.remoteToneId)
            val cacheKey = "$audioBookId/$tone/$itemId"
            timelineCache[cacheKey]?.let { return it }
            val timeline = runCatching {
                fqNovelApi.chapterTimeline(audioBookId, itemId, tone, tts = book.isTtsVoice)
            }.getOrNull()
            if (timeline != null) {
                if (timelineCache.size > MAX_TIMELINE_CACHE) timelineCache.clear()
                timelineCache[cacheKey] = timeline
                return timeline
            }
            // Timeline unavailable (no time points, or the text has no paragraph structure):
            // fall back to the chapter's plain text under the same ids.
            fqNovelApi.chapterText(audioBookId, itemId)?.let { return it.asTimeline() }
        }
        return plainTextTimeline(book, chapter, chapters)
    }

    private suspend fun plainTextTimeline(
        book: Book,
        chapter: Chapter,
        chapters: List<Chapter>,
    ): FqChapterTimeline {
        val novelBookId = novelBookIdFor(book)
            ?: throw TextUnavailableException("这本书没有关联的文字版本")
        val itemId = resolveNovelItemId(book, chapter, chapters, novelBookId)
            ?: throw TextUnavailableException("没有找到这一章对应的文字正文")
        val text = fqNovelApi.chapterText(novelBookId, itemId)
            ?: throw TextUnavailableException("没有找到这一章对应的文字正文")
        return text.asTimeline()
    }

    private fun com.tingxia.app.data.remote.FqChapterText.asTimeline(): FqChapterTimeline {
        val paragraphs = text.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, line -> FqTimelineParagraph(index, index, index == 0, line) }
        return FqChapterTimeline(
            title = title,
            synced = false,
            paragraphs = paragraphs,
            sentences = emptyList(),
        )
    }

    private suspend fun resolveNovelItemId(
        book: Book,
        chapter: Chapter,
        chapters: List<Chapter>,
        novelBookId: String,
    ): String? {
        if (book.isRemote && book.isTtsVoice) {
            return chapter.remoteItemId?.takeIf { it.isNotBlank() }
        }
        val toc = novelToc(novelBookId)
        if (toc.isEmpty()) return null
        val number = ChapterTitleAligner.parseNumber(chapter.displayTitle)
            ?: ChapterTitleAligner.localNumber(chapter)
        if (number != null) {
            toc.firstOrNull { ChapterTitleAligner.parseNumber(it.title) == number }?.let { return it.itemId }
        }
        // Positional fallback: only trustworthy when both sides have the same length,
        // otherwise a 片头 file or a missing 序章 would shift every chapter.
        if (toc.size == chapters.size) {
            return toc.getOrNull(chapter.index)?.itemId
        }
        return null
    }

    private suspend fun novelToc(novelBookId: String): List<FqAudioChapter> {
        tocCache[novelBookId]?.let { return it }
        return tocLock.withLock {
            tocCache[novelBookId] ?: fqNovelApi.novelChapters(novelBookId).also { toc ->
                if (toc.isNotEmpty()) tocCache[novelBookId] = toc
            }
        }
    }

    private companion object {
        const val MAX_TIMELINE_CACHE = 24
    }
}
