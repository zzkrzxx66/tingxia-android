# fqnovel API 调用文档

本文档描述当前部署的 `fqnovel` 小说与真人有声书接口。

## 1. 服务地址

### 1.1 外部调用地址

```text
https://fq.logix.cc.cd
```

该域名已配置 HTTPS，并通过 Nginx 反向代理到本机的 `fqnovel` 服务：

```text
Client
  -> https://fq.logix.cc.cd
  -> Nginx :443
  -> http://127.0.0.1:9999
  -> fqnovel Docker container
```

外部程序、Android 客户端以及其他服务器应优先使用域名。例如：

```text
https://fq.logix.cc.cd/search?key=三体&page=1&size=20
```

### 1.2 本机调试地址

```text
http://127.0.0.1:9999
```

该地址只适用于运行 `fqnovel` 的服务器本机。Docker 端口当前仅绑定到
`127.0.0.1`，因此不能直接从其他设备访问 `服务器IP:9999`。

下文示例统一使用外部域名：

```bash
BASE_URL='https://fq.logix.cc.cd'
```

## 2. 通用约定

### 2.1 请求格式

- 所有公开接口当前均为 `GET`。
- 响应内容类型为 `application/json`。
- `bookId`、`audioBookId` 和 `itemId` 都应作为字符串处理，避免 JavaScript
  等环境发生 64 位整数精度丢失。
- 请求中文关键词时应进行 URL 编码。

### 2.2 通用响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "serverTime": 1785591097360,
  "success": true
}
```

字段说明：

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | Integer | `0` 表示成功，非 `0` 表示业务或上游错误 |
| `message` | String | 响应消息或错误原因 |
| `data` | Object/Array/null | 具体业务数据 |
| `serverTime` | Long | 服务端 Unix 毫秒时间戳 |
| `success` | Boolean | 是否成功 |

客户端应同时检查：

```text
HTTP 2xx && code == 0 && success == true
```

不能只根据 HTTP 状态判断业务是否成功，因为上游错误也可能通过 HTTP 200
返回。

## 3. 小说接口

### 3.1 搜索小说

```http
GET /search
```

参数：

| 参数 | 必填 | 默认值 | 限制或说明 |
|---|---:|---:|---|
| `key` | 是 | - | 搜索关键词，不能为空 |
| `page` | 否 | `1` | 页码，从 1 开始 |
| `size` | 否 | `20` | 每页数量，范围 `1-50` |
| `tabType` | 否 | `3` | 搜索类型，当前通常使用 `3` |
| `searchId` | 否 | - | 后续翻页可传上一页返回的 `searchId` |

调用示例：

```bash
curl --get "$BASE_URL/search" \
  --data-urlencode 'key=三体' \
  --data 'page=1' \
  --data 'size=20' \
  --data 'tabType=3'
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "books": [
      {
        "bookId": "6983938353934634020",
        "bookName": "三体全集（全三册）",
        "author": "刘慈欣",
        "category": "精品小说",
        "coverUrl": "https://example.com/cover",
        "description": "书籍简介",
        "lastChapterTitle": "最后一章标题",
        "wordCount": 887289
      }
    ],
    "total": 1,
    "hasMore": false,
    "searchId": "search-session-id"
  },
  "serverTime": 1785591097360,
  "success": true
}
```

### 3.2 获取小说详情

```http
GET /book/{bookId}
```

路径参数：

| 参数 | 说明 |
|---|---|
| `bookId` | 搜索接口返回的小说 ID，只允许数字 |

调用示例：

```bash
curl "$BASE_URL/book/6983938353934634020"
```

响应中的 `data`：

```json
{
  "bookId": "6983938353934634020",
  "bookName": "三体全集（全三册）",
  "author": "刘慈欣",
  "description": "书籍简介",
  "coverUrl": "https://example.com/cover",
  "totalChapters": 0,
  "wordNumber": 887289,
  "lastChapterTitle": "最后一章标题",
  "category": "精品小说",
  "status": 1
}
```

### 3.3 获取小说目录

```http
GET /toc/{bookId}
```

调用示例：

```bash
curl "$BASE_URL/toc/6982529841564224526"
```

响应中的 `data` 主要字段：

```json
{
  "catalogData": [],
  "itemDataList": [
    {
      "itemId": "6982735801973113351",
      "title": "第一章",
      "volumeName": "",
      "chapterType": 0,
      "chapterWordNumber": 2100
    }
  ],
  "fieldCacheStatus": {},
  "bookInfo": {},
  "serialCount": 1000
}
```

小说目录是服务端转换后的 DTO，通常使用驼峰字段名。

### 3.4 获取小说章节正文

```http
GET /chapter/{bookId}/{chapterId}
```

调用示例：

```bash
curl "$BASE_URL/chapter/6982529841564224526/6982735801973113351"
```

响应中的 `data`：

```json
{
  "chapterId": "6982735801973113351",
  "bookId": "6982529841564224526",
  "authorName": "作者名",
  "title": "第一章",
  "rawContent": "<p>章节 HTML 正文</p>",
  "txtContent": "章节纯文本正文",
  "chapterIndex": 1,
  "wordCount": 2100,
  "updateTime": 0,
  "prevChapterId": null,
  "nextChapterId": "下一章ID",
  "isFree": true
}
```

## 4. 真人有声书接口

真人有声书需要依次完成：

```text
小说搜索
  -> 查询小说的音色和关联有声书
  -> 获取有声书目录
  -> 获取章节播放信息
  -> 获取并解密 CDN 音频
```

### 4.1 获取音色和关联有声书

```http
GET /audio/tones/{bookId}
```

这里的 `bookId` 是小说 ID，不是有声书 ID。

调用示例：

```bash
curl "$BASE_URL/audio/tones/6982529841564224526"
```

响应中的 `data` 主要字段：

```json
{
  "book_infos": [
    {
      "book_id": "6982529841564224526",
      "book_name": "我在精神病院学斩神",
      "related_audio_bookids": "[7088215107158690853]",
      "tts_status": "1",
      "audio_thumb_uri": "https://example.com/audio-cover"
    }
  ],
  "tts_tones": [
    {
      "id": 80,
      "title": "多角色对话升级版",
      "is_multi_tone": true,
      "tone_gender": 0
    }
  ],
  "audio_tones": null,
  "recommend_tone": 80,
  "relate_novel_bookid_str": "6982529841564224526"
}
```

真人有声书 ID 通常位于：

```text
data.book_infos[].related_audio_bookids
```

注意：`related_audio_bookids` 当前是包含 JSON 数组的字符串，例如：

```json
"[7088215107158690853]"
```

客户端需要再次解析该字符串，并将其中的 ID 保留为字符串。

有些小说没有关联真人有声书；有些关联书可能已下架。

### 4.2 获取真人有声书目录

```http
GET /audio/toc/{audioBookId}
```

这里必须使用上一步取得的真人有声书 ID。

调用示例：

```bash
curl "$BASE_URL/audio/toc/7088215107158690853"
```

响应中的 `data` 主要字段：

```json
{
  "book_info": {
    "book_id": "7088215107158690853",
    "book_name": "我在精神病院学斩神",
    "author": "作者及主播信息",
    "chapter_number": "1766",
    "duration": "1092128.901",
    "copyright_info": "版权信息"
  },
  "catalog_data": null,
  "item_data_list": [
    {
      "item_id": "7088605907067915278",
      "title": "001-黑缎 少年",
      "volume_name": "",
      "chapter_type": "0",
      "chapter_word_number": 0
    }
  ],
  "item_list": [],
  "field_cache_status": {},
  "update_notice_list": []
}
```

音频接口大部分为上游原始 JSON，因此字段通常使用下划线命名。

一本有声书可能包含上千章，响应体可能达到数百 KB，调用方应设置合理的
网络超时并缓存目录。

### 4.3 获取真人有声章节播放信息

```http
GET /audio/play/{audioBookId}/{itemId}
```

查询参数：

| 参数 | 必填 | 默认值 | 说明 |
|---|---:|---:|---|
| `toneId` | 否 | `0` | 真人有声书通常使用 `0` |
| `download` | 否 | `false` | 上游请求类型；设为 `true` 仍返回加密媒体 |

调用示例：

```bash
curl --get \
  "$BASE_URL/audio/play/7088215107158690853/7088605907067915278" \
  --data 'toneId=0' \
  --data 'download=false'
```

响应中的 `data` 是数组：

```json
[
  {
    "main_url": "https://cdn.example.com/encrypted-audio",
    "backup_url": "https://backup.example.com/encrypted-audio",
    "item_id": "7088605907067915278",
    "quality": 48,
    "vid": "video-id",
    "is_encrypt": true,
    "encryption_key": "wrapped-temporary-key",
    "indate": 86400,
    "video_model": "{\"status\":10,\"media_type\":\"audio\",...}"
  }
]
```

`video_model` 本身是 JSON 字符串，需要再次反序列化。解析后的主要结构：

```json
{
  "status": 10,
  "message": "success",
  "media_type": "audio",
  "video_duration": 882.39,
  "url_expire": 1785678378,
  "video_list": [
    {
      "video_meta": {
        "quality": "medium",
        "vtype": "m4a",
        "bitrate": 50087,
        "codec_type": "aac",
        "size": 5524544,
        "audio_profile": "aac_he_v2",
        "audio_sample_rate": "44100"
      },
      "encrypt_info": {
        "encrypt": true,
        "kid": "content-key-id",
        "spade_a": "wrapped-temporary-key",
        "encryption_method": "cenc-aes-ctr"
      }
    }
  ]
}
```

播放信息注意事项：

- `main_url` 和 `backup_url` 是临时 CDN 地址，不应永久写入数据库。
- `indate` 当前通常为 `86400` 秒。
- 应以 `video_model.url_expire` 判断具体地址过期时间。
- CDN 支持 HTTP `Range`，正常返回 `206 Partial Content`。
- CDN 文件使用 CENC AES-CTR 加密，不能直接交给普通 ExoPlayer 播放。
- `download=true` 不代表返回明文，实测仍为加密媒体。
- `encryption_key`、`spade_a` 和 CDN URL 不应输出到公开日志。

仓库中的第一阶段诊断工具可以把已下载的单章加密文件转为普通 M4A：

```bash
python3 tools/fqnovel_audio_poc.py \
  --encryption-key "$ENCRYPTION_KEY" \
  encrypted.m4a \
  decrypted.m4a
```

该工具目前只用于单章技术验证，不是正式的流媒体接口。

## 5. 真人有声书完整调用示例

下面使用 `jq` 演示调用顺序。所有 ID 都按字符串处理。

```bash
BASE_URL='https://fq.logix.cc.cd'

# 1. 搜索小说
curl --get "$BASE_URL/search" \
  --data-urlencode 'key=我在精神病院学斩神' \
  --data 'page=1' \
  --data 'size=1' \
  -o search.json

BOOK_ID=$(jq -r '.data.books[0].bookId' search.json)

# 2. 查找关联的真人有声书
curl "$BASE_URL/audio/tones/$BOOK_ID" -o tones.json
AUDIO_BOOK_ID=$(jq -r '.data.book_infos[0].related_audio_bookids' tones.json \
  | jq -r '.[0] | tostring')

# 3. 获取真人有声书目录
curl "$BASE_URL/audio/toc/$AUDIO_BOOK_ID" -o audio-toc.json
ITEM_ID=$(jq -r '.data.item_data_list[0].item_id' audio-toc.json)

# 4. 获取章节临时播放信息
curl --get "$BASE_URL/audio/play/$AUDIO_BOOK_ID/$ITEM_ID" \
  --data 'toneId=0' \
  --data 'download=false' \
  -o play-info.json

CDN_URL=$(jq -r '.data[0].main_url' play-info.json)
ENCRYPTION_KEY=$(jq -r '.data[0].encryption_key' play-info.json)

# 5. 下载加密媒体；正式实现应支持分段和缓存
curl -L "$CDN_URL" -o encrypted.m4a

# 6. 第一阶段 PoC 解密
python3 tools/fqnovel_audio_poc.py \
  --encryption-key "$ENCRYPTION_KEY" \
  encrypted.m4a \
  decrypted.m4a
```

## 6. 错误处理

例如关联有声书已经下架：

```json
{
  "code": 101121,
  "message": "该书不存在或已停止合作，请去书城阅读新书",
  "data": null,
  "serverTime": 1785511703662,
  "success": false
}
```

调用方至少应处理：

| 情况 | 建议处理 |
|---|---|
| HTTP 非 2xx | 网络或反向代理错误，按退避策略重试 |
| `code != 0` | 展示业务错误，不继续读取 `data` |
| `data == null` | 当作业务失败处理 |
| 没有 `related_audio_bookids` | 该小说没有真人有声版本 |
| 有声目录返回 `101121` | 有声书已下架或停止合作 |
| CDN 返回 403/过期 | 重新调用 `/audio/play` 刷新地址 |
| `main_url` 失败 | 尝试 `backup_url` |
| 解密失败 | 重新获取播放信息，避免使用过期密钥或错误章节信息 |

## 7. 部署与安全说明

- 域名 `https://fq.logix.cc.cd` 当前已经可以从外部调用。
- HTTP 会重定向到 HTTPS。
- Nginx 当前代理读取和发送超时均为 120 秒。
- `fqnovel` 容器仍只绑定到本机端口，这是合理的部署方式；外部访问统一由
  Nginx 提供。
- 当前接口没有 API Key、JWT 或其他客户端认证。
- 如果域名暴露到公网，建议增加认证、限流、访问日志脱敏和健康检查。
- 不要在客户端、日志、崩溃报告或公开仓库中保存 Cookie、设备标识、
  `encryption_key` 或完整临时 CDN URL。
- 内容的使用和分发应遵守平台服务条款及版权要求。

## 8. 接口总览

| 功能 | 方法 | 路径 |
|---|---|---|
| 搜索小说 | GET | `/search` |
| 小说详情 | GET | `/book/{bookId}` |
| 小说目录 | GET | `/toc/{bookId}` |
| 小说正文 | GET | `/chapter/{bookId}/{chapterId}` |
| 音色和真人有声关联 | GET | `/audio/tones/{bookId}` |
| 真人有声目录 | GET | `/audio/toc/{audioBookId}` |
| 真人有声播放信息 | GET | `/audio/play/{audioBookId}/{itemId}` |

