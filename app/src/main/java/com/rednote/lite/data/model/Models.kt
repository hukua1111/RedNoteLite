package com.rednote.lite.data.model

import com.rednote.lite.util.toHttpsUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiEnvelope<T>(
    val code: Int = 0,
    val success: Boolean = false,
    val msg: String? = null,
    val data: T? = null,
)

// ---------------- 信息流 / 搜索 ----------------
//
// 注意：小红书 web 接口的字段一律是 snake_case（note_card / url_default / interact_info ...）。
// kotlinx.serialization 默认按属性名精确匹配，所以下面每个多词字段都必须显式写 @SerialName，
// 否则 ignoreUnknownKeys 会把它们静默丢掉，界面就会变成「有列表但全是空白卡片」。

@Serializable
data class FeedData(
    val items: List<FeedItem> = emptyList(),
    val cursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("search_id") val searchId: String? = null,
)

@Serializable
data class FeedItem(
    val id: String = "",
    @SerialName("model_type") val modelType: String? = null,
    @SerialName("note_card") val noteCard: NoteCard? = null,
    @SerialName("xsec_token") val xsecToken: String? = null,
)

@Serializable
data class NoteCard(
    val type: String? = null,
    @SerialName("note_id") val noteId: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    val title: String? = null,
    val desc: String? = null,
    val user: UserBrief? = null,
    @SerialName("interact_info") val interactInfo: InteractInfo? = null,
    val cover: Cover? = null,
    @SerialName("video") val video: VideoInfo? = null,
    // 有些列表接口把 xsec_token 放在 note_card 里而不是外层，取不到时用它兜底
    @SerialName("xsec_token") val xsecToken: String? = null,
)

@Serializable
data class Cover(
    @SerialName("url_default") val urlDefault: String? = null,
    @SerialName("url_pre") val urlPre: String? = null,
    val url: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("info_list") val infoList: List<ImageInfo> = emptyList(),
) {
    /**
     * 取第一个可用链接。
     *
     * 注意：首页/详情用 `url_default`，但 user_posted 的封面 `url` 是空串，
     * 地址只放在 `info_list` 里（scene 是 CRD_WM_WEBP / CRD_PRV_WEBP），
     * 所以必须带 info_list 兜底，否则用户主页全是「无封面」。
     */
    val bestUrl: String?
        get() = listOfNotNull(urlDefault, urlPre, url)
            .firstOrNull { it.isNotBlank() }?.toHttpsUrl()
            ?: infoList.firstOrNull { it.imageScene == "CRD_PRV_WEBP" }?.url
                ?.takeIf { it.isNotBlank() }?.toHttpsUrl()
            ?: infoList.firstOrNull { !it.url.isNullOrBlank() }?.url?.toHttpsUrl()
}

@Serializable
data class VideoInfo(
    val duration: Int = 0,
    val image: Cover? = null,
)

@Serializable
data class UserBrief(
    @SerialName("user_id") val userId: String = "",
    val nickname: String = "",
    // 评论 / 详情接口的头像字段叫 image，部分列表接口叫 avatar，两个都收
    @SerialName("image") val image: String? = null,
    @SerialName("avatar") val avatar: String? = null,
    @SerialName("xsec_token") val xsecToken: String? = null,
) {
    /** 头像也可能是 http://，统一升到 https 才不会被明文策略拦掉。 */
    val avatarUrl: String?
        get() = (image?.takeIf { it.isNotBlank() } ?: avatar?.takeIf { it.isNotBlank() })?.toHttpsUrl()
}

@Serializable
data class InteractInfo(
    @SerialName("liked_count") val likedCount: String = "0",
    val liked: Boolean = false,
    val collected: Boolean = false,
    @SerialName("collected_count") val collectedCount: String? = null,
    @SerialName("comment_count") val commentCount: String? = null,
    @SerialName("shared_count") val sharedCount: String? = null,
)

// ---------------- 笔记详情 ----------------

@Serializable
data class NoteDetailResponse(
    val items: List<NoteDetailItem> = emptyList(),
    @SerialName("current_time") val currentTime: Long = 0,
)

@Serializable
data class NoteDetailItem(
    val id: String = "",
    @SerialName("note_card") val noteCard: NoteDetailCard? = null,
    val comments: CommentsPage? = null,
)

@Serializable
data class NoteDetailCard(
    @SerialName("note_id") val noteId: String = "",
    val type: String = "normal",
    val title: String? = null,
    val desc: String? = null,
    val user: UserBrief? = null,
    @SerialName("interact_info") val interactInfo: InteractInfo? = null,
    @SerialName("image_list") val imageList: List<NoteImage> = emptyList(),
    val video: VideoDetail? = null,
    val time: Long = 0,
    @SerialName("last_update_time") val lastUpdateTime: Long = 0,
    @SerialName("ip_location") val ipLocation: String? = null,
    @SerialName("tag_list") val tags: List<NoteTag> = emptyList(),
)

@Serializable
data class NoteTag(val name: String = "", val type: String? = null)

@Serializable
data class VideoDetail(
    val media: VideoMedia? = null,
    val consumer: VideoConsumer? = null,
)

@Serializable
data class VideoMedia(
    val stream: VideoStream? = null,
)

@Serializable
data class VideoStream(
    val h264: List<VideoCodec> = emptyList(),
    val h265: List<VideoCodec> = emptyList(),
    val av1: List<VideoCodec> = emptyList(),
    // 网页版实际返回的 key 是 EF4/EF5/EF6/EF7（h264/h265 那套是 App 端命名），
    // 之前只按 h264 取，所以永远拿不到播放地址。
    @SerialName("EF4") val ef4: List<VideoCodec> = emptyList(),
    @SerialName("EF5") val ef5: List<VideoCodec> = emptyList(),
    @SerialName("EF6") val ef6: List<VideoCodec> = emptyList(),
    @SerialName("EF7") val ef7: List<VideoCodec> = emptyList(),
) {
    /**
     * 选一个能播的直链。
     *
     * 原来的顺序是 EF5 -> EF4，但实测 EF5 这类低码率流经常是**静音预览流**
     * （有 audio_codec 但 volume=0），放出来就是有画面没声音。
     * 现在先挑带音轨的，再在其中取分辨率最高的；全都没音轨才退化为最高分辨率。
     */
    val bestUrl: String?
        get() {
            val all = (ef4 + ef5 + ef6 + ef7 + h264 + h265 + av1)
                .filter { !it.masterUrl.isNullOrBlank() }
            if (all.isEmpty()) return null
            val withAudio = all.filter { it.hasAudio }
            return (withAudio.ifEmpty { all }).maxByOrNull { it.height }?.masterUrl
        }

    /** 调试用：把所有流的关键信息打成一行，方便确认选的到底是不是静音流。 */
    val debugSummary: String
        get() {
            val named = listOf("EF4" to ef4, "EF5" to ef5, "EF6" to ef6, "EF7" to ef7)
            return named.joinToString(" | ") { (name, list) ->
                val item = list.firstOrNull() ?: return@joinToString "$name=-"
                "$name=${item.height}p vol=${item.volume} " +
                    "audio=${item.audioBitrate}/${item.audioCodec ?: "-"}"
            }
        }
}

@Serializable
data class VideoCodec(
    @SerialName("master_url") val masterUrl: String? = null,
    @SerialName("backup_urls") val backupUrls: List<String> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    val duration: Int = 0,
    val format: String? = null,
    /** 网页端每条流都带这个：0 表示静音预览流。 */
    val volume: Int = 0,
    @SerialName("audio_bitrate") val audioBitrate: Int = 0,
    @SerialName("audio_codec") val audioCodec: String? = null,
    @SerialName("avg_bitrate") val avgBitrate: Int = 0,
) {
    /** 有没有音轨数据。 */
    val hasAudio: Boolean get() = audioBitrate > 0 && !audioCodec.isNullOrBlank()
}

@Serializable
data class VideoConsumer(@SerialName("origin_video_key") val originVideoKey: String? = null)

@Serializable
data class NoteImage(
    @SerialName("url_default") val urlDefault: String? = null,
    @SerialName("url_pre") val urlPre: String? = null,
    val url: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("trace_id") val traceId: String? = null,
    @SerialName("info_list") val infoList: List<ImageInfo> = emptyList(),
) {
    /**
     * 详情图的可用链接。
     *
     * 接口不一定给 `url_default`，有时只把地址塞在 `info_list` 里（`WB_DFT` 是默认图），
     * 也可能返回空字符串——所以这里按「非空」逐个筛，而不是用 `?:`。
     */
    val bestUrl: String?
        get() = listOfNotNull(urlDefault, urlPre, url)
            .firstOrNull { it.isNotBlank() }?.toHttpsUrl()
            ?: infoList.firstOrNull { it.imageScene == "WB_DFT" }?.url
                ?.takeIf { it.isNotBlank() }?.toHttpsUrl()
            ?: infoList.firstOrNull { !it.url.isNullOrBlank() }?.url?.toHttpsUrl()
}

@Serializable
data class ImageInfo(
    @SerialName("image_scene") val imageScene: String? = null,
    val url: String? = null,
)

// ---------------- 评论 ----------------

@Serializable
data class CommentsPage(
    // 评论接口（GET /api/sns/web/v2/comment/page）返回的是 data.comments，
    // 不是 data.list —— 之前按 list 解析，所以永远是 0 条
    @SerialName("comments") val list: List<Comment> = emptyList(),
    val cursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class Comment(
    val id: String = "",
    @SerialName("note_id") val noteId: String = "",
    val content: String = "",
    @SerialName("user_info") val userInfo: UserBrief? = null,
    @SerialName("create_time") val createTime: Long = 0,
    @SerialName("like_count") val likeCount: String = "0",
    val liked: Boolean = false,
    @SerialName("sub_comment_count") val subCommentCount: String? = null,
    @SerialName("sub_comments") val subComments: List<Comment> = emptyList(),
    @SerialName("ip_location") val ipLocation: String? = null,
)

// ---------------- 关注列表 / 用户主页 ----------------

@Serializable
data class FollowingData(
    val items: List<FollowUser> = emptyList(),
)

/**
 * 关注列表里的一项。
 *
 * 注意 `intimacy_list` 的字段名和别处都不一样：用户 id 叫 `rid`（24 位十六进制），
 * 还有一个 `userid` 是「rid_哈希」的形式，头像叫 `images` 而不是 image/avatar。
 */
@Serializable
data class FollowUser(
    @SerialName("rid") val rid: String = "",
    @SerialName("userid") val userid: String = "",
    val nickname: String = "",
    @SerialName("images") val images: String? = null,
    @SerialName("image") val image: String? = null,
    val desc: String? = null,
) {
    /** user_posted 要的是 24 位 user_id，也就是 rid。 */
    val userId: String
        get() = rid.ifBlank { userid.substringBefore('_') }

    val avatarUrl: String?
        get() = (images?.takeIf { it.isNotBlank() } ?: image?.takeIf { it.isNotBlank() })?.toHttpsUrl()
}

@Serializable
data class UserNotesData(
    val notes: List<UserNoteItem> = emptyList(),
    val cursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class UserNoteItem(
    @SerialName("note_id") val noteId: String = "",
    @SerialName("display_title") val displayTitle: String? = null,
    val type: String? = null,
    val cover: Cover? = null,
    @SerialName("cover_info") val coverInfo: Cover? = null,
    @SerialName("note_card") val noteCard: NoteCard? = null,
    @SerialName("interact_info") val interactInfo: InteractInfo? = null,
    @SerialName("xsec_token") val xsecToken: String? = null,
) {
    /** user_posted 的封面字段位置不固定，几个可能的地方都试一下。 */
    val bestCover: String?
        get() = cover?.bestUrl ?: coverInfo?.bestUrl ?: noteCard?.cover?.bestUrl
}

/** 当前登录用户（/api/sns/web/v2/user/me）。 */
@Serializable
data class SelfInfo(
    @SerialName("user_id") val userId: String = "",
    val nickname: String = "",
    @SerialName("image") val image: String? = null,
    @SerialName("images") val images: String? = null,
    @SerialName("avatar") val avatar: String? = null,
    val guest: Boolean = false,
) {
    val avatarUrl: String?
        get() = (image?.takeIf { it.isNotBlank() }
            ?: images?.takeIf { it.isNotBlank() }
            ?: avatar?.takeIf { it.isNotBlank() })?.toHttpsUrl()
}

// ---------------- 通用 ----------------

@Serializable
data class EmptyData(val unused: JsonElement? = null)
