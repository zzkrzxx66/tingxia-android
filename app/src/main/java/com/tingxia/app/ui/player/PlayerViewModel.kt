package com.tingxia.app.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.tingxia.app.R
import com.tingxia.app.data.model.Chapter
import com.tingxia.app.data.model.ChapterFilter
import com.tingxia.app.data.repo.BookRepository
import com.tingxia.app.data.repo.BookmarkRepository
import com.tingxia.app.data.repo.ChapterTextRepository
import com.tingxia.app.player.CacheManager
import com.tingxia.app.player.PlayerController
import com.tingxia.app.player.PlayerUiState
import com.tingxia.app.player.PrefetchService
import com.tingxia.app.player.SleepTimerMode
import com.tingxia.app.ui.chapters.ChapterListControls
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    private val playerController: PlayerController,
    private val bookmarkRepository: BookmarkRepository,
    private val bookRepository: BookRepository,
    private val chapterTextRepository: ChapterTextRepository,
    private val cacheManager: CacheManager,
) : ViewModel() {

    val state: StateFlow<PlayerUiState> = playerController.state

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    // ---- read-along chapter text ------------------------------------------------------

    data class ChapterTextUiState(
        val visible: Boolean = false,
        val loading: Boolean = false,
        val chapterTitle: String = "",
        val timeline: com.tingxia.app.data.remote.FqChapterTimeline? = null,
        val error: String? = null,
    )

    private val _chapterText = MutableStateFlow(ChapterTextUiState())
    val chapterText: StateFlow<ChapterTextUiState> = _chapterText.asStateFlow()

    /** Whether the book being played has a text edition to read along with. */
    val textAvailable: StateFlow<Boolean> = state
        .map { it.bookId }
        .distinctUntilChanged()
        .map { bookId ->
            bookId?.let { id ->
                bookRepository.getBook(id)?.let { chapterTextRepository.canShowText(it) }
            } ?: false
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Open the text of the chapter that is currently loaded in the player. */
    fun openChapterText() {
        val bookId = state.value.bookId ?: return
        val chapterId = state.value.chapterId ?: return
        _chapterText.value = ChapterTextUiState(
            visible = true,
            loading = true,
            chapterTitle = state.value.chapterTitle.orEmpty(),
        )
        viewModelScope.launch {
            try {
                val book = bookRepository.getBook(bookId) ?: error("书籍不存在")
                val list = chapters.value.ifEmpty { bookRepository.getChapters(bookId) }
                val chapter = list.firstOrNull { it.id == chapterId } ?: error("章节不存在")
                val timeline = chapterTextRepository.timelineFor(book, chapter, list)
                _chapterText.value = _chapterText.value.copy(
                    loading = false,
                    chapterTitle = timeline.title.ifBlank { chapter.displayTitle },
                    timeline = timeline,
                )
            } catch (e: Exception) {
                _chapterText.value = _chapterText.value.copy(
                    loading = false,
                    error = e.message ?: app.getString(R.string.chapter_text_failed),
                )
            }
        }
    }

    /** Chapter changed under the drawer: reload so the highlight follows the new chapter. */
    fun refreshChapterTextIfOpen() {
        if (_chapterText.value.visible) openChapterText()
    }

    fun closeChapterText() {
        _chapterText.value = ChapterTextUiState()
    }

    /** Chapters of whatever book is loaded, so the picker never needs a separate load. */
    private val chapters: StateFlow<List<Chapter>> = state
        .map { it.bookId }
        .distinctUntilChanged()
        .flatMapLatest { bookId ->
            if (bookId == null) flowOf(emptyList()) else bookRepository.observeChapters(bookId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val isRemote: StateFlow<Boolean> = state
        .map { it.bookId }
        .distinctUntilChanged()
        .map { bookId -> bookId?.let { bookRepository.getBook(it)?.isRemote } ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Sheet-local controls; the chapter data itself is merged in from [chapters]. */
    private val _picker = MutableStateFlow(ChapterPickerUiState())

    val picker: StateFlow<ChapterPickerUiState> =
        combine(_picker, chapters, isRemote) { picker, list, remote ->
            picker.copy(
                chapters = list,
                isRemote = remote,
                // A rescan can delete chapters while they sit in a selection.
                controls = picker.controls.retainExisting(list.mapTo(mutableSetOf()) { it.id }),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChapterPickerUiState())

    /** Chapter ids currently downloading, for row spinners (kept as plain ids to avoid
     *  leaking the media3 opt-in type into the navigation layer). */
    val cachingChapterIds: StateFlow<Set<Long>> = PrefetchService.state
        .map { it.singleChapterIds }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun connect() = playerController.connect()

    fun playBook(
        bookId: Long,
        chapterId: Long? = null,
        positionMs: Long? = null,
        onResult: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val ok = playerController.playBook(bookId, chapterId, positionMs)
            onResult(ok)
        }
    }

    fun togglePlayPause() = playerController.togglePlayPause()
    fun seekTo(ms: Long) = playerController.seekTo(ms)
    fun seekBy(delta: Long) = playerController.seekBy(delta)
    fun nextChapter() = playerController.nextChapter()
    fun previousChapter() = playerController.previousChapter()
    fun setSpeed(speed: Float) = playerController.setSpeed(speed, asBookDefault = true)
    fun useGlobalSpeed() = playerController.useGlobalSpeed()
    fun setSleepMinutes(minutes: Int) = playerController.setSleepMinutes(minutes)
    fun setSleepEndOfChapter() = playerController.setSleepMode(SleepTimerMode.EndOfChapter)
    fun extendSleep() = playerController.extendSleep()
    fun clearError() = playerController.clearError()

    // ---- chapter picker --------------------------------------------------------------------

    fun openChapterPicker() {
        _picker.value = _picker.value.copy(visible = true)
    }

    fun closeChapterPicker() {
        // Search text and multi-select are transient; order and filter survive so a listener who
        // prefers 倒序 does not re-pick it on every visit.
        _picker.value = _picker.value.copy(visible = false, controls = _picker.value.controls.collapsed())
    }

    fun setPickerQuery(value: String) = updateControls { it.withQuery(value) }

    fun togglePickerSearch() = updateControls { it.toggleSearch() }

    fun togglePickerOrder() = updateControls { it.toggleOrder() }

    fun setPickerFilter(filter: ChapterFilter) = updateControls { it.withFilter(filter) }

    private fun updateControls(transform: (ChapterListControls) -> ChapterListControls) {
        _picker.value = _picker.value.copy(controls = transform(_picker.value.controls))
    }

    /**
     * Switch chapter without rebuilding the queue when the book is already loaded; the play/pause
     * state is preserved. Falls back to a full [PlayerController.playBook] otherwise.
     */
    fun playChapter(chapterId: Long) {
        if (playerController.playChapterInCurrentBook(chapterId)) return
        val bookId = state.value.bookId ?: return
        playBook(bookId, chapterId)
    }

    fun toggleSelection(chapterId: Long) = updateControls { it.toggleSelection(chapterId) }

    fun startSelection(chapterId: Long) = updateControls { it.startSelection(chapterId) }

    fun clearSelection() = updateControls { it.clearSelection() }

    fun selectAllVisible(chapterIds: List<Long>) = updateControls { it.selectAll(chapterIds) }

    fun cacheSelection() {
        val bookId = state.value.bookId ?: return
        val ids = _picker.value.controls.selection
        if (ids.isEmpty()) return
        PrefetchService.cacheChapters(app, bookId, ids)
        _toast.value = app.getString(R.string.cache_started)
        clearSelection()
    }

    fun clearCacheForSelection() {
        val bookId = state.value.bookId ?: return
        val ids = _picker.value.controls.selection.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val book = bookRepository.getBook(bookId) ?: return@launch
            ids.forEach { chapterId ->
                val itemId = chapters.value.firstOrNull { it.id == chapterId }?.remoteItemId ?: return@forEach
                try {
                    cacheManager.cache.removeResource(
                        cacheManager.cacheKeyForChapter(book.remoteAudioBookId.orEmpty(), itemId, book.remoteToneId),
                    )
                } catch (_: Exception) {
                }
                bookRepository.setChapterCached(chapterId, false)
            }
            _toast.value = app.getString(R.string.cache_cleared)
            clearSelection()
        }
    }

    fun markSelection(completed: Boolean) {
        val ids = _picker.value.controls.selection
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                bookRepository.setChaptersCompleted(ids, completed)
                _toast.value = app.getString(
                    if (completed) R.string.chapters_marked_done else R.string.chapters_marked_undone,
                    ids.size,
                )
                clearSelection()
            } catch (e: Exception) {
                _toast.value = e.message
            }
        }
    }

    fun cacheChapter(chapter: Chapter) {
        val bookId = state.value.bookId ?: return
        if (chapter.remoteItemId.isNullOrBlank()) return
        PrefetchService.cacheChapter(app, bookId, chapter.id)
    }

    fun clearChapterCache(chapter: Chapter) {
        val bookId = state.value.bookId ?: return
        val itemId = chapter.remoteItemId ?: return
        viewModelScope.launch {
            val book = bookRepository.getBook(bookId) ?: return@launch
            try {
                cacheManager.cache.removeResource(
                    cacheManager.cacheKeyForChapter(book.remoteAudioBookId.orEmpty(), itemId, book.remoteToneId),
                )
            } catch (_: Exception) {
            }
            bookRepository.setChapterCached(chapter.id, false)
        }
    }

    fun addBookmark() {
        val s = state.value
        val bookId = s.bookId ?: return
        val chapterId = s.chapterId ?: return
        viewModelScope.launch {
            try {
                bookmarkRepository.addBookmark(bookId, chapterId, s.positionMs)
                _toast.value = app.getString(R.string.bookmark_added)
            } catch (e: Exception) {
                _toast.value = e.message ?: app.getString(R.string.bookmark_add_failed)
            }
        }
    }

    fun setSkipOffsets(skipIntroMs: Long, skipOutroMs: Long) {
        val bookId = state.value.bookId ?: return
        viewModelScope.launch {
            try {
                playerController.setSkipOffsets(bookId, skipIntroMs, skipOutroMs)
                _toast.value = app.getString(R.string.skip_offsets_saved)
            } catch (e: Exception) {
                _toast.value = e.message ?: app.getString(R.string.skip_offsets_failed)
            }
        }
    }

    fun clearToast() { _toast.value = null }

    fun showMessage(message: String) {
        _toast.value = message
    }
}
