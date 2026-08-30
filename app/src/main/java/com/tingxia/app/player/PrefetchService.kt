package com.tingxia.app.player

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import com.tingxia.app.MainActivity
import com.tingxia.app.R
import com.tingxia.app.TingXiaApp
import com.tingxia.app.data.repo.BookRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Foreground service that prefetches online-book chapters into the shared
 * [CacheManager] audio cache, so they play without network later.
 *
 * One prefetch job runs at a time; starting a new job for the same book replaces
 * the queue. Progress is exposed through [state] and mirrored into a notification.
 */
@UnstableApi
@AndroidEntryPoint
class PrefetchService : Service() {

    @Inject lateinit var cacheManager: CacheManager
    @Inject lateinit var bookRepository: BookRepository
    @Inject lateinit var fqNovelApi: com.tingxia.app.data.remote.FqNovelApi

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    data class PrefetchState(
        val running: Boolean = false,
        val bookId: Long = 0L,
        val bookTitle: String = "",
        val totalCount: Int = 0,
        val doneCount: Int = 0,
        val currentChapterTitle: String = "",
        val failedCount: Int = 0,
        val finished: Boolean = false,
        val cancelled: Boolean = false,
        /** Single-chapter downloads keyed by chapter id, for row spinners. */
        val singleChapterIds: Set<Long> = emptySet(),
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> cancelJob()
            ACTION_START -> {
                val bookId = intent.getLongExtra(EXTRA_BOOK_ID, 0L)
                val fromIndex = intent.getIntExtra(EXTRA_FROM_INDEX, 0)
                val count = intent.getIntExtra(EXTRA_COUNT, -1) // -1 = all remaining
                if (bookId > 0L) startJob(bookId, fromIndex, count)
            }
            ACTION_CACHE_CHAPTER -> {
                val bookId = intent.getLongExtra(EXTRA_BOOK_ID, 0L)
                val chapterId = intent.getLongExtra(EXTRA_CHAPTER_ID, 0L)
                if (bookId > 0L && chapterId > 0L) cacheSingleChapter(bookId, chapterId)
            }
            ACTION_CACHE_SELECTION -> {
                val bookId = intent.getLongExtra(EXTRA_BOOK_ID, 0L)
                val ids = intent.getLongArrayExtra(EXTRA_CHAPTER_IDS)?.toList().orEmpty()
                if (bookId > 0L && ids.isNotEmpty()) startJob(bookId, chapterIds = ids)
            }
        }
        return START_NOT_STICKY
    }

    /** Download one chapter into the cache, without a foreground notification. */
    private fun cacheSingleChapter(bookId: Long, chapterId: Long) {
        scope.launch {
            _state.value = _state.value.copy(
                singleChapterIds = _state.value.singleChapterIds + chapterId,
            )
            try {
                val book = withContext(Dispatchers.IO) { bookRepository.getBook(bookId) }
                val chapter = withContext(Dispatchers.IO) { bookRepository.getChapter(chapterId) }
                if (book != null && chapter != null && !chapter.remoteItemId.isNullOrBlank()) {
                    val ok = withContext(Dispatchers.IO) {
                        prefetchUrl(cacheUrlFor(book, chapter.remoteItemId!!), cacheKeyFor(book, chapter))
                    }
                    if (ok) {
                        withContext(Dispatchers.IO) {
                            bookRepository.setChapterCached(chapterId, true)
                        }
                        fillDuration(book, chapter)
                    }
                }
            } finally {
                _state.value = _state.value.copy(
                    singleChapterIds = _state.value.singleChapterIds - chapterId,
                )
                if (job?.isActive != true && _state.value.singleChapterIds.isEmpty()) {
                    stopSelf()
                }
            }
        }
    }

    private fun cacheKeyFor(book: com.tingxia.app.data.model.Book, chapter: com.tingxia.app.data.model.Chapter): String =
        cacheManager.cacheKeyForChapter(
            book.remoteAudioBookId.orEmpty(),
            chapter.remoteItemId.orEmpty(),
            book.remoteToneId,
        )

    private fun cacheUrlFor(book: com.tingxia.app.data.model.Book, remoteItemId: String): String =
        com.tingxia.app.data.remote.FqEndpoints.streamUrl(
            book.remoteAudioBookId.orEmpty(),
            remoteItemId,
            book.remoteToneId,
        )

    /**
     * Fill in a chapter's duration from the service. The play endpoint knows it without
     * anyone downloading audio, so a cached chapter can show its length immediately.
     */
    private suspend fun fillDuration(
        book: com.tingxia.app.data.model.Book,
        chapter: com.tingxia.app.data.model.Chapter,
    ) {
        if (chapter.durationMs > 0L) return
        val itemId = chapter.remoteItemId?.takeIf { it.isNotBlank() } ?: return
        val audioBookId = book.remoteAudioBookId?.takeIf { it.isNotBlank() } ?: return
        runCatching {
            withContext(Dispatchers.IO) {
                fqNovelApi.chapterDurationMs(
                    audioBookId,
                    itemId,
                    com.tingxia.app.data.remote.FqEndpoints.normalizeTone(book.remoteToneId),
                )
            }
        }.getOrNull()?.takeIf { it > 0L }?.let { durationMs ->
            withContext(Dispatchers.IO) {
                bookRepository.recordRemoteChapterDuration(book.id, chapter.id, durationMs)
            }
        }
    }

    /** Blocking download of one URL into the shared cache. Caller provides the dispatcher. */
    private fun prefetchUrl(url: String, key: String): Boolean {
        return try {
            val dataSourceFactory = CacheDataSource.Factory()
                .setCache(cacheManager.cache)
                .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            val dataSpec = DataSpec.Builder().setUri(url).setKey(key).build()
            val dataSource = dataSourceFactory.createDataSource()
            val writer = CacheWriter(dataSource, dataSpec, null) { _, _, _ -> }
            try {
                writer.cache()
                true
            } finally {
                runCatching { dataSource.close() }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
    }

    /**
     * Queue a prefetch job. Either a window of the book ([fromIndex] / [count]) or an explicit
     * [chapterIds] selection, which wins when non-empty.
     */
    private fun startJob(
        bookId: Long,
        fromIndex: Int = 0,
        count: Int = -1,
        chapterIds: List<Long> = emptyList(),
    ) {
        job?.cancel()
        job = scope.launch {
            _state.value = PrefetchState(running = true, bookId = bookId)
            startForegroundWithNotification(buildNotification(_state.value))
            try {
                val book = withContext(Dispatchers.IO) { bookRepository.getBook(bookId) }
                val chapters = withContext(Dispatchers.IO) { bookRepository.getChapters(bookId) }
                if (book == null || chapters.isEmpty()) {
                    finish(cancelled = true)
                    return@launch
                }
                val selection = chapterIds.toSet()
                val targets = chapters
                    .filter { !it.remoteItemId.isNullOrBlank() }
                    .sortedBy { it.index }
                    .let { list ->
                        if (selection.isNotEmpty()) {
                            list.filter { it.id in selection }
                        } else {
                            val tail = if (fromIndex > 0) list.filter { it.index >= fromIndex } else list
                            if (count > 0) tail.take(count) else tail
                        }
                    }
                _state.value = _state.value.copy(
                    bookTitle = book.title,
                    totalCount = targets.size,
                )
                updateNotification()

                targets.forEach { chapter ->
                    currentCoroutineContext().ensureActive()
                    _state.value = _state.value.copy(currentChapterTitle = chapter.displayTitle)
                    updateNotification()
                    // Ask the service to decrypt and remux ahead of the download, so the
                    // first byte does not wait on ffmpeg.
                    withContext(Dispatchers.IO) {
                        chapter.remoteItemId?.let { itemId ->
                            fqNovelApi.warm(
                                book.remoteAudioBookId.orEmpty(),
                                itemId,
                                com.tingxia.app.data.remote.FqEndpoints.normalizeTone(book.remoteToneId),
                            )
                        }
                    }
                    val ok = withContext(Dispatchers.IO) {
                        prefetchUrl(cacheUrlFor(book, chapter.remoteItemId!!), cacheKeyFor(book, chapter))
                    }
                    if (ok) {
                        withContext(Dispatchers.IO) {
                            bookRepository.setChapterCached(chapter.id, true)
                        }
                        fillDuration(book, chapter)
                    }
                    _state.value = _state.value.copy(
                        doneCount = _state.value.doneCount + 1,
                        failedCount = _state.value.failedCount + if (ok) 0 else 1,
                    )
                    updateNotification()
                }
                finish(cancelled = false)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    finish(cancelled = true)
                } else {
                    finish(cancelled = false)
                }
            }
        }
    }

    private fun cancelJob() {
        job?.cancel()
        finish(cancelled = true)
    }

    private fun finish(cancelled: Boolean) {
        _state.value = _state.value.copy(
            running = false,
            finished = true,
            cancelled = cancelled,
        )
        updateNotification()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun startForegroundWithNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(_state.value))
    }

    private fun buildNotification(s: PrefetchState): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, TingXiaApp.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(s.running)
        when {
            s.running -> {
                builder
                    .setContentTitle(getString(R.string.prefetch_notification_title, s.bookTitle))
                    .setContentText(
                        getString(
                            R.string.prefetch_notification_progress,
                            s.doneCount, s.totalCount, s.currentChapterTitle,
                        ),
                    )
                    .setProgress(s.totalCount.coerceAtLeast(1), s.doneCount, false)
                    .addAction(
                        0,
                        getString(R.string.cancel),
                        PendingIntent.getService(
                            this, 1,
                            Intent(this, PrefetchService::class.java).setAction(ACTION_CANCEL),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
            }
            else -> {
                builder
                    .setContentTitle(getString(R.string.prefetch_notification_title, s.bookTitle))
                    .setContentText(
                        if (s.cancelled) {
                            getString(R.string.prefetch_notification_cancelled)
                        } else {
                            getString(
                                R.string.prefetch_notification_done,
                                s.doneCount - s.failedCount, s.failedCount,
                            )
                        },
                    )
                    .setProgress(0, 0, false)
                    .setAutoCancel(true)
            }
        }
        return builder.build()
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        const val ACTION_START = "com.tingxia.app.prefetch.START"
        const val ACTION_CANCEL = "com.tingxia.app.prefetch.CANCEL"
        const val ACTION_CACHE_CHAPTER = "com.tingxia.app.prefetch.CACHE_CHAPTER"
        const val ACTION_CACHE_SELECTION = "com.tingxia.app.prefetch.CACHE_SELECTION"
        const val EXTRA_BOOK_ID = "bookId"
        const val EXTRA_CHAPTER_ID = "chapterId"
        const val EXTRA_CHAPTER_IDS = "chapterIds"
        const val EXTRA_FROM_INDEX = "fromIndex"
        const val EXTRA_COUNT = "count"

        private val _state = MutableStateFlow(PrefetchState())
        val state: StateFlow<PrefetchState> = _state.asStateFlow()

        fun start(context: Context, bookId: Long, fromIndex: Int = 0, count: Int = -1) {
            val intent = Intent(context, PrefetchService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_BOOK_ID, bookId)
                putExtra(EXTRA_FROM_INDEX, fromIndex)
                putExtra(EXTRA_COUNT, count)
            }
            context.startForegroundService(intent)
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, PrefetchService::class.java).setAction(ACTION_CANCEL),
            )
        }

        /** Download a single chapter into the cache (no notification, no queue). */
        fun cacheChapter(context: Context, bookId: Long, chapterId: Long) {
            val intent = Intent(context, PrefetchService::class.java).apply {
                action = ACTION_CACHE_CHAPTER
                putExtra(EXTRA_BOOK_ID, bookId)
                putExtra(EXTRA_CHAPTER_ID, chapterId)
            }
            context.startService(intent)
        }

        /**
         * Download an explicit chapter selection as one foreground job, so a 50-chapter batch
         * shows a single progress notification instead of 50 silent downloads.
         */
        fun cacheChapters(context: Context, bookId: Long, chapterIds: Collection<Long>) {
            if (chapterIds.isEmpty()) return
            val intent = Intent(context, PrefetchService::class.java).apply {
                action = ACTION_CACHE_SELECTION
                putExtra(EXTRA_BOOK_ID, bookId)
                putExtra(EXTRA_CHAPTER_IDS, chapterIds.toLongArray())
            }
            context.startForegroundService(intent)
        }
    }
}
