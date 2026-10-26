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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.tingxia.app.data.policy.LatestRequest
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
    private val _voiceLoading = MutableStateFlow(false)
    val fqLoading = combine(_fqLoading, _voiceLoading) { search, voice -> search || voice }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
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
    val isListView = preferences.shelfListView.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val globalDefaultSpeed = preferences.defaultSpeed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)
    val remainingByBook = combine(books, recent, bookRepository.observeChapterTimings(), globalDefaultSpeed) { list, recentBook, timings, globalSpeed ->
        val byBook = timings.groupBy { it.bookId }
        (list + listOfNotNull(recentBook)).distinctBy { it.id }.associate { book ->
            val remaining = com.tingxia.app.player.remainingPlaybackMs(book, byBook[book.id].orEmpty().map { it.asChapter() }, book.currentChapterId, book.currentPositionMs)
            book.id to remaining?.let { (it / (book.playbackSpeed ?: globalSpeed).coerceAtLeast(0.25f)).toLong() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setListView(value: Boolean) { viewModelScope.launch { preferences.setShelfListView(value) } }
    private var activeSearchQuery = ""
    private val searchRequests = LatestRequest<String>()
    private var searchTicket: LatestRequest.Ticket<String>? = null
    private var searchJob: Job? = null
    private var pageJob: Job? = null
    private var voiceJob: Job? = null
    private val voiceRequests = LatestRequest<String>()
    private var discoverJob: Job? = null
    private var fqDiscoverLoaded = false
    private val _discoverError = MutableStateFlow<String?>(null)
    val discoverError = _discoverError.asStateFlow()
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
        val normalized = keyword.trim()
        if (normalized.isEmpty()) return
        searchJob?.cancel()
        pageJob?.cancel()
        val ticket = searchRequests.begin(normalized)
        searchTicket = ticket
        activeSearchQuery = normalized
        _fqQuery.value = normalized
        _fqSearch.value = emptyList()
        _fqHasSearched.value = true
        _fqHasMore.value = false
        _fqLoading.value = true
        _fqLoadingMore.value = false
        fqPage = 1
        fqSearchId = null
        searchJob = viewModelScope.launch {
            try {
                val page = fqNovelApi.search(normalized, page = 1, size = fqPageSize)
                if (!searchRequests.accepts(ticket)) return@launch
                _fqSearch.value = page.books.distinctBy { it.bookId }
                fqSearchId = page.searchId
                _fqHasMore.value = page.hasMore && page.books.isNotEmpty()
                preferences.rememberOnlineSearch(normalized)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (searchRequests.accepts(ticket)) _error.value = error.message ?: "番茄搜索失败"
            } finally {
                if (searchRequests.accepts(ticket)) _fqLoading.value = false
            }
        }
    }

    fun loadMoreFqNovel() {
        val keyword = activeSearchQuery
        val ticket = searchTicket ?: return
        if (keyword.isEmpty() || !_fqHasMore.value || _fqLoadingMore.value || _fqLoading.value) return
        val next = fqPage + 1
        val searchId = fqSearchId
        _fqLoadingMore.value = true
        pageJob = viewModelScope.launch {
            try {
                val page = fqNovelApi.search(keyword, page = next, size = fqPageSize, searchId = searchId)
                if (!searchRequests.accepts(ticket)) return@launch
                val known = _fqSearch.value.mapTo(HashSet()) { it.bookId }
                val fresh = page.books.distinctBy { it.bookId }.filter { it.bookId !in known }
                _fqSearch.value = _fqSearch.value + fresh
                fqPage = next
                fqSearchId = page.searchId ?: fqSearchId
                _fqHasMore.value = page.hasMore && fresh.isNotEmpty()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (searchRequests.accepts(ticket)) _error.value = error.message ?: "加载更多失败"
            } finally {
                if (searchRequests.accepts(ticket)) _fqLoadingMore.value = false
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
        if (fqDiscoverLoaded || discoverJob?.isActive == true) return
        discoverJob = viewModelScope.launch {
            _discoverError.value = null
            try {
                _fqSections.value = fqNovelApi.discoverSections()
                if (_fqSections.value.isEmpty()) _fqHotBooks.value = fqNovelApi.hotAudioBooks()
                fqDiscoverLoaded = _fqSections.value.isNotEmpty() || _fqHotBooks.value.isNotEmpty()
                if (!fqDiscoverLoaded) _discoverError.value = "暂无推荐内容，可以重试或搜索书名"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _discoverError.value = error.message ?: "发现页加载失败，请重试"
            }
        }
    }

    fun selectFqBook(book: FqSearchBook) {
        voiceJob?.cancel()
        val ticket = voiceRequests.begin(book.bookId)
        _fqSelectedBook.value = book
        _fqVoices.value = null
        _voiceLoading.value = true
        voiceJob = viewModelScope.launch {
            try {
                val voices = fqNovelApi.voices(book.bookId)
                if (voiceRequests.accepts(ticket)) _fqVoices.value = voices
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (voiceRequests.accepts(ticket)) _error.value = error.message ?: "获取收听方式失败"
            } finally {
                if (voiceRequests.accepts(ticket)) _voiceLoading.value = false
            }
        }
    }

    fun clearFqSelection() {
        voiceJob?.cancel()
        voiceRequests.clear()
        _fqSelectedBook.value = null
        _fqVoices.value = null
        _voiceLoading.value = false
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
