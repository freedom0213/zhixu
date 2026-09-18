# P11 · 教师端接口契约（阶段 1：题库）

> 这份文档回答一个问题：**前端和后端谁先做。**
> 答案不是二选一 —— **先把契约冻住，然后前端 mock 先行，后端按同一份契约跟进，最后拨一个开关切换。**

---

## 一、为什么不是「先 A 后 B」

三条判断：

1. **两周内要能演示。** 这是简历项目，页面必须打开就能看。等后端把 5 张表建完再动前端，等于把演示时间押在后端进度上。
2. **后端不是零起点。** exam-service 里题库的增删改查大部分已经在了（见第三节），真正缺的只有两块：**Excel 导入校验**和**试卷快照**。所以「后端要从头写」这个前提本身不成立。
3. **唯一必须先定的是数据形状，不是实现。** 字段名、枚举值、分页形状、权限语义 —— 这些一旦两端各拍一次就会返工。把它们冻成一份可执行的契约（mock 就是契约的可运行版本），两端就能并行。

所以顺序是：

```
① 冻结契约（本次已完成）
      ↓
② 前端用 mock 把页面跑通（阶段 1 正在做）
      ↓
③ 后端按同一份契约实现
      ↓
④ VITE_TEACHER_MOCK=0 切换，页面代码一行不改
```

**切换点只有一个环境变量**，这是这套做法成立的关键。

---

## 二、切换方式

| 变量 | 值 | 行为 |
|---|---|---|
| `VITE_TEACHER_MOCK` | 未配置 / `1` | 走 `src/mock/teacher/*` 内存数据（默认） |
| `VITE_TEACHER_MOCK` | `0` | 走 `@/utils/request` 打真实后端 |

定义位置：`src/config/teacherApi.js`。默认开 mock —— 后端没起也不会白屏。

---

## 三、后端现状盘点（此前逐个控制器核对过）

| 能力 | 现状 | 结论 |
|---|---|---|
| 题目新增 `POST /es/questions` | 已有 | 复用 |
| 题目分页 `GET /es/questions/page` | 已有 | 复用（**字段名待核对，不一致就在 api 层做适配**） |
| 题目改 / 删 `PUT,DELETE /es/questions/{id}` | 已有 | 复用；删除语义要改成「停用」 |
| 题目批量绑定到小节 `POST /es/question-biz/list` | 已有 | 阶段 2 组卷用 |
| **Excel 导入（解析 + 逐行校验）** | **缺** | 阶段 1 需新增 |
| **题目的 status（可用 / 已停用）** | **缺** | 阶段 1 需新增 |
| **知识点（课程级）** | **缺** | 阶段 1 需新增（与课程绑定） |
| **题目使用次数 / 被哪些试卷引用** | **缺** | 阶段 1 需新增（停用提示用） |
| 试卷快照 `paperVersion` / `snapshotItems` | **缺** | 阶段 2/3 |
| 课程知识点维护接口 | **缺** | 阶段 1（页面本轮不做，接口先留） |

> ⚠️ 另一处实现期会卡住的事（与题库无关）：**media-service 没有腾讯云 VOD 凭证**，`deploy/.env` 里一条 `TENCENT_*` 都没有。真实上传视频走不通，需先补子应用凭证。属阶段 4。

---

## 四、契约正文（阶段 1）

### 4.1 题目对象

```jsonc
{
  "id": 1001,
  "type": "single",                  // single | multi —— 本期只做选择题
  "stem": "JVM 中哪个内存区域通常不会发生垃圾回收？",
  "options": [                        // 2–8 个，字母由后端生成 A~H
    { "key": "A", "text": "程序计数器" },
    { "key": "B", "text": "堆区" }
  ],
  "answer": ["A"],                    // 字母数组；单选 1 个，多选多个
  "analysis": "程序计数器只记录……",     // 选填
  "difficulty": "easy",               // easy | medium | hard（固定三档，不给自定义）
  "knowledgePoints": ["JVM 内存"],     // 课程级数据，只能是该课程知识点树里的名字
  "courseId": 301,                    // 单选：一道题只归一门课程
  "courseName": "Java 集合与并发编程",
  "creatorId": 42,                    // 出题人（系统记录，老师不填）
  "creatorName": "王老师",
  "status": "enabled",                // enabled | disabled —— 停用替代删除
  "usageCount": 12,                   // 被多少份试卷引用（系统统计）
  "createdAt": "2026-09-10 14:20"
}
```

**刻意不含 `score`（分值）**：分值属于试卷，同一道题在不同卷里可以不同分。带进题库会造成「改一次题库、历史卷子分数全变」。

**字段计数**：老师要填的 8 个 —— 题型 / 题干 / 选项 / 正确答案 / 解析 / 难度 / 知识点 / 所属课程；
系统自动记录的 1 个 —— 出题人。合计 9 个。

### 4.2 接口清单

| 方法 | 路径 | 用途 | 关键约束 |
|---|---|---|---|
| GET | `/es/questions/identity` | 当前教师身份 | 返回 `{ id, name, boundSchool, schoolName }`；`boundSchool=false` 时前端不显示「全部题目」分段与「出题人」列 |
| GET | `/es/questions/page` | 分页查询 | 入参 `keyword,type,difficulty,knowledgePoint,courseId,status,scope(mine\|all),page,size`；返回 `{list,total,page,size}` |
| GET | `/es/questions/count` | 分段计数 | 返回 `{ all, mine, disabled }` |
| GET | `/es/questions/{id}` | 详情（预览） | 含选项、答案、解析 |
| GET | `/es/questions/{id}/usage` | 引用情况 | 返回 `{ usageCount, papers[] }`，停用前提示用 |
| POST | `/es/questions` | 新建 | 保存即入库，无二次提交 |
| PUT | `/es/questions/{id}` | 更新 | **只能改自己出的题**，否则 403 |
| PUT | `/es/questions/{id}/status` | 停用 / 启用 | `{ status }`；**不硬删** |
| PUT | `/es/questions/batch` | 批量 | `{ ids, difficulty?, knowledgePoints?, status? }`；**只对自己出的题生效**，返回 `{ changed, skipped }` |
| POST | `/es/questions/import` | Excel 导入 | `multipart/form-data`；**逐行校验**，返回 `{ success[], failed[], skipped[] }` |
| GET | `/cs/courses/{id}/knowledge-points` | 知识点树 | 课程级；录题时只能选，不能新建 |

### 4.3 权限模型（三条硬规则）

1. **只能编辑自己出的题。** 别人的题只有两个动作：查看、引用。这是「共享池子」能成立的前提。
2. **停用不删除。** 已有快照保护历史试卷，停用只改变「今后能不能被选到」。
3. **`scope=all` 只返回同校老师的题**；未绑定学校时该范围为空。

### 4.4 Excel 导入的返回形状（这一块最容易被做粗）

```jsonc
{
  "fileName": "questions.xlsx",
  "total": 21,
  "success": [{ "row": 1, "id": 2000 }],
  "failed":  [{ "row": 5, "type": "单选题", "stem": "……", "reason": "「正确答案」为空" }],
  "skipped": [{ "row": 7, "type": "单选题", "stem": "……", "reason": "题库里已存在相同题干，已跳过（不覆盖、不新增副本）" }]
}
```

三条必须做到：
- **逐行校验，不是整份退回**：通过的行已经入库，只有失败的行需要改。
- **failed 必须精确到「哪一行、哪个字段、什么问题」**。只说「有 3 处错误」等于没说，老师得自己对整份表。
- **已存在的题干 → skipped，绝不覆盖、不新增副本**。覆盖别人的题会破坏引用它的试卷。

### 4.5 模板列（与字段一一对应）

`题型 | 题干 | 选项 | 正确答案 | 解析 | 难度 | 知识点 | 所属课程`

- 选项用竖线分隔：`选项1|选项2|选项3|选项4`
- 答案填字母：单选 `B`，多选 `AB`
- 知识点多个用竖线分隔；必须已存在于该课程的知识点树里，否则该行失败

---

## 五、后端阶段 1 的开工清单

按依赖顺序：

1. **`question` 表加 `status`、`creator_id`、`usage_count`**（迁移脚本）
2. **知识点表 + 课程绑定** + `GET /cs/courses/{id}/knowledge-points`
3. **改写 `GET /es/questions/page`** 支持 scope / 知识点 / 状态筛选，并返回 `creatorName`
4. **`PUT /es/questions/{id}/status`、`PUT /es/questions/batch`**（含「只对自己生效」的权限判断）
5. **`POST /es/questions/import`** —— Excel 解析 + 逐行校验 + 三段结果
6. **`GET /es/questions/{id}/usage`** —— 统计引用它的试卷数

第 5 项是唯一工作量较大的，其余都是常规 CRUD。

---

## 六、已落地的前端文件（阶段 1 全部）

| 文件 | 作用 |
|---|---|
| `src/config/teacherApi.js` | mock 开关（`VITE_TEACHER_MOCK`）+ 服务前缀 `/es` |
| `src/config/teacherNav.js` | 教师端 8 项导航 |
| `src/config/teacherDict.js` | 题型 / 难度 / 状态字典（页面与组件共用） |
| `src/api/teacher/questions.js` | 接口层：拼 URL + mock/真实切换 |
| `src/mock/teacher/questions.js` | mock 数据（= 契约的可执行版本） |
| `src/style/teacher.scss` | `.teacherShell` 作用域 + 通用按钮 `.q-btn` / `.q-act` |
| `src/components/shell/TeacherTopBar.vue` | 教师端顶栏 |
| `src/pages/teacher/TeacherLayout.vue` | 教师端外壳 |
| `src/pages/teacher/questions.vue` | Q2 题库列表 |
| `src/pages/teacher/questionEdit.vue` | Q3 新建 / 编辑题目（同一表单，`?id=` 即编辑态） |
| `src/pages/teacher/questionImport.vue` | Q4 Excel 导入（三态 + 逐行校验） |
| `src/pages/teacher/components/QuestionPreview.vue` | 预览抽屉（Q6 左半） |
| `src/pages/teacher/Placeholder.vue` | 未实装模块占位（标题取 `meta.title`） |
| `src/router/modules/teacher.js` | `/teacher/*` 路由 |

**改动既有文件（均为加法式，学员端零回归）**：
- `src/router/index.js` —— 注册 teacherRouters、更新注释
- `src/components/shell/AppSidebar.vue` —— 加 `items/navLabel/brandName/profilePath/originPath` 五个 prop（默认值 = 学员端原行为）
- `src/config/loginRedirect.js` —— 回跳前缀白名单加入 `/teacher`（学员端行为不变）

---

## 七、实测结论（无头 Chrome，1440×900）

题库列表（Q2）：

| 指标 | 结果 |
|---|---|
| 路由 / 外壳 | `/teacher/questions` 未被兜底吃掉；教师外壳渲染；导航 8 项、选中「题库」 |
| 列宽对齐 | 表头与数据行列宽逐列一致（36,353,64,56,112,144,76,64,128） |
| 横向溢出 / 塌陷 | 0 处越界元素、0 个塌陷盒子 |
| 分页 / 计数 | 共 24 题 · 每页 20 条（全部 24 / 我的 18） |
| 预览抽屉 | 选项 4 个、正确答案高亮 3 个、显示「被 6 份试卷用过」 |
| 停用确认 | 已被引用的题弹窗提示「有 N 份试卷正在使用它」，没被引用过的直接生效 |
| 批量操作条 | 吸底 y=816，首屏可见 |

新建 / 编辑题目（Q3）：

| 指标 | 结果 |
|---|---|
| 表结构 | 7 个填充分组 ↔ 8 个字段；选项默认 4 行，字母 A~D 自动编号 |
| 知识点联动 | 未选课程时提示「先选择所属课程」；选 301 后列出 3 个知识点；选 302 后换成另外 3 个 |
| 校验 | 空题干 → 「题干不能为空。」；中间空行 → 「选项 B 是空的，请补齐或删掉（只能忽略末尾的空行）。」；多选仅 1 个答案 → 「多选题至少要有 2 个正确答案。」 |
| 末尾空行 | 只填 2 个选项 + 2 行留空 → **正常入库**，且保留「所属课程」与「知识点」便于连续录题 |
| 编辑态（`?id=1001`） | 题干 / 4 个选项 / 答案 A / 难度「容易」/ 课程 301 / 知识点「JVM 内存」全部正确回填；编辑态隐藏「保存并继续新建」 |
| 横向溢出 | 0 |

Excel 导入（Q4）：

| 指标 | 结果 |
|---|---|
| 门槛 | 未选文件时「上传并校验」禁用 |
| 校验结果 | 2 张卡；chips「已入库 18 题 / 失败 2 行 / 跳过 1 行（已存在）」；逐行表 3 行，列宽与表头一致 |
| 逐行措辞 | 「第 5 行 · 单选题 · … · 「正确答案」为空」 |
| 重新选择 | 清空后 `input.value` 归零，重挑同名文件仍能触发（否则老师会以为点了没用） |
| 再导一份 | 回到单卡初始态，文件与结果都清空 |
| 横向溢出 | 0 |

学员端回归：导航 10 项、品牌「知序学堂」、顶栏「优惠券」按钮与「搜索课程」占位全部原样，`/student/courses` 正常。

---

## 八、阶段 1 已闭环，下一步

**阶段 1（题库）已完成**：列表 Q2 + 新建/编辑 Q3 + Excel 导入 Q4，全部可在无后端情况下演示。

**下一步 · 阶段 2（组卷与发布）**：E2 题库选题（三栏）→ E3 现场新建题 → E4 发布设置。
这一阶段才让「**引用 + 发布快照**」这条主线成型 —— 也是整套设计里最值得讲的部分：

1. 新建试卷时**引用**题库的题（改题库能同步修正草稿卷）
2. **发布时才生成快照**（已发布 / 有作答记录的卷子不可变）
3. 学生查看答卷、教师批改、成绩统计**三个回看入口读同一份快照**

对应后端要新增 `paperVersion` 与 `snapshotItems`（阶段 2/3）。

---

## 九、阶段 2（组卷与发布）· 契约补充

### 9.1 考试 / 试卷接口（全部为新增，走 `/es` 前缀）

| 方法 | 路径 | 用途 | 说明 |
|---|---|---|---|
| GET | `/es/exams/page` | 考试列表 | `{ status?, keyword?, page, size }` → `{ list, total }` |
| GET | `/es/exams/count` | 状态计数 | `{ all, draft, published, closed }`（published 含批改中） |
| GET | `/es/exams/{id}` | 详情 | 含 `expandedItems`；**已发布必须先读快照**，读不到才回退引用 |
| POST | `/es/exams` | 新建草稿 | 草稿**只存引用**：`items = [{ questionId, score }]` |
| PUT | `/es/exams/{id}` | 更新草稿 | 只有草稿可编辑，否则 409 |
| POST | `/es/exams/{id}/publish` | 发布 | 服务端在此刻生成快照，返回 `paperVersion` + `snapshotAt` |
| DELETE | `/es/exams/{id}` | 删除 | **仅草稿**；已发布的卷子有作答记录，只能结束不能删 |

**组卷选题不新增接口** —— 复用 `GET /es/questions/page`（`scope=all` + 筛选）。「从题库选题」在数据上没有独立模型，只是题库的一个查询视图。

### 9.2 核心数据模型：草稿存引用，发布才生成快照

```
exam.items          = [{ questionId, score }]        // 草稿：指针，跟着题库变
exam.snapshotItems  = [{ questionId, score, type, stem, options,
                        answer, analysis, difficulty,
                        knowledgePoints, courseId }]  // 发布瞬间冻结的完整副本
exam.paperVersion   = 'v1'   // 发布后才有
exam.snapshotAt     = '2026-09-25 09:00'
```

- 纯引用的问题：事后改题库会**静默改写历史考卷**；
- 纯快照的问题：出卷过程中修正一道题还得重新选一遍；
- 所以按状态切：**草稿引用（改题库，草稿跟着对）→ 发布快照（之后怎么改都不影响）**。学生答卷 / 教师批改 / 成绩统计三个回看入口读同一份快照。

### 9.3 阶段 2 后端开工清单

1. `exam` 表：`items`（JSON 引用）+ `snapshot_items`（JSON 快照，发布前为 NULL）+ `paper_version` + `snapshot_at`
2. 上表 7 个接口（发布接口里做「引用 → 快照」的冻结动作）
3. 权限：草稿只能本人编辑/删除；发布需 `items` 非空且必填时间完整

### 9.4 阶段 2 新增前端文件

| 文件 | 作用 |
|---|---|
| `src/api/teacher/exams.js` | 考试接口层（mock/真实三元切换） |
| `src/mock/teacher/exams.js` | 内存 mock —— **引用/快照双模型的可执行版本** |
| `src/pages/teacher/exams.vue` | 考试管理列表（状态 chips / 行动按状态分叉 / 草稿才可删） |
| `src/pages/teacher/examWizard.vue` | 三步向导：考试信息 → 组卷（题库选题三栏 + 现场新建复用录题表单）→ 发布设置 |

---

## 十、阶段 2 实测结论（无头 Chrome，1440×900）

考试管理列表（`/teacher/exams`）：

| 指标 | 结果 |
|---|---|
| 数据 | 4 场考试；chips「全部 4 / 草稿 1 / 已发布 2（含批改中）/ 已结束 1」 |
| 权限分叉 | 草稿行 2 个动作（继续编辑 · 删除）；已发布/已结束行**没有删除**，只有去批改/统计 |
| 列宽对齐 | 表头与数据行逐列一致（440,72,96,132,72,80,156），mismatch=0 |
| 横向溢出 / 塌陷 | 0 / 0 |

新建向导全流程（`/teacher/exams/new`）：

| 步骤 | 结果 |
|---|---|
| ① 空表单点下一步 | 拦住，停留在步骤 1（名称与课程必填） |
| ① 填名称 + 选课程 | 进入步骤 2；题池**自动预选同一门课程** |
| ② 题库选题 | 池内 6 题；勾 2 题进卷，合计 10 分 |
| ② 改分值 | 第一题 5→10 分，合计实时变 15 分；试卷结构统计同步 |
| ③ 发布前拦截 | 开始/结束时间未填时「发布」按钮禁用（`canPublish`） |
| ③ 发布 | 弹窗「已发布，试卷快照已生成」· 版本 v1 · 冻结时间正确 |
| 几何 | 三步均 0 横向溢出、0 塌陷 |

编辑草稿（`/teacher/exams/new?id=9004`）：

| 指标 | 结果 |
|---|---|
| 表单回填 | 标题「编辑考试」；名称「MySQL 性能优化 · 随堂测」正确回填 |
| 已选清单 | 步骤 2 中 2 题全部回填 |
| 横向溢出 | 0 |

学员端回归：导航 10 项、品牌「知序学堂」原样，0 溢出。

**过程中修掉一个真 bug**：从「新建」跳「编辑」只是 query 变化（`?id=`），vue-router 会复用组件实例、`onMounted` 不再执行 → 表单不回填。已把初始化抽成 `init()` 并 `watch(examId)` 重新加载（同时关掉可能残留的发布弹窗）。

---

## 十一、阶段 3（考务）· 批改与统计

### 11.1 新增接口（4 个，走 `/es` 前缀）

| 方法 | 路径 | 用途 | 说明 |
|---|---|---|---|
| GET | `/es/exams/{id}/submissions` | 交卷列表 + 汇总 | `{ status?, keyword? }`；summary 含 enrolled / submitted / absent / confirmed / avgScore / passRate / highest / lowest |
| GET | `/es/exams/{id}/submissions/{subId}` | 单份答卷 | 含按**快照**展开的逐题：学生答案、对错、得分 |
| PUT | `/es/exams/{id}/submissions/{subId}/confirm` | 复核确认 | 客观题已自动判分，此动作 = 教师复核完毕 |
| GET | `/es/exams/{id}/stats` | 统计 | 分数分布 5 档 / 逐题正确率 / 逐知识点正确率（薄弱在前） |

### 11.2 两条硬规则（已写进 mock 与页面）

1. **判分与统计只读快照**：学生答案对比的是 `snapshotItems.answer`，逐题题干 / 选项 / 知识点全部来自快照 —— 页头有「快照口径条」向老师声明这一点。回看页**禁止查题库**。
2. **数字单一来源**：列表页展示的 `submittedCount / avgScore / passRate` 从提交记录**反推**，列表、批改、统计三处同源，不再各写一份。

### 11.3 统计口径（别把分母做错）

- 平均分 / 通过率 / 分布**只计已交卷学生**，缺考不进分母
- 正确率配色：<50% 红 / 50–75% 橙 / >75% 绿，只染文字与细条
- 知识点正确率按「薄弱在前」排序 —— 这份数据将来直接喂学情分析

### 11.4 阶段 3 新增前端文件

| 文件 | 作用 |
|---|---|
| `src/pages/teacher/marking.vue` | T4 批改：左学生列表（搜索 + 状态 chips）+ 右答卷复核（逐题学生答案 vs 快照答案，确认成绩动作） |
| `src/pages/teacher/stats.vue` | T8 统计：概览 4 卡 + 分数分布 + 知识点掌握 + 逐题正确率表 |
| `src/mock/teacher/exams.js`（扩展） | 确定性种子伪随机生成 87 份交卷记录（LCG，刷新数字不变）；判分按快照自动完成 |
| `src/api/teacher/exams.js`（扩展） | 上述 4 个接口的 mock/真实切换 |

### 11.5 阶段 3 实测结论（无头 Chrome，1440×900）

批改页（9001，42 名学生）：

| 指标 | 结果 |
|---|---|
| 列表 | 42 人；chips「全部 42 / 已交 40 / 缺考 2」计数正确 |
| 答卷 | 5 题逐题展示；对错判定真实（✓/✕）；「正确答案 / 学生选择」双标记 |
| 动作 | 「确认成绩」点击后变「已复核」，列表同步 |
| 缺考 | 缺考学生答卷显示「缺考」，无分可确认 |
| 搜索 | 按班级「2301」过滤 → 14 人 |
| 溢出 | 0 |

统计页：

| 指标 | 结果 |
|---|---|
| 9001 | 40/42 · 平均 18.4/30 · 通过 53% · 分布 5 档 · 逐题 5 行 · 知识点 3 项 |
| 9002 | 36/38 · 平均 16.5/20 · 通过 86% —— **与 9001 数字完全独立** |
| 表格 | 逐题表列宽与表头逐列一致，mismatch=0 |
| 草稿 9004 | 空态「还没有发布，没有可统计的成绩」 |
| 溢出 | 0 |

学员端回归：10 项导航 / 品牌 / 优惠券按钮原样。

### 11.6 阶段 3 修掉的三个真问题（都在数据层，值得引以为戒）

1. **种子考试从来没有 snapshotItems** —— 只有 `paperVersion` 字段。批改页全显示「未作答」、知识点统计为空。修复：mock 初始化时按题库现状冻结副本（等价于发布那一刻）。
2. **及格线 60 分、满分 30 分** —— mock 自相矛盾，通过率恒 0%。修复：及格线按满分 60% 重新设定（18 / 12 / 11）。
3. **`:id` 变化不触发 onMounted**（与阶段 2 的 `?id=` 同根因）—— 从 9001 统计跳 9002 统计，组件复用、数字残留。修复：stats / marking 均抽 `load()` + `watch(() => route.params.id)`。

**下一步 · 阶段 4**：建课五步向导（A1→A2→A3→A5→A6）+ T1 工作概览 + T2 我的课程。A3 视频上传依赖腾讯云 VOD 凭证，届时做「UI + 本地模拟进度」并标注后端待补。

---

## 十二、阶段 4 进行中 · T1/T2 已完成 + 部署安全阀

### 12.1 mock 不会进 docker（安全阀已落）

「冻结契约 → mock 先行」的终点就是最终形态：**docker 里跑的是真前端 + 真后端，mock 只是开发期脚手架**。切换只有一个环境变量。为杜绝「假数据被打进容器」，`src/config/teacherApi.js` 现在的行为是：

| 场景 | 行为 |
|---|---|
| 开发（vite dev），未配置 | mock 开（页面打开就能演示） |
| 开发，`VITE_TEACHER_MOCK=0` | 走真实后端 |
| **生产构建（vite build / docker），未配置** | **mock 强制关** —— 教师端接口未就绪时页面显示后端待补，而不是假数据 |
| 生产构建，显式 `VITE_TEACHER_MOCK=1` | 才会打包 mock（给容器里演示教师端用的明示开关） |

### 12.2 T1 工作概览 / T2 我的课程（新增）

| 文件 | 作用 |
|---|---|
| `src/api/teacher/dashboard.js` | 2 个聚合接口（`/cs/teacher/overview`、`/cs/teacher/courses`） |
| `src/mock/teacher/dashboard.js` | 聚合 mock —— 全部从 questions / exams 既有数据**推导**，不另写一份 |
| `src/pages/teacher/dashboard.vue` | T1：问候 + 4 概览卡 + 待办（数字带链接直达落地页）+ 近期考试 + 课程速览 |
| `src/pages/teacher/courses.vue` | T2：课程卡 2×2（章节/学生/题目/考试/知识点），「查题目」带 courseId 跳题库筛选 |
| `src/mock/teacher/exams.js`（扩展） | 新增只读口 `submissionsOf()` / `allExams()` 供聚合页使用 |
| `src/mock/teacher/questions.js`（扩展） | 导出 `QUESTIONS`（此前为模块私有） |

聚合纪律：待复核 / 缺考 / 草稿数从提交记录反推，与批改页同一个数字；课程卡的题目数与题库列表的筛选口径一致（四门课 6+6+7+5=24，与题库总数吻合）。

### 12.3 阶段 4 剩余

建课五步向导 A1→A2→A3→A5→A6（A3 视频上传做「UI + 本地模拟进度」，标注后端待补）。

### 12.4 本轮环境坑（两记）

1. **vite 预构建失败会让 server 永久卡死**：新文件 import 了未导出的变量（`QUESTIONS` / `EXAMS`），esbuild 报错后 optimizer 挂掉，之后所有模块请求无限等待、静态文件却正常 —— 症状极具迷惑性。修完 import 还必须**重启 server**（旧预构建 promise 已死，不会自愈）。
2. **`--force` 不是万能的**：卡住时以为是缓存问题加了 `--force`，结果卡得更死；清缓存 + 普通启动反而秒起。遇到「静态 200 / 模块 000」先看 server 日志里的 `X [ERROR]` 行 —— 多半是代码触发了 optimizer 报错，比猜快得多。

### 12.5 聚合接口的精确形状（补全 §12.2 的一句话定义）

**GET `/cs/teacher/overview`** →

```jsonc
{
  "teacher": { "id": 42, "name": "王老师", "boundSchool": true, "schoolName": "示例大学" },
  "teaching": { "courses": 4, "students": 300, "questions": 18, "exams": 4 },
  "todo": { "toReview": 47, "absentTotal": 4, "drafts": 1 },   // 从提交记录反推，与批改页同源
  "recentExams": [{
    "id": 9001, "name": "…", "courseName": "…", "status": "marking",
    "submittedCount": 40, "enrolled": 42, "passRate": 0.53,
    "paperVersion": "v1", "snapshotAt": "2026-09-20 09:00"
  }],
  "courses": [{ "id": 301, "name": "…", "chapters": 5, "students": 96, "knowledgePoints": 3 }]
}
```

**GET `/cs/teacher/courses`** →

```jsonc
[{
  "id": 301, "name": "Java 集合与并发编程",
  "chapters": 5, "students": 96,
  "knowledgePoints": ["集合框架", "并发基础", "JVM 内存"],   // 名称数组，前端只展示
  "questionCount": 6,                                        // 与题库列表按课程筛选的口径一致
  "examCount": 1,
  "lastExam": { "name": "…", "status": "closed" }            // 没考过为 null
}]
```

两条实现注意：① `toReview` / `absentTotal` / `questionCount` 必须**从明细反推**，不要另存一份汇总表（本项目两次靠这条规矩避免数字打架）；② `knowledgePoints` 只需要名称数组，前端不编辑它。

### 12.6 契约完成度

| 部分 | 状态 |
|---|---|
| 题库 10 接口（§4） | ✅ 完整 |
| 考试 7 接口（§9） | ✅ 完整 |
| 批改 / 统计 4 接口（§11） | ✅ 完整 |
| 聚合 2 接口（§12.5） | ✅ 完整（本节补全） |
| 建课向导（A1→A6） | ❌ 待建课向导实装后补 §13 —— 课程 CRUD / 章节课时 / 视频上传（VOD 凭证）/ 课程发布 |

`/cs/courses/simpleInfo/list` 为既有接口，不在新契约范围。已实装 9 个页面消费的 21 个接口全部有契约 —— **后端可以按 §4/§9/§11/§12.5 直接开工**，写完拨 `VITE_TEACHER_MOCK=0` 逐模块联调。

---

## 十三、阶段 4 收尾 · 建课五步向导（契约最终章）

### 13.1 建课接口（9 个，走 `/cs` 前缀）

| 方法 | 路径 | 用途 | 说明 |
|---|---|---|---|
| GET | `/cs/teacher/course-draft` | 读取草稿 | 含每步内容 + `checks`（上架前校验清单，**从明细推导**） |
| PUT | `/cs/teacher/course-draft/basic` | 步骤① 基本信息 | 名称 / 分类 / 价格 / 有效期 / 封面 / 简介 |
| PUT | `/cs/teacher/course-draft/catalog` | 步骤② 目录整体保存 | 章 → 小节两层，增删改排序**收拢成一个数组**，简单且不易出同步错 |
| PUT | `/cs/teacher/sections/{sid}/video` | 步骤③ 视频登记 | 上传完成 / 替换 / 删除后调用；服务端登记时长并**返回 duration** |
| PUT | `/cs/teacher/sections/{sid}/preview` | 试看开关 | 游客可见部分 |
| PUT | `/cs/teacher/sections/{sid}/quiz` | 步骤④ 小节配题 | `{ count, totalScore, dist[] }`；题目来自题库（引用，非复制） |
| POST / DELETE | `/cs/teacher/course-draft/teachers` | 步骤⑤ 讲师增删 | 主讲 / 助教 |
| POST | `/cs/teacher/course-draft/publish` | 提交上架 | 基本信息 / 目录不完整 → **422**；视频 / 配题只警告（可上架后补） |

### 13.2 视频上传（契约里唯一的「后端待补」）

真上传分三步，前端已按此形状预留：**① 签发腾讯云 VOD 临时凭证 → ② 前端直传点播 → ③ 回调登记时长/封面**。当前 `uploadVideoSim()` 做本地模拟进度（180ms 心跳，页面无感知真假）；凭证接口就绪后只换接口层实现，页面不动。页面上有明示文案「本地模拟进度 · 真上传依赖点播凭证」。

### 13.3 与设计稿的两处有意偏离（都是已拍板的既定决策）

1. **A5 题型分布只有单选/多选** —— 设计稿示例里有「判断 / 主观」，但题库本期只做选择题，向导如实反映题库能力，不做假入口。
2. **A2 拖动排序 → ↑↓ 按钮** —— 键盘可达、无头可测，语义不变；拖拽手柄视觉保留（≡）。

### 13.4 建课向导实测（无头 Chrome，1440×900，五步全流程）

| 步骤 | 结果 |
|---|---|
| ① | 草稿回填；名称非空显示「名称可用」chip |
| ② | 3 章 9 节；添加章 → **两步确认删除**（误删防护，实测第 1 章不被误伤）；加/删小节即时保存 |
| ③ | 三种行状态（已上传 / 上传中 / 未上传）；**DataTransfer 注入真实 File 触发模拟上传 → 完成登记 → 校验数字 4/9→5/9**；试看开关切换；「后端待补」明示 |
| ④ | 尚未配题 chip 7 个；题库选题面板加载本课 6 题 → 选 2 题入小节 → 题量/分布/总分回填 |
| ⑤ | 校验清单进入时**刷新**（修掉挂载快照 bug）；讲师增删；提交上架 → 弹窗 |
| 回归 | 我的课程页 4 卡 + 「＋ 新建课程」入口；学员端零回归 |

**过程中修掉三个真问题**：删除章无确认（自动保存向导里误点即丢整章）；校验清单是挂载快照（传完视频数字不动）；上传完成后时长不回填（mock 登记了但没带回给页面）。

### 13.5 教师端前端至此全部完成

已实装 10 页 + 1 向导：工作概览 / 我的课程 / 建课向导 / 题库三页 / 考试管理 / 出卷向导 / 批改 / 统计。T5/T6/T7（学生分析 / 师生对话 / 个人资料）与「试卷批改」导航位仍为占位页 —— 不在四阶段范围，需要时按同一套契约先行的打法加。

---

## 十四、后端实现状态（tj-exam 模块，第一轮 CRUD）

> 结构事实：网关 `/es/**` StripPrefix 后即 exam-service（`/es/questions` = `QuestionController /questions`）；
> MyBatis-Plus + `UserContext.getUser()` + `UserClient` 查用户。迁移 SQL：`deploy/mysql/p11-teacher-backend.sql`。

### 14.1 本轮已实现（编译通过，未起服务）

| 契约 | 接口 | 实现 |
|---|---|---|
| §4 | `GET /questions/identity` | IdentityVO（boundSchool 恒 true —— 学校/院系体系未建模，代码有注释） |
| §4 | `PUT /questions/{id}/status` | 停用不删除；仅出题人本人 |
| §4 | `PUT /questions/batch` | 只对自己出的题生效，返回 `{changed, skipped}` |
| §4 | `GET /questions/{id}/usage` | `JSON_CONTAINS(items, ...)` 统计引用考试，返回 usageCount + papers |
| §4 | `GET /knowledge-points?courseId=` | 新表 knowledge_point；**路径从 /cs 调整到 /es**（知识点归属 exam 库，联调时前端接口层改一行 URL） |
| §9 | `GET /exams/page` / `count` / `{id}` / POST / PUT / DELETE / `{id}/publish` | 全新 exam 表 + ExamController/Service 全套；publish 在服务端生成快照（引用→快照的切换点） |
| §4 | 分页扩展 | question 加 `status` 列；分页参数支持 status；VO 返回 `creatorName` |

### 14.2 数据库变更

- `question` 加 `status TINYINT DEFAULT 1`
- 新表 `knowledge_point`（course_id + name 唯一；种子数据 302 课 3 条）
- 新表 `exam`（items / snapshot_items 为 JSON 列；paper_version / snapshot_at；submitted_count 批改阶段回填）

### 14.3 本轮不做（契约在，下轮继续）

- **Excel 导入**（§4.4）—— 唯一工作量大头
- **批改 / 统计 4 接口**（§11）—— 无作答数据来源，需先有学生端交卷链路
- **聚合 2 接口**（§12.5）与 **建课 9 接口**（§13）
- **题目 ↔ 知识点关联表** —— knowledge_point 表已建，但题目侧没有挂知识点（snapshot 里为空数组），按知识点筛选暂不生效
- **前端接口层适配**（枚举映射：single/multi ↔ 1/2、字母 ↔ 数字答案、URL 路径微调）—— 后端字段与契约的偏差集中在这一层解决，页面代码不动

### 14.4 实现注意（已写进代码注释）

1. `exam` 状态枚举用 **0 草稿 / 1 已发布 / 2 批改中 / 3 已结束**（前端 mock 用字符串，适配层映射）
2. `question.type` 1 单选 / 2 多选；`difficulty` 1-3；答案存数字编号串（"1" 或 "1,3"）—— `ExamDetailVO.toLetters()/toOptions()` 负责转契约字母形状
3. 快照里 `courseId` 用 `cateId3` 近似填充（三级分类与前端 mock 的 301-304 课程体系不同源，联调时对齐口径）
4. snapshot 写入用 `JacksonTypeHandler`（autoResultMap = true），MySQL JSON 列

---

## 十五、建课后端（tj-course）· 契约适配层实现

> 迁移 SQL：`deploy/mysql/p11-course-backend.sql`（已在 tj_course 库执行，幂等）
> 代码：`tj-course/.../controller/TeacherCourseDraftController.java` +
> `service/ITeacherCourseDraftService` + `impl/TeacherCourseDraftServiceImpl` +
> `domain/dto/TeacherCourseDraftDTO` + `domain/vo/TeacherCourseDraftVO`

### 15.1 关键发现：老系统的课程模型完整存在，但草稿表缺表

tj-course 里已有**草稿 → 上架**双向模型（`CourseDraftService` / `CourseCatalogueDraftService` /
`CourseTeacherDraftService` / `CourseCataSubjectDraftService` + `copyToShelf`），PO 也齐全。
但 **tj_course 库里没有 5 张草稿表**（`course_draft` / `course_catalogue_draft` /
`course_content_draft` / `course_teacher_draft` / `course_cata_subject_draft`）——
所以老建课流程的「草稿阶段」在本环境从未跑通。本轮补齐建表。

### 15.2 实现方式：只加适配层，不重写业务

| 契约 §13 | 转调的老服务 |
|---|---|
| `GET /cs/teacher/course-draft` | `courseDraftService.getCourseBaseInfo(id,false)` + `catalogueDraftService.queryCourseCatalogues(id,false,true)` + `courseTeacherDraftService.queryTeacherOfCourse(id,false)` + `CourseDraft` 读 status/step |
| `PUT course-draft/basic` | `courseDraftService.save(CourseBaseInfoSaveDTO)`（返回 id） |
| `PUT course-draft/catalog` | `catalogueDraftService.save(courseId, List<CataSaveDTO>, step=2)`；**返回含服务端真实 id 的目录** |
| `PUT sections/{sid}/video` | `catalogueDraftService.saveMediaInfo(courseId, [CourseMediaDTO])` |
| `PUT sections/{sid}/preview` | 同上（trailer 取反） |
| `PUT sections/{sid}/quiz` | `catalogueDraftService.saveSuject(courseId, [CataSubjectDTO])` |
| `POST/DELETE course-draft/teachers` | `courseTeacherDraftService.save(CourseTeacherSaveDTO)`（整体覆盖保存） |
| `POST course-draft/publish` | `checkBeforeUpShelf(id)` → `upShelf(id)` |

### 15.3 三条实现纪律（写进代码注释）

1. **不新造业务实现** —— 老接口一行不改，老管理端不受影响；教师端契约不随老模型漂移。
2. **id 归属校验** —— 前端传来的章/小节 id 只有确实属于本课程（`course_id` 匹配）才按更新处理，否则一律当新增。防止前端本地占位 id 撞进主键。
3. **checks 从明细推导** —— 不存汇总表，读一次草稿现算（与考试模块同一条纪律）。

### 15.4 ⚠️ 联调时必须改的三处前端（下一轮做）

| # | 事项 | 原因 |
|---|---|---|
| 1 | `api/teacher/course.js` 恢复真实分支 | 目前 9 个函数全部是 `mock.xxx() /* TODO(backend §13) */` —— 后端就绪后要换回 `request(...)` |
| 2 | **目录保存后采纳服务端 id** | `saveCatalog` 现在返回保存后的完整目录；向导必须用它替换本地树，否则后续 video/preview/quiz 按 id 寻址会失配 |
| 3 | 分类与讲师要选**真实数据** | 分类：向导的「分类」目前是写死的字符串，应改为从 `/cs/categorys/all` 拉三级分类选择（真实课程用 `third_cate_id`）；讲师：`course_teacher` 存的是 `teacher_id`，而向导目前只录姓名，需要一个教师列表来源（`UserClient` 只支持按 id 查） |

### 15.5 编译状态

`-pl tj-course -am compile` **EXIT=0**。容器（course-service）尚未重建 —— 等确认。

---

## 十六、建课后端实测（course-service 已重建 · 直连 8086 + user-info 头）

### 16.1 实测结果

| 契约 §13 接口 | 结果 |
|---|---|
| `GET /cs/teacher/course-draft`（无 courseId） | ✅ 新建底稿：空基本信息 + 默认讲师=当前登录老师 + checks 全为待办 |
| `GET /cs/teacher/course-draft?courseId=` | ✅ 回读齐全：名称 / 分类全名（后端开发/Java/微服务）/ step / status / 章节目录 / 讲师（真实姓名） |
| `PUT course-draft/basic` | ✅ 返回课程 id；老 save 的购买时间窗由 adapter 用「当前时间 + 有效期」推导 |
| `PUT course-draft/catalog` | ✅ 2 章 3 节，**父子关系正确**，返回雪花真实 id；checks 变成「课程目录已建立（2 章 3 节）」 |
| `POST / DELETE course-draft/teachers` | ✅ 增删均落库；讲师名由老服务回填（「演示讲师」） |
| `PUT sections/{sid}/video` | ❌ 老 `saveMediaInfo` 校验「部分章节未选择视频」——**要求小节带媒资 id**，媒资来自 media-service |
| `PUT sections/{sid}/quiz` | ❌ 「媒资当前不能保存」——老流程要求 `draft.step >= MEDIA(3)`，而视频步无法完成 |
| `POST course-draft/publish` | ❌ 老 `upShelf` 内部校验「小节《1.1 环境搭建》未上传媒资」 |

### 16.2 视频/配题/上架被同一个前置条件挡住（结论要看清）

三处失败是**同一个根因**：老平台的「小节媒资」依赖 **media-service（本项目已停用）+ 腾讯云 VOD 凭证**（契约 §13.2 早已标注「后端待补」）。老 `upShelf` 的口径是「每节必须有媒资」，与我们契约里「视频 / 配题可上架后补」冲突。

已在适配层把 publish 的老 `checkBeforeUpShelf` 去掉（改用契约口径），但 `upShelf` 内部仍会校验媒资 —— 说明**要打通必须二选一**（见 16.3）。

### 16.3 待决策：两条路

| 方案 | 做法 | 代价 |
|---|---|---|
| **A · 简化上架（推荐）** | 在**新增的适配层**里实现「草稿→正式表」的数据搬运（course_draft→course、course_catalogue_draft→course_catalogue、course_teacher_draft→course_teacher、course_content_draft→course_content，置 status=2），不走老 upShelf | 我们自己维护这段复制逻辑；但完全在新代码里，老接口零改动。未配视频的小节对学生隐藏（老设计本来就有的规则） |
| B · 启用 media-service | 恢复被停用的 media-service，按老流程登记媒资 | 最正统，但仍需腾讯云 VOD 凭证才能真上传视频；且要动运行环境重启服务 |

### 16.4 已落地的工程细节（供后续查阅）

- **主键策略**：MP 全局 `id-type: auto`（数据库自增）→ 5 张草稿表必须 AUTO_INCREMENT（首版漏了，报 "Field 'id' doesn't have a default value"）。SQL 里已加自增修正段（幂等）。
- **目录保存不走老 `save()`**：老逻辑用「入参 id 或 IdWorker 生成值」当小节的 `parent_catalogue_id`，但插入时主键被数据库改号 → 父指针永远挂不上（这是老流程在 auto 策略下的固有缺陷）。适配层改为 **显式 id（雪花）的 upsert**：新增用 `INSERT ... (id, ...)` 写入自己生成的 id，已有行按 id 精确更新（视频/试看/配题不会被冲掉），并增量删除已移除的行。
- **step 维护**：目录保存后置 2；讲师保存后置 5（老流程用 step 判定编辑进度）。

---

## 十七、方案 A 落地 · 建课 9 接口全部打通（含视频 / 配题 / 上架）

> 决策：用户选 **A · 简化上架**（在新增适配层里完成草稿→正式表的数据搬运，不走老的 `upShelf`）。
> 老接口一行未改，管理端的严格流程照旧；教师端走契约口径（视频 / 配题可上架后补）。

### 17.1 实现方式（都在新增代码里）

| 环节 | 做法 | 为什么 |
|---|---|---|
| 视频登记 / 试看 | 直写 `course_catalogue_draft` 的 `video_name` / `media_duration` / `trailer` | 老的 `saveMediaInfo` 要求小节携带 media-service 的媒资 id，而 media-service 已停用、真上传依赖腾讯云 VOD。向导登记的是「视频名 + 时长 + 试看」这些**真实已知**的信息 |
| 小节配题 | 直写 `course_cata_subject_draft`（先清该节旧引用再写新的，题目本体在 tj_exam 不动） | 老 `saveSuject` 要求 `step >= MEDIA`，而视频步无法完成 |
| 上架 | `TeacherCourseShelfMapper` 用 `INSERT ... SELECT ... ON DUPLICATE KEY UPDATE` 搬 4 张表（course / course_content / course_catalogue / course_teacher），并锁定草稿目录 + 置 status=2 | 老 `upShelf` 内部硬校验「每节必须有媒资」 |
| **编辑已有上架课程** | 读草稿时若「草稿空 + 正式表有数据」→ 自动把正式表播种成草稿（课程信息 / 内容 / 目录 / 讲师，全部 can_update=1） | 老师点「编辑」打开的是已上架的课，老流程靠前端先调复制；现在对前端透明，打开即见现有目录 |
| 目录树构建 | 由**自己的草稿行**建树（不再调老 `queryCourseCatalogues`） | 老查询在 status=2 时按 `can_update=0` 的锁定行算最大序号，数据稍有出入就 `Optional.get()` 抛空（踩过两次） |

### 17.2 实测（course-service 重建后，直连 8086 + user-info 头）

| 场景 | 结果 |
|---|---|
| 新建 → 基本信息 → 目录 → 视频登记 ×2 → 试看 → 配题 → 讲师 → **上架** | ✅ 全链路通过；正式表 course(id=2) status=2、section_num=3、目录含视频名与时长、配题 2 行 |
| 上架后回读草稿 | ✅ 视频「2 / 3 节」、试看=True、配题「2 题」、checks 全部按契约口径（视频/配题只警告） |
| **重复上架** | ✅ 幂等（之前崩在这条） |
| **编辑已有上架课程 1001** | ✅ 自动播种出**真实目录**：3 章 9 节（第一章 微服务架构认知 → 1.1/1.2/1.3…）+ 讲师；改名/改完再上架 ✅ |

### 17.3 已知边界（都是「如实状态」，非假数据）

1. **视频只有名字与时长，没有可播放的媒资文件** —— 等 media-service + 腾讯云 VOD 接入后由回调补 `media_id`/`video_id`。学生端对「未配媒资的小节隐藏」是老设计本来就有的规则。
2. **讲师按 id 关联**（`course_teacher.teacher_id`），而向导目前只录姓名 → 需要一个教师列表来源（`UserClient` 只支持按 id 查）。
3. **分类要选真实三级分类**（`/cs/categorys/all`），向导现在是写死的字符串。

### 17.4 下一步（前端切换，契约 §15.4 的延续）

`api/teacher/course.js` 的 9 个函数恢复真实 `request` 分支 + 目录保存采纳服务端返回的 id + 分类/讲师改选真实数据 —— 做完后浏览器里就能真正建课入库。

---

## 十八、前端切换 · 建课向导接真实后端（P12）

> 目标：把 `api/teacher/course.js` 的 mock 兜底换成真实请求 —— 做完之后在浏览器里
> 「选真实分类 → 建目录 → 传视频 → 配题 → 选讲师 → 上架」全流程真实落库。

### 18.1 新增两个「真实来源」接口

| 方法 | 路径 | 用途 | 说明 |
|---|---|---|---|
| GET | `/us/teachers/simple` | 建课向导「选择讲师」 | 教师角色（type=3）且账号正常的用户，返回 `{id,name,job,intro}`。**不走 Feign 聚合**（老的 `/us/teachers/page` 要跨服务统计课程数，本地方案里直接 500） |
| GET | `/cs/teacher/my-courses` | 「我的课程」列表 | 我讲的课（`course_teacher`）+ 我建的课（`course_draft.creater`），合并去重。返回 `{id,name,coverUrl,price,status,chapterNum,sectionNum,editing}` |

`editing=true` = 已上架但草稿表还有未上架改动（界面显示「有未上架改动」）。

### 18.2 前端三个关键改动

1. **9 个函数全部恢复真实分支**（`VITE_TEACHER_MOCK=0` 生效），并在 `api/teacher/course.js` 里做一层**适配**：
   页面拿到的形状与 mock 完全一致，所以页面在 mock ↔ 真实之间不需要分支。
2. **目录保存采纳服务端 id**：`saveCatalog` 返回含真实 id 与 c_index 顺序的目录，
   页面 `adoptServerIds()` 把 id 写回本地（只改 id、保留本地对象，上传进度等临时状态不被覆盖）。
   不做这一步，第 ③④ 步登记视频 / 配题会拿 `Date.now()` 占位 id 去寻址 → 必然失配。
3. **分类与讲师改成真实数据**：分类来自 `/cs/categorys/all`（三级联动，选到叶子），
   讲师来自 `/us/teachers/simple`（后端按 `teacher_id` 关联，手输姓名存不进库，已移除这条路）。

### 18.3 字段 / 单位适配（都在接口层抹平）

| 后端 | 页面 | 处理 |
|---|---|---|
| `basic.price`（**分**） | 价格（元） | 读 `/100`、写 `*100`。库里存分 |
| `basic.introduce` / `coverUrl` | `intro` / `cover{name,dataUrl}` | 字段改名 + 封面包装成对象 |
| `section.quiz{count}` | `quiz{count,totalScore,dist}` | 只回题量；总分 / 分布显示「—」（分值归试卷、题型分布要跨服务，不编造） |
| `section.duration` 缺失 | `duration: null` | 后端 Jackson 配了 NON_NULL，空值字段整个不出现，前端必须容忍 |
| 讲师 `{id}`（增删返回） | 展示姓名 / 岗位 | 用刚选中的那位（同源真实数据）补，不靠后端编造 |

### 18.4 ⚠️ 两个必须知道的接口口径

1. **响应是裸 JSON，不包 `R{code,data}`**：`GET /cs/teacher/course-draft` 直接返回 VO，
   `PUT .../basic` 直接返回 `"1005"` 这样的 id 字符串。
2. **错误是 HTTP 400 + `text/plain` 的中文原因**（如「上架前校验未通过：基本信息 课程目录」）。
   默认 axios 只会给「Request failed with status code 400」——所以接口层加了统一的 `call()`：
   成功直通、失败把**服务端原话**抛出来给页面 toast。忽略这条，上架失败时用户看到的是一句看不懂的话。

### 18.5 分类树的一个数据坑（已处理）

本地库的分类树存在**层级异常**分支：`101 IT互联网 → 1 后端开发 → 2 Java → 3 微服务`，
其中 `3 微服务` 的 `level` 字段仍标 3，实际在第 4 层 —— 而它就是现有课程在用的 `third_cate_id`。
所以第三个下拉按**叶子节点**取（深层分支拍平成「Java / 微服务」相对路径），
不按 `level` 字段取，否则这些正在用的分类根本选不到。

### 18.6 实测（course-service / user-service 重建后，直连 + 回读）

| 场景 | 结果 |
|---|---|
| ① 新建基本信息（价格 9900 分 = 99 元、三级分类=叶子 213） | ✅ 返回新课程 id |
| ② 目录保存（占位 id = 毫秒时间戳）→ 服务端发真实雪花 id | ✅ 2 章 3 节，id 全部换新 |
| ③ 视频登记（含删除）/ ④ 试看 / ⑤ 配题（2 题引用） | ✅ 时长 00:13 回填、trailer=true、`course_cata_subject_draft` 2 行 |
| ⑥ 讲师（真实 user id=2） | ✅ 只回 id+isShow，回读带真实姓名 |
| ⑦ 上架 | ✅ 正式表 status=2、third_cate_id=213、price=9900、目录 5 行、讲师关联 |
| ⑧ 我的课程 | ✅ 新建的课出现在列表（status=published、2 章 3 节） |
| ⑨ 前端模块编译 | ✅ 改动文件全部通过 vite dev 的按需转换（无 500 / 无 import-analysis 报错） |

### 18.7 仍然如实标注的边界

1. **视频只有名字与时长**，没有可播放媒资（等腾讯云 VOD，契约 §13.2）。
2. **封面**：库里 `cover_url` 是 `varchar(500)`，dataURL 存不下 → 页面本地预览是真、
   正式上传待对象存储接入；选完提示已经如实写明，不再谎报「已保存」。
3. **「工作概览」(T1) 仍是 mock 兜底**（契约 §12.5 的聚合接口未实现），
   只有「我的课程」接了真实数据。
4. **定时上架置灰**：后端没有定时公开字段，显式禁用并写明原因，不让它假装成功。
5. 演示库里 **49 门种子课程全部挂在教师 2（演示讲师）名下**，所以「我的课程」会显示 51 张卡 —— 这是种子数据的真实归属，不是统计错误。

---

## 十九、讲师概念纠正：「自己填账号」而不是「选一份名册」

> 用户指出（原文大意）：**知序学堂是面向所有有能力教课的老师的开放平台，不是学校内部系统**，
> 所以「添加讲师」不应该是一个像已经知道预期教师是谁的下拉框，而应该由老师自己填。

这个判断是对的，而且错的不只是控件 —— 是概念。原先的实现里有两处把「学校内部系统」的假设写进了代码：

1. 前端拉一份「可授课教师名录」做下拉 → 暗示平台预先知道会有哪些人来教课；
2. 界面出现「助教 / 已通过院系认证 / 计算机学院」这类**根本不存在的字段**（`course_teacher` 表只有
   `course_id / teacher_id / is_show / c_index` 四列，没有角色、没有院系）。

### 19.1 改后的模型

| 概念 | 做法 | 为什么 |
|---|---|---|
| **主讲** | = **课程归属人**（当前在编辑这门课的老师）。`getDraft` 保证它在讲师列表里、排第一位；`removeTeacher` 拒绝移除它 | 开放平台上「谁建的课谁署名」，不需要谁来分配 |
| **协作讲师** | 老师**自己填对方的平台账号**（用户名 / 手机号）→ `GET /us/users/lookup` 解析成真实用户 id → 关联 | 平台没有名册，但 `course_teacher.teacher_id` 必须是真 id —— 手输姓名存不进库 |
| ~~教师名录接口~~ | **删除** `GET /us/teachers/simple`（连带 `TeacherSimpleVO` / `querySimpleTeachers`） | 它就是把错误概念固化下来的那层，留着下次还会被拿来当"选人"入口 |

### 19.2 新增 `GET /us/users/lookup?keyword=`

按**用户名或手机号精确匹配**，只放行教师身份账号：

| 情况 | 返回 |
|---|---|
| 命中且是教师 | `{id, name, type, username, job, intro}`（姓名 / 岗位 / 简介为空就留空，前端显示 `—`） |
| 账号不存在 | 400「平台里没有这个账号：xxx」 |
| 账号存在但不是教师 | 400「「xxx」不是教师身份的账号，不能作为讲师」 |
| 账号被禁用 | 400「「xxx」的账号已被禁用」 |
| keyword 为空 | 400「请输入对方在平台上的账号（用户名或手机号）」 |

三种情况**分开报**，不合并成「不存在」—— 否则老师不知道自己到底填错了什么。

### 19.3 `ownerId`：让前端知道「哪条是我」

`TeacherCourseDraftVO` 增加 `ownerId`。前端据此把自己的那条标记为「主讲 · 你」并**去掉移除按钮**，
后端 `removeTeacher` 同样拒绝（两道防线，理由：不能把课程的作者从作者列表里删掉）。

⚠️ `getDraft` 里会自动把归属人**落库**一次（缺失时才写，幂等）——只在内存里补的话，
界面显示的主讲在库中并不存在，上架后 `course_teacher` 会是空的（课程一个讲师都没有）。

### 19.4 实测

| 场景 | 结果 |
|---|---|
| `GET /us/teachers/simple` | ✅ 已下线（404） |
| `lookup?keyword=teacher` / `=13900000000`（手机号） | ✅ 都命中 `{id:2, name:演示讲师}` |
| `lookup?keyword=demo`（学员身份） | ✅ 400「不是教师身份的账号」 |
| `lookup?keyword=nobody_x` | ✅ 400「平台里没有这个账号」 |
| `lookup?keyword=`（空） | ✅ 400 |
| 新建底稿 | ✅ `ownerId=2`，讲师列表自带本人（主讲） |
| 历史种子课 1001（草稿讲师表为空） | ✅ 自动落一条主讲（`c_index` 0/1），重复读**不重复插** |
| `DELETE .../teachers/2`（想移除主讲） | ✅ 400「课程归属人（主讲）不能移除」 |
| 重复 `POST teachers` 同一账号 | ✅ 幂等，列表仍 1 人 |

### 19.5 第 2 个教师账号已建，「添加协作者」全链路实测通过

原先只能验「查不到 / 身份不符 / 空账号」三个失败分支 —— 因为演示库里只有 1 个教师账号。
2026-09-15 建了 `teacher2`（`user.id=10`，`type=3`，`user_detail.name=演示讲师二号`，`job=讲师`，
手机号 `13900000001`，密码 `123456`），链路补齐：

| 场景 | 结果 |
|---|---|
| `login teacher2/123456`（学生通道探针） | ✅ 口令通过（返回「非学生端用户」）；错密码返回「用户登录信息错误」 |
| `lookup?keyword=teacher2` / `=13900000001` | ✅ 命中 `{id:10, name:演示讲师二号, job:讲师}` |
| `POST teachers {courseId:1005, teacherId:10}` | ✅ 草稿列表 `[2(owner, c_index=0), 10(c_index=1)]` |
| **再次上架 1005** | ✅ 正式表 `course_teacher` 2 行 —— 学员端会看到两位讲师 |

⚠️ 演示数据说明：`teacher2` **刻意保留**在课程 1005 的讲师里当样本，方便在浏览器里测「移除」
再重新添加；不要以为是自动关联的。
