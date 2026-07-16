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
import kotlinx.coroutines.withContext
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var bookRepository: BookRepository

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepJob: Job? = null
    private var progressJob: Job? = null

    /** Last known progress snapshot taken on the main/player thread. */
    @Volatile private var lastBookId: Long? = null
    @Volatile private var lastChapterId: Long? = null
    @Volatile private var lastPositionMs: Long = 0L
    @Volatile private var previousChapterId: Long? = null
    @Volatile private var previousBookId: Long? = null
    @Volatile private var previousPositionMs: Long = 0L
    @Volatile private var previousDurationMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
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
                val sessionCommands = SessionCommands.Builder()
                    .add(SessionCommand(CustomCommands.SEEK_BACK_15, Bundle.EMPTY))
                    .add(SessionCommand(CustomCommands.SEEK_FWD_15, Bundle.EMPTY))
                    .add(SessionCommand(CustomCommands.SEEK_BACK_30, Bundle.EMPTY))
                    .add(SessionCommand(CustomCommands.SEEK_FWD_30, Bundle.EMPTY))
                    .add(SessionCommand(CustomCommands.SET_SPEED, Bundle.EMPTY))
                    .add(SessionCommand(CustomCommands.SET_SLEEP, Bundle.EMPTY))
                    .build()
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
                val p = session.player
                when (customCommand.customAction) {
                    CustomCommands.SEEK_BACK_15 -> p.seekTo((p.currentPosition - SeekOffsets.SHORT_MS).coerceAtLeast(0))
                    CustomCommands.SEEK_FWD_15 -> seekFwd(p, SeekOffsets.SHORT_MS)
                    CustomCommands.SEEK_BACK_30 -> p.seekTo((p.currentPosition - SeekOffsets.LONG_MS).coerceAtLeast(0))
                    CustomCommands.SEEK_FWD_30 -> seekFwd(p, SeekOffsets.LONG_MS)
                    CustomCommands.SET_SPEED -> {
                        val speed = args.getFloat("speed", 1f)
                        p.setPlaybackSpeed(speed)
                    }
                    CustomCommands.SET_SLEEP -> {
                        val minutes = args.getInt("minutes", 0)
                        scheduleSleep(p, minutes)
                    }
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
            if (isActive) {
                player.pause()
            }
        }
    }

    private fun startProgressPersistence(player: Player) {
        progressJob?.cancel()
        // Snapshot on main (player) thread, then persist on IO.
        progressJob = serviceScope.launch {
            while (isActive) {
                delay(10_000)
                snapshotAndPersist(player, reason = PersistReason.PERIODIC)
            }
        }
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    serviceScope.launch {
                        snapshotAndPersist(player, reason = PersistReason.PAUSE)
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                serviceScope.launch {
                    // On transition, prefer previous chapter snapshot if available
                    snapshotAndPersist(
                        player,
                        reason = if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                            PersistReason.AUTO_NEXT
                        } else {
                            PersistReason.TRANSITION
                        },
                    )
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

    private enum class PersistReason { PERIODIC, PAUSE, TRANSITION, AUTO_NEXT }

    private suspend fun snapshotAndPersist(player: Player, reason: PersistReason) {
        // Must touch Player on its application looper (main for ExoPlayer by default).
        val snapshot = withContext(Dispatchers.Main.immediate) {
            val item = player.currentMediaItem
            val pos = player.currentPosition.coerceAtLeast(0L)
            val dur = player.duration.coerceAtLeast(0L)
            val ids = parseIds(item)
            // Keep previous for transition save
            val prevBook = lastBookId
            val prevChapter = lastChapterId
            val prevPos = lastPositionMs
            val prevDur = previousDurationMs

            if (ids != null) {
                if (lastChapterId != null && lastChapterId != ids.second) {
                    previousBookId = lastBookId
                    previousChapterId = lastChapterId
                    previousPositionMs = lastPositionMs
                    previousDurationMs = previousDurationMs
                }
                lastBookId = ids.first
                lastChapterId = ids.second
                lastPositionMs = pos
                previousDurationMs = dur
            }

            Snapshot(
                currentBookId = ids?.first,
                currentChapterId = ids?.second,
                currentPositionMs = pos,
                currentDurationMs = dur,
                previousBookId = prevBook,
                previousChapterId = prevChapter,
                previousPositionMs = prevPos,
                previousDurationMs = if (prevDur > 0) prevDur else dur,
            )
        }

        withContext(Dispatchers.IO) {
            try {
                when (reason) {
                    PersistReason.AUTO_NEXT -> {
                        // Save previous chapter as completed if we have it
                        val b = snapshot.previousBookId
                        val ch = snapshot.previousChapterId
                        if (b != null && ch != null && ch != snapshot.currentChapterId) {
                            val completedPos = if (snapshot.previousDurationMs > 0L) {
                                snapshot.previousDurationMs
                            } else {
                                snapshot.previousPositionMs
                            }
                            bookRepository.saveProgress(b, ch, completedPos)
                        } else {
                            persistSnapshotCurrent(snapshot)
                        }
                    }
                    PersistReason.TRANSITION -> {
                        val b = snapshot.previousBookId
                        val ch = snapshot.previousChapterId
                        if (b != null && ch != null && ch != snapshot.currentChapterId) {
                            bookRepository.saveProgress(b, ch, snapshot.previousPositionMs)
                        } else {
                            persistSnapshotCurrent(snapshot)
                        }
                    }
                    else -> persistSnapshotCurrent(snapshot)
                }
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun persistSnapshotCurrent(snapshot: Snapshot) {
        val b = snapshot.currentBookId ?: return
        val ch = snapshot.currentChapterId ?: return
        bookRepository.saveProgress(b, ch, snapshot.currentPositionMs)
    }

    private data class Snapshot(
        val currentBookId: Long?,
        val currentChapterId: Long?,
        val currentPositionMs: Long,
        val currentDurationMs: Long,
        val previousBookId: Long?,
        val previousChapterId: Long?,
        val previousPositionMs: Long,
        val previousDurationMs: Long,
    )

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
            stopSelf()
        }
    }

    override fun onDestroy() {
        progressJob?.cancel()
        sleepJob?.cancel()
        // Final persist of last snapshot on main then IO is best-effort
        val b = lastBookId
        val ch = lastChapterId
        val pos = lastPositionMs
        if (b != null && ch != null) {
            // fire-and-forget blocking is avoided; try launch before cancel
            serviceScope.launch(Dispatchers.IO) {
                try {
                    bookRepository.saveProgress(b, ch, pos)
                } catch (_: Exception) {
                }
            }
        }
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
