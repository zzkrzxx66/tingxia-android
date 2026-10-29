package com.tingxia.app.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.SystemClock
import androidx.collection.LruCache
import androidx.compose.ui.graphics.toArgb
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.tingxia.app.R
import com.tingxia.app.ui.components.extractCoverInitial
import com.tingxia.app.ui.theme.CoverPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

/**
 * Thread-safe positive LRU + short-lived negative failure cache + in-flight request coalescer.
 *
 * When a remote cover fails to load (offline, 404, bad bytes), caching that failure for a short
 * TTL lets subsequent Media3 notification/chapter refreshes synchronously return the book's
 * fallback cover (`future.isDone == true`) instead of launching a new async load that blanks
 * the notification icon first. Once [failureTtlMs] elapses, the next update retries cleanly.
 */
internal class ArtworkRequestCache<V : Any>(
    private val maxEntries: Int = 24,
    private val maxFailures: Int = 32,
    private val failureTtlMs: Long = 60_000L,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
) {
    sealed interface Lookup<out T> {
        data class Hit<T>(val value: T) : Lookup<T>
        data object RecentFailure : Lookup<Nothing>
        data class InFlight<T>(val future: ListenableFuture<T>) : Lookup<T>
        class Started<T>(val future: SettableFuture<T>) : Lookup<T>
    }

    private val positive = object : LinkedHashMap<String, V>(maxEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean =
            size > maxEntries
    }
    private val failures = LinkedHashMap<String, Long>()
    private val inFlight = HashMap<String, ListenableFuture<V>>()

    @Synchronized
    fun acquireOrStart(
        cacheKey: String,
        flightKey: String = cacheKey,
        honorFailureCache: Boolean = true,
    ): Lookup<V> {
        positive[cacheKey]?.let { return Lookup.Hit(it) }
        if (honorFailureCache) {
            val failedUntil = failures[cacheKey]
            if (failedUntil != null) {
                if (nowMs() < failedUntil) {
                    return Lookup.RecentFailure
                } else {
                    failures.remove(cacheKey)
                }
            }
        }
        inFlight[flightKey]?.let { return Lookup.InFlight(it) }
        val started = SettableFuture.create<V>()
        inFlight[flightKey] = started
        return Lookup.Started(started)
    }

    @Synchronized
    fun completeSuccess(cacheKey: String, flightKey: String = cacheKey, value: V) {
        inFlight.remove(flightKey)
        failures.remove(cacheKey)
        positive[cacheKey] = value
    }

    @Synchronized
    fun completeFailure(cacheKey: String, flightKey: String = cacheKey) {
        inFlight.remove(flightKey)
        failures.remove(cacheKey)
        failures[cacheKey] = nowMs() + failureTtlMs
        while (failures.size > maxFailures) {
            val eldestKey = failures.entries.firstOrNull()?.key ?: break
            failures.remove(eldestKey)
        }
    }

    @Synchronized
    fun abortInFlight(flightKey: String) {
        inFlight.remove(flightKey)
    }
}

/**
 * MediaSession [BitmapLoader] backed by Coil's shared memory/disk cache, with a literary
 * [CoverPalette] fallback cover when a book has no artwork or its remote URL fails to decode.
 *
 * Media3's default `DataSourceBitmapLoader` bypasses Coil's cache and fails on CDN WebP/HEIC
 * responses without explicit image MIME headers, causing system media notifications and OEM
 * live capsules (OriginOS / MagicOS / HyperOS) to fall back to the generic app launcher icon.
 */
@UnstableApi
class CoilBitmapLoader(
    context: Context,
    private val scope: CoroutineScope,
) : BitmapLoader {

    private val appContext = context.applicationContext
    private val requestCache = ArtworkRequestCache<Bitmap>(
        maxEntries = 24,
        maxFailures = 32,
        failureTtlMs = FAILURE_TTL_MS,
    )
    private val fallbackCache = LruCache<String, Bitmap>(16)
    private val fallbackLock = Any()

    override fun supportsMimeType(mimeType: String): Boolean =
        mimeType.startsWith("image/", ignoreCase = true)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val cacheKey = byteCacheKey(data)
        val flightKey = "raw:$cacheKey"
        return when (val lookup = requestCache.acquireOrStart(cacheKey, flightKey, honorFailureCache = true)) {
            is ArtworkRequestCache.Lookup.Hit -> Futures.immediateFuture(lookup.value)
            ArtworkRequestCache.Lookup.RecentFailure ->
                Futures.immediateFailedFuture(IllegalArgumentException("Cached artwork decode failure"))
            is ArtworkRequestCache.Lookup.InFlight -> lookup.future
            is ArtworkRequestCache.Lookup.Started -> {
                scope.launch(Dispatchers.IO) {
                    try {
                        val decoded = BitmapFactory.decodeByteArray(data, 0, data.size)
                        if (decoded != null) {
                            requestCache.completeSuccess(cacheKey, flightKey, decoded)
                            lookup.future.set(decoded)
                        } else {
                            requestCache.completeFailure(cacheKey, flightKey)
                            lookup.future.setException(IllegalArgumentException("Failed to decode artwork bytes"))
                        }
                    } catch (c: CancellationException) {
                        requestCache.abortInFlight(flightKey)
                        lookup.future.cancel(false)
                        throw c
                    } catch (t: Throwable) {
                        requestCache.completeFailure(cacheKey, flightKey)
                        lookup.future.setException(t)
                    }
                }
                lookup.future
            }
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val cacheKey = uri.toString()
        val flightKey = "raw:$cacheKey"
        return when (val lookup = requestCache.acquireOrStart(cacheKey, flightKey, honorFailureCache = true)) {
            is ArtworkRequestCache.Lookup.Hit -> Futures.immediateFuture(lookup.value)
            ArtworkRequestCache.Lookup.RecentFailure ->
                Futures.immediateFailedFuture(IllegalStateException("Cached artwork load failure for $uri"))
            is ArtworkRequestCache.Lookup.InFlight -> lookup.future
            is ArtworkRequestCache.Lookup.Started -> {
                scope.launch(Dispatchers.IO) {
                    try {
                        val bitmap = fetchWithCoil(uri)
                        if (bitmap != null) {
                            requestCache.completeSuccess(cacheKey, flightKey, bitmap)
                            lookup.future.set(bitmap)
                        } else {
                            requestCache.completeFailure(cacheKey, flightKey)
                            lookup.future.setException(IllegalStateException("Coil returned null bitmap for $uri"))
                        }
                    } catch (c: CancellationException) {
                        requestCache.abortInFlight(flightKey)
                        lookup.future.cancel(false)
                        throw c
                    } catch (t: Throwable) {
                        requestCache.completeFailure(cacheKey, flightKey)
                        lookup.future.setException(t)
                    }
                }
                lookup.future
            }
        }
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val bookTitle = (metadata.albumTitle ?: metadata.title ?: "").toString()
        val data = metadata.artworkData
        val uri = metadata.artworkUri

        if (data == null && uri == null) {
            return Futures.immediateFuture(renderFallbackCover(bookTitle))
        }

        // Fast path: return an already-completed Future on positive cache hit OR recent failure
        // before launching a coroutine, and coalesce duplicate in-flight metadata requests.
        val cacheKey = if (data != null) byteCacheKey(data) else uri!!.toString()
        val flightKey = "meta:$cacheKey|$bookTitle"
        return when (val lookup = requestCache.acquireOrStart(cacheKey, flightKey, honorFailureCache = true)) {
            is ArtworkRequestCache.Lookup.Hit -> Futures.immediateFuture(lookup.value)
            ArtworkRequestCache.Lookup.RecentFailure ->
                Futures.immediateFuture(renderFallbackCover(bookTitle))
            is ArtworkRequestCache.Lookup.InFlight -> lookup.future
            is ArtworkRequestCache.Lookup.Started -> {
                scope.launch(Dispatchers.IO) {
                    try {
                        val loaded = runCatching {
                            when {
                                data != null -> BitmapFactory.decodeByteArray(data, 0, data.size)
                                uri != null -> fetchWithCoil(uri)
                                else -> null
                            }
                        }.getOrNull()
                        if (loaded != null) {
                            requestCache.completeSuccess(cacheKey, flightKey, loaded)
                            lookup.future.set(loaded)
                        } else {
                            requestCache.completeFailure(cacheKey, flightKey)
                            lookup.future.set(renderFallbackCover(bookTitle))
                        }
                    } catch (c: CancellationException) {
                        requestCache.abortInFlight(flightKey)
                        lookup.future.cancel(false)
                        throw c
                    } catch (t: Throwable) {
                        requestCache.completeFailure(cacheKey, flightKey)
                        lookup.future.set(renderFallbackCover(bookTitle))
                    }
                }
                lookup.future
            }
        }
    }

    private fun byteCacheKey(data: ByteArray): String =
        "bytes:${data.size}:${data.contentHashCode()}"

    private suspend fun fetchWithCoil(uri: Uri): Bitmap? {
        val raw = uri.toString()
        val model: Any = when {
            raw.startsWith("content:") || raw.startsWith("file:") ||
                raw.startsWith("android.resource:") || raw.startsWith("http") -> uri
            else -> File(uri.path ?: raw).takeIf(File::isFile) ?: uri
        }
        val request = ImageRequest.Builder(appContext)
            .data(model)
            .size(ARTWORK_SIZE_PX)
            .allowHardware(false)
            .build()
        val result = appContext.imageLoader.execute(request)
        val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            ?: return null
        return if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        }
    }

    private fun renderFallbackCover(title: String): Bitmap {
        val normalized = title.trim()
        val key = normalized.ifEmpty { "?" }
        synchronized(fallbackLock) {
            fallbackCache[key]?.let { return it }
        }

        val size = FALLBACK_SIZE_PX
        val paletteColor = CoverPalette[abs(normalized.hashCode()) % CoverPalette.size].toArgb()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Warm linen gradient wash matching in-app FallbackCover
        canvas.drawRect(
            0f,
            0f,
            size.toFloat(),
            size.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    size.toFloat(),
                    size.toFloat(),
                    intArrayOf(
                        paletteColor.lightened(0.14f),
                        paletteColor,
                        paletteColor.darkened(0.16f),
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )

        // Left spine crease band
        val spineWidth = size * 0.065f
        canvas.drawRect(
            0f,
            0f,
            spineWidth,
            size.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    spineWidth,
                    0f,
                    intArrayOf(0x2EFFFFFF, 0x38000000),
                    null,
                    Shader.TileMode.CLAMP,
                )
            },
        )

        // Debossed inner hairline frame
        val inset = size * 0.055f
        canvas.drawRect(
            RectF(inset, inset, size - inset, size - inset),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = size * 0.006f
                color = 0x3DFFFFFF
            },
        )

        // Book initial (skipping punctuation like 《》 so 《三体》 renders "三")
        val glyph = extractCoverInitial(normalized)
            ?: appContext.getString(R.string.cover_fallback_character)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xF5FFFFFF.toInt()
            textSize = size * 0.42f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setShadowLayer(size * 0.025f, 0f, size * 0.012f, 0x55000000)
        }
        val metrics = textPaint.fontMetrics
        val baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(glyph, size / 2f, baseline, textPaint)

        synchronized(fallbackLock) {
            fallbackCache.put(key, bitmap)
        }
        return bitmap
    }

    private fun Int.lightened(amount: Float): Int {
        val r = ((this shr 16) and 0xFF).let { (it + (255 - it) * amount).toInt().coerceIn(0, 255) }
        val g = ((this shr 8) and 0xFF).let { (it + (255 - it) * amount).toInt().coerceIn(0, 255) }
        val b = (this and 0xFF).let { (it + (255 - it) * amount).toInt().coerceIn(0, 255) }
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun Int.darkened(amount: Float): Int {
        val factor = 1f - amount
        val r = (((this shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((this shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((this and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    companion object {
        private const val ARTWORK_SIZE_PX = 512
        private const val FALLBACK_SIZE_PX = 384
        private const val FAILURE_TTL_MS = 60_000L
    }
}
