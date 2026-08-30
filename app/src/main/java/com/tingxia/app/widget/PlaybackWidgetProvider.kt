package com.tingxia.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver.PendingResult
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.tingxia.app.player.PlaybackService

@OptIn(UnstableApi::class)
class PlaybackWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        PlaybackWidgetUpdater.renderCached(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        PlaybackWidgetUpdater.renderCached(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        // ACTION_APPWIDGET_UPDATE lands in onUpdate through super and is served from the cached
        // snapshot: it used to bind the session too, which spun up the whole playback service
        // just to redraw a strip on boot or on add.
        super.onReceive(context, intent)
        val command = when (intent.action) {
            ACTION_PREVIOUS -> WidgetCommand.Previous
            ACTION_TOGGLE -> WidgetCommand.Toggle
            ACTION_NEXT -> WidgetCommand.Next
            else -> return
        }
        WidgetSessionHolder.dispatch(context.applicationContext, command, goAsync())
    }

    companion object {
        const val ACTION_PREVIOUS = "com.tingxia.app.widget.PREVIOUS"
        const val ACTION_TOGGLE = "com.tingxia.app.widget.TOGGLE"
        const val ACTION_NEXT = "com.tingxia.app.widget.NEXT"
    }
}

/** What one widget button asks of the session. */
internal enum class WidgetCommand { Previous, Toggle, Next }

internal enum class WidgetPreviousAction { RestartChapter, PreviousChapter }

/** Far enough into a chapter, "previous" means "start this one over" — as it does in the app. */
internal fun widgetPreviousAction(positionMs: Long): WidgetPreviousAction =
    if (positionMs > RESTART_CHAPTER_THRESHOLD_MS) {
        WidgetPreviousAction.RestartChapter
    } else {
        WidgetPreviousAction.PreviousChapter
    }

/**
 * What is left to do once an empty session has restored the last book. `play()` is what asks the
 * service for that restore, so a toggle is already served by the time the queue arrives; a skip
 * still has to be applied on top of it.
 */
internal fun widgetCommandAfterResumption(command: WidgetCommand): WidgetCommand? =
    if (command == WidgetCommand.Toggle) null else command

internal const val RESTART_CHAPTER_THRESHOLD_MS = 3_000L

/**
 * Holds the widget's controller connection across the moment a tap wakes the app up.
 *
 * A tap can be the first thing to touch the app after its process was killed: there is no session
 * then, so the controller's `play()` makes the service restore the last book (see
 * `MediaSession.Callback.onPlaybackResumption`), which reads the database before it can hand a
 * queue back. The old code released the controller in a `finally` block right after issuing the
 * command, which unbound the only client of a service that nothing else was holding up — it was
 * destroyed before the restore finished, so the tap did nothing at all. Playback only becomes
 * self-sustaining once media3 posts its notification and calls `startForegroundService`, which
 * needs `playWhenReady` plus a non-idle player. So the connection is kept for a short while after
 * the broadcast ends, which also makes a second tap cheap.
 */
@UnstableApi
private object WidgetSessionHolder {
    private val handler = Handler(Looper.getMainLooper())
    private var future: ListenableFuture<MediaController>? = null
    private val releaseRunnable = Runnable { release() }

    fun dispatch(
        context: Context,
        command: WidgetCommand,
        pendingResult: PendingResult,
    ) {
        // onReceive already runs on the main thread; posting keeps every touch of `future` there.
        handler.post { connect(context, command, pendingResult) }
    }

    private fun connect(
        context: Context,
        command: WidgetCommand,
        pendingResult: PendingResult,
    ) {
        handler.removeCallbacks(releaseRunnable)
        val pending = future?.takeIf { it.isReusable() }
        val connecting = pending ?: newConnection(context)
        connecting.addListener(
            { onConnected(context, connecting, command, pendingResult) },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun newConnection(context: Context): ListenableFuture<MediaController> {
        release()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        return MediaController.Builder(context, token).buildAsync().also { future = it }
    }

    /** A finished future is only worth reusing while its controller is still connected. */
    private fun ListenableFuture<MediaController>.isReusable(): Boolean =
        if (isDone) runCatching { get().isConnected }.getOrDefault(false) else !isCancelled

    private fun onConnected(
        context: Context,
        connection: ListenableFuture<MediaController>,
        command: WidgetCommand,
        pendingResult: PendingResult,
    ) {
        val controller = runCatching { connection.get() }.getOrNull()?.takeIf { it.isConnected }
        if (controller == null) {
            Log.w(TAG, "Widget tap could not reach the playback session")
            if (future === connection) release()
            finish(context, controller = null, pendingResult = pendingResult)
            return
        }
        if (controller.currentMediaItem == null) {
            resumeThenApply(context, controller, command, pendingResult)
        } else {
            apply(controller, command)
            finish(context, controller, pendingResult)
        }
    }

    private fun apply(controller: MediaController, command: WidgetCommand) {
        when (command) {
            WidgetCommand.Previous -> when (widgetPreviousAction(controller.currentPosition)) {
                WidgetPreviousAction.RestartChapter -> controller.seekTo(0L)
                WidgetPreviousAction.PreviousChapter -> controller.seekToPreviousMediaItem()
            }
            WidgetCommand.Toggle ->
                if (controller.playWhenReady) controller.pause() else controller.play()
            WidgetCommand.Next -> controller.seekToNextMediaItem()
        }
    }

    /**
     * Empty session: ask for the last book back, then finish the tap once the queue lands, so a
     * skip acts on real chapters and the widget is redrawn with the restored book.
     */
    private fun resumeThenApply(
        context: Context,
        controller: MediaController,
        command: WidgetCommand,
        pendingResult: PendingResult,
    ) {
        controller.play()
        var settled = false
        lateinit var listener: Player.Listener
        val timeout = Runnable {
            if (settled) return@Runnable
            settled = true
            controller.removeListener(listener)
            Log.w(TAG, "Playback resumption did not deliver a queue in time")
            finish(context, controller, pendingResult)
        }
        listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (settled || player.currentMediaItem == null) return
                settled = true
                controller.removeListener(this)
                handler.removeCallbacks(timeout)
                widgetCommandAfterResumption(command)?.let { apply(controller, it) }
                finish(context, controller, pendingResult)
            }
        }
        controller.addListener(listener)
        handler.postDelayed(timeout, RESUME_TIMEOUT_MS)
    }

    private fun finish(
        context: Context,
        controller: MediaController?,
        pendingResult: PendingResult,
    ) {
        if (controller != null && controller.currentMediaItem != null) {
            PlaybackWidgetUpdater.update(context, controller)
        } else {
            PlaybackWidgetUpdater.renderCached(context)
        }
        runCatching { pendingResult.finish() }
        // Outlive the broadcast: the service must not be torn down between "playback requested"
        // and "notification posted", and taps often come in pairs.
        handler.removeCallbacks(releaseRunnable)
        handler.postDelayed(releaseRunnable, KEEP_ALIVE_MS)
    }

    private fun release() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }

    private const val TAG = "TingXiaWidget"
    private const val RESUME_TIMEOUT_MS = 5_000L
    private const val KEEP_ALIVE_MS = 15_000L
}
