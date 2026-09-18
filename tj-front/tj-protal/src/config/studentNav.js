// =============================================================================
// 学员端左侧导航配置
// -----------------------------------------------------------------------------
// 图标统一用内联 SVG（stroke="currentColor"），由 CSS 控制颜色，
// 这样「选中态变主色」不需要维护两套图标。
// 不用 iconfont：图标形状完全自控，且在任意缩放下都清晰。
// =============================================================================

const stroke = (d, extra = '') =>
  `<svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">${d
    .split('|')
    .map((p) => `<path d="${p}" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>`)
    .join('')}${extra}</svg>`;

export default [
  {
    key: 'dashboard',
    label: '学习首页',
    path: '/student/dashboard',
    icon: stroke('M3.2 8.6 10 3.5l6.8 5.1v7.4a1.5 1.5 0 0 1-1.5 1.5h-3.1v-4.4H7.8v4.4H4.7a1.5 1.5 0 0 1-1.5-1.5V8.6Z'),
  },
  {
    key: 'courses',
    label: '课程中心',
    path: '/student/courses',
    icon: `<svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <rect x="3.2" y="3.2" width="5.6" height="5.6" rx="1.6" stroke="currentColor" stroke-width="1.6"/>
      <rect x="11.2" y="3.2" width="5.6" height="5.6" rx="1.6" stroke="currentColor" stroke-width="1.6"/>
      <rect x="3.2" y="11.2" width="5.6" height="5.6" rx="1.6" stroke="currentColor" stroke-width="1.6"/>
      <rect x="11.2" y="11.2" width="5.6" height="5.6" rx="1.6" stroke="currentColor" stroke-width="1.6"/>
    </svg>`,
  },
  {
    key: 'records',
    label: '学习记录',
    path: '/student/records',
    icon: `<svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <path d="M3.5 16.6h13" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
      <rect x="4.6" y="9.4" width="2.9" height="4.8" rx="1" stroke="currentColor" stroke-width="1.6"/>
      <rect x="8.6" y="5.4" width="2.9" height="8.8" rx="1" stroke="currentColor" stroke-width="1.6"/>
      <rect x="12.6" y="11.4" width="2.9" height="2.8" rx="1" stroke="currentColor" stroke-width="1.6"/>
    </svg>`,
  },
  {
    key: 'exams',
    label: '在线考试',
    path: '/student/exams',
    icon: `<svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <path d="M7.6 3.6H6A1.5 1.5 0 0 0 4.5 5.1v9.8A1.5 1.5 0 0 0 6 16.4h8a1.5 1.5 0 0 0 1.5-1.5V5.1A1.5 1.5 0 0 0 14 3.6h-1.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
      <rect x="7.5" y="2.2" width="5" height="3" rx="1.2" stroke="currentColor" stroke-width="1.6"/>
      <path d="M7.9 11.4l1.6 1.6 2.9-3.1" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>`,
  },
  {
    key: 'notes',
    label: '学习笔记',
    path: '/student/notes',
    icon: stroke(
      'M5.6 2.8h7.1l3 3v11.4a1 1 0 0 1-1 1H5.6a1 1 0 0 1-1-1V3.8a1 1 0 0 1 1-1Z|M12.5 2.9v3.1h3.1M8 10.3h4.4M8 13.3h3'
    ),
  },
  {
    key: 'points',
    label: '我的积分',
    path: '/student/points',
    icon: stroke(
      'M10 2.6l2.2 4.4 4.9.7-3.6 3.5.9 4.9L10 13.9l-4.4 2.2.9-4.9-3.6-3.5 4.9-.7L10 2.6Z'
    ),
  },
  {
    key: 'notices',
    label: '公告与新闻',
    path: '/student/notices',
    icon: stroke(
      'M10 3.1a4.6 4.6 0 0 0-4.6 4.6c0 3.4-1.1 4.6-1.1 4.6h11.4s-1.1-1.2-1.1-4.6A4.6 4.6 0 0 0 10 3.1Z|M8.4 15.3a1.8 1.8 0 0 0 3.2 0'
    ),
  },
  {
    key: 'messages',
    label: '师生对话',
    path: '/student/messages',
    icon: stroke(
      'M3.2 9.4c0-3.2 3-5.8 6.8-5.8s6.8 2.6 6.8 5.8-3 5.8-6.8 5.8c-.7 0-1.4-.1-2-.2l-3.5 2 .9-2.6c-1.3-1-2.2-2.7-2.2-4.6Z'
    ),
  },
  {
    key: 'profile',
    label: '个人中心',
    // 学员端内一律走 /student/* 前缀，不再跳回官网的 /personal/main/*
    path: '/student/profile',
    icon: `<svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <circle cx="10" cy="6.8" r="3.4" stroke="currentColor" stroke-width="1.6"/>
      <path d="M3.9 16.6c0-2.8 2.7-4.7 6.1-4.7s6.1 1.9 6.1 4.7" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
    </svg>`,
  },
  {
    key: 'ai',
    label: 'AI 助手',
    // 学员端壳内的 AI 助手页（/student/ai），保持学员端单一前缀
    path: '/student/ai',
    icon: stroke('M8.6 2.8 10 7.1l4.3 1.4L10 9.9l-1.4 4.3L7.2 9.9 2.9 8.5l4.3-1.4L8.6 2.8Z|M15.1 12.6l.7 2.1 2.1.7-2.1.7-.7 2.1-.7-2.1-2.1-.7 2.1-.7.7-2.1Z'),
  },
];
