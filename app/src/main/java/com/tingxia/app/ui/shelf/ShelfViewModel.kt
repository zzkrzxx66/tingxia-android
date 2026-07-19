package com.tingxia.app.ui.shelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingxia.app.data.importer.ScanProgress
import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.ShelfFilter
import com.tingxia.app.data.model.ShelfSort
import com.tingxia.app.data.repo.BookRepository
import com.tingxia.app.data.repo.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShelfViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val sort: StateFlow<ShelfSort> = preferences.shelfSort
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShelfSort.RECENT)

    val filter: StateFlow<ShelfFilter> = preferences.shelfFilter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShelfFilter.ALL)

    val books: StateFlow<List<Book>> = combine(
        _query.debounce(250),
        sort,
        filter,
    ) { q, s, f -> Triple(q, s, f) }
        .flatMapLatest { (q, s, f) -> bookRepository.observeBooks(q, s, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<Book?> = bookRepository.observeRecentBook()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _importProgress = MutableStateFlow<ScanProgress?>(null)
    val importProgress: StateFlow<ScanProgress?> = _importProgress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setSort(value: ShelfSort) {
        viewModelScope.launch { preferences.setShelfSort(value) }
    }

    fun setFilter(value: ShelfFilter) {
        viewModelScope.launch { preferences.setShelfFilter(value) }
    }

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

    fun importFiles(uris: List<Uri>) {
        if (_importing.value || uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true
            _importProgress.value = null
            try {
                bookRepository.importFiles(uris) { progress ->
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
