// =============================================================================
// 教师端路由（/teacher/*）
// -----------------------------------------------------------------------------
// 外壳 TeacherLayout（左侧栏 + 顶栏）。左侧导航 10 项已全部实装，不再需要占位页
//（原 Placeholder.vue 已删除，站内零占位）。
//
// 落地节奏（与 p11-teacher-portal-plan.md 的阶段划分一致）：
//   阶段 1 题库闭环   questions（已实装）
//   阶段 2 组卷与发布 exams/new、exams/paper…
//   阶段 3 考务        exams、exams/stats、review
//   阶段 4 建课与周边  courses、courses/edit、dashboard、students、messages、profile
//
// ⚠️ router/index.js 里有一条「非 /student 一律跳学习首页」的兜底规则 ——
// 它是 `/:pathMatch(.*)*` 的 catch-all，优先级低于本文件的显式路由，
// 所以本文件注册后 /teacher/* 不会被吃掉；但这条兜底的注释已过期（现在还有 /teacher），
// 已在 index.js 里更新说明。
// =============================================================================
import TeacherLayout from '@/pages/teacher/TeacherLayout.vue';

export default [
  {
    path: '/teacher',
    component: TeacherLayout,
    redirect: '/teacher/dashboard',
    name: 'teacher',
    meta: { title: '工作概览' },
    children: [
      // ---- 阶段 4：工作概览 / 我的课程（已实装）----
      {
        path: 'dashboard',
        name: 'teacherDashboard',
        component: () => import('@/pages/teacher/dashboard.vue'),
        meta: { title: '工作概览' },
      },

      {
        path: 'courses',
        name: 'teacherCourses',
        component: () => import('@/pages/teacher/courses.vue'),
        meta: { title: '我的课程' },
      },
      {
        // 建课五步向导（A1→A6）：每步单独保存，随时退出再回来
        path: 'courses/new',
        name: 'teacherCourseWizard',
        component: () => import('@/pages/teacher/courseWizard.vue'),
        meta: { title: '新建课程' },
      },

      // ---- 阶段 1：题库（列表 / 新建 / 导入均已实装）----
      {
        path: 'questions',
        name: 'teacherQuestions',
        component: () => import('@/pages/teacher/questions.vue'),
        meta: { title: '我的题库' },
      },
      {
        // 新建与编辑共用同一个表单（带 ?id= 即为编辑态）
        path: 'questions/new',
        name: 'teacherQuestionNew',
        component: () => import('@/pages/teacher/questionEdit.vue'),
        meta: { title: '新建题目' },
      },
      {
        path: 'questions/import',
        name: 'teacherQuestionImport',
        component: () => import('@/pages/teacher/questionImport.vue'),
        meta: { title: 'Excel 导入题目' },
      },

      // ---- 阶段 2/3：考试管理（列表 / 向导已实装；批改与统计走 :id 子页）----
      {
        path: 'exams',
        name: 'teacherExams',
        component: () => import('@/pages/teacher/exams.vue'),
        meta: { title: '考试管理' },
      },
      {
        // 新建与编辑共用同一个向导（带 ?id= 即为编辑态，可带 &step= 恢复到指定步）
        path: 'exams/new',
        name: 'teacherExamWizard',
        component: () => import('@/pages/teacher/examWizard.vue'),
        meta: { title: '新建考试' },
      },
      {
        // T4 批改：左学生列表 + 右答卷复核（全部读快照）
        path: 'exams/:id/marking',
        name: 'teacherExamMarking',
        component: () => import('@/pages/teacher/marking.vue'),
        meta: { title: '试卷批改' },
      },
      {
        // T8 统计：分数分布 / 逐题 / 逐知识点正确率（全部读快照）
        path: 'exams/:id/stats',
        name: 'teacherExamStats',
        component: () => import('@/pages/teacher/stats.vue'),
        meta: { title: '考试统计' },
      },
      {
        // 侧栏「试卷批改」= 待处理清单（跨考试的待复核份数，点一行进 /exams/:id/marking）
        path: 'review',
        name: 'teacherReview',
        component: () => import('@/pages/teacher/review.vue'),
        meta: { title: '试卷批改' },
      },

      // ---- 阶段 4：周边 ----
      {
        // 学生分析（P24）：选课 → 该课报名学生明细（learning_lesson，服务端校验课程归属）
        path: 'students',
        name: 'teacherStudents',
        component: () => import('@/pages/teacher/students.vue'),
        meta: { title: '学生分析' },
      },
      {
        // 师生对话（P22）：与学生端同一套接口与 WebSocket，两端互为收发
        path: 'messages',
        name: 'teacherMessages',
        component: () => import('@/pages/teacher/messages.vue'),
        meta: { title: '师生对话' },
      },
      {
        // 个人资料（P24）：读 /us/users/me，写 /us/teachers/profile（后端把 id 强制成登录用户）
        path: 'profile',
        name: 'teacherProfile',
        component: () => import('@/pages/teacher/profile.vue'),
        meta: { title: '个人资料' },
      },
    ],
  },
];
