package com.rednote.lite.ui.detail

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.rednote.lite.data.model.Comment
import com.rednote.lite.data.model.NoteDetailCard
import com.rednote.lite.ui.common.LoadState
import com.rednote.lite.util.AppLog
import com.rednote.lite.util.MediaDownloader
import com.rednote.lite.util.stripTopicTags
import com.rednote.lite.util.toHttpsUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

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
    // 长按只负责「问一下」，确认后才真下载
    var pendingDownload by remember { mutableStateOf<DownloadRequest?>(null) }

    pendingDownload?.let { req ->
        ConfirmDownloadDialog(
            title = req.title,
            onConfirm = { MediaDownloader.enqueue(context, req.url, req.fileName) },
            onDismiss = { pendingDownload = null },
        )
    }

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
                                            pendingDownload = DownloadRequest(
                                                title = "下载这张图片？",
                                                url = link,
                                                fileName = "rednote_${note.noteId}_${page + 1}.$ext",
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
 * 底层用 Media3 ExoPlayer，控件全部自绘：
 *  - 常态只是一条很细的进度条，没有底色遮罩，不挡画面
 *  - 按住进度条（底部一片区域）才变粗，同时在画面正中显示「当前 / 总时长」
 *  - 单击画面暂停/播放
 *  - 长按右半屏 2 倍速，画面正中显示 2×，松手恢复
 *  - 长按中间/左侧弹确认框后下载
 */
@Composable
private fun VideoPlayer(url: String) {
    val context = LocalContext.current
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var boosting by remember { mutableStateOf(false) }
    var askDownload by remember { mutableStateOf(false) }

    // PlayerView 的引用留着，onDispose 时先解绑再释放（见下面的注释）
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            // 显式声明媒体音频属性并申请音频焦点：
            // 不申请的话，系统里其它音频通道（比如隐藏 WebView）可能压住它，表现就是没声音
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            volume = 1f
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

            /**
             * 解析出的音轨数量。
             *
             * 这里只做一件事：把结果写进日志。audioGroups=0 说明这条流本身没有音轨，
             * 那就是选流选到了静音预览流，不是播放器的问题。
             */
            override fun onTracksChanged(tracks: Tracks) {
                var audioGroups = 0
                var maxChannels = 0
                var sampleMime: String? = null
                tracks.groups.forEach { group ->
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        audioGroups++
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            if (format.channelCount > maxChannels) maxChannels = format.channelCount
                            sampleMime = format.sampleMimeType
                        }
                    }
                }
                AppLog.d(
                    "Video",
                    "tracks: audioGroups=$audioGroups channels=$maxChannels mime=$sampleMime",
                )
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            // 退出详情页时，SurfaceView 会把自己那一层留在窗口上，
            // 只 release 播放器的话最后一帧还能挂好几秒（看起来像画中画）。
            // 顺序很重要：先解绑 PlayerView，再 stop + clearVideoSurface，最后 release。
            runCatching {
                playerView?.player = null
                player.stop()
                player.clearVideoSurface()
            }
            player.release()
            playerView = null
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (!dragging) positionMs = player.currentPosition.coerceAtLeast(0L).toInt()
            delay(200)
        }
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun setSpeed(speed: Float) {
        runCatching { player.setPlaybackSpeed(speed) }
    }

    if (askDownload) {
        ConfirmDownloadDialog(
            title = "下载这个视频？",
            onConfirm = {
                val ext = MediaDownloader.extensionOf(url, "mp4")
                MediaDownloader.enqueue(
                    context,
                    url,
                    "rednote_video_${System.currentTimeMillis()}.$ext",
                )
            },
            onDismiss = { askDownload = false },
        )
    }

    val fraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    // 拖动时才长高，松手缩回细线
    val trackHeight by animateDpAsState(if (dragging) 6.dp else 2.5.dp, label = "trackHeight")
    val thumbRadius by animateDpAsState(if (dragging) 6.dp else 3.dp, label = "thumbRadius")

    Box(Modifier.fillMaxWidth().height(420.dp).background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                    playerView = this
                }
            },
        )

        // 手势层：底部一片是进度条，其余区域单击/长按
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(url, durationMs) {
                    val trackZone = 44.dp.toPx()
                    val horizontalPadding = 12.dp.toPx()
                    val touchSlop = viewConfiguration.touchSlop

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        if (down.position.y >= size.height - trackZone) {
                            // ---------- 拖动进度条 ----------
                            dragging = true
                            val usable = (size.width - horizontalPadding * 2).coerceAtLeast(1f)
                            fun seekAt(x: Float) {
                                val f = ((x - horizontalPadding) / usable).coerceIn(0f, 1f)
                                positionMs = (f * (if (durationMs > 0) durationMs else 1)).toInt()
                            }
                            seekAt(down.position.x)
                            var released = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    released = true
                                    break
                                }
                                seekAt(change.position.x)
                                change.consume()
                            }
                            if (released) runCatching { player.seekTo(positionMs.toLong()) }
                            dragging = false
                        } else {
                            // ---------- 单击播放/暂停，长按加速或下载 ----------
                            val onRight = down.position.x > size.width * 0.6f
                            val longPressMs = if (onRight) 350L else 600L
                            val start = down.position
                            val startAt = System.currentTimeMillis()
                            var longPressed = false
                            var released = false

                            while (true) {
                                val elapsed = System.currentTimeMillis() - startAt
                                if (elapsed >= longPressMs) {
                                    longPressed = true
                                    break
                                }
                                val event = withTimeoutOrNull(longPressMs - elapsed) {
                                    awaitPointerEvent()
                                }
                                if (event == null) continue
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) break
                                if (!change.pressed) {
                                    released = true
                                    break
                                }
                                // 手指在滑动（列表滚动）或被别人接管：不当成长按
                                if (change.isConsumed ||
                                    (change.position - start).getDistance() > touchSlop
                                ) {
                                    break
                                }
                            }

                            if (longPressed) {
                                if (onRight) {
                                    boosting = true
                                    setSpeed(2f)
                                } else {
                                    askDownload = true
                                }
                                // 等手指抬起。
                                // 这里不能包 runCatching / try-catch：AwaitPointerEventScope 是
                                // @RestrictsSuspension 的，只有 inline 的 suspend lambda
                                //（withTimeoutOrNull）能调用它的成员；超时也当作结束，
                                // 保证倍速一定会被恢复。
                                while (true) {
                                    val ev = withTimeoutOrNull(10_000L) { awaitPointerEvent() }
                                    if (ev == null) break
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                }
                                if (onRight) {
                                    boosting = false
                                    setSpeed(1f)
                                }
                            } else if (released) {
                                togglePlay()
                            }
                        }
                    }
                },
        )

        // 进度条：常态一条极细的线，背景透明
        Canvas(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 20.dp)
                .height(14.dp),
        ) {
            val h = trackHeight.toPx()
            val cy = size.height / 2f
            val radius = CornerRadius(h / 2f, h / 2f)

            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(0f, cy - h / 2f),
                size = Size(size.width, h),
                cornerRadius = radius,
            )
            if (fraction > 0f) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(0f, cy - h / 2f),
                    size = Size((size.width * fraction).coerceAtLeast(h), h),
                    cornerRadius = radius,
                )
            }
            val r = thumbRadius.toPx()
            drawCircle(
                color = Color.White,
                radius = r,
                center = Offset((size.width * fraction).coerceIn(r, size.width - r), cy),
            )
        }

        // 画面正中：拖动时是时间，加速时是倍速（同一个位置）
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                boosting -> Text(
                    "2×",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                dragging -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatClock(positionMs),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        " / ${formatClock(durationMs)}",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun formatClock(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}

/** 一次待确认的下载。 */
private data class DownloadRequest(
    val title: String,
    val url: String,
    val fileName: String,
)

/** 长按下载前的确认框，避免误触。 */
@Composable
private fun ConfirmDownloadDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text("文件会保存到手机的 Download 目录。") },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) { Text("下载") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
