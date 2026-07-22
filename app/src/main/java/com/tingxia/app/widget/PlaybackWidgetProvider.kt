package com.tingxia.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
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
        super.onReceive(context, intent)
        val command = when (intent.action) {
            ACTION_PREVIOUS -> WidgetCommand.Previous
            ACTION_TOGGLE -> WidgetCommand.Toggle
            ACTION_NEXT -> WidgetCommand.Next
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> WidgetCommand.Refresh
            else -> return
        }
        connectAndRun(context.applicationContext, command)
    }

    private fun connectAndRun(context: Context, command: WidgetCommand) {
        val pendingResult = goAsync()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture.addListener(
            {
                try {
                    val controller = controllerFuture.get()
                    when (command) {
                        WidgetCommand.Previous -> {
                            if (controller.currentPosition > RESTART_CHAPTER_THRESHOLD_MS) {
                                controller.seekTo(0L)
                            } else {
                                controller.seekToPreviousMediaItem()
                            }
                        }
                        WidgetCommand.Toggle -> {
                            if (controller.playWhenReady) controller.pause() else controller.play()
                        }
                        WidgetCommand.Next -> controller.seekToNextMediaItem()
                        WidgetCommand.Refresh -> Unit
                    }
                    if (controller.currentMediaItem != null) {
                        PlaybackWidgetUpdater.update(context, controller)
                    } else {
                        PlaybackWidgetUpdater.renderCached(context)
                    }
                } catch (_: Exception) {
                    PlaybackWidgetUpdater.renderCached(context)
                } finally {
                    MediaController.releaseFuture(controllerFuture)
                    pendingResult.finish()
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private sealed interface WidgetCommand {
        data object Previous : WidgetCommand
        data object Toggle : WidgetCommand
        data object Next : WidgetCommand
        data object Refresh : WidgetCommand
    }

    companion object {
        const val ACTION_PREVIOUS = "com.tingxia.app.widget.PREVIOUS"
        const val ACTION_TOGGLE = "com.tingxia.app.widget.TOGGLE"
        const val ACTION_NEXT = "com.tingxia.app.widget.NEXT"

        private const val RESTART_CHAPTER_THRESHOLD_MS = 3_000L
    }
}
