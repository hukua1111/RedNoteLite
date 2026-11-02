package com.rednote.lite.util

/**
 * 图片地址统一升级成 https。
 *
 * 小红书接口有时返回 `http://sns-webpic-qc.xhscdn.com/...`，而本应用
 * （Manifest 里 usesCleartextTraffic=false）在 Android 9+ 上会直接拦截明文请求，
 * 表现就是图片统统加载不出来、详情页只剩一片白。CDN 本身是支持 https 的。
 */
fun String.toHttpsUrl(): String =
    if (startsWith("http://", ignoreCase = true)) "https://" + substring(7) else this
