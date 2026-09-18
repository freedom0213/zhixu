// 路由配置
// =============================================================================
// 学员端（/student/*）已取代老站，教师端（/teacher/*）为新增的第二端，故路由表为四部分：
//   1) /login            —— 登录页（两端共用的入口页）
//   2) /student/*        —— 学员端全部页面
//   3) /teacher/*        —— 教师端全部页面
//   4) 老站地址重定向     —— modules/legacy.js（老地址不再渲染老页面，统一跳学员端）
// 其余任何非上述前缀的地址由兜底规则统一落到学习首页，老页面不再可达。
//
// 注意：兜底是 `/:pathMatch(.*)*` 的 catch-all，**优先级低于显式路由**，
// 因此后注册的 /teacher/* 不会被它吃掉，无需为教师端另写例外规则。
// =============================================================================
import { useRoute, createRouter, createWebHashHistory } from 'vue-router';

import { rememberStudentOrigin } from '@/config/loginRedirect';
import studentRouters from './modules/student';
import teacherRouters from './modules/teacher';
import legacyRouters from './modules/legacy';

// 关于单层路由，meta 中设置 { single: true } 即可为单层路由，{ hidden: true } 即可在侧边栏隐藏该路由

// 存放动态路由
export const asyncRouterList = [...studentRouters, ...teacherRouters, ...legacyRouters];

// 存放固定的路由
const defaultRouterList = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/pages/login/index.vue'),
  },
  {
    path: '/',
    // 根路径直接进学员端学习首页
    redirect: '/student/dashboard',
  },
  {
    // 兜底：既不是 /student 也不是 /teacher 的历史地址一律回到学员端首页（老站入口已下线）
    // 显式路由优先级高于 catch-all，故 /student/* 与 /teacher/* 不受此规则影响
    path: '/:pathMatch(.*)*',
    name: 'fallbackToStudent',
    redirect: '/student/dashboard',
  },
];

export const allRoutes = [...defaultRouterList, ...asyncRouterList];

export const getActive = (maxLevel = 2) => {
  const route = useRoute();
  if (!route.path) {
    return '';
  }
  return route.path
    .split('/')
    .filter((_item, index) => index <= maxLevel && index > 0)
    .map((item) => `/${item}`)
    .join('');
};

const router = createRouter({
  history: createWebHashHistory(),
  routes: allRoutes,
  scrollBehavior() {
    return {
      el: '#app',
      top: 0,
      behavior: 'smooth',
    };
  },
});

// -----------------------------------------------------------------------------
// 讲师端登录守卫
// -----------------------------------------------------------------------------
// 学员端**允许匿名浏览**（课程列表 / 详情本来就是公开内容），但讲师端是后台，
// 必须登录。原先两端都没有守卫 → 未登录也能把整个后台界面渲染出来（只是接口 401），
// 观感上就是"不用登录就能进后台"。
//
// 这里只校验「有没有登录态」，**不做角色校验** —— 管理员和讲师都能进，避免误伤；
// 至于"这门课是不是我的"，由服务端的归属校验兜底（P19/P24 已下沉到服务层）。
// 无登录态时记下来源再跳登录页，登录成功后会回到原来要去的那个讲师页。
router.beforeEach((to) => {
  const needAuth = to.path === '/teacher' || to.path.startsWith('/teacher/');
  if (!needAuth) return true;
  let token = null;
  try {
    token = sessionStorage.getItem('token');
  } catch (e) {
    // 无痕模式等拿不到 sessionStorage 的场景：按未登录处理
  }
  if (token) return true;
  rememberStudentOrigin(to.fullPath);
  return { path: '/login', replace: true };
});

export default router;
