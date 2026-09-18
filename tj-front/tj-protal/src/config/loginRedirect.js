// 登录成功后的跳转目标：
// - 从学员端（/student/*）触发的登录 → 登录后回到来源页（默认学习首页）
// - 从教师端（/teacher/*）触发的登录 → 登录后回到来源页（默认工作概览）
// - 其它来源 / 直接访问登录页 → 默认进学员端首页（新前端已取代老站 /main/index）
// hash 路由下来源路径记录在 sessionStorage（登录页自身不带来源信息）。
export const STUDENT_HOME = '/student/dashboard';
export const TEACHER_HOME = '/teacher/dashboard';
const KEY = 'tianji:login-redirect';

// 允许回跳的前缀白名单：只认「本前端自己的端」，防止开放重定向到外部地址
const ALLOWED_PREFIXES = ['/student', '/teacher'];
const isAppPath = (p) =>
  typeof p === 'string' && ALLOWED_PREFIXES.some((prefix) => p === prefix || p.startsWith(`${prefix}/`));

// 在需要「登录后回原端」的入口（学员端 / 教师端侧栏的登录按钮）调用
export function rememberStudentOrigin(path) {
  try {
    sessionStorage.setItem(KEY, isAppPath(path) ? path : STUDENT_HOME);
  } catch (e) { /* 无痕模式等场景静默 */ }
}

export function loginRedirect() {
  let from = null;
  try {
    from = sessionStorage.getItem(KEY);
    sessionStorage.removeItem(KEY);
  } catch (e) { /* ignore */ }
  if (isAppPath(from)) return from;
  return STUDENT_HOME;
}

// 登录成功后按「身份 + 来源」决定落点：
// - 来源页存在且与身份同端 → 回来源页（教师从 /teacher 进来登录回 /teacher，学员同理）
// - 无来源或来源与身份不符 → 按身份落各自的首页（一个登录页、按身份跳不同端）
// 身份判定：userInfo.type===3 即教师；type 缺失时（/us/users/me 的 VO 不含 type）退回用登录通道判断 ——
// 只有「非学生端用户」被拒后才走管理端通道，所以 staffChannel=true 就意味着教师/管理员身份。
export function redirectAfterLogin(userInfo, staffChannel = false) {
  const type = Number(userInfo?.type);
  const isTeacher = type === 3 || (Number.isNaN(type) && staffChannel === true) || staffChannel;
  const home = isTeacher ? TEACHER_HOME : STUDENT_HOME;
  let from = null;
  try {
    from = sessionStorage.getItem(KEY);
    sessionStorage.removeItem(KEY);
  } catch (e) { /* ignore */ }
  if (from && isAppPath(from)) {
    const fromIsTeacher = from.startsWith('/teacher');
    if (fromIsTeacher === isTeacher) return from; // 来源与身份同端才回跳
  }
  return home;
}
