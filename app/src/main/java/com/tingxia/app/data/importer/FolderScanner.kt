package com.tingxia.app.data.importer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale
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
)

@Singleton
class FolderScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataReader: MetadataReader,
) {
    suspend fun scanTree(
        treeUri: Uri,
        onProgress: (ScanProgress) -> Unit = {},
    ): ScannedBook = withContext(Dispatchers.IO) {
        val folderName = queryDisplayName(treeUri) ?: "未命名书籍"
        val audioFiles = mutableListOf<AudioFile>()
        var coverUri: String? = null

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val visitedDirectories = mutableSetOf(rootDocId)
        traverse(treeUri, rootDocId, relativePrefix = "", visitedDirectories = visitedDirectories, depth = 0) { name, uri, docId, mime, size, isDir, relativePath ->
            coroutineContext.ensureActive()
            if (isDir) return@traverse
            val lower = name.lowercase(Locale.ROOT)
            when {
                lower == "cover.jpg" || lower == "cover.jpeg" || lower == "cover.png" ||
                    lower == "cover.webp" -> {
                    if (coverUri == null) coverUri = uri.toString()
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

        val chapters = audioFiles.mapIndexed { index, file ->
            coroutineContext.ensureActive()
            onProgress(ScanProgress(index + 1, file.relativePath))
            val duration = metadataReader.readDurationMs(file.uri)
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

        if (chapters.isEmpty()) {
            error("该文件夹中未找到支持的音频文件")
        }

        val cover = coverUri ?: metadataReader.extractEmbeddedCover(chapters.first().uri)

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
            return ra.size.compareTo(rb.size)
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
            val dir = context.filesDir.resolve("covers").apply { mkdirs() }
            val file = dir.resolve("cover_${uriString.hashCode().toUInt()}.jpg")
            if (!file.exists()) {
                file.writeBytes(art)
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
}
