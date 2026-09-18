# P14 · 作答与判分链路（正确率 / 被考次数的数据源）

> 起因：2026-09-16 你回答「正确率 / 考试次数没有来源就不显示？如果是这样，我希望你能添加这个功能」。
> 你要的是**把数据源补上**。这份文档说明真实成本 —— 它不是"加两个计数器"，而是**这条链路在当前后端根本不存在**。
>
> 配套文档：`p13-platform-change-list.md`（题库可见范围 + 讲师称呼）。两者可独立实施，但**建议同批重建 exam-service**。

---

## 0 · 现状勘误：学生作答这条链路是断的

读了代码，四个实证：

### 0.1 前端在调的三个接口，后端一个都没有

旧组件 `pages/learning/components/Practise.vue`（学生真实作答就在这里）调用：

| 前端调用 | 期望语义 | 后端实际情况 |
|---|---|---|
| `POST /es/exams` | 按 `{sectionId,type,courseId}` **拉题目** | ❌ **同路径被实现成** `saveExamDraft`（老师保存试卷草稿，body 是 `ExamFormDTO`）—— **语义冲突** |
| `POST /es/exams/details` | **交卷**（带每题的答案） | ❌ 不存在 |
| `POST /es/exam-records`、`/es/exam-records/details` | 开始考试 / 保存作答记录 | ❌ 不存在 |

`tj-exam` 只有 4 个 Controller（Exam / Question / QuestionBiz / KnowledgePoint），**没有任何与"作答/记录"相关的类**；`exam_record` 表、PO、Mapper 全项目**搜索不到**。

### 0.2 `exam` 表缺"挂在小节上"的字段

学生随堂练习是按**小节**发起的（`startExaminationHandle({sectionId, ...})`），但 `exam` 表只有 `course_id`，
**没有 `section_id`**（`exam_type=2` 是"随堂练习"，但没地方记它属于哪一节）。

### 0.3 `submitted_count` 是死字段

建表注释自己写着：`已提交人数（批改阶段回填）`，而 `exam` 的 `submitted_count` **恒为 0** —— 因为批改阶段从没做。
（P11 记忆里登记的"未做：批改/统计 4 接口"就是这个坑的另一半。）

### 0.4 所以现状是

| 能力 | 现状 |
|---|---|
| 学生真实考试 / 练习 | ❌ 链路断（前端老组件对着不存在的接口发请求） |
| 老师批改 | ❌ mock（页面在，接口不在） |
| 老师统计 | ❌ mock（同上） |
| 题库的「正确率 / 被考次数」 | ❌ 无数据源（`correct_times`/`answer_times` 从未被写入） |

**好消息**：判分需要的东西**已经齐了**。`ExamSnapshotItem` 里冻结了 `answer`（标准答案）+ `stem/options/analysis`，
发布时生成快照的逻辑也已实现。**判分只是没人去读它。**

---

## 1 · 目标

**主目标**：让「正确率 / 被考次数」有真数据源。

**顺带解开**（同一套记录，不额外花钱）：
- 学生端**真实考试/练习**（今天对着不存在的接口发请求）
- 老师端**批改**（复核自动判分）
- 老师端**统计**（成绩分布、通过率 —— 现在全是 mock）
- `exam.submitted_count` 变成真数字

---

## 2 · 数据模型（迁移 `deploy/mysql/p14-answer-grading.sql`）

### 2.1 补 `exam` 的一个字段

```sql
ALTER TABLE `exam` ADD COLUMN `section_id` BIGINT DEFAULT NULL
  COMMENT '随堂练习挂在哪个小节（exam_type=2 时有值）' AFTER `course_id`;
ALTER TABLE `exam` ADD INDEX `idx_section` (`section_id`);
```
（正式考试 `exam_type=1` 仍按 `course_id` 组织，`section_id` 为 NULL。）

### 2.2 新增作答记录表

```sql
CREATE TABLE IF NOT EXISTS `exam_record` (
    `id`            BIGINT   NOT NULL COMMENT '主键（雪花）',
    `exam_id`       BIGINT   NOT NULL COMMENT '考试/练习id',
    `student_id`    BIGINT   NOT NULL COMMENT '作答学生',
    `score`         INT      NOT NULL DEFAULT 0 COMMENT '得分',
    `correct_count` INT      NOT NULL DEFAULT 0 COMMENT '答对题数',
    `total_count`   INT      NOT NULL DEFAULT 0 COMMENT '总题数',
    `passed`        TINYINT  NOT NULL DEFAULT 0 COMMENT '是否及格（按 exam.pass_score）',
    `start_time`    DATETIME DEFAULT NULL COMMENT '开始作答时间',
    `finish_time`   DATETIME DEFAULT NULL COMMENT '交卷时间',
    `status`        TINYINT  NOT NULL DEFAULT 1 COMMENT '1：已交卷，2：已复核',
    `paper_version` VARCHAR(8) DEFAULT NULL COMMENT '作答时冻结的试卷版本（回看要指回这一版快照）',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_exam_student` (`exam_id`, `student_id`),
    KEY `idx_student` (`student_id`),
    KEY `idx_exam` (`exam_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考试/练习作答记录';
```

⚠️ `uk_exam_student` 是**业务约束的落点**：正式考试"只能考一次"（旧 UI 明说了这句话），
唯一键直接把它变成数据库事实；随堂练习允许重考时改为按 `(exam_id, student_id, attempt)`，本期先不支持重考。

### 2.3 新增作答明细表

```sql
CREATE TABLE IF NOT EXISTS `exam_record_detail` (
    `id`          BIGINT  NOT NULL COMMENT '主键（雪花）',
    `record_id`   BIGINT  NOT NULL COMMENT '所属作答记录',
    `question_id` BIGINT  NOT NULL COMMENT '题目id（题库题id，用于回写计数）',
    `answer`      VARCHAR(64)  DEFAULT NULL COMMENT '学生答案（选项编号串，如 "1,3"）',
    `correct`     TINYINT NOT NULL DEFAULT 0 COMMENT '是否正确',
    `score`       INT     NOT NULL DEFAULT 0 COMMENT '本题得分',
    `marked`      TINYINT NOT NULL DEFAULT 0 COMMENT '学生是否标记待查（答题卡用）',
    PRIMARY KEY (`id`),
    KEY `idx_record` (`record_id`),
    KEY `idx_question` (`question_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作答明细';
```

### 2.4 `question` 的两个计数列**不用改**（已存在）

`correct_times` / `answer_times` 本来就有列，只是从没人写。这次把它们变成真的。

---

## 3 · 接口清单（新增 8 个，都在 `/es`）

| 方法 | 路径 | 用途 | 谁用 |
|---|---|---|---|
| POST | `/es/exams/{id}/start` | 开始作答：校验"还没考过"、建 `exam_record(status=0)`、返回**快照题目**（不含答案！） | 学生 |
| POST | `/es/exams/{id}/submit` | 交卷：判分 → 写明细 → 回写计数 → 返回成绩 | 学生 |
| GET | `/es/exams/{id}/result` | 我的这场成绩（含逐题对错，按快照回看） | 学生 |
| GET | `/es/exam-records/page` | 我的作答记录分页（"在线考试"列表的真实数据） | 学生 |
| GET | `/es/exams/section/{sectionId}` | 按小节取随堂练习（替代冲突的 `POST /es/exams`） | 学生 |
| GET | `/es/exams/{id}/records` | 某场考试的全部作答（批改用） | 老师 |
| GET | `/es/exams/{id}/statistics` | 成绩分布 / 平均分 / 通过率（只计已交卷） | 老师 |
| PUT | `/es/exam-records/{id}/review` | 复核成绩（批改 = 复核自动判分） | 老师 |

⚠️ **不动的**：`POST /es/exams` 保持"老师保存草稿"的现有语义。学生端改用
`POST /es/exams/{id}/start`，把语义冲突就地解决，**不新建同路径的第二套含义**。

---

## 4 · 判分口径（这是最容易做错的地方）

1. **只读快照**：判分取 `exam.snapshot_items[i].answer`，**绝不查题库当前答案**。
   理由：这就是我们早先定的"试卷发布才生成快照"的兑现点 —— 题库改题不能改历史成绩。
2. **比对方式**：学生答案与标准答案都归一成"升序编号串"（如 `"1,3"`）后**字符串相等**即算对。
   多选题**不给部分分**（本期）。单选/多选都走同一条逻辑。
3. **每题得分**：对 → `exam.items[i].score`；错 → 0。总分 = 各题得分之和。
4. **及格**：`score >= exam.pass_score`。
5. **幂等**：`submit` 命中已存在的 `exam_record`（已交卷）→ 直接返回既有成绩，**不重复判分、不重复累加计数**。
6. **只允许已发布的试卷被作答**（`exam.status >= 1` 且 `snapshot_items` 非空）→ 否则报"这份卷子还没发布"。

### 4.1 计数累加规则（正确率的真实性靠这一条）

交卷判分**成功后**，对**试卷里的每道题**执行一次：

```
answer_times  += 1
correct_times += (该题答对 ? 1 : 0)
```

- 用一条 `UPDATE ... CASE WHEN id IN (...) THEN correct_times+1 END` 批量做，避免 N 次单条更新。
- **只在真正交卷成功时加**（幂等返回的场景不加）。
- ⚠️ **口径要写在界面上**：这是"**被作答次数**"，不是"人数"——同一道题被同一个学生重考多次（随堂练习）会算多次。
  讲师在看这个数字时应该知道它衡量的是"这道题被练了多少次、对的比例有多高"。

---

## 5 · 前端改动

### 5.1 学生端（接真接口，删假链路）

- `Practise.vue`：`getSubject → POST /es/exams/{id}/start`；`postSubject → POST /es/exams/{id}/submit`。
- 「在线考试」列表（`pages/student/exams.vue`）：接 `/es/exam-records/page`，不再只显示"已交卷记录"的假数据。
- 答题回看（`examReview.vue`）：接 `/es/exams/{id}/result`，**按作答时那一版快照**展示（含解析）。
- 学习页小节：随堂练习入口改用 `/es/exams/section/{sectionId}`。

### 5.2 讲师端

- **题库列表**：新增两列 **被考次数 / 正确率**
  - 正确率 `answer_times = 0` 时显示 **`—`**，**不显示 0%**（这是本次的诚实性底线）
  - 与 `p13` 的"引用数"一起，形成三列真实信号：被引用 / 被考过 / 正确率
- **批改页**（`marking.vue`）：从 mock 换成 `/es/exams/{id}/records` + `/es/exam-records/{id}/review`
  - 选择题全自动判分 → 讲师的动作是**复核**（这与早先定的"判分只读快照答案"一致）
- **统计页**（`stats.vue`）：从 mock 换成 `/es/exams/{id}/statistics`
  - 只计**已交卷**学生；及格线按 `exam.pass_score`，不写死 60

---

## 6 · 明确不做（本期）

- ❌ **主观题判分 / 部分分**（题库本期只做选择题，见 P10 既定规则）
- ❌ **重考 / 多次作答**（唯一键锁"一人一考"；随堂练习先也按一次算）
- ❌ 不做**防作弊**（切屏检测、IP 限制、试题乱序）—— 不是本期问题
- ❌ 不做**成绩导出 Excel**
- ❌ 不做**实时榜单 / 排名**
- ❌ 不改**试卷快照的生成逻辑**（只在判分处**读**它）
- ❌ 不动 `chapter`/`section` 的既有目录模型（只给 `exam` 补一个 `section_id` 引用）
- ❌ 不动老师"保存草稿"的 `POST /es/exams` 语义（学生端另开路径）
- ❌ 不引入**新的基础设施**

---

## 7 · 预期目标效果

### 7.1 前后对照

| 场景 | 现在 | 改完 |
|---|---|---|
| 学生点"开始考试" | 请求打到"老师保存草稿"的路径上，实际不可用 | 拿到冻结快照里的题目（不含答案），开始计时 |
| 学生交卷 | 没有任何可用接口 | 立即出分（选择题全自动），成绩入库 |
| 学生再看这场考试 | 无数据 | 按**考试当时那一版快照**回看：我的答案 / 正确答案 / 解析 |
| 讲师改题库里某道题 | —— | **历史成绩一个字都不变**（判分读快照，兑现既定规则） |
| 题库列表 | 只有一个"引用 0 次"的假数字 | 真实三列：被引用 / 被考过 N 次 / 正确率 X% |
| 讲师批改页 | mock | 真实的作答列表 + 复核动作 |
| 讲师统计页 | mock | 真实分布 / 平均分 / 通过率（只计已交卷） |
| `exam.submitted_count` | 恒 0（死字段） | 真实已交卷人数 |

### 7.2 一个完整剧本

> 讲师小林发布了一份 5 题的随堂练习（挂在第 2 章第 3 节）。学生小周点进小节 → 开始作答
> → 交卷 → 立刻看到 80 分和逐题解析（题目来自**发布时冻结的那一版**）。
> 小林在题库里看到其中那道多选题「被考过 1 次 / 正确率 0%」→ 意识到题目歧义，去改了选项。
> **但小周的成绩单、以及小林统计页上的平均分，一个数字都没变**（快照保护）。
> 下一届学生做的是改后的版本。小林三年后回看统计，看到的仍是每场考试当时的真实分布。

### 7.3 量化目标

| 指标 | 目标 |
|---|---|
| 假数据 | 题库"正确率"在无作答时显示 `—`，**任何位置不出现 0/0 或恒 0 的假数字** |
| 判分一致性 | 同一次交卷无论重试多少次，成绩与计数累加**只发生一次**（幂等） |
| 快照保护 | 改题库题目后，历史成绩与统计**零变化**（可验证） |
| 链路闭合 | 学生交卷 → 讲师批改 → 讲师统计 → 题库正确率，四处数字**同源**（都来自 `exam_record*`） |

---

## 8 · 验收清单

**后端**
- [ ] `exam.section_id` 就位；随堂练习能按小节取到
- [ ] 未发布的卷子不能开始作答（报"还没发布"）
- [ ] 同一学生同一场考试只能有一条 `exam_record`（唯一键生效；重复交卷返回既有成绩）
- [ ] 判分**只读快照**：改题库题目的答案后重新交卷（新学生）不影响已有成绩
- [ ] 多选题不给部分分；单选/多选各自判分正确
- [ ] 交卷后 `question.answer_times / correct_times` 按题各自 +1，且**只加一次**
- [ ] `exam.submitted_count` 变成真实人数
- [ ] 统计只计已交卷学生；及格线取 `exam.pass_score`

**前端**
- [ ] 学生端：开始作答 / 交卷 / 看成绩 / 看记录，四个动作全部走真接口（无 404、无 console 报错）
- [ ] 答题回看是**当时那一版快照**的题目与解析
- [ ] 题库列表出现「被考过 / 正确率」；无作答时显示 `—`
- [ ] 批改页与统计页显示真实数据（不再是 mock）
- [ ] 回归：题库列表 / 组卷向导 / 建课向导 / 学员端 12 页 无回归

**你亲自看一眼**
- [ ] 用 `teacher` / `teacher2` 两个账号各跑一遍：建卷 → 发布 → （学生）作答 → 批改 → 统计

---

## 9 · 影响面 / 风险 / 回滚

| 项 | 内容 |
|---|---|
| 数据库 | 新增 2 张表 + `exam` 加 1 列 1 索引。**不动任何现有数据**（比 p13 的迁移安全） |
| 后端 | tj-exam 新增：2 个 PO/Mapper、1 个 Controller、判分服务；`ExamServiceImpl` 补 `section_id` 透传 |
| 需要重建的容器 | **exam-service**（与 p13 建议同批，只重建一次） |
| 前端 | `Practise.vue`、`pages/student/exams.vue`、`examReview.vue`、讲师端 `questions/marking/stats` |
| 回滚 | 两张新表 drop 即可；`exam.section_id` drop 即可；前端按 git 回退。**没有破坏性数据变更** |
| 风险 | ① 判分若误读题库当前答案 → 历史成绩会被改（**已用"只读快照"加验收项锁死**）② 重复交卷重复累加计数 → 正确率失真（**唯一键 + 幂等 + 验收项**） |

---

## 10 · 建议分两阶段（你说先做哪段）

| 阶段 | 内容 | 产出 |
|---|---|---|
| **P14-a（建议先做）** | 数据模型 + 判分 + 计数 + 题库显示正确率/被考次数 | **你要的"正确率"变成真数字**；链路后端就位 |
| **P14-b** | 学生端接真接口 + 讲师批改/统计接真数据 | 学生真能考、讲师真能批改看统计 |

**理由**：a 段做完，"正确率"立刻是真的（哪怕暂时只有你手工造的交卷数据）；b 段是把它接到界面上。
两段加起来才是完整闭环，所以如果你要一次做完也行 —— 只是要一次性重建 exam-service 并跑一圈完整回归。

---

## 11 · 实施进度（2026-09-16 已落地，实测通过）

### ✅ 已完成

| 部分 | 内容 |
|---|---|
| 迁移 | `deploy/mysql/p14-answer-grading.sql`（**已执行**，幂等）：`exam.section_id` + `exam_record` + `exam_record_detail` |
| 后端 | 新增 `ExamRecord/ExamRecordDetail` PO、两个 Mapper、`ExamSubmitDTO`、4 个 VO、`IExamAnswerService(+Impl)`、`ExamAnswerController`（**9 个端点**，比原计划的 8 个多一个 `GET /exam-records/{id}/result` 供讲师看作答明细）。exam-service 已重建 |
| 学生端 | `api/subject.js` 重写；`Practise.vue` 接真接口；`pages/student/exams.vue` 与 `examReview.vue` 换真实数据源 |

**实测（全部通过）**：按试卷 start / 按小节 start（题目**不含答案**）· 交卷判分 · **幂等**（重复交卷分数与计数都不变）· 只能考一次（再 start 被拒）· 我的记录 · 答卷回看（含标准答案+解析）· 讲师作答列表（带学生姓名）· 统计（平均分/通过率/分布）· 复核成绩 · `answer_times/correct_times 0→1` · `submitted_count` 变真数字。

### ⚠️ 实施中修掉的两个既有 bug

1. **单选永远判错**：`Practise.vue` 原来只把 `answers` 当数组处理，而单选/判断的 `v-model` 是**标量**（数字/布尔）→ 被过滤成空串 → 单选怎么答都算错。已改为标量/数组两种都收。
2. **「查看批阅」文案错**（用户指出）：学员看的是**自己的答卷**，不是批阅（批阅是讲师动作）。已改为「查看答卷」。

### ⏳ 未完成

| 项 | 说明 |
|---|---|
| 讲师端页面 | `marking.vue` / `stats.vue` 仍读 mock；`api/teacher/exams.js` 的 4 个函数需要加真实分支（后端接口已就绪） |
| 讲师挂试卷到小节 | **没有 UI** —— 出卷向导只能选课程，选不到小节。目前靠一条种子数据让链路可达 |
| 题库信号列（p13 §A5） | 被考次数/正确率的数据源已就绪，题库列表的展示列还没加 |
| 其他章节 | 已有 `section_id`，但「按小节取卷」只做了单条查询，没有做"一个小节多张卷"的支持 |

### 演示数据（可回退）

- 课程 1002 第三章下新增考试小节 `1002035「3.5 本章随堂测试」`（原目录**没有任何 type=3 小节**，学生本无入口）
- 试卷改名 `Spring Boot 基础随堂测试`，挂到该小节，及格线 3、30 分钟
- 学生 8（13800138005）有一条真实作答记录（供讲师批改/统计页有数据）；**demo（学生 3）是干净的**

---

## 12 · 待核实（环境起来第一步就核）

- `question` 表 `correct_times / answer_times` 的**实际列类型与历史值**（是否全 0）
- `tj_exam` 库里是否已有人建过 `exam_record` 之类的表（我搜的是代码，没查库）
- `POST /es/exams` 那个"拉题"语义，在**老站前端**是否还有别的调用方（若有，改路径时要一起处理）
- `Practise.vue` 里 `answers` 的编号口径（`parseInt` 后升序拼串）与题库 `question_detail.answer` 是否**同口径**
  —— 不同口径会导致"全判错"，这条必须实测

---

## 13 · 学员实测反馈修的两个问题（2026-09-16，无头复现 → 修 → 复测）

> 用户实测：① 从课程学习页点「3.5 本章随堂测试」进不去试卷，且**目录区看起来是空的**；
> ② 课程中心左下角的「我的学习」点一行直接跳视频页，想先看课程详情很不方便。

### 13.1 「点考试小节进不去」——两个独立原因叠在一起

**原因 A：`player.value` 是 null，`pause()` 抛错，把后面的流程整段掐断了。**
`learn.vue` 的 `playHadle` 里，考试分支第一步是 `player.value.pause()`。而播放器要在
`initPlay()` 里才被赋值，`initPlay` 又要求播放凭证里有 `appId` —— **媒资服务未启用时它提前 return**，
于是 `player.value` 永远是 null，`.pause()` 抛 TypeError，后面的
`ElMessageBox.confirm → startExaminationHandle（pageType=2）` 一行都没执行。

无头实测证据（修复前）：
```
A.examRow   = "3.5 本章随堂测试"
A.dialog    = NO DIALOG            ← 确认弹窗根本没出现
A.stageAfter= {videoVisible:true, practise:false}   ← 仍停在视频区
```

**原因 B：章节名是白字，白底上看不见 —— 这才是"目录是空的"的真身。**
`src/style/element/index.scss` 里有一条为**老深色学习页**写的全局规则：
```scss
.el-collapse-item__header, .el-collapse-item__wrap{ background-color:transparent; border:none; color:#FFF; }
```
新学员端是浅色底 → 章节标题 = 白底白字。折叠状态下（`el-collapse accordion`）连小节也一起藏起来，
于是目录区看起来"什么都没有"。实测 `getComputedStyle` 返回 `rgb(255,255,255)` 确认。

**修法**
| 改动 | 说明 |
|---|---|
| `if (player.value) player.value.pause()` / `?.play()` | 播放器没就绪不再把整个流程掐断 |
| 进页面就落在考试小节（`type=3`）→ 直接 `startExaminationHandle` | 深链/从课程详情页点考试小节进来也能直接看到卷子，且不再向媒体服务要凭证 |
| `Catalogue.vue` 里 `:deep(.el-collapse-item__header){ color:#1d1d1f }` | 用 scoped `:deep()`（权重 0,2,0）盖住那条全局 `#FFF`；顺带修 hover 变白、`练习` 徽标 hover 变白 |
| 视频放不了时在播放区写一句人话（`mediaError`） | 不再弹网关原文「服务不存在」；黑屏换成「本节视频暂时无法播放 + 原因」 |

**复测（同一探针）**
```
A.chapterTitleColor = rgb(29,29,31)         ← 章节名可见
A.dialog            = 继续考试              ← 弹窗出现
A.stageAfter        = {practise:true, paper:"Spring Boot 基础随堂测试", meta:"共 1 题 · 总分 5 · 限时 30 分钟 · 已作答 0 / 1"}
B（深链 ?sectionId=1002035）= 直接进试卷，点选项后「已作答 1 / 1」
C（普通视频小节）= 播放区显示「本节视频暂时无法播放」，无 toast
```

### 13.2 顺手修掉的两个老 bug

1. **目录时长显示成 `15:0`**：`{{(it.mediaDuration/60).toFixed(0)}}:{{item.mediaDuration%60}}`
   —— 分钟被四舍五入、秒数取的是**章**的时长（写成 `item` 而不是 `it`）。改成 `mmss()` → `15:00`。
2. **题目卡下方多一条横向滚动条**：Element 的 `.el-radio/.el-checkbox` 默认 `margin-right:32px`，
   最后一项把 `scrollWidth` 撑到 464（容器 432）。选项统一改「一行一个」（块级 + 去右边距），
   实测 `scrollWidth 492 == clientWidth 492`。

### 13.3 试卷外观：从「黑框里贴两张白纸」改成浅色卡片

`Practise.vue` 的容器原先也是照老深色页写的（`background:black` + `height:calc(100vh - 60px)` + `padding:30px`），
放进新学员端会多出 60px 把整页顶出滚动条。改为：容器透明、两栏用设计系统的 `soft-card`，
题目卡给 `max-height:calc(100vh - 210px)` 以便题目多时内部滚动。

### 13.4 课程中心：「我的学习」→「我的课程」，点一行去课程详情页

原行为 `goLearn(courseId)` → `/student/learn`（视频页）。学生想先看清"这门课是什么"（简介/目录/笔记）
却直接被丢进播放器；而"最近学习"在学习首页已经有了 → 改为课程入口。

实测：`cardTitle="我的课程"`、点第一行 → `#/student/courses/detail?id=1002`（页面标题「课程详情」）。
卡片右上角的「学习记录」入口保留（它是另一件事的入口）。

> 探针用完即删（`__measure.html`），并从库里清掉了探针产生的 demo 进行中作答记录。

---

## 14 · 补记：随堂练习的「入口」还有两处依赖（2026-09-16 实施时发现）

p14 把「按小节取卷」做通了，但当时那张卷是我**手工插进 exam 表**的。真做「老师配题 → 学生能考」时
才发现入口还差两环，已在 p15 一并修掉：

1. **上架不搬配题**：`course_cata_subject_draft` → `course_cata_subject` 以前没搬，
   正式表里没有这一节的题 → 课程目录接口算出来 `subjectNum=0`。
2. **`subjectNum` 的真正来源是 `question_biz`（考试库）**，不是 `course_cata_subject`：
   `/cs/courses/{id}/catalogs` 走 `ExamClient.queryQuestionIdsByBizIds` → 读 `question_biz`。
   前端 `subjectNum > 0` 才在小节上显示「练习」按钮 —— **只建卷不写它，学生看不到入口**。
   现在 `POST /es/practice/upsert` 在同一事务里同步 `question_biz`。

顺带修掉：`exam.duration` 的列默认值 90 会被写进不限时的随堂练习（学生看到「限时 90 分钟」）→ 显式置 0。
