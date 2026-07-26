package com.tingxia.app.player

import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tingxia.app.data.model.Book
import com.tingxia.app.data.model.Chapter
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.sin

/**
 * Plays a generated audio file through a real ExoPlayer to prove the intro/outro
 * clip is audible end to end, not just present on the MediaItem.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class ClippedPlaybackTest {
    @Test
    fun clippedChapter_reportsClippedWindowAndEndsAtOutro() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val wav = File(context.cacheDir, "clipped_playback_test.wav")
        writeWav(wav, durationMs = 10_000L)
        val item = testChapter(wav, durationMs = 10_000L)
            .toMediaItem(testBook(skipIntroMs = 3_000L, skipOutroMs = 2_000L), chapterCount = 1)

        val ready = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val error = AtomicReference<PlaybackException?>()
        val playerRef = AtomicReference<ExoPlayer>()
        instrumentation.runOnMainSync {
            val player = ExoPlayer.Builder(context).build()
            playerRef.set(player)
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) ready.countDown()
                    if (playbackState == Player.STATE_ENDED) ended.countDown()
                }

                override fun onPlayerError(e: PlaybackException) {
                    error.set(e)
                    ready.countDown()
                    ended.countDown()
                }
            })
            player.setMediaItem(item)
            player.prepare()
        }
        try {
            assertTrue("播放器未进入 READY", ready.await(20, TimeUnit.SECONDS))
            assertNull("播放出错: ${error.get()}", error.get())

            val readyDuration = AtomicLong()
            instrumentation.runOnMainSync { readyDuration.set(playerRef.get().duration) }
            assertTrue(
                "窗口时长 ${readyDuration.get()}ms 未反映 5 秒裁剪",
                abs(readyDuration.get() - 5_000L) <= 600L,
            )

            val playStartedAt = SystemClock.elapsedRealtime()
            instrumentation.runOnMainSync { playerRef.get().play() }
            assertTrue("裁剪章节未在限时内播完", ended.await(20, TimeUnit.SECONDS))
            assertNull("播放出错: ${error.get()}", error.get())

            val elapsed = SystemClock.elapsedRealtime() - playStartedAt
            assertTrue("播放耗时 ${elapsed}ms，片尾裁剪未生效", elapsed < 9_000L)

            val endPosition = AtomicLong()
            instrumentation.runOnMainSync { endPosition.set(playerRef.get().currentPosition) }
            assertTrue(
                "结束位置 ${endPosition.get()}ms 不在裁剪终点附近",
                abs(endPosition.get() - 5_000L) <= 600L,
            )
        } finally {
            instrumentation.runOnMainSync { playerRef.get()?.release() }
            wav.delete()
        }
    }

    @Test
    fun unclippedChapter_keepsFullWindow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val wav = File(context.cacheDir, "unclipped_playback_test.wav")
        writeWav(wav, durationMs = 4_000L)
        val item = testChapter(wav, durationMs = 4_000L)
            .toMediaItem(testBook(skipIntroMs = 0L, skipOutroMs = 0L), chapterCount = 1)

        val ready = CountDownLatch(1)
        val error = AtomicReference<PlaybackException?>()
        val playerRef = AtomicReference<ExoPlayer>()
        instrumentation.runOnMainSync {
            val player = ExoPlayer.Builder(context).build()
            playerRef.set(player)
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) ready.countDown()
                }

                override fun onPlayerError(e: PlaybackException) {
                    error.set(e)
                    ready.countDown()
                }
            })
            player.setMediaItem(item)
            player.prepare()
        }
        try {
            assertTrue("播放器未进入 READY", ready.await(20, TimeUnit.SECONDS))
            assertNull("播放出错: ${error.get()}", error.get())
            val duration = AtomicLong()
            instrumentation.runOnMainSync { duration.set(playerRef.get().duration) }
            assertTrue(
                "未配置跳过时窗口时长 ${duration.get()}ms 应接近完整 4 秒",
                abs(duration.get() - 4_000L) <= 600L,
            )
        } finally {
            instrumentation.runOnMainSync { playerRef.get()?.release() }
            wav.delete()
        }
    }
}

/** 16-bit mono PCM WAV with a quiet tone so decoders see real samples. */
private fun writeWav(file: File, durationMs: Long) {
    val sampleRate = 8_000
    val totalSamples = (durationMs * sampleRate / 1_000L).toInt()
    val dataSize = totalSamples * 2
    DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { out ->
        out.writeBytes("RIFF")
        out.writeIntLe(36 + dataSize)
        out.writeBytes("WAVE")
        out.writeBytes("fmt ")
        out.writeIntLe(16)
        out.writeShortLe(1)
        out.writeShortLe(1)
        out.writeIntLe(sampleRate)
        out.writeIntLe(sampleRate * 2)
        out.writeShortLe(2)
        out.writeShortLe(16)
        out.writeBytes("data")
        out.writeIntLe(dataSize)
        for (i in 0 until totalSamples) {
            out.writeShortLe((sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 2_000).toInt())
        }
    }
}

private fun DataOutputStream.writeIntLe(value: Int) {
    write(value and 0xFF)
    write((value shr 8) and 0xFF)
    write((value shr 16) and 0xFF)
    write((value shr 24) and 0xFF)
}

private fun DataOutputStream.writeShortLe(value: Int) {
    write(value and 0xFF)
    write((value shr 8) and 0xFF)
}

private fun testBook(skipIntroMs: Long, skipOutroMs: Long) = Book(
    id = 1L,
    title = "测试书籍",
    author = null,
    coverPath = null,
    rootUri = "file:///tmp",
    totalDurationMs = 10_000L,
    lastPlayedAt = 0L,
    currentChapterId = null,
    currentPositionMs = 0L,
    linearPositionMs = 0L,
    createdAt = 0L,
    needsReauth = false,
    skipIntroMs = skipIntroMs,
    skipOutroMs = skipOutroMs,
)

private fun testChapter(file: File, durationMs: Long) = Chapter(
    id = 7L,
    bookId = 1L,
    title = "第一章",
    uri = Uri.fromFile(file).toString(),
    index = 0,
    durationMs = durationMs,
    fileName = file.name,
)
