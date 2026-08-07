package com.tingxia.app.data.repo

import com.tingxia.app.data.db.BookDao
import com.tingxia.app.data.db.BookListening
import com.tingxia.app.data.db.DailyListening
import com.tingxia.app.data.db.ListenSessionDao
import com.tingxia.app.data.db.ListenSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class ListeningStats(
    val totalListenedMs: Long,
    val weekListenedMs: Long,
    val todayListenedMs: Long,
    val completedBooks: Int,
    val totalBooks: Int,
    val daily: List<DailyListening>,
    val topBooks: List<BookListening>,
)

@Singleton
class StatsRepository @Inject constructor(
    private val listenSessionDao: ListenSessionDao,
    private val bookDao: BookDao,
) {
    private val mutex = Mutex()

    /**
     * Records real listening time. [wallDeltaMs] is elapsed wall-clock time from the
     * playback ticker (already accounts for the current speed); split across local
     * calendar days when it spans midnight.
     */
    suspend fun recordListening(bookId: Long, wallDeltaMs: Long, nowMs: Long = System.currentTimeMillis()) {
        if (bookId <= 0L || wallDeltaMs <= 0L) return
        mutex.withLock {
            withContext(Dispatchers.IO) {
                var remaining = wallDeltaMs
                var cursor = nowMs - wallDeltaMs
                // Guard against pathological values; a ticker delta should be seconds.
                if (remaining > MAX_SESSION_DELTA_MS) {
                    remaining = MAX_SESSION_DELTA_MS
                    cursor = nowMs - remaining
                }
                while (remaining > 0L) {
                    val dayStart = dayStartMs(cursor)
                    val dayEnd = dayStart + DAY_MS
                    val chunk = minOf(remaining, dayEnd - cursor).coerceAtLeast(1L)
                    listenSessionDao.insert(
                        ListenSessionEntity(
                            bookId = bookId,
                            dayStartMs = dayStart,
                            listenedMs = chunk,
                        ),
                    )
                    remaining -= chunk
                    cursor += chunk
                }
            }
        }
    }

    suspend fun stats(): ListeningStats = withContext(Dispatchers.IO) {
        val today = dayStartMs(System.currentTimeMillis())
        val weekAgo = today - 6 * DAY_MS
        ListeningStats(
            totalListenedMs = listenSessionDao.totalListenedMs(),
            weekListenedMs = listenSessionDao.listenedMsSince(weekAgo),
            todayListenedMs = listenSessionDao.listenedMsSince(today),
            completedBooks = bookDao.countCompleted(),
            totalBooks = bookDao.countAll(),
            daily = listenSessionDao.dailyTotalsSince(weekAgo),
            topBooks = listenSessionDao.topBooks(5),
        )
    }

    suspend fun clearForBook(bookId: Long) {
        withContext(Dispatchers.IO) { listenSessionDao.deleteForBook(bookId) }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) { listenSessionDao.clearAll() }
    }

    companion object {
        const val DAY_MS = 24 * 3600_000L
        private const val MAX_SESSION_DELTA_MS = 60_000L

        fun dayStartMs(epochMs: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = epochMs
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
}
