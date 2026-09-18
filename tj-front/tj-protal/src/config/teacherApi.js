// =============================================================================
// 教师端数据源开关
// -----------------------------------------------------------------------------
// 阶段 1（题库闭环）刻意做成「前端先行、契约先冻」：
//   后端未就绪时走 mock，接口就绪后改环境变量即可切到真实请求，页面代码一行不动。
//
//   开发：VITE_TEACHER_MOCK=1（默认）→ 用 src/mock/teacher/* 的内存数据
//   开发：VITE_TEACHER_MOCK=0         → 走 @/utils/request 打真实后端
//   生产（vite build / docker）：默认强制关 —— mock 是开发脚手架，
//   绝不能被默认打进容器；要在容器里演示教师端，必须显式 VITE_TEACHER_MOCK=1。
// =============================================================================

const raw = import.meta.env.VITE_TEACHER_MOCK;
const isProd = import.meta.env.PROD;

// 开发：未配置默认开；生产：必须显式配 '1' 才开（安全阀，防止假数据进 docker）
export const USE_MOCK = isProd
  ? raw === '1'
  : raw === undefined || raw === ''
    ? true
    : !(raw === '0' || raw === 'false');

// 考试/题库服务前缀，与既有 api/subject.js 保持一致
export const EXAM_API_PREFIX = '/es';
