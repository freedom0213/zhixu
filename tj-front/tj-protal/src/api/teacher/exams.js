// =============================================================================
// 教师端 · 考试 / 试卷接口 + **适配层**
// -----------------------------------------------------------------------------
// 组卷选题**不需要新接口** —— 复用题库的 GET /es/questions/page。
// 真实分支把后端字段适配成契约形状（与 mock 完全一致），页面零改动。
//
// 适配速查：
//   exam.status        0草稿/1已发布/2批改中/3已结束 ↔ 'draft'|'published'|'marking'|'closed'
//   exam.exam_type     1正式/2练习                  ↔ 'formal' / 'practice'
//   LocalDateTime 输出 ISO "2026-09-20T09:00:00"    ↔ "2026-09-20 09:00"（去 T 截分）
//   响应统一包装 R{code, data}                      → 取 res.data
//   分页参数 pageNo/pageSize                        ↔ page / size
//
// ⚠️ 批改 / 统计（本文件后半段）2026-09-16 起接真实后端（p14 的 9 个端点），
//    形状与 mock 对齐，页面基本零改动；没有数据源的字段（应考人数/缺考/班级/知识点）
//    一律 null → 页面显示「—」，不拿 0 冒充。
// =============================================================================
import request from '@/utils/request.js';
import { USE_MOCK, EXAM_API_PREFIX as P } from '@/config/teacherApi';
import * as mock from '@/mock/teacher/exams';

// ---- 枚举 / 时间映射 ----
const toStatus = (s) => ({ 0: 'draft', 1: 'published', 2: 'marking', 3: 'closed' }[s] || 'draft');
const fromStatus = (s) => ({ draft: 0, published: 1, marking: 2, closed: 3 }[s] ?? 0);
const toExamType = (t) => (t === 2 ? 'practice' : 'formal');
const fromExamType = (t) => (t === 'practice' ? 2 : 1);
const fmt = (dt) => (dt ? String(dt).replace('T', ' ').slice(0, 16) : '');
const unwrap = (res) => res?.data ?? res;

/** 后端考试行 / 详情 → 契约形状（通过率 / 均分要等批改阶段回填，此前恒 null，前端显示 —） */
function toExamContract(raw) {
  if (!raw) return raw;
  return {
    id: raw.id,
    name: raw.name,
    courseId: raw.courseId,
    courseName: raw.courseName || '',
    status: toStatus(raw.status),
    examType: toExamType(raw.examType),
    passScore: raw.passScore,
    duration: raw.duration,
    notice: raw.notice || '',
    startAt: fmt(raw.startAt),
    endAt: fmt(raw.endAt),
    items: raw.items || [],
    submittedCount: raw.submittedCount ?? 0,
    passRate: raw.passRate ?? null,
    avgScore: raw.avgScore ?? null,
    paperVersion: raw.paperVersion ?? null,
    snapshotAt: fmt(raw.snapshotAt),
    snapshotItems: raw.snapshotItems ?? null,
  };
}

/** 考试列表：{ status, keyword, page, size } → { list, total, page, size } */
export const listExams = async (params = {}) => {
  if (USE_MOCK) return mock.listExams(params);
  const { status, keyword, page = 1, size = 20 } = params;
  const res = unwrap(
    await request({
      url: `${P}/exams/page`,
      method: 'get',
      params: {
        // mine=1：教师端「考试管理」只看**我创建的**卷子（后端按 creater 过滤，P18）
        mine: 1,
        status: status ? fromStatus(status) : undefined,
        keyword: keyword || undefined,
        pageNo: page,
        pageSize: size,
      },
    }),
  );
  return {
    list: (res?.list || []).map(toExamContract),
    total: res?.total ?? 0,
    page,
    size,
  };
};

/** 状态计数（列表页的筛选 chips）：{ all, draft, published, closed } */
export const countExams = async () => {
  if (USE_MOCK) return mock.countExams();
  const res = await unwrap(await request({ url: `${P}/exams/count`, method: 'get', params: { mine: 1 } }));
  return res || { all: 0, draft: 0, published: 0, closed: 0 };
};

/** 考试详情（含展开后的题目；已发布读快照 —— 后端 ExamDetailVO 已输出契约形状） */
export const getExam = async (id) => {
  if (USE_MOCK) return mock.getExam(id);
  const res = toExamContract(unwrap(await request({ url: `${P}/exams/${id}`, method: 'get' })));
  return { ...res, expandedItems: res.expandedItems || [] };
};

/** 保存草稿（新建或更新）—— 草稿只存引用，不生成快照 */
export const saveExamDraft = async (data, id) => {
  if (USE_MOCK) return mock.saveExamDraft(data, id);
  const body = {
    name: data.name,
    courseId: data.courseId,
    examType: fromExamType(data.examType),
    passScore: data.passScore,
    duration: data.duration,
    notice: data.notice,
    startAt: data.startAt || undefined,
    endAt: data.endAt || undefined,
    items: data.items || [],
    publishMode: data.publishMode || 'now',
  };
  if (id) {
    await request({ url: `${P}/exams/${id}`, method: 'put', data: body });
    return { id, ...data };
  }
  const newId = unwrap(await request({ url: `${P}/exams`, method: 'post', data: body }));
  return { id: newId, ...data };
};

/**
 * 发布 —— 服务端在这一步生成快照（paperVersion / snapshotItems）。
 * 返回值补齐弹窗需要的 name / items（来自提交的 settings，口径与页面一致）。
 */
export const publishExam = async (id, settings = {}) => {
  if (USE_MOCK) return mock.publishExam(id, settings);
  const body = {
    name: settings.name,
    courseId: settings.courseId,
    examType: fromExamType(settings.examType),
    passScore: settings.passScore,
    duration: settings.duration,
    notice: settings.notice,
    startAt: settings.startAt || undefined,
    endAt: settings.endAt || undefined,
    items: settings.items || [],
    publishMode: settings.publishMode || 'now',
  };
  const res = (await unwrap(await request({ url: `${P}/exams/${id}/publish`, method: 'post', data: body }))) || {};
  return {
    ...settings,
    status: toStatus(res.status),
    paperVersion: res.paperVersion,
    snapshotAt: fmt(res.snapshotAt),
  };
};

/** 删除（仅草稿） */
export const removeExam = async (id) => {
  if (USE_MOCK) return mock.removeExam(id);
  await request({ url: `${P}/exams/${id}`, method: 'delete' });
  return { ok: true };
};

// ---- 批改 / 统计（p15 第 2、3 步：后端已就绪，这里把真实数据适配成页面期望的形状） ----
//
// 适配口径（都和 mock 对齐，页面因此几乎零改动）：
//   · 记录状态  0进行中 / 1已交卷 / 2已复核  → 'doing' / 'graded' + confirmed
//   · 选项       快照里是字符串数组          → [{key:'A', text:'…'}]（页面按 A/B/C 渲染）
//   · 答案       "1,3"（1 基的选项序号）     → ['A','C']
//   · 比率       后端返回**百分比 0~100**     → 页面用 0~1（乘 100 显示），这里 /100
//   · 没有数据源的字段一律 null（应考人数 / 缺考 / 班级 / 知识点），页面显示「—」
//     ——不拿 0 冒充：应考人数要靠"选课名单"，考试服务里根本没有这张表。

const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'];
/** 快照里的 options（字符串数组）→ 页面要的 [{key,text}] */
const toOptions = (opts) =>
  (opts || []).map((t, i) => ({
    key: LETTERS[i] || String(i + 1),
    text: typeof t === 'string' ? t : t?.text ?? String(t ?? ''),
  }));
/** "1,3" → ['A','C']（题库答案是 1 基的选项序号；页面按字母渲染） */
const toKeys = (ans) =>
  String(ans ?? '')
    .split(',')
    .map((x) => x.trim())
    .filter(Boolean)
    .map((x) => LETTERS[Number(x) - 1] || '')
    .filter(Boolean);

/** ExamRecordPageVO → 批改页期望的交卷记录 */
const toSubmission = (r) => ({
  id: r.id,
  studentId: r.studentId,
  studentName: r.studentName || '学员',
  className: '', // 平台不是学校系统：没有班级这个概念，页面显示 —
  status: r.status === 0 ? 'doing' : 'graded',
  total: r.status === 0 ? null : r.score ?? null,
  passed: r.passed === 1,
  confirmed: r.status === 2,
  submittedAt: fmt(r.finishTime),
  durationMin: r.duration ? Math.round(r.duration / 60) : null,
});

/** 汇总从明细推导：列表 / 批改 / 统计三处数字同源，不各算一份 */
const buildSummary = (rows) => {
  const graded = rows.filter((r) => r.status !== 'doing');
  const totals = graded.map((r) => r.total ?? 0);
  const avg = totals.length ? totals.reduce((s, x) => s + x, 0) / totals.length : null;
  return {
    enrolled: null, // 无数据源（没有"选课名单"）
    absent: null, // 同上
    submitted: graded.length,
    confirmed: rows.filter((r) => r.confirmed).length,
    avgScore: avg === null ? null : Math.round(avg * 10) / 10,
    passRate: graded.length ? graded.filter((r) => r.passed).length / graded.length : null,
    highest: totals.length ? Math.max(...totals) : null,
    lowest: totals.length ? Math.min(...totals) : null,
  };
};

/**
 * 待批改总览（P18，讲师视角）—— 供应「试卷批改」页与工作概览的「需要处理」。
 * 后端只给**我建的、非草稿**的卷里**有人交过卷**的场次，份数由 exam_record 明细数出。
 */
export const getPendingReview = async () => {
  if (USE_MOCK) return mock.getPendingReview();
  const res = unwrap(await request({ url: `${P}/exam-records/pending-review`, method: 'get' }));
  const items = (res?.items || []).map((r) => ({
    examId: r.examId,
    examName: r.examName,
    courseId: r.courseId,
    courseName: r.courseName || '',
    examType: toExamType(r.examType),
    questionCount: r.questionCount ?? 0,
    totalScore: r.totalScore ?? 0,
    submittedCount: r.submittedCount ?? 0,
    pendingCount: r.pendingCount ?? 0,
    reviewedCount: r.reviewedCount ?? 0,
    lastSubmitAt: fmt(r.lastSubmitTime),
  }));
  return {
    examCount: items.length,
    pendingCount: items.reduce((s, x) => s + x.pendingCount, 0),
    submittedCount: items.reduce((s, x) => s + x.submittedCount, 0),
    items,
  };
};

/** 交卷列表 + 汇总（批改页左栏与页头） */
export const listSubmissions = async (examId, params = {}) => {
  if (USE_MOCK) return mock.listSubmissions(examId, params);
  const [records, exam] = await Promise.all([
    unwrap(await request({ url: `${P}/exams/${examId}/records`, method: 'get' })),
    getExam(examId),
  ]);
  const all = (records || []).map(toSubmission);
  const { status = '', keyword = '' } = params;
  const kw = String(keyword).trim().toLowerCase();
  const list = all.filter((s) => {
    if (status && s.status !== status) return false;
    if (kw && !s.studentName.toLowerCase().includes(kw)) return false;
    return true;
  });
  return { exam, summary: buildSummary(all), list };
};

/** 单份答卷（批改页右栏）—— 逐题读**作答时冻结的那份快照** */
export const getSubmission = async (examId, subId) => {
  if (USE_MOCK) return mock.getSubmission(examId, subId);
  const [res, exam] = await Promise.all([
    unwrap(await request({ url: `${P}/exam-records/${subId}/result`, method: 'get' })),
    getExam(examId),
  ]);
  const submission = {
    id: res.recordId,
    studentId: res.studentId,
    studentName: res.studentName || '学员',
    className: '',
    status: res.status === 0 ? 'doing' : 'graded',
    total: res.status === 0 ? null : res.score ?? null,
    fullScore: res.totalScore ?? null,
    passed: res.passed === 1,
    confirmed: res.status === 2,
    submittedAt: fmt(res.finishTime),
    durationMin: res.duration ? Math.round(res.duration / 60) : null,
  };
  const items = (res.details || []).map((d) => ({
    order: d.index,
    questionId: d.questionId,
    score: d.maxScore,
    type: d.type,
    stem: d.name,
    options: toOptions(d.options),
    answer: toKeys(d.correctAnswer),
    analysis: d.analysis || '',
    knowledgePoints: [], // 题目↔知识点关联尚未实施，页面显示 —
    studentAnswer: toKeys(d.myAnswer),
    correct: d.correct === 1,
    gotScore: d.score ?? 0,
    answered: !!(d.myAnswer && String(d.myAnswer).length),
  }));
  return { submission, items, paperVersion: exam.paperVersion, snapshotAt: exam.snapshotAt };
};

/** 复核（讲师确认成绩） */
export const confirmSubmission = async (examId, subId) => {
  if (USE_MOCK) return mock.confirmSubmission(examId, subId);
  await request({ url: `${P}/exam-records/${subId}/review`, method: 'put' });
  return { ok: true };
};

/** 统计（T8）：概览 + 分数段 + 逐题正确率；全部只计已交卷 */
export const getStats = async (examId) => {
  if (USE_MOCK) return mock.getStats(examId);
  const [st, exam] = await Promise.all([
    unwrap(await request({ url: `${P}/exams/${examId}/statistics`, method: 'get' })),
    getExam(examId),
  ]);
  const snapshot = exam.snapshotItems || exam.expandedItems || [];
  const fullFromSnapshot = snapshot.reduce((s, x) => s + (x.score || 0), 0);
  return {
    exam,
    fullScore: st.totalScore ?? fullFromSnapshot,
    summary: {
      enrolled: null,
      absent: null,
      submitted: st.submittedCount ?? 0,
      avgScore: st.avgScore ?? null,
      passRate: st.passRate == null ? null : st.passRate / 100,
      highest: st.maxScore ?? null,
      lowest: st.minScore ?? null,
    },
    distribution: (st.distribution || []).map((b) => ({ label: b.label, count: b.count })),
    perQuestion: (st.perQuestion || []).map((q) => ({
      order: q.order,
      questionId: q.questionId,
      stem: q.stem,
      type: q.type,
      score: q.score,
      knowledgePoints: [],
      attempts: q.attempts,
      correctCount: q.correctCount,
      correctRate: q.correctRate == null ? null : q.correctRate / 100,
    })),
    // 题目与知识点还没建立关联（契约遗留项）→ 没有数据源，页面给一句说明
    perKnowledge: [],
  };
};
