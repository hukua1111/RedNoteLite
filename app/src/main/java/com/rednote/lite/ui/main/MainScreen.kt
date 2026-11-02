package com.rednote.lite.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rednote.lite.data.model.FeedItem
import com.rednote.lite.ui.common.LoadState
import com.rednote.lite.ui.common.itemsOrEmpty

@Composable
fun MainScreen(
    onOpenNote: (String, String) -> Unit,
    onLogin: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(FeedMode.HOME) }
    var state by remember { mutableStateOf<LoadState<List<FeedItem>>>(LoadState.Idle) }
    var cursor by remember { mutableStateOf("") }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }

    val vm = remember { MainViewModel() }
    val gridState = rememberLazyGridState()

    suspend fun load(reset: Boolean) {
        if (reset) {
            state = LoadState.Loading
            cursor = ""
        } else {
            loadingMore = true
        }
        val existing = state.itemsOrEmpty()
        val result = when (mode) {
            FeedMode.HOME -> vm.homeFeed(cursor)
            FeedMode.SEARCH -> vm.search(query, cursor)
        }
        if (result == null) {
            state = if (reset) LoadState.Error("加载失败：请确认已登录并联网") else LoadState.Success(existing)
        } else {
            state = LoadState.Success(if (reset) result.first else existing + result.first)
            cursor = result.second
            hasMore = cursor.isNotEmpty()
        }
        loadingMore = false
    }

    LaunchedEffect(mode, query) { load(reset = true) }

    LaunchedEffect(gridState) {
        snapshotFlow {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to gridState.layoutInfo.totalItemsCount
        }.collect { (last, total) ->
            if (hasMore && !loadingMore && total > 0 && last >= total - 4) {
                load(reset = false)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = query,
            onQueryChange = { query = it },
            active = mode == FeedMode.SEARCH,
            onSubmit = { if (query.isNotBlank()) mode = FeedMode.SEARCH },
            onClear = {
                query = ""
                mode = FeedMode.HOME
            },
            onLogin = onLogin,
        )

        when (val s = state) {
            is LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is LoadState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { state = LoadState.Idle }) { Text("重试") }
                }
            }

            else -> {
                val items = state.itemsOrEmpty()
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        items(items.size) { index ->
                            val item = items[index]
                            NoteCardView(item = item, onClick = { onOpenNote(item.id, item.xsecToken ?: "") })
                        }
                        if (loadingMore) {
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

private enum class FeedMode { HOME, SEARCH }

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    active: Boolean,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onLogin: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        placeholder = { Text("搜索笔记、用户、话题") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (active) {
                TextButton(onClick = onClear) { Text("取消") }
            } else {
                TextButton(onClick = onLogin) { Text("登录") }
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
    val cover = card?.cover?.urlDefault ?: card?.cover?.urlPre ?: card?.cover?.url
        ?: card?.video?.image?.urlDefault

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
