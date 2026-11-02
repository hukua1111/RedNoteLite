package com.rednote.lite.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.rednote.lite.RedNoteApp
import com.rednote.lite.data.model.FeedItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenNote: (String, String) -> Unit,
    onLogin: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // 数据挂在 ViewModel 上：进详情页再返回时界面会重建，状态不会丢，也就不会重新拉流
    val vm: MainViewModel = viewModel()

    var query by rememberSaveable { mutableStateOf("") }
    var retryKey by rememberSaveable { mutableStateOf(0) }
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
        SearchBar(
            query = query,
            onQueryChange = { query = it },
            active = vm.mode == FeedMode.SEARCH,
            avatarUrl = avatarUrl,
            nickname = nickname,
            loggedIn = loggedIn,
            onSubmit = {
                if (query.isNotBlank()) {
                    vm.submitSearch(query)
                    retryKey++
                }
            },
            onClear = {
                query = ""
                vm.clearSearch()
                retryKey++
            },
            onLogin = onLogin,
            onOpenLogs = onOpenLogs,
            onOpenSettings = onOpenSettings,
        )

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
                    if (vm.items.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            StatusBlock(
                                error = vm.error,
                                onRetry = { retryKey++ },
                                onLogin = onLogin,
                                onOpenLogs = onOpenLogs,
                            )
                        }
                    } else {
                        items(vm.items.size) { index ->
                            val item = vm.items[index]
                            // xsec_token 有的接口放在外层、有的塞在 note_card 里，两处都兜一下
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
}

@Composable
private fun StatusBlock(
    error: String?,
    onRetry: () -> Unit,
    onLogin: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().padding(top = 96.dp, start = 24.dp, end = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (error == null) {
                Text("暂无内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    active: Boolean,
    avatarUrl: String,
    nickname: String,
    loggedIn: Boolean,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onLogin: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        placeholder = { Text("搜索笔记、用户、话题") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            when {
                active -> TextButton(onClick = onClear) { Text("取消") }
                // 已登录：头像按钮，点开是账号菜单
                loggedIn -> Box {
                    Surface(
                        onClick = { menuOpen = true },
                        shape = CircleShape,
                        tonalElevation = 2.dp,
                        modifier = Modifier.padding(end = 8.dp).size(32.dp),
                    ) {
                        if (avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "我的账号",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            // 头像还没取到时先占位，避免按钮突然消失
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("我", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                nickname.ifBlank { "已登录" },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "登录状态：正常",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("设置") },
                            onClick = {
                                menuOpen = false
                                onOpenSettings()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("日志记录") },
                            onClick = {
                                menuOpen = false
                                onOpenLogs()
                            },
                        )
                    }
                }
                else -> TextButton(onClick = onLogin) { Text("登录") }
            }
        },
        shape = RoundedCornerShape(24.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
    )
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
