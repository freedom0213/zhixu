# P24 · 教师端收口：学生分析 + 个人资料

> 2026-09-17 · 目标：把教师端最后两个占位页（`/teacher/students`、`/teacher/profile`）做成真页面 ——
> 做完后教师端 10 项导航**全部落地、零占位**。

## 1 · 摸底结论（决定实现方式）

| 页面 | 数据源 | 现状 |
|---|---|---|
| 个人资料 | `user` + `user_detail` | **读接口现成**（`GET /us/users/me` → `UserDetailVO`）；**写接口缺失**：「更新当前用户」的 `PUT /us/users` 只吃 `UserFormDTO`（仅 oldPassword/password），`updateUserWithPassword` 的资料字段拿不到 → 改不了资料 |
| 学生分析 | `learning_lesson` | 有 **9 行真数据**（报名时间 / 已学小节 / 最近学习时间）；但 `learning_lesson` **没有学习时长列**，`learning_record`（逐小节）当时 0 行 → 时长类维度做不了 |

课程归属判定：`course` 表**没有 owner 列**，归属写在 `course_teacher`；`CourseClient.getCourseInfoById(id, false, true)`
会回填 `teacherIds`（`courseTeacherService.getTeacherIdOfCourse`）→ 用它做服务层归属校验。

## 2 · 后端

### 2.1 讲师资料（tj-user）
- 新增 `PUT /us/teachers/profile`（`TeacherController`）：**复用通用 `userService.updateUser(UserDTO)`**，
  `userDTO.setId(UserContext.getUser())` —— 传任何别的 id 都会被覆盖成登录用户，改不到别人。
- 🔴 **`UserDetailVO` 原本漏了 `job`**（表有、DTO 有、VO 没有）→ 个人资料页的「职业/头衔」读不出来。已补。

### 2.2 学生分析（tj-learning）
- 新增 `GET /ls/lessons/course-students?courseId=` → `List<CourseStudentVO>`（新 VO）
- 实现（`LearningLessonServiceImpl.queryCourseStudents`）：
  1. **归属校验**：`courseClient.getCourseInfoById(courseId, false, true).getTeacherIds()` 必须包含当前用户，
     否则 `ForbiddenException("只能查看自己课程的学生")` —— 与 P19 的口径一致：**屏障放服务层，不靠前端**；
  2. 查 `learning_lesson WHERE course_id = ?`（按报名时间倒序）；
  3. `userClient.queryUserByIds()` **批量**补昵称/账号（不在循环里单查）。
- VO 只放服务端真拿得到的字段；**学习时长不提供** → 前端该处显示「—」。

### 2.3 顺带修正
`UserConstants.TEACHER_ROLE_NAME`：`"教师"` → **`"讲师"`** —— 它是资料页上的角色徽标，用户 9-15 已定「称呼一律讲师」。

## 3 · 前端（tj-front）

| 文件 | 说明 |
|---|---|
| `api/teacher/profile.js` | 读 `GET /us/users/me`、写 `PUT /us/teachers/profile`（统一 `call()` 把服务端中文原话抛给页面） |
| `api/teacher/students.js` | 复用 `listMyCourses()` 取课程下拉；`listCourseStudents(courseId)`；状态码→文案映射 |
| `pages/teacher/profile.vue` | 左「学员看到的样子」身份卡 + 右可编辑表单；**手机号刻意不做成表单项**（`username` 与 `cell_phone` 同源，改手机会连带改登录名） |
| `pages/teacher/students.vue` | 选课 → 三概览卡（人数 / 平均进度 / 沉默学生）+ 学生明细表；统计**全部从明细推**（行数、各行进度均值、逐行判沉默），不允许各算一套 |
| `router/modules/teacher.js` | 两处 `Placeholder` → 真页面；`Placeholder.vue` 现无引用（保留备用，未删） |

**口径**：进度 = 已学小节 ÷ 全课小节数（`sectionNum` 来自 my-courses，取不到就显示「—」）；
沉默 = 报名后从未学习，或最近学习距今 > 7 天。

## 4 · 实测

| 场景 | 结果 |
|---|---|
| `GET /us/users/me`（user-info: 2） | ✅ `roleName='讲师'`、`job='Java 架构师 · 平台讲师'`、`city='深圳市'`、`gender=null` |
| `PUT /us/teachers/profile` | ✅ HTTP 200，读回一致 |
| `GET /ls/lessons/course-students?courseId=1002`（teacher） | ✅ 2 名学生（含昵称/账号/报名时间/已学小节/状态） |
| 同一接口用 **demo（学生）** 调用 | ✅ 403「只能查看自己课程的学生」 |
| 同一接口用 **teacher2(10)** 查 teacher 的课 | ✅ 403 |
| 无学生的课（1004） | ✅ 返回 `[]`（前端走空态） |
| 经网关 `/ls/lessons/course-students`（demo token） | ✅ 业务 403（链路通，非「服务不存在」） |
| 前端 SFC 校验 / vite 转换 | ✅ 0 错误 / 200 |

## 5 · 过程中的失误与修正（如实记录）

1. **误覆盖了演示讲师的名字**：补资料种子数据时用了 `ON DUPLICATE KEY UPDATE`，而 `user_detail` 里
   `id=2/10` **本来就有行**（`name='演示讲师'` / `'演示讲师二号'`），结果把这两个名字改成了我编的名字。
   已立即恢复原名，并改为**只补 NULL 字段**（`COALESCE`）。教训：**写种子数据前先 SELECT，别默认表是空的**。
2. **代填了 gender**：同一批操作里把 NULL 的性别填成了「男」—— 性别不该由我决定，已置回 `NULL`
   （页面显示「未选择」，讲师自己选）。
3. **测 SQL 时又漏 `--default-character-set=utf8mb4`**（本项目的老坑，记忆里明确写过）：中文显示成 `????`，
   一度怀疑数据坏了。已按规范复查确认数据完好。

## 6 · 遗留

- 「教师」→「讲师」**仍有约 49 处用户可见文案**未替换（后端常量已改 1 处，前端与其余服务待做）。
- 学生分析缺「学习时长」维度：需 `learning_lesson` 加时长列 + 播放端埋点（与热力图同一块前置工作）。
- `learning_record`（逐小节明细）目前 0 行 → 「学到第几节 / 逐节完成情况」暂无数据，等播放记录攒出来。
- `Placeholder.vue` 已无引用（未删，留作后续模块占位）。
