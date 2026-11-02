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
) {
    /** 取第一个非空链接：接口有时会返回空字符串，`?:` 是兜不住的。 */
    val bestUrl: String?
        get() = listOfNotNull(urlDefault, urlPre, url).firstOrNull { it.isNotBlank() }?.toHttpsUrl()
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
    val avatar: String? = null,
    @SerialName("xsec_token") val xsecToken: String? = null,
) {
    /** 头像也可能是 http://，统一升到 https 才不会被明文策略拦掉。 */
    val avatarUrl: String? get() = avatar?.toHttpsUrl()
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
)

@Serializable
data class VideoCodec(
    @SerialName("master_url") val masterUrl: String? = null,
    @SerialName("backup_urls") val backupUrls: List<String> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    val duration: Int = 0,
    val format: String? = null,
)

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

// ---------------- 通用 ----------------

@Serializable
data class EmptyData(val unused: JsonElement? = null)
