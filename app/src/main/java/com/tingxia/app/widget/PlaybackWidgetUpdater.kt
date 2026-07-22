package com.tingxia.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.util.LruCache
import android.view.View
import android.widget.RemoteViews
import androidx.media3.common.Player
import com.tingxia.app.MainActivity
import com.tingxia.app.R
import com.tingxia.app.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

object PlaybackWidgetUpdater {
    private val artworkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingArtwork = ConcurrentHashMap.newKeySet<String>()
    private val failedArtwork = ConcurrentHashMap.newKeySet<String>()
    private val artworkCache = object : LruCache<String, Bitmap>(ARTWORK_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun update(context: Context, player: Player) {
        val item = player.currentMediaItem ?: return
        val extras = item.mediaMetadata.extras
        val snapshot = PlaybackWidgetSnapshot(
            hasMedia = true,
            bookTitle = item.mediaMetadata.albumTitle?.toString().orEmpty(),
            chapterTitle = item.mediaMetadata.title?.toString().orEmpty(),
            artworkUri = item.mediaMetadata.artworkUri?.toString().orEmpty(),
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

        ids.forEach { appWidgetId ->
            val heightDp = manager.getAppWidgetOptions(appWidgetId).getInt(
                AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                DEFAULT_EXPANDED_HEIGHT_DP,
            )
            val views = RemoteViews(
                context.packageName,
                widgetLayoutForHeight(heightDp),
            ).apply {
                setTextViewText(
                    R.id.widget_book_title,
                    state.bookTitle.ifBlank { context.getString(R.string.app_name) },
                )
                setTextViewText(
                    R.id.widget_chapter_title,
                    state.chapterTitle.ifBlank { context.getString(R.string.widget_no_media) },
                )
                setTextViewText(R.id.widget_status, statusText(context, state))
                val artwork = state.artworkUri.takeIf { it.isNotBlank() }?.let(artworkCache::get)
                if (artwork == null) {
                    setImageViewResource(
                        R.id.widget_artwork,
                        R.drawable.ic_widget_artwork_placeholder,
                    )
                } else {
                    setImageViewBitmap(R.id.widget_artwork, artwork)
                }
                setViewVisibility(
                    R.id.widget_progress,
                    if (state.hasMedia && state.durationMs > 0L) View.VISIBLE else View.INVISIBLE,
                )
                setProgressBar(R.id.widget_progress, 1_000, state.progressPermille, false)
                setImageViewResource(
                    R.id.widget_play_pause,
                    if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
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
            manager.updateAppWidget(appWidgetId, views)
        }
        requestArtwork(context, state)
    }

    private fun requestArtwork(context: Context, state: PlaybackWidgetSnapshot) {
        val artworkUri = state.artworkUri.takeIf { it.isNotBlank() } ?: return
        if (artworkCache.get(artworkUri) != null || artworkUri in failedArtwork) return
        if (!pendingArtwork.add(artworkUri)) return
        val appContext = context.applicationContext
        artworkScope.launch {
            val bitmap = runCatching { decodeArtwork(appContext, artworkUri) }.getOrNull()
            pendingArtwork.remove(artworkUri)
            if (bitmap == null) {
                failedArtwork.add(artworkUri)
                return@launch
            }
            artworkCache.put(artworkUri, bitmap)
            val latest = PlaybackWidgetStateStore.load(appContext)
            if (latest.artworkUri == artworkUri) render(appContext, latest)
        }
    }

    private fun decodeArtwork(context: Context, value: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openArtwork(context, value)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > ARTWORK_DECODE_SIZE_PX) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = openArtwork(context, value)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        return roundedSquareArtwork(decoded)
    }

    private fun roundedSquareArtwork(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(
            ARTWORK_OUTPUT_SIZE_PX,
            ARTWORK_OUTPUT_SIZE_PX,
            Bitmap.Config.ARGB_8888,
        )
        val scale = maxOf(
            ARTWORK_OUTPUT_SIZE_PX.toFloat() / source.width,
            ARTWORK_OUTPUT_SIZE_PX.toFloat() / source.height,
        )
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                (ARTWORK_OUTPUT_SIZE_PX - source.width * scale) / 2f,
                (ARTWORK_OUTPUT_SIZE_PX - source.height * scale) / 2f,
            )
        }
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(matrix)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        Canvas(output).drawRoundRect(
            0f,
            0f,
            ARTWORK_OUTPUT_SIZE_PX.toFloat(),
            ARTWORK_OUTPUT_SIZE_PX.toFloat(),
            ARTWORK_CORNER_RADIUS_PX,
            ARTWORK_CORNER_RADIUS_PX,
            paint,
        )
        source.recycle()
        return output
    }

    private fun openArtwork(context: Context, value: String): InputStream? {
        val uri = Uri.parse(value)
        return when (uri.scheme) {
            "content", "file", "android.resource" -> context.contentResolver.openInputStream(uri)
            else -> File(value).takeIf(File::isFile)?.inputStream()
        }
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
    private const val ARTWORK_CACHE_BYTES = 512 * 1_024
    private const val ARTWORK_DECODE_SIZE_PX = 192
    private const val ARTWORK_OUTPUT_SIZE_PX = 160
    private const val ARTWORK_CORNER_RADIUS_PX = 14f
    private const val DEFAULT_EXPANDED_HEIGHT_DP = 160
}

internal fun widgetLayoutForHeight(heightDp: Int): Int =
    if (heightDp in 1 until 120) {
        R.layout.playback_widget_compact
    } else {
        R.layout.playback_widget
    }

private object PlaybackWidgetStateStore {
    fun save(context: Context, state: PlaybackWidgetSnapshot) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAS_MEDIA, state.hasMedia)
            .putString(KEY_BOOK_TITLE, state.bookTitle)
            .putString(KEY_CHAPTER_TITLE, state.chapterTitle)
            .putString(KEY_ARTWORK_URI, state.artworkUri)
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
            artworkUri = values.getString(KEY_ARTWORK_URI, "").orEmpty(),
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
    private const val KEY_ARTWORK_URI = "artwork_uri"
    private const val KEY_CHAPTER_INDEX = "chapter_index"
    private const val KEY_CHAPTER_COUNT = "chapter_count"
    private const val KEY_POSITION_MS = "position_ms"
    private const val KEY_DURATION_MS = "duration_ms"
    private const val KEY_IS_PLAYING = "is_playing"
}
