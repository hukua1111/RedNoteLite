package com.rednote.lite.ui.main

import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.rednote.lite.BuildConfig
import com.rednote.lite.RedNoteApp
import com.rednote.lite.util.CacheManager
import com.rednote.lite.data.model.FeedItem
import com.rednote.lite.data.model.FollowUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 底栏两个 tab。用 0/1 存，`rememberSaveable` 对 Int 支持最稳。 */
private const val TAB_HOME = 0
private const val TAB_FOLLOWING = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenNote: (String, String) -> Unit,
    onOpenUser: (String, String, String) -> Unit,
    onLogin: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    // 数据挂在 ViewModel 上：进详情页再返回时界面会重建，状态不会丢，也就不会重新拉流
    val vm: MainViewModel = viewModel()
    val followVm: FollowingViewModel = viewModel()

    var query by rememberSaveable { mutableStateOf("") }
    var retryKey by rememberSaveable { mutableStateOf(0) }
    var tab by rememberSaveable { mutableStateOf(TAB_HOME) }
    var showFilter by remember { mutableStateOf(false) }
    var showCache by remember { mutableStateOf(false) }
    // 顶栏搜索是点右上角放大镜才展开的，默认收起
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    // 登录成功后由 LoginScreen 自增，作为 LaunchedEffect 的 key 触发一次重新拉流
    val authVersion = RedNoteApp.instance.authVersion
    val avatarUrl = RedNoteApp.instance.avatarUrl
    val nickname = RedNoteApp.instance.nickname
    val loggedIn = remember(authVersion, avatarUrl) {
        RedNoteApp.instance.signEngine.hasLoginSession() || avatarUrl.isNotBlank()
    }

    // key 相同就复用已有数据，所以从详情页返回不会再请求一次
    LaunchedEffect(vm.mode, vm.searchTerm, retryKey, authVersion) {
        vm.loadIfNeeded("${vm.mode}|${vm.searchTerm}|$retryKey|$authVersion")
    }

    LaunchedEffect(authVersion) { vm.refreshProfile() }

    fun logout() {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies { }
        cm.flush()
        // 签名页带着旧会话，销毁让它下次用干净状态重新加载
        RedNoteApp.instance.signEngine.destroy()
        RedNoteApp.instance.clearProfile()
        RedNoteApp.instance.notifyAuthChanged()
    }

    LaunchedEffect(tab, authVersion) {
        if (tab == TAB_FOLLOWING) followVm.loadIfNeeded()
    }

    LaunchedEffect(gridState) {
        snapshotFlow {
            Triple(
                gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0,
                gridState.layoutInfo.totalItemsCount,
                vm.hasMore,
            )
        }.collect { (last, total, more) ->
            if (more && total > 0 && last >= total - 4) vm.loadMore()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // enableEdgeToEdge 后界面会画到状态栏/导航栏下面，这里统一避让安全区
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        MainTopBar(
            searchOpen = searchOpen,
            query = query,
            onQueryChange = { query = it },
            searchActive = vm.mode == FeedMode.SEARCH,
            onSubmit = {
                if (query.isNotBlank()) {
                    vm.submitSearch(query)
                    retryKey++
                }
            },
            onOpenSearch = {
                // 搜索结果落在首页，所以从关注页点搜索先切回首页
                tab = TAB_HOME
                searchOpen = true
            },
            onCloseSearch = {
                searchOpen = false
                query = ""
                vm.clearSearch()
                retryKey++
            },
            avatarUrl = avatarUrl,
            nickname = nickname,
            loggedIn = loggedIn,
            onLogin = onLogin,
            onOpenLogs = onOpenLogs,
            onOpenFilter = { showFilter = true },
            onOpenCache = { showCache = true },
            onLogout = { logout() },
        )

        if (tab == TAB_HOME) {
            val visible = vm.visibleItems

            Box(Modifier.weight(1f).fillMaxWidth()) {
                PullToRefreshBox(
                    isRefreshing = vm.refreshing,
                    onRefresh = { scope.launch { vm.refresh() } },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (vm.loading && vm.items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (visible.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    StatusBlock(
                                        error = vm.error,
                                        emptyText = if (vm.filter.active) {
                                            "笔记都被筛选条件隐藏了"
                                        } else {
                                            "暂无内容"
                                        },
                                        onRetry = { retryKey++ },
                                        onLogin = onLogin,
                                        onOpenLogs = onOpenLogs,
                                        onClearFilter = if (vm.filter.active) {
                                            { vm.updateFilter(FeedFilter()) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            } else {
                                items(visible.size) { index ->
                                    val item = visible[index]
                                    val token = item.xsecToken ?: item.noteCard?.xsecToken ?: ""
                                    NoteCardView(item = item, onClick = { onOpenNote(item.id, token) })
                                }
                                if (vm.loadingMore) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Box(
                                            Modifier.fillMaxWidth().padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) { CircularProgressIndicator() }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            FollowingContent(
                vm = followVm,
                onOpenUser = onOpenUser,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }

        NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
            NavigationBarItem(
                selected = tab == TAB_HOME,
                onClick = {
                    if (tab == TAB_HOME) {
                        // 已经在首页：等同下拉刷新
                        scope.launch { vm.refresh() }
                    } else {
                        // 从别的 tab 回首页：只切 tab，不刷新
                        tab = TAB_HOME
                    }
                },
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                label = { Text("首页") },
            )
            NavigationBarItem(
                selected = tab == TAB_FOLLOWING,
                onClick = { tab = TAB_FOLLOWING },
                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                label = { Text("关注") },
            )
        }
    }

    if (showFilter) {
        FilterDialog(
            initial = vm.filter,
            onDismiss = { showFilter = false },
            onApply = {
                vm.updateFilter(it)
                showFilter = false
            },
        )
    }

    if (showCache) {
        CacheDialog(onDismiss = { showCache = false })
    }
}

/**
 * 缓存管理。
 *
 * 判断依据从「笔记条数」改成「占用大小」：滑块选 2~8 GB 的上限，
 * 超过就清到上限的 40%，删的时候从最旧的文件开始。
 */
@Composable
private fun CacheDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var threshold by remember { mutableStateOf(CacheManager.thresholdGb(context)) }
    var usage by remember { mutableStateOf(-1L) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    suspend fun refreshUsage() {
        usage = withContext(Dispatchers.IO) { CacheManager.usageBytes(context) }
    }

    LaunchedEffect(Unit) { refreshUsage() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("缓存管理") },
        text = {
            Column {
                Text(
                    "当前占用 " + if (usage < 0) "计算中…" else CacheManager.formatSize(usage),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "占用上限 %.1f GB".format(threshold),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Slider(
                    value = threshold,
                    // 0.5 GB 一档
                    onValueChange = { threshold = (it * 2f).roundToInt() / 2f },
                    valueRange = CacheManager.MIN_GB..CacheManager.MAX_GB,
                    steps = 11,
                )
                Text(
                    "超过上限时自动清理到 %.1f GB（上限的 40%%），优先删最旧的。"
                        .format(threshold * 0.4f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (message.isNotEmpty()) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (busy) return@OutlinedButton
                        busy = true
                        scope.launch {
                            CacheManager.setThresholdGb(context, threshold)
                            val freed = withContext(Dispatchers.IO) {
                                CacheManager.clearToTarget(context)
                            }
                            refreshUsage()
                            message = if (freed > 0) {
                                "已释放 ${CacheManager.formatSize(freed)}"
                            } else {
                                "占用已低于清理目标，无需清理"
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(if (busy) "清理中…" else "立即清理")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    CacheManager.setThresholdGb(context, threshold)
                    onDismiss()
                },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun FilterDialog(
    initial: FeedFilter,
    onDismiss: () -> Unit,
    onApply: (FeedFilter) -> Unit,
) {
    var keyword by remember { mutableStateOf(initial.keyword) }
    var likes by remember { mutableStateOf(if (initial.minLikes > 0) initial.minLikes.toString() else "") }
    var comments by remember {
        mutableStateOf(if (initial.minComments > 0) initial.minComments.toString() else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("筛选（隐藏不想要的）") },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("关键词（多个用 / 分隔，命中即隐藏）") },
                    placeholder = { Text("例如：广告/代购/链接") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = likes,
                    onValueChange = { input -> likes = input.filter { it.isDigit() } },
                    label = { Text("点赞少于 N 则隐藏") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = comments,
                    onValueChange = { input -> comments = input.filter { it.isDigit() } },
                    label = { Text("评论少于 N 则隐藏") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        FeedFilter(
                            keyword = keyword,
                            minLikes = likes.toIntOrNull() ?: 0,
                            minComments = comments.toIntOrNull() ?: 0,
                        ),
                    )
                },
            ) { Text("应用") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply(FeedFilter()) }) { Text("清除") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun FollowingContent(
    vm: FollowingViewModel,
    onOpenUser: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Box(modifier) {
        when {
            vm.loading && vm.users.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            vm.users.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        vm.error ?: "还没有关注任何人",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { scope.launch { vm.refresh() } }) { Text("刷新") }
                }
            }

            else -> PullToRefreshBox(
                isRefreshing = vm.refreshing,
                onRefresh = { scope.launch { vm.refresh() } },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(count = vm.users.size) { index ->
                        val user = vm.users[index]
                        FollowRow(
                            user = user,
                            unfollowing = vm.busyUserId == user.userId,
                            onClick = {
                                onOpenUser(user.userId, user.nickname, user.avatarUrl ?: "")
                            },
                            onUnfollow = {
                                scope.launch {
                                    val result = vm.unfollow(user.userId)
                                    if (result.isNotBlank()) {
                                        Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowRow(
    user: FollowUser,
    unfollowing: Boolean,
    onClick: () -> Unit,
    onUnfollow: () -> Unit,
) {
    Surface(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!user.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = user.nickname,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                )
            } else {
                Box(
                    Modifier.size(48.dp).clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(user.nickname.take(1).ifBlank { "?" })
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    user.nickname.ifBlank { "用户" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!user.desc.isNullOrBlank()) {
                    Text(
                        user.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // 右侧按钮：点一下取关（这里原本只是个不可点的状态标）
            OutlinedButton(
                onClick = onUnfollow,
                enabled = !unfollowing,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Text(
                    if (unfollowing) "处理中…" else "已关注",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun StatusBlock(
    error: String?,
    emptyText: String = "暂无内容",
    onRetry: () -> Unit,
    onLogin: () -> Unit,
    onOpenLogs: () -> Unit,
    onClearFilter: (() -> Unit)? = null,
) {
    Box(
        Modifier.fillMaxWidth().padding(top = 96.dp, start = 24.dp, end = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (error == null) {
                Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // 整个列表都被筛掉时给个出口，否则只能翻头像菜单
                val clear = onClearFilter
                if (clear != null) {
                    TextButton(onClick = clear, modifier = Modifier.padding(top = 8.dp)) {
                        Text("清除筛选")
                    }
                }
            } else {
                Text("内容加载失败", style = MaterialTheme.typography.titleSmall)
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    TextButton(onClick = onRetry) { Text("重试") }
                    TextButton(onClick = onLogin) { Text("重新登录") }
                    TextButton(onClick = onOpenLogs) { Text("查看日志") }
                }
            }
        }
    }
}

@Composable
private fun MainTopBar(
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    searchActive: Boolean,
    onSubmit: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    avatarUrl: String,
    nickname: String,
    loggedIn: Boolean,
    onLogin: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenFilter: () -> Unit,
    onOpenCache: () -> Unit,
    onLogout: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于") },
            text = {
                Text(
                    "第三方非官方客户端，仅供学习与技术研究，内容版权归小红书及原作者所有。\n\n" +
                        "版本 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("知道了") }
            },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchOpen) {
            IconButton(onClick = onCloseSearch) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出搜索")
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text("搜索笔记、用户、话题") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                trailingIcon = {
                    if (searchActive) TextButton(onClick = onCloseSearch) { Text("取消") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            )
            LaunchedEffect(Unit) {
                // 等一帧再要焦点，否则输入框可能还没挂到窗口上
                delay(120)
                runCatching { focus.requestFocus() }
            }
        } else {
            // 左上角：菜单（原头像菜单 + 设置页的内容都收在这里）
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.Menu, contentDescription = "菜单")
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.width(268.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (loggedIn && avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "头像",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(40.dp).clip(CircleShape),
                            )
                        } else {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (loggedIn) "我" else "?",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(
                                if (loggedIn) nickname.ifBlank { "已登录" } else "未登录",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (loggedIn) "登录状态：正常" else "登录后才能浏览笔记",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("筛选") },
                        onClick = {
                            menuOpen = false
                            onOpenFilter()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("缓存管理") },
                        onClick = {
                            menuOpen = false
                            onOpenCache()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("日志记录") },
                        onClick = {
                            menuOpen = false
                            onOpenLogs()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("关于") },
                        onClick = {
                            menuOpen = false
                            showAbout = true
                        },
                    )
                    HorizontalDivider()
                    if (loggedIn) {
                        DropdownMenuItem(
                            text = { Text("退出登录") },
                            onClick = {
                                menuOpen = false
                                onLogout()
                            },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("登录") },
                            onClick = {
                                menuOpen = false
                                onLogin()
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // 右上角：搜索
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Default.Search, contentDescription = "搜索")
            }
        }
    }
}

@Composable
private fun NoteCardView(item: FeedItem, onClick: () -> Unit) {
    val card = item.noteCard
    val title = card?.displayTitle ?: card?.title ?: ""
    val cover = card?.cover?.bestUrl ?: card?.video?.image?.bestUrl

    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                if (!cover.isNullOrEmpty()) {
                    AsyncImage(
                        model = cover,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("无封面", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (card?.type == "video") {
                    Surface(
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    ) {
                        Text(
                            "▶ 视频",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                text = title.ifEmpty { "无标题" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = card?.user?.nickname.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "♥ " + (card?.interactInfo?.likedCount ?: "0"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
