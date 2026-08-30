package com.tingxia.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class FqSearchBook(
    val bookId: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val description: String?,
    val category: String?,
    val wordCount: Long,
    /** A human-narrated edition exists; otherwise only TTS voices can read it. */
    val hasRealAudio: Boolean = false,
    val ttsEnabled: Boolean = true,
    val score: String? = null,
    val listenCount: Long = 0L,
    /** null when upstream did not say whether the book is complete. */
    val finished: Boolean? = null,
    val audioCoverUrl: String? = null,
) {
    val displayCoverUrl: String? get() = coverUrl ?: audioCoverUrl
}

/** One page of search results; [searchId] and [hasMore] drive "load more". */
data class FqSearchPage(
    val books: List<FqSearchBook>,
    val hasMore: Boolean,
    val searchId: String?,
    val total: Int,
)

/** A human-narrated edition of a novel (one cast of 主播). */
data class FqAudioTone(val audioBookId: String, val title: String, val iconUrl: String?)

/** A synthesized voice; works for any novel, including those without a narrated edition. */
data class FqTtsTone(
    val toneId: Int,
    val title: String,
    val description: String?,
    val iconUrl: String?,
    val multiRole: Boolean,
    val gender: Int,
)

data class FqVoices(
    val audioBooks: List<FqAudioTone>,
    val ttsTones: List<FqTtsTone>,
    val recommendToneId: Int?,
    val coverUrl: String?,
)

/**
 * What the user picked to listen with. The two flavours differ only in which id
 * goes into the play request:
 *
 * - [Real]: the narrated edition's own book id, toneId 0.
 * - [Tts]: the novel's book id plus a synthesized voice id.
 */
sealed interface FqVoiceChoice {
    val audioBookId: String
    val toneId: String
    val label: String
    val iconUrl: String?
    val isTts: Boolean

    data class Real(val tone: FqAudioTone) : FqVoiceChoice {
        override val audioBookId: String get() = tone.audioBookId
        override val toneId: String get() = "0"
        override val label: String get() = tone.title
        override val iconUrl: String? get() = tone.iconUrl
        override val isTts: Boolean get() = false
    }

    data class Tts(val novelBookId: String, val tone: FqTtsTone) : FqVoiceChoice {
        override val audioBookId: String get() = novelBookId
        override val toneId: String get() = tone.toneId.toString()
        override val label: String get() = tone.title
        override val iconUrl: String? get() = tone.iconUrl
        override val isTts: Boolean get() = true
    }
}

data class FqAudioChapter(val itemId: String, val title: String, val index: Int)

/** Book-level facts that would otherwise need the whole catalogue parsed. */
data class FqAudioMeta(
    val audioBookId: String,
    val bookName: String?,
    val author: String?,
    val description: String?,
    val coverUrl: String?,
    val chapterCount: Int,
    val totalDurationMs: Long,
    val score: String?,
    val listenCount: Long,
    val lastChapterTitle: String?,
    val lastChapterItemId: String?,
    val lastChapterUpdateTime: Long,
    val finished: Boolean?,
    val novelBookId: String?,
)

/** A discover-page section: one title plus the query behind it. */
data class FqDiscoverSection(
    val title: String,
    val query: String,
    val books: List<FqSearchBook>,
)

/** Chapter text for the read-along drawer. */
data class FqChapterText(val itemId: String, val title: String, val text: String, val wordCount: Int)

class FqNovelApi(
    private val baseUrl: String = FqEndpoints.baseUrl,
    private val apiToken: String = FqEndpoints.apiToken,
) {

    suspend fun search(
        keyword: String,
        page: Int = 1,
        size: Int = 20,
        searchId: String? = null,
    ): FqSearchPage = withContext(Dispatchers.IO) {
        val query = buildString {
            append("/search?key=").append(URLEncoder.encode(keyword, "UTF-8"))
            append("&page=").append(page)
            append("&size=").append(size)
            append("&tabType=3")
            if (!searchId.isNullOrBlank()) append("&searchId=").append(URLEncoder.encode(searchId, "UTF-8"))
        }
        val data = get(query).optJSONObject("data")
        val books = data?.optJSONArray("books").toBookList()
        FqSearchPage(
            books = books,
            hasMore = data?.optBoolean("hasMore", books.size >= size) ?: false,
            searchId = data?.optString("searchId")?.takeIf { it.isNotBlank() },
            total = data?.optInt("total", books.size) ?: books.size,
        )
    }

    /** Real aggregated hot audio books for the online-find discover section. */
    suspend fun hotAudioBooks(): List<FqSearchBook> = withContext(Dispatchers.IO) {
        get("/search/hot").optJSONArray("data").toBookList()
    }

    /** Discover sections (hot / 玄幻 / 都市 / 悬疑 …), each with its own search term. */
    suspend fun discoverSections(): List<FqDiscoverSection> = withContext(Dispatchers.IO) {
        val sections = get("/discover/sections").optJSONArray("data") ?: JSONArray()
        buildList {
            for (i in 0 until sections.length()) {
                val item = sections.optJSONObject(i) ?: continue
                val title = item.optString("title").takeIf { it.isNotBlank() } ?: continue
                add(
                    FqDiscoverSection(
                        title = title,
                        query = item.optString("query", title),
                        books = item.optJSONArray("books").toBookList(),
                    ),
                )
            }
        }
    }

    /** Narrated editions plus TTS voices for one novel. */
    suspend fun voices(bookId: String): FqVoices = withContext(Dispatchers.IO) {
        val data = get("/audio/voices/$bookId").optJSONObject("data") ?: JSONObject()
        val audioBooks = buildList {
            val array = data.optJSONArray("audioBooks") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("audioBookId")
                if (id.isBlank()) continue
                add(
                    FqAudioTone(
                        audioBookId = id,
                        title = item.optString("title").takeIf { it.isNotBlank() } ?: "真人有声",
                        iconUrl = item.optString("iconUrl").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
        val ttsTones = buildList {
            val array = data.optJSONArray("ttsTones") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val toneId = item.optInt("toneId", 0)
                if (toneId <= 0) continue
                add(
                    FqTtsTone(
                        toneId = toneId,
                        title = item.optString("title").takeIf { it.isNotBlank() } ?: "AI 朗读",
                        description = item.optString("description").takeIf { it.isNotBlank() },
                        iconUrl = item.optString("iconUrl").takeIf { it.isNotBlank() },
                        multiRole = item.optBoolean("multiRole", false),
                        gender = item.optInt("gender", 0),
                    ),
                )
            }
        }
        FqVoices(
            audioBooks = audioBooks,
            ttsTones = ttsTones,
            recommendToneId = data.optInt("recommendToneId", 0).takeIf { it > 0 },
            coverUrl = data.optString("coverUrl").takeIf { it.isNotBlank() },
        )
    }

    /**
     * Chapter list for a listening choice. Narrated editions have their own catalogue;
     * TTS reads the novel's own table of contents.
     */
    suspend fun chapters(choice: FqVoiceChoice): List<FqAudioChapter> =
        if (choice.isTts) novelChapters(choice.audioBookId) else audioChapters(choice.audioBookId)

    suspend fun audioChapters(audioBookId: String): List<FqAudioChapter> = withContext(Dispatchers.IO) {
        val items = get("/audio/toc/$audioBookId").optJSONObject("data")?.optJSONArray("item_data_list")
        items.toChapterList()
    }

    /** Text-novel table of contents; also the chapter list used by TTS playback. */
    suspend fun novelChapters(bookId: String): List<FqAudioChapter> = withContext(Dispatchers.IO) {
        val items = get("/toc/$bookId").optJSONObject("data")?.optJSONArray("item_data_list")
        items.toChapterList()
    }

    suspend fun audioMeta(audioBookId: String): FqAudioMeta? = withContext(Dispatchers.IO) {
        val data = get("/audio/meta/$audioBookId").optJSONObject("data") ?: return@withContext null
        FqAudioMeta(
            audioBookId = data.optString("audioBookId", audioBookId),
            bookName = data.optString("bookName").takeIf { it.isNotBlank() },
            author = data.optString("author").takeIf { it.isNotBlank() },
            description = data.optString("description").takeIf { it.isNotBlank() },
            coverUrl = data.optString("coverUrl").takeIf { it.isNotBlank() },
            chapterCount = data.optInt("chapterCount", 0),
            totalDurationMs = data.optLong("totalDurationMs", 0L),
            score = data.optString("score").takeIf { it.isNotBlank() },
            listenCount = data.optLong("listenCount", 0L),
            lastChapterTitle = data.optString("lastChapterTitle").takeIf { it.isNotBlank() },
            lastChapterItemId = data.optString("lastChapterItemId").takeIf { it.isNotBlank() },
            lastChapterUpdateTime = data.optLong("lastChapterUpdateTime", 0L),
            finished = if (data.has("finished")) data.optBoolean("finished") else null,
            novelBookId = data.optString("novelBookId").takeIf { it.isNotBlank() },
        )
    }

    /** Chapter duration in ms without downloading the audio. 0 when upstream has none. */
    suspend fun chapterDurationMs(
        audioBookId: String,
        itemId: String,
        toneId: String = "0",
    ): Long = withContext(Dispatchers.IO) {
        get("/audio/duration/$audioBookId/$itemId?toneId=$toneId").optLong("data", 0L)
    }

    /** Chapter text of the linked novel, for the read-along drawer. */
    suspend fun chapterText(bookId: String, itemId: String): FqChapterText? = withContext(Dispatchers.IO) {
        val data = get("/chapter/$bookId/$itemId").optJSONObject("data") ?: return@withContext null
        val text = data.optString("txtContent")
        if (text.isBlank()) return@withContext null
        FqChapterText(
            itemId = itemId,
            title = data.optString("title").takeIf { it.isNotBlank() } ?: "",
            text = text,
            wordCount = data.optInt("wordCount", text.length),
        )
    }

    /**
     * Ask the stream service to prepare a chapter ahead of time. Returns without
     * waiting for the transcode; failures are not worth surfacing to the user.
     */
    suspend fun warm(audioBookId: String, itemId: String, toneId: String = "0"): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { get("/audio/warm/$audioBookId/$itemId?toneId=$toneId", requireCode = false) }
                .isSuccess
        }

    /** Playable URL for one chapter. */
    fun streamUrl(audioBookId: String, itemId: String, toneId: String = FqEndpoints.DEFAULT_TONE): String =
        FqEndpoints.streamUrl(audioBookId, itemId, toneId)

    private fun JSONArray?.toBookList(): List<FqSearchBook> {
        val array = this ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                parseBook(item)?.let(::add)
            }
        }
    }

    private fun JSONArray?.toChapterList(): List<FqAudioChapter> {
        val array = this ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("item_id").takeIf { it.isNotBlank() }
                    ?: item.optString("itemId").takeIf { it.isNotBlank() }
                    ?: continue
                val title = item.optString("title").takeIf { it.isNotBlank() } ?: "第 ${size + 1} 章"
                add(FqAudioChapter(id, title, size))
            }
        }
    }

    private fun parseBook(item: JSONObject): FqSearchBook? {
        val id = item.optString("bookId")
        if (id.isBlank()) return null
        return FqSearchBook(
            bookId = id,
            title = item.optString("bookName", "未命名"),
            author = item.optString("author").takeIf { it.isNotBlank() },
            coverUrl = item.optString("coverUrl").takeIf { it.isNotBlank() },
            description = item.optString("description").takeIf { it.isNotBlank() },
            category = item.optString("category").takeIf { it.isNotBlank() },
            wordCount = item.optLong("wordCount").coerceAtLeast(0L),
            hasRealAudio = item.optBoolean("hasRealAudio", false),
            ttsEnabled = item.optBoolean("ttsEnabled", true),
            score = item.optString("score").takeIf { it.isNotBlank() },
            listenCount = item.optLong("listenCount").coerceAtLeast(0L),
            finished = if (item.has("finished")) item.optBoolean("finished") else null,
            audioCoverUrl = item.optString("audioCoverUrl").takeIf { it.isNotBlank() },
        )
    }

    private fun get(path: String, requireCode: Boolean = true): JSONObject {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 45_000
            setRequestProperty("Accept", "application/json")
            if (apiToken.isNotBlank()) setRequestProperty("X-Api-Key", apiToken)
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) error("番茄服务请求失败（$status）")
            val root = JSONObject(body)
            if (requireCode && (root.optInt("code", -1) != 0 || root.optBoolean("success", true).not())) {
                error(root.optString("message", "番茄服务返回错误"))
            }
            return root
        } finally {
            connection.disconnect()
        }
    }
}
