package com.rednote.lite.util

import android.content.Context
import coil3.SingletonImageLoader
import java.io.File

/**
 * 图片缓存管理（按占用大小回收）。
 *
 * 策略：
 *  - 用户设一个上限（2~8 GB），存 SharedPreferences；
 *  - 占用超过上限时，从**最旧**的文件开始删，一直删到上限的 40%；
 *  - 每次启动、改阈值、手动点「立即清理」时都会跑一次。
 *
 * 上限判断放在这里而不是交给 Coil：Coil 自己的 maxSize 是磁盘百分比，
 * 用户想要的是绝对值，所以 ImageLoader 里把 Coil 的上限设成 8G（最大值），
 * 真正的回收由本类负责。
 */
object CacheManager {

    /** 缓存目录名，必须和 RedNoteApp 里 ImageLoader 的配置一致。 */
    const val DIR_NAME = "image_cache"

    /** 阈值可选区间（GB）。 */
    const val MIN_GB = 2f
    const val MAX_GB = 8f

    /** 清理目标：降到阈值的这个比例。 */
    private const val TARGET_RATIO = 0.4f

    private const val PREFS = "rednote_cache"
    private const val KEY_THRESHOLD_GB = "threshold_gb"
    private const val DEFAULT_GB = 4f

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 用户设定的上限（GB）。 */
    fun thresholdGb(context: Context): Float =
        prefs(context).getFloat(KEY_THRESHOLD_GB, DEFAULT_GB).coerceIn(MIN_GB, MAX_GB)

    fun setThresholdGb(context: Context, gb: Float) {
        prefs(context).edit()
            .putFloat(KEY_THRESHOLD_GB, gb.coerceIn(MIN_GB, MAX_GB))
            .apply()
    }

    fun thresholdBytes(context: Context): Long = gbToBytes(thresholdGb(context))

    private fun gbToBytes(gb: Float): Long = (gb.toDouble() * 1024 * 1024 * 1024).toLong()

    fun cacheDir(context: Context): File = File(context.cacheDir, DIR_NAME)

    /** 当前占用字节数。 */
    fun usageBytes(context: Context): Long = runCatching {
        val dir = cacheDir(context)
        if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)

    /**
     * 超过阈值才清理，清到阈值的 40%。
     *
     * @return 释放的字节数（0 表示没超阈值、没动）
     */
    fun enforce(context: Context): Long {
        val threshold = thresholdBytes(context)
        val usage = usageBytes(context)
        if (usage <= threshold) return 0L
        AppLog.d(
            "CacheManager",
            "enforce: usage=${formatSize(usage)} > threshold=${formatSize(threshold)}",
        )
        return trimTo(context, (threshold * TARGET_RATIO).toLong())
    }

    /**
     * 手动清理：不管有没有超阈值，都删到阈值的 40%（已经更小就不动）。
     */
    fun clearToTarget(context: Context): Long =
        trimTo(context, (thresholdBytes(context) * TARGET_RATIO).toLong())

    /**
     * 按最后修改时间从旧到新删，直到占用不大于 [targetBytes]。
     */
    private fun trimTo(context: Context, targetBytes: Long): Long {
        val dir = cacheDir(context)
        if (!dir.exists()) return 0L
        val files = runCatching {
            dir.walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.lastModified() }
                .toList()
        }.getOrNull() ?: return 0L

        var size = files.sumOf { it.length() }
        if (size <= targetBytes) return 0L

        var freed = 0L
        for (file in files) {
            if (size <= targetBytes) break
            val len = file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) {
                size -= len
                freed += len
            }
        }
        // 文件删了，内存里可能还留着同一张图，一起清掉
        runCatching { SingletonImageLoader.get(context).memoryCache?.clear() }
        AppLog.d(
            "CacheManager",
            "trimTo: freed=${formatSize(freed)} 剩余=${formatSize(size)} 目标=${formatSize(targetBytes)}",
        )
        return freed
    }

    /** 整个清空（退出登录等场景）。 */
    fun clearAll(context: Context) {
        runCatching {
            val loader = SingletonImageLoader.get(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
    }

    /** 字节 → "1.24 GB" / "320 MB"。 */
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> "%.2f GB".format(gb)
            mb >= 1.0 -> "%.0f MB".format(mb)
            else -> "%.0f KB".format(kb)
        }
    }
}
