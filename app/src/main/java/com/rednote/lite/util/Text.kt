package com.rednote.lite.util

/** 正文里的 #话题[话题]# 标记（小红书把话题同时写进正文和 tag_list）。 */
private val TOPIC_TAG = Regex("#[^#\\s]{1,40}\\[话题\\]#")
private val MULTI_SPACE = Regex("[ \\t]{2,}")

/**
 * 去掉正文里的话题标记。
 *
 * 详情页正文和下面的标签胶囊来自两份数据，正文里本来就内嵌了
 * `#情侣[话题]#` 这种文本，不去掉就会和胶囊重复显示一遍。
 */
fun String.stripTopicTags(): String =
    replace(TOPIC_TAG, "").replace(MULTI_SPACE, " ").trim()
