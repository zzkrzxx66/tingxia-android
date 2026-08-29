package com.tingxia.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.LruCache
import android.view.View
import android.widget.RemoteViews
import androidx.media3.common.Player
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.tingxia.app.MainActivity
import com.tingxia.app.R
import com.tingxia.app.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object PlaybackWidgetUpdater {
    private val artworkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingArtwork = ConcurrentHashMap.newKeySet<String>()
    private val failedArtwork = ConcurrentHashMap.newKeySet<String>()
    /** Decoded covers, before they are fitted to a widget slot. */
    private val sourceCache = object : LruCache<String, Bitmap>(ARTWORK_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    /** Covers cut to one slot's aspect ratio, keyed by uri plus that ratio. */
    private val coverCache = object : LruCache<String, Bitmap>(COVER_CACHE_BYTES) {
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
            val layoutId = widgetLayoutForHeight(heightDp)
            val singleLine = layoutId == R.layout.playback_widget_compact
            val views = RemoteViews(context.packageName, layoutId).apply {
                // The strip has room for one line of text, so book and chapter share it there.
                setTextViewText(
                    R.id.widget_book_title,
                    widgetHeadline(
                        bookTitle = state.bookTitle.ifBlank { context.getString(R.string.app_name) },
                        chapterTitle = state.chapterTitle,
                        merged = singleLine,
                    ),
                )
                setTextViewText(
                    R.id.widget_chapter_title,
                    widgetChapterLine(
                        chapterTitle = state.chapterTitle,
                        countLabel = if (state.chapterCount > 0) {
                            context.getString(
                                R.string.widget_chapter_progress,
                                state.chapterIndex.coerceAtLeast(0) + 1,
                                state.chapterCount,
                            )
                        } else {
                            ""
                        },
                    ).ifBlank { context.getString(R.string.widget_no_media) },
                )
                // Elapsed/total rides beside the progress bar in both sizes now, so the hairline is
                // not the only thing saying where in the chapter we are.
                val timeLabel = if (state.hasMedia && state.durationMs > 0L) {
                    context.getString(
                        R.string.widget_time_progress,
                        formatWidgetDuration(state.positionMs),
                        formatWidgetDuration(state.durationMs),
                    )
                } else {
                    ""
                }
                setTextViewText(
                    R.id.widget_status,
                    timeLabel.ifBlank { context.getString(R.string.widget_tap_to_resume) },
                )
                setViewVisibility(
                    R.id.widget_status,
                    if (timeLabel.isNotBlank() || !singleLine) View.VISIBLE else View.GONE,
                )
                // The cover is drawn for this widget's slot, not at a fixed 3:4: a bitmap whose
                // ratio misses the slot got letterboxed by the ImageView, and that empty column
                // was the gap between cover and panel.
                val spec = if (singleLine) {
                    widgetCoverSpec(COMPACT_COVER_WIDTH_DP, COMPACT_STRIP_HEIGHT_DP)
                } else {
                    widgetCoverSpec(EXPANDED_COVER_WIDTH_DP, EXPANDED_COVER_HEIGHT_DP)
                }
                val source = state.artworkUri.takeIf { it.isNotBlank() }?.let(sourceCache::get)
                val artwork = state.artworkUri.takeIf { it.isNotBlank() }?.let { uri ->
                    fittedCover(uri, spec)
                }
                if (artwork == null) {
                    // Match the in-app fallback cover: palette wash + initial, so the
                    // desk widget speaks the same visual language as the shelf.
                    setImageViewBitmap(
                        R.id.widget_artwork,
                        fallbackArtwork(context, state.bookTitle, spec),
                    )
                } else {
                    setImageViewBitmap(R.id.widget_artwork, artwork)
                }
                // Panel colour follows the cover: a muted, darkened version of the cover's own
                // average, poured into the white alpha gradient in drawable-v31. A flat grey slab
                // beside a red-and-black cover read as two unrelated objects.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val tint = ColorStateList.valueOf(
                        widgetPanelTint(
                            source?.let(::averageColor) ?: fallbackBase(state.bookTitle),
                        ),
                    )
                    setColorStateList(R.id.widget_panel, "setBackgroundTintList", tint)
                    // The plate behind the cover takes the same tint, so the few dp a fitted cover
                    // leaves over read as the panel's frame instead of letterboxing.
                    setColorStateList(R.id.widget_artwork, "setBackgroundTintList", tint)
                }
                setViewVisibility(
                    R.id.widget_progress,
                    if (state.hasMedia && state.durationMs > 0L) View.VISIBLE else View.INVISIBLE,
                )
                setProgressBar(R.id.widget_progress, 1_000, state.progressPermille, false)
                setImageViewResource(
                    R.id.widget_play_pause,
                    if (state.isPlaying) {
                        R.drawable.ic_widget_pause_circle
                    } else {
                        R.drawable.ic_widget_play_circle
                    },
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

    /** Cover for one slot, composed on demand from the decoded source and cached per ratio. */
    private fun fittedCover(uri: String, spec: WidgetCoverSpec): Bitmap? {
        val key = "$uri|${spec.widthPx}x${spec.heightPx}"
        coverCache.get(key)?.let { return it }
        val source = sourceCache.get(uri) ?: return null
        return coverBitmap(source, spec).also { coverCache.put(key, it) }
    }

    private fun requestArtwork(context: Context, state: PlaybackWidgetSnapshot) {
        val artworkUri = state.artworkUri.takeIf { it.isNotBlank() } ?: return
        if (sourceCache.get(artworkUri) != null || artworkUri in failedArtwork) return
        if (!pendingArtwork.add(artworkUri)) return
        val appContext = context.applicationContext
        artworkScope.launch {
            val bitmap = runCatching { decodeArtwork(appContext, artworkUri) }.getOrNull()
            pendingArtwork.remove(artworkUri)
            if (bitmap == null) {
                failedArtwork.add(artworkUri)
                return@launch
            }
            sourceCache.put(artworkUri, bitmap)
            val latest = PlaybackWidgetStateStore.load(appContext)
            if (latest.artworkUri == artworkUri) render(appContext, latest)
        }
    }

    /**
     * Loads artwork through Coil, which brings http(s) support and the app's own disk cache with
     * it. The hand-rolled decoder this replaces only understood content/file/local paths, so every
     * online book — whose cover is an https URL — fell back to the generated placeholder.
     */
    private suspend fun decodeArtwork(context: Context, value: String): Bitmap? {
        val model: Any = when {
            value.startsWith("content:") || value.startsWith("file:") ||
                value.startsWith("android.resource:") || value.startsWith("http") -> value
            else -> File(value).takeIf(File::isFile) ?: return null
        }
        val request = ImageRequest.Builder(context)
            .data(model)
            .size(ARTWORK_DECODE_SIZE_PX)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        return ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
    }

    /**
     * Fills the slot edge to edge, centre-cropped, with only the outward (start) corners rounded:
     * the edge facing the panel stays square so cover and panel meet on a straight seam.
     */
    private fun coverBitmap(source: Bitmap, spec: WidgetCoverSpec): Bitmap {
        val output = Bitmap.createBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888)
        val scale = maxOf(
            spec.widthPx.toFloat() / source.width,
            spec.heightPx.toFloat() / source.height,
        )
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                (spec.widthPx - source.width * scale) / 2f,
                (spec.heightPx - source.height * scale) / 2f,
            )
        }
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(matrix)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        Canvas(output).drawPath(startRoundedPath(spec), paint)
        return output
    }

    /** Rounded on the start edge, square on the edge that touches the panel. */
    private fun startRoundedPath(spec: WidgetCoverSpec): Path = Path().apply {
        val r = spec.radiusPx
        addRoundRect(
            RectF(0f, 0f, spec.widthPx.toFloat(), spec.heightPx.toFloat()),
            floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r),
            Path.Direction.CW,
        )
    }

    private val fallbackCache = LruCache<String, Bitmap>(16)

    /**
     * Renders the same fallback the Compose `FallbackCover` paints: a diagonal
     * three-stop palette wash, a darker spine band and the book's initial. Colours
     * mirror [com.tingxia.app.ui.theme.CoverPalette] and the theme's mint/forest
     * foreground for the light and night widget themes.
     */
    private fun fallbackArtwork(context: Context, title: String, spec: WidgetCoverSpec): Bitmap {
        val dark = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val key = "$dark|${spec.widthPx}x${spec.heightPx}|${title.ifBlank { "?" }}"
        fallbackCache.get(key)?.let { return it }
        val width = spec.widthPx
        val height = spec.heightPx
        val base = FALLBACK_PALETTE[kotlin.math.abs(title.hashCode()) % FALLBACK_PALETTE.size]
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val shape = startRoundedPath(spec)
        canvas.drawPath(
            shape,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(base.lightened(0.12f), base, base.darkened(0.14f)),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )
        // Spine band, clipped so it does not square off the rounded start edge.
        canvas.save()
        canvas.clipPath(shape)
        canvas.drawRect(
            0f, 0f, width * 0.09f, height.toFloat(),
            Paint().apply { color = 0x1F000000 },
        )
        canvas.restore()
        // Book initial, optically centred.
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xF5FFFFFF.toInt()
            textSize = width * 0.44f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.SERIF,
                android.graphics.Typeface.BOLD,
            )
        }
        val textY = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(widgetCoverInitial(title), width / 2f, textY, textPaint)
        fallbackCache.put(key, bitmap)
        return bitmap
    }

    private fun Int.lightened(amount: Float): Int {
        val r = android.graphics.Color.red(this)
        val g = android.graphics.Color.green(this)
        val b = android.graphics.Color.blue(this)
        return android.graphics.Color.rgb(
            (r + (255 - r) * amount).toInt(),
            (g + (255 - g) * amount).toInt(),
            (b + (255 - b) * amount).toInt(),
        )
    }

    private fun Int.darkened(amount: Float): Int {
        val r = android.graphics.Color.red(this)
        val g = android.graphics.Color.green(this)
        val b = android.graphics.Color.blue(this)
        return android.graphics.Color.rgb(
            (r * (1f - amount)).toInt(),
            (g * (1f - amount)).toInt(),
            (b * (1f - amount)).toInt(),
        )
    }

    /**
     * Average colour of the cover, sampled from a 16x16 copy. A full histogram would name the most
     * saturated patch, but the panel wants the cover's overall cast, not its loudest detail.
     */
    private fun averageColor(source: Bitmap): Int {
        val sampled = Bitmap.createScaledBitmap(source, COLOR_SAMPLE_SIZE, COLOR_SAMPLE_SIZE, true)
        val pixels = IntArray(COLOR_SAMPLE_SIZE * COLOR_SAMPLE_SIZE)
        sampled.getPixels(pixels, 0, COLOR_SAMPLE_SIZE, 0, 0, COLOR_SAMPLE_SIZE, COLOR_SAMPLE_SIZE)
        if (sampled !== source) sampled.recycle()
        var r = 0L
        var g = 0L
        var b = 0L
        var counted = 0
        pixels.forEach { pixel ->
            if ((pixel ushr 24 and 0xFF) < 128) return@forEach
            r += (pixel shr 16) and 0xFF
            g += (pixel shr 8) and 0xFF
            b += pixel and 0xFF
            counted++
        }
        if (counted == 0) return FALLBACK_PALETTE[0]
        return (0xFF shl 24) or
            ((r / counted).toInt() shl 16) or
            ((g / counted).toInt() shl 8) or
            (b / counted).toInt()
    }

    /** The palette colour the generated cover would use, so an undecoded book still tints the panel. */
    private fun fallbackBase(title: String): Int =
        FALLBACK_PALETTE[kotlin.math.abs(title.hashCode()) % FALLBACK_PALETTE.size]

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
    private const val ARTWORK_CACHE_BYTES = 2 * 1_024 * 1_024
    private const val COVER_CACHE_BYTES = 4 * 1_024 * 1_024
    private const val ARTWORK_DECODE_SIZE_PX = 480
    private const val DEFAULT_EXPANDED_HEIGHT_DP = 160
    // Slot sizes from the two layouts. Both are fixed there, so these are the real on-screen boxes
    // rather than the launcher's estimate of them.
    private const val COMPACT_COVER_WIDTH_DP = 60
    private const val COMPACT_STRIP_HEIGHT_DP = 72
    private const val EXPANDED_COVER_WIDTH_DP = 100
    private const val EXPANDED_COVER_HEIGHT_DP = 132
    private const val COLOR_SAMPLE_SIZE = 16

    /** Mirrors [com.tingxia.app.ui.theme.CoverPalette]. */
    private val FALLBACK_PALETTE = intArrayOf(
        0xFF315E4B.toInt(),
        0xFF526C78.toInt(),
        0xFF745866.toInt(),
        0xFF75612F.toInt(),
        0xFF74513E.toInt(),
        0xFF3F6261.toInt(),
        0xFF555B75.toInt(),
        0xFF53613F.toInt(),
    )
}

/**
 * First character worth drawing on a placeholder cover: 《10日终焉》 used to render as 《, because the
 * old code took `first()` and Chinese titles often open with a bracket or quote.
 */
/** Strip layout headline: "书名 · 章节", falling back to the book title alone. */
internal fun widgetHeadline(bookTitle: String, chapterTitle: String, merged: Boolean): String =
    if (merged && chapterTitle.isNotBlank()) "$bookTitle · $chapterTitle" else bookTitle

internal fun widgetCoverInitial(title: String): String =
    title.firstOrNull { it.isLetterOrDigit() }?.toString() ?: "听"

/** Pixel size and start-corner radius of the cover bitmap for one widget slot. */
internal data class WidgetCoverSpec(val widthPx: Int, val heightPx: Int, val radiusPx: Float)

/**
 * Sizes the cover bitmap to the slot it will occupy, so the ImageView neither letterboxes it (which
 * left a gap before the panel) nor crops it hard. Heights are bucketed to 8dp: regenerating the
 * bitmap for every reported pixel would thrash the cache on each resize tick.
 */
/**
 * Sizes the cover bitmap to the slot it will occupy. Both slot sizes are fixed in the layouts on
 * purpose: the launcher reports a height some dp larger than the box it actually hands out, and every
 * attempt to render the cover for that reported height ended either cropping the artwork or leaving
 * a band of background around it. A fixed strip means the ratio here is exactly the ratio on screen.
 */
internal fun widgetCoverSpec(
    slotWidthDp: Int,
    slotHeightDp: Int,
    widthPx: Int = 330,
    cornerDp: Int = 14,
): WidgetCoverSpec = WidgetCoverSpec(
    widthPx = widthPx,
    heightPx = Math.round(widthPx.toFloat() * slotHeightDp / slotWidthDp).coerceAtLeast(1),
    radiusPx = widthPx.toFloat() * cornerDp / slotWidthDp,
)

/**
 * Chapter line: the chapter title with its position in the book appended, so the tall size can carry
 * both without a second status row.
 */
internal fun widgetChapterLine(chapterTitle: String, countLabel: String): String = when {
    chapterTitle.isBlank() -> countLabel
    countLabel.isBlank() -> chapterTitle
    else -> "$chapterTitle · $countLabel"
}

/**
 * Turns a cover's average colour into a panel colour: chroma pulled back and luminance forced to a
 * dark, even level, so white text and the outlined controls stay readable whether the cover is a
 * black-and-red woodcut or a pastel photograph. Hue survives, which is the point — the panel should
 * look like it belongs to that cover.
 */
internal fun widgetPanelTint(color: Int): Int {
    val r = ((color shr 16) and 0xFF).toFloat()
    val g = ((color shr 8) and 0xFF).toFloat()
    val b = (color and 0xFF).toFloat()
    val grey = 0.299f * r + 0.587f * g + 0.114f * b
    // Halfway to grey: a fully saturated cover would otherwise stain the panel.
    val mr = grey + (r - grey) * PANEL_CHROMA
    val mg = grey + (g - grey) * PANEL_CHROMA
    val mb = grey + (b - grey) * PANEL_CHROMA
    val luminance = 0.299f * mr + 0.587f * mg + 0.114f * mb
    val scale = if (luminance <= 1f) 1f else PANEL_LUMINANCE / luminance
    fun channel(value: Float): Int = (value * scale).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (channel(mr) shl 16) or (channel(mg) shl 8) or channel(mb)
}

private const val PANEL_CHROMA = 0.55f
private const val PANEL_LUMINANCE = 82f

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
