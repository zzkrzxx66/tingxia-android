package com.tingxia.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.collection.LruCache
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Progress in this app is drawn in the colour of the book it belongs to: the shelf tile's hairline,
 * the resume strip and the mini player ring all take their tint from the cover artwork.
 *
 * It is the one thing here Material cannot hand you, and it makes a shelf of covers read as a set
 * of individual books rather than a grid of identical widgets.
 */
private val accentCache = LruCache<String, Color>(128)

@Composable
fun rememberCoverAccent(coverPath: String?): Color {
    val fallback = MaterialTheme.colorScheme.primary
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    if (coverPath.isNullOrBlank()) return fallback
    val key = "$coverPath|${if (dark) "d" else "l"}"
    val cached = accentCache[key]
    var color by remember(key) { mutableStateOf(cached ?: fallback) }
    LaunchedEffect(key) {
        if (accentCache[key] != null) return@LaunchedEffect
        val extracted = withContext(Dispatchers.IO) { extractAccent(context, coverPath, dark) }
        if (extracted != null) {
            accentCache.put(key, extracted)
            color = extracted
        }
    }
    return color
}

/** Loads a 32px sample of the artwork through Coil's cache and hands it to [CoverAccentPolicy]. */
private suspend fun extractAccent(context: Context, coverPath: String, dark: Boolean): Color? {
    val model: Any = when {
        coverPath.startsWith("content:") || coverPath.startsWith("file:") ||
            coverPath.startsWith("http") -> coverPath
        else -> File(coverPath)
    }
    return try {
        val request = ImageRequest.Builder(context)
            .data(model)
            .size(32)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            ?: return null
        val sample = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        }
        val pixels = IntArray(sample.width * sample.height)
        sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
        CoverAccentPolicy.pick(pixels, dark)?.let { Color(it) }
    } catch (_: Exception) {
        null
    }
}
