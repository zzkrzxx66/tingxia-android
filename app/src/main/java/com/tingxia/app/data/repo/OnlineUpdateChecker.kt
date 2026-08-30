package com.tingxia.app.data.repo

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tingxia.app.MainActivity
import com.tingxia.app.R
import com.tingxia.app.TingXiaApp
import com.tingxia.app.data.model.Book
import com.tingxia.app.data.remote.FqNovelApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 追更: looks for chapters that appeared upstream after a book was added.
 *
 * Serial audiobooks keep publishing after import, and the catalogue snapshot taken at
 * import time never changed on its own. A check appends only new item ids, so existing
 * chapter rows — with their progress, bookmarks and custom titles — are left alone.
 */
@Singleton
class OnlineUpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fqNovelApi: FqNovelApi,
    private val bookRepository: BookRepository,
    private val preferences: UserPreferencesRepository,
) {

    /** Check one book. Returns null when it is not an online book or the request failed. */
    suspend fun check(book: Book): BookRepository.RemoteUpdateResult? {
        if (!book.isRemote) return null
        val audioBookId = book.remoteAudioBookId?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val meta = if (book.isTtsVoice) null else fqNovelApi.audioMeta(audioBookId)
            val chapters = if (book.isTtsVoice) {
                fqNovelApi.novelChapters(audioBookId)
            } else {
                fqNovelApi.audioChapters(audioBookId)
            }
            if (chapters.isEmpty()) return@runCatching null
            bookRepository.applyRemoteUpdate(book.id, chapters, meta)
        }.getOrNull()
    }

    suspend fun check(bookId: Long): BookRepository.RemoteUpdateResult? =
        bookRepository.getBook(bookId)?.let { check(it) }

    /**
     * Check every online book, at most once per [minIntervalMs]. Runs on app start, so it
     * costs one catalogue request per online book and only when the user opens the app.
     */
    suspend fun sweep(minIntervalMs: Long = DEFAULT_SWEEP_INTERVAL_MS): List<BookRepository.RemoteUpdateResult> {
        if (!preferences.updateCheckEnabled.first()) return emptyList()
        val now = System.currentTimeMillis()
        val last = preferences.lastUpdateSweepAt.first()
        if (now - last < minIntervalMs) return emptyList()
        preferences.setLastUpdateSweepAt(now)
        val updated = bookRepository.getRemoteBooks()
            .filter { it.remoteFinished != true }
            .mapNotNull { book -> check(book)?.takeIf { it.addedCount > 0 } }
        if (updated.isNotEmpty()) notify(updated)
        return updated
    }

    private fun notify(results: List<BookRepository.RemoteUpdateResult>) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (results.size == 1) {
            context.getString(R.string.update_notification_one, results.first().bookTitle)
        } else {
            context.getString(R.string.update_notification_many, results.size)
        }
        val body = results.joinToString("\n") { result ->
            context.getString(R.string.update_notification_line, result.bookTitle, result.addedCount)
        }
        val notification = NotificationCompat.Builder(context, TingXiaApp.UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 43
        const val DEFAULT_SWEEP_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
