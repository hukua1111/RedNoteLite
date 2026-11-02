package com.rednote.lite.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rednote.lite.data.model.Comment
import com.rednote.lite.data.model.NoteDetailCard
import com.rednote.lite.ui.common.LoadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    noteId: String,
    xsecToken: String,
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    var state by remember { mutableStateOf<LoadState<NoteDetailCard>>(LoadState.Loading) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var retryKey by remember { mutableStateOf(0) }
    val vm = remember { NoteDetailViewModel() }

    LaunchedEffect(noteId, xsecToken, retryKey) {
        state = LoadState.Loading
        val detail = vm.load(noteId, xsecToken)
        if (detail == null) {
            // 展示真实原因，不再一律甩给用户一句「可能需要登录」
            state = LoadState.Error(vm.lastError.ifBlank { "笔记加载失败" })
        } else {
            state = LoadState.Success(detail.first)
            comments = detail.second
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("笔记详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenLogs) { Text("日志") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is LoadState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) {
                        Text("笔记加载失败", style = MaterialTheme.typography.titleSmall)
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        TextButton(onClick = { retryKey++ }, modifier = Modifier.padding(top = 8.dp)) {
                            Text("重试")
                        }
                    }
                }
                is LoadState.Success -> NoteContent(s.data, comments)
                else -> Unit
            }
        }
    }
}

@Composable
private fun NoteContent(note: NoteDetailCard, comments: List<Comment>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // 卡片拿回来是空的：给一句明确提示，而不是留一整屏白
        if (note.imageList.isEmpty() && note.title.isNullOrBlank() && note.desc.isNullOrBlank()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "这条笔记没有返回内容\nxsec_token 可能已失效，请返回首页重新打开",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        // 图片轮播
        if (note.imageList.isNotEmpty()) {
            item {
                val pager = rememberPagerState(pageCount = { note.imageList.size })
                Box(Modifier.fillMaxWidth().height(420.dp)) {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        val img = note.imageList[page]
                        AsyncImage(
                            model = img.bestUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (note.imageList.size > 1) {
                        Surface(
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                        ) {
                            Text(
                                "${pager.currentPage + 1}/${note.imageList.size}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        // 标题与正文
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                if (!note.title.isNullOrBlank()) {
                    Text(
                        note.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (!note.desc.isNullOrBlank()) {
                    Text(
                        note.desc,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (note.tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        note.tags.take(6).forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    "#${tag.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    AsyncImage(
                        model = note.user?.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                    )
                    Text(
                        note.user?.nickname.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Text(
                        "   ♥ ${note.interactInfo?.likedCount ?: "0"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { HorizontalDivider(Modifier.padding(horizontal = 16.dp)) }
        item {
            val count = if (comments.isNotEmpty()) comments.size.toString()
            else note.interactInfo?.commentCount ?: "0"
            Text(
                "评论 $count",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp),
            )
        }

        items(comments, key = { it.id }) { c ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = c.userInfo?.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(28.dp).clip(CircleShape),
                    )
                    Text(
                        c.userInfo?.nickname.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Text(
                        "  ♥ ${c.likeCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    c.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 36.dp, top = 2.dp),
                )
            }
        }

        item { Box(Modifier.height(24.dp)) }
    }
}
