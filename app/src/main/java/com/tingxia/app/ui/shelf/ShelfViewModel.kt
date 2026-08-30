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
import com.tingxia.app.data.remote.FqNovelApi
import com.tingxia.app.data.remote.FqSearchBook
import com.tingxia.app.data.remote.FqAudioTone
import com.tingxia.app.data.remote.FqDiscoverSection
import com.tingxia.app.data.remote.FqVoiceChoice
import com.tingxia.app.data.remote.FqVoices
import com.tingxia.app.data.repo.OnlineUpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShelfViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val preferences: UserPreferencesRepository,
    private val fqNovelApi: FqNovelApi,
    private val updateChecker: OnlineUpdateChecker,
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

    /** Drives the 需重新授权 filter chip, which is noise on a shelf where nothing is broken. */
    val hasReauthBooks: StateFlow<Boolean> = bookRepository.observeBooks()
        .map { list -> list.any { it.needsReauth } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _importProgress = MutableStateFlow<ScanProgress?>(null)
    val importProgress: StateFlow<ScanProgress?> = _importProgress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _fqSearch = MutableStateFlow<List<FqSearchBook>>(emptyList())
    val fqSearch: StateFlow<List<FqSearchBook>> = _fqSearch.asStateFlow()
    private val _fqQuery = MutableStateFlow("")
    val fqQuery: StateFlow<String> = _fqQuery.asStateFlow()
    private val _fqHasSearched = MutableStateFlow(false)
    val fqHasSearched: StateFlow<Boolean> = _fqHasSearched.asStateFlow()
    private val _fqLoading = MutableStateFlow(false)
    val fqLoading: StateFlow<Boolean> = _fqLoading.asStateFlow()
    private val _fqLoadingMore = MutableStateFlow(false)
    val fqLoadingMore: StateFlow<Boolean> = _fqLoadingMore.asStateFlow()
    private val _fqHasMore = MutableStateFlow(false)
    val fqHasMore: StateFlow<Boolean> = _fqHasMore.asStateFlow()
    private val _fqVoices = MutableStateFlow<FqVoices?>(null)
    val fqVoices: StateFlow<FqVoices?> = _fqVoices.asStateFlow()
    private val _fqSelectedBook = MutableStateFlow<FqSearchBook?>(null)
    val fqSelectedBook: StateFlow<FqSearchBook?> = _fqSelectedBook.asStateFlow()
    private val _fqImporting = MutableStateFlow(false)
    val fqImporting: StateFlow<Boolean> = _fqImporting.asStateFlow()
    private val _fqHotBooks = MutableStateFlow<List<FqSearchBook>>(emptyList())
    val fqHotBooks: StateFlow<List<FqSearchBook>> = _fqHotBooks.asStateFlow()
    private val _fqSections = MutableStateFlow<List<FqDiscoverSection>>(emptyList())
    val fqSections: StateFlow<List<FqDiscoverSection>> = _fqSections.asStateFlow()
    val fqHistory: StateFlow<List<String>> = preferences.onlineSearchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var fqDiscoverLoaded = false
    private var fqSearchId: String? = null
    private var fqPage = 1
    private var fqPageSize = 20

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

    fun searchFqNovel(keyword: String) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            _fqLoading.value = true
            try {
                val normalized = keyword.trim()
                _fqQuery.value = normalized
                fqPage = 1
                fqSearchId = null
                val page = fqNovelApi.search(normalized, page = 1, size = fqPageSize)
                _fqSearch.value = page.books
                fqSearchId = page.searchId
                _fqHasMore.value = page.hasMore && page.books.isNotEmpty()
                _fqHasSearched.value = true
                preferences.rememberOnlineSearch(normalized)
            } catch (e: Exception) {
                _error.value = e.message ?: "番茄搜索失败"
            } finally {
                _fqLoading.value = false
            }
        }
    }

    /** Next page of the current search, appended to the list. */
    fun loadMoreFqNovel() {
        val keyword = _fqQuery.value.trim()
        if (keyword.isEmpty() || !_fqHasMore.value || _fqLoadingMore.value || _fqLoading.value) return
        viewModelScope.launch {
            _fqLoadingMore.value = true
            try {
                val next = fqPage + 1
                val page = fqNovelApi.search(keyword, page = next, size = fqPageSize, searchId = fqSearchId)
                val known = _fqSearch.value.mapTo(HashSet()) { it.bookId }
                val fresh = page.books.filter { it.bookId !in known }
                _fqSearch.value = _fqSearch.value + fresh
                fqPage = next
                fqSearchId = page.searchId ?: fqSearchId
                // Upstream keeps reporting hasMore on repeated pages; an all-duplicate page
                // is the real end of the list.
                _fqHasMore.value = page.hasMore && fresh.isNotEmpty()
            } catch (e: Exception) {
                _error.value = e.message ?: "加载更多失败"
            } finally {
                _fqLoadingMore.value = false
            }
        }
    }

    fun clearFqHistory() {
        viewModelScope.launch { preferences.clearOnlineSearchHistory() }
    }

    fun setFqQuery(value: String) {
        _fqQuery.value = value
    }

    /** Load the discover page once per app session: sections first, hot list as fallback. */
    fun loadFqDiscover() {
        if (fqDiscoverLoaded) return
        fqDiscoverLoaded = true
        viewModelScope.launch {
            try {
                _fqSections.value = fqNovelApi.discoverSections()
            } catch (_: Exception) {
            }
            if (_fqSections.value.isEmpty()) {
                try {
                    _fqHotBooks.value = fqNovelApi.hotAudioBooks()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun selectFqBook(book: FqSearchBook) {
        viewModelScope.launch {
            _fqLoading.value = true
            try {
                _fqSelectedBook.value = book
                _fqVoices.value = fqNovelApi.voices(book.bookId)
            } catch (e: Exception) {
                _error.value = e.message ?: "获取收听方式失败"
            } finally {
                _fqLoading.value = false
            }
        }
    }

    fun clearFqSelection() {
        _fqSelectedBook.value = null
        _fqVoices.value = null
    }

    fun importFqNovel(book: FqSearchBook, choice: FqVoiceChoice, onDone: (Long) -> Unit) {
        if (_fqImporting.value) return
        viewModelScope.launch {
            _fqImporting.value = true
            try {
                val chapters = fqNovelApi.chapters(choice)
                // Narrated editions carry their own catalogue facts (total duration, score,
                // 完结状态); TTS reads the novel, which has no audio metadata of its own.
                val meta = if (choice.isTts) {
                    null
                } else {
                    runCatching { fqNovelApi.audioMeta(choice.audioBookId) }.getOrNull()
                }
                val id = bookRepository.importFqNovelBook(book, choice, chapters, meta)
                onDone(id)
                clearFqSelection()
            } catch (e: Exception) {
                _error.value = e.message ?: "加入书架失败"
            } finally {
                _fqImporting.value = false
            }
        }
    }

    init {
        // 追更: one catalogue request per unfinished online book, at most every few hours.
        viewModelScope.launch {
            runCatching { updateChecker.sweep() }
        }
    }
}
