package com.rednote.lite

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import com.rednote.lite.data.remote.XhsApi
import com.rednote.lite.data.sign.SignEngine
import com.rednote.lite.util.CacheManager
import okio.Path.Companion.toOkioPath

/**
 * 全局单例容器（规模小，故意不引入 DI 框架，保持构建轻量）。
 */
class RedNoteApp : Application(), SingletonImageLoader.Factory {

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

    /**
     * 自定义图片加载器。
     *
     * 只做一件事：把磁盘缓存目录固定下来、把 Coil 自己的上限顶到最大值。
     * Coil 默认按磁盘百分比（约 2%）自己淘汰，和用户要的「2~8G 绝对阈值」冲突，
     * 所以这里放宽到 8G，实际回收交给 [CacheManager.enforce]。
     */
    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder()
                    // Coil3 的 directory 要 okio Path，不是 java.io.File
                    .directory(CacheManager.imageCacheDir(context).toOkioPath())
                    // 注意方法名是 maxSizeBytes；不设的话 Coil 默认上限只有 250MB，
                    // 用户设的 2~8G 阈值永远触发不了
                    .maxSizeBytes(MAX_DISK_CACHE_BYTES)
                    .build()
            }
            .build()

    override fun onCreate() {
        super.onCreate()
        instance = this
        signEngine = SignEngine(this)
        api = XhsApi(signEngine)
        // 启动时按阈值检查一次；放后台线程，别拖慢冷启动
        Thread {
            runCatching { CacheManager.enforce(this) }
        }.start()
    }

    companion object {
        lateinit var instance: RedNoteApp
            private set

        /** 磁盘缓存上限：取阈值区间的最大值，真正回收由 CacheManager 负责。 */
        private const val MAX_DISK_CACHE_BYTES = 8L * 1024 * 1024 * 1024
    }
}
