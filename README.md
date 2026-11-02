# xhs Lite (RedNote Lite)

一个基于 **xhs 网页版接口** 的第三方 Android 客户端，使用 Kotlin + Jetpack Compose (Material 3) 构建，通过 GitHub Actions 自动打包签名 APK。

> 项目结构参考 [TiebaApp](https://github.com/Aur5411/TiebaApp) 的构建约定。

---

## ⚠️ 免责声明

- 本项目为**第三方非官方客户端**，仅供**学习与技术研究**使用。
- 所有内容（文字、图片、视频、评论等）版权归 **xhs 及原始作者**所有。
- 请勿将本项目用于商业用途；使用产生的一切后果由使用者自行承担。
- 项目不含任何破解、绕过付费或批量抓取功能；数据全部来自用户本人已登录账号可见的页面。
- 如有侵权，请联系删除。

---

## 功能

- 🏠 首页推荐信息流（瀑布流双列）
- 🔍 关键词搜索笔记
- 📄 笔记详情（多图轮播、正文、话题标签、作者信息）
- 💬 笔记评论列表
- 🔐 内置登录页（WebView 以**桌面版 UA**加载 PC 版页面，扫码/手机号/账号密码登录，自动获取会话 Cookie）
- 👤 登录后头像菜单（登录状态 / 设置 / 运行日志）

## 技术实现

### 架构

```
ui/          Compose 界面（首页 / 搜索 / 详情 / 登录 / 设置 / 日志）
data/
  remote/    OkHttp + kotlinx.serialization 的 API 客户端
  model/     数据模型
  sign/      ★ 隐藏 WebView 签名引擎
```

### 关于签名（重点）

xhs 网页版接口（`edith.xiaohongshu.com`）的读接口在网关层要求 `X-s` 请求头，其中 `x3` 字段必须由站点自身的反爬 SDK `window.mnsv2` 生成。

实测结论（详见 `ref/xhs/FINDINGS.md`）：

| 接口 | 是否需要 mnsv2 签名 |
|---|---|
| `/api/sns/web/v1/search/notes` | ❌ 只需登录 Cookie |
| `/api/sns/web/v1/homefeed` | ✅ 需要 |
| `/api/sns/web/v1/feed`（笔记详情） | ✅ 需要 |
| `/api/sns/web/v2/comment/page` | ✅ 需要 |
| `/api/sns/web/v1/config` | ❌ 完全公开 |

`mnsv2` 是混淆的字节码 VM（mns0301 blobs，约 233 KB hex），无法脱离浏览器环境复刻。因此本项目采用**混合方案**：

1. App 内创建一个 1×1 像素的**隐藏 WebView**，加载 `https://www.xiaohongshu.com/explore`；
2. 等页面注入 `window.mnsv2` 后，通过 `evaluateJavascript` 在**页面内**调用官方签名函数；
3. 拿到 `X-s` / `X-t` / `X-S-Common` 后，用原生 OkHttp 发起请求，原生 Compose 渲染结果。

这样既保证签名长期有效（永远用官方签名逻辑），又保持原生 UI 体验。

> 注：也曾试过直接在页面里 `fetch`、让站点的 XHR 拦截器补签名，但实测站点并不拦截普通 `fetch`，feed / comment 一律返回 406，因此该路径已移除，只保留「页面内取签名 + 原生请求」。

## 构建

### 本地构建

```bash
# 需要 JDK 17 + Android SDK 36
./gradlew :app:assembleRelease
```

产物：`app/build/outputs/apk/release/rednote-release-<version>.apk`

### 签名

APK 使用固定 keystore 签名，保证每次构建签名一致，可直接覆盖安装升级。

密钥**不随仓库分发**，CI 构建时由 GitHub Actions Secrets 还原（见 `.github/workflows/build.yml` 的 `Prepare signing config` 步骤）。请在本仓库 **Settings → Secrets and variables → Actions** 中配置：

| Secret | 说明 |
|---|---|
| `KEYSTORE_JKS_BASE64` | keystore 的 base64 内容（`base64 -w0 rednote.jks`） |
| `KEYSTORE_PASSWORD` | keystore 密码（同时用作 key 密码） |
| `KEYSTORE_ALIAS` | 密钥别名，默认 `rednote` |

本地构建时，在**项目根目录**自行创建 `keystore.properties`（已被 `.gitignore` 忽略）：

```properties
keystore.file=keystore/rednote.jks
keystore.password=<你的密码>
keystore.alias=rednote
keystore.key.password=<你的密码>
```

> 🔒 `keystore.properties` 与 `*.jks` 一律不得提交到仓库。未配置 Secrets 时（例如 fork 或 PR 构建），会退化为 debug 签名，仅用于验证编译。

### CI

推送到 `main` 或打 tag（`v*`）时，GitHub Actions 会自动：
1. 从 Secrets 还原签名配置
2. 配置 JDK 17 + Android SDK 36
3. 构建 release + debug APK
4. 如果是 tag 推送，自动创建 Release 并上传 APK

## 目录说明

| 路径 | 说明 |
|---|---|
| `app/src/main/java/com/rednote/lite/data/sign/SignEngine.kt` | 隐藏 WebView 签名引擎 |
| `app/src/main/java/com/rednote/lite/data/remote/XhsApi.kt` | 接口封装 |
| `app/src/main/java/com/rednote/lite/ui/` | Compose 界面 |
| `.github/workflows/build.yml` | CI 构建 & 发布 |
| `ref/xhs/FINDINGS.md` | 接口探测报告 |

## License

GPL-3.0（与参考项目保持一致）
