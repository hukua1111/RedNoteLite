package com.rednote.lite.ui.main

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
    val active: Boolean get() = keyword.isNotBlank() || minLikes > 0 || minComments > 0

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

    /** 本地筛选条件。 */
    var filter by mutableStateOf(FeedFilter())
        private set

    fun setFilter(value: FeedFilter) {
        filter = value
    }

    /** 实际展示的列表：套用筛选条件后的结果。 */
    val visibleItems: List<FeedItem>
        get() {
            val f = filter
            if (!f.active) return items
            val keyword = f.keyword.trim()
            return items.filter { item ->
                val card = item.noteCard
                val text = card?.displayTitle ?: card?.title ?: card?.desc ?: ""
                val likes = FeedFilter.parseCount(card?.interactInfo?.likedCount)
                val comments = FeedFilter.parseCount(card?.interactInfo?.commentCount)
                (keyword.isEmpty() || text.contains(keyword, ignoreCase = true)) &&
                    likes >= f.minLikes &&
                    comments >= f.minComments
            }
        }

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

    /** 下拉刷新：首页只把新内容插到最前面，原有内容原样留在后面。 */
    suspend fun refresh() {
        if (refreshing) return
        refreshing = true
        if (mode == FeedMode.HOME && items.isNotEmpty()) {
            // 首页：取回第一页，挑出列表里还没有的条目，最多插 10 条
            val result = api.homeFeed("")
            if (result == null) {
                error = lastError.ifBlank { "未知错误" }
            } else {
                val existing = items.mapTo(HashSet()) { it.id }
                val fresh = result.items.filterNot { it.id in existing }.take(REFRESH_BATCH)
                if (fresh.isNotEmpty()) items = fresh + items
                error = null
                // 不动 cursor / hasMore：翻页仍从原来的位置继续
            }
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
