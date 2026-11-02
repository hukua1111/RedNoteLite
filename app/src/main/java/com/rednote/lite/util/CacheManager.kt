package com.rednote.lite.util

import android.content.Context
import coil3.SingletonImageLoader

/**
 * 图片缓存管理。
 *
 * - 启动时整体清一次（用户要求每次重启清理缓存）
 * - 笔记数量超过上限时，把最早那批笔记的图片从磁盘缓存里删掉
 */
object CacheManager {

    fun clearAll(context: Context) {
        runCatching {
            val loader = SingletonImageLoader.get(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
    }

    /** 只淘汰指定 URL 的磁盘缓存，其余保留。 */
    fun evict(context: Context, urls: Collection<String>) {
        if (urls.isEmpty()) return
        runCatching {
            val disk = SingletonImageLoader.get(context).diskCache ?: return
            urls.forEach { url -> runCatching { disk.remove(url) } }
        }
    }
}
