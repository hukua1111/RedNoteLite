package com.rednote.lite.ui.main

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.rednote.lite.RedNoteApp
import com.rednote.lite.data.model.FeedItem
import com.rednote.lite.data.sign.UserProfile
import com.rednote.lite.util.AppLog

enum class FeedMode { HOME, SEARCH }

/**
 * 首页筛选条件。
 *
 * 只是本地过滤（不重新请求），所以调参是即时的。
 */
data class FeedFilter(
    val keyword: String = "",
    val minLikes: Int = 0,
    val minComments: Int = 0,
) {
    /**
     * 关键词拆成多个。
     *
     * 用 `/` 分隔（全角 `／` 也认），空项丢掉：
     *   "广告/带货//代购"  →  ["广告", "带货", "代购"]
     */
    val keywords: List<String>
        get() = keyword.split('/', '／')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    val active: Boolean get() = keywords.isNotEmpty() || minLikes > 0 || minComments > 0

    /**
     * 命中任意一条就隐藏。
     *
     * 关键词：多个之间是「或」，匹配上任一个就屏蔽。
     * 点赞：**缺失按 0 算**。有些卡片根本没有 interact_info，界面也是显示 0，
     * 这种必须能被屏蔽，否则「点赞为 0 的屏蔽不掉」。
     * 评论：首页接口不返回 comment_count，缺失时这条不参与判断，
     * 否则一填阈值就会把整页清空。
     */
    fun blocks(item: FeedItem): Boolean {
        // 只 split 一次：visibleItems 会对整页条目逐个调用它
        val kws = keywords
        if (kws.isEmpty() && minLikes <= 0 && minComments <= 0) return false
        val card = item.noteCard
        val info = card?.interactInfo
        val text = listOfNotNull(card?.displayTitle, card?.title, card?.desc).joinToString(" ")

        val byKeyword = kws.any { text.contains(it, ignoreCase = true) }
        val byLikes = minLikes > 0 && parseCount(info?.likedCount) < minLikes
        val commentRaw = info?.commentCount
        val byComments = minComments > 0 && !commentRaw.isNullOrBlank() &&
            parseCount(commentRaw) < minComments

        return byKeyword || byLikes || byComments
    }

    companion object {
        /** "1.2万" / "2750" / "" → 数值 */
        fun parseCount(raw: String?): Int {
            val v = raw?.trim().orEmpty()
            if (v.isEmpty()) return 0
            return when {
                v.endsWith("万") -> ((v.dropLast(1).toDoubleOrNull() ?: 0.0) * 10_000).toInt()
                v.endsWith("w", true) -> ((v.dropLast(1).toDoubleOrNull() ?: 0.0) * 10_000).toInt()
                else -> v.toIntOrNull() ?: 0
            }
        }
    }
}

/**
 * 首页/搜索的数据装载。
 *
 * 状态放在 ViewModel 里（作用域是导航条目）：进详情页再返回时 Compose 会重建界面，
 * 如果状态还挂在 `remember` 上就会丢，于是又拉一次首页——这就是「返回自动刷新」的成因。
 */
class MainViewModel : ViewModel() {

    companion object {
        /** 下拉刷新时一次最多插入多少条新内容。 */
        private const val REFRESH_BATCH = 10
        /** 为了凑够新内容，一次刷新最多请求几页。 */
        private const val MAX_REFRESH_FETCH = 2
        /** 列表最多保留多少条笔记，超出就把最早的丢掉。 */
        private const val MAX_ITEMS = 70

        private const val KEY_KEYWORD = "keyword"
        private const val KEY_LIKES = "min_likes"
        private const val KEY_COMMENTS = "min_comments"
    }

    private val api = RedNoteApp.instance.api

    var items by mutableStateOf<List<FeedItem>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var mode by mutableStateOf(FeedMode.HOME)
        private set
    var searchTerm by mutableStateOf("")
        private set

    /** 最近一次失败的真实原因（HTTP 码 / 业务 code），用于界面展示。 */
    val lastError: String get() = api.lastError

    /** 筛选条件持久化，重启 App 不会丢。 */
    private val prefs = RedNoteApp.instance.getSharedPreferences("rednote_filter", Context.MODE_PRIVATE)

    var filter by mutableStateOf(
        FeedFilter(
            keyword = prefs.getString(KEY_KEYWORD, "").orEmpty(),
            minLikes = prefs.getInt(KEY_LIKES, 0),
            minComments = prefs.getInt(KEY_COMMENTS, 0),
        ),
    )
        private set

    fun updateFilter(value: FeedFilter) {
        filter = value
        prefs.edit()
            .putString(KEY_KEYWORD, value.keyword)
            .putInt(KEY_LIKES, value.minLikes)
            .putInt(KEY_COMMENTS, value.minComments)
            .apply()
    }

    /**
     * 实际展示的列表。
     *
     * 三个条件是「或」的关系，命中任意一条就把这条笔记隐藏：
     *  - 关键词：命中就隐藏（关键词是用来排除的，不是用来筛出来的）
     *  - 点赞 / 评论：少于阈值就隐藏
     */
    val visibleItems: List<FeedItem>
        get() = if (!filter.active) items else items.filterNot { filter.blocks(it) }

    private var cursor = ""
    /** 上次是按哪组条件加载的，用来跳过「返回详情页」这种无意义重取。 */
    private var loadedKey: String? = null

    fun submitSearch(term: String) {
        searchTerm = term
        mode = FeedMode.SEARCH
    }

    fun clearSearch() {
        searchTerm = ""
        mode = FeedMode.HOME
    }

    /**
     * 首次进入或条件变化时加载；key 相同时直接复用已有数据。
     * key 里带 retryKey 与登录态，所以点「重试」或重新登录仍会真的重取。
     */
    suspend fun loadIfNeeded(key: String) {
        if (key == loadedKey && (items.isNotEmpty() || error != null)) return
        loadedKey = key
        loading = true
        error = null
        cursor = ""
        val result = fetch("")
        if (result == null) {
            error = lastError.ifBlank { "未知错误" }
            hasMore = false
        } else {
            items = result.first
            cursor = result.second
            hasMore = cursor.isNotEmpty()
        }
        loading = false
    }

    /**
     * 下拉刷新。
     *
     * 首页：取回的新内容插到最前面，最多 10 条。筛选开着时，被屏蔽的条目直接跳过
     * 并继续往后翻页补齐，最多请求 [MAX_REFRESH_FETCH] 页；仍不足 10 条就按实际
     * 数量插入，不再继续请求。
     */
    suspend fun refresh() {
        if (refreshing) return
        refreshing = true
        if (mode == FeedMode.HOME && items.isNotEmpty()) {
            val existing = items.mapTo(HashSet()) { it.id }
            val collected = mutableListOf<FeedItem>()
            var nextCursor = ""
            var attempts = 0
            while (collected.size < REFRESH_BATCH && attempts < MAX_REFRESH_FETCH) {
                attempts++
                val page = api.homeFeed(nextCursor) ?: break
                page.items
                    .filterNot { it.id in existing }
                    .filterNot { filter.blocks(it) }
                    .forEach { collected += it }
                page.items.forEach { existing += it.id }
                nextCursor = page.cursor ?: ""
                if (nextCursor.isEmpty()) break
            }
            if (collected.isNotEmpty()) {
                items = collected.take(REFRESH_BATCH) + items
                error = null
                trimOldest()
            } else {
                AppLog.d("MainViewModel", "refresh: 没有可插入的新内容")
            }
            // 不动 cursor / hasMore：翻页仍从原来的位置继续
        } else {
            cursor = ""
            val result = fetch("")
            if (result == null) {
                error = lastError.ifBlank { "未知错误" }
            } else {
                items = result.first
                cursor = result.second
                hasMore = cursor.isNotEmpty()
                error = null
            }
        }
        refreshing = false
    }

    /** 触底加载下一页。 */
    suspend fun loadMore() {
        if (loadingMore || loading || refreshing || !hasMore) return
        loadingMore = true
        val result = fetch(cursor)
        if (result != null) {
            items = items + result.first
            cursor = result.second
            hasMore = cursor.isNotEmpty()
        } else {
            AppLog.w("MainViewModel", "loadMore failed: $lastError")
        }
        loadingMore = false
    }

    /**
     * 列表超过 [MAX_ITEMS] 时丢掉最早的一批笔记。
     *
     * 只是控制内存里的列表长度；图片缓存的回收已经改成按占用大小判断
     * （见 CacheManager），不再跟着列表条数走。
     */
    private fun trimOldest() {
        if (items.size <= MAX_ITEMS) return
        items = items.take(MAX_ITEMS)
    }

    /** 读取当前登录用户，供首页右上角展示头像。 */
    suspend fun refreshProfile() {
        val app = RedNoteApp.instance
        val hasCookie = app.signEngine.hasLoginSession()
        var profile = app.signEngine.readUserInfo()

        // 页面里的 __INITIAL_STATE__ 有时只给昵称不给头像，退回接口再取一次
        if (profile == null || profile.avatar.isBlank()) {
            val me = api.selfInfo()
            if (me != null && !me.guest) {
                profile = UserProfile(
                    nickname = profile?.nickname?.ifBlank { me.nickname } ?: me.nickname,
                    avatar = profile?.avatar?.ifBlank { me.avatarUrl.orEmpty() } ?: me.avatarUrl.orEmpty(),
                )
            }
        }

        AppLog.d(
            "Profile",
            "cookie=$hasCookie user=${profile?.nickname} avatarLen=${profile?.avatar?.length ?: 0}",
        )
        if (profile != null && (profile.nickname.isNotBlank() || profile.avatar.isNotBlank())) {
            app.updateProfile(profile.nickname, profile.avatar)
        } else if (!hasCookie) {
            app.clearProfile()
        }
    }

    private suspend fun fetch(after: String): Pair<List<FeedItem>, String>? = when (mode) {
        FeedMode.HOME -> api.homeFeed(after)?.let { it.items to (it.cursor ?: "") }
        FeedMode.SEARCH -> {
            // search 接口用页码分页，cursor 里存的其实是页码
            val page = if (after.isEmpty()) 1 else (after.toIntOrNull() ?: 1)
            api.searchNotes(searchTerm, page)?.let {
                it.items to if (it.hasMore) (page + 1).toString() else ""
            }
        }
    }
}
