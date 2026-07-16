package com.tingxia.app.ui.book

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingxia.app.data.importer.ScanProgress
import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.Chapter
import com.tingxia.app.data.repo.BookRepository
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
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle["bookId"])

    val book: StateFlow<Book?> = bookRepository.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chapters: StateFlow<List<Chapter>> = bookRepository.observeChapters(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _reauthing = MutableStateFlow(false)
    val reauthing: StateFlow<Boolean> = _reauthing.asStateFlow()

    private val _reauthProgress = MutableStateFlow<ScanProgress?>(null)
    val reauthProgress: StateFlow<ScanProgress?> = _reauthProgress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

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
            } catch (e: Exception) {
                _error.value = e.message ?: "重新授权失败"
            } finally {
                _reauthing.value = false
                _reauthProgress.value = null
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
