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
            bookRepository.removeBook(bookId)
            onDone()
        }
    }

    fun reauthFolder(uri: Uri) {
        if (_reauthing.value) return
        viewModelScope.launch {
            _reauthing.value = true
            _reauthProgress.value = null
            try {
                bookRepository.reauthBook(bookId, uri) { progress ->
                    _reauthProgress.value = progress
                }
                _message.value = "重新授权完成"
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
    }

    fun confirmRescan(onApplied: (bookId: Long, chapterId: Long?, positionMs: Long) -> Unit = { _, _, _ -> }) {
        val preview = _rescanPreview.value ?: return
        if (preview.plan.weakMatches.isNotEmpty() || preview.plan.ambiguous.isNotEmpty()) {
            // Auto-accept weak matches for MVP simplicity when user confirms dialog.
        }
        viewModelScope.launch {
            try {
                val acceptedWeak = preview.plan.weakMatches
                val result = bookRepository.applyRescan(
                    bookId = bookId,
                    plan = preview.plan,
                    acceptedWeak = acceptedWeak,
                )
                _rescanPreview.value = null
                _message.value = "已更新：+${preview.plan.addedCount} / -${preview.plan.removedCount} / ~${preview.plan.renamedCount}"
                onApplied(bookId, result.currentChapterId, result.currentPositionMs)
            } catch (e: Exception) {
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

    fun clearError() { _error.value = null }
    fun clearMessage() { _message.value = null }
}
