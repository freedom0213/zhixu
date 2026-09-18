// =============================================================================
// 教师端（教师工作台）左侧导航配置
// -----------------------------------------------------------------------------
// 与学员端同源：同样的内联 SVG + currentColor 方案，颜色交给 CSS，
// 选中态不需要维护两套图标。
//
// 与设计稿对应关系（画板 T1–T8 / Q2）：
//   工作概览 T1 · 我的课程 T2 · 题库 Q2 · 考试管理 T3 · 试卷批改 T4
//   学生分析 T5 · 师生对话 T6 · 个人资料 T7
//
// 注意：「题库」是跨课程复用的一级导航（不是某门课的子页）——
// 题目可以跨课程、跨试卷复用，挂在课程下会让「这题能不能给另一门课用」说不清。
// =============================================================================

const stroke = (d, extra = '') =>
  `<svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">${d
    .split('|')
    .map(
      (p) =>
        `<path d="${p}" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>`
    )
    .join('')}${extra}</svg>`;

export default [
  {
    key: 'dashboard',
    label: '工作概览',
    path: '/teacher/dashboard',
    icon: `<svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <rect x="3.2" y="3.2" width="5.6" height="5.6" rx="1.6" stroke="currentColor" stroke-width="1.6"/>
      <rect x="11.2" y="3.2" width="5.6" height="5.6" rx="1.6" stroke="currentColor" stroke-width="1.6"/>
      <rect x="3.2" y="11.2" width="5.6" height="5.6" rx="1.6" stroke="currentColor" stroke-width="1.6"/>
      <rect x="11.2" y="11.2" width="5.6" height="5.6" rx="1.6" stroke="currentColor" stroke-width="1.6"/>
    </svg>`,
  },
  {
    key: 'courses',
    label: '我的课程',
    path: '/teacher/courses',
    icon: `<svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <rect x="3.6" y="3.8" width="12.8" height="12.4" rx="1.6" stroke="currentColor" stroke-width="1.6"/>
      <path d="M7.4 3.8v12.4" stroke="currentColor" stroke-width="1.6"/>
    </svg>`,
  },
  {
    key: 'questions',
    label: '题库',
    path: '/teacher/questions',
    icon: `<svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <rect x="3.4" y="4.2" width="13.2" height="3.4" rx="1.2" stroke="currentColor" stroke-width="1.6"/>
      <rect x="3.4" y="9.4" width="13.2" height="3.4" rx="1.2" stroke="currentColor" stroke-width="1.6"/>
      <rect x="3.4" y="14.6" width="8.4" height="2.2" rx="1.1" stroke="currentColor" stroke-width="1.6"/>
    </svg>`,
  },
  {
    key: 'exams',
    label: '考试管理',
    path: '/teacher/exams',
    icon: `<svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <path d="M7.6 3.6H6A1.5 1.5 0 0 0 4.5 5.1v9.8A1.5 1.5 0 0 0 6 16.4h8a1.5 1.5 0 0 0 1.5-1.5V5.1A1.5 1.5 0 0 0 14 3.6h-1.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
      <rect x="7.5" y="2.2" width="5" height="3" rx="1.2" stroke="currentColor" stroke-width="1.6"/>
      <path d="M7.9 11.4l1.6 1.6 2.9-3.1" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>`,
  },
  {
    key: 'review',
    label: '试卷批改',
    path: '/teacher/review',
    icon: stroke(
      'M5.6 2.8h7.1l3 3v11.4a1 1 0 0 1-1 1H5.6a1 1 0 0 1-1-1V3.8a1 1 0 0 1 1-1Z|M12.5 2.9v3.1h3.1|M7.4 12.9l4.4-4.4 1.1 1.1-4.4 4.4-1.6.5.5-1.6Z'
    ),
  },
  {
    key: 'students',
    label: '学生分析',
    path: '/teacher/students',
    icon: `<svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <path d="M3.5 16.6h13" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
      <rect x="4.6" y="9.4" width="2.9" height="4.8" rx="1" stroke="currentColor" stroke-width="1.6"/>
      <rect x="8.6" y="5.4" width="2.9" height="8.8" rx="1" stroke="currentColor" stroke-width="1.6"/>
      <rect x="12.6" y="11.4" width="2.9" height="2.8" rx="1" stroke="currentColor" stroke-width="1.6"/>
    </svg>`,
  },
  {
    key: 'messages',
    label: '师生对话',
    path: '/teacher/messages',
    icon: stroke(
      'M3.2 9.4c0-3.2 3-5.8 6.8-5.8s6.8 2.6 6.8 5.8-3 5.8-6.8 5.8c-.7 0-1.4-.1-2-.2l-3.5 2 .9-2.6c-1.3-1-2.2-2.7-2.2-4.6Z'
    ),
  },
  {
    key: 'profile',
    label: '个人资料',
    path: '/teacher/profile',
    icon: `<svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <circle cx="10" cy="6.8" r="3.4" stroke="currentColor" stroke-width="1.6"/>
      <path d="M3.9 16.6c0-2.8 2.7-4.7 6.1-4.7s6.1 1.9 6.1 4.7" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
    </svg>`,
  },
];
