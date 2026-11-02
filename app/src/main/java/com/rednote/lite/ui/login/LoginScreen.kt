package com.rednote.lite.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rednote.lite.RedNoteApp
import com.rednote.lite.data.sign.SignEngine

/**
 * 登录页：加载小红书网页版，用户扫码或账号密码登录后，
 * 我们直接从 WebView 的 Cookie 里取 `web_session`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onClose: () -> Unit) {
    val app = RedNoteApp.instance
    val loading = remember { mutableStateOf(true) }
    val progress = remember { mutableStateOf(0f) }
    val loggedIn = remember { mutableStateOf(app.signEngine.hasLoginSession()) }

    fun refreshLoginState() {
        val cookie = CookieManager.getInstance().getCookie("https://www.xiaohongshu.com") ?: return
        val has = cookie.split(";").any { it.trim().startsWith("web_session=") && it.substringAfter("=").isNotBlank() }
        loggedIn.value = has
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录小红书") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading.value) {
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.userAgentString = SignEngine.USER_AGENT
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    loading.value = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading.value = false
                                    CookieManager.getInstance().flush()
                                    refreshLoginState()
                                    Log.d("LoginScreen", "page finished: $url loggedIn=${loggedIn.value}")
                                }
                            }
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress.value = newProgress / 100f
                                }
                            }
                            loadUrl("https://www.xiaohongshu.com/explore")
                        }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (loggedIn.value) {
                    Button(onClick = onClose, modifier = Modifier.weight(1f)) {
                        Text("已登录，继续使用")
                    }
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "请在页面内扫码或账号登录",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "登录成功后按钮会自动出现",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}
