# 知序学堂 · 学员端改版 P8 — 前端实施记录

> 设计稿：Ardot《知序学堂 · 学员端改版》（12 页）；本阶段把设计稿落成 Vue 代码。
> 技术栈：沿用现有 Vue 3 + Element Plus + SCSS，未引入新依赖。
> 验收地址：`http://127.0.0.1:18090/#/student/dashboard`（`npx vite --port 18090 --mode development`）

## 已完成

### 1. 学员端外壳（设计稿 §三）
- `src/style/shell.scss` — Apple 式 token（单强调色 `#0066CC`、发丝线、平底色 `#F5F5F7`；作用域 `.studentShell`，不影响官网营销页）
- `src/components/shell/AppSidebar.vue` — 248px 左栏：Logo + 9 项导航（路由驱动选中态：浅蓝药丸 + 主色文字图标）+ 底部用户块（读 userInfo，未登录降级"点击登录"）
- `src/components/shell/AppTopBar.vue` — 64px 顶栏：路由 meta.title 标题 + 全局搜索（跳官网 /search/index）+ 通知（个人中心消息页）+ 返回官网
- `src/pages/student/ShellLayout.vue` — flex 壳：sticky 侧栏 + 顶栏 + 内容区（padding 40/48）；隐藏页脚沿用 AI 页方案（运行时类名 `.layoutsWrapper.isStudentShell .siteFooter`）；`meta.fullHeight` 支持类 App 页壳内自滚动
- `src/config/studentNav.js` — 9 项导航：dashboard/courses/records/exams/notes/notices/messages 指向 `/student/*`；**个人中心→现有 `/personal/main/mySet`、AI 助手→现有 `/main/ai`（复用，不重复建设）**

### 2. 路由（`src/router/modules/student.js`）
`/student` 挂 ShellLayout，redirect `/student/dashboard`；已实装 dashboard/courses/records，其余（exams/notes/notices/messages）挂 ComingSoon 占位（统一空状态 + 引导入口，保证导航全可达不 404）。已在 `router/index.js` 注册。

### 3. 复用组件（设计稿 §四）
- `SPagination.vue` — 分页器（页码窗口 ≤7，当前页主色）
- `CourseCard.vue` — 课程卡：深色渐变封面占位 + 课程代号水印（`coverUrl` 存在时显示真实图）；字段对齐现有 ClassCards（id/name/teacher/sections/sold/price/coverUrl），点击→`/details?id=`
- `ComingSoon.vue` — 建设中占位

### 4. 学习首页 `/student/dashboard`（设计稿 01）
- 左栏：欢迎横幅（真实 banner1.jpg + 渐变压深保白字可读）→ 推荐课程 3 列（`getRecommendClassList('new'/'best')` 去重取 6）→ 学习足迹热力图（近 20 周×7 天，签到数据着色；**无时长字段，页脚明示口径**）→ 近期学习（`getMylessons` 分页 + 进度条）
- 右栏：学习状态卡（头像/连续打卡天数/最近课程 conic 进度环/打卡按钮——`pointsSign` 真实打卡）→ 今日学习计划（`getMyPlan` 真实字段）→ 最近学习 4 门
- 未登录：各模块降级为引导文案，不编造数据

### 5. 课程中心 `/student/courses`（设计稿 02）
- 推荐 Banner（`getRecommendClassList('best')` 首门）→ 左「我的学习」300px + 右「课程中心」
- 搜索/筛选/分页统一走官网 `classSeach`（`/ss/courses/portal`，keyword/categoryIdLv1/categoryIdLv2/free/pageNo/pageSize）
- **筛选 = 透明底浮层**（设计稿 v3 核心要求）：`rgba(255,255,255,.7)+blur(24)`，悬浮于课程栅格之上；一级分类来自 `/cs/categorys/all?admin=true` 真实数据（非示例）、二级联动、收付费 chips；chip 实心白+发丝线（浮层上立边界）；Esc 可关
- 分页 6/页（3 列 × 2 行）

### 6. 学习记录 `/student/records`（设计稿 03）
- 左 400px：概览 4 项（在学课程/累计已学节数/连续打卡/本周计划）+ 同款热力图
- 右：学习明细时间线（按日期分组；接口时间字段名未知→逐字段探测，均无归入"全部记录"组）+ 分页

## 校验方式（重要）
- 本次破例跑了一次 `vite build`，**输出到 `.workbuddy/verify-build/`（工作区临时目录），未触碰项目 `dist/`**——构建通过，dashboard/courses/records 均产出产物，证明全部 import/依赖解析正确。
- 全部新文件经 dev server transform 校验（SFC 模板 + scoped/global 样式块 200 OK）。

## 修复记录

### 学员端登录后被甩回旧官网首页（2026-09-12）

**现象**：在 `/student/dashboard` 点登录，登录成功后落到 `/main/index`（旧官网页）。

**根因**：登录组件的成功回调**写死 `router.push('/main/index')`**，与来源无关。学员端是新增路由，官网原页面按约定全部保留，所以旧页还在。

**修复**：新增 `src/config/loginRedirect.js`
- `rememberStudentOrigin(path)` — 写入 sessionStorage，仅记录 `/student/*` 来源
- `loginRedirect()` — 读出即清除，有学员端来源返回该路径，否则回落 `/main/index`（官网行为完全不变）

改动文件：`config/loginRedirect.js`(新)、`pages/login/components/LoginPass.vue`、`LoginPhone.vue`、`components/shell/AppSidebar.vue`、`pages/student/dashboard.vue`。

**验证**：无头实测点击「立即登录」→ 进入登录页且来源标记为 `/student/dashboard` → `loginRedirect()` 返回该路径并清除标记 → 无来源时回落 `/main/index`，符合预期。

## 第二批：考试 / 笔记 / 公告 / 对话 四模块实装（2026-09-13）

四个 ComingSoon 占位已全部替换为真实实现，严格只用接口已有字段。

### 在线考试（设计稿 04）
- 左侧「考试概览」卡：总场次 / 考试 / 练习 / 平均分 由记录聚合；下方快速筛选 chips。
- 右侧列表卡：分段页签（全部/考试/练习）+ 搜索 + 两列考试卡 + 分页。
- 数据 `getExamList({pageNo,pageSize})` → `{list:[{id,courseName,sectionName,finishTime,duration,score,type}],total}`，`type==1` 为考试、否则练习；卡片显示类型 chip、时长（`timeFormat`）、「查看批阅」。
- **诚实降级**：真实作答流程在课程章节内（`Practise.vue` 的 `getSubject`/`postSubject`），考试列表接口**只返回已提交记录**。因此 `examAnswer.vue` 不编造题目，改为引导页（去我的课程 / 查看考试记录）。

### 答题详解（设计稿 12）
- 得分概览头（得分 / 总分、答对题数、正确率、所用时长、提交时间）+ 结果筛选（全部/答对/答错·未答）+ 逐题卡。
- 逐题展示：题号徽标 + 类型标签 + 状态 chip、题干（`v-html`）、选项列表（✓/✕ 标记，答对绿 / 答错红）、你的答案 / 正确答案 / 难易程度 / 得分、解析框 + 教师评语。
- 数据 `getExamDetails(id)` → `[{correct,answer,score,comment,question:{name,type,options,answer,difficulty,score,analysis}}]`。
- **无「及格线」**：后端未给及格线，改用**正确率**表述，不臆造及格判定。

### 学习笔记（设计稿 06）
- 左「笔记分类」卡（全部 / 我创建的 / 我采集的 + 计数）、右「我的笔记」卡（搜索 + 新建 + 三列笔记卡 + 分页）。
- 笔记卡：标签（我的笔记 / authorName）、私密 chip、视频时间点（`noteMoment`）、内容三行截断、字数·日期，自己的笔记才显示编辑/删除。
- 数据 `getAllNotes({pageNo,pageSize})` → `{list:[{id,content,createTime,authorId,authorName,authorIcon,noteMoment,isPrivate,isGathered}],total}`；增删改 `addNotes`/`updateNotes`/`delNote`。
- 接口无「课程文件夹」维度 → 改用「我创建的 / 我采集的」作为分类，不编造目录结构。

### 公告中心（设计稿 07 / 11）
- 列表页：首条未读（否则最新）做成渐变头条卡 + 筛选 chips（全部/系统消息/笔记通知/未读含计数）+ 行式列表 + 全部标为已读；点击行标记已读并进详情。
- 详情页：返回链接、文章卡（圆角 18 / 内边距 40）、meta 标签 + 时间、标题 24px、正文 `white-space:pre-wrap`。
- 数据 `queryUserInbox({pageNo,pageSize})` → `{list:[{id,title,content,pushTime,isRead,publisher,type}],total}`，`type` 0 系统 / 1 笔记；`publisher==0` 视为系统。

### 师生对话（设计稿 08）
- 采用**壳内自滚动**模式（`meta.fullHeight:true`，`ShellLayout` 加 `.isStudentApp` → `height:100vh; overflow:hidden`，`.body` 内部 `overflow-y:auto`），复用 `ai.vue` 已踩通的规避方案。
- 左 264px 会话栏（头像 / 未读红角标 / 最后消息）、右聊天面板（头 + 可滚动消息区 + 输入框，Enter 发送）；未登录显示登录引导（`rememberStudentOrigin`）。
- 数据 `queryUserConversation` → `{list:[{id,otherUserId,otherUsername,otherAvatar,unReadCount,lastMessage,lastMessageTime}]}`；`getMessageRecords({otherUserId,pageNo,pageSize})`；发送 `sendMessageToUser({userId,content})`；`MSG_PAGE_SIZE=20`。

### 修复

1. **Vite 500 误判（排查结论推翻）**：前一阶段判定 6 个新页面因 `optimizeDeps` 崩溃返回 500 —— **结论错误**。真实原因是排查时新建的临时配置 `vite.config.no-hmr.js` 用**对象展开**复制主配置，而 `vite.config.js` 导出的是**函数**（`defineConfig((mode)=>...)`），展开得到空对象 → 丢失 `plugins`（`@vitejs/plugin-vue`）与 `resolve.alias` → `.vue` 被当纯 JS 解析。**源文件从未损坏**（`@vue/compiler-sfc` 静态校验 8 个 SFC 全 OK）。删除临时配置、用真实配置重启后，全部 200。
2. **顶栏宽按钮破版（真实缺陷）**：`AppTopBar.vue` 的「官网首页」用 `tb__btn tb__btn--wide`，但 `shell.scss` 只定义了 36×36 圆形的 `.tb__btn`，**`--wide` 与 `.tb__btnText` 从未定义** → 图标 + 5 个汉字在 36px 圆内换行、竖向溢出 20px（**每个学员端页面都存在**）。已在 `shell.scss` 补胶囊态 `&--wide`（`width:auto`/`gap:6px`/`padding:0 14px 0 12px`/`border-radius:18px`）与 `.tb__btnText`（13px/500/nowrap）。

### 验收（无头实测，1440×900，未登录态）

9 条路由（dashboard / courses / records / exams / exams/answer / exams/review / notes / notices / messages）：

| 指标 | 结果 |
|---|---|
| 横向溢出 `overflowX` | 全部 **0** |
| 越界元素 | 全部 **0** |
| 塌陷盒子（`.s-card` 高 < 24） | 全部 **0** |
| 文字溢出 / 截断 | 修复后全部 **0** |
| 类 App 页外壳尺寸 | `1280×800` 恰好铺满视口，`outerOverflow=0`，内部滚动 |
| 侧栏高亮 | 全部正确（含 `/main/ai`→AI 助手、`/personal/main/mySet`→个人中心 别名映射） |
| Dashboard / Courses | 整页自然滚动（1247px / 907px），符合非 fullHeight 设计 |

⚠️ **未登录态**下 Exams / Notes / Notices / Messages 显示空态与「立即登录」；**有数据渲染、真实交互（发消息、新建笔记、标已读、考试批阅内容）须用户在真实账号下验收**。

## 待办

1. 用户账号下验收四模块的真实数据渲染与交互
2. 热力图「时长三档深浅」需等后端签到记录补充 `learningTime` 类字段
3. 设计稿「学习数据 2 大 + 6 小格」简化为 4 大项（无全量聚合接口，不编造）；如需 6 小格须后端加统计接口
4. 出题/判分逻辑目前在前端，建议下沉为后端 `/ct/quiz/generate`

## 文件清单
```
src/style/shell.scss                      (设计 token；含顶栏宽按钮修复)
src/style/theme.scss
src/config/studentNav.js
src/config/loginRedirect.js
src/components/shell/AppSidebar.vue
src/components/shell/AppTopBar.vue
src/components/shell/CourseCard.vue
src/components/shell/SPagination.vue
src/pages/student/ShellLayout.vue
src/pages/student/dashboard.vue
src/pages/student/courses.vue
src/pages/student/records.vue
src/pages/student/exams.vue               (新)
src/pages/student/examAnswer.vue          (新)
src/pages/student/examReview.vue          (新)
src/pages/student/notes.vue               (新)
src/pages/student/notices.vue             (新)
src/pages/student/noticeDetail.vue        (新)
src/pages/student/messages.vue            (新)
src/router/modules/student.js             (12 条路由，router/index.js 已注册)
```
（`src/pages/student/components/ComingSoon.vue` 四个模块已实装，该占位组件暂无调用方，保留备用。）

## 与设计稿逐页比对（2026-09-13，设计稿 fileId 725009394574981）

设计稿共 **12 页**：01 学习首页 / 02 课程中心 / 03 学习记录 / 04 在线考试·列表 / 05 在线考试·答题页 / 06 学习笔记 / 07 公告中心 / 08 师生对话 / 09 个人中心 / 10 AI 助手 / 11 公告详情 / 12 答题详解。

**已一致（结构与视觉体系）**：外壳（侧栏 248 + 顶栏 64）、01、02、03、09、10 六页，以及 07 / 08 / 11 / 12 的骨架。

**尚未对齐（6 处）**：

| # | 页面 | 设计稿 | 当前实现 | 差距性质 |
|---|---|---|---|---|
| 1 | **05 答题页** | 完整作答界面：Exam TopBar（返回 / 标题 / **倒计时** / **交卷**）＋ 题型页签（单选·多选·判断·填空·问答）＋ 题号·分值 ＋ 选项 A–D（含已选态）＋ 上一题/下一题 ＋ 右侧 **312px 答题卡**（Answer Grid + Legend 图例）＋ 分数卡（总分 / 及格分） | `examAnswer.vue` 为**引导页**：一张说明卡 + 「去我的课程 / 查看考试记录」两个按钮 | **结构性缺失**（最大的不一致） |
| 2 | 04 考试列表 | 页签「**可参加考试 / 考试记录**」；卡片含 Chip 可参加/已交卷 + 时长 + 说明 + Meta「总分 100 · 及格 60 · 共 10 题」+ 按钮「**开始 / 预览**」（已交卷为「查看成绩 / 预览」）；概览四项「可参加 / 已交卷 / 通过率 / 平均分」；「快速**排序**」chips 最新/时长/难度 | 只展示已交卷记录；页签「全部 / 考试 / 练习」；卡片仅类型 chip + 时长 + 「查看批阅」；概览「总场次 / 考试 / 练习 / 平均分」；「快速**筛选**」全部/考试/练习 | 功能与文案差异 |
| 3 | 06 学习笔记 | 分类按**课程文件夹**（全部笔记 48 / Java 基础入门 12 / Vue3 前端开发实战 9 / 高等数学 / 数据结构 / 操作系统）；笔记卡含「Folder Tag + 标题 + 摘要 + 1,245 字 · 02-06」 | 分类为「全部笔记 / 我创建的 / 我采集的」；卡片为内容三行截断 | 分类维度不同（`getAllNotes` **不返回所属课程**，做课程文件夹需后端补字段） |
| 4 | 11 公告详情 | 署名「教务处 · 发布于 知序学堂」＋ **Callout 高亮块** ＋ 「相关附件」＋ 文件 Chip ＋ **上一篇 / 下一篇**导航 | 返回链接 + 标签 + 时间 + 标题 + 正文 | 缺 4 块（附件无接口；署名可用 `publisher`；上下篇可基于列表实现） |
| 5 | 08 师生对话 | 会话栏 **320px 含搜索框**；消息区有**日期分隔**；Composer 含 图片/代码/表情 三个工具按钮；Header 含「更多」「视频」按钮 | 会话栏 264px 无搜索；无日期分隔；Composer 仅输入框 + 发送 | 细节缺失 |
| 6 | 12 答题详解 | 成绩概览含「**重练错题 / 返回列表**」按钮，第三项 stat 为「已通过 / 及格线 60 分」；Result Tabs **4 个**（全部/答对/答错/未答） | 无 Actions；筛选 3 个（全部/答对/答错·未答）；用「正确率」替代「已通过」 | 缺交互 + 后端未给及格线 |
| 7 | 07 公告中心 | 头条卡 Chip「置顶公告」+ 标题 + 摘要 + 日期 + 「查看详情」按钮；筛选 chips「全部 / 系统通知 / **课程更新** / **活动公告**」 | 头条为渐变卡（未读/最新）；chips「全部 / 系统消息 / 笔记通知 / 未读」 | 分类体系受接口限制（`type` 仅 0 系统 / 1 笔记） |

**可行性结论**：
- 第 1、2 项**技术可行**：真实作答接口已存在（`api/subject.js` 的 `getSubject` / `postSubject`，`Practise.vue` 在用）；「可参加考试」可由「我的课程 → 课程目录」聚合 `hasTest == true` 的章节得出（`Catalogue.vue` 已用 `it.hasTest` 区分练习/考试）。
- 第 3 项受限于 `getAllNotes` 返回字段（无课程归属），需后端补 `courseId` / `courseName`。
- 第 6 项「及格线」后端未提供，不宜臆造。

### ⚠️ 复核纠正：考试闭环的前后端接口并不存在（2026-09-13 深入核查）

上一条「技术可行」的判断**需要修正**。逐一核查后端源码与数据库后确认：

| 核查项 | 结论 |
|---|---|
| `api/subject.js` 的 `getSubject` / `postSubject` | 指向 `/es/exams`、`/es/exams/details` → **exam-service 无此 mapping，实测 404** |
| `api/class.js` 的 `getExamList` / `getExamDetails` | 指向 `/es/exams/page`、`/es/exams/{id}` → **404** |
| `submitExamRecords` / `addExamRecords` | `/es/exam-records*` → **404** |
| exam-service 实际控制器 | 仅 `QuestionBizController`(`/question-biz`) 与 `QuestionController`(`/questions`)，全库无 `/exams`、`/exam-records` |
| 考试记录表 | **不存在**（全库无 `%exam%` / `%record%` 相关表，仅 `tj_learning.learning_record`） |
| `SectionVO.hasTest` | 由 `subjectNum > 0` 派生；`subjectNum` 来自 `CourseCatalogueServiceImpl` 调 `examClient.queryQuestionScoresByBizIds()` |
| `tj_course.course_cata_subject` / `subject` | **均为 0 行** |
| `tj_exam` 题库 | 共 **1 道题**（id 9001，Nacos 题，单选，5 分），绑定的 `biz_id=1103` **在 `course_catalogue` 中不存在**（孤儿数据） |
| 结论 | **没有任何小节被标记为含题（`subjectNum` 全 0 → `hasTest` 全 false）**；`/es/exams` 也不可用 → 课程学习页的「练习」入口当前同样是坏的 |

**但题库接口是真实可用的**（网关前缀 `/es`，已实测）：

| 接口 | 返回 |
|---|---|
| `GET /es/questions/listOfBiz?bizId={sectionId}` | `[{id,name,type,difficulty,score,options[],answer,analysis}]` —— **含答案与解析** |
| `GET /es/question-biz/scores?ids={sectionIds}` | `{ "1103": 5 }` 业务 id → 题目总分 |
| `GET /es/question-biz/biz/{sectionId}` | `[{bizId,questionId}]` |

因此「答题页」可以基于题库接口做成**真实作答 + 前端判分**（`answer` 字段可见），但没有提交/判分/记录接口，「考试记录」只能本地保存。⚠️ 另外 `question_detail.analysis` 字段在库中存在**编码错乱**（UTF-8 被按 latin1 读入），如要展示解析需先修数据。

**因此需要用户决策**：
- **A. 补后端**（正统）：在 exam-service 实现 `/exams`、`/exams/details`、`/exams/page`、`/exam-records` 并建记录表 —— 需改 Java + 重建容器（须用户确认）；顺带修好课程学习页的「练习」。
- **B. 前端闭环**（不动 Java）：用 `/es/questions/listOfBiz` + `/es/question-biz/scores` 做 04/05/12 三页真实作答与判分，记录存本地 —— 需向 `tj_exam` 插入演示题目并绑定到**真实小节 id**（数据变更，须用户确认）。
- **C. 先跳过考试**，补其余 5 处不依赖后端的差异。

### 本轮已补齐（2026-09-13 第二轮，用户选择「先跳过考试」）

| 页面 | 改动 | 文件 |
|---|---|---|
| 11 公告详情 | 新增**署名行**（`知序学堂 · 官方发布` / `· 学习通知`，按系统/笔记通知区分；接口无作者字段故不编造人名）；Byline 与正文间补**分隔线**；正文上方新增**上一篇 / 下一篇**导航 —— 列表页把当前页快照写入 `sessionStorage['studentNoticeList']`，详情页据此定位相邻条目，点击用 `router.replace` 切换并顺带标已读。无快照时按钮正确置灰并提示「已是第一篇 / 已是最后一篇」 | `noticeDetail.vue`、`notices.vue` |
| 07 公告中心 | 筛选 chip 文案对齐设计稿：`系统消息` → **`系统通知`** | `notices.vue` |
| 08 师生对话 | 会话栏 **264px → 320px**（对齐设计稿 Session List）；新增**联系人搜索框**（接口无搜索参数 → 在 `otherUsername` / `lastMessage` 上本地过滤）；消息区新增**日期分隔线**（按 `pushTime` 前 10 位分组）；输入区新增**工具条**：表情（弹出 12 个表情，点击插入光标处）、代码（插入 ```` ``` ````代码块并把光标放到中间）为**真实功能**；图片（无上传接口）与头部的「更多 / 视频」按钮置灰 + `title` 提示，**不做假交互** | `messages.vue` |
| 12 答题详解 | 结果筛选由 3 个拆为设计稿的 **4 个**（全部 / 答对 / 答错 / 未答），「答错」与「未答」按 `!correct && answer` 区分 | `examReview.vue` |

**仍未补齐（受数据/接口限制，按「不编造」约定留空）**：11 的 Callout 高亮块与「相关附件」（后端无对应字段）；06 笔记按课程文件夹分类（`getAllNotes` 无课程字段）；12 的「重练错题」按钮与「及格线 / 已通过」（依赖考试闭环，本轮整体跳过）。

**验收（无头实测 1440×900）**：公告列表 4 chip 正确；公告详情署名渲染为「知序学堂 · 官方发布」、上下篇按钮 `540×71` 各一、无快照时置灰；对话会话栏实测 `320×756`、搜索框 `287×36`；四页 `overflowX=0`、越界元素 0、塌陷盒子 0、文字溢出 0。
⚠️ 登录态下的日期分隔线、输入区工具条、表情/代码插入、上下篇真实跳转**须在真实账号下验收**（无头环境未登录，聊天面板未渲染）。

## 01 学习首页按设计稿严格重建（2026-09-13 第三轮）

用户反馈实现稿「很胖、很大、很丑」，要求与设计稿一模一样。**从 Ardot 设计文件提取精确规格后逐项比对，确认问题不在单一字号，而是多处尺寸叠加偏离**：

| 项 | 原实现 | 设计稿 |
|---|---|---|
| 主栏宽度 / 列间距 | 752（`flex:1`，gap 24） | **744**（gap **32**） |
| 主栏区块间距 | 40 | **28** |
| Hero | 188 高，banner 背景图 + 渐变压深，内容是问候语 | **280** 高，纯 45° 渐变，内容是**课程推广卡**（chip 28 + 标题 34/44 + 描述 15/24 + 按钮 40） |
| 课程卡 | 白底描边卡，封面 16:9，讲师/节数/人数/价格/查看课程 4 行 | **无底色无描边**，封面 237×134（圆角 12）+ 标题 15/21 + 单行 meta 12/17，总高 **192** |
| 学习足迹/记录 | 标题写在卡内 | 标题在卡外（Section Head + Card） |
| 右栏 | 学习状态 / 今日学习计划 / 最近学习 | **基本信息 / 学习数据 / 最近学习** |
| 右栏间距 | 16 | **20** |

**设计稿 01 关键规格（1440 视口，Content 1096）**
- Content = 主栏 **744** + 间距 **32** + 右栏 **320**；主栏列间距 **28**、右栏 **20**
- Hero 744×280 圆角 20、内边距 36；渐变 `135deg #0A172B → #12407A 55% → #2485DB`；chip→标题 16、标题→描述 8、描述→按钮 24
- 区块标题 **21/28 SemiBold `#1D1D1F`**；更多链接 **13/18 Medium `#0066CC`** + 14px chevron；标题行与内容间距 **16**
- 课程卡 237×192（封面 237×134 圆角 12、卡内间距 10）
- 足迹卡圆角 18、内边距 24、行间距 24；热力图 **20 周 × 7 天，格 14×14、间距 5、圆角 3.5 = 375×128**；图例 `#EDEEF3` / `#CFE3FA` / `#0066CC`
- 记录卡圆角 18、内边距 20、行间距 14（日期列 88、状态 12/17，完成 `#34C759` / 进行中 `#FF9F0A`）
- 右栏卡圆角 18、内边距 20；大卡 `#F5F5F7`、小格 `#FAFAFC`（**134×73**）圆角 12、内边距 14

**数据诚实性**：后端无字段的位置**保留版式并显示 `—`**（累计学习、日均学习、考试次数、通过考试、通过率）；「本周学习」用真实 `weekLearnedSections`（显示「N 节」，接口无时长）；「近期学习记录」的真实时间来自 `learning_lesson.latest_learn_time`，经 `/ls/lessons/plans` 的 `LearningPlanVO.latestLearnTime` 暴露；课程封面用真实 `coverUrl`（`/covers/{id}.svg`，实测 200）。

**验收（无头实测 1440×900，全部命中）**：版心 1096 / 主栏 744 / 右栏 320；Hero 744×280 圆角 20、渐变值与设计一致；课程卡 237×192（封面 237×134 圆角 12）；热力图 375×128（14×14 × 140 格、间距 5、圆角 3.5）；足迹卡内边距 24、记录卡 20、右栏卡 20 圆角 18；大卡 `#F5F5F7`、小格 134×73 `#FAFAFC`；区块标题 21/28 600 `#1D1D1F`。`overflowX=0`、越界元素 0、文字裁切 0。

## 02–12 全量按设计稿对齐（2026-09-13 第四轮）

沿用上一轮的方法（**从 Ardot 文件取精确数值 → 照抄落地 → 无头逐项对表**），把其余页面全部过了一遍。**结论：尺寸类偏差集中在 01/02/03/06 四页，09/10/07/11 原本就是照设计稿写的，仅需微调。**

### 逐页规格与结果（1440 视口，Content 均为 1096）

| 页面 | 设计稿关键规格 | 处理 |
|---|---|---|
| **02 课程中心** | Content 纵向间距 32；推荐课程 = Section Head + **Banner 1096×200 圆角 20**（chip 76×26、标题 28/36、描述 14/22、按钮 104×38、箭头 40×40、圆点 8×8）；Bottom Row 间距 32 = **我的学习 328**（卡圆角18/内边距20/行距16，缩略图 40×28）+ **课程中心 736**（卡圆角18/内边距**24**/间距20，搜索行 44 高圆角22、按钮 78×44 与 92×44、课程卡 **218×177** 封面 218×124）；筛选浮层 **688** 圆角 16 内边距 16，chip 高 32 圆角 16 | **重写** `courses.vue` |
| **03 学习记录** | **学习概览 415**（卡圆角18/内边距20/间距18：5 条统计 标签13/18+值15/20 SemiBold、Divider 1px`#F0F0F0`、小节标题 13/18 SemiBold、热力图 375×128）+ **学习明细 649**（卡圆角18/内边距24/间距20：搜索框 200×34 圆角17、日期分组 12/17、条目 时间列52 + 状态列48） | **重写** `records.vue` |
| **06 学习笔记** | **笔记分类 248**（卡圆角18/内边距16/行距8，分类行高 40 圆角 10，选中 `#E8F1FC`）+ **笔记列表 816**（卡圆角18/内边距24/间距20，搜索框 220×36 圆角18、新建 88×36 圆角18，笔记卡 **245×148** 圆角 14 底色 `#FAFAFC`，标签11/15 / 标题14/20 / 摘要12/18 两行 / 元信息11/15） | **重写** `notes.vue` |
| 07 公告中心 | 头条卡 **176** 高圆角 20（chip 82×26、标题 24/32、描述 14/22）；列表卡内边距 8、行高 **72**、日期列 88；chips 高 32 | 微调（min-height 176、标题 24/32） |
| 08 师生对话 | 会话栏 320（内边距 12、行距 4），会话行高 **68** 圆角 **12**（头像 40、名称 14/18、时间 11/15、消息 12/16、未读 8×8）；Chat Header **64** 高（头像 36）；Composer **120** 高，输入 736×44 圆角 **12**，工具 32×32 | 微调 + **补上会话时间列**（真实 `lastMessageTime`） |
| 09 个人中心 | 设置导航 **200**（卡圆角18/内边距12/行距4，行高 40 圆角 10）+ 表单 **864**（卡圆角18/内边距**28**/间距24） | 原实现已符合，实测通过 |
| 10 AI 助手 | AI Panel **1096 圆角 20**；会话栏 **264**（内边距 12、底色 `#FAFAFC`、行距 4），新建对话 40 高圆角 12，会话行 40 高圆角 10；Header 64 高；消息区 `#F7F8FA` 内边距 24；输入与发送 **56** 高圆角 14 | 原实现已符合，实测通过 |
| 11 公告详情 | Article Card 内边距 **40** 圆角 18、间距 20；标题 **28/38**；Byline 13/18；正文 15/28；Article Nav 列间距 16、条内边距 **20** 圆角 **14** | 微调（标题 28/38、Nav 内边距20/圆角14、Byline 行高18） |
| 04/05/12 考试三页 | — | **跳过**（后端 `/es/exams*` 与考试记录表不存在，见上文核查） |

### 顺带修掉的两个问题

1. **flex 主轴压缩**：Banner / Hero 是纵向 flex 容器且高度固定，子元素默认会被压缩 —— 02 的「立即学习」按钮实测被压成 `29.9px`（应为 38）。已统一加 `> * { flex: 0 0 auto; }` 保护（01、02 均加）。
2. **类名冲突**：`notes.vue` 原本用了 `.main` / `.side`，与外壳布局的非 scoped `.main` 同名 —— 虽然 scoped 属性让样式不串，但语义混淆且会让自动化选择器选错元素。已改为 `.noteList` / `.catCard`。

### 验收（无头实测 1440×900）

- **02**：版心 1096、Banner 1096×200 圆角20（渐变值一致）、行 1096、我的学习 328、课程中心 736（内边距24）、课程卡 219×177（封面 219×124）、搜索/按钮 44 高圆角22、筛选按钮底色 `#E8F1FC`、箭头2圆点3 —— 全部命中
- **03**：1096 = 415 + 649；概览卡内边距20/圆角18/间距18；热力图 375×128（格14×14、间距5、圆角3.5、140 格）；明细卡内边距24/圆角18/间距20；搜索框 200×34 圆角17；5 条统计 —— 全部命中
- **06**：1096 = 248 + 816；分类卡内边距16/圆角18/间距8；分类行高40圆角10；搜索 220×36；新建按钮 88×36；列表卡内边距24/圆角18/间距20 —— 全部命中
- **09**：1096 = 200 + 864；导航行 40 圆角10；表单卡内边距28/圆角18 —— 命中
- **10**：1096 圆角20；会话栏 264（内边距12、`#FAFAFC`、间距4）；Header 64；消息区 `#F7F8FA` 内边距24；会话行 40 圆角10；输入/发送 56 圆角14 —— 命中
- **11**：1096；文章卡内边距40/圆角18；标题 28/38；Byline 13/18；上下篇 2 个、内边距20/圆角14 —— 命中
- **07**：列表卡内边距8/圆角18；chips 32 高圆角16（4 个）—— 命中（头条卡与行内容需登录态）
- **08**：`msg` 1096；会话栏 320；搜索框 36 高 —— 命中（会话/聊天区需登录态）
- **全部页面**：`overflowX=0`、越界元素 0、文字裁切 0

⚠️ **受登录态限制未能实测**（无头环境拿不到 token，相关 DOM 不渲染）：01 右栏数据、02 我的学习行、03 明细条目、06 笔记卡（库中 0 条笔记）、07 头条卡与列表行、08 会话行与聊天区、12 全部。**这些页面的静态尺寸值均已按设计稿写入 CSS，但真实渲染请在你的账号下确认。**

## 第五轮：功能补齐（2026-09-13，学员端替代老站）

用户明确「新学员端将取代老站 `/main/index`，老功能可直接复用」，据此补齐 5 项：

### 1. 移除右上角「官网首页」按钮
`AppTopBar.vue` 删除该按钮（连同 `shell.scss` 中为它加的 `--wide` 胶囊样式），为新站替代老站做准备。通知按钮保留。

### 2. 公告中心 → 公告与新闻（`/student/notices`）
- 侧栏导航与顶栏标题改名「公告与新闻」
- 页面**左上角新增 公告 / 新闻 切换**（同一套 chip 语言，高 32 圆角 16）
- **新闻沿用老项目的实现方式**：`src/config/news.json`（200 条静态快照：id/title/summary/body/aiSummary/source/category/lang/date/url）+ `api/news.js`。老项目 `pages/main/news.vue` 用的正是这份数据。
- 版式按新风格重排：分类 chips（6 个，来自 `news.json.categories`）+ 左列表 **415** + 间距 32 + 右详情 **649**
- 配套：`api/news.js` 的 `getNewsList` 增补返回 `categories`（附加字段，不影响老页面）

### 3. AI 三助手补齐
| 助手 | 落地 | 说明 |
|---|---|---|
| **全局助手** | **新增** `student/components/GlobalAssistant.vue`，挂在 `ShellLayout` → **学员端所有页面**右下角悬浮球（56×56，`right/bottom: 32`） | 由老项目 `pages/main/components/GlobalAssistant.vue` 移植：实时课程目录、快捷按钮、流式问答（`assistantType=GLOBAL`）全部保留；样式换新；悬浮球图标由原 1391×1024 复杂字形换成线性图标；「去私人助手」跳转改为 `/student/ai` |
| **课程助手** | 复用老项目 `pages/learning/components/AiTutor.vue`，挂在新学习页第 4 个页签「AI助教」 | 逻辑与组件零改动 |
| **私人助手** | `/student/ai` | 已有 |

### 4. AI 助手页补知识库
- 新增 `student/components/KnowledgePanel.vue`（由老项目 `pages/main/components/KnowledgePanel.vue` 移植，**逻辑零改动**：上传 / 查看原文 / 编辑 / 删除，`uploadMarkdown`/`updateMarkdown`/`deleteMarkdown`/`queryMarkdownPage`/`getMarkdown`）
- 样式整体重写为学员端语言（白卡 + 发丝线 + `#0066CC`，抽屉 360px，列表项悬停显操作）
- `ai.vue` 头部在「知识库已连接」chip 旁新增**「知识库」按钮**（显示真实资料数），点击开关抽屉；`files-changed` 后刷新计数

### 5. 课程详情页（新风格）`/student/courses/detail?id=`
- 新建 `courseDetail.vue`。版式：版心 1096 = 主栏 **744** + 间距 32 + 右栏 **320**
- 面包屑（`cateNames`）→ 课程信息卡（封面 260×164 + 名称 24/32 + 课程节数/有效期/评分/在学人数 + 收藏/分享）→ 价格卡（价格 + 马上学习）→ 页签卡（**课程简介 / 课程目录**）+ 右栏（常见问题 / 猜你喜欢）
- 接口：`getClassDetails`（`/cs/courses/baseInfo/{id}`）、`getClassTeachers`、`getClassList`（目录）、`enrolledFreeCourse`、`putCarts`、`addMyCollect`/`isCollect`、`getRecommendClassList`
- 「马上学习」：免费/已购 → 进学习页；未购 → 加购并跳购物车。章节点击 → 学习页对应小节
- 「常见问题」沿用老项目 `classDetails/index.vue` 的静态 `askData` 原文案（4 条）；「猜你喜欢」取自真实推荐接口
- `courses.vue`（课程卡）、`dashboard.vue`（推荐卡）、`records.vue`（学习明细）的跳转统一改为本详情页

### 6. 学习 / 视频页（新风格）`/student/learn?id=&sectionId=`
- **script 逻辑与老项目 `pages/learning/index.vue` 完全一致**（TCPlayer 签名、播放日志、目录/练习题/问答/笔记/AI助教 原样保留），只重写模板与样式，路径改写 6 个子组件为 `@/pages/learning/components/*`
- 版式：左视频区（16:9 自适应）+ 右信息与页签栏 **380px**（课程卡 + 目录/问答/笔记/AI助教 四页签）
- 路由 `meta.fullHeight: true`（壳内自滚动）
- 子组件 `Catalogue / Question / Note / AiTutor / TableSwitchBar / Practise` 均为 **scoped 样式**，可直接复用，不依赖老页面的 `index.scss`

### 验收（无头实测 1440×900）
| 项 | 结果 |
|---|---|
| 悬浮球 | 6 个页面全部存在，实测 **56×56**，右下角 `right/bottom ≈ 32` |
| 官网首页按钮 | 全部页面**已移除** ✓ |
| 公告与新闻 | 切换标签 `公告/新闻` 2 个；新闻列表 **8 条**、分类 chips **6 个**、分页有、详情卡宽 **649**、正文 2000 字 |
| 课程详情 | 面包屑 1096、信息卡 1096×212、价格卡 1096×84、**页签卡 744**、右栏 320、页签 2 个、右栏卡片 2 个、常见问题 4 条 |
| 学习页 | 视频区 696×440、视频框 **696×392（16:9）**、右栏 **380**、页签 4 个 |
| 全站 | `overflowX=0`、越界元素 0、文字裁切 0 |

⚠️ **需登录验收**：课程详情的 `baseInfo` 接口需鉴权（无 token 返回 401）→ 无头环境下课程名/价格为空态；学习页的播放签名同理；悬浮球对话、知识库上传、新闻正文均为本地/静态数据，已实测渲染。
⚠️ 学习页右侧「问答 / 笔记 / AI助教」用的是老项目组件，**内部样式仍是老风格**（仅外框与页签为新风格），如需彻底统一需逐个重排这 3 个组件。

## 复用流程（剩余改动建议照此推进）

1. **取规格**：用 Ardot `batch_read` 读目标页 frame（如 `02 课程中心` = 3:361）的 `Content`，逐层拿 `width / height / padding / gap / layout / fontSize / lineHeight / fontWeight / cornerRadius / fills`。**不要凭感觉调样式**。
2. **对齐版心**：确认 `Content` 宽度（多数页为 1096），主栏/右栏比值与列间距照抄。
3. **落地**：文本类字号行高、圆角、内边距、间距全部照抄数值；卡片统一 圆角 18 / 内边距 20（足迹类 24）。
4. **数据诚实性**：接口无字段的位置保留版式、值显示 `—`，并在文件头注释写清契约与降级。
5. **验证**：无头同源 iframe 探针按选择器量测宽高 + `getComputedStyle` 抽查字号/圆角/底色，与设计稿数值逐项对表；同时检查 `overflowX` / 越界元素 / 文字裁切。

---

## 2026-09-16 · 两处学员反馈修复

1. **课程中心「我的学习」→「我的课程」**：点一行从「直接进视频页」改为「进课程详情页」
   （`goLearn` → `goCourse`，路由 `/student/courses/detail?id=`）；空态文案同步改。
   「最近学习」已在学习首页有展示，这里定位为课程入口。右上角「学习记录」入口保留。
2. **课程学习页（`/student/learn`）目录区"看起来是空的"**：章节标题被全局
   `src/style/element/index.scss` 里那条 `.el-collapse-item__header{color:#FFF}`（为老深色页写的）
   刷成白字，白底上看不见；折叠时看不到小节 → 整块像空的。已在 `Catalogue.vue` 用
   scoped `:deep()` 覆盖为深色（明细见 `p14-answer-grading-pipeline.md` §13）。
