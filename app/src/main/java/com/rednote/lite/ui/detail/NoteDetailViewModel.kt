package com.rednote.lite.ui.detail

import com.rednote.lite.RedNoteApp
import com.rednote.lite.data.model.Comment
import com.rednote.lite.data.model.NoteDetailCard

class NoteDetailViewModel {

    private val api = RedNoteApp.instance.api

    /** 最近一次失败的真实原因，用于界面展示。 */
    val lastError: String get() = api.lastError

    /** 返回 (笔记卡片, 评论列表)。 */
    suspend fun load(noteId: String, xsecToken: String): Pair<NoteDetailCard, List<Comment>>? {
        val resp = api.noteDetail(noteId, xsecToken) ?: return null
        val item = resp.items.firstOrNull() ?: return null
        val card = item.noteCard ?: return null
        val comments = item.comments?.list ?: emptyList()
        return card to comments
    }
}
