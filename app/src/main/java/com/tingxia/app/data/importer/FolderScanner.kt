package com.tingxia.app.data.importer

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class ScannedChapter(
    val title: String,
    val uri: String,
    val documentId: String?,
    val relativePath: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String?,
    val durationMs: Long,
    val index: Int,
    val stableKey: String,
)

data class ScannedBook(
    val title: String,
    val rootUri: String,
    val coverPath: String?,
    val chapters: List<ScannedChapter>,
    val totalDurationMs: Long,
)

data class ScanProgress(
    val scannedFiles: Int,
    val currentName: String,
    val totalFiles: Int = 0,
)

@Singleton
class FolderScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataReader: MetadataReader,
) {
    suspend fun scanFiles(
        uris: List<Uri>,
        onProgress: (ScanProgress) -> Unit = {},
    ): ScannedBook = withContext(Dispatchers.IO) {
        require(uris.isNotEmpty()) { "请选择至少一个音频文件" }
        val files = uris.distinct().mapNotNull(::queryAudioFile)
            .filter { isAudio(it.name, it.mimeType) }
            .sortedWith { a, b -> naturalCompare(a.name, b.name) }
        require(files.isNotEmpty()) { "未找到支持的音频文件" }
        val completed = AtomicInteger(0)
        val semaphore = Semaphore(METADATA_CONCURRENCY)
        val chapters = coroutineScope {
            files.mapIndexed { index, file ->
                async {
                    semaphore.withPermit {
                        val duration = metadataReader.readDurationMs(file.uri)
                        val done = completed.incrementAndGet()
                        onProgress(ScanProgress(done, file.name, files.size))
                        ScannedChapter(
                            title = stripExtension(file.name),
                            uri = file.uri.toString(),
                            documentId = file.documentId,
                            relativePath = file.name,
                            fileName = file.name,
                            fileSize = file.fileSize,
                            mimeType = file.mimeType,
                            durationMs = duration,
                            index = index,
                            stableKey = ChapterIdentity.stableKey(file.name, file.fileSize, duration),
                        )
                    }
                }
            }.awaitAll()
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(files.joinToString("\n") { it.uri.toString() }.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val title = if (files.size == 1) {
            stripExtension(files.first().name)
        } else {
            commonTitle(files.map { stripExtension(it.name) })
                .ifBlank { "导入的音频（${files.size} 个文件）" }
        }
        ScannedBook(
            title = title,
            rootUri = "multi://files/$digest",
            coverPath = metadataReader.extractEmbeddedCover(chapters.first().uri),
            chapters = chapters,
            totalDurationMs = chapters.sumOf { it.durationMs },
        )
    }

    suspend fun scanTree(
        treeUri: Uri,
        onProgress: (ScanProgress) -> Unit = {},
    ): ScannedBook = withContext(Dispatchers.IO) {
        val folderName = queryDisplayName(treeUri) ?: "未命名书籍"
        val audioFiles = mutableListOf<AudioFile>()
        var coverCandidate: CoverCandidate? = null

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val visitedDirectories = mutableSetOf(rootDocId)
        traverse(treeUri, rootDocId, relativePrefix = "", visitedDirectories = visitedDirectories, depth = 0) { name, uri, docId, mime, size, isDir, relativePath ->
            coroutineContext.ensureActive()
            if (isDir) return@traverse
            val lower = name.lowercase(Locale.ROOT)
            when {
                lower == "cover.jpg" || lower == "cover.jpeg" || lower == "cover.png" ||
                    lower == "cover.webp" -> {
                    val candidate = CoverCandidate(
                        uri = uri.toString(),
                        depth = relativePath.count { it == '/' },
                        path = relativePath.lowercase(Locale.ROOT),
                    )
                    val current = coverCandidate
                    if (current == null || candidate.depth < current.depth ||
                        (candidate.depth == current.depth && candidate.path < current.path)
                    ) {
                        coverCandidate = candidate
                    }
                }
                isAudio(name, mime) -> audioFiles += AudioFile(
                    name = name,
                    uri = uri,
                    documentId = docId,
                    relativePath = relativePath,
                    mimeType = mime,
                    fileSize = size,
                )
            }
        }

        audioFiles.sortWith { a, b -> naturalCompare(a.relativePath, b.relativePath) }

        val completed = AtomicInteger(0)
        val semaphore = Semaphore(METADATA_CONCURRENCY)
        val chapters = coroutineScope {
            audioFiles.mapIndexed { index, file ->
                async {
                    semaphore.withPermit {
                        coroutineContext.ensureActive()
                        val duration = metadataReader.readDurationMs(file.uri)
                        synchronized(completed) {
                            val done = completed.incrementAndGet()
                            onProgress(ScanProgress(done, file.relativePath, audioFiles.size))
                        }
                        val stableKey = ChapterIdentity.stableKey(file.relativePath, file.fileSize, duration)
                        ScannedChapter(
                            title = stripExtension(file.name),
                            uri = file.uri.toString(),
                            documentId = file.documentId,
                            relativePath = file.relativePath,
                            fileName = file.name,
                            fileSize = file.fileSize,
                            mimeType = file.mimeType,
                            durationMs = duration,
                            index = index,
                            stableKey = stableKey,
                        )
                    }
                }
            }.awaitAll()
        }

        if (chapters.isEmpty()) {
            error("该文件夹中未找到支持的音频文件")
        }

        val cover = coverCandidate?.uri ?: metadataReader.extractEmbeddedCover(chapters.first().uri)

        ScannedBook(
            title = folderName,
            rootUri = treeUri.toString(),
            coverPath = cover,
            chapters = chapters,
            totalDurationMs = chapters.sumOf { it.durationMs },
        )
    }

    private data class AudioFile(
        val name: String,
        val uri: Uri,
        val documentId: String?,
        val relativePath: String,
        val mimeType: String?,
        val fileSize: Long,
    )

    private data class CoverCandidate(val uri: String, val depth: Int, val path: String)

    private fun queryAudioFile(uri: Uri): AudioFile? {
        val projection = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val name = nameIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString)
                    ?: uri.lastPathSegment ?: return@use null
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                AudioFile(
                    name = name,
                    uri = uri,
                    documentId = idIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString),
                    relativePath = name,
                    mimeType = mimeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString)
                        ?: context.contentResolver.getType(uri),
                    fileSize = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong) ?: 0L,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun commonTitle(titles: List<String>): String {
        if (titles.isEmpty()) return ""
        var prefix = titles.first()
        titles.drop(1).forEach { title ->
            val max = minOf(prefix.length, title.length)
            var index = 0
            while (index < max && prefix[index].equals(title[index], ignoreCase = true)) index++
            prefix = prefix.take(index)
        }
        return prefix.trim().trimEnd('-', '_', '.', ' ', '第', '章').trim()
    }

    private fun traverse(
        treeUri: Uri,
        parentDocId: String,
        relativePrefix: String,
        visitedDirectories: MutableSet<String>,
        depth: Int,
        visitor: (
            name: String,
            uri: Uri,
            docId: String,
            mime: String?,
            size: Long,
            isDir: Boolean,
            relativePath: String,
        ) -> Unit,
    ) {
        require(depth <= MAX_SCAN_DEPTH) { "目录层级过深，已停止扫描" }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val docId = cursor.getString(idIdx) ?: continue
                val name = cursor.getString(nameIdx) ?: continue
                val mime = cursor.getString(mimeIdx)
                val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val relativePath = if (relativePrefix.isEmpty()) name else "$relativePrefix/$name"
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                visitor(name, docUri, docId, mime, size, isDir, relativePath)
                if (isDir && visitedDirectories.add(docId)) {
                    traverse(treeUri, docId, relativePath, visitedDirectories, depth + 1, visitor)
                }
            }
        }
    }

    private fun queryDisplayName(treeUri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        context.contentResolver.query(
            docUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return treeUri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
    }

    companion object {
        private const val MAX_SCAN_DEPTH = 64
        private const val METADATA_CONCURRENCY = 3
        private val AUDIO_EXT = setOf(
            "mp3", "m4a", "m4b", "aac", "flac", "ogg", "oga", "wav", "opus"
        )

        fun isAudio(name: String, mime: String?): Boolean {
            if (mime?.startsWith("audio/") == true) return true
            val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
            return ext in AUDIO_EXT
        }

        fun stripExtension(name: String): String {
            val i = name.lastIndexOf('.')
            return if (i > 0) name.substring(0, i) else name
        }

        fun naturalCompare(a: String, b: String): Int {
            val ra = tokenize(a)
            val rb = tokenize(b)
            val n = minOf(ra.size, rb.size)
            for (i in 0 until n) {
                val xa = ra[i]
                val xb = rb[i]
                val cmp = if (xa is Long && xb is Long) {
                    xa.compareTo(xb)
                } else {
                    xa.toString().compareTo(xb.toString(), ignoreCase = true)
                }
                if (cmp != 0) return cmp
            }
            val tokenCount = ra.size.compareTo(rb.size)
            if (tokenCount != 0) return tokenCount
            val folded = a.compareTo(b, ignoreCase = true)
            return if (folded != 0) folded else a.compareTo(b)
        }

        private fun tokenize(s: String): List<Any> {
            val out = mutableListOf<Any>()
            var i = 0
            while (i < s.length) {
                if (s[i].isDigit()) {
                    var j = i
                    while (j < s.length && s[j].isDigit()) j++
                    out += s.substring(i, j).toLongOrNull() ?: s.substring(i, j)
                    i = j
                } else {
                    var j = i
                    while (j < s.length && !s[j].isDigit()) j++
                    out += s.substring(i, j)
                    i = j
                }
            }
            return out
        }
    }
}

@Singleton
class MetadataReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun readDurationMs(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun extractEmbeddedCover(uriString: String): String? {
        val uri = Uri.parse(uriString)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val art = retriever.embeddedPicture ?: return null
            if (art.size > MAX_EMBEDDED_COVER_BYTES) return null
            val dir = context.filesDir.resolve("covers").apply { mkdirs() }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(uriString.toByteArray(Charsets.UTF_8))
                .take(10)
                .joinToString("") { "%02x".format(it) }
            val file = dir.resolve("cover_$digest.jpg")
            if (!file.exists()) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(art, 0, art.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > MAX_COVER_EDGE || bounds.outHeight / sample > MAX_COVER_EDGE) {
                    sample *= 2
                }
                val bitmap = BitmapFactory.decodeByteArray(
                    art,
                    0,
                    art.size,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                ) ?: return null
                file.outputStream().buffered().use { output ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, output)
                }
                bitmap.recycle()
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private companion object {
        const val MAX_EMBEDDED_COVER_BYTES = 20 * 1024 * 1024
        const val MAX_COVER_EDGE = 1_200
    }
}
