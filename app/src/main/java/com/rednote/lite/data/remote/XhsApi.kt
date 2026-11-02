package com.rednote.lite.data.remote

import android.util.Log
import com.rednote.lite.data.model.ApiEnvelope
import com.rednote.lite.data.model.CommentsPage
import com.rednote.lite.data.model.FeedData
import com.rednote.lite.data.model.NoteDetailResponse
import com.rednote.lite.data.sign.SignEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 小红书网页版 API 客户端。
 *
 * 实测结论（详见 ref/xhs/FINDINGS.md）：
 *  - `/api/sns/web/v1/search/notes` 只校验登录态，不校验签名；
 *  - feed / homefeed / comment / user 等接口在网关层（406）要求真实 mnsv2 签名，
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
        Log.w(TAG, msg)
        return null
    }

    private suspend fun postRaw(path: String, body: String, signed: Boolean): String? =
        withContext(Dispatchers.IO) {
            lastError = ""

            // ---- 路线 1：整段请求交给页面内的真实 JS 发起 ----
            // 这样 X-s 由站点自己的拦截器补，Cookie/UA/Referer/a1 完全一致。
            if (signed) {
                val page = runCatching { signEngine.fetchInPage(path, body) }.getOrNull()
                if (page != null && page.status == 200 && page.body.trimStart().startsWith("{")) {
                    Log.d(TAG, "PAGE $path -> ${page.status} ${page.body.take(160)}")
                    return@withContext page.body
                }
                if (page != null) {
                    Log.w(TAG, "page fetch unusable: status=${page.status} body=${page.body.take(120)}")
                }
            }

            // ---- 路线 2：原生签名 + OkHttp ----
            val builder = Request.Builder()
                .url(BASE + path)
                .header("User-Agent", SignEngine.USER_AGENT)
                .header("Content-Type", CONTENT_TYPE)
                .header("Origin", WEB_ORIGIN)
                .header("Referer", REFERER)
                .header("Accept", "application/json, text/plain, */*")
                .header("Cookie", cookieHeader())

            if (signed) {
                val headers = signEngine.sign(path, body)
                if (headers == null) {
                    Log.w(TAG, "sign unavailable for $path; sending unsigned")
                } else {
                    builder.header("X-s", headers.xs)
                    builder.header("X-t", headers.xt)
                    builder.header("X-S-Common", headers.xSCommon)
                }
            }

            val request = builder.post(body.toRequestBody(mediaType)).build()
            runCatching {
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string()
                    Log.d(TAG, "POST $path -> ${resp.code} ${text?.take(160)}")
                    if (resp.isSuccessful) {
                        text
                    } else {
                        fail("请求被拒绝：HTTP ${resp.code}${text?.take(80)?.let { " · $it" } ?: ""}（$path）")
                    }
                }
            }.getOrElse {
                fail("网络错误：${it.message}（$path）")
                null
            }
        }

    private inline fun <reified T> decodeData(raw: String?): T? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val env = json.decodeFromString<ApiEnvelope<T>>(raw)
            if (!env.success) {
                lastError = "接口返回 code=${env.code} msg=${env.msg ?: ""}".trim()
                Log.w(TAG, "api error: code=${env.code} msg=${env.msg}")
            }
            env.data ?: run {
                if (lastError.isEmpty()) lastError = "接口没有返回数据（code=${env.code}）"
                null
            }
        }.getOrElse {
            lastError = "解析失败：${it.message}"
            Log.e(TAG, "decode failed: ${it.message}")
            null
        }
    }

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
        return decodeData(postRaw("/api/sns/web/v1/search/notes", body, signed = false))
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
        return decodeData(postRaw("/api/sns/web/v1/homefeed", body, signed = true))
    }

    /** 笔记详情（含首批评论）。 */
    suspend fun noteDetail(noteId: String, xsecToken: String): NoteDetailResponse? {
        val body = buildJsonObject {
            put("source_note_id", noteId)
            putJsonArray("image_formats") {
                add(kotlinx.serialization.json.JsonPrimitive("jpg"))
                add(kotlinx.serialization.json.JsonPrimitive("webp"))
                add(kotlinx.serialization.json.JsonPrimitive("avif"))
            }
            put("xsec_token", xsecToken)
        }.toString()
        return decodeData(postRaw("/api/sns/web/v1/feed", body, signed = true))
    }

    /** 评论分页。 */
    suspend fun comments(noteId: String, cursor: String): CommentsPage? {
        val body = buildJsonObject {
            put("note_id", noteId)
            put("cursor", cursor)
        }.toString()
        return decodeData(postRaw("/api/sns/web/v2/comment/page", body, signed = true))
    }
}
