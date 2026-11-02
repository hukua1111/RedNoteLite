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
}
