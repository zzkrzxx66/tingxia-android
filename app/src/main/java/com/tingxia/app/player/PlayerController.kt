package com.tingxia.app.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.Chapter
import com.tingxia.app.data.repo.BookRepository
import com.tingxia.app.data.repo.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PlayerUiState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val bookId: Long? = null,
    val bookTitle: String? = null,
    val chapterId: Long? = null,
    val chapterTitle: String? = null,
    val chapterIndex: Int = 0,
    val chapterCount: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    /** Linear position across the whole book (sum of chapter durations + current). */
    val bookPositionMs: Long = 0L,
    val bookDurationMs: Long = 0L,
    val speed: Float = 1.0f,
    val usesBookSpeedOverride: Boolean = false,
    val sleepMode: SleepTimerMode = SleepTimerMode.Off,
    val sleepRemainingMs: Long? = null,
    val sleepTargetChapterId: Long? = null,
    val coverPath: String? = null,
    val skipIntroMs: Long = 0L,
    val skipOutroMs: Long = 0L,
    val needsReauth: Boolean = false,
    val lastError: String? = null,
    val errorCanSkip: Boolean = false,
)

data class LibraryMutationSnapshot(
    val wasActive: Boolean = false,
    val wasPlaying: Boolean = false,
    val chapterId: Long? = null,
    val positionMs: Long = 0L,
)

@OptIn(UnstableApi::class)
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val preferences: UserPreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Inject lateinit var cacheManager: CacheManager

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var positionJob: Job? = null
    private var sleepJob: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
            if (isPlaying) startProgressLoop() else stopProgressLoop()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateFromMediaItem(mediaItem)
            // Manual chapter change keeps EndOfChapter timer targeting the new chapter.
            if (_state.value.sleepMode is SleepTimerMode.EndOfChapter) {
                _state.value = _state.value.copy(sleepTargetChapterId = _state.value.chapterId)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val c = controller ?: return
            _state.value = _state.value.copy(
                durationMs = c.duration.coerceAtLeast(0L),
                positionMs = c.currentPosition.coerceAtLeast(0L),
            )
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            _state.value = _state.value.copy(speed = playbackParameters.speed)
        }

        override fun onPlayerError(error: PlaybackException) {
            val bookId = _state.value.bookId
            val isPermission = error.errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                error.cause is SecurityException
            if (isPermission && bookId != null) {
                _state.value = _state.value.copy(
                    needsReauth = true,
                    isPlaying = false,
                    lastError = "目录权限失效，请重新授权",
                    errorCanSkip = false,
                )
                scope.launch(Dispatchers.IO) {
                    bookRepository.markNeedsReauth(bookId, true)
                }
            } else {
                val hasNextChapter = controller?.hasNextMediaItem() == true
                scope.launch {
                    val autoSkip = shouldSkipPlaybackError(
                        policy = preferences.playbackErrorPolicy.first(),
                        isPermissionError = false,
                        hasNextChapter = hasNextChapter,
                    )
                    _state.value = _state.value.copy(
                        isPlaying = if (autoSkip) _state.value.isPlaying else false,
                        lastError = if (autoSkip) {
                            "章节播放失败，已自动跳过"
                        } else {
                            error.message ?: "播放失败"
                        },
                        errorCanSkip = !autoSkip && hasNextChapter,
                    )
                }
            }
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            syncRuntimeState(extras)
        }

        override fun onDisconnected(controller: MediaController) {
            stopProgressLoop()
            sleepJob?.cancel()
            _state.value = _state.value.copy(isConnected = false)
        }
    }

    fun connect() {
        if (controller != null || controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture?.addListener({
            try {
                val c = controllerFuture?.get()
                controller = c
                c?.addListener(listener)
                _state.value = _state.value.copy(isConnected = c != null)
                if (c != null) {
                    syncFromController(c)
                    syncRuntimeState(c.sessionExtras)
                    if (c.isPlaying) startProgressLoop()
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isConnected = false,
                    lastError = e.message ?: "无法连接播放服务",
                )
            }
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        stopProgressLoop()
        sleepJob?.cancel()
        sleepJob = null
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
        _state.value = _state.value.copy(isConnected = false)
    }

    suspend fun playBook(
        bookId: Long,
        chapterId: Long? = null,
        positionMs: Long? = null,
    ): Boolean {
        ensureConnected()
        val accessible = bookRepository.checkBookAccess(bookId)
        if (!accessible) {
            _state.value = _state.value.copy(
                bookId = bookId,
                needsReauth = true,
                lastError = "目录权限失效，请重新授权",
            )
            return false
        }

        val book = bookRepository.getBook(bookId) ?: return false
        val chapters = bookRepository.getChapters(bookId)
        if (chapters.isEmpty()) return false

        val startChapterId = chapterId
            ?: book.currentChapterId
            ?: chapters.first().id
        val startIndex = chapters.indexOfFirst { it.id == startChapterId }.coerceAtLeast(0)
        val requestedPos = positionMs
            ?: if (chapterId == null || chapterId == book.currentChapterId) book.currentPositionMs else 0L
        val startChapter = chapters[startIndex]
        val startClip = chapterClip(
            startChapter.durationMs, book.skipIntroMs, book.skipOutroMs,
            startChapter.clipStartMs, startChapter.clipEndMs,
        )
        val startPos = clampToChapterClip(requestedPos, startClip, startChapter.durationMs)

        val items = buildQueueItems(book, chapters)
        val c = controller ?: return false
        c.setMediaItems(items, startIndex, startPos.coerceAtLeast(0L))
        val speed = book.playbackSpeed ?: preferences.defaultSpeed.first()
        c.setPlaybackSpeed(speed)
        // autoPlayNext: Media3 continues by default; service enforces EndOfChapter sleep.
        c.prepare()
        c.play()
        if (book.isRemote) {
            // Pre-populate the cached flag from whatever is already on disk
            // (playback itself fills the cache as it streams).
            scope.launch {
                chapters.forEach { ch ->
                    val itemId = ch.remoteItemId ?: return@forEach
                    if (!ch.isCached && isChapterFullyCached(book, itemId)) {
                        bookRepository.setChapterCached(ch.id, true)
                    }
                }
            }
        }

        _state.value = _state.value.copy(
            bookId = book.id,
            bookTitle = book.title,
            coverPath = book.coverPath,
            chapterCount = chapters.size,
            speed = speed,
            usesBookSpeedOverride = book.playbackSpeed != null,
            needsReauth = false,
            lastError = null,
            errorCanSkip = false,
        )
        updateFromMediaItem(c.currentMediaItem)
        return true
    }

    /** Rebuild playlist after rescan while preserving chapter id + position when possible. */
    suspend fun refreshPlaylistAfterRescan(
        bookId: Long,
        chapterId: Long?,
        positionMs: Long,
        wasPlaying: Boolean,
    ) {
        ensureConnected()
        if (_state.value.bookId != bookId) return
        val book = bookRepository.getBook(bookId) ?: return
        val chapters = bookRepository.getChapters(bookId)
        if (chapters.isEmpty()) return
        val c = controller ?: return
        val startId = chapterId ?: chapters.first().id
        val startIndex = chapters.indexOfFirst { it.id == startId }.coerceAtLeast(0)
        val ch = chapters[startIndex]
        val clip = chapterClip(
            ch.durationMs, book.skipIntroMs, book.skipOutroMs,
            ch.clipStartMs, ch.clipEndMs,
        )
        val pos = clampToChapterClip(positionMs, clip, ch.durationMs)
        c.setMediaItems(buildQueueItems(book, chapters), startIndex, pos)
        c.prepare()
        if (wasPlaying) c.play() else c.pause()
        _state.value = _state.value.copy(chapterCount = chapters.size)
        updateFromMediaItem(c.currentMediaItem)
    }

    /**
     * Switch to another chapter of the book that is already loaded, without rebuilding the queue.
     * Keeps the current play/pause state — tapping a chapter while paused should not start audio.
     * Returns false when the book is not the live one, so the caller can fall back to [playBook].
     */
    fun playChapterInCurrentBook(chapterId: Long): Boolean {
        val bookId = _state.value.bookId ?: return false
        val c = controller ?: return false
        val targetId = "${bookId}_$chapterId"
        val index = (0 until c.mediaItemCount).firstOrNull { c.getMediaItemAt(it).mediaId == targetId }
            ?: return false
        c.seekTo(index, 0L)
        updateFromMediaItem(c.getMediaItemAt(index))
        return true
    }

    /** Flushes Service progress before chapters are changed or a book is removed. */
    suspend fun prepareLibraryMutation(
        bookId: Long,
        clearPlaylist: Boolean = false,
    ): LibraryMutationSnapshot {
        ensureConnected()
        val c = controller ?: return LibraryMutationSnapshot()
        val result = c.sendCustomCommand(
            SessionCommand(CustomCommands.PREPARE_LIBRARY_MUTATION, Bundle.EMPTY),
            bundleOf(
                PlaybackStateKeys.MUTATION_BOOK_ID to bookId,
                PlaybackStateKeys.MUTATION_CLEAR_PLAYLIST to clearPlaylist,
            ),
        ).awaitResult()
        if (result.resultCode != androidx.media3.session.SessionResult.RESULT_SUCCESS) {
            error("无法保存当前播放进度，请稍后重试")
        }
        val extras = result.extras
        val snapshot = LibraryMutationSnapshot(
            wasActive = extras.getBoolean(PlaybackStateKeys.MUTATION_WAS_ACTIVE, false),
            wasPlaying = extras.getBoolean(PlaybackStateKeys.MUTATION_WAS_PLAYING, false),
            chapterId = extras.getLong(PlaybackStateKeys.MUTATION_CHAPTER_ID, -1L).takeIf { it > 0L },
            positionMs = extras.getLong(PlaybackStateKeys.MUTATION_POSITION_MS, 0L),
        )
        if (clearPlaylist && snapshot.wasActive) {
            _state.value = PlayerUiState(isConnected = true)
        }
        return snapshot
    }

    fun play() = controller?.play()
    fun pause() {
        controller?.pause()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val target = (c.currentPosition + deltaMs).coerceAtLeast(0L)
        val dur = c.duration
        c.seekTo(if (dur > 0) target.coerceAtMost(dur) else target)
    }

    fun nextChapter() {
        controller?.seekToNextMediaItem()
    }

    fun previousChapter() {
        val c = controller ?: return
        if (c.currentPosition > 3_000L) {
            c.seekTo(0L)
        } else {
            c.seekToPreviousMediaItem()
        }
    }

    fun setSpeed(speed: Float, asBookDefault: Boolean = true) {
        require(speed in PlaybackSpeeds.ALL) { "不支持的播放倍速" }
        controller?.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(
            speed = speed,
            usesBookSpeedOverride = asBookDefault && _state.value.bookId != null,
        )
        val bookId = _state.value.bookId
        scope.launch {
            if (asBookDefault && bookId != null) {
                bookRepository.setBookPlaybackSpeed(bookId, speed)
                refreshQueueMetadata(bookId)
            } else {
                preferences.setDefaultSpeed(speed)
            }
        }
        if (bookId != null) {
            val args = bundleOf("bookId" to bookId, "speed" to speed)
            controller?.sendCustomCommand(
                SessionCommand(CustomCommands.SET_BOOK_SPEED, Bundle.EMPTY),
                args,
            )
        }
    }

    fun useGlobalSpeed() {
        val bookId = _state.value.bookId ?: return
        scope.launch {
            bookRepository.setBookPlaybackSpeed(bookId, null)
            val speed = preferences.defaultSpeed.first()
            controller?.setPlaybackSpeed(speed)
            _state.value = _state.value.copy(speed = speed, usesBookSpeedOverride = false)
            refreshQueueMetadata(bookId)
        }
    }

    suspend fun setAutoPlayNext(bookId: Long, enabled: Boolean) {
        ensureConnected()
        val c = controller
        bookRepository.setAutoPlayNext(bookId, enabled)
        if (c == null || _state.value.bookId != bookId) {
            return
        }
        val result = c.sendCustomCommand(
            SessionCommand(CustomCommands.SET_AUTO_PLAY_NEXT, Bundle.EMPTY),
            bundleOf("bookId" to bookId, "enabled" to enabled),
        ).awaitResult()
        if (result.resultCode != androidx.media3.session.SessionResult.RESULT_SUCCESS) {
            error("更新连播设置失败")
        }
        refreshQueueMetadata(bookId)
    }

    suspend fun setSkipOffsets(bookId: Long, skipIntroMs: Long, skipOutroMs: Long) {
        bookRepository.setSkipOffsets(bookId, skipIntroMs, skipOutroMs)
        if (_state.value.bookId != bookId) return

        ensureConnected()
        val c = controller ?: return
        val book = bookRepository.getBook(bookId) ?: return
        val chapters = bookRepository.getChapters(bookId)
        if (chapters.isEmpty()) return

        val currentChapterId = _state.value.chapterId
        val startIndex = chapters.indexOfFirst { it.id == currentChapterId }
            .takeIf { it >= 0 }
            ?: c.currentMediaItemIndex.coerceIn(0, chapters.lastIndex)
        val chapter = chapters[startIndex]
        // currentPosition is relative to whatever clip the live queue was built with,
        // so map it through source-file coordinates into the new clip window.
        val liveClipStartMs = c.currentMediaItem?.clippingConfiguration?.startPositionMs ?: 0L
        val absolutePositionMs = liveClipStartMs + c.currentPosition.coerceAtLeast(0L)
        val newClip = chapterClip(
            chapter.durationMs, book.skipIntroMs, book.skipOutroMs,
            chapter.clipStartMs, chapter.clipEndMs,
        )
        val mappedPositionMs = absoluteToClipRelative(absolutePositionMs, newClip, chapter.durationMs)
        // A grown outro must not drop the listener onto the clip end and instantly
        // finish the chapter; keep at least the minimum playable tail.
        val startPositionMs = newClip.playableDurationMs
            ?.let { playable -> mappedPositionMs.coerceAtMost((playable - MINIMUM_PLAYABLE_MS).coerceAtLeast(0L)) }
            ?: mappedPositionMs
        val wasPlaying = c.isPlaying
        val speed = c.playbackParameters.speed

        c.setMediaItems(
            buildQueueItems(book, chapters),
            startIndex,
            startPositionMs,
        )
        c.setPlaybackSpeed(speed)
        c.prepare()
        if (wasPlaying) c.play() else c.pause()
        _state.value = _state.value.copy(chapterCount = chapters.size)
        updateFromMediaItem(c.currentMediaItem)
    }

    suspend fun refreshQueueMetadata(bookId: Long) {
        if (_state.value.bookId != bookId) return
        val c = controller ?: return
        val book = bookRepository.getBook(bookId) ?: return
        val chapters = bookRepository.getChapters(bookId)
        if (chapters.isEmpty() || c.mediaItemCount != chapters.size) return
        c.replaceMediaItems(0, c.mediaItemCount, buildQueueItems(book, chapters))
        _state.value = _state.value.copy(
            bookTitle = book.title,
            coverPath = book.coverPath,
            usesBookSpeedOverride = book.playbackSpeed != null,
        )
        updateFromMediaItem(c.currentMediaItem)
    }

    fun setSleepMinutes(minutes: Int) {
        if (minutes <= 0) {
            setSleepMode(SleepTimerMode.Off)
        } else {
            setSleepMode(SleepTimerMode.AfterDuration(minutes * 60_000L))
        }
    }

    fun setSleepMode(mode: SleepTimerMode) {
        val c = controller ?: return
        val args = when (mode) {
            is SleepTimerMode.Off -> bundleOf("mode" to "off")
            is SleepTimerMode.EndOfChapter -> bundleOf(
                "mode" to "end_of_chapter",
                "chapterId" to (_state.value.chapterId ?: -1L),
            )
            is SleepTimerMode.AfterDuration -> bundleOf(
                "mode" to "duration",
                "durationMs" to mode.durationMs,
            )
        }
        c.sendCustomCommand(SessionCommand(CustomCommands.SET_SLEEP_MODE, Bundle.EMPTY), args)
        _state.value = _state.value.copy(
            sleepMode = mode,
            sleepRemainingMs = when (mode) {
                is SleepTimerMode.AfterDuration -> mode.durationMs
                else -> null
            },
            sleepTargetChapterId = when (mode) {
                is SleepTimerMode.EndOfChapter -> _state.value.chapterId
                else -> null
            },
        )
        sleepJob?.cancel()
        if (mode is SleepTimerMode.AfterDuration) {
            startSleepTicker(SystemClock.elapsedRealtime() + mode.durationMs)
        }
    }

    fun extendSleep(extraMs: Long = 15 * 60_000L) {
        val mode = _state.value.sleepMode
        if (mode is SleepTimerMode.AfterDuration) {
            val left = (_state.value.sleepRemainingMs ?: 0L) + extraMs
            setSleepMode(SleepTimerMode.AfterDuration(left))
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(lastError = null, errorCanSkip = false)
    }

    private fun startSleepTicker(endElapsedMs: Long) {
        sleepJob?.cancel()
        sleepJob = scope.launch {
            while (isActive) {
                val left = endElapsedMs - SystemClock.elapsedRealtime()
                if (left <= 0) {
                    _state.value = _state.value.copy(
                        sleepRemainingMs = null,
                        sleepMode = SleepTimerMode.Off,
                    )
                    break
                }
                _state.value = _state.value.copy(sleepRemainingMs = left)
                delay(1_000)
            }
        }
    }

    private suspend fun ensureConnected() {
        if (controller != null) return
        connect()
        repeat(50) {
            if (controller != null) return
            delay(50)
        }
        controllerFuture?.let { future ->
            try {
                val c = suspendCancellableCoroutine<MediaController> { cont ->
                    future.addListener({
                        try {
                            cont.resume(future.get())
                        } catch (e: Exception) {
                            cont.resumeWithException(e)
                        }
                    }, MoreExecutors.directExecutor())
                }
                if (controller == null) {
                    controller = c
                    c.addListener(listener)
                    syncFromController(c)
                    syncRuntimeState(c.sessionExtras)
                    _state.value = _state.value.copy(isConnected = true)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(lastError = e.message ?: "无法连接播放服务")
            }
        }
    }

    private fun syncFromController(c: MediaController) {
        _state.value = _state.value.copy(
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.coerceAtLeast(0L),
            speed = c.playbackParameters.speed,
        )
        updateFromMediaItem(c.currentMediaItem)
    }

    private fun syncRuntimeState(extras: Bundle) {
        val mode = when (extras.getString(PlaybackStateKeys.SLEEP_MODE, "off")) {
            "duration" -> {
                val endAt = extras.getLong(PlaybackStateKeys.SLEEP_END_ELAPSED_MS, -1L)
                val remaining = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                SleepTimerMode.AfterDuration(remaining)
            }
            "end_of_chapter" -> SleepTimerMode.EndOfChapter
            else -> SleepTimerMode.Off
        }
        val endAt = extras.getLong(PlaybackStateKeys.SLEEP_END_ELAPSED_MS, -1L)
        val target = extras.getLong(PlaybackStateKeys.SLEEP_TARGET_CHAPTER_ID, -1L).takeIf { it > 0L }
        _state.value = _state.value.copy(
            sleepMode = mode,
            sleepRemainingMs = if (mode is SleepTimerMode.AfterDuration) {
                (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            } else {
                null
            },
            sleepTargetChapterId = target,
        )
        sleepJob?.cancel()
        if (mode is SleepTimerMode.AfterDuration && endAt > SystemClock.elapsedRealtime()) {
            startSleepTicker(endAt)
        }
    }

    private fun updateFromMediaItem(item: MediaItem?) {
        if (item == null) return
        val extras = item.mediaMetadata.extras
        val bookId = extras?.getLong(KEY_BOOK_ID)?.takeIf { it != 0L }
            ?: item.mediaId.substringBefore('_').toLongOrNull()
        val chapterId = extras?.getLong(KEY_CHAPTER_ID)
            ?: item.mediaId.substringAfter('_', missingDelimiterValue = "").toLongOrNull()
        val index = extras?.getInt(KEY_CHAPTER_INDEX) ?: 0
        val count = extras?.getInt(KEY_CHAPTER_COUNT) ?: _state.value.chapterCount
        val usesBookSpeedOverride = extras?.getBoolean(KEY_BOOK_SPEED_OVERRIDE)
            ?: _state.value.usesBookSpeedOverride
        val skipIntroMs = extras?.getLong(KEY_SKIP_INTRO_MS) ?: _state.value.skipIntroMs
        val skipOutroMs = extras?.getLong(KEY_SKIP_OUTRO_MS) ?: _state.value.skipOutroMs
        val bookTotalMs = extras?.getLong(KEY_BOOK_TOTAL_MS) ?: _state.value.bookDurationMs
        val chapterStartOffsetMs = extras?.getLong(KEY_CHAPTER_START_OFFSET_MS) ?: 0L
        _state.value = _state.value.copy(
            bookDurationMs = bookTotalMs,
            bookId = bookId ?: _state.value.bookId,
            bookTitle = item.mediaMetadata.albumTitle?.toString() ?: _state.value.bookTitle,
            chapterId = chapterId,
            chapterTitle = item.mediaMetadata.title?.toString(),
            chapterIndex = index,
            chapterCount = count,
            usesBookSpeedOverride = usesBookSpeedOverride,
            skipIntroMs = skipIntroMs,
            skipOutroMs = skipOutroMs,
            coverPath = item.mediaMetadata.artworkUri?.let { uri ->
                // Restore plain filesystem paths from file: URIs so covers still resolve
                // after process death (SAF file: persist permission is not guaranteed).
                if (uri.scheme == "file") uri.path else uri.toString()
            } ?: _state.value.coverPath,
            durationMs = controller?.duration?.coerceAtLeast(0L) ?: _state.value.durationMs,
            positionMs = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L,
        )
        updateBookPosition(chapterStartOffsetMs)
    }

    /** True when the whole chapter payload is already in the offline cache. */
    fun isChapterFullyCached(book: Book, remoteItemId: String): Boolean {
        return try {
            val key = "fqnovel_${book.remoteAudioBookId}_$remoteItemId"
            cacheManager.isFullyCached(key)
        } catch (_: Exception) {
            false
        }
    }

    private fun updateBookPosition(chapterStartOffsetMs: Long? = null) {
        val c = controller ?: return
        val offset = chapterStartOffsetMs
            ?: c.currentMediaItem?.mediaMetadata?.extras?.getLong(KEY_CHAPTER_START_OFFSET_MS)
            ?: return
        _state.value = _state.value.copy(
            bookPositionMs = offset + c.currentPosition.coerceAtLeast(0L),
        )
    }

    private fun startProgressLoop() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (isActive) {
                val c = controller
                if (c != null) {
                    _state.value = _state.value.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0L),
                        durationMs = c.duration.coerceAtLeast(0L),
                        bufferedMs = c.bufferedPosition.coerceAtLeast(0L),
                        isPlaying = c.isPlaying,
                    )
                    updateBookPosition()
                }
                delay(500)
            }
        }
    }

    private fun stopProgressLoop() {
        positionJob?.cancel()
        positionJob = null
    }

    companion object {
        const val KEY_BOOK_ID = "book_id"
        const val KEY_CHAPTER_ID = "chapter_id"
        const val KEY_CHAPTER_INDEX = "chapter_index"
        const val KEY_CHAPTER_COUNT = "chapter_count"
        const val KEY_AUTO_PLAY_NEXT = "auto_play_next"
        const val KEY_BOOK_SPEED_OVERRIDE = "book_speed_override"
        const val KEY_SKIP_INTRO_MS = "skip_intro_ms"
        const val KEY_SKIP_OUTRO_MS = "skip_outro_ms"
        const val KEY_BOOK_TOTAL_MS = "book_total_ms"
        const val KEY_CHAPTER_START_OFFSET_MS = "chapter_start_offset_ms"

        /** Builds queue items with each chapter's linear start offset baked in. */
        fun buildQueueItems(book: Book, chapters: List<Chapter>): List<MediaItem> {
            var acc = 0L
            return chapters.sortedBy { it.index }.map { ch ->
                val item = ch.toMediaItem(book, chapters.size, acc)
                acc += ch.durationMs.coerceAtLeast(0L)
                item
            }
        }
    }
}

private suspend fun ListenableFuture<androidx.media3.session.SessionResult>.awaitResult(): androidx.media3.session.SessionResult =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            MoreExecutors.directExecutor(),
        )
        continuation.invokeOnCancellation { cancel(true) }
    }

@androidx.annotation.OptIn(UnstableApi::class)
fun Chapter.toMediaItem(
    book: Book,
    chapterCount: Int = 0,
    chapterStartOffsetMs: Long = 0L,
): MediaItem {
    val clip = chapterClip(
        durationMs = durationMs,
        skipIntroMs = book.skipIntroMs,
        skipOutroMs = book.skipOutroMs,
        clipStartMs = clipStartMs,
        clipEndMs = clipEndMs,
    )
    val extras = bundleOf(
        PlayerController.KEY_BOOK_ID to book.id,
        PlayerController.KEY_CHAPTER_ID to id,
        PlayerController.KEY_CHAPTER_INDEX to index,
        PlayerController.KEY_CHAPTER_COUNT to chapterCount,
        PlayerController.KEY_AUTO_PLAY_NEXT to book.autoPlayNext,
        PlayerController.KEY_BOOK_SPEED_OVERRIDE to (book.playbackSpeed != null),
        PlayerController.KEY_SKIP_INTRO_MS to book.skipIntroMs,
        PlayerController.KEY_SKIP_OUTRO_MS to book.skipOutroMs,
        PlayerController.KEY_BOOK_TOTAL_MS to book.totalDurationMs,
        PlayerController.KEY_CHAPTER_START_OFFSET_MS to chapterStartOffsetMs,
    )
    val metadata = MediaMetadata.Builder()
        .setTitle(displayTitle)
        .setAlbumTitle(book.title)
        .setArtist(book.author ?: book.title)
        .setArtworkUri(book.coverPath?.let { path ->
            if (path.startsWith("content:") || path.startsWith("file:") || path.startsWith("http")) {
                android.net.Uri.parse(path)
            } else {
                android.net.Uri.fromFile(java.io.File(path))
            }
        })
        .setExtras(extras)
        .build()
    val clipping = MediaItem.ClippingConfiguration.Builder()
        .setStartPositionMs(clip.startMs)
        .apply { clip.endMs?.let(::setEndPositionMs) }
        .build()
    val isRemoteStream = book.isRemote &&
        !book.remoteAudioBookId.isNullOrBlank() && !remoteItemId.isNullOrBlank()
    val mediaUri = if (isRemoteStream) {
        val tone = book.remoteToneId?.takeIf { it.isNotBlank() } ?: "0"
        "https://fq.logix.cc.cd/audio/stream/${book.remoteAudioBookId}/${remoteItemId}?toneId=$tone"
    } else {
        uri
    }
    return MediaItem.Builder()
        .setMediaId("${book.id}_$id")
        .setUri(mediaUri)
        .apply {
            // Stable cache key per (audiobook, chapter) so playback and prefetch
            // share one cache entry regardless of the toneId query param.
            if (isRemoteStream) {
                setCustomCacheKey("fqnovel_${book.remoteAudioBookId}_$remoteItemId")
            }
        }
        .setMediaMetadata(metadata)
        .setClippingConfiguration(clipping)
        .build()
}
