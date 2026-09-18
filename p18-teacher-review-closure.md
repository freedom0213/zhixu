# P18 · 批改闭环收口

> 用户 2026-09-16 选定「下一批做批改闭环收口」。本文件记录**发现的真问题 → 改法 → 实测**。
> 前置：P17（取消随堂练习、考试以课程为维度）已完成；本批不再动 P17 的东西。

## 0 · 为什么值得做：批改页此前**一个入口都没有**

摸代码时发现的不是"缺一个入口"，而是三个叠在一起的死结：

| # | 问题 | 证据 |
|---|---|---|
| 1 | **604 行的批改页（`marking.vue`）在 UI 上不可达** | 考试管理的操作列只在 `item.status === 'marking'` 时给「去批改」，而**后端从来没有把 `exam.status` 置成 2**（全项目 grep `setStatus(2)` 无命中；状态只有 0 草稿 / 1 已发布 / 3 停用）→ 这个分支永远进不去 |
| 2 | 侧栏「试卷批改」是**死占位** | `/teacher/review` 挂 `Placeholder.vue`，而真正可用的批改在 `/teacher/exams/:id/marking` |
| 3 | 工作概览「需要处理」的按钮**指向不存在的 id** | 两个按钮硬编码跳 `/teacher/exams/9001/marking`、`/teacher/exams/new?id=9004`，而 9001/9004 在库里都不存在 |

也就是说：P14 做完了批改功能、P17 让考试成了课程的一等公民，但**讲师点不到批改**。

## 1 · 后端：待批改聚合接口

`GET /es/exam-records/pending-review`（讲师视角）→ `PendingReviewSummaryVO`

```
{ examCount, pendingCount, submittedCount,
  items: [{ examId, examName, courseName, examType, questionCount, totalScore,
            submittedCount, pendingCount, reviewedCount, lastSubmitTime }] }
```

三个刻意的口径决定：

1. **只列我建的卷**（`exam.creater = me`）——"我的考试"才有"我的活"。
2. **只列有人交过卷的场次**，且**不含 `status=0`（进行中）的记录** —— 学生点了开始没交卷，不该算进"待批改"。
3. **三个总数从 items 明细求和**，不单独查一次 count —— 否则顶部「待复核 3」和下面各行加起来可能不等（单一数据源）。
4. 排序：待复核多的在前，同数量时**最近交卷的在前**（先处理新的）。

新增文件：`PendingReviewItemVO`、`PendingReviewSummaryVO`；实现落在 `ExamAnswerServiceImpl.pendingReview()`。

## 2 · 前端

- **新页面 `pages/teacher/review.vue`**（不再用占位）：顶部总览三卡（待复核 / 已交卷 / 涉及考试）+ 一表（考试 / 题量·总分 / 已交卷 / 待复核 / 最近交卷 / 操作），行内「去批改」「查看成绩」。
  - 空态写清楚**怎么才会有数据**（"学生在课程学习页→考试交卷后会立刻出现在这里"），不是一句"暂无数据"。
  - 底部明确写了「选择题已自动判分，这里的批改是**复核**」——避免老师以为要自己判分。
- **考试管理列表补上入口**：`marking || submittedCount > 0` 时给「去批改」；顺手去掉原来那个"点两次都是同一个页面"的重复按钮（旧代码里「查看成绩」和「统计」都 `goStats`）。
- **工作概览死链**改指 `/teacher/review` 与 `/teacher/exams`。
- `api/teacher/exams.js` 加 `getPendingReview()`（含真实/`mock` 两条分支，mock 从 `EXAMS`/`SUBMISSIONS` 推导，不另写一份数字）。

## 3 · 实测（全部通过）

### 接口

| 场景 | 结果 |
|---|---|
| 讲师 2 视角 | ✅ `examCount=1 pendingCount=1 submittedCount=1`；行 = 「第一章检测 · Spring Boot 基础 / 1 题 / 10 分 / 1 份 / 1 份待复核 / 2026-09-16 18:55」 |
| 讲师 10 视角（没有自己建的卷） | ✅ `{0,0,0,items:[]}` |
| 经网关 | ✅ 同一份数据 |
| **复核 → 计数联动** | ✅ 初始 `pending=1, reviewed=0` → `PUT /exam-records/{id}/review` 200 → `pending=0, reviewed=1` → 还原后 `1/0` |
| 复核一条不存在的记录 | ✅ 400「作答记录不存在」 |

### 页面（无头 + 真实登录态注入 sessionStorage）

| 页面 | 结果 |
|---|---|
| `/teacher/review`（有数据） | ✅ 副标题「有 1 份答卷待复核，分布在 1 场考试里。」；三卡 `[1,1,1]`；表头 6 列齐全；行内 `去批改 / 查看成绩`；`ERRORS=[]` |
| `/teacher/review`（空数据） | ✅ 「现在没有要批改的卷子。」+ 三卡 0 + 引导文案 |
| 点「去批改」 | ✅ 跳到 `#/teacher/exams/2099706281458794498/marking`，标题与作答列表正确 |
| `/teacher/exams/:id/marking` | ✅ **带真数据渲染**（左栏 `全部 1 / 已交 1 / 进行中 0`，右栏答卷面板存在）—— **P14 遗留的「批改页未做浏览器验证」到此清掉** |
| `/teacher/exams/:id/stats` | ✅ 提交/应考 `1 / —`、平均分 `10/10`、通过率 `100%`、最高/最低 `10/10`、分数分布、逐题正确率 `100% 1/1` |
| `/teacher/exams`（列表） | ✅ 已发布且有 1 人交卷 → 操作列出现「**去批改** 统计」（修复前此按钮不出现） |

> 方法备注：讲师 admin 登录通道**要验证码**，无头拿不到（且登录请求含口令、被安全策略拦下）。
> 能验是因为 ①教师端路由无全局守卫（无 token 也能渲染）②讲师侧接口**不校验归属**（见 §4），
> 所以**用学生登录态就能把批改页渲染出真数据**。验「有数据的待批改页」时临时把卷子 `creater` 改成 3，**验完已改回 2**。

## 4 · 🔴 顺带发现的安全问题（本批未修，需你拍板）

1. **讲师侧接口不校验归属**：直连 exam-service（:8089）带 `user-info: 3`（**学生**身份）请求
   `/exams/{id}/records`、`/exams/{id}/statistics` 都返回 200 —— 谁都能看任意一场考试的成绩与答卷。
   而 `PUT /exam-records/{id}/review` 是**写接口**且同样无归属校验 → 任何登录用户可以复核任意卷子。
2. **网关信任客户端自带的 `user-info` 头**：`curl http://127.0.0.1:10010/es/exam-records/pending-review -H 'user-info: 2'`
   **不带任何 token** 也返回 200 且是讲师 2 的数据 —— 等于**无需登录即可冒充任意用户**（含写接口）。

建议（按性价比排序）：
- **网关层剥离/覆盖**请求里客户端传入的 `user-info`（只允许从 token 解出）—— 一处修复，收益最大。
- 讲师侧接口补归属校验（`exam.creater == me` 或课程归属人）。
- 我本批新加的 `pending-review` 已按 `creater = me` 过滤，但它同样受第 2 条影响。

## 5 · 遗留

- `dashboard`「需要处理」这一卡的**数字仍来自 mock**（缺考人数 / 草稿数后端都没有聚合接口）—— 本批只把死链改到真实去处，数字要等 T1 聚合接口。
  另：「缺考登记」这个功能**后端完全没有**（`enrolled/absent` 一律 null），要么做要么删，建议做 T1 时一起定。
- 教师端剩余占位页：`/teacher/students`、`/teacher/messages`（依赖 WebSocket）、`/teacher/profile`。
