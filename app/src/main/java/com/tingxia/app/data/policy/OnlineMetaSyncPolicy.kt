package com.tingxia.app.data.policy

/**
 * Undo bookkeeping for syncing online catalogue metadata onto a local book. Chapter-title
 * alignment lives in [ChapterTitleAligner].
 *
 * Everything here is pure Kotlin (no Android / org.json) so it can be unit tested on the JVM,
 * mirroring [com.tingxia.app.data.backup.BackupCodec]'s hand-rolled serialisation.
 */
object OnlineMetaSyncPolicy {

    /** Snapshot of everything a sync overwrites, so the sync can be undone exactly. */
    data class Backup(
        val author: String?,
        val coverPath: String?,
        val description: String?,
        val category: String?,
        val wordCount: Long,
        /** chapter id → custom title before the first sync touched it (null = had none). */
        val chapterCustomTitles: Map<Long, String?>,
    )

    /**
     * The snapshot to persist when syncing. An existing snapshot always wins for book fields —
     * re-syncing must not record already-synced values as "original". Chapters absent from the
     * previous snapshot contribute their current title, which is still their pre-sync value.
     */
    fun mergeBackup(
        existing: Backup?,
        currentAuthor: String?,
        currentCoverPath: String?,
        currentDescription: String?,
        currentCategory: String?,
        currentWordCount: Long,
        chaptersAboutToChange: Map<Long, String?>,
    ): Backup {
        val chapters = LinkedHashMap<Long, String?>()
        existing?.chapterCustomTitles?.let(chapters::putAll)
        chaptersAboutToChange.forEach { (id, title) ->
            if (!chapters.containsKey(id)) chapters[id] = title
        }
        return Backup(
            // An existing snapshot is authoritative even where its fields are null — "the author was
            // empty before the first sync" is exactly the state to restore.
            author = if (existing != null) existing.author else currentAuthor,
            coverPath = if (existing != null) existing.coverPath else currentCoverPath,
            description = if (existing != null) existing.description else currentDescription,
            category = if (existing != null) existing.category else currentCategory,
            wordCount = existing?.wordCount ?: currentWordCount,
            chapterCustomTitles = chapters,
        )
    }

    // ---- serialisation -------------------------------------------------------------------

    private const val VERSION_LINE = "tx-meta-backup/1"
    private const val NULL = "-"
    private const val PRESENT = "!"

    fun encode(backup: Backup): String = buildString {
        append(VERSION_LINE)
        appendField("author", backup.author)
        appendField("cover", backup.coverPath)
        appendField("description", backup.description)
        appendField("category", backup.category)
        appendField("wordCount", backup.wordCount.toString())
        backup.chapterCustomTitles.forEach { (id, title) ->
            append('\n').append("ch\t").append(id).append('\t').append(marker(title))
        }
    }

    fun decode(raw: String?): Backup? {
        if (raw.isNullOrBlank()) return null
        val lines = raw.split('\n')
        if (lines.firstOrNull() != VERSION_LINE) return null
        var author: String? = null
        var cover: String? = null
        var description: String? = null
        var category: String? = null
        var wordCount = 0L
        val chapters = LinkedHashMap<Long, String?>()
        lines.drop(1).forEach { line ->
            val parts = line.split('\t')
            when {
                parts.size == 2 -> {
                    val value = unmarker(parts[1])
                    when (parts[0]) {
                        "author" -> author = value
                        "cover" -> cover = value
                        "description" -> description = value
                        "category" -> category = value
                        "wordCount" -> wordCount = value?.toLongOrNull() ?: 0L
                    }
                }
                parts.size == 3 && parts[0] == "ch" -> {
                    val id = parts[1].toLongOrNull() ?: return@forEach
                    chapters[id] = unmarker(parts[2])
                }
            }
        }
        return Backup(author, cover, description, category, wordCount, chapters)
    }

    private fun StringBuilder.appendField(key: String, value: String?) {
        append('\n').append(key).append('\t').append(marker(value))
    }

    private fun marker(value: String?): String =
        if (value == null) NULL else PRESENT + escape(value)

    private fun unmarker(token: String): String? = when {
        token == NULL -> null
        token.startsWith(PRESENT) -> unescape(token.substring(1))
        else -> null
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\t", "\\t")

    private fun unescape(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    '\\' -> out.append('\\')
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
