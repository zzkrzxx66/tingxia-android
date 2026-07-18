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
    val speed: Float = 1.0f,
    val usesBookSpeedOverride: Boolean = false,
    val sleepMode: SleepTimerMode = SleepTimerMode.Off,
    val sleepRemainingMs: Long? = null,
    val sleepTargetChapterId: Long? = null,
    val coverPath: String? = null,
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
        val duration = chapters[startIndex].durationMs
        val startPos = if (duration > 0L) requestedPos.coerceIn(0L, duration) else requestedPos.coerceAtLeast(0L)

        val items = chapters.map { it.toMediaItem(book, chapters.size) }
        val c = controller ?: return false
        c.setMediaItems(items, startIndex, startPos.coerceAtLeast(0L))
        val speed = book.playbackSpeed ?: preferences.defaultSpeed.first()
        c.setPlaybackSpeed(speed)
        // autoPlayNext: Media3 continues by default; service enforces EndOfChapter sleep.
        c.prepare()
        c.play()

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
        val ch = chapters.getOrNull(startIndex)
        val pos = if (ch != null && ch.durationMs > 0) positionMs.coerceIn(0, ch.durationMs) else positionMs
        c.setMediaItems(chapters.map { it.toMediaItem(book, chapters.size) }, startIndex, pos)
        c.prepare()
        if (wasPlaying) c.play() else c.pause()
        _state.value = _state.value.copy(chapterCount = chapters.size)
        updateFromMediaItem(c.currentMediaItem)
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

    suspend fun refreshQueueMetadata(bookId: Long) {
        if (_state.value.bookId != bookId) return
        val c = controller ?: return
        val book = bookRepository.getBook(bookId) ?: return
        val chapters = bookRepository.getChapters(bookId)
        if (chapters.isEmpty() || c.mediaItemCount != chapters.size) return
        c.replaceMediaItems(0, c.mediaItemCount, chapters.map { it.toMediaItem(book, chapters.size) })
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
        _state.value = _state.value.copy(
            bookId = bookId ?: _state.value.bookId,
            bookTitle = item.mediaMetadata.albumTitle?.toString() ?: _state.value.bookTitle,
            chapterId = chapterId,
            chapterTitle = item.mediaMetadata.title?.toString(),
            chapterIndex = index,
            chapterCount = count,
            usesBookSpeedOverride = usesBookSpeedOverride,
            coverPath = item.mediaMetadata.artworkUri?.toString() ?: _state.value.coverPath,
            durationMs = controller?.duration?.coerceAtLeast(0L) ?: _state.value.durationMs,
            positionMs = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L,
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

fun Chapter.toMediaItem(book: Book, chapterCount: Int = 0): MediaItem {
    val extras = bundleOf(
        PlayerController.KEY_BOOK_ID to book.id,
        PlayerController.KEY_CHAPTER_ID to id,
        PlayerController.KEY_CHAPTER_INDEX to index,
        PlayerController.KEY_CHAPTER_COUNT to chapterCount,
        PlayerController.KEY_AUTO_PLAY_NEXT to book.autoPlayNext,
        PlayerController.KEY_BOOK_SPEED_OVERRIDE to (book.playbackSpeed != null),
    )
    val metadata = MediaMetadata.Builder()
        .setTitle(displayTitle)
        .setAlbumTitle(book.title)
        .setArtist(book.author ?: book.title)
        .setArtworkUri(book.coverPath?.let { path ->
            if (path.startsWith("content:") || path.startsWith("file:")) {
                android.net.Uri.parse(path)
            } else {
                android.net.Uri.fromFile(java.io.File(path))
            }
        })
        .setExtras(extras)
        .build()
    return MediaItem.Builder()
        .setMediaId("${book.id}_$id")
        .setUri(uri)
        .setMediaMetadata(metadata)
        .build()
}
