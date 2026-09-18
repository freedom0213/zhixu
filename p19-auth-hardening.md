# P19 · 鉴权加固

> 用户 2026-09-16 要求「修鉴权」。触发点是 P18 顺手发现的两条，动手时又挖出两条更严重的。
> 涉及模块：`tj-gateway`（1 处）、`tj-exam`（5 处）、教师端 api 层（1 处）。

## 0 · 摸清的真实问题（比 P18 报告里写的多）

| # | 问题 | 严重度 | 证据 |
|---|---|---|---|
| 1 | **网关原样透传客户端自带的 `user-info`** | 🔴 可冒充任意用户 | 免登录路径直接放行，`curl :10010/es/... -H 'user-info: 2'`（**不带任何 token**）就返回讲师 2 的数据 |
| 2 | **`GET /es/exams/{id}` 把正确答案与解析一起返回** | 🔴 泄题 | `expandedItems[].answer` / `.analysis` 对**任何**登录用户可见 → 学生考前去读一遍就能拿满分 |
| 3 | **讲师侧 4 个接口无归属校验** | 🔴 | `exams/{id}/records`、`exams/{id}/statistics`、`exam-records/{id}/result`、`exam-records/{id}/review`（**写**接口）—— 任何登录用户可读全场成绩、可改复核状态 |
| 4 | **题库「编辑 / 删除题目」无归属校验** | 🔴 | `PUT /es/questions/{id}`、`DELETE /es/questions/{id}` —— 能改/删别人的题（含答案）。`updateStatus` 与 `visibility` 早就有校验，这两个漏了 |
| 5 | **考试列表 / 计数无归属过滤** | 🟠 越权可见 | `GET /es/exams/page`、`/count` 返回**全平台**卷子，讲师看得到别人的考试 |

根因（1）：`AccountAuthFilter` 里
```java
if (isExcludePath(antPath)) { return chain.filter(exchange); }   // ← 原样放行，含伪造头
...
if (r.success()) { builder.header(USER_HEADER, userId) }         // ← 仅"已登录"时才覆盖
```
且本地 `tj.auth.exclude-path: []` + Redis 里没配权限路径 → `checkAuth` 的 `findMatchPath` 返回 null → **所有请求都匿名放行**。

## 1 · 改法

**网关（`AccountAuthFilter`）**：进门先无条件剥掉 `user-info`，之后**只可能**由网关从 token 里写入。
```java
ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
        .headers(headers -> headers.remove(USER_HEADER));   // 身份只能由网关注入
if (isExcludePath(antPath)) return chain.filter(exchange.mutate().request(builder.build()).build());
...
if (r.success()) builder.header(USER_HEADER, r.getData().getUserId().toString());
```

**考试答卷侧（`ExamAnswerServiceImpl`）**：新增 `requireExamOwner(exam)`（未登录 → 「请先登录」；非作者 → 「这场考试不是你创建的，不能查看或批改别人的考试」），
挂到 `recordsOfExam` / `statistics` / `resultOfRecord` / `review` 四处。

**考试侧（`ExamServiceImpl`）**：
- `queryExamDetail`：作者全量；**非作者**——草稿直接拒（`这场考试还是草稿，只有创建者能查看`），已发布可看卷面但**抹掉 `answer` / `analysis`**。
- `queryExamPage` / `countExams` 加 `mine` 开关（教师端传 1）→ `creater = 我`；不传时行为不变（老页面/学生页不受影响）。

**题库（`QuestionServiceImpl`）**：`updateQuestion` / `deleteQuestionById` 开头补「只能修改/删除自己出的题」。

**教师端**：`api/teacher/exams.js` 的 `listExams` / `countExams` 带上 `mine: 1`（列表与 chips 计数同口径）。

## 2 · 实测（全部通过）

### 网关（核心：伪造身份失效）

| 请求 | 结果 |
|---|---|
| :10010 `/es/exams/{id}/records` + **伪造** `user-info: 2`，无 token | ✅ `{"code":400,"msg":"请先登录"}` |
| :10010 同一接口 **不带任何头** | ✅ 同上（**两者一致 → 伪造头已被剥掉**） |
| :10010 `/es/exam-records/pending-review` + 伪造 `user-info: 2` | ✅ `{examCount:0, pendingCount:0, items:[]}`（拿不到讲师数据） |
| :10010 + **真 token**（学生）`/es/exams/published?courseId=1002` | ✅ 200 + 真实数据（**网关仍能正确注入身份**，这是最大的回归风险） |
| :10010 + 真 token `/es/exam-records/page`、`/es/exams/{id}/result` | ✅ 200 + 自己的记录/成绩 |
| :10010 + 真 token 打讲师接口 `/es/exams/{id}/records` | ✅ `{"code":400,"msg":"这场考试不是你创建的，不能查看或批改别人的考试"}` |
| :10010 无 token `POST /es/exams/{id}/start` | ✅ `请先登录`；带 token → 200 |
| 免登录路径 `POST /us/code/verifycode` | ✅ 仍可用（登录/验证码不受影响） |

### 服务层（直连 :8089，`user-info` 是内部契约）

| 场景 | 结果 |
|---|---|
| 作者 2 读 `records` | ✅ 200 + 数据 |
| 学生 3 / 讲师 10 / 无身份 读 `records` | ✅ 400「这场考试不是你创建的…」/「请先登录」 |
| 学生 3 读 `statistics` | ✅ 400 |
| 学生 3 读 `exam-records/{id}/result` | ✅ 400 |
| 学生 3 `PUT exam-records/{id}/review`（写） | ✅ 400 |
| **防泄题**：`GET /es/exams/{id}` 作者 | ✅ `answer=['A']`、`analysis=非空` |
| **防泄题**：同一接口非作者 | ✅ `answer=None`、`analysis=None`（`stem` 保留，卷面仍可看） |
| 草稿卷：作者 2 读 | ✅ 200 |
| 草稿卷：学生 3 / 讲师 10 / 无身份 读 | ✅ 400「这场考试还是草稿，只有创建者能查看」 |
| `exams/page?mine=1` 讲师 2 / 讲师 10 | ✅ total `2`（含临时草稿）/ `0` |
| `exams/count?mine=1` 讲师 2 / 讲师 10 | ✅ `{all:2,…}` / `{all:0,…}` |
| 不带 `mine` 的老口径 | ✅ total 仍为 2（老接口行为未变） |
| 学生 3 / 讲师 2 删管理员的题 9001 | ✅ 400「只能删除自己出的题」 |

> 测试用的一张临时草稿卷已删（库里只剩演示那张「第一章检测」）。

## 3 · 刻意没做的事 / 需要你拍板

1. **网关仍未"强制登录"**：`checkAuth` 只在路径命中 Redis 里配置的权限路径时才拦。本次没有打开全站强制登录
   —— 因为分不清哪些页面该允许游客（课程中心/首页大概率要允许）。**现在的实际屏障是服务层的归属校验**：
   要身份才能拿到的数据，拿不到就是拿不到。要真正"必须登录"，得配一遍权限路径表（另开一批做更稳）。
2. **未登录仍可读某课程的已发布试卷列表**（`GET /es/exams/published?courseId=`）。它只给卷名/题量/满分数，
   不含答案也不含成绩，**与课程详情页的公开程度一致**，所以保留。若你希望"选了课才看得到"，说一句我加。
3. **协作讲师暂时批改不了**：归属只认 `exam.creater`。要支持"课程协作者也能批改"得查课程讲师名单
   （需要 exam-service 调 course-service），`requireExamOwner` 里已留好替换点。
4. **错误码语义**：一律用项目的既有约定「HTTP 400 + 中文原话」（前端 `call()` 直接 toast 原话）。
   严格说应该是 401/403，但这套链路目前只认 400，改状态码要连着改前端拦截器，单独一批再说。
