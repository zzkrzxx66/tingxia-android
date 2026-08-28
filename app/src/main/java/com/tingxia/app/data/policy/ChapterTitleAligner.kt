package com.tingxia.app.data.policy

import com.tingxia.app.data.model.Chapter

/**
 * Aligns online chapter titles onto local chapters.
 *
 * Position-only alignment breaks as soon as the two sides start at different places (a 片头 file,
 * a missing 序章, a folder that begins at chapter 30), so the default mode pairs by the chapter
 * number parsed out of both sides, and a manual offset covers whatever the parser cannot read.
 *
 * Pure Kotlin so it can be unit tested on the JVM.
 */
object ChapterTitleAligner {

    enum class Mode {
        /** Pair by parsed chapter number; gaps, extras and 上/下 splits sort themselves out. */
        BY_NUMBER,

        /** Pair by position with a fixed drift: local[i] takes remote[i + offset]. */
        BY_OFFSET,
    }

    data class PreviewRow(
        val localLabel: String,
        val remoteTitle: String?,
        val chapterNumber: Int?,
    )

    data class Plan(
        val mode: Mode,
        val offset: Int,
        /** chapter id → title to write. Chapters absent here keep whatever they have. */
        val updates: Map<Long, String>,
        val matchedCount: Int,
        val unmatchedCount: Int,
        val preview: List<PreviewRow>,
    )

    /** Number of preview rows handed to the UI. */
    const val PREVIEW_SIZE = 6

    /**
     * Chapter number parsed from [text], or null when nothing recognisable is there.
     *
     * Priority: an explicit 第…章/集/回/话 marker wins over a bare number, so
     * "001_第412章.mp3" is chapter 412 and not track 1.
     */
    fun parseNumber(text: String): Int? {
        val cleaned = normalizeDigits(stripExtension(text)).trim()
        if (cleaned.isEmpty()) return null
        MARKER.find(cleaned)?.let { match ->
            val body = match.groupValues[1]
            arabic(body)?.let { return it }
            chineseNumeral(body)?.let { return it }
        }
        ARABIC_RUN.find(cleaned)?.let { match ->
            arabic(match.value)?.let { return it }
        }
        // Bare Chinese numerals, e.g. 一百零八 with no 第/章 around them.
        CHINESE_RUN.find(cleaned)?.let { match ->
            chineseNumeral(match.value)?.let { return it }
        }
        return null
    }

    /** Chapter number of a local chapter: the scanned title first, then the file name. */
    fun localNumber(chapter: Chapter): Int? =
        parseNumber(chapter.title) ?: parseNumber(chapter.fileName)

    /**
     * Default alignment: match the numbers on both sides. Remote titles that repeat a number keep
     * the first occurrence; local chapters sharing a number (上/下 splits) all take the same title.
     */
    fun byNumber(local: List<Chapter>, remoteTitles: List<String>): Plan {
        val ordered = local.sortedBy { it.index }
        val remoteByNumber = LinkedHashMap<Int, String>()
        remoteTitles.forEach { title ->
            val number = parseNumber(title) ?: return@forEach
            val clean = title.trim()
            if (clean.isNotEmpty()) remoteByNumber.putIfAbsent(number, clean)
        }
        val updates = LinkedHashMap<Long, String>()
        val preview = mutableListOf<PreviewRow>()
        ordered.forEach { chapter ->
            val number = localNumber(chapter)
            val title = number?.let { remoteByNumber[it] }
            if (title != null) updates[chapter.id] = title
            if (preview.size < PREVIEW_SIZE) {
                preview += PreviewRow(chapter.displayTitle, title, number)
            }
        }
        return Plan(
            mode = Mode.BY_NUMBER,
            offset = 0,
            updates = updates,
            matchedCount = updates.size,
            unmatchedCount = ordered.size - updates.size,
            preview = preview,
        )
    }

    /**
     * Manual drift: local chapter i takes remote title i + [offset]. Out-of-range positions stay
     * untouched instead of wrapping, so an over-shot offset degrades to "fewer chapters renamed".
     */
    fun byOffset(local: List<Chapter>, remoteTitles: List<String>, offset: Int): Plan {
        val ordered = local.sortedBy { it.index }
        val updates = LinkedHashMap<Long, String>()
        val preview = mutableListOf<PreviewRow>()
        ordered.forEachIndexed { position, chapter ->
            val title = remoteTitles.getOrNull(position + offset)?.trim()?.takeIf { it.isNotEmpty() }
            if (title != null) updates[chapter.id] = title
            if (preview.size < PREVIEW_SIZE) {
                preview += PreviewRow(chapter.displayTitle, title, position + offset + 1)
            }
        }
        return Plan(
            mode = Mode.BY_OFFSET,
            offset = offset,
            updates = updates,
            matchedCount = updates.size,
            unmatchedCount = ordered.size - updates.size,
            preview = preview,
        )
    }

    /** Offset range worth offering in the UI, given both list sizes. */
    fun offsetRange(localCount: Int, remoteCount: Int): IntRange {
        if (localCount <= 0 || remoteCount <= 0) return 0..0
        return -(localCount - 1)..(remoteCount - 1)
    }

    /**
     * Drift the number matches imply, used to pre-fill manual mode instead of dropping the user at
     * 0. Median rather than mean so a couple of stray numbers cannot skew it.
     */
    fun suggestedOffset(local: List<Chapter>, remoteTitles: List<String>): Int {
        val remoteIndexByNumber = LinkedHashMap<Int, Int>()
        remoteTitles.forEachIndexed { index, title ->
            parseNumber(title)?.let { remoteIndexByNumber.putIfAbsent(it, index) }
        }
        if (remoteIndexByNumber.isEmpty()) return 0
        val drifts = local.sortedBy { it.index }.mapIndexedNotNull { position, chapter ->
            localNumber(chapter)?.let { number -> remoteIndexByNumber[number]?.minus(position) }
        }
        if (drifts.isEmpty()) return 0
        return drifts.sorted()[drifts.size / 2]
    }

    private fun stripExtension(name: String): String {        val dot = name.lastIndexOf('.')
        if (dot <= 0) return name
        val ext = name.substring(dot + 1)
        // Only strip things that look like an extension, never a trailing ".5" style number.
        return if (ext.length in 1..5 && ext.all { it.isLetterOrDigit() } && ext.any { it.isLetter() }) {
            name.substring(0, dot)
        } else {
            name
        }
    }

    /** Full-width digits (０１２) show up in scraped titles; fold them to ASCII before parsing. */
    private fun normalizeDigits(text: String): String =
        text.map { ch -> if (ch in '０'..'９') ('0' + (ch - '０')) else ch }.joinToString("")

    private fun arabic(text: String): Int? {
        val digits = ARABIC_RUN.find(text)?.value ?: return null
        return digits.trimStart('0').ifEmpty { "0" }.toIntOrNull()?.takeIf { it > 0 }
    }

    /**
     * 零一二三四五六七八九十百千万 (plus 〇 and 两) → Int. Handles both positional forms
     * (一百零八) and the loose 十X / X十 shorthand.
     */
    fun chineseNumeral(text: String): Int? {
        val body = text.filter { it in CHINESE_DIGITS || it in CHINESE_UNITS }
        if (body.isEmpty()) return null
        var total = 0
        var section = 0
        var current = 0
        var sawDigit = false
        body.forEach { ch ->
            when {
                ch in CHINESE_DIGITS -> {
                    current = CHINESE_DIGITS.getValue(ch)
                    sawDigit = true
                }
                ch == '十' -> {
                    section += (if (sawDigit) current else 1) * 10
                    current = 0
                    sawDigit = false
                }
                ch == '百' -> {
                    section += (if (sawDigit) current else 1) * 100
                    current = 0
                    sawDigit = false
                }
                ch == '千' -> {
                    section += (if (sawDigit) current else 1) * 1000
                    current = 0
                    sawDigit = false
                }
                ch == '万' -> {
                    total += (section + current) * 10_000
                    section = 0
                    current = 0
                    sawDigit = false
                }
            }
        }
        val value = total + section + current
        return value.takeIf { it > 0 }
    }

    private val MARKER = Regex("第\\s*([0-9０-９零〇一二三四五六七八九十百千万两]+)\\s*[章集回话節节篇]")
    private val ARABIC_RUN = Regex("[0-9]+")
    private val CHINESE_RUN = Regex("[零〇一二三四五六七八九十百千万两]{1,8}")

    private val CHINESE_DIGITS = mapOf(
        '零' to 0, '〇' to 0,
        '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )
    private val CHINESE_UNITS = setOf('十', '百', '千', '万')
}
