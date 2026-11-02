package com.rednote.lite.ui.settings

import android.webkit.CookieManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rednote.lite.BuildConfig
import com.rednote.lite.RedNoteApp

/**
 * 设置页：账号状态、退出登录、关于信息。
 * 运行日志的入口放在首页头像的弹出菜单里，这里不再重复放一个大按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    val app = RedNoteApp.instance
    val avatar = app.avatarUrl
    val nickname = app.nickname
    val loggedIn = app.signEngine.hasLoginSession()

    fun logout() {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies { }
        cm.flush()
        // 签名页带着旧会话，销毁让它下次用干净状态重新加载
        app.signEngine.destroy()
        app.clearProfile()
        app.notifyAuthChanged()
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- 账号 ----
            Surface {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Box(
                        Modifier.size(56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (loggedIn && avatar.isNotBlank()) {
                            AsyncImage(
                                model = avatar,
                                contentDescription = "头像",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(CircleShape),
                            )
                        } else {
                            Text(
                                if (loggedIn) "已登录" else "未登录",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = if (loggedIn) nickname.ifBlank { "已登录" } else "未登录",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = if (loggedIn) "登录状态：正常" else "登录后才能浏览首页与笔记详情",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            HorizontalDivider()

            SettingRow(title = "运行日志", subtitle = "查看请求与响应，便于排查问题", onClick = onOpenLogs)

            if (loggedIn) {
                HorizontalDivider()
                SettingRow(title = "退出登录", subtitle = "清除 Cookie 与本地账号信息", onClick = { logout() })
            }

            HorizontalDivider()
            SettingRow(
                title = "版本",
                subtitle = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            )
            HorizontalDivider()
            SettingRow(
                title = "关于",
                subtitle = "第三方非官方客户端，仅供学习与技术研究，内容版权归小红书及原作者所有",
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
