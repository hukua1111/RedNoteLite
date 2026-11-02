package com.rednote.lite.ui.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.rednote.lite.RedNoteApp
import com.rednote.lite.data.model.UserNoteItem
import com.rednote.lite.util.toHttpsUrl

class UserProfileViewModel : ViewModel() {

    private val api = RedNoteApp.instance.api

    var notes by mutableStateOf<List<UserNoteItem>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set

    private var cursor = ""
    private var currentUserId: String? = null

    suspend fun loadIfNeeded(userId: String) {
        if (currentUserId == userId && (notes.isNotEmpty() || error != null)) return
        currentUserId = userId
        loading = true
        error = null
        cursor = ""
        val result = api.userNotes(userId)
        if (result == null) {
            error = api.lastError.ifBlank { "未知错误" }
            hasMore = false
        } else {
            notes = result.notes
            cursor = result.cursor ?: ""
            hasMore = result.hasMore
        }
        loading = false
    }

    suspend fun loadMore() {
        val uid = currentUserId ?: return
        if (loading || !hasMore) return
        val result = api.userNotes(uid, cursor) ?: return
        notes = notes + result.notes
        cursor = result.cursor ?: ""
        hasMore = result.hasMore
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: String,
    nickname: String,
    avatar: String,
    onBack: () -> Unit,
    onOpenNote: (String, String) -> Unit,
) {
    val vm: UserProfileViewModel = viewModel()
    LaunchedEffect(userId) { vm.loadIfNeeded(userId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        nickname.ifBlank { "用户主页" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ProfileHeader(userId = userId, nickname = nickname, avatar = avatar)
                }

                if (vm.loading && vm.notes.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                } else if (vm.notes.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                vm.error ?: "TA 还没有发布笔记",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(vm.notes.size) { index ->
                        val note = vm.notes[index]
                        NoteTile(note = note) {
                            onOpenNote(note.noteId, note.xsecToken ?: "")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(userId: String, nickname: String, avatar: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (avatar.isNotBlank()) {
                AsyncImage(
                    model = avatar,
                    contentDescription = nickname,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(CircleShape),
                )
            } else {
                Box(
                    Modifier.size(64.dp).clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(nickname.take(1).ifBlank { "?" }, style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    nickname.ifBlank { "用户" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "ID: $userId",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp))
        Text(
            "TA 的笔记",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun NoteTile(note: UserNoteItem, onClick: () -> Unit) {
    val cover = note.bestCover
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(0.75f)) {
                if (!cover.isNullOrBlank()) {
                    AsyncImage(
                        model = cover.toHttpsUrl(),
                        contentDescription = note.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("无封面", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(
                note.displayTitle ?: "无标题",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
