package com.tingxia.app.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.tingxia.app.MainActivity
import com.tingxia.app.R
import com.tingxia.app.TingXiaApp
import com.tingxia.app.data.repo.BookRepository
import com.tingxia.app.data.repo.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Single serialized progress writer via [ProgressWriter].
 * UI/PlayerController must not call [BookRepository.saveProgress].
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var bookRepository: BookRepository
    @Inject lateinit var preferences: UserPreferencesRepository

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sleepJob: Job? = null
    private var tickerJob: Job? = null
    private var progressWriter: ProgressWriter? = null

    private var sleepMode: SleepTimerMode = SleepTimerMode.Off
    private var sleepTargetChapterId: Long? = null
    private var sleepEndElapsedMs: Long? = null
    private var originalVolume: Float? = null
    private var fadeJob: Job? = null
    private var currentAutoPlayNext: Boolean = true

    private var lastBookId: Long? = null
    private var lastChapterId: Long? = null
    private var lastPositionMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(TingXiaApp.PLAYBACK_CHANNEL_ID)
                .setChannelName(R.string.notification_channel_playback)
                .build(),
        )
        progressWriter = ProgressWriter(
            scope = progressScope,
            save = { bookId, chapterId, positionMs ->
                bookRepository.saveProgress(bookId, chapterId, positionMs)
            },
            onWriteFailure = { error -> Log.w(TAG, "Progress write failed", error) },
        )

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult {
                val isOwnApp = controller.packageName == packageName
                val isTrustedSystemController = controller.isTrusted ||
                    session.isMediaNotificationController(controller) ||
                    session.isAutomotiveController(controller) ||
                    session.isAutoCompanionController(controller)
                if (!isOwnApp && !isTrustedSystemController) {
                    return MediaSession.ConnectionResult.reject()
                }
                val sessionCommands = SessionCommands.Builder().apply {
                    if (isOwnApp) {
                        add(SessionCommand(CustomCommands.SEEK_BACK_15, Bundle.EMPTY))
                        add(SessionCommand(CustomCommands.SEEK_FWD_15, Bundle.EMPTY))
                        add(SessionCommand(CustomCommands.SEEK_BACK_30, Bundle.EMPTY))
                        add(SessionCommand(CustomCommands.SEEK_FWD_30, Bundle.EMPTY))
                        add(SessionCommand(CustomCommands.SET_SLEEP_MODE, Bundle.EMPTY))
                        add(SessionCommand(CustomCommands.SET_BOOK_SPEED, Bundle.EMPTY))
                        add(SessionCommand(CustomCommands.SET_AUTO_PLAY_NEXT, Bundle.EMPTY))
                        add(SessionCommand(CustomCommands.PREPARE_LIBRARY_MUTATION, Bundle.EMPTY))
                    }
                }.build()
                val playerCommands = if (isOwnApp) {
                    Player.Commands.Builder().addAllCommands().build()
                } else {
                    // External trusted surfaces may control transport, but must not
                    // replace the queue or inject arbitrary media items.
                    Player.Commands.Builder()
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .add(Player.COMMAND_PREPARE)
                        .add(Player.COMMAND_STOP)
                        .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_BACK)
                        .add(Player.COMMAND_SEEK_FORWARD)
                        .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                        .add(Player.COMMAND_GET_TIMELINE)
                        .add(Player.COMMAND_GET_METADATA)
                        .build()
                }
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .setAvailablePlayerCommands(playerCommands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle,
            ): ListenableFuture<SessionResult> {
                if (controller.packageName != packageName) {
                    return Futures.immediateFuture(
                        SessionResult(SessionError.ERROR_PERMISSION_DENIED),
                    )
                }
                val p = session.player
                when (customCommand.customAction) {
                    CustomCommands.SEEK_BACK_15 ->
                        p.seekTo((p.currentPosition - SeekOffsets.SHORT_MS).coerceAtLeast(0))
                    CustomCommands.SEEK_FWD_15 -> seekFwd(p, SeekOffsets.SHORT_MS)
                    CustomCommands.SEEK_BACK_30 ->
                        p.seekTo((p.currentPosition - SeekOffsets.LONG_MS).coerceAtLeast(0))
                    CustomCommands.SEEK_FWD_30 -> seekFwd(p, SeekOffsets.LONG_MS)
                    CustomCommands.SET_BOOK_SPEED -> {
                        val speed = args.getFloat("speed", 1f)
                        if (speed in PlaybackSpeeds.ALL) p.setPlaybackSpeed(speed)
                    }
                    CustomCommands.SET_AUTO_PLAY_NEXT -> {
                        val requestedBookId = args.getLong("bookId", -1L)
                        val enabled = args.getBoolean("enabled", true)
                        if (requestedBookId == lastBookId) {
                            currentAutoPlayNext = enabled
                            updatePauseAtEnd(p)
                        }
                    }
                    CustomCommands.SET_SLEEP_MODE -> applySleepMode(p, args)
                    CustomCommands.PREPARE_LIBRARY_MUTATION -> {
                        return prepareLibraryMutation(p, args)
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            override fun onPlaybackResumption(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                serviceScope.launch {
                    try {
                        val resume = withContext(Dispatchers.IO) {
                            val book = bookRepository.getRecentBook()
                                ?: error("没有可恢复的播放记录")
                            if (!bookRepository.checkBookAccess(book.id)) {
                                error("最近播放书籍的目录权限已失效")
                            }
                            val chapters = bookRepository.getChapters(book.id)
                            val plan = createPlaybackResumePlan(
                                book = book,
                                chapters = chapters,
                                defaultSpeed = preferences.defaultSpeed.first(),
                            ) ?: error("最近播放书籍没有可用章节")
                            Triple(book, chapters, plan)
                        }
                        val (book, chapters, plan) = resume
                        lastBookId = book.id
                        lastChapterId = chapters[plan.startIndex].id
                        lastPositionMs = plan.startPositionMs
                        currentAutoPlayNext = book.autoPlayNext
                        session.player.setPlaybackSpeed(plan.speed)
                        updatePauseAtEnd(session.player)
                        future.set(
                            MediaSession.MediaItemsWithStartPosition(
                                chapters.map { it.toMediaItem(book, chapters.size) },
                                plan.startIndex,
                                plan.startPositionMs,
                            ),
                        )
                    } catch (error: Exception) {
                        future.setException(error)
                    }
                }
                return future
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setSessionActivity(sessionActivity)
            .setId("tingxia_playback")
            .build()

        publishRuntimeState()

        startProgressPersistence(player)
    }

    private fun seekFwd(player: Player, delta: Long) {
        val dur = player.duration
        val target = player.currentPosition + delta
        if (dur > 0) player.seekTo(target.coerceAtMost(dur)) else player.seekTo(target)
    }

    private fun startProgressPersistence(player: Player) {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(5_000)
                captureAndEnqueueCurrent(player)
            }
        }
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    serviceScope.launch { captureAndEnqueueCurrent(player) }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val completedChapterId = lastChapterId
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && completedChapterId != null) {
                    serviceScope.launch(Dispatchers.IO) {
                        bookRepository.setChapterCompleted(completedChapterId, true)
                    }
                }
                currentAutoPlayNext = autoPlayNext(mediaItem)
                updatePauseAtEnd(player)
                if (sleepMode is SleepTimerMode.EndOfChapter) {
                    sleepTargetChapterId = parseIds(mediaItem)?.second ?: sleepTargetChapterId
                    publishRuntimeState()
                }
                serviceScope.launch { handleEnter(player, mediaItem) }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                serviceScope.launch { captureAndEnqueueCurrent(player) }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady &&
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
                ) {
                    lastChapterId?.let { completedChapterId ->
                        serviceScope.launch(Dispatchers.IO) {
                            bookRepository.setChapterCompleted(completedChapterId, true)
                        }
                    }
                    if (sleepMode is SleepTimerMode.EndOfChapter) clearSleep(restoreVolume = true)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    lastChapterId?.let { completedChapterId ->
                        serviceScope.launch(Dispatchers.IO) {
                            bookRepository.setChapterCompleted(completedChapterId, true)
                        }
                    }
                    if (sleepMode is SleepTimerMode.EndOfChapter) clearSleep(restoreVolume = true)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val bookId = lastBookId ?: return
                val isPermission =
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                        error.cause is SecurityException
                if (isPermission) {
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            bookRepository.markNeedsReauth(bookId, true)
                        } catch (_: Exception) {
                        }
                    }
                }
                serviceScope.launch {
                    val policy = withContext(Dispatchers.IO) {
                        preferences.playbackErrorPolicy.first()
                    }
                    if (
                        shouldSkipPlaybackError(
                            policy = policy,
                            isPermissionError = isPermission,
                            hasNextChapter = player.hasNextMediaItem(),
                        )
                    ) {
                        player.seekToNextMediaItem()
                        player.prepare()
                        player.play()
                    }
                }
            }
        })
    }

    private suspend fun handleEnter(player: Player, newItem: MediaItem?) {
        val enter = withContext(Dispatchers.Main.immediate) {
            val newIds = parseIds(newItem)
            val newPos = player.currentPosition.coerceAtLeast(0L)
            if (newIds != null) {
                lastBookId = newIds.first
                lastChapterId = newIds.second
                lastPositionMs = newPos
            }
            if (newIds != null) Triple(newIds.first, newIds.second, newPos) else null
        }
        enter?.let { progressWriter?.enqueue(it.first, it.second, it.third) }
    }

    private suspend fun captureAndEnqueueCurrent(player: Player) {
        val snap = withContext(Dispatchers.Main.immediate) {
            val ids = parseIds(player.currentMediaItem)
            val pos = player.currentPosition.coerceAtLeast(0L)
            if (ids != null) {
                lastBookId = ids.first
                lastChapterId = ids.second
                lastPositionMs = pos
            }
            ids?.let { Triple(it.first, it.second, pos) }
        } ?: return
        progressWriter?.enqueue(snap.first, snap.second, snap.third)
    }


    private fun applySleepMode(player: Player, args: Bundle) {
        when (args.getString("mode")) {
            "off" -> clearSleep(restoreVolume = true)
            "end_of_chapter" -> {
                fadeJob?.cancel()
                sleepJob?.cancel()
                restoreVolumeIfNeeded(player)
                sleepMode = SleepTimerMode.EndOfChapter
                sleepEndElapsedMs = null
                val ch = args.getLong("chapterId", -1L).takeIf { it > 0 } ?: lastChapterId
                sleepTargetChapterId = ch
                updatePauseAtEnd(player)
                publishRuntimeState()
            }
            "duration" -> {
                val durationMs = args.getLong("durationMs", 0L)
                scheduleSleepMs(player, durationMs)
            }
            else -> clearSleep(restoreVolume = true)
        }
    }

    private fun scheduleSleepMs(player: Player, durationMs: Long) {
        sleepJob?.cancel()
        fadeJob?.cancel()
        restoreVolumeIfNeeded(player)
        if (durationMs <= 0L) {
            clearSleep(restoreVolume = true)
            return
        }
        sleepMode = SleepTimerMode.AfterDuration(durationMs)
        sleepTargetChapterId = null
        sleepEndElapsedMs = SystemClock.elapsedRealtime() + durationMs
        updatePauseAtEnd(player)
        publishRuntimeState()
        val fadeStart = (durationMs - 30_000L).coerceAtLeast(0L)
        sleepJob = serviceScope.launch {
            if (fadeStart > 0) delay(fadeStart)
            if (!isActive) return@launch
            if (sleepMode !is SleepTimerMode.AfterDuration) return@launch
            startFadeOut(player)
            val remainingFade = minOf(30_000L, durationMs)
            delay(remainingFade)
            if (isActive && sleepMode is SleepTimerMode.AfterDuration) {
                player.pause()
                clearSleep(restoreVolume = true)
            }
        }
    }

    private fun startFadeOut(player: Player) {
        fadeJob?.cancel()
        if (originalVolume == null) {
            originalVolume = player.volume
        }
        val startVol = originalVolume ?: player.volume
        fadeJob = serviceScope.launch {
            val steps = 30
            repeat(steps) { i ->
                if (!isActive) return@launch
                val fraction = 1f - ((i + 1).toFloat() / steps)
                player.volume = (startVol * fraction).coerceAtLeast(0f)
                delay(1_000)
            }
        }
    }

    private fun restoreVolumeIfNeeded(player: Player) {
        originalVolume?.let {
            player.volume = it
            originalVolume = null
        }
        fadeJob?.cancel()
        fadeJob = null
    }

    private fun clearSleep(restoreVolume: Boolean) {
        sleepJob?.cancel()
        fadeJob?.cancel()
        sleepJob = null
        fadeJob = null
        sleepMode = SleepTimerMode.Off
        sleepTargetChapterId = null
        sleepEndElapsedMs = null
        if (restoreVolume) {
            mediaSession?.player?.let { restoreVolumeIfNeeded(it) }
        }
        mediaSession?.player?.let { updatePauseAtEnd(it) }
        publishRuntimeState()
    }

    private fun updatePauseAtEnd(player: Player) {
        (player as? ExoPlayer)?.setPauseAtEndOfMediaItems(
            sleepMode is SleepTimerMode.EndOfChapter || !currentAutoPlayNext,
        )
    }

    private fun publishRuntimeState() {
        val mode = when (sleepMode) {
            SleepTimerMode.Off -> "off"
            SleepTimerMode.EndOfChapter -> "end_of_chapter"
            is SleepTimerMode.AfterDuration -> "duration"
        }
        mediaSession?.setSessionExtras(
            Bundle().apply {
                putString(PlaybackStateKeys.SLEEP_MODE, mode)
                putLong(PlaybackStateKeys.SLEEP_END_ELAPSED_MS, sleepEndElapsedMs ?: -1L)
                putLong(PlaybackStateKeys.SLEEP_TARGET_CHAPTER_ID, sleepTargetChapterId ?: -1L)
            },
        )
    }

    private fun prepareLibraryMutation(
        player: Player,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        val future = SettableFuture.create<SessionResult>()
        val requestedBookId = args.getLong(PlaybackStateKeys.MUTATION_BOOK_ID, -1L)
        val clearPlaylist = args.getBoolean(PlaybackStateKeys.MUTATION_CLEAR_PLAYLIST, false)
        val ids = parseIds(player.currentMediaItem)
        val wasActive = ids?.first == requestedBookId
        val wasPlaying = wasActive && player.isPlaying
        val chapterId = if (wasActive) ids?.second else null
        val positionMs = if (wasActive) player.currentPosition.coerceAtLeast(0L) else 0L
        if (wasActive) player.pause()
        serviceScope.launch {
            val saved = if (wasActive) {
                progressWriter?.flushWithCurrent(requestedBookId, chapterId, positionMs) ?: false
            } else {
                true
            }
            if (saved && wasActive && clearPlaylist) {
                player.clearMediaItems()
                lastBookId = null
                lastChapterId = null
                lastPositionMs = 0L
                clearSleep(restoreVolume = true)
            }
            val resultExtras = Bundle().apply {
                putBoolean(PlaybackStateKeys.MUTATION_WAS_ACTIVE, wasActive)
                putBoolean(PlaybackStateKeys.MUTATION_WAS_PLAYING, wasPlaying)
                putLong(PlaybackStateKeys.MUTATION_CHAPTER_ID, chapterId ?: -1L)
                putLong(PlaybackStateKeys.MUTATION_POSITION_MS, positionMs)
            }
            if (!saved && wasPlaying) player.play()
            future.set(
                if (saved) {
                    SessionResult(SessionResult.RESULT_SUCCESS, resultExtras)
                } else {
                    SessionResult(
                        SessionError(SessionError.ERROR_IO, "无法保存当前播放进度"),
                        resultExtras,
                    )
                },
            )
        }
        return future
    }

    private fun parseIds(item: MediaItem?): Pair<Long, Long>? {
        if (item == null) return null
        val extras = item.mediaMetadata.extras
        val bookId = extras?.getLong(PlayerController.KEY_BOOK_ID)
            ?: item.mediaId.substringBefore('_').toLongOrNull()
            ?: return null
        val chapterId = extras?.getLong(PlayerController.KEY_CHAPTER_ID)
            ?: item.mediaId.substringAfter('_').toLongOrNull()
            ?: return null
        return bookId to chapterId
    }

    private fun autoPlayNext(item: MediaItem?): Boolean =
        item?.mediaMetadata?.extras?.getBoolean(PlayerController.KEY_AUTO_PLAY_NEXT, true) ?: true

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            closeWriterWithFinal(player)
            stopSelf()
        }
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        sleepJob?.cancel()
        fadeJob?.cancel()
        closeWriterWithFinalAsync(mediaSession?.player)
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun closeWriterWithFinal(player: Player?) {
        closeWriterWithFinalAsync(player)
    }

    private fun closeWriterWithFinalAsync(player: Player?) {
        // Capture latest ids on current thread (player looper or last snapshot).
        val b: Long?
        val ch: Long?
        val pos: Long
        if (player != null) {
            val ids = parseIds(player.currentMediaItem)
            b = ids?.first ?: lastBookId
            ch = ids?.second ?: lastChapterId
            pos = if (ids != null) player.currentPosition.coerceAtLeast(0L) else lastPositionMs
        } else {
            b = lastBookId
            ch = lastChapterId
            pos = lastPositionMs
        }
        val writer = progressWriter
        progressWriter = null
        if (writer == null) return
        progressScope.launch {
            try {
                // The writer has its own IO lifecycle, so service teardown never blocks main.
                val saved = writer.closeWithFinal(b, ch, pos, timeoutMs = 1_500)
                if (!saved && b != null && ch != null) {
                    Log.w(TAG, "Final progress save did not complete for book=$b chapter=$ch")
                }
            } catch (_: Exception) {
                writer.cancel()
                Log.w(TAG, "Final progress save failed during service teardown")
            } finally {
                progressScope.cancel()
            }
        }
    }

    private companion object {
        const val TAG = "TingXiaPlayback"
    }
}
