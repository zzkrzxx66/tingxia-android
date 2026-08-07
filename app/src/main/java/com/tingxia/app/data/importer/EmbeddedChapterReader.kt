package com.tingxia.app.data.importer

import android.content.Context
import android.net.Uri
import android.os.Build
import android.system.Os
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads embedded chapter markers from single-file audiobooks (m4b) using a
 * statically-linked ffprobe binary.
 *
 * SAF content URIs are not seekable through pipe protocol, so the source file
 * is copied into a bounded temp area first. Binaries are downloaded once per
 * version into filesDir; failures degrade gracefully to "no embedded chapters".
 */
@Singleton
class EmbeddedChapterReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class EmbeddedChapter(
        val title: String?,
        val startMs: Long,
        val endMs: Long,
    )

    private val mutex = Mutex()

    @Volatile
    private var binaryPath: String? = null

    @Volatile
    private var unavailable = false

    /**
     * @return parsed chapters (at least two), or null when chapters are absent or
     *         cannot be read. Callers must treat null as "treat the whole file as
     *         one chapter".
     */
    suspend fun readChapters(uri: Uri, mimeType: String?, fileName: String): List<EmbeddedChapter>? =
        withContext(Dispatchers.IO) {
            if (unavailable) return@withContext null
            val probe = ensureBinary() ?: return@withContext null
            val tempCopy = copyToTemp(uri) ?: return@withContext null
            try {
                parseChapters(probe, tempCopy)
            } finally {
                tempCopy.delete()
                cleanupStaleTemp()
            }
        }

    fun isCandidate(name: String, mime: String?): Boolean {
        if (mime == "audio/mp4" || mime == "audio/x-m4b" || mime == "audio/mp4a-latm") return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext == "m4b" || ext == "m4a" || ext == "mp4"
    }

    private suspend fun ensureBinary(): String? {
        binaryPath?.let { if (File(it).canExecute()) return it }
        return mutex.withLock {
            binaryPath?.let { if (File(it).canExecute()) return@withLock it }
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return@withLock markUnavailable()
            val key = when {
                abi.startsWith("arm64") || abi == "aarch64" -> "arm64"
                abi.startsWith("x86_64") -> "x86_64"
                else -> return@withLock markUnavailable()
            }
            val url = when (key) {
                "arm64" -> ARM64_URL
                else -> X86_64_URL
            }
            val expectedSha = if (key == "arm64") ARM64_SHA256 else X86_64_SHA256
            val target = context.filesDir.resolve("ffprobe/$FFPROBE_VERSION/$key/ffprobe")
            if (!target.canExecute()) {
                val downloaded = download(url, expectedSha) ?: return@withLock markUnavailable()
                target.parentFile?.mkdirs()
                if (target.exists()) target.delete()
                try {
                    Os.chmod(downloaded.absolutePath, 0b111101101) // rwxr-xr-x
                    if (!downloaded.renameTo(target)) {
                        downloaded.copyTo(target, overwrite = true)
                        downloaded.delete()
                        Os.chmod(target.absolutePath, 0b111101101)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "install ffprobe failed", e)
                    downloaded.delete()
                    return@withLock markUnavailable()
                }
            }
            binaryPath = target.absolutePath
            target.absolutePath
        }
    }

    private fun markUnavailable(): String? {
        unavailable = true
        return null
    }

    private fun copyToTemp(uri: Uri): File? {
        val dir = context.cacheDir.resolve("ffprobe-tmp").apply { mkdirs() }
        val target = File.createTempFile("probe_", ".m4b", dir)
        return try {
            var total = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_COPY_BYTES) {
                            throw IllegalStateException("file too large for embedded-chapter probe")
                        }
                        output.write(buffer, 0, n)
                    }
                }
            } ?: return null
            if (total <= 0L) null else target
        } catch (e: Exception) {
            Log.w(TAG, "copy for ffprobe failed", e)
            target.delete()
            null
        }
    }

    private fun parseChapters(probe: String, file: File): List<EmbeddedChapter>? {
        return try {
            val process = ProcessBuilder(
                probe,
                "-v", "error",
                "-print_format", "json",
                "-show_chapters",
                file.absolutePath,
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0 || output.isBlank()) return null
            val chaptersJson = JSONObject(output).optJSONArray("chapters") ?: return null
            val parsed = buildList {
                for (i in 0 until chaptersJson.length()) {
                    val c = chaptersJson.optJSONObject(i) ?: continue
                    val startMs = secondsToMs(c.optString("start_time"))
                        ?: (c.optLong("start", Long.MIN_VALUE)
                            .takeIf { it != Long.MIN_VALUE }
                            ?.let { ticks -> timebaseToMs(ticks, c.optString("time_base")) })
                        ?: continue
                    val endMs = secondsToMs(c.optString("end_time"))
                        ?: (c.optLong("end", Long.MIN_VALUE)
                            .takeIf { it != Long.MIN_VALUE }
                            ?.let { ticks -> timebaseToMs(ticks, c.optString("time_base")) })
                        ?: continue
                    if (endMs - startMs < MIN_CHAPTER_MS) continue
                    add(
                        EmbeddedChapter(
                            title = c.optJSONObject("tags")?.optString("title")
                                ?.takeIf { it.isNotBlank() },
                            startMs = startMs,
                            endMs = endMs,
                        ),
                    )
                }
            }
            // A single embedded chapter carries no navigation value; treat as none.
            parsed.takeIf { it.size >= 2 }
        } catch (e: Exception) {
            Log.w(TAG, "ffprobe parse failed", e)
            null
        }
    }

    private fun secondsToMs(seconds: String): Long? =
        seconds.toDoubleOrNull()?.let { (it * 1000.0).toLong() }

    private fun timebaseToMs(ticks: Long, timebase: String): Long? {
        val parts = timebase.split('/')
        if (parts.size != 2) return null
        val num = parts[0].toLongOrNull() ?: return null
        val den = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return ticks * 1000L * num / den
    }

    private fun download(url: String, expectedSha256: String): File? {
        val target = File.createTempFile("ffprobe_dl_", ".bin", context.cacheDir)
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                target.delete()
                return null
            }
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                }
            }
            connection.disconnect()
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                Log.w(TAG, "ffprobe checksum mismatch: $actual")
                target.delete()
                null
            } else {
                target
            }
        } catch (e: Exception) {
            Log.w(TAG, "ffprobe download failed", e)
            target.delete()
            null
        }
    }

    private fun cleanupStaleTemp() {
        try {
            val dir = context.cacheDir.resolve("ffprobe-tmp")
            val now = System.currentTimeMillis()
            dir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > 24 * 3600_000L) file.delete()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "EmbeddedChapterReader"
        private const val FFPROBE_VERSION = "7.0.2"
        private const val MAX_COPY_BYTES = 1_500L * 1024L * 1024L // 1.5 GB
        private const val MIN_CHAPTER_MS = 1_000L

        // johnvansickle static builds re-hosted on the project repo, pinned by sha256.
        private const val ARM64_URL =
            "https://github.com/zzkrzxx66/tingxia-android/releases/download/ffprobe-v7.0.2/ffprobe-arm64"
        private const val X86_64_URL =
            "https://github.com/zzkrzxx66/tingxia-android/releases/download/ffprobe-v7.0.2/ffprobe-x86_64"

        private const val ARM64_SHA256 = "d17ae9b4c297d48e2521ba14e417bb0537c6ff77c584cdbcd6bb0d8d0307a2e8"
        private const val X86_64_SHA256 = "4f231a1960d83e403d08f7971e271707bec278a9ae18e21b8b5b03186668450d"
    }
}
