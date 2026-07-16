package com.tingxia.app.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
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
    val sleepRemainingMs: Long? = null,
    val coverPath: String? = null,
    val needsReauth: Boolean = false,
)

/**
 * App-facing façade over Media3 [MediaController].
 * UI / ViewModels talk only to this, never hold ExoPlayer directly.
 */
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
    private var progressSaveJob: Job? = null
    private var lastSavedPosition = -1L
    private var lastSavedChapterId = -1L

    /** Snapshot of the chapter being left, for correct progress flush on transition. */
    private var pendingLeaveBookId: Long? = null
    private var pendingLeaveChapterId: Long? = null
    private var pendingLeavePositionMs: Long = 0L

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
            if (isPlaying) {
                startProgressLoop()
            } else {
                stopProgressLoop()
                flushProgress()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Capture outgoing chapter progress BEFORE updating state to the new item.
            val prev = _state.value
            val leaveBookId = prev.bookId
            val leaveChapterId = prev.chapterId
            val leavePosition = prev.positionMs
            val leaveDuration = prev.durationMs

            updateFromMediaItem(mediaItem)

            if (
                leaveBookId != null &&
                leaveChapterId != null &&
                leaveChapterId != _state.value.chapterId
            ) {
                // When auto-advancing, old chapter is effectively finished.
                val positionToSave = when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ->
                        if (leaveDuration > 0L) leaveDuration else leavePosition
                    else -> leavePosition
                }
                saveProgressNow(leaveBookId, leaveChapterId, positionToSave)
            } else {
                flushProgress()
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
            val bookId = _state.value.bookId ?: return
            val isPermission = error.errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                error.cause is SecurityException
            if (isPermission) {
                _state.value = _state.value.copy(needsReauth = true, isPlaying = false)
                scope.launch(Dispatchers.IO) {
                    bookRepository.markNeedsReauth(bookId, true)
                }
            }
        }
    }

    fun connect() {
        if (controller != null || controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture?.addListener({
            try {
                val c = controllerFuture?.get()
                controller = c
                c?.addListener(listener)
                _state.value = _state.value.copy(isConnected = c != null)
                if (c != null) {
                    syncFromController(c)
                    if (c.isPlaying) startProgressLoop()
                }
            } catch (_: Exception) {
                _state.value = _state.value.copy(isConnected = false)
            }
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        flushProgress()
        stopProgressLoop()
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
        _state.value = _state.value.copy(isConnected = false)
    }

    suspend fun playBook(bookId: Long, chapterId: Long? = null, positionMs: Long? = null) {
        ensureConnected()
        // Access check before loading media
        val accessible = bookRepository.checkBookAccess(bookId)
        if (!accessible) {
            _state.value = _state.value.copy(bookId = bookId, needsReauth = true)
            return
        }

        val book = bookRepository.getBook(bookId) ?: return
        val chapters = bookRepository.getChapters(bookId)
        if (chapters.isEmpty()) return

        val startChapterId = chapterId
            ?: book.currentChapterId
            ?: chapters.first().id
        val startIndex = chapters.indexOfFirst { it.id == startChapterId }.coerceAtLeast(0)
        val startPos = positionMs
            ?: if (chapterId == null || chapterId == book.currentChapterId) book.currentPositionMs else 0L

        val items = chapters.map { it.toMediaItem(book, chapters.size) }
        val c = controller ?: return
        // Flush previous book's progress before swapping playlist
        flushProgress()
        c.setMediaItems(items, startIndex, startPos.coerceAtLeast(0L))
        val speed = preferences.defaultSpeed.first()
        c.setPlaybackSpeed(speed)
        c.prepare()
        c.play()

        _state.value = _state.value.copy(
            bookId = book.id,
            bookTitle = book.title,
            coverPath = book.coverPath,
            chapterCount = chapters.size,
            speed = speed,
            needsReauth = false,
        )
        updateFromMediaItem(c.currentMediaItem)
    }

    fun play() = controller?.play()
    fun pause() {
        controller?.pause()
        flushProgress()
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
        // Snapshot current before transition
        val s = _state.value
        pendingLeaveBookId = s.bookId
        pendingLeaveChapterId = s.chapterId
        pendingLeavePositionMs = s.positionMs
        controller?.seekToNextMediaItem()
    }

    fun previousChapter() {
        val c = controller ?: return
        if (c.currentPosition > 3_000L) {
            c.seekTo(0L)
        } else {
            val s = _state.value
            pendingLeaveBookId = s.bookId
            pendingLeaveChapterId = s.chapterId
            pendingLeavePositionMs = s.positionMs
            c.seekToPreviousMediaItem()
        }
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
        scope.launch { preferences.setDefaultSpeed(speed) }
    }

    fun setSleepMinutes(minutes: Int) {
        val c = controller ?: return
        val args = bundleOf("minutes" to minutes)
        c.sendCustomCommand(
            SessionCommand(CustomCommands.SET_SLEEP, Bundle.EMPTY),
            args,
        )
        _state.value = _state.value.copy(
            sleepRemainingMs = if (minutes > 0) minutes * 60_000L else null
        )
        if (minutes > 0) startSleepTicker(minutes * 60_000L)
        else sleepJob?.cancel()
    }

    private var sleepJob: Job? = null
    private fun startSleepTicker(totalMs: Long) {
        sleepJob?.cancel()
        val endAt = System.currentTimeMillis() + totalMs
        sleepJob = scope.launch {
            while (isActive) {
                val left = endAt - System.currentTimeMillis()
                if (left <= 0) {
                    _state.value = _state.value.copy(sleepRemainingMs = null)
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
                controller = c
                c.addListener(listener)
                _state.value = _state.value.copy(isConnected = true)
            } catch (_: Exception) {
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

    private fun updateFromMediaItem(item: MediaItem?) {
        if (item == null) return
        val extras = item.mediaMetadata.extras
        val bookId = extras?.getLong(KEY_BOOK_ID)?.takeIf { it != 0L }
            ?: item.mediaId.substringBefore('_').toLongOrNull()
        val chapterId = extras?.getLong(KEY_CHAPTER_ID)
            ?: item.mediaId.substringAfter('_', missingDelimiterValue = "").toLongOrNull()
        val index = extras?.getInt(KEY_CHAPTER_INDEX) ?: 0
        val count = extras?.getInt(KEY_CHAPTER_COUNT) ?: _state.value.chapterCount
        _state.value = _state.value.copy(
            bookId = bookId ?: _state.value.bookId,
            bookTitle = item.mediaMetadata.albumTitle?.toString() ?: _state.value.bookTitle,
            chapterId = chapterId,
            chapterTitle = item.mediaMetadata.title?.toString(),
            chapterIndex = index,
            chapterCount = count,
            coverPath = item.mediaMetadata.artworkUri?.toString() ?: _state.value.coverPath,
            durationMs = controller?.duration?.coerceAtLeast(0L) ?: _state.value.durationMs,
            // Reset position for new item until player reports
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
                    maybeSaveProgress()
                }
                delay(500)
            }
        }
    }

    private fun stopProgressLoop() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun maybeSaveProgress() {
        val s = _state.value
        val bookId = s.bookId ?: return
        val chapterId = s.chapterId ?: return
        val pos = s.positionMs
        if (chapterId == lastSavedChapterId && kotlin.math.abs(pos - lastSavedPosition) < 5_000L) {
            return
        }
        if (progressSaveJob?.isActive == true) return
        progressSaveJob = scope.launch(Dispatchers.IO) {
            delay(8_000)
            val cur = _state.value
            // Only save if still on the same chapter we scheduled for
            val b = cur.bookId ?: return@launch
            val ch = cur.chapterId ?: return@launch
            if (ch != chapterId) return@launch
            bookRepository.saveProgress(b, ch, cur.positionMs)
            lastSavedPosition = cur.positionMs
            lastSavedChapterId = ch
        }
    }

    fun flushProgress() {
        val s = _state.value
        val bookId = s.bookId ?: return
        val chapterId = s.chapterId ?: return
        saveProgressNow(bookId, chapterId, s.positionMs)
    }

    private fun saveProgressNow(bookId: Long, chapterId: Long, positionMs: Long) {
        scope.launch(Dispatchers.IO) {
            bookRepository.saveProgress(bookId, chapterId, positionMs)
            lastSavedPosition = positionMs
            lastSavedChapterId = chapterId
        }
    }

    companion object {
        const val KEY_BOOK_ID = "book_id"
        const val KEY_CHAPTER_ID = "chapter_id"
        const val KEY_CHAPTER_INDEX = "chapter_index"
        const val KEY_CHAPTER_COUNT = "chapter_count"
    }
}

fun Chapter.toMediaItem(book: Book, chapterCount: Int = 0): MediaItem {
    val extras = bundleOf(
        PlayerController.KEY_BOOK_ID to book.id,
        PlayerController.KEY_CHAPTER_ID to id,
        PlayerController.KEY_CHAPTER_INDEX to index,
        PlayerController.KEY_CHAPTER_COUNT to chapterCount,
    )
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
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
