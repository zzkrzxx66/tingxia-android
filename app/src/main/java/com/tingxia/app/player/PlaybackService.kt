package com.tingxia.app.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.tingxia.app.MainActivity
import com.tingxia.app.data.repo.BookRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Single **serialized** writer of playback progress.
 * All DB writes go through [progressChannel] so periodic/pause/transition cannot race.
 * UI/PlayerController must not call [BookRepository.saveProgress].
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var bookRepository: BookRepository

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepJob: Job? = null
    private var tickerJob: Job? = null
    private var writerJob: Job? = null

    // Snapshots always taken on the player/main looper.
    private var lastBookId: Long? = null
    private var lastChapterId: Long? = null
    private var lastPositionMs: Long = 0L
    private var lastDurationMs: Long = 0L

    /** Monotonic sequence so a late stale capture can still be ordered (writer is FIFO). */
    private var progressSeq: Long = 0L

    private data class ProgressWrite(
        val seq: Long,
        val bookId: Long,
        val chapterId: Long,
        val positionMs: Long,
    )

    private val progressChannel = Channel<ProgressWrite>(capacity = Channel.UNLIMITED)

    override fun onCreate() {
        super.onCreate()
        startProgressWriter()

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
                val isTrusted = controller.packageName == packageName
                val sessionCommands = SessionCommands.Builder().apply {
                    if (isTrusted) {
                        add(SessionCommand(CustomCommands.SEEK_BACK_15, Bundle.EMPTY))
                        add(SessionCommand(CustomCommands.SEEK_FWD_15, Bundle.EMPTY))
                        add(SessionCommand(CustomCommands.SEEK_BACK_30, Bundle.EMPTY))
                        add(SessionCommand(CustomCommands.SEEK_FWD_30, Bundle.EMPTY))
                        add(SessionCommand(CustomCommands.SET_SPEED, Bundle.EMPTY))
                        add(SessionCommand(CustomCommands.SET_SLEEP, Bundle.EMPTY))
                    }
                }.build()
                val playerCommands = Player.Commands.Builder()
                    .addAllCommands()
                    .build()
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
                    CustomCommands.SET_SPEED -> p.setPlaybackSpeed(args.getFloat("speed", 1f))
                    CustomCommands.SET_SLEEP -> scheduleSleep(p, args.getInt("minutes", 0))
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setSessionActivity(sessionActivity)
            .setId("tingxia_playback")
            .build()

        startProgressPersistence(player)
    }

    private fun startProgressWriter() {
        writerJob?.cancel()
        writerJob = serviceScope.launch(Dispatchers.IO) {
            for (write in progressChannel) {
                try {
                    bookRepository.saveProgress(write.bookId, write.chapterId, write.positionMs)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun enqueueProgress(bookId: Long, chapterId: Long, positionMs: Long) {
        val seq = ++progressSeq
        // trySend never blocks; channel is unlimited.
        progressChannel.trySend(
            ProgressWrite(seq, bookId, chapterId, positionMs.coerceAtLeast(0L)),
        )
    }

    private fun seekFwd(player: Player, delta: Long) {
        val dur = player.duration
        val target = player.currentPosition + delta
        if (dur > 0) player.seekTo(target.coerceAtMost(dur)) else player.seekTo(target)
    }

    private fun scheduleSleep(player: Player, minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) return
        sleepJob = serviceScope.launch {
            delay(minutes * 60_000L)
            if (isActive) player.pause()
        }
    }

    private fun startProgressPersistence(player: Player) {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(10_000)
                captureAndEnqueueCurrent(player)
            }
        }
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    // Capture synchronously on player thread via main dispatcher launch.
                    serviceScope.launch { captureAndEnqueueCurrent(player) }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                serviceScope.launch {
                    // Model only keeps one current pointer per book: write the entered chapter.
                    // Leave-chapter completion is not stored separately.
                    handleEnter(player, mediaItem)
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
            }
        })
    }

    private suspend fun handleEnter(player: Player, newItem: MediaItem?) {
        val enter = withContext(Dispatchers.Main.immediate) {
            val newIds = parseIds(newItem)
            val newPos = player.currentPosition.coerceAtLeast(0L)
            val newDur = player.duration.coerceAtLeast(0L)
            if (newIds != null) {
                lastBookId = newIds.first
                lastChapterId = newIds.second
                lastPositionMs = newPos
                lastDurationMs = newDur
            }
            ProgressLeavePolicy.progressOnEnter(
                bookId = newIds?.first,
                chapterId = newIds?.second,
                positionMs = newPos,
            )
        }
        enter?.let { enqueueProgress(it.bookId, it.chapterId, it.positionMs) }
    }

    private suspend fun captureAndEnqueueCurrent(player: Player) {
        val snap = withContext(Dispatchers.Main.immediate) {
            val ids = parseIds(player.currentMediaItem)
            val pos = player.currentPosition.coerceAtLeast(0L)
            val dur = player.duration.coerceAtLeast(0L)
            if (ids != null) {
                lastBookId = ids.first
                lastChapterId = ids.second
                lastPositionMs = pos
                lastDurationMs = dur
            }
            ids?.let { Triple(it.first, it.second, pos) }
        } ?: return
        enqueueProgress(snap.first, snap.second, snap.third)
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            flushProgressBlocking(player)
            stopSelf()
        }
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        sleepJob?.cancel()
        // Capture last position then close channel so writer drains remaining items.
        flushProgressBlocking(mediaSession?.player)
        progressChannel.close()
        // Wait briefly for writer to drain (avoid long main-thread stall).
        try {
            runBlocking {
                withTimeoutOrNull(500) {
                    writerJob?.join()
                }
            }
        } catch (_: Exception) {
        }
        writerJob?.cancel()
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun flushProgressBlocking(player: Player?) {
        try {
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
            if (b != null && ch != null) {
                // Direct write for teardown path (channel may be closing); still single process.
                runBlocking {
                    withTimeoutOrNull(800) {
                        withContext(Dispatchers.IO) {
                            bookRepository.saveProgress(b, ch, pos)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}
