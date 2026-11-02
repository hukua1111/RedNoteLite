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

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun cookieHeader(): String {
        val parts = mutableListOf<String>()
        for (name in listOf("a1", "webId", "web_session", "abRequestId", "gid", "customerClientId")) {
            signEngine.readCookie(name)?.takeIf { it.isNotEmpty() }?.let { parts += "$name=$it" }
        }
        if (parts.none { it.startsWith("xsecappid=") }) parts += "xsecappid=xhs-pc-web"
        if (parts.none { it.startsWith("webBuild=") }) parts += "webBuild=6.7.1"
        return parts.joinToString("; ")
    }

    private suspend fun postRaw(path: String, body: String, signed: Boolean): String? =
        withContext(Dispatchers.IO) {
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
                    if (resp.isSuccessful) text else null
                }
            }.getOrElse {
                Log.e(TAG, "POST $path failed: ${it.message}")
                null
            }
        }

    private inline fun <reified T> decodeData(raw: String?): T? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val env = json.decodeFromString<ApiEnvelope<T>>(raw)
            if (!env.success) Log.w(TAG, "api error: code=${env.code} msg=${env.msg}")
            env.data
        }.getOrElse {
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
