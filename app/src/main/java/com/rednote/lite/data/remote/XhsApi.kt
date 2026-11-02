package com.rednote.lite.data.remote

import com.rednote.lite.data.model.ApiEnvelope
import com.rednote.lite.data.model.CommentsPage
import com.rednote.lite.data.model.FeedData
import com.rednote.lite.data.model.NoteDetailResponse
import com.rednote.lite.data.sign.SignEngine
import com.rednote.lite.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 小红书网页版 API 客户端。
 *
 * 实测结论（详见 ref/xhs/FINDINGS.md）：
 *  - `/api/sns/web/v1/search/notes` 只校验登录态，不校验签名；
 *  - feed / homefeed / comment 等接口在网关层（406）要求真实 mnsv2 签名，
 *    签名由隐藏 WebView 内的官方 JS 生成。
 */
class XhsApi(private val signEngine: SignEngine) {

    companion object {
        private const val TAG = "XhsApi"
        const val BASE = "https://edith.xiaohongshu.com"
        const val WEB_ORIGIN = "https://www.xiaohongshu.com"
        private const val REFERER = "$WEB_ORIGIN/explore"
        private const val CONTENT_TYPE = "application/json;charset=UTF-8"
    }

    val json: Json = appJson

    private val mediaType = CONTENT_TYPE.toMediaType()

    /** 最近一次失败原因，供界面展示（比统一的「请确认已登录」有用得多）。 */
    @Volatile
    var lastError: String = ""
        private set

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * 原样透传 WebView 里该域下的**全部** Cookie。
     *
     * 老实现只挑 a1/webId/web_session 等 6 个，会把 acw_tc、websectiga 这类
     * 风控 cookie 丢掉，网关照样判异常。全量透传最接近真实浏览器。
     */
    private fun cookieHeader(): String {
        val raw = signEngine.cookie() ?: ""
        val parts = raw.split(";").map { it.trim() }.filter { it.contains("=") }.toMutableList()
        if (parts.none { it.startsWith("xsecappid=") }) parts += "xsecappid=xhs-pc-web"
        if (parts.none { it.startsWith("webBuild=") }) parts += "webBuild=6.7.1"
        return parts.joinToString("; ")
    }

    private fun fail(msg: String): String? {
        lastError = msg
        AppLog.w(TAG, msg)
        return null
    }

    /**
     * 统一的请求入口。
     *
     * @param pathWithQuery 带 query 的路径（评论接口是 GET + query）
     * @param body JSON 请求体；GET 传 null
     */
    private suspend fun requestRaw(
        pathWithQuery: String,
        method: String,
        body: String?,
        signed: Boolean,
    ): String? = withContext(Dispatchers.IO) {
        lastError = ""
        AppLog.d(TAG, "→ $method $pathWithQuery")
        if (!body.isNullOrBlank()) AppLog.d(TAG, "  req: $body")

        // ---- 路线 1：整段请求交给页面内的真实 JS 发起 ----
        // 这样 X-s 由站点自己的拦截器补，Cookie/UA/Referer/a1 完全一致。
        if (signed) {
            val page = runCatching { signEngine.fetchInPage(pathWithQuery, method, body) }.getOrNull()
            if (page != null && page.status == 200 && page.body.trimStart().startsWith("{")) {
                AppLog.d(TAG, "← page HTTP ${page.status} ${page.body.length}B: ${page.body}")
                return@withContext page.body
            }
            if (page != null) {
                AppLog.w(TAG, "page fetch unusable: HTTP ${page.status}: ${page.body.take(300)}")
            } else {
                AppLog.w(TAG, "page fetch returned null, fallback to native")
            }
        }

        // ---- 路线 2：原生签名 + OkHttp ----
        val builder = Request.Builder()
            .url(BASE + pathWithQuery)
            .header("User-Agent", SignEngine.USER_AGENT)
            .header("Origin", WEB_ORIGIN)
            .header("Referer", REFERER)
            .header("Accept", "application/json, text/plain, */*")
            .header("Cookie", cookieHeader())

        if (signed) {
            // GET 的签名串是「路径 + query」，没有请求体
            val headers = signEngine.sign(pathWithQuery, body ?: "")
            if (headers == null) {
                AppLog.w(TAG, "sign unavailable, sending unsigned")
            } else {
                builder.header("X-s", headers.xs)
                builder.header("X-t", headers.xt)
                builder.header("X-S-Common", headers.xSCommon)
            }
        }

        val request = if (method == "GET") {
            builder.get().build()
        } else {
            builder.header("Content-Type", CONTENT_TYPE)
                .post((body ?: "").toRequestBody(mediaType))
                .build()
        }

        runCatching {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string()
                AppLog.d(TAG, "← native HTTP ${resp.code} ${text?.length ?: 0}B: $text")
                if (resp.isSuccessful) {
                    text
                } else {
                    fail("请求被拒绝：HTTP ${resp.code}")
                }
            }
        }.getOrElse {
            fail("网络错误：${it.message}")
            null
        }
    }

    private inline fun <reified T> decodeData(raw: String?): T? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val env = json.decodeFromString<ApiEnvelope<T>>(raw)
            if (!env.success) {
                lastError = "接口返回 code=${env.code} msg=${env.msg ?: ""}".trim()
                AppLog.w(TAG, "api error: code=${env.code} msg=${env.msg}")
            }
            env.data ?: run {
                if (lastError.isEmpty()) lastError = "接口没有返回数据（code=${env.code}）"
                null
            }
        }.getOrElse {
            lastError = "解析失败：${it.message}"
            AppLog.e(TAG, "decode failed", it)
            null
        }
    }

    /**
     * 把响应压成「有哪些 key、多长」的摘要。
     * 贴整段 JSON 太长，这个摘要足够判断字段名对不对、卡片是不是空的。
     */
    private fun summarize(raw: String): String = runCatching {
        val root = JSONObject(raw)
        val data = root.optJSONObject("data")
        val item0 = data?.optJSONArray("items")?.optJSONObject(0)
        val card = item0?.optJSONObject("note_card")
        buildString {
            append("code=").append(root.optInt("code"))
            append(" msg=").append(root.optString("msg"))
            append(" dataKeys=").append(data?.keys()?.asSequence()?.toList())
            append(" item0Keys=").append(item0?.keys()?.asSequence()?.toList())
            append(" cardSize=").append(card?.length() ?: 0)
            append(" cardKeys=").append(card?.keys()?.asSequence()?.toList())
            append(" imageList=").append(card?.optJSONArray("image_list")?.length() ?: -1)
        }
    }.getOrElse { "summarize failed: ${it.message}" }

    // ---------------- 业务接口 ----------------

    /** 搜索笔记（无需签名，但需要登录 Cookie）。 */
    suspend fun searchNotes(keyword: String, page: Int = 1, pageSize: Int = 20): FeedData? {
        val body = buildJsonObject {
            put("keyword", keyword)
            put("page", page)
            put("page_size", pageSize)
            put("sort", "general")
            put("note_type", 0)
            putJsonArray("ext_flags") { }
            putJsonArray("image_formats") {
                add(kotlinx.serialization.json.JsonPrimitive("jpg"))
                add(kotlinx.serialization.json.JsonPrimitive("webp"))
                add(kotlinx.serialization.json.JsonPrimitive("avif"))
            }
            put("search_id", "")
            put("image_scene", "SR_1_1")
        }.toString()
        return decodeData(requestRaw("/api/sns/web/v1/search/notes", "POST", body, signed = false))
    }

    /** 首页推荐信息流。 */
    suspend fun homeFeed(cursor: String = ""): FeedData? {
        val body = buildJsonObject {
            put("cursor_score", cursor)
            put("num", 20)
            put("refresh_type", 1)
            put("category", "homefeed_recommend")
            put("search_id", "")
        }.toString()
        return decodeData(requestRaw("/api/sns/web/v1/homefeed", "POST", body, signed = true))
    }

    /**
     * 笔记详情（含正文与图片）。
     *
     * 请求体里 `extra` 和 `xsec_source` 不能省：少了它们服务端可能返回一个
     * 内容被剥空的 `note_card`（表现就是详情页全白、评论 0）。
     * token 可能来自首页信息流（pc_feed）或搜索（pc_search），
     * 所以拿到空卡片时换一个来源重试一次。
     */
    suspend fun noteDetail(noteId: String, xsecToken: String): NoteDetailResponse? {
        for (source in listOf("pc_feed", "pc_search")) {
            val body = buildJsonObject {
                put("source_note_id", noteId)
                putJsonArray("image_formats") {
                    add(kotlinx.serialization.json.JsonPrimitive("jpg"))
                    add(kotlinx.serialization.json.JsonPrimitive("webp"))
                    add(kotlinx.serialization.json.JsonPrimitive("avif"))
                }
                putJsonObject("extra") { put("need_body_topic", 1) }
                put("xsec_source", source)
                put("xsec_token", xsecToken)
            }.toString()
            val raw = requestRaw("/api/sns/web/v1/feed", "POST", body, signed = true)
            if (!raw.isNullOrBlank()) AppLog.d(TAG, "feed[$source] ${summarize(raw)}")
            val resp = decodeData<NoteDetailResponse>(raw)
            val card = resp?.items?.firstOrNull()?.noteCard
            val usable = card != null &&
                (card.imageList.isNotEmpty() || !card.title.isNullOrBlank() || !card.desc.isNullOrBlank())
            if (usable) {
                AppLog.d(
                    TAG,
                    "detail ok[$source]: images=${card?.imageList?.size} " +
                        "title=${card?.title?.length} desc=${card?.desc?.length} " +
                        "tags=${card?.tags?.size}",
                )
                return resp
            }
            AppLog.w(
                TAG,
                "detail empty[$source]: card=${card != null} " +
                    "images=${card?.imageList?.size} title=${card?.title} desc=${card?.desc?.length}",
            )
        }
        lastError = "笔记内容为空（xsec_token 可能已失效，请返回首页重新打开）"
        return null
    }

    /**
     * 评论分页。
     *
     * 真实接口是 **GET** `/api/sns/web/v2/comment/page`，参数走 query，
     * 返回 `data.comments`。老实现按 POST + `data.list` 处理，所以永远拿到 0 条。
     */
    suspend fun comments(noteId: String, cursor: String, xsecToken: String): CommentsPage? {
        fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
        val query = buildString {
            append("/api/sns/web/v2/comment/page")
            append("?note_id=").append(enc(noteId))
            append("&cursor=").append(enc(cursor))
            append("&top_comment_id=")
            append("&image_formats=").append(enc("jpg,webp,avif"))
            append("&xsec_token=").append(enc(xsecToken))
        }
        return decodeData(requestRaw(query, "GET", null, signed = true))
    }
}
