package com.tingxia.app.data.backup

import com.tingxia.app.data.model.ShelfFilter
import com.tingxia.app.data.model.ShelfSort
import com.tingxia.app.data.repo.PlaybackErrorPolicy
import com.tingxia.app.data.repo.PreferencesSnapshot
import com.tingxia.app.data.repo.ThemeMode

object BackupCodec {
    const val CURRENT_FORMAT_VERSION = 1

    fun encode(document: BackupDocument): String = Writer().apply {
        obj {
            field("formatVersion", document.formatVersion)
            field("exportedAt", document.exportedAt)
            field("settings") { settings(document.settings) }
            field("books") {
                array(document.books) { book(it) }
            }
        }
    }.result()

    fun decode(json: String): BackupDocument {
        val root = Parser(json).parse().asObject()
        val version = root.int("formatVersion")
        require(version == CURRENT_FORMAT_VERSION) { "不支持的备份格式：$version" }
        return BackupDocument(
            formatVersion = version,
            exportedAt = root.long("exportedAt"),
            settings = settings(root.obj("settings")),
            books = root.array("books").map { book(it.asObject()) },
        )
    }

    private fun Writer.settings(settings: PreferencesSnapshot) = obj {
        field("themeMode", settings.themeMode.name)
        field("defaultSpeed", settings.defaultSpeed)
        field("shelfSort", settings.shelfSort.name)
        field("shelfFilter", settings.shelfFilter.name)
        field("playbackErrorPolicy", settings.playbackErrorPolicy.name)
    }

    private fun settings(map: Map<String, Value>) = PreferencesSnapshot(
        themeMode = enumValue(map.string("themeMode"), ThemeMode.SYSTEM),
        defaultSpeed = map.number("defaultSpeed").toFloat().coerceIn(0.25f, 4f),
        shelfSort = enumValue(map.string("shelfSort"), ShelfSort.RECENT),
        shelfFilter = enumValue(map.string("shelfFilter"), ShelfFilter.ALL),
        playbackErrorPolicy = enumValue(map.string("playbackErrorPolicy"), PlaybackErrorPolicy.STOP),
    )

    private fun Writer.book(book: BackupBook) = obj {
        field("title", book.title)
        field("author", book.author)
        field("rootUri", book.rootUri)
        field("totalDurationMs", book.totalDurationMs)
        field("lastPlayedAt", book.lastPlayedAt)
        field("currentChapterKey", book.currentChapterKey)
        field("currentPositionMs", book.currentPositionMs)
        field("listenedDurationMs", book.listenedDurationMs)
        field("createdAt", book.createdAt)
        field("playbackSpeed", book.playbackSpeed)
        field("autoPlayNext", book.autoPlayNext)
        field("lastScannedAt", book.lastScannedAt)
        field("skipIntroMs", book.skipIntroMs)
        field("skipOutroMs", book.skipOutroMs)
        field("coverUri", book.coverUri)
        field("chapters") { array(book.chapters) { chapter(it) } }
        field("bookmarks") { array(book.bookmarks) { bookmark(it) } }
    }

    private fun book(map: Map<String, Value>): BackupBook = BackupBook(
        title = map.string("title").takeIf { it.isNotBlank() } ?: "未命名书籍",
        author = map.stringOrNull("author"),
        rootUri = map.string("rootUri"),
        totalDurationMs = map.long("totalDurationMs").coerceAtLeast(0L),
        lastPlayedAt = map.long("lastPlayedAt").coerceAtLeast(0L),
        currentChapterKey = map.stringOrNull("currentChapterKey"),
        currentPositionMs = map.long("currentPositionMs").coerceAtLeast(0L),
        listenedDurationMs = map.long("listenedDurationMs").coerceAtLeast(0L),
        createdAt = map.long("createdAt").coerceAtLeast(0L),
        playbackSpeed = map.numberOrNull("playbackSpeed")?.toFloat()?.coerceIn(0.25f, 4f),
        autoPlayNext = map.booleanOrDefault("autoPlayNext", true),
        lastScannedAt = map.longOrDefault("lastScannedAt", 0L).coerceAtLeast(0L),
        skipIntroMs = map.longOrDefault("skipIntroMs", 0L).coerceIn(0L, 300_000L),
        skipOutroMs = map.longOrDefault("skipOutroMs", 0L).coerceIn(0L, 300_000L),
        coverUri = map.stringOrNull("coverUri")?.takeIf { it.startsWith("content:") || it.startsWith("http") },
        chapters = map.array("chapters").map { chapter(it.asObject()) },
        bookmarks = map.array("bookmarks").map { bookmark(it.asObject()) },
    )

    private fun Writer.chapter(chapter: BackupChapter) = obj {
        field("key", chapter.key)
        field("title", chapter.title)
        field("uri", chapter.uri)
        field("relativePath", chapter.relativePath)
        field("fileName", chapter.fileName)
        field("fileSize", chapter.fileSize)
        field("documentId", chapter.documentId)
        field("mimeType", chapter.mimeType)
        field("durationMs", chapter.durationMs)
        field("index", chapter.index)
        field("customTitle", chapter.customTitle)
        field("completionState", chapter.completionState.coerceIn(0, 2))
        field("completedAt", chapter.completedAt)
    }

    private fun chapter(map: Map<String, Value>) = BackupChapter(
        key = map.string("key"),
        title = map.string("title"),
        uri = map.string("uri"),
        relativePath = map.string("relativePath"),
        fileName = map.string("fileName"),
        fileSize = map.long("fileSize").coerceAtLeast(0L),
        documentId = map.stringOrNull("documentId"),
        mimeType = map.stringOrNull("mimeType"),
        durationMs = map.long("durationMs").coerceAtLeast(0L),
        index = map.int("index").coerceAtLeast(0),
        customTitle = map.stringOrNull("customTitle"),
        completionState = map.intOrDefault("completionState", 0).coerceIn(0, 2),
        completedAt = map.longOrNull("completedAt"),
    )

    private fun Writer.bookmark(bookmark: BackupBookmark) = obj {
        field("chapterKey", bookmark.chapterKey)
        field("positionMs", bookmark.positionMs.coerceAtLeast(0L))
        field("note", bookmark.note)
        field("createdAt", bookmark.createdAt.coerceAtLeast(0L))
    }

    private fun bookmark(map: Map<String, Value>) = BackupBookmark(
        chapterKey = map.string("chapterKey"),
        positionMs = map.long("positionMs").coerceAtLeast(0L),
        note = map.stringOrNull("note"),
        createdAt = map.long("createdAt").coerceAtLeast(0L),
    )

    private inline fun <reified T : Enum<T>> enumValue(value: String, fallback: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)

    private sealed interface Value {
        data class Str(val value: String) : Value
        data class Num(val value: Double) : Value
        data class Bool(val value: Boolean) : Value
        data class Obj(val value: Map<String, Value>) : Value
        data class Arr(val value: List<Value>) : Value
        data object Null : Value

        fun asObject(): Map<String, Value> = (this as? Obj)?.value ?: error("备份对象格式错误")
    }

    private fun Map<String, Value>.value(key: String): Value = get(key) ?: error("备份缺少字段：$key")
    private fun Map<String, Value>.string(key: String): String = (value(key) as? Value.Str)?.value ?: error("字段类型错误：$key")
    private fun Map<String, Value>.stringOrNull(key: String): String? = when (val item = get(key)) {
        is Value.Str -> item.value
        null, Value.Null -> null
        else -> error("字段类型错误：$key")
    }
    private fun Map<String, Value>.number(key: String): Double = (value(key) as? Value.Num)?.value ?: error("字段类型错误：$key")
    private fun Map<String, Value>.numberOrNull(key: String): Double? = when (val item = get(key)) {
        is Value.Num -> item.value
        null, Value.Null -> null
        else -> error("字段类型错误：$key")
    }
    private fun Map<String, Value>.long(key: String): Long = number(key).toLong()
    private fun Map<String, Value>.longOrNull(key: String): Long? = numberOrNull(key)?.toLong()
    private fun Map<String, Value>.longOrDefault(key: String, fallback: Long): Long = longOrNull(key) ?: fallback
    private fun Map<String, Value>.int(key: String): Int = long(key).toInt()
    private fun Map<String, Value>.intOrDefault(key: String, fallback: Int): Int = longOrNull(key)?.toInt() ?: fallback
    private fun Map<String, Value>.booleanOrDefault(key: String, fallback: Boolean): Boolean = when (val item = get(key)) {
        is Value.Bool -> item.value
        null, Value.Null -> fallback
        else -> error("字段类型错误：$key")
    }
    private fun Map<String, Value>.obj(key: String): Map<String, Value> = value(key).asObject()
    private fun Map<String, Value>.array(key: String): List<Value> = (value(key) as? Value.Arr)?.value ?: error("字段类型错误：$key")

    private class Writer {
        private val output = StringBuilder()
        private var needsComma = false

        fun result(): String = output.toString()

        fun obj(block: Writer.() -> Unit) {
            output.append('{')
            val previous = needsComma
            needsComma = false
            block()
            needsComma = previous
            output.append('}')
        }

        fun <T> array(values: List<T>, block: Writer.(T) -> Unit) {
            output.append('[')
            values.forEachIndexed { index, item ->
                if (index > 0) output.append(',')
                block(item)
            }
            output.append(']')
        }

        fun field(name: String, value: Any?) {
            if (needsComma) output.append(',') else needsComma = true
            string(name)
            output.append(':')
            writeValue(value)
        }

        fun field(name: String, block: Writer.() -> Unit) {
            if (needsComma) output.append(',') else needsComma = true
            string(name)
            output.append(':')
            block()
        }

        private fun writeValue(value: Any?) = when (value) {
            null -> output.append("null")
            is String -> string(value)
            is Boolean -> output.append(value)
            is Int, is Long, is Float, is Double -> output.append(value)
            else -> error("不支持的备份字段类型")
        }

        private fun string(value: String) {
            output.append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> output.append("\\\\")
                    '"' -> output.append("\\\"")
                    '\b' -> output.append("\\b")
                    '\u000C' -> output.append("\\f")
                    '\n' -> output.append("\\n")
                    '\r' -> output.append("\\r")
                    '\t' -> output.append("\\t")
                    in '\u0000'..'\u001f' -> output.append("\\u%04x".format(char.code))
                    else -> output.append(char)
                }
            }
            output.append('"')
        }
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun parse(): Value {
            val value = value()
            whitespace()
            require(index == text.length) { "备份 JSON 尾部包含无效内容" }
            return value
        }

        private fun value(): Value {
            whitespace()
            require(index < text.length) { "备份 JSON 意外结束" }
            return when (text[index]) {
                '{' -> objectValue()
                '[' -> arrayValue()
                '"' -> Value.Str(string())
                't' -> literal("true", Value.Bool(true))
                'f' -> literal("false", Value.Bool(false))
                'n' -> literal("null", Value.Null)
                '-', in '0'..'9' -> number()
                else -> error("备份 JSON 字段无效")
            }
        }

        private fun objectValue(): Value.Obj {
            index++
            val values = linkedMapOf<String, Value>()
            whitespace()
            if (take('}')) return Value.Obj(values)
            while (true) {
                whitespace()
                val key = string()
                whitespace()
                require(take(':')) { "备份 JSON 缺少冒号" }
                values[key] = value()
                whitespace()
                if (take('}')) break
                require(take(',')) { "备份 JSON 缺少逗号" }
            }
            return Value.Obj(values)
        }

        private fun arrayValue(): Value.Arr {
            index++
            val values = mutableListOf<Value>()
            whitespace()
            if (take(']')) return Value.Arr(values)
            while (true) {
                values += value()
                whitespace()
                if (take(']')) break
                require(take(',')) { "备份 JSON 数组缺少逗号" }
            }
            return Value.Arr(values)
        }

        private fun string(): String {
            require(take('"')) { "备份 JSON 字符串无效" }
            val result = StringBuilder()
            while (index < text.length) {
                val char = text[index++]
                when (char) {
                    '"' -> return result.toString()
                    '\\' -> {
                        require(index < text.length) { "备份 JSON 转义无效" }
                        when (val escaped = text[index++]) {
                            '"', '\\', '/' -> result.append(escaped)
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000c')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> {
                                require(index + 4 <= text.length) { "备份 JSON Unicode 转义无效" }
                                result.append(text.substring(index, index + 4).toInt(16).toChar())
                                index += 4
                            }
                            else -> error("备份 JSON 转义无效")
                        }
                    }
                    else -> result.append(char)
                }
            }
            error("备份 JSON 字符串意外结束")
        }

        private fun number(): Value.Num {
            val start = index
            if (text[index] == '-') index++
            while (index < text.length && text[index].isDigit()) index++
            if (index < text.length && text[index] == '.') {
                index++
                while (index < text.length && text[index].isDigit()) index++
            }
            if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
                index++
                if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
                while (index < text.length && text[index].isDigit()) index++
            }
            return Value.Num(text.substring(start, index).toDoubleOrNull() ?: error("备份 JSON 数字无效"))
        }

        private fun literal(expected: String, value: Value): Value {
            require(text.regionMatches(index, expected, 0, expected.length)) { "备份 JSON 字面量无效" }
            index += expected.length
            return value
        }

        private fun whitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        private fun take(char: Char): Boolean = if (index < text.length && text[index] == char) {
            index++
            true
        } else {
            false
        }
    }
}
