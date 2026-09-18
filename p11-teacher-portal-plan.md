# P11 · 教师端开发方案（计划稿）

> 对应画布：39 个画板 = 学生端 12（已完成）+ 教师工作台 T1–T8 + 建课 A0–A6 + 出卷 E0–E4 + 题库 Q0–Q4/Q6 + 图例 F0
> 本文只覆盖**教师端**。学生端（P8）已上线，只做回归保护。

---

## 0. 结论先说

**要计划。** 理由不是流程要求，是这 39 个画板里有 **5 个不是真实页面**（A0/A4/E0/F0 是流程图与图例、Q0/Q1 是规范说明），直接按画板数量开工必然做无用功。必须先区分「要开发的页面」和「用来讲清楚设计的图」。

---

## 1. 现状盘点（已核对代码）

| 项 | 现状 | 对教师端的影响 |
|---|---|---|
| 外壳 | `pages/student/ShellLayout.vue`（67 行）= AppSidebar + AppTopBar + GlobalAssistant | 教师端只需换 nav 数据与作用域，结构可完全照搬 |
| 样式 | `style/shell.scss` 作用域 `.studentShell`，token `--sa / --sa-hover / --sa-soft` | 新增 `.teacherShell` 作用域即可，**不改学生端 token** |
| 导航 | `components/shell/AppSidebar.vue` 用 `v-for` 渲染，**已是数据驱动** | 抽成 prop 成本极低 |
| 路由 | `router/modules/student.js`（20 条）+ `modules/legacy.js` | 新增 `modules/teacher.js` |
| ⚠️ 兜底 | `router/index.js` 把**任何非 `/student` 地址重定向到 `/student/dashboard`** | **加 `/teacher/*` 必须改这条兜底**，否则教师端全部 404 跳回学生首页。这是第一个要动的文件 |
| 可复用组件 | `SPagination.vue` / `CourseCard.vue` / `GlobalAssistant.vue` / `KnowledgePanel.vue` | 直接复用 |

---

## 2. 外壳接入方案（需你拍板）

| 方案 | 做法 | 优点 | 代价 |
|---|---|---|---|
| **A. 参数化复用**（推荐） | `AppSidebar` 增加 `items` / `ariaLabel` prop，默认值 = 现有学员端导航；`AppTopBar` 同理 | 无重复代码，一处改两处受益 | 动了**已验收**的学生端组件，需回归 |
| B. 复制一套 | 新建 `TeacherSidebar.vue` / `TeacherTopBar.vue` | 零回归风险 | 重复约 200 行，两边会漂 |
| C. 抽公共壳 | 抽 `ShellBase` + 两份 nav 配置 | 最干净 | 改动最大，收益与 A 接近 |

**我的建议：A，但用「加法」的方式做**——只加 prop 并保留原默认值，学生端一行不改也能跑；改完立刻跑一遍学生端 12 页的无头量测做回归。

---

## 3. 目录与路由约定

```
src/pages/teacher/            # 与 student 一致的扁平结构
  ShellLayout.vue             # class=teacherShell
  dashboard.vue               # T1 工作概览
  courses.vue / courseEdit.vue        # T2 / A1-A6 建课
  questions.vue / questionEdit.vue    # Q2 / Q3
  questionImport.vue          # Q4
  bank.vue                    # 题库来源图例（可选）
  exams.vue / examEdit.vue    # T3 / E1-E4
  examStats.vue               # T8
  grading.vue                 # T4
  students.vue / chat.vue / profile.vue   # T5 / T6 / T7

src/router/modules/teacher.js # path: '/teacher'
src/style/teacher-shell.scss  # 或并入 shell.scss 的 .teacherShell 段
src/api/teacher/*.js          # mock-first
```

- `meta.title` 驱动顶栏标题（沿用学生端机制）
- 入口：`/teacher/dashboard`；**兜底规则改为「`/teacher` 前缀不参与兜底」**
- 老站 `/main/*` 保持现状不动

---

## 4. 页面清单与优先级

**先分清 39 个画板里哪些是「真页面」：**

| 类型 | 画板 | 处置 |
|---|---|---|
| 真页面（要做） | T1 T2 T3 T4 T5 T6 T7 T8 / A1 A2 A3 A5 A6 / E1 E2 E3 E4 / Q2 Q3 Q4 | **21 个** |
| 流程图 / 图例（不做页面） | A0 A4 E0 F0 | 只作设计说明 |
| 规范说明（不做页面） | Q0 Q1 Q6 | Q6 里的两个交互要落到 Q2/Q3 里实现 |

**四个阶段（每阶段结束都能演示）：**

- **阶段 1 · 题库闭环**（优先，它是所有考试的上游）
  Q2 列表（含「全部/我的」分段、来源列、批量操作）→ Q3 新建题目（含正确答案标记、保存并继续新建）→ Q4 Excel 导入（含逐行校验/跳过/只重传失败行）
- **阶段 2 · 组卷与发布**
  E2 题库选题（三栏：筛选/列表/已选清单）→ E3 新建题目 → E4 发布设置（含发布前校验）
- **阶段 3 · 考务**
  T3 考试管理（含「统计」入口）→ T8 考试统计（含快照口径条）→ T4 试卷批改（含快照提示）
- **阶段 4 · 建课与周边**
  A1→A2→A3→A5→A6 五步向导 → T1 工作概览 → T2 我的课程 → T5/T6/T7

> 建议从阶段 1 开始，因为它能独立演示，且不依赖任何未完成的后端。

---

## 5. 数据契约（mock-first，先定形状再写页面）

**题目（9 字段，8 个老师填 + 1 个系统记）**
```js
{
  id, type: 'single'|'multiple',        // 只做单选/多选
  stem,                                  // 题干（纯文本，不支持图片）
  options: [{ key:'A', text }],          // 2–8 个
  answer: ['B'],                         // 单选 1 个，多选多个
  analysis,                              // 解析（选填）
  difficulty: 'easy'|'medium'|'hard',
  knowledgePoints: ['自动配置'],          // 只能从课程知识点树选
  courseId,                              // 单选，一道题只归一门课
  createdBy, createdAt,                  // 系统字段：出题人
  status: 'active'|'archived',           // 停用而非删除
  refCount, usedByPapers: [],            // 被引用情况（停用提示要用）
  stats: { usedTimes, correctRate }      // 「使用情况」列
  // 注意：**不含 score** —— 分值归试卷
}
```

**试卷快照（贯穿 T8/T4/学生端答题详解）**
```js
{
  examId, paperVersion: 'v1', publishedAt,
  snapshotItems: [{ questionId, /* 题目当时的完整副本 */ }]
}
```
> 规则：**新建试卷引用题库 → 发布时生成快照 → 学生查看答卷 / 教师批改 / 成绩统计三个入口都读快照**。
> 前端只需认 `paperVersion`，题目内容一律从 `snapshotItems` 取，**禁止在回看类页面直接查题库**。

**mock 策略**：`src/api/teacher/*.js` 返回本地 JSON，用 `import.meta.env.VITE_USE_MOCK` 开关；字段按上面的契约写死，将来接真接口只换实现。

---

## 6. 需要新抽的公共组件

`STable`（教师端表格形态统一：表头 12px 灰、行高 48、列尾对齐卡片内框）· `SModal` · `SSegment`（全部/我的 分段控件）· `SField`（label + 输入行）· `SChip`（状态/来源/难度标签）· `SSteps`（建课左侧五步 / 出卷顶部三步）。

> 画板里的列宽是量过的（如 Q2：题干 320 / 题型 60 / 难度 44 / 知识点 104 / 所属课程 148 / 出题人 72 / 状态 56 / 操作 144，合计 1068 = 卡片 1096 − 左右内边距 28）。**实现时按画板数值，不要凭感觉调。**

---

## 7. 已知阻塞与风险

| 风险 | 说明 | 处置 |
|---|---|---|
| **视频上传（A3/A4）** | media-service 未配置腾讯云 VOD 凭证，真实上传不通 | 阶段 4 时做「UI + 本地模拟进度」，并标注为后端待补 |
| **考试实体缺失** | 后端无「考试实体 / 交卷记录 / 主观题批改」接口 | T8、T4 全量 mock；**不要**在这轮啃后端 |
| **兜底路由** | 不改 `router/index.js` 则教师端全跳学生首页 | 开工第一件事 |
| **学生端回归** | 方案 A 会动 AppSidebar | 改完跑学生端 12 页无头量测 |
| **构建事故** | 沙箱内 `npm install` 会被拒；容器 dist 会清空 | **不跑 `vite build`**，用 dev server 验收 |

---

## 8. 验收标准（沿用学生端 SOP）

- dev server 显式 `--port 18090`（18082 被占且 Vite 不报错）
- 1440×900 无头量测：**零横向溢出 / 零越界元素 / 零塌陷盒子 / 零文字溢出**
- 未登录态（sessionStorage 无 token）下登录相关区域须由你本人实测
- 每页对齐画板规格；改样式前先读画板数值，不猜

---

## 9. 待你确认（3 条）

1. **外壳方案**：A 参数化复用（推荐）/ B 复制一套 / C 抽公共壳
2. **阶段 1 范围**：就定「题库 Q2+Q3+Q4」这一个闭环？
3. **mock 形式**：独立 `src/api/teacher/*.js` + JSON 开关（推荐）/ 先直接写死在各页面里

确认后我从阶段 1 开工，先动兜底路由 + 外壳，再逐页落地。
