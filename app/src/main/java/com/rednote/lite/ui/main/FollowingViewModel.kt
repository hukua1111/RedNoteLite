package com.rednote.lite.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.rednote.lite.RedNoteApp
import com.rednote.lite.data.model.FollowUser

/** 「关注」页：我关注的人。 */
class FollowingViewModel : ViewModel() {

    private val api = RedNoteApp.instance.api

    var users by mutableStateOf<List<FollowUser>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set

    /** 正在取关的那一行，用来禁用重复点击。 */
    var busyUserId by mutableStateOf<String?>(null)
        private set

    private var loaded = false

    /** 只在第一次进入时拉一次；切回来不重复请求。 */
    suspend fun loadIfNeeded() {
        if (loaded || loading) return
        loading = true
        val result = api.following()
        if (result == null) {
            error = api.lastError.ifBlank { "未知错误" }
        } else {
            users = result
            error = null
        }
        loaded = true
        loading = false
    }

    suspend fun refresh() {
        if (refreshing) return
        refreshing = true
        val result = api.following()
        if (result == null) {
            error = api.lastError.ifBlank { "未知错误" }
        } else {
            users = result
            error = null
            loaded = true
        }
        refreshing = false
    }

    /**
     * 取关。
     *
     * 成功后直接把这一行从列表里移除——比去重新拉一次列表省一次请求，
     * 而且用户点完立刻能看到结果。
     *
     * @return 给用户看的提示文本
     */
    suspend fun unfollow(userId: String): String {
        if (userId.isBlank()) return "缺少用户 ID，无法取关"
        if (busyUserId != null) return ""
        val name = users.firstOrNull { it.userId == userId }?.nickname.orEmpty()
        busyUserId = userId
        val ok = api.setFollow(userId, follow = false)
        if (ok) {
            users = users.filterNot { it.userId == userId }
            busyUserId = null
            return if (name.isBlank()) "已取消关注" else "已取消关注 $name"
        }
        busyUserId = null
        return api.lastError.ifBlank { "取关失败" }
    }
}
