package com.tingxia.app.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/** SimpleCache invokes callbacks under its lock. Only temporary spans count towards the cap. */
@UnstableApi
class PinnedCacheEvictor(private val maxBytes: Long, private val isPinned: (String) -> Boolean) : CacheEvictor {
    private val spans = TreeSet<CacheSpan>(compareBy<CacheSpan> { it.lastTouchTimestamp }.thenBy { it.key }.thenBy { it.position })
    override fun requiresCacheSpanTouches() = true
    override fun onCacheInitialized() = Unit
    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (!isPinned(key)) evict(cache, if (length == C.LENGTH_UNSET.toLong()) 0L else length)
    }
    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        spans.add(span)
        evict(cache, 0L)
    }
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) { spans.remove(span) }
    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        spans.remove(oldSpan)
        spans.add(newSpan)
    }
    private fun evict(cache: Cache, incoming: Long) {
        var temporaryBytes = spans.filter { !isPinned(it.key) }.sumOf { it.length }
        while (temporaryBytes + incoming > maxBytes) {
            val oldest = spans.firstOrNull { !isPinned(it.key) } ?: break
            cache.removeSpan(oldest)
            temporaryBytes -= oldest.length
        }
    }
}
