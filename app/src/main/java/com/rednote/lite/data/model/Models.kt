package com.rednote.lite.data.model

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

@Serializable
data class FeedData(
    val items: List<FeedItem> = emptyList(),
    val cursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class FeedItem(
    val id: String = "",
    val modelType: String? = null,
    val noteCard: NoteCard? = null,
    val xsecToken: String? = null,
)

@Serializable
data class NoteCard(
    val type: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    val title: String? = null,
    val user: UserBrief? = null,
    val interactInfo: InteractInfo? = null,
    val cover: Cover? = null,
    @SerialName("video") val video: VideoInfo? = null,
)

@Serializable
data class Cover(
    val urlDefault: String? = null,
    val urlPre: String? = null,
    val url: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)

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
    val xsecToken: String? = null,
)

@Serializable
data class InteractInfo(
    val likedCount: String = "0",
    val liked: Boolean = false,
    val collected: Boolean = false,
    val commentCount: String? = null,
    val sharedCount: String? = null,
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
    val noteCard: NoteDetailCard? = null,
    val comments: CommentsPage? = null,
)

@Serializable
data class NoteDetailCard(
    val noteId: String = "",
    val type: String = "normal",
    val title: String? = null,
    val desc: String? = null,
    val user: UserBrief? = null,
    val interactInfo: InteractInfo? = null,
    val imageList: List<NoteImage> = emptyList(),
    val video: VideoDetail? = null,
    val time: Long = 0,
    val lastUpdateTime: Long = 0,
    val ipLocation: String? = null,
    @SerialName("tagList") val tags: List<NoteTag> = emptyList(),
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
    val masterUrl: String? = null,
    val backupUrls: List<String> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    val duration: Int = 0,
    val format: String? = null,
)

@Serializable
data class VideoConsumer(val originVideoKey: String? = null)

@Serializable
data class NoteImage(
    val urlDefault: String? = null,
    val urlPre: String? = null,
    val url: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val traceId: String? = null,
    val infoList: List<ImageInfo> = emptyList(),
)

@Serializable
data class ImageInfo(val imageScene: String? = null, val url: String? = null)

// ---------------- 评论 ----------------

@Serializable
data class CommentsPage(
    val list: List<Comment> = emptyList(),
    val cursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class Comment(
    val id: String = "",
    val noteId: String = "",
    val content: String = "",
    val userInfo: UserBrief? = null,
    val createTime: Long = 0,
    val likeCount: String = "0",
    val liked: Boolean = false,
    val subCommentCount: String? = null,
    val subComments: List<Comment> = emptyList(),
    val ipLocation: String? = null,
)

// ---------------- 通用 ----------------

@Serializable
data class EmptyData(val unused: JsonElement? = null)
