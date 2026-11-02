package com.rednote.lite.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 应用内环形日志。
 *
 * 除了一并写到 logcat，还会留在内存里，供「运行日志」页复制 / 导出——
 * 这样遇到只在真机上复现的问题，不需要连 adb 也能把现场带出来。
 */
object AppLog {

    private const val MAX_LINES = 300
    private const val CHUNK = 8000

    private val lines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun d(tag: String, msg: String) = append("D", tag, msg, null)

    fun w(tag: String, msg: String, tr: Throwable? = null) = append("W", tag, msg, tr)

    fun e(tag: String, msg: String, tr: Throwable? = null) = append("E", tag, msg, tr)

    @Synchronized
    private fun append(level: String, tag: String, msg: String, tr: Throwable?) {
        val stamp = timeFormat.format(Date())
        val body = if (msg.length > CHUNK) msg.take(CHUNK) + "…(共 ${msg.length} 字)" else msg
        lines.addLast("$stamp $level/$tag: $body")
        if (tr != null) {
            lines.addLast("    ↳ ${tr.javaClass.simpleName}: ${tr.message}")
        }
        while (lines.size > MAX_LINES) lines.removeFirst()
        when (level) {
            "E" -> Log.e(tag, body, tr)
            "W" -> Log.w(tag, body, tr)
            else -> Log.d(tag, body)
        }
    }

    @Synchronized
    fun dump(): String = if (lines.isEmpty()) "(暂无日志)" else lines.joinToString("\n")

    @Synchronized
    fun clear() {
        lines.clear()
    }
}
