# P36 · 全项目体检（推送前）

> 2026-09-18 · 用户要求：整体检查一遍项目（不含 P33 清单的第二档、第三档），确认无潜在 bug 风险后推到远程 Git。
> 本文所有结论**均来自实测**（编译 / 静态校验 / 接口探活 / 数据库查询 / 容器日志），不是凭印象。

---

## 1 · 体检维度与结论

| 维度 | 方法 | 结论 |
|---|---|---|
| 后端编译 | 根目录全量 `package -DskipTests` | ✅ **BUILD SUCCESS**，0 错误 |
| 前端语法 | `node --check` × 51 个 js；SFC 校验 × 159 个 vue | ✅ **259 个文件 0 错误** |
| 接口契约 | 前端 197 个请求点 × 后端 390 个端点（脚本比对路径 + HTTP 方法） | ⚠️ 方法不匹配 **1 处**（且为死代码，见 §3.1） |
| 服务探活 | 网关 6 个前缀实际请求 | ✅ 全部可达（404/401 = 服务活着） |
| 容器状态 | `docker ps` | ⚠️ 23 个容器中 **22 正常**，`pay-service` 崩溃重启（见 §2.1） |
| 数据完整性 | 题库 / 课程 / 学习记录 / 收件箱 | ✅ 无异常（收件箱 6 条**零重复**，P35 修复在数据上确认） |
| 敏感信息 | 扫描 1691 个待提交文件 | ✅ 无真实凭据泄漏（仅一处本地默认口令，见 §3.3） |

---

## 2 · 需要决策的两项

### 2.1 🔴 `pay-service` 无限重启 —— 微信支付证书类配置缺失

**现象**：`docker ps` 里该容器长期是 `Restarting (1)`。

**根因**（容器日志，不是猜的）：

```
Caused by: java.lang.NullPointerException
  at cn.hutool.crypto.PemUtil.readPemKey(PemUtil.java:66)
  at com.tianji.pay.third.wx.config.WxConfiguration.certificatesManager(WxConfiguration.java:33)
```

`WxConfiguration` 在**启动时**就要用私钥构造 `CertificatesManager`，而本地 `.env` 里 `WX_PAY_PRIVATE_KEY` 等是空值 → `PemUtil` 拿到 null → NPE → 应用启动失败 → Compose 的 `restart` 策略让它无限重启。

**影响**：不影响任何业务功能（支付链路本就是配置占位），但会让 `docker ps` 一直显示 `Restarting`、日志不断刷屏，演示时观感不佳，也一直空耗资源。

**两个处理方案**（都不动业务代码）：

| 方案 | 做法 | 特点 |
|---|---|---|
| **A. 让它安静地停着**（推荐） | 在 `deploy/docker-compose.services.yml` 给 `pay-service` 单独加 `restart: "no"`，或直接 `docker compose stop pay-service` | 零风险；`docker ps` 干净；需要支付演示时再手动起 |
| B. 补齐证书配置 | 在 `deploy/.env` 填真实微信支付私钥 / 序列号 / APIv3 密钥 | 需要真实商户资质，本地演示无必要 |

> ⚠️ 这是**改动运行环境**的操作，按项目约定需你确认后再动。

### 2.2 🔴 `PUT /ct/file/update` 后端不存在 → 学员端「知识库编辑」必然失败

**实测**：

```
PUT  /ct/file/update → HTTP 405
POST /ct/file/update → HTTP 405
```

**证据链**：

- 前端 `api/ai.js` 的 `updateMarkdown` 用 `PUT ${AI_API_PREFIX}/file/update`
- 调用点：`pages/student/ai.vue` → `components/KnowledgePanel.vue` 的 `submitEditForm`
- 后端 `AiController` 只有：`POST /file/upload`、`GET /file/page`、`GET /file/{id}`、`DELETE /file/{id}` —— **没有任何 file 的更新端点**

**影响**：学员端 AI 页里，打开知识库抽屉 → 编辑某个文档 → 保存，**一定保存不上**。这是 P35 同类型的缺陷（前端假设了一个后端没实现的接口），只是还没被触发过。

**两种处理方案**：

| 方案 | 做法 | 特点 |
|---|---|---|
| **A. 补后端端点**（推荐） | 在 `AiController` 加 `PUT /file/{id}`（或按前端期望的 `/file/update`），更新 Markdown 正文 + 重新分块向量化 | 改动小（约 30 行，复用现有 `KnowledgeService` 的写入/分块逻辑），**但需要重建 ai-service 容器** |
| B. 先摘掉入口 | 隐藏「编辑」按钮，避免演示时点到 | 最小改动，但少一个功能 |

---

## 3 · 观察项（不阻塞推送）

### 3.1 ⚪ 契约不匹配 1 处 —— 是死代码，不是线上 bug

| 前端 | 后端 | 判定 |
|---|---|---|
| `api/notes.js` → `collectionNotes()`：`POST /ls/notes/{id}` | `NoteController` 只有 `PUT /notes/{id}` / `DELETE /notes/{id}` | **该函数无任何调用点**（全项目搜索为 0）→ 死代码 |

顺带发现一批同样**无调用点**的死函数（不影响运行，建议清理）：

```
api/notes.js   collectionNotes、unNotesGathers、unLikeed
api/class.js   getLearningPlan、submitExamRecords
api/message.js createGroup、addGroupMember、removeGroupMember、getGroupMembers
api/user.js    wxLogins
```

### 3.2 🟡 老站路由仍挂载 13 条，其中若干接口后端已不存在

`router/modules/legacy.js` 仍挂了 `/main/*`、`/personal/*`、`/pay/*`、`/details/*`、`/learning/*`、`/search/*`、`/askDetails/*`。

这些是**被学员端取代的旧页面**（前端源码已从 Git 排除，但路由仍在）。其中这些调用**后端没有对应实现**，一旦误点进老页面就会报错：

```
/ls/share/generate/{id}      /ls/share/{code}
/sms/chat/groups*            /sms/chat/messages/group      /sms/online-count
/ts/pay/*                    /ms 相关旧媒资接口
```

**建议**：演示前明确不引导到老站入口即可；要彻底干净就得摘掉 `legacy.js`（**但老学习页的 components 子目录仍被新学习页复用，不能整体删**，需逐条核对，属中等改动）。本次不动。

### 3.3 🟡 两处轻微项

1. **`AiProperties.java` 里 `private String pgPassword = "tianji123";`** —— 本地 pgvector 默认口令硬编码。它同时出现在 `deploy/.env.example`（那里是正确做法），代码里作为兜底默认值。公开仓库会暴露这个**本地演示用**口令，风险低（非真实生产凭据），但严格说应改为纯环境变量注入。
2. **题库 2 道历史题的 `cate_id1` 为空**（含 P11 时期造的 `9001`，其解析在库里是乱码）—— 属 P33 第三档「历史半截数据」，按你的要求本次不处理；已确认**不会导致页面打不开**（P16 那类脏数据才是致命的）。

### 3.4 ✅ 一项目标已达成：空 `catch {}` 归零

全项目扫描"完全空的 catch"：**自身代码 0 处**，命中全部集中在压缩后的第三方播放器（`assets/tcadpter/*.min.js`）。
说明 P35 定下的规则（"`catch` 里至少留一条 `console.warn`，别让失败无声无息"）在自己的代码里已经落实。

---

## 4 · 审计方法（可复现）

```bash
# 后端全量编译
mvn -q package -DskipTests

# 前端静态校验
node --check <每个 .js>
node sfc-check.cjs <每个 .vue>

# 接口契约比对（脚本：解析 @XxxMapping 与前端 request/call 的 url+method，归一化网关前缀后比对）
python .workbuddy/contract-audit.py
```

> 契约脚本有两个易踩的解析盲区，已在第二轮修正，记录备查：
> ① 类级 `@RequestMapping` 的**数组写法** `@RequestMapping({"/a","/b"})`；
> ② 类级路径**没有前导斜杠**（如 `@RequestMapping("categorys")`）时拼接会漏掉 `/`。
> 第一轮没修这两个，误报了 60 多条"接口不存在"——所以**工具输出的结论必须逐条人工核对**，不能直接当结论。

---

## 5 · 结论

- **代码层面无阻塞性问题**：能编译、能跑、前端零语法错误、契约基本对齐、数据干净。
- **两项待你决策**：`pay-service` 重启策略（§2.1）、知识库编辑端点（§2.2）。
- 其余均为观察项，不影响推送到远程仓库。
