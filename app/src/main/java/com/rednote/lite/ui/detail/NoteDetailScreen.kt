package com.rednote.lite.ui.detail

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.rednote.lite.data.model.Comment
import com.rednote.lite.data.model.NoteDetailCard
import com.rednote.lite.ui.common.LoadState
import com.rednote.lite.util.MediaDownloader
import com.rednote.lite.util.stripTopicTags
import com.rednote.lite.util.toHttpsUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    noteId: String,
    xsecToken: String,
    onBack: () -> Unit,
    onOpenUser: (String, String, String) -> Unit,
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
                is LoadState.Success -> NoteContent(s.data, comments, onOpenUser)
                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteContent(
    note: NoteDetailCard,
    comments: List<Comment>,
    onOpenUser: (String, String, String) -> Unit,
) {
    val context = LocalContext.current
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
        // 视频笔记：有播放地址就直接放播放器，不然只能看到封面
        val videoUrl = note.video?.media?.stream?.bestUrl
        if (!videoUrl.isNullOrBlank()) {
            // 视频直链也可能是 http://，同样要升到 https，否则会被明文策略拦掉
            item { VideoPlayer(url = videoUrl.toHttpsUrl()) }
        } else if (note.imageList.isNotEmpty()) {
            // 图片轮播
            item {
                val pager = rememberPagerState(pageCount = { note.imageList.size })
                Box(Modifier.fillMaxWidth().height(420.dp)) {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        val img = note.imageList[page]
                        AsyncImage(
                            model = img.bestUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                // 长按图片下载
                                .pointerInput(img.bestUrl) {
                                    detectTapGestures(
                                        onLongPress = {
                                            val link = img.bestUrl ?: return@detectTapGestures
                                            val ext = MediaDownloader.extensionOf(link, "jpg")
                                            MediaDownloader.enqueue(
                                                context,
                                                link,
                                                "rednote_${note.noteId}_${page + 1}.$ext",
                                            )
                                        },
                                    )
                                },
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
                // 正文里内嵌的 #xxx[话题]# 和下面的标签胶囊是同一批话题，去掉避免重复
                val descText = note.desc?.stripTopicTags().orEmpty()
                if (descText.isNotBlank()) {
                    Text(
                        descText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (note.tags.isNotEmpty()) {
                    // 用 FlowRow：标签多了会自动换行，不会把最后一个挤成竖排的窄框
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clickable(enabled = !note.user?.userId.isNullOrBlank()) {
                            val u = note.user ?: return@clickable
                            onOpenUser(u.userId, u.nickname, u.avatarUrl ?: "")
                        },
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = !c.userInfo?.userId.isNullOrBlank()) {
                        val u = c.userInfo ?: return@clickable
                        onOpenUser(u.userId, u.nickname, u.avatarUrl ?: "")
                    },
                ) {
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

/**
 * 视频笔记的播放器。
 *
 * 底层用 Media3 ExoPlayer：VideoView 拿不到播放速度控制，长按倍速做不了。
 * 控件自己画 —— 进度条可拖动、单击暂停/播放、长按右半屏 2 倍速。
 */
@Composable
private fun VideoPlayer(url: String) {
    val context = LocalContext.current
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var boosting by remember { mutableStateOf(false) }
    var suppressTap by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L).toInt()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (!dragging) positionMs = player.currentPosition.coerceAtLeast(0L).toInt()
            delay(250)
        }
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    Box(Modifier.fillMaxWidth().height(420.dp).background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                }
            },
        )

        // 手势层：单击播放/暂停，长按右半屏加速（松手恢复）
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(url) {
                    detectTapGestures(
                        onTap = {
                            if (suppressTap) suppressTap = false else togglePlay()
                        },
                        onPress = { offset ->
                            suppressTap = false
                            val onRight = offset.x > size.width * 0.6f
                            var fired = false
                            val job = scope.launch {
                                // 右侧长按加速；中间/左侧长按下载
                                delay(if (onRight) 350 else 600)
                                fired = true
                                suppressTap = true
                                if (onRight) {
                                    boosting = true
                                    runCatching { player.setPlaybackSpeed(2f) }
                                } else {
                                    val ext = MediaDownloader.extensionOf(url, "mp4")
                                    MediaDownloader.enqueue(
                                        context,
                                        url,
                                        "rednote_video_${System.currentTimeMillis()}.$ext",
                                    )
                                }
                            }
                            tryAwaitRelease()
                            job?.cancel()
                            if (onRight && fired) {
                                boosting = false
                                runCatching { player.setPlaybackSpeed(1f) }
                            }
                        },
                    )
                },
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 2.dp),
        ) {
            Slider(
                value = positionMs.toFloat(),
                onValueChange = { value ->
                    dragging = true
                    positionMs = value.toInt()
                },
                onValueChangeFinished = {
                    runCatching { player.seekTo(positionMs.toLong()) }
                    dragging = false
                },
                valueRange = 0f..(if (durationMs > 0) durationMs.toFloat() else 1f),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDuration(positionMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    " / ${formatDuration(durationMs)}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                if (boosting) {
                    Text("2×", color = Color.White, style = MaterialTheme.typography.labelMedium)
                } else {
                    Text(
                        if (isPlaying) "暂停" else "播放",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
