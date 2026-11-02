package com.rednote.lite.ui.main

import com.rednote.lite.RedNoteApp
import com.rednote.lite.data.model.FeedItem

/**
 * 首页/搜索的数据装载。返回 (新增条目, 下一页 cursor)。
 */
class MainViewModel {

    private val api = RedNoteApp.instance.api

    /** 最近一次失败的真实原因（HTTP 码 / 业务 code），用于界面展示。 */
    val lastError: String get() = api.lastError

    /** 读取当前登录用户，供首页右上角展示头像。 */
    suspend fun refreshProfile() {
        val app = RedNoteApp.instance
        if (!app.signEngine.hasLoginSession()) {
            app.clearProfile()
            return
        }
        val profile = app.signEngine.readUserInfo() ?: return
        app.updateProfile(profile.nickname, profile.avatar)
    }

    suspend fun homeFeed(cursor: String): Pair<List<FeedItem>, String>? {
        val data = api.homeFeed(cursor) ?: return null
        return data.items to (data.cursor ?: "")
    }

    suspend fun search(keyword: String, cursor: String): Pair<List<FeedItem>, String>? {
        // search 接口用 page 分页；cursor 里存的是页码
        val page = cursor.toIntOrNull() ?: 1
        val data = api.searchNotes(keyword, page) ?: return null
        val next = if (data.hasMore) (page + 1).toString() else ""
        return data.items to next
    }
}
