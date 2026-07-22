package com.tingxia.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.media3.common.Player
import com.tingxia.app.MainActivity
import com.tingxia.app.R
import com.tingxia.app.player.PlayerController

object PlaybackWidgetUpdater {
    fun update(context: Context, player: Player) {
        val item = player.currentMediaItem ?: return
        val extras = item.mediaMetadata.extras
        val snapshot = PlaybackWidgetSnapshot(
            hasMedia = true,
            bookTitle = item.mediaMetadata.albumTitle?.toString().orEmpty(),
            chapterTitle = item.mediaMetadata.title?.toString().orEmpty(),
            chapterIndex = extras?.getInt(PlayerController.KEY_CHAPTER_INDEX) ?: 0,
            chapterCount = extras?.getInt(PlayerController.KEY_CHAPTER_COUNT) ?: 0,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L),
            isPlaying = player.playWhenReady,
        )
        PlaybackWidgetStateStore.save(context, snapshot)
        render(context, snapshot)
    }

    fun clear(context: Context) {
        val empty = PlaybackWidgetSnapshot()
        PlaybackWidgetStateStore.save(context, empty)
        render(context, empty)
    }

    fun renderCached(context: Context) {
        render(context, PlaybackWidgetStateStore.load(context))
    }

    private fun render(context: Context, state: PlaybackWidgetSnapshot) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, PlaybackWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        val views = RemoteViews(context.packageName, R.layout.playback_widget).apply {
            setTextViewText(
                R.id.widget_book_title,
                state.bookTitle.ifBlank { context.getString(R.string.app_name) },
            )
            setTextViewText(
                R.id.widget_chapter_title,
                state.chapterTitle.ifBlank { context.getString(R.string.widget_no_media) },
            )
            setTextViewText(R.id.widget_status, statusText(context, state))
            setViewVisibility(
                R.id.widget_progress,
                if (state.hasMedia && state.durationMs > 0L) View.VISIBLE else View.INVISIBLE,
            )
            setProgressBar(R.id.widget_progress, 1_000, state.progressPermille, false)
            setImageViewResource(
                R.id.widget_play_pause,
                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            )
            setContentDescription(
                R.id.widget_play_pause,
                context.getString(if (state.isPlaying) R.string.pause else R.string.play),
            )
            setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            setOnClickPendingIntent(
                R.id.widget_previous,
                controlIntent(context, PlaybackWidgetProvider.ACTION_PREVIOUS, REQUEST_PREVIOUS),
            )
            setOnClickPendingIntent(
                R.id.widget_play_pause,
                controlIntent(context, PlaybackWidgetProvider.ACTION_TOGGLE, REQUEST_TOGGLE),
            )
            setOnClickPendingIntent(
                R.id.widget_next,
                controlIntent(context, PlaybackWidgetProvider.ACTION_NEXT, REQUEST_NEXT),
            )
        }
        manager.updateAppWidget(ids, views)
    }

    private fun statusText(context: Context, state: PlaybackWidgetSnapshot): String {
        if (!state.hasMedia) return context.getString(R.string.widget_tap_to_resume)
        val parts = buildList {
            if (state.chapterCount > 0) {
                add(
                    context.getString(
                        R.string.widget_chapter_progress,
                        state.chapterIndex.coerceAtLeast(0) + 1,
                        state.chapterCount,
                    ),
                )
            }
            if (state.durationMs > 0L) {
                add(
                    context.getString(
                        R.string.widget_time_progress,
                        formatWidgetDuration(state.positionMs),
                        formatWidgetDuration(state.durationMs),
                    ),
                )
            }
        }
        return parts.joinToString(" · ").ifBlank { context.getString(R.string.widget_ready) }
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN_APP,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun controlIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, PlaybackWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private const val REQUEST_OPEN_APP = 40
    private const val REQUEST_PREVIOUS = 41
    private const val REQUEST_TOGGLE = 42
    private const val REQUEST_NEXT = 43
}

private object PlaybackWidgetStateStore {
    fun save(context: Context, state: PlaybackWidgetSnapshot) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAS_MEDIA, state.hasMedia)
            .putString(KEY_BOOK_TITLE, state.bookTitle)
            .putString(KEY_CHAPTER_TITLE, state.chapterTitle)
            .putInt(KEY_CHAPTER_INDEX, state.chapterIndex)
            .putInt(KEY_CHAPTER_COUNT, state.chapterCount)
            .putLong(KEY_POSITION_MS, state.positionMs)
            .putLong(KEY_DURATION_MS, state.durationMs)
            .putBoolean(KEY_IS_PLAYING, state.isPlaying)
            .apply()
    }

    fun load(context: Context): PlaybackWidgetSnapshot {
        val values = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return PlaybackWidgetSnapshot(
            hasMedia = values.getBoolean(KEY_HAS_MEDIA, false),
            bookTitle = values.getString(KEY_BOOK_TITLE, "").orEmpty(),
            chapterTitle = values.getString(KEY_CHAPTER_TITLE, "").orEmpty(),
            chapterIndex = values.getInt(KEY_CHAPTER_INDEX, 0),
            chapterCount = values.getInt(KEY_CHAPTER_COUNT, 0),
            positionMs = values.getLong(KEY_POSITION_MS, 0L),
            durationMs = values.getLong(KEY_DURATION_MS, 0L),
            isPlaying = values.getBoolean(KEY_IS_PLAYING, false),
        )
    }

    private const val PREFERENCES = "playback_widget_state"
    private const val KEY_HAS_MEDIA = "has_media"
    private const val KEY_BOOK_TITLE = "book_title"
    private const val KEY_CHAPTER_TITLE = "chapter_title"
    private const val KEY_CHAPTER_INDEX = "chapter_index"
    private const val KEY_CHAPTER_COUNT = "chapter_count"
    private const val KEY_POSITION_MS = "position_ms"
    private const val KEY_DURATION_MS = "duration_ms"
    private const val KEY_IS_PLAYING = "is_playing"
}
