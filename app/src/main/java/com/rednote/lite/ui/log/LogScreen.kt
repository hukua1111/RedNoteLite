package com.rednote.lite.ui.log

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rednote.lite.util.AppLog
import java.io.File

/**
 * 运行日志页：把 AppLog 里的内容展示出来，支持复制 / 导出文件。
 * 用于把只在真机上复现的问题反馈出来。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(AppLog.dump()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("运行日志") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { text = AppLog.dump() }) { Text("刷新") }
                TextButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("rednote-log", text))
                        Toast.makeText(context, "已复制，可直接粘贴发送", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("复制全部") }
                TextButton(
                    onClick = {
                        val dir = context.getExternalFilesDir(null) ?: context.filesDir
                        val file = File(dir, "rednote-log.txt")
                        runCatching { file.writeText(text) }
                            .onSuccess {
                                Toast.makeText(context, "已导出：${file.absolutePath}", Toast.LENGTH_LONG).show()
                            }
                            .onFailure {
                                Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show()
                            }
                    },
                ) { Text("导出文件") }
                TextButton(onClick = { AppLog.clear(); text = AppLog.dump() }) { Text("清空") }
            }
        }
    }
}
