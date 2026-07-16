package com.tingxia.app.data.importer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ScannedChapter(
    val title: String,
    val uri: String,
    val fileName: String,
    val durationMs: Long,
    val index: Int,
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
        val audioFiles = mutableListOf<Pair<String, Uri>>()
        var coverUri: String? = null

        traverse(treeUri) { name, uri, mime, isDir ->
            if (isDir) return@traverse
            val lower = name.lowercase(Locale.ROOT)
            when {
                lower == "cover.jpg" || lower == "cover.jpeg" || lower == "cover.png" ||
                    lower == "cover.webp" -> {
                    if (coverUri == null) coverUri = uri.toString()
                }
                isAudio(name, mime) -> audioFiles += name to uri
            }
        }

        audioFiles.sortWith { a, b -> naturalCompare(a.first, b.first) }

        val chapters = audioFiles.mapIndexed { index, (name, uri) ->
            onProgress(ScanProgress(index + 1, name))
            val duration = metadataReader.readDurationMs(uri)
            val title = stripExtension(name)
            ScannedChapter(
                title = title,
                uri = uri.toString(),
                fileName = name,
                durationMs = duration,
                index = index,
            )
        }

        if (chapters.isEmpty()) {
            error("该文件夹中未找到支持的音频文件")
        }

        // Prefer embedded album art of first track if no cover file
        val cover = coverUri ?: metadataReader.extractEmbeddedCover(chapters.first().uri)?.let {
            // saved as file path by metadata reader
            it
        }

        ScannedBook(
            title = folderName,
            rootUri = treeUri.toString(),
            coverPath = cover,
            chapters = chapters,
            totalDurationMs = chapters.sumOf { it.durationMs },
        )
    }

    private fun traverse(
        treeUri: Uri,
        visitor: (name: String, uri: Uri, mime: String?, isDir: Boolean) -> Unit,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val docId = cursor.getString(idIdx)
                val name = cursor.getString(nameIdx) ?: continue
                val mime = cursor.getString(mimeIdx)
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                visitor(name, docUri, mime, isDir)
                if (isDir) {
                    // one-level nested folders: treat nested audio as part of book (common layout)
                    traverseChildren(treeUri, docId, visitor)
                }
            }
        }
    }

    private fun traverseChildren(
        treeUri: Uri,
        parentDocId: String,
        visitor: (name: String, uri: Uri, mime: String?, isDir: Boolean) -> Unit,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val docId = cursor.getString(idIdx)
                val name = cursor.getString(nameIdx) ?: continue
                val mime = cursor.getString(mimeIdx)
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                if (!isDir) {
                    visitor(name, docUri, mime, false)
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
        // fallback: last path segment
        return treeUri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
    }

    companion object {
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

        /**
         * Numeric-aware natural sort: 第2章 before 第10章.
         */
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

    /**
     * Extract embedded album art to app files dir; returns absolute path or null.
     */
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
