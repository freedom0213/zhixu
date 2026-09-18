// =============================================================================
// 教师端 · 学生分析
// -----------------------------------------------------------------------------
// 课程下拉：GET /cs/teacher/my-courses        → TeacherMyCourseVO（含 sectionNum 总小节数）
// 学生明细：GET /ls/lessons/course-students   → CourseStudentVO[]
//
// ⚠️ 服务端只给「真实有出处的字段」：学习时长在 learning_lesson 里没有对应列，
//    接口不给、页面显示「—」。别在这里拿 0 或估算值补位。
// =============================================================================
import request from '@/utils/request.js';
import { listMyCourses } from '@/api/teacher/dashboard';

const L = '/ls';

/**
 * 统一请求：成功返回裸 JSON；失败抛出**服务端的中文原因**。
 * learning-service 出错是 HTTP 400 + text/plain，axios 默认只给「status code 400」。
 */
const call = async (config, fallback = '请求失败') => {
  let res;
  try {
    res = await request(config);
  } catch (e) {
    const body = e?.response?.data;
    if (typeof body === 'string' && body.trim()) throw new Error(body.trim());
    throw new Error(e?.message || fallback);
  }
  if (res && typeof res === 'object' && !Array.isArray(res) && 'code' in res) {
    if (Number(res.code) !== 200) throw new Error(res.msg || fallback);
    return res.data;
  }
  return res;
};

export { listMyCourses };

/** 某门课的报名学生明细（服务端已校验：只允许该课讲师调用） */
export const listCourseStudents = (courseId) =>
  call(
    { url: `${L}/lessons/course-students`, method: 'get', params: { courseId }, timeout: 15000 },
    '读取学生数据失败'
  );

/** 后端状态码 → 文案（0未学习/1学习中/2已学完/3已过期） */
export const LESSON_STATUS_TEXT = {
  0: '未开始',
  1: '学习中',
  2: '已学完',
  3: '已过期',
};
