// =============================================================================
// 教师端 · 工作概览 / 我的课程 接口（T1 / T2）
// -----------------------------------------------------------------------------
// 「我的课程」已接真实数据（契约 §18）：
//   GET /cs/teacher/my-courses —— 我讲的课（正式表）+ 我建的课（草稿表）
//   章节数来自目录表、状态来自课程状态；**题目数**按课程从题库反推（与题库列表同口径）。
//   学生数 / 考试场次 / 知识点缺少聚合来源 → 返回 null，页面显示「—」，不用 0 冒充。
//
// ✅ 「工作概览」已改接真实数据（P20）：后端那个跨服务聚合接口（契约 §12.5）**不用等了** ——
//    四个数字各自都有现成来源，前端并列取一次就有：
//      我的课程 / 课程速览 ← /cs/teacher/my-courses（含按课程反推的题目数）
//      我的题目            ← /es/questions/page?visibilityScope=mine（只数我出的，含私有的）
//      考试                ← /es/exams/count?mine=1（总数 + 草稿数）
//      待复核成绩          ← /es/exam-records/pending-review（P18 新接口，同一口径）
//    拿不到的（学生数 / 缺考）**返回 null 让页面显示「—」**，不再用 mock 数字冒充。
//    ⚠️ 以前这里是整页 mock，导致**每个讲师看到的概览一模一样**，看起来像越权。
// =============================================================================
import request from '@/utils/request.js';
import { USE_MOCK, EXAM_API_PREFIX } from '@/config/teacherApi';
import * as mock from '@/mock/teacher/dashboard';
// 复用考试接口层的适配结果（状态映射 / 通过率口径），别再写第二份
import { listExams, countExams, getPendingReview } from '@/api/teacher/exams';

const C = '/cs';

/**
 * 统一请求：成功返回裸 JSON；失败抛出**服务端的中文原因**。
 * course-service 出错是 HTTP 400 + text/plain，默认 axios 只给「Request failed with status code 400」。
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

/** 一门课的题目数（真数：题库按课程过滤后的 total） */
const countCourseQuestions = async (courseId) => {
  try {
    const res = await call({
      url: `${EXAM_API_PREFIX}/questions/page`,
      method: 'get',
      params: { courseId, pageNo: 1, pageSize: 1 },
      timeout: 8000,
    });
    return res?.total ?? 0;
  } catch (e) {
    return null; // 拿不到就显示「—」，不写 0
  }
};

/** 我出的题目总数（题库口径 mine = 只数我出的，含「仅我」的私有题） */
const countMyQuestions = async () => {
  try {
    const res = await call({
      url: `${EXAM_API_PREFIX}/questions/page`,
      method: 'get',
      params: { visibilityScope: 'mine', pageNo: 1, pageSize: 1 },
      timeout: 8000,
    });
    return res?.total ?? 0;
  } catch (e) {
    return null; // 拿不到 → 页面显示「—」
  }
};

/** 显示名取登录态；拿不到就让页面回落到「老师」 */
const meName = () => {
  try {
    return JSON.parse(sessionStorage.getItem('userInfo') || '{}').name || '';
  } catch (e) {
    return '';
  }
};

/** T1 工作概览（真数据：课程 / 题目 / 考试 / 待复核各有来源） */
export const getOverview = async () => {
  if (USE_MOCK) return mock.getOverview();

  // 并列取，且**各自兜底**：任何一个挂了都只让那一格显示「—」，不该整页打不开
  const [courses, questions, examCounts, pending, recent] = await Promise.all([
    listMyCourses().catch(() => []),
    countMyQuestions(),
    countExams().catch(() => null),
    getPendingReview().catch(() => null),
    listExams({ page: 1, size: 4 }).catch(() => ({ list: [] })),
  ]);

  return {
    teacher: {
      name: meName(),
      // ⚠️ 平台不是学校系统：没有「学校名」这个概念，后端也没有该字段 → 不再显示
      schoolName: null,
    },
    teaching: {
      courses: courses.length,
      students: null, // 无聚合来源 → 显示「—」
      questions,
      exams: examCounts?.all ?? null,
    },
    todo: {
      drafts: examCounts?.draft ?? null,
      toReview: pending?.pendingCount ?? null,
      submitted: pending?.submittedCount ?? null, // 同一个来源的第二个数，页面拿它当副标
      absentTotal: null, // 平台没有「缺考登记」这个概念（后端无字段）→ 页面不再显示这一项
    },
    recentExams: (recent.list || []).map((e) => ({
      id: e.id,
      name: e.name,
      courseName: e.courseName,
      status: e.status,
      submittedCount: e.submittedCount,
      enrolled: null, // 应考人数需要「选课名单」，平台暂无 → 页面只显示已交卷数
      passRate: e.passRate,
      snapshotAt: e.snapshotAt,
      paperVersion: e.paperVersion,
    })),
    courses,
  };
};

/** T2 我的课程（真实：章节数 / 状态 / 题目数来自库；学生数等无来源的显示「—」） */
export const listMyCourses = async () => {
  if (USE_MOCK) return mock.listMyCourses();
  const res = await call({ url: `${C}/teacher/my-courses`, method: 'get', timeout: 10000 }, '读取我的课程失败');
  const rows = Array.isArray(res) ? res : [];
  return Promise.all(
    rows.map(async (c) => ({
      id: c.id,
      name: c.name,
      chapters: c.chapterNum ?? 0,
      sections: c.sectionNum ?? 0,
      students: null, // 无聚合来源
      examCount: null, // 无聚合来源
      questionCount: await countCourseQuestions(c.id),
      knowledgePoints: [], // 知识点关联表未建（契约 §14.3）
      lastExam: null, // 考试聚合未接（契约 §12.5）
      status: c.status || 'draft',
      // 已上架但草稿表还有未上架的改动 —— 与 mock 的「本地编辑过」语义一致
      edited: !!c.editing,
      coverUrl: c.coverUrl || null,
      outline: null, // 目录明细由编辑页 / 刷新的草稿提供，这里不编造
    })),
  );
};
