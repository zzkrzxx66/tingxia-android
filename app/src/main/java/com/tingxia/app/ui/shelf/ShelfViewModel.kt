package com.tingxia.app.ui.shelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingxia.app.data.importer.ScanProgress
import com.tingxia.app.data.model.Book
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
class ShelfViewModel @Inject constructor(
    private val bookRepository: BookRepository,
) : ViewModel() {

    val books: StateFlow<List<Book>> = bookRepository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<Book?> = bookRepository.observeRecentBook()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _importProgress = MutableStateFlow<ScanProgress?>(null)
    val importProgress: StateFlow<ScanProgress?> = _importProgress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun importFolder(uri: Uri) {
        if (_importing.value) return
        viewModelScope.launch {
            _importing.value = true
            _importProgress.value = null
            try {
                bookRepository.importFolder(uri) { progress ->
                    _importProgress.value = progress
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "导入失败"
            } finally {
                _importing.value = false
                _importProgress.value = null
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
