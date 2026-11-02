package com.rednote.lite

import android.app.Application
import com.rednote.lite.data.remote.XhsApi
import com.rednote.lite.data.sign.SignEngine

/**
 * 全局单例容器（规模小，故意不引入 DI 框架，保持构建轻量）。
 */
class RedNoteApp : Application() {

    lateinit var signEngine: SignEngine
        private set

    lateinit var api: XhsApi
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        signEngine = SignEngine(this)
        api = XhsApi(signEngine)
    }

    companion object {
        lateinit var instance: RedNoteApp
            private set
    }
}
