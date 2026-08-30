package com.tingxia.app.data.repo

import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.Chapter
import com.tingxia.app.data.policy.ChapterTitleAligner
import com.tingxia.app.data.remote.FqAudioChapter
import com.tingxia.app.data.remote.FqChapterText
import com.tingxia.app.data.remote.FqNovelApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chapter text for the read-along drawer.
 *
 * The novel and its narrated edition are two different catalogues with different item
 * ids, so text can only be fetched after the audio chapter is mapped onto a text
 * chapter. Three cases:
 *
 * - TTS books already play the novel's own chapters, so the id is used as is.
 * - Narrated editions are matched by chapter number (第412章 on both sides), falling
 *   back to position when the numbers cannot be read on either side.
 * - Local books that were synced against the online catalogue use the synced source id.
 */
@Singleton
class ChapterTextRepository @Inject constructor(
    private val fqNovelApi: FqNovelApi,
) {

    private val tocCache = ConcurrentHashMap<String, List<FqAudioChapter>>()
    private val textCache = ConcurrentHashMap<String, FqChapterText>()
    private val tocLock = Mutex()

    class TextUnavailableException(message: String) : Exception(message)

    /** Novel book id this book's text lives under, or null when there is nothing to link to. */
    fun novelBookIdFor(book: Book): String? = when {
        book.isRemote && book.isTtsVoice -> book.remoteAudioBookId
        book.isRemote -> book.remoteBookId
        else -> book.metaSyncSourceId
    }?.takeIf { it.isNotBlank() }

    fun canShowText(book: Book): Boolean = novelBookIdFor(book) != null

    suspend fun textFor(book: Book, chapter: Chapter, chapters: List<Chapter>): FqChapterText {
        val novelBookId = novelBookIdFor(book)
            ?: throw TextUnavailableException("这本书没有关联的文字版本")
        val itemId = resolveItemId(book, chapter, chapters, novelBookId)
            ?: throw TextUnavailableException("没有找到这一章对应的文字正文")
        val cacheKey = "$novelBookId/$itemId"
        textCache[cacheKey]?.let { return it }
        val text = fqNovelApi.chapterText(novelBookId, itemId)
            ?: throw TextUnavailableException("没有找到这一章对应的文字正文")
        if (textCache.size > MAX_TEXT_CACHE) textCache.clear()
        textCache[cacheKey] = text
        return text
    }

    private suspend fun resolveItemId(
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
        const val MAX_TEXT_CACHE = 40
    }
}
