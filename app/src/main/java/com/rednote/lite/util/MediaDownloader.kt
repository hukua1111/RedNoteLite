package com.rednote.lite.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

/**
 * 图片 / 视频下载。
 *
 * 交给系统的 DownloadManager：不需要自己管理线程和通知，
 * 也免得在 Android 10+ 上处理存储权限。
 */
object MediaDownloader {

    fun enqueue(context: Context, url: String, fileName: String) {
        val result = runCatching {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                )
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
        }
        val message = if (result.isSuccess) "已开始下载：$fileName" else "下载失败：${result.exceptionOrNull()?.message}"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /** 从 URL 猜一个像样的扩展名。 */
    fun extensionOf(url: String, fallback: String): String {
        val path = url.substringBefore('?').substringAfterLast('/')
        val ext = path.substringAfterLast('.', "")
        return if (ext.length in 2..5 && ext.all { it.isLetterOrDigit() }) ext.lowercase() else fallback
    }
}
