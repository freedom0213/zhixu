# P22 · 师生对话（WebSocket 私信）

> 用户 2026-09-17 选定「完成师生对话」。两端同一套接口与 WebSocket：
> 教师端 `/teacher/messages`（原占位页转正）+ 学生端 `/student/messages`（原半成品接真）。

## 0 · 摸底结论：后端有「通知」没有「对话」

- `tj-message-service` 只有**站内通知收件箱**（`user_inbox`：type/title/content/isRead，`sentMessageToUser` 只是往别人收件箱塞一条通知）
  与短信/公告 —— **没有会话、没有历史、没有实时**。
- 学生端 `messages.vue`（726 行）与 `api/message.js` 是按老站完整消息中心写的：
  `GET /sms/conversations`、`GET|POST /sms/messages/` —— 这些端点后端**不存在**，页面一直是打不通的半成品。
- 学生端页面已定义好了完整契约（字段/分页语义），后端**照契约实现即可让页面最小改动接真**。

## 1 · 后端（tj-message-service + user-service + 网关）

**表**（`deploy/mysql/p22-chat.sql`，已执行、幂等，库 `tj_message`）：
- `chat_conversation`：`user_low/user_high` 按 id 大小编排对（`uk_pair` 唯一键保证两人一条会话），
  `last_message/last_time`（列表预览与排序）、`unread_low/unread_high`（**未读记在会话行上、各记各的**
  → 会话列表 + 角标一次查询就够：发送时对方 +1，拉历史时自己清 0）。
- `chat_message`：conversation_id / sender_id / content(≤500) / push_time。

**REST**（`ChatController`，响应统一 **R 包装** —— 页面判 `res.code == 200`；本服务其它接口是裸返回，别混）：
- `GET /sms/conversations?pageNo&pageSize` → 会话列表（对方名/头像走 `UserClient`，查不到回落「同学」+ 默认图）
- `GET /sms/messages/?otherUserId&pageNo&pageSize` → **pageNo=1 是最新一页（页内正序）**、页码递增越早（前端 prepend）；
  副作用：清我方未读
- `POST /sms/messages/` {userId, content} → 校验（空/超 500 字/给自己/对方不存在）→ 落库 → 更新会话摘要与未读 → **WS 推送对方**
- `GET /sms/ws-ticket` → 换 WS 握手票据（见下）

**WebSocket**（`spring-boot-starter-websocket`）：
- 服务内路径 `/ws`（外部 `/sms/ws`），`ChatWebSocketHandler` + 在线注册表 `userId → Set<Session>`（支持一人多端）；
  推送失败仅 warn —— 消息已落库，离线兜底靠 REST。
- 握手身份 = **一次性票据**：客户端先 `GET /sms/ws-ticket`（正常 token 鉴权）拿 30 秒一次性票据，
  再 `new WebSocket("/sms/ws?ticket=…")`，`UserIdHandshakeInterceptor` 消费票据（用后即焚）确认身份。

**网关**：`/sms/ws` **原样转发**（不经过 P19 的请求改写）。原因见 §3。

**踩到的坑（都修了）**：
1. **message-service 缺 `spring-cloud-starter-loadbalancer`** → `@FeignClient("user-service")` 退化成 DNS 解析连 `:80` 失败
   （exam/course 的 pom 都显式带了，唯独它没有）。
2. **WS 推送 payload 里的 `LocalDateTime`** 用裸 ObjectMapper 序列化会抛异常（没有 JavaTimeModule）且被 catch 吞掉
   → 推送静默失败。时间先格式化成字符串再进 payload。

## 2 · 前端

- `api/message.js`：`openChatSocket(onChat)`（换票 → 连接 → 派发 chat 事件）、`lookupChatUser(keyword)`（按账号解析**任意**用户）。
- **教师端新页 `pages/teacher/messages.vue`**：会话栏（搜索/未读角标/时间）+ 聊天面板（日期分隔、左右气泡、加载更早、表情、代码块）
  + **「＋ 新对话」按账号发起**（`/us/users/chat-lookup`，用户新增端点 —— 原 `/lookup` 只认教师）。
  会话在第一条消息发出后才落库，发起只在本地区一个占位。
- 学生端 `messages.vue`：接 `openChatSocket`；发送失败的**服务端中文原话**不再被吞掉；「需要处理的待办」逻辑不变。
- 近期考试那页顺手修的：草稿行按钮原来跳 stats（草稿没快照没成绩）→ 改跳向导（P20 已记）。

## 3 · 网关为什么对 `/sms/ws` 原样转发（重要）

SCG 的 WS 代理与「请求改写」（P19 的 `headers(remove/add)`）冲突：走改写的 WS 升级会以
**close 1002 Protocol error** 断开（客户端收到 101 但会话秒断，且后端在线注册表为空）——
直连后端则一切正常。所以 `/sms/ws` 在网关**原样转发**，身份改由**一次性票据**保证：
票据由已鉴权的 REST 接口签发、绑定用户、30 秒过期、用后即焚 —— 不把 token 暴露在 URL，
也不给「伪造 user-info」留口子（浏览器本来也发不了这个头）。

## 4 · 实测（全绿）

| 场景 | 结果 |
|---|---|
| `chat-lookup`：讲师 2 解析 `demo` | ✅ `{id:3, name:111, type:2}` |
| 讲师 2 发第一条私信 | ✅ 200 + 消息 id |
| 学生 3 会话列表 | ✅ 1 条「演示讲师」未读 1、摘要正确 |
| 学生 3 拉历史 | ✅ total=1、内容/时间正确；**拉取后未读清 0** |
| 学生回复 → 讲师视角 | ✅ 会话未读 1、最后一条正确 |
| 边界：空内容 / 给自己发 | ✅ 400「消息内容不能为空」/「不能给自己发私信」 |
| **端到端 WS**（票据握手经网关 + 讲师发送 → 学生实时收到） | ✅ PASS，推送含完整字段（senderId/content/pushTime/对方名与头像） |
| 测试临时数据 | ✅ 已清理（两道临时题、临时草稿卷）；对话演示数据保留 |

## 5 · 遗留

- 学生端**发起**会话还没有入口（现在只能回复；页面空态文案建议「课程页联系讲师」后续做入口）。
- 图片/文件消息、表情面板扩展、会话删除/屏蔽：前端按钮已置灰「暂未开放」，等上传通路。
- WS 断线重连：当前页面刷新才重连；建议后续加指数退避重连。
- 网关仍不强制登录（P19 的遗留决定不变）。

---

## 9 · 用户测试反馈修复（P22-fix，纯前端）

| # | 反馈 | 根因 | 修复 |
|---|---|---|---|
| 1 | 「＋ 新对话」输入 `demo` 报「没找到这个账号」 | `chat-lookup` **经网关返回 R 包装**（`{code:200,data:{…}}`），前端按裸对象读 `u.id` → 永远 undefined。直连服务是裸 JSON、经网关被包成 R —— 两种形态并存 | `const u = res?.data ?? res` 解包；失败时透传后端中文原话 `res.msg`（如「平台里没有这个账号：xxx」） |
| 2 | 未读角标要等接收方**回复**才消失 | 后端「拉取即清未读」正常；漏的是**会话正开着时收到 WS 推送**：后端把未读 +1，前端只追加气泡、没触发拉取 → 角标挂着 | `onIncomingChat` 里若推送属于当前打开的会话：追加气泡后**补一次轻量拉取**（`pageNo=1&pageSize=1`，只为触发服务端清零）再刷列表。两端都改 |
| 3 | 点「插入代码块」后回不去普通输入 | 原来只会往草稿塞一对 ``` 围栏，草稿里留着 ```，看起来像被锁进代码模式（用户那条 `年号` 消息前的围栏就是它） | 改成**插入 ⇄ 退出切换**：按钮高亮 `is-on`，再点一次把围栏从草稿摘掉（光标归位）；发送 / 切换会话自动复位。两端都改 |
| 4 | 学生端 / 讲师端都没有退出登录 | AppSidebar（两端共用侧栏）没做；官网 Header 里那行「退出」是**被注释的死代码** | AppSidebar 底部加「退出登录」（已登录才显示，`store.logout()` 清 token+userInfo → `/login`）；Header.vue 的退出也接成真动作 |

> 顺带发现（未动）：Header.vue 的 `const userStore = getToken()` 拿到的是**字符串**，却在 `onBeforeMount` 里调 `userStore.logout()` —— 潜在 TypeError；本次修复用的是真正的 `useUserStore()` 实例。

验证：sfc-check 4 文件全过、vite 按需编译全 200；测试产生的 11 条聊天消息已从库中删除（保留用户自己发的「你好 / 年号」）。无后端改动，刷新页面即生效。
