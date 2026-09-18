// =============================================================================
// 学生作答接口（p14：把「答题 → 交卷判分 → 记录 → 题库正确率」这条链路接起来）
// -----------------------------------------------------------------------------
// 历史遗留：这里原来是
//   getSubject  → POST /es/exams        ← 那个路径在**讲师端**是「保存试卷草稿」，语义冲突
//   postSubject → POST /es/exams/details ← 后端根本不存在
// 现在换成诚实的三步：
//   ① 按小节开始作答 → 拿到题目（**不含答案**）
//   ② 交卷            → 自动判分，返回成绩与逐题结果
//   ③ 回看            → 读**作答时冻结的那份快照**
//
// ⚠️ exam-service 的响应是裸 JSON（不包 R{code,data}）；出错是 HTTP 400 + text/plain 中文。
//    所以统一走 call()：成功直通、失败把服务端原话抛出来。
// =============================================================================
import request from '@/utils/request.js';

const P = '/es';

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

/** 按小节开始作答（随堂练习入口：课程学习页点「考试」小节）。返回题目，**不含答案** */
export const startExamBySection = (sectionId) =>
  call({ url: `${P}/exams/section/${sectionId}/start`, method: 'post', timeout: 15000 }, '开始作答失败');

/** 按试卷id开始作答。已交卷会被如实拒绝（「考试只能考一次」） */
export const startExam = (examId) =>
  call({ url: `${P}/exams/${examId}/start`, method: 'post', timeout: 15000 }, '开始作答失败');

/**
 * 交卷。
 * @param examId 试卷id
 * @param examDetails [{ questionId, answer, questionType }]，answer 是「选项编号升序串」（如 "1,3"）
 * 幂等：重复交卷返回既有成绩，不会重复判分。
 */
export const submitExam = (examId, examDetails) =>
  call({ url: `${P}/exams/${examId}/submit`, method: 'post', data: { examDetails }, timeout: 20000 }, '交卷失败');

/** 我的这场成绩（答卷回看，读作答时冻结的快照） */
export const getExamResult = (examId) =>
  call({ url: `${P}/exams/${examId}/result`, method: 'get', timeout: 10000 }, '读取答卷失败');

/**
 * 某门课程下**已发布**的试卷（学生视角，P17）。
 * 学生端两处共用：课程学习页右侧「考试」页签 + 课程详情页的「课程试卷」。
 * 每项带 `myStatus / myScore`（null = 还没考），所以列表就能看出哪场没考、哪场考了多少分。
 */
export const pageCourseExams = (courseId) =>
  call(
    { url: `${P}/exams/published`, method: 'get', params: { courseId }, timeout: 10000 },
    '读取课程试卷失败',
  );

/** 我的作答记录（分页） */
export const pageMyExamRecords = (params = {}) =>
  call(
    {
      url: `${P}/exam-records/page`,
      method: 'get',
      params: { pageNo: params.pageNo || 1, pageSize: params.pageSize || 10 },
      timeout: 10000,
    },
    '读取考试记录失败',
  );
