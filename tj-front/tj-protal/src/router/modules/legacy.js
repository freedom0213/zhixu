// =============================================================================
// 老站入口重定向（学员端已取代老站）
// -----------------------------------------------------------------------------
// 学员端上线后，老站的功能入口统一落到新学员端对应页面；
// 老页面源码保留（新端仍复用老学习页组件），但不再对外暴露独立入口。
// 注意：重定向按前缀匹配，query 原样透传（搜索关键词、订单号等）。
// =============================================================================

const keepQuery = (path) => (to) => ({ path, query: to.query });

export default [
  // ---- 老官网首页及其子页 ----
  {
    path: '/main/ai',
    redirect: '/student/ai',
  },
  {
    path: '/main/coupon',
    redirect: '/student/coupons',
  },
  {
    path: '/main/:rest(.*)*',
    redirect: '/student/dashboard',
  },

  // ---- 个人中心 ----
  {
    path: '/personal/:rest(.*)*',
    redirect: '/student/profile',
  },

  // ---- 支付链路 ----
  {
    path: '/pay/success',
    redirect: keepQuery('/student/paySuccess'),
  },
  {
    path: '/pay/carts',
    redirect: '/student/carts',
  },
  {
    path: '/pay/settlement',
    redirect: keepQuery('/student/settlement'),
  },
  {
    path: '/pay/payment',
    redirect: keepQuery('/student/payment'),
  },
  {
    path: '/pay/:rest(.*)*',
    redirect: '/student/carts',
  },

  // ---- 课程详情 / 学习页 / 搜索 / 问答详情 ----
  {
    path: '/details/:rest(.*)*',
    redirect: keepQuery('/student/courses/detail'),
  },
  {
    path: '/learning/:rest(.*)*',
    redirect: keepQuery('/student/learn'),
  },
  {
    path: '/search/:rest(.*)*',
    redirect: keepQuery('/student/courses'),
  },
  {
    path: '/askDetails/:rest(.*)*',
    redirect: '/student/messages',
  },
];
