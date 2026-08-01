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
)

data class FqAudioTone(val audioBookId: String, val title: String, val iconUrl: String?)

data class FqAudioChapter(val itemId: String, val title: String, val index: Int)

class FqNovelApi(
    private val baseUrl: String = "https://fq.logix.cc.cd",
) {
    suspend fun search(keyword: String, page: Int = 1, size: Int = 20): List<FqSearchBook> = withContext(Dispatchers.IO) {
        val root = get("/search?key=${URLEncoder.encode(keyword, "UTF-8")}&page=$page&size=$size&tabType=3")
        val books = root.optJSONObject("data")?.optJSONArray("books") ?: JSONArray()
        buildList {
            for (i in 0 until books.length()) {
                val item = books.optJSONObject(i) ?: continue
                val id = item.optString("bookId")
                if (id.isNotBlank()) add(
                    FqSearchBook(
                        bookId = id,
                        title = item.optString("bookName", "未命名"),
                        author = item.optString("author").takeIf { it.isNotBlank() },
                        coverUrl = item.optString("coverUrl").takeIf { it.isNotBlank() },
                        description = item.optString("description").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    suspend fun tones(bookId: String): List<FqAudioTone> = withContext(Dispatchers.IO) {
        val root = get("/audio/tones/$bookId")
        val tones = root.optJSONObject("data")?.optJSONArray("audio_tones") ?: JSONArray()
        buildList {
            for (i in 0 until tones.length()) {
                val item = tones.optJSONObject(i) ?: continue
                val id = item.optString("abook_id")
                if (id.isNotBlank()) add(FqAudioTone(id, item.optString("title", "真人有声"), item.optString("icon_url").takeIf { it.isNotBlank() }))
            }
        }
    }

    suspend fun chapters(audioBookId: String): List<FqAudioChapter> = withContext(Dispatchers.IO) {
        val root = get("/audio/toc/$audioBookId")
        val items = root.optJSONObject("data")?.optJSONArray("item_data_list") ?: JSONArray()
        buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("item_id")
                if (id.isNotBlank()) add(FqAudioChapter(id, item.optString("title", "第 ${i + 1} 章"), i))
            }
        }
    }

    private fun get(path: String): JSONObject {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 45_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) error("番茄服务请求失败（$status）")
            val root = JSONObject(body)
            if (root.optInt("code", -1) != 0 || root.optBoolean("success", true).not()) {
                error(root.optString("message", "番茄服务返回错误"))
            }
            return root
        } finally {
            connection.disconnect()
        }
    }
}
