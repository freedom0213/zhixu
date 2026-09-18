// =============================================================================
// 学员端路由（/student/*）
// -----------------------------------------------------------------------------
// 外壳 ShellLayout（左侧栏 + 顶栏）。左侧导航已全部实装，不再需要占位页
//（原 ComingSoon.vue 已删除，站内零占位）。
// meta.title 驱动顶栏标题；meta.fullHeight 用于类 App 双栏页（壳内自滚动）。
//
// 前缀约定（用户明确要求）：**学员端内所有地址一律 /student/***
//   个人中心 → /student/profile（壳内实现，不再跳 /personal/main/mySet）
//   AI 助手 → /student/ai（壳内实现，不再跳 /main/ai）
// 官网原有 /personal/*、/main/* 路由保持不动，供官网侧继续使用。
// =============================================================================
import ShellLayout from '@/pages/student/ShellLayout.vue';

export default [
  {
    path: '/student',
    component: ShellLayout,
    redirect: '/student/dashboard',
    name: 'student',
    meta: { title: '学习首页' },
    children: [
      {
        path: 'dashboard',
        name: 'studentDashboard',
        component: () => import('@/pages/student/dashboard.vue'),
        meta: { title: '学习首页' },
      },
      {
        path: 'courses',
        name: 'studentCourses',
        component: () => import('@/pages/student/courses.vue'),
        meta: { title: '课程中心' },
      },
      {
        path: 'courses/detail',
        name: 'studentCourseDetail',
        component: () => import('@/pages/student/courseDetail.vue'),
        meta: { title: '课程详情' },
      },
      {
        path: 'learn',
        name: 'studentLearn',
        component: () => import('@/pages/student/learn.vue'),
        meta: { title: '课程学习' },
      },
      {
        path: 'records',
        name: 'studentRecords',
        component: () => import('@/pages/student/records.vue'),
        meta: { title: '学习记录' },
      },
      {
        path: 'exams',
        name: 'studentExams',
        component: () => import('@/pages/student/exams.vue'),
        meta: { title: '在线考试' },
      },
      {
        path: 'exams/answer',
        name: 'studentExamAnswer',
        component: () => import('@/pages/student/examAnswer.vue'),
        meta: { title: '在线答题' },
      },
      {
        path: 'exams/review',
        name: 'studentExamReview',
        component: () => import('@/pages/student/examReview.vue'),
        meta: { title: '答题详解' },
      },
      {
        path: 'notes',
        name: 'studentNotes',
        component: () => import('@/pages/student/notes.vue'),
        meta: { title: '学习笔记' },
      },
      {
        path: 'paySuccess',
        name: 'studentPaySuccess',
        component: () => import('@/pages/student/paySuccess.vue'),
        meta: { title: '支付成功' },
      },
      {
        path: 'payment',
        name: 'studentPayment',
        component: () => import('@/pages/student/payment.vue'),
        meta: { title: '订单支付' },
      },
      {
        path: 'coupons',
        name: 'studentCoupons',
        component: () => import('@/pages/student/coupons.vue'),
        meta: { title: '优惠券' },
      },
      {
        path: 'settlement',
        name: 'studentSettlement',
        component: () => import('@/pages/student/settlement.vue'),
        meta: { title: '确认订单' },
      },
      {
        path: 'points',
        name: 'studentPoints',
        component: () => import('@/pages/student/points.vue'),
        meta: { title: '我的积分' },
      },
      {
        path: 'carts',
        name: 'studentCarts',
        component: () => import('@/pages/student/carts.vue'),
        meta: { title: '我的购物车' },
      },
      {
        path: 'notices',
        name: 'studentNotices',
        component: () => import('@/pages/student/notices.vue'),
        meta: { title: '公告与新闻' },
      },
      {
        path: 'notices/detail',
        name: 'studentNoticeDetail',
        component: () => import('@/pages/student/noticeDetail.vue'),
        meta: { title: '公告详情' },
      },
      {
        path: 'messages',
        name: 'studentMessages',
        component: () => import('@/pages/student/messages.vue'),
        meta: { title: '师生对话', fullHeight: true },
      },
      {
        path: 'profile',
        name: 'studentProfile',
        component: () => import('@/pages/student/profile.vue'),
        meta: { title: '个人中心' },
      },
      {
        path: 'ai',
        name: 'studentAi',
        component: () => import('@/pages/student/ai.vue'),
        meta: { title: 'AI 助手', fullHeight: true },
      },
    ],
  },
];
