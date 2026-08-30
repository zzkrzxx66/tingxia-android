package com.tingxia.app.ui.book

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingxia.app.R
import com.tingxia.app.data.importer.ScanProgress
import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.Bookmark
import com.tingxia.app.data.model.Chapter
import com.tingxia.app.data.model.ChapterFilter
import com.tingxia.app.ui.chapters.ChapterListControls
import com.tingxia.app.data.repo.BookRepository
import com.tingxia.app.data.repo.BookmarkRepository
import com.tingxia.app.data.repo.ChapterTextRepository
import com.tingxia.app.data.repo.OnlineUpdateChecker
import com.tingxia.app.data.repo.RescanPreview
import com.tingxia.app.data.repo.ReauthDecisionRequiredException
import com.tingxia.app.data.remote.FqChapterText
import com.tingxia.app.data.remote.FqNovelApi
import com.tingxia.app.data.remote.FqSearchBook
import com.tingxia.app.data.remote.FqTtsTone
import com.tingxia.app.data.remote.FqVoices
import com.tingxia.app.data.policy.ChapterTitleAligner
import com.tingxia.app.player.CacheManager
import com.tingxia.app.player.LibraryMutationSnapshot
import com.tingxia.app.player.PlayerController
import com.tingxia.app.player.PrefetchService
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class BookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val app: Context,
    private val bookRepository: BookRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val playerController: PlayerController,
    private val fqNovelApi: FqNovelApi,
    private val updateChecker: OnlineUpdateChecker,
    private val chapterTextRepository: ChapterTextRepository,
    val cacheManager: CacheManager,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle["bookId"])

    val book: StateFlow<Book?> = bookRepository.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chapters: StateFlow<List<Chapter>> = bookRepository.observeChapters(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bookmarks: StateFlow<List<Bookmark>> = bookmarkRepository.observeBookmarks(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _reauthing = MutableStateFlow(false)
    val reauthing: StateFlow<Boolean> = _reauthing.asStateFlow()

    private val _reauthProgress = MutableStateFlow<ScanProgress?>(null)
    val reauthProgress: StateFlow<ScanProgress?> = _reauthProgress.asStateFlow()

    private val _rescanning = MutableStateFlow(false)
    val rescanning: StateFlow<Boolean> = _rescanning.asStateFlow()

    private val _rescanProgress = MutableStateFlow<ScanProgress?>(null)
    val rescanProgress: StateFlow<ScanProgress?> = _rescanProgress.asStateFlow()

    private val _rescanPreview = MutableStateFlow<RescanPreview?>(null)
    val rescanPreview: StateFlow<RescanPreview?> = _rescanPreview.asStateFlow()
    private val weakDecisions = mutableMapOf<Long, Boolean>()
    private val ambiguousDecisions = mutableMapOf<String, Long?>()
    private val _decisionVersion = MutableStateFlow(0)
    val decisionVersion: StateFlow<Int> = _decisionVersion.asStateFlow()
    private var pendingReauthUri: Uri? = null

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Online-book prefetch progress, mirrored from the foreground service. */
    val prefetchState: StateFlow<PrefetchService.PrefetchState> = PrefetchService.state

    // ---- chapter list controls (search / order / filter / multi-select) ---------------------

    private val _chapterControls = MutableStateFlow(ChapterListControls())
    val chapterControls: StateFlow<ChapterListControls> = combine(
        _chapterControls,
        chapters,
    ) { controls, list ->
        controls.retainExisting(list.mapTo(mutableSetOf()) { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChapterListControls())

    private fun updateControls(transform: (ChapterListControls) -> ChapterListControls) {
        _chapterControls.value = transform(_chapterControls.value)
    }

    fun setChapterQuery(value: String) = updateControls { it.withQuery(value) }
    fun toggleChapterSearch() = updateControls { it.toggleSearch() }
    fun toggleChapterOrder() = updateControls { it.toggleOrder() }
    fun setChapterFilter(filter: ChapterFilter) = updateControls { it.withFilter(filter) }
    fun startChapterSelection(chapterId: Long) = updateControls { it.startSelection(chapterId) }
    fun toggleChapterSelection(chapterId: Long) = updateControls { it.toggleSelection(chapterId) }
    fun clearChapterSelection() = updateControls { it.clearSelection() }
    fun selectAllVisibleChapters(ids: List<Long>) = updateControls { it.selectAll(ids) }

    fun cacheSelectedChapters() {
        val ids = _chapterControls.value.selection
        if (ids.isEmpty() || book.value?.isRemote != true) return
        PrefetchService.cacheChapters(app, bookId, ids)
        _message.value = app.getString(R.string.cache_started)
        clearChapterSelection()
    }

    fun clearCacheForSelectedChapters() {
        val ids = _chapterControls.value.selection.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val book = book.value ?: return@launch
            ids.forEach { chapterId ->
                val itemId = chapters.value.firstOrNull { it.id == chapterId }?.remoteItemId
                    ?: return@forEach
                try {
                    cacheManager.cache.removeResource(
                        cacheManager.cacheKeyForChapter(book.remoteAudioBookId.orEmpty(), itemId, book.remoteToneId),
                    )
                } catch (_: Exception) {
                }
                bookRepository.setChapterCached(chapterId, false)
            }
            _message.value = app.getString(R.string.cache_cleared)
            clearChapterSelection()
        }
    }

    fun markSelectedChapters(completed: Boolean) {
        val ids = _chapterControls.value.selection
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                bookRepository.setChaptersCompleted(ids, completed)
                _message.value = app.getString(
                    if (completed) R.string.chapters_marked_done else R.string.chapters_marked_undone,
                    ids.size,
                )
                clearChapterSelection()
            } catch (e: Exception) {
                _error.value = e.message ?: "更新章节状态失败"
            }
        }
    }

    // ---- online metadata sync (local books) ------------------------------------------------

    private val _metaSync = MutableStateFlow(OnlineMetaSyncUiState())
    val metaSync: StateFlow<OnlineMetaSyncUiState> = _metaSync.asStateFlow()

    /** Open the sync sheet, pre-filled with the book title, and search straight away. */
    fun openMetaSync() {
        val title = book.value?.title.orEmpty()
        _metaSync.value = OnlineMetaSyncUiState(visible = true, query = title)
        if (title.isNotBlank()) searchMetaCandidates(title)
    }

    fun closeMetaSync() {
        _metaSync.value = OnlineMetaSyncUiState()
    }

    fun setMetaSyncQuery(value: String) {
        _metaSync.value = _metaSync.value.copy(query = value)
    }

    fun setMetaSyncCover(enabled: Boolean) {
        _metaSync.value = _metaSync.value.copy(syncCover = enabled)
    }

    fun setMetaSyncChapterTitles(enabled: Boolean) {
        _metaSync.value = _metaSync.value.copy(syncChapterTitles = enabled)
    }

    fun searchMetaCandidates(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.isEmpty()) return
        viewModelScope.launch {
            _metaSync.value = _metaSync.value.copy(query = normalized, loading = true, searched = false)
            try {
                val results = fqNovelApi.search(normalized).books
                _metaSync.value = _metaSync.value.copy(candidates = results, loading = false, searched = true)
            } catch (e: Exception) {
                _metaSync.value = _metaSync.value.copy(loading = false, searched = true)
                _error.value = e.message ?: "在线搜索失败"
            }
        }
    }

    /**
     * Apply one candidate. Chapter titles need the remote table of contents, so the flow stops at
     * an alignment step ([OnlineMetaSyncUiState.alignment]) where the number-based pairing can be
     * reviewed and, if the parser came up short, replaced by a manual drift.
     */
    fun applyMetaCandidate(candidate: FqSearchBook) {
        if (_metaSync.value.applying) return
        viewModelScope.launch {
            _metaSync.value = _metaSync.value.copy(applying = true)
            try {
                if (!_metaSync.value.syncChapterTitles) {
                    commitMetaSync(candidate, emptyMap())
                    return@launch
                }
                val remoteTitles = fetchRemoteChapterTitles(candidate)
                if (remoteTitles.isEmpty()) {
                    commitMetaSync(candidate, emptyMap())
                    return@launch
                }
                val local = chapters.value
                val plan = ChapterTitleAligner.byNumber(local, remoteTitles)
                _metaSync.value = _metaSync.value.copy(
                    applying = false,
                    alignment = ChapterAlignmentState(
                        candidate = candidate,
                        remoteTitles = remoteTitles,
                        localCount = local.size,
                        plan = plan,
                        suggestedOffset = ChapterTitleAligner.suggestedOffset(local, remoteTitles),
                    ),
                )
            } catch (e: Exception) {
                _metaSync.value = _metaSync.value.copy(applying = false)
                _error.value = e.message ?: "同步在线信息失败"
            }
        }
    }

    /** Switch the alignment step between number matching and manual drift. */
    fun setAlignmentMode(mode: ChapterTitleAligner.Mode) {
        val current = _metaSync.value.alignment ?: return
        if (current.plan.mode == mode) return
        val plan = when (mode) {
            ChapterTitleAligner.Mode.BY_NUMBER ->
                ChapterTitleAligner.byNumber(chapters.value, current.remoteTitles)
            // Entering manual mode starts from the drift the number matches imply, not from 0.
            ChapterTitleAligner.Mode.BY_OFFSET ->
                ChapterTitleAligner.byOffset(chapters.value, current.remoteTitles, current.suggestedOffset)
        }
        _metaSync.value = _metaSync.value.copy(alignment = current.copy(plan = plan))
    }

    /** Nudge the manual drift; the preview under it updates immediately. */
    fun setAlignmentOffset(offset: Int) {
        val current = _metaSync.value.alignment ?: return
        val range = ChapterTitleAligner.offsetRange(current.localCount, current.remoteTitles.size)
        val clamped = offset.coerceIn(range)
        val plan = ChapterTitleAligner.byOffset(chapters.value, current.remoteTitles, clamped)
        _metaSync.value = _metaSync.value.copy(alignment = current.copy(plan = plan))
    }

    /** Write the reviewed alignment. */
    fun confirmAlignment() {
        val current = _metaSync.value.alignment ?: return
        viewModelScope.launch {
            _metaSync.value = _metaSync.value.copy(alignment = null, applying = true)
            try {
                commitMetaSync(current.candidate, current.plan.updates)
            } catch (e: Exception) {
                _metaSync.value = _metaSync.value.copy(applying = false)
                _error.value = e.message ?: "同步在线信息失败"
            }
        }
    }

    /** Skip chapter titles entirely and sync only the book-level fields. */
    fun confirmBookFieldsOnly() {
        val current = _metaSync.value.alignment ?: return
        viewModelScope.launch {
            _metaSync.value = _metaSync.value.copy(alignment = null, applying = true)
            try {
                commitMetaSync(current.candidate, emptyMap())
            } catch (e: Exception) {
                _metaSync.value = _metaSync.value.copy(applying = false)
                _error.value = e.message ?: "同步在线信息失败"
            }
        }
    }

    fun dismissAlignment() {
        _metaSync.value = _metaSync.value.copy(alignment = null, applying = false)
    }

    fun clearOnlineMeta() {
        viewModelScope.launch {
            try {
                bookRepository.clearOnlineMeta(bookId)
                playerController.refreshQueueMetadata(bookId)
                _message.value = app.getString(R.string.meta_sync_cleared)
            } catch (e: Exception) {
                _error.value = e.message ?: app.getString(R.string.meta_sync_clear_failed)
            }
        }
    }

    private suspend fun commitMetaSync(candidate: FqSearchBook, titleUpdates: Map<Long, String>) {
        val overwriteCover = _metaSync.value.syncCover
        val outcome = bookRepository.applyOnlineMeta(
            bookId = bookId,
            remote = candidate,
            chapterTitleUpdates = titleUpdates,
            overwriteCover = overwriteCover,
        )
        playerController.refreshQueueMetadata(bookId)
        closeMetaSync()
        _message.value = if (outcome.chapterTitlesApplied > 0) {
            app.getString(R.string.meta_sync_done_with_titles, outcome.chapterTitlesApplied)
        } else {
            app.getString(R.string.meta_sync_done)
        }
    }

    /** Chapter titles live on the audio edition, so the tone list has to be resolved first. */
    private suspend fun fetchRemoteChapterTitles(candidate: FqSearchBook): List<String> {
        val voices = fqNovelApi.voices(candidate.bookId)
        val narrated = voices.audioBooks.firstOrNull()
        return if (narrated != null) {
            fqNovelApi.audioChapters(narrated.audioBookId).sortedBy { it.index }.map { it.title }
        } else {
            // No narrated edition: the novel's own table of contents is the better source
            // for titles anyway, and it exists for every book.
            fqNovelApi.novelChapters(candidate.bookId).sortedBy { it.index }.map { it.title }
        }
    }

    init {
        viewModelScope.launch {
            bookRepository.checkBookAccess(bookId)
        }
    }

    fun removeBook(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                playerController.prepareLibraryMutation(bookId, clearPlaylist = true)
                bookRepository.removeBook(bookId)
                onDone()
            } catch (e: Exception) {
                _error.value = e.message ?: "移除书籍失败"
            }
        }
    }

    fun reauthFolder(uri: Uri) {
        if (_reauthing.value) return
        viewModelScope.launch {
            _reauthing.value = true
            _reauthProgress.value = null
            try {
                bookRepository.reauthBook(bookId, uri, onProgress = { progress ->
                    _reauthProgress.value = progress
                })
                _message.value = "重新授权完成"
            } catch (e: ReauthDecisionRequiredException) {
                pendingReauthUri = uri
                weakDecisions.clear()
                ambiguousDecisions.clear()
                _rescanPreview.value = e.preview
                _message.value = "请确认新目录中的章节对应关系"
            } catch (e: Exception) {
                _error.value = e.message ?: "重新授权失败"
            } finally {
                _reauthing.value = false
                _reauthProgress.value = null
            }
        }
    }

    fun startRescan() {
        if (_rescanning.value) return
        viewModelScope.launch {
            _rescanning.value = true
            _rescanProgress.value = null
            _rescanPreview.value = null
            try {
                val preview = bookRepository.previewRescan(bookId) { p ->
                    _rescanProgress.value = p
                }
                _rescanPreview.value = preview
                weakDecisions.clear()
                ambiguousDecisions.clear()
                if (
                    preview.plan.addedCount == 0 &&
                    preview.plan.removedCount == 0 &&
                    preview.plan.renamedCount == 0 &&
                    preview.plan.ambiguousCount == 0
                ) {
                    _message.value = "目录无变化"
                    _rescanPreview.value = null
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "重新扫描失败"
            } finally {
                _rescanning.value = false
                _rescanProgress.value = null
            }
        }
    }

    fun dismissRescanPreview() {
        _rescanPreview.value = null
        pendingReauthUri = null
        weakDecisions.clear()
        ambiguousDecisions.clear()
    }

    fun decideWeak(oldChapterId: Long, accept: Boolean) {
        weakDecisions[oldChapterId] = accept
        _decisionVersion.value++
    }

    fun decideAmbiguous(scannedUri: String, oldChapterId: Long?) {
        ambiguousDecisions[scannedUri] = oldChapterId
        _decisionVersion.value++
    }

    fun isWeakDecided(oldChapterId: Long) = weakDecisions.containsKey(oldChapterId)
    fun weakAccepted(oldChapterId: Long) = weakDecisions[oldChapterId] == true
    fun isAmbiguousDecided(uri: String) = ambiguousDecisions.containsKey(uri)
    fun ambiguousChoice(uri: String) = ambiguousDecisions[uri]

    fun canConfirmRescan(): Boolean {
        val plan = _rescanPreview.value?.plan ?: return false
        return plan.weakMatches.keys.all(weakDecisions::containsKey) &&
            plan.ambiguous.all { ambiguousDecisions.containsKey(it.scanned.uri) }
    }

    fun confirmRescan() {
        val preview = _rescanPreview.value ?: return
        if (!canConfirmRescan()) return
        viewModelScope.launch {
            var mutation = LibraryMutationSnapshot()
            try {
                mutation = playerController.prepareLibraryMutation(bookId)
                val acceptedWeak = preview.plan.weakMatches.filterKeys { weakDecisions[it] == true }
                val rejectedWeak = preview.plan.weakMatches.keys.filterTo(mutableSetOf()) { weakDecisions[it] == false }
                val acceptedAmbiguous = ambiguousDecisions.mapNotNull { (uri, id) -> id?.let { uri to it } }.toMap()
                val rejectedAmbiguous = ambiguousDecisions.filterValues { it == null }.keys
                val reauthUri = pendingReauthUri
                val result = if (reauthUri != null) {
                    val updated = bookRepository.reauthBook(
                        bookId = bookId,
                        treeUri = reauthUri,
                        acceptedWeak = acceptedWeak,
                        acceptedAmbiguous = acceptedAmbiguous,
                        rejectedWeak = rejectedWeak,
                        rejectedAmbiguous = rejectedAmbiguous,
                        expectedBaseFingerprint = preview.baseChapterFingerprint,
                    )
                    com.tingxia.app.data.repo.RescanApplyResult(
                        book = updated,
                        chapters = bookRepository.getChapters(bookId),
                        currentChapterId = updated.currentChapterId,
                        currentPositionMs = updated.currentPositionMs,
                        removedChapterIds = emptySet(),
                    )
                } else {
                    bookRepository.applyRescan(
                        bookId = bookId,
                        plan = preview.plan,
                        acceptedWeak = acceptedWeak,
                        acceptedAmbiguous = acceptedAmbiguous,
                        rejectedWeak = rejectedWeak,
                        rejectedAmbiguous = rejectedAmbiguous,
                        expectedBaseFingerprint = preview.baseChapterFingerprint,
                        scannedCoverPath = preview.scannedCoverPath,
                    )
                }
                _rescanPreview.value = null
                pendingReauthUri = null
                _message.value = "已更新：+${preview.plan.addedCount} / -${preview.plan.removedCount} / ~${preview.plan.renamedCount}"
                if (mutation.wasActive) {
                    playerController.refreshPlaylistAfterRescan(
                        bookId = bookId,
                        chapterId = result.currentChapterId,
                        positionMs = result.currentPositionMs,
                        wasPlaying = mutation.wasPlaying,
                    )
                }
            } catch (e: Exception) {
                if (mutation.wasActive && mutation.wasPlaying) playerController.play()
                _error.value = e.message ?: "应用扫描结果失败"
            }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { bookmarkRepository.delete(id) }
    }

    fun updateBookmarkNote(id: Long, note: String?) {
        viewModelScope.launch { bookmarkRepository.updateNote(id, note) }
    }

    fun updateBookCover(uri: Uri?) {
        viewModelScope.launch {
            try {
                bookRepository.updateBookCover(bookId, uri)
                playerController.refreshQueueMetadata(bookId)
                _message.value = if (uri == null) "已移除自定义封面" else "封面已更新"
            } catch (e: Exception) {
                _error.value = e.message ?: "更新封面失败"
            }
        }
    }

    fun setAutoPlayNext(enabled: Boolean) {
        viewModelScope.launch {
            try {
                playerController.setAutoPlayNext(bookId, enabled)
            } catch (e: Exception) {
                _error.value = e.message ?: "更新连播设置失败"
            }
        }
    }

    fun setSkipOffsets(skipIntroMs: Long, skipOutroMs: Long) {
        viewModelScope.launch {
            try {
                playerController.setSkipOffsets(bookId, skipIntroMs, skipOutroMs)
                _message.value = app.getString(R.string.skip_offsets_saved)
            } catch (e: Exception) {
                _error.value = e.message ?: app.getString(R.string.skip_offsets_failed)
            }
        }
    }

    fun setChapterCompleted(chapterId: Long, completed: Boolean) {
        viewModelScope.launch {
            try {
                bookRepository.setChapterCompleted(chapterId, completed)
            } catch (e: Exception) {
                _error.value = e.message ?: "更新章节状态失败"
            }
        }
    }

    fun setAllChaptersCompleted(completed: Boolean) {
        viewModelScope.launch {
            try {
                bookRepository.setAllChaptersCompleted(bookId, completed)
                _message.value = if (completed) "已将全书标记为完成" else "已清除全书完成状态"
            } catch (e: Exception) {
                _error.value = e.message ?: "更新全书状态失败"
            }
        }
    }

    fun updateBookMetadata(title: String, author: String?) {
        viewModelScope.launch {
            try {
                bookRepository.updateBookMetadata(bookId, title, author)
                playerController.refreshQueueMetadata(bookId)
                _message.value = "书籍信息已更新"
            } catch (e: Exception) {
                _error.value = e.message ?: "更新书籍信息失败"
            }
        }
    }

    fun updateChapterTitle(chapterId: Long, title: String?) {
        viewModelScope.launch {
            try {
                bookRepository.updateChapterTitle(chapterId, title)
                playerController.refreshQueueMetadata(bookId)
            } catch (e: Exception) {
                _error.value = e.message ?: "更新章节标题失败"
            }
        }
    }

    /** Cache the whole book (or [count] chapters starting at [fromIndex]). */
    fun prefetch(fromIndex: Int = 0, count: Int = -1) {
        val book = book.value ?: return
        if (book.isRemote) {
            PrefetchService.start(app, bookId, fromIndex, count)
            _message.value = app.getString(R.string.cache_started)
        }
    }

    fun cancelPrefetch() {
        PrefetchService.cancel(app)
        _message.value = app.getString(R.string.cache_cancelled)
    }

    /** Download one chapter into the offline cache. */
    fun cacheChapter(chapter: Chapter) {
        val book = book.value ?: return
        if (!book.isRemote || chapter.remoteItemId.isNullOrBlank()) return
        PrefetchService.cacheChapter(app, bookId, chapter.id)
    }

    /** Remove one chapter's bytes from the offline cache. */
    fun clearChapterCache(chapter: Chapter) {
        viewModelScope.launch {
            val book = book.value ?: return@launch
            val itemId = chapter.remoteItemId ?: return@launch
            try {
                cacheManager.cache.removeResource(
                    cacheManager.cacheKeyForChapter(
                        book.remoteAudioBookId.orEmpty(),
                        itemId,
                        book.remoteToneId,
                    ),
                )
            } catch (_: Exception) {
            }
            bookRepository.setChapterCached(chapter.id, false)
        }
    }

    fun clearBookCache() {
        viewModelScope.launch {
            try {
                val book = book.value ?: return@launch
                val chapters = chapters.value
                chapters.forEach { ch ->
                    val itemId = ch.remoteItemId ?: return@forEach
                    val key = cacheManager.cacheKeyForChapter(
                        book.remoteAudioBookId.orEmpty(), itemId, book.remoteToneId,
                    )
                    try {
                        cacheManager.cache.removeResource(key)
                    } catch (_: Exception) {
                    }
                }
                bookRepository.clearCachedFlagForBook(bookId)
                _message.value = app.getString(R.string.cache_cleared)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    // ---- 追更 / voice switching / read-along text -----------------------------------------

    private val _checkingUpdate = MutableStateFlow(false)
    val checkingUpdate: StateFlow<Boolean> = _checkingUpdate.asStateFlow()

    /** Look for chapters published after this book was added. */
    fun checkForUpdates() {
        if (_checkingUpdate.value) return
        val book = book.value ?: return
        if (!book.isRemote) return
        viewModelScope.launch {
            _checkingUpdate.value = true
            try {
                val result = updateChecker.check(book)
                _message.value = when {
                    result == null -> app.getString(R.string.book_update_failed)
                    result.addedCount > 0 -> app.getString(R.string.book_update_added, result.addedCount)
                    else -> app.getString(R.string.book_update_none)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: app.getString(R.string.book_update_failed)
            } finally {
                _checkingUpdate.value = false
            }
        }
    }

    /** The "n 章未读" badge is answered by looking at the list, so opening it clears the count. */
    fun markNewChaptersSeen() {
        val book = book.value ?: return
        if (book.remoteNewChapterCount <= 0) return
        viewModelScope.launch { bookRepository.clearNewChapterBadge(bookId) }
    }

    private val _voiceSwitch = MutableStateFlow(VoiceSwitchUiState())
    val voiceSwitch: StateFlow<VoiceSwitchUiState> = _voiceSwitch.asStateFlow()

    data class VoiceSwitchUiState(
        val visible: Boolean = false,
        val loading: Boolean = false,
        val voices: FqVoices? = null,
        val currentToneId: String? = null,
    )

    /** Only TTS books can change voice in place; a narrated edition is a different catalogue. */
    fun openVoiceSwitch() {
        val book = book.value ?: return
        if (!book.isTtsVoice) return
        val novelBookId = book.remoteAudioBookId ?: return
        _voiceSwitch.value = VoiceSwitchUiState(
            visible = true,
            loading = true,
            currentToneId = book.remoteToneId,
        )
        viewModelScope.launch {
            try {
                val voices = fqNovelApi.voices(novelBookId)
                _voiceSwitch.value = _voiceSwitch.value.copy(loading = false, voices = voices)
            } catch (e: Exception) {
                _voiceSwitch.value = VoiceSwitchUiState()
                _error.value = e.message ?: app.getString(R.string.book_switch_voice_failed)
            }
        }
    }

    fun closeVoiceSwitch() {
        _voiceSwitch.value = VoiceSwitchUiState()
    }

    fun switchVoice(tone: FqTtsTone) {
        viewModelScope.launch {
            try {
                val changed = bookRepository.switchTtsTone(bookId, tone.toneId.toString())
                closeVoiceSwitch()
                if (changed) {
                    playerController.refreshQueueMetadata(bookId)
                    _message.value = app.getString(R.string.book_switch_voice_done)
                } else {
                    _error.value = app.getString(R.string.book_switch_voice_failed)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: app.getString(R.string.book_switch_voice_failed)
            }
        }
    }

    private val _chapterText = MutableStateFlow(ChapterTextUiState())
    val chapterText: StateFlow<ChapterTextUiState> = _chapterText.asStateFlow()

    data class ChapterTextUiState(
        val visible: Boolean = false,
        val loading: Boolean = false,
        val chapterTitle: String = "",
        val text: String = "",
        val error: String? = null,
    )

    val canShowChapterText: Boolean
        get() = book.value?.let { chapterTextRepository.canShowText(it) } ?: false

    fun openChapterText(chapter: Chapter) {
        val book = book.value ?: return
        _chapterText.value = ChapterTextUiState(
            visible = true,
            loading = true,
            chapterTitle = chapter.displayTitle,
        )
        viewModelScope.launch {
            try {
                val text: FqChapterText = chapterTextRepository.textFor(book, chapter, chapters.value)
                _chapterText.value = ChapterTextUiState(
                    visible = true,
                    loading = false,
                    chapterTitle = text.title.ifBlank { chapter.displayTitle },
                    text = text.text,
                )
            } catch (e: Exception) {
                _chapterText.value = _chapterText.value.copy(
                    loading = false,
                    error = e.message ?: app.getString(R.string.chapter_text_failed),
                )
            }
        }
    }

    fun closeChapterText() {
        _chapterText.value = ChapterTextUiState()
    }

    fun clearError() { _error.value = null }
    fun clearMessage() { _message.value = null }
}
