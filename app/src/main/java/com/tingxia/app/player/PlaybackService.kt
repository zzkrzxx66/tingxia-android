package com.tingxia.app.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepJob: Job? = null
    private var tickerJob: Job? = null
    private var progressWriter: ProgressWriter? = null

    private var lastBookId: Long? = null
    private var lastChapterId: Long? = null
    private var lastPositionMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        progressWriter = ProgressWriter(
            scope = serviceScope,
            save = { bookId, chapterId, positionMs ->
                bookRepository.saveProgress(bookId, chapterId, positionMs)
            },
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
                    serviceScope.launch { captureAndEnqueueCurrent(player) }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                serviceScope.launch { handleEnter(player, mediaItem) }
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
            closeWriterWithFinal(player)
            stopSelf()
        }
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        sleepJob?.cancel()
        closeWriterWithFinal(mediaSession?.player)
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun closeWriterWithFinal(player: Player?) {
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
        try {
            val saved = runBlocking {
                // Final write goes through the queue AFTER any pending items → no stale overwrite.
                writer.closeWithFinal(b, ch, pos, timeoutMs = 1_500)
            }
            if (!saved && b != null && ch != null) {
                Log.w(TAG, "Final progress save did not complete for book=$b chapter=$ch")
            }
        } catch (_: Exception) {
            writer.cancel()
            Log.w(TAG, "Final progress save failed during service teardown")
        }
    }

    private companion object {
        const val TAG = "TingXiaPlayback"
    }
}
