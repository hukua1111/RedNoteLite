# 小红书网页版接口探测结论（2026-11-02 实测）

## 环境
- 出口：本机 node 24 的 `fetch`（PowerShell/.NET TLS 不可用）
- 目标：`https://edith.xiaohongshu.com`（PC web 接口域）

## 实测矩阵（POST，匿名 a1/webId，无 web_session）

| 端点 | 无签名 | 带 v1 | 带 v2(伪造 x3) | 结论 |
|---|---|---|---|---|
| `/api/sns/web/v1/config` | 200 code=0 | 200 code=0 | 200 code=0 | 公开接口，无需签名 |
| `/api/sns/web/v1/search/notes` | 200 code=-101 无登录信息 | 同 | 同 | **签名不被校验**，只需登录 Cookie |
| `/api/sns/web/v1/feed` | **406** | 406 | 406 | 网关层拦截（需真实 mnsv2 签名或登录） |
| `/api/sns/web/v1/homefeed` | **406** | 406 | 406 | 同上 |
| `/api/sns/web/v1/homefeed/initial_load` | 406 | 406 | 406 | 同上 |
| `/api/sns/web/v1/user/info` | 406 | 406 | 406 | 同上 |
| `/api/sns/web/v1/search/usersearch` | 406 | 406 | 406 | 同上 |
| `/api/sns/web/v1/login/qrcode/create` | 406 | 406 | 406 | 同上 |
| `/api/sns/web/v2/comment/page` | **404** | 404 | 404 | 该路径在 edith 不存在 |
| `/api/sns/web/v1/user_posted`、`/user/otherinfo` | 404 | 404 | 404 | 同上 |

## 关键判断
1. **`search/notes` 是唯一"仅需登录、不校验签名"的读接口**；返回 `-101` 说明请求已进入业务层。
2. 其余读接口（feed/homefeed/comment/user）在**网关层**返回 406，签名占位值无效 —— 这些接口必须由真实浏览器环境（`window.mnsv2`）生成 `X-s.x3`。
3. `window.mnsv2` 本体是混淆 VM（`mns0301` blob，233082 hex 字节），在 node 沙箱内不安装 `mnsv2`，仅导出 16 个内部 helper：**无法脱机复刻**。
4. 因此**纯原生 API 路线只能覆盖搜索**；信息流、笔记详情、评论、用户主页必须走 WebView 内页内签名（`evaluateJavascript` 调站点自身 `mnsv2`）。

## 待办（需要用户输入）
- 登录 Cookie（`web_session`）——用于打通 search 链路并验证 UI 与数据层。
- GitHub PAT —— 用于建仓 / 推送 / 触发 Actions 出包。
