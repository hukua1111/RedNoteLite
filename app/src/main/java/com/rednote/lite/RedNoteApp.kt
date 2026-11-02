package com.rednote.lite

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.rednote.lite.data.remote.XhsApi
import com.rednote.lite.data.sign.SignEngine
import com.rednote.lite.util.CacheManager

/**
 * 全局单例容器（规模小，故意不引入 DI 框架，保持构建轻量）。
 */
class RedNoteApp : Application() {

    lateinit var signEngine: SignEngine
        private set

    lateinit var api: XhsApi
        private set

    private val authVersionState = mutableIntStateOf(0)

    /**
     * 登录状态版本号。
     *
     * 登录页是一个独立的导航目标，登录成功返回首页时 MainScreen 并不会重组重取，
     * 于是会停在「登录了但首页还是那屏报错」——这是最容易被误解成"没登录"的一环。
     * 登录成功就 +1，首页把它当 LaunchedEffect 的 key，一次登录只多刷一次。
     */
    val authVersion: Int get() = authVersionState.intValue

    fun notifyAuthChanged() {
        authVersionState.intValue++
    }

    private val avatarState = mutableStateOf("")
    private val nicknameState = mutableStateOf("")

    /** 当前登录用户的头像 / 昵称；未登录时为空串。 */
    val avatarUrl: String get() = avatarState.value
    val nickname: String get() = nicknameState.value

    fun updateProfile(nickname: String, avatar: String) {
        nicknameState.value = nickname
        avatarState.value = avatar
    }

    fun clearProfile() {
        nicknameState.value = ""
        avatarState.value = ""
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        signEngine = SignEngine(this)
        api = XhsApi(signEngine)
        // 每次启动清一次图片缓存；放后台线程，别拖慢冷启动
        Thread {
            CacheManager.clearAll(this)
        }.start()
    }

    companion object {
        lateinit var instance: RedNoteApp
            private set
    }
}
