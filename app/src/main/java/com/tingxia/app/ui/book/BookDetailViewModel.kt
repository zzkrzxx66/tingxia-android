package com.tingxia.app.ui.book

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingxia.app.data.importer.ScanProgress
import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.Bookmark
import com.tingxia.app.data.model.Chapter
import com.tingxia.app.data.repo.BookRepository
import com.tingxia.app.data.repo.BookmarkRepository
import com.tingxia.app.data.repo.RescanPreview
import com.tingxia.app.data.repo.ReauthDecisionRequiredException
import com.tingxia.app.player.LibraryMutationSnapshot
import com.tingxia.app.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val playerController: PlayerController,
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
                _message.value = "跳过设置已保存"
            } catch (e: Exception) {
                _error.value = e.message ?: "更新跳过设置失败"
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

    fun clearError() { _error.value = null }
    fun clearMessage() { _message.value = null }
}
