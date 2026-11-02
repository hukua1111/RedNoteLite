package com.rednote.lite.data.remote

import kotlinx.serialization.json.Json

/** 全局 JSON 实例。 */
internal val appJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}
