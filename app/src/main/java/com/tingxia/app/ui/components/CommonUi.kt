package com.tingxia.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

private val CoverPalette = listOf(
    Color(0xFF3D5A9E),
    Color(0xFF5B4B8A),
    Color(0xFF2E6B5E),
    Color(0xFF8A4B3D),
    Color(0xFF4B6B8A),
    Color(0xFF6B4B6B),
)

@Composable
fun BookCover(
    title: String,
    coverPath: String?,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    corner: Dp = 12.dp,
) {
    val shape = RoundedCornerShape(corner)
    val boxMod = if (size != null) {
        modifier.size(size).clip(shape)
    } else {
        modifier.aspectRatio(1f).clip(shape)
    }
    Box(boxMod) {
        val model: Any? = when {
            coverPath.isNullOrBlank() -> null
            coverPath.startsWith("content:") || coverPath.startsWith("file:") ||
                coverPath.startsWith("http") -> coverPath
            else -> File(coverPath)
        }
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            val color = CoverPalette[kotlin.math.abs(title.hashCode()) % CoverPalette.size]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title.take(1).ifEmpty { "书" },
                    color = Color.White,
                    fontSize = if (size != null && size < 80.dp) 22.sp else 36.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatProgressLabel(positionMs: Long, durationMs: Long): String {
    return "${formatDuration(positionMs)} / ${formatDuration(durationMs)}"
}
