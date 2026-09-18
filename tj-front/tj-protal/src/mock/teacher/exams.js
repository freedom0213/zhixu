// =============================================================================
// 教师端 · 考试 / 试卷 mock（内存实现）
// -----------------------------------------------------------------------------
// 这份文件是阶段 2 的**契约可执行版本**，核心只有一件事：
//
//   **草稿存「引用」，发布才生成「快照」。**
//     · exam.items         —— 只存 { questionId, score }，是**指针**
//       草稿阶段改题库，卷子会跟着变（这是想要的：出卷时修正错别字不用重新选）
//     · exam.snapshotItems —— 发布瞬间把题目内容**冻结**成副本
//       之后题库怎么改，已发布的卷子、学生的答卷、教师的批改、成绩统计全部不受影响
//
// 为什么必须这样分：
//   纯引用 → 老师改了题库，历史考卷跟着变，成绩单上的题和学生对不上的题永远对不上；
//   纯快照 → 出卷过程中修正一道题，还得重新选一遍。
//   所以按状态分开：**草稿引用，发布快照**。
// =============================================================================
import { findQuestionSync, COURSES } from './questions';

const CURRENT = { id: 42, name: '王老师' };

const delay = (ms = 260) => new Promise((resolve) => setTimeout(resolve, ms));
const clone = (v) => JSON.parse(JSON.stringify(v));

const courseName = (id) => COURSES.find((c) => c.id === Number(id))?.name || '';

const e = (id, name, courseId, status, items, extra = {}) => ({
  id,
  name,
  courseId: Number(courseId),
  courseName: courseName(courseId),
  status,
  examType: 'formal',
  passScore: 60,
  duration: 90,
  attempts: 1,
  shuffleQuestions: true,
  shuffleOptions: false,
  publishMode: 'now',
  startAt: '2026-09-20 09:00',
  endAt: '2026-09-20 10:30',
  notice: '',
  creatorId: CURRENT.id,
  // 引用：只有 id 与分值
  items,
  // 快照：发布后才有
  snapshotItems: null,
  paperVersion: null,
  snapshotAt: null,
  submittedCount: 0,
  passRate: null,
  avgScore: null,
  ...extra,
});

// 引用式 items：{ questionId, score }
const items = (...pairs) => pairs.map(([questionId, score]) => ({ questionId, score }));

let EXAMS = [
  e(9001, 'Java 集合与并发编程 · 期中测试', 301, 'marking', items([1001, 5], [1002, 6], [1003, 5], [1004, 8], [1005, 6]), {
    submittedCount: 42,
    passScore: 18, // 满分 30，及格线 60%
    duration: 45,
    passRate: 0.71,
    avgScore: 76.4,
    paperVersion: 'v1',
    snapshotAt: '2026-09-20 09:00',
    startAt: '2026-09-20 09:00',
    endAt: '2026-09-20 10:30',
  }),
  e(9002, 'Spring Boot 章节测验 3', 302, 'closed', items([1006, 5], [1007, 5], [1008, 5], [1009, 5]), {
    submittedCount: 38,
    passScore: 12, // 满分 20
    duration: 60,
    passRate: 0.86,
    avgScore: 82.1,
    paperVersion: 'v1',
    snapshotAt: '2026-09-10 14:00',
    startAt: '2026-09-10 14:00',
    endAt: '2026-09-10 15:00',
  }),
  e(9003, 'Vue3 期中测验', 303, 'published', items([1011, 5], [1012, 8], [1013, 5]), {
    submittedCount: 7,
    passScore: 11, // 满分 18
    duration: 90,
    passRate: null,
    avgScore: null,
    paperVersion: 'v1',
    snapshotAt: '2026-09-18 10:00',
    startAt: '2026-09-18 10:00',
    endAt: '2026-09-18 11:30',
  }),
  e(9004, 'MySQL 性能优化 · 随堂测', 304, 'draft', items([1016, 5], [1017, 5]), {
    passScore: 6, // 满分 10
    duration: 45,
    startAt: '2026-09-25 14:00',
    endAt: '2026-09-25 14:45',
  }),
];

// ---- 给已发布的种子考试补上快照 ----
// 这些考试的 paperVersion 是写死的，但 snapshotItems 之前没人造 ——
// 结果批改 / 统计页只能拿到 { questionId, score }，整页「未作答」。
// 这里按题库现状冻结一份副本，等价于「发布那一刻」的快照。
for (const ex of EXAMS) {
  if (ex.paperVersion && !ex.snapshotItems) {
    ex.snapshotItems = ex.items.map((item) => {
      const q = findQuestionSync(item.questionId);
      return {
        questionId: item.questionId,
        score: item.score,
        type: q?.type || 'single',
        stem: q?.stem || '',
        options: clone(q?.options || []),
        answer: clone(q?.answer || []),
        analysis: q?.analysis || '',
        difficulty: q?.difficulty || 'medium',
        knowledgePoints: clone(q?.knowledgePoints || []),
        courseId: q?.courseId,
      };
    });
  }
}

const STATUS_LABEL = { draft: '草稿', published: '已发布', marking: '批改中', closed: '已结束' };

export { STATUS_LABEL };

/**
 * 把引用展开成可展示的题目（列表页 / 编辑器读的都是这个）。
 * 注意：**已发布的卷子必须先读 snapshotItems**，读不到才回退到 items ——
 * 这样即便题库里的题后来被改过或停用，历史卷子显示的仍是考场上那一版。
 */
const expandItems = (exam) => {
  const source = exam.snapshotItems || exam.items;
  return source.map((item, i) => {
    const q = exam.snapshotItems ? item : findQuestionSync(item.questionId);
    return {
      questionId: item.questionId,
      score: item.score,
      order: i + 1,
      type: q?.type || 'single',
      stem: q?.stem || '（题目已不存在）',
      difficulty: q?.difficulty || 'medium',
      analysis: q?.analysis || '',
      // 「题库里现在长什么样」——与快照不同就说明这道题被改过
      changedFromLibrary: exam.snapshotItems
        ? (findQuestionSync(item.questionId)?.stem || '') !== q?.stem
        : false,
    };
  });
};

export const listExams = async (params = {}) => {
  await delay();
  const { status, keyword, page = 1, size = 20 } = params;
  const filtered = EXAMS.filter((x) => {
    if (status && x.status !== status) return false;
    if (keyword) {
      const kw = String(keyword).trim().toLowerCase();
      if (!kw) return true;
      return x.name.toLowerCase().includes(kw) || x.courseName.toLowerCase().includes(kw);
    }
    return true;
  }).sort((a, b) => b.id - a.id);
  const start = (page - 1) * size;
  return { list: clone(filtered.slice(start, start + size)), total: filtered.length, page, size };
};

export const countExams = async () => {
  await delay(120);
  return {
    all: EXAMS.length,
    draft: EXAMS.filter((x) => x.status === 'draft').length,
    published: EXAMS.filter((x) => ['published', 'marking'].includes(x.status)).length,
    closed: EXAMS.filter((x) => x.status === 'closed').length,
  };
};

export const getExam = async (id) => {
  await delay(160);
  const found = EXAMS.find((x) => x.id === Number(id));
  if (!found) throw new Error('考试不存在');
  return { ...clone(found), expandedItems: expandItems(found) };
};

/** 保存草稿（新建或更新）—— 草稿只存引用，不生成快照 */
export const saveExamDraft = async (data, id) => {
  await delay(320);
  if (id) {
    const idx = EXAMS.findIndex((x) => x.id === Number(id));
    if (idx < 0) throw new Error('考试不存在');
    if (EXAMS[idx].status !== 'draft') throw new Error('只有草稿可以编辑');
    EXAMS[idx] = {
      ...EXAMS[idx],
      ...clone(data),
      courseId: Number(data.courseId ?? EXAMS[idx].courseId),
      courseName: courseName(data.courseId ?? EXAMS[idx].courseId),
    };
    return clone(EXAMS[idx]);
  }
  const newId = Math.max(...EXAMS.map((x) => x.id)) + 1;
  const item = e(newId, data.name, data.courseId, 'draft', clone(data.items || []), {
    passScore: Number(data.passScore) || 60,
    duration: Number(data.duration) || 90,
    examType: data.examType || 'formal',
    startAt: data.startAt || '',
    endAt: data.endAt || '',
    notice: data.notice || '',
  });
  EXAMS = [item, ...EXAMS];
  return clone(item);
};

/**
 * 发布 —— 这一步才生成快照。
 * 返回带上 paperVersion 与 snapshotAt，前端据此提示「已冻结」。
 */
export const publishExam = async (id, settings = {}) => {
  await delay(480);
  const idx = EXAMS.findIndex((x) => x.id === Number(id));
  if (idx < 0) throw new Error('考试不存在');
  const exam = EXAMS[idx];
  if (!exam.items.length) throw new Error('还没选任何题目，无法发布');

  // 冻结：把每道题**当时的内容**抄一份进 snapshotItems
  const snapshotItems = exam.items.map((item) => {
    const q = findQuestionSync(item.questionId);
    return {
      questionId: item.questionId,
      score: item.score,
      type: q?.type || 'single',
      stem: q?.stem || '',
      options: clone(q?.options || []),
      answer: clone(q?.answer || []),
      analysis: q?.analysis || '',
      difficulty: q?.difficulty || 'medium',
      knowledgePoints: clone(q?.knowledgePoints || []),
      courseId: q?.courseId,
    };
  });

  EXAMS[idx] = {
    ...exam,
    ...clone(settings),
    status: settings.publishMode === 'draft' ? 'draft' : 'published',
    snapshotItems,
    paperVersion: 'v1',
    snapshotAt: new Date().toISOString().slice(0, 16).replace('T', ' '),
  };
  return clone(EXAMS[idx]);
};

/** 删除：只有草稿可以删（已发布的卷子有作答记录，只能结束不能删） */
export const removeExam = async (id) => {
  await delay(240);
  const idx = EXAMS.findIndex((x) => x.id === Number(id));
  if (idx < 0) throw new Error('考试不存在');
  if (EXAMS[idx].status !== 'draft') throw new Error('已发布的考试不能删除');
  EXAMS.splice(idx, 1);
  return { ok: true };
};

// =============================================================================
// 阶段 3 · 交卷记录与统计（T4 批改 / T8 统计）
// -----------------------------------------------------------------------------
// 两条硬规则在这里落地：
//   1. **判分只读快照** —— 学生答案对比的是 snapshotItems 里的 answer，
//      题库后来怎么改都不会影响历史成绩。
//   2. **确定性生成** —— 用种子伪随机（LCG）而非 Math.random()，
//      同一场考试每次刷新数字一致，演示与截图才可信。
// 题型只有单选/多选（全部客观题），所以判分是自动的：批改页的角色是
// 「复核 + 确认成绩」，不是逐题打分。
// =============================================================================

/** 种子伪随机：同一 seed 序列完全一致 */
const rng = (seed) => () => {
  seed = (seed * 1103515245 + 12345) % 2147483648;
  return seed / 2147483648;
};

const DIFF_PASS_RATE = { easy: 0.88, medium: 0.66, hard: 0.42 };

const NAMES = ['陈一诺', '程翊', '戴澜', '邓子昂', '丁遥', '董书宁', '杜若飞', '范承志', '方晏清', '冯砚',
  '傅明远', '甘霖', '顾星辞', '关山月', '管清越', '郭既白', '韩栖迟', '何朗润', '贺闻笛', '衡芷',
  '洪以行', '侯青山', '华未眠', '黄秉文', '姬厚朴', '纪云开', '简素秋', '江晚照', '姜叙白', '蒋星野',
  '金慕橙', '康叙', '乐正和', '黎照邻', '李望舒', '连曼声', '林疏桐', '刘既明', '柳青崖', '龙在野',
  '卢清和', '陆听澜'];

/** 生成一场考试的交卷记录（含按快照判好的分） */
const buildSubmissions = (exam) => {
  const snapshot = exam.snapshotItems || exam.items;
  const n = exam.submittedCount || 0;
  if (!n || !snapshot.length) return [];
  const rand = rng(exam.id * 7919);
  const out = [];
  for (let i = 0; i < n; i++) {
    const absent = rand() < 0.06; // 约 6% 缺考
    const answers = snapshot.map((item) => {
      // 正确率随难度衰减；学生个体再有 ±0.15 的浮动
      const rate = Math.min(0.96, Math.max(0.1, DIFF_PASS_RATE[item.difficulty || 'medium'] + (rand() - 0.5) * 0.3));
      const correct = rand() < rate;
      // 伪造一个「学生答案」：对则等于正确答案，错则从选项里挑一个非正确答案
      const opts = (item.options || []).map((o) => o.key);
      const ans = item.answer || [];
      if (correct) return { questionId: item.questionId, answer: [...ans], correct: true, score: item.score };
      const wrong = opts.filter((k) => !ans.includes(k));
      const picked = wrong.length ? [wrong[Math.floor(rand() * wrong.length)]] : ans.slice(0, 1);
      return { questionId: item.questionId, answer: picked, correct: false, score: 0 };
    });
    const total = answers.reduce((s, x) => s + x.score, 0);
    const full = snapshot.reduce((s, x) => s + x.score, 0);
    out.push({
      id: exam.id * 100 + i + 1,
      studentId: 20260 + i,
      studentName: NAMES[i % NAMES.length],
      className: ['软件 2301', '软件 2302', '软工 2303'][i % 3],
      status: absent ? 'absent' : rand() < 0.08 ? 'late' : 'graded',
      confirmed: exam.status === 'closed', // 已结束的考试视为全部复核过
      submittedAt: absent ? null : `${exam.startAt.slice(0, 10)} ${String(9 + Math.floor(rand() * 2)).padStart(2, '0')}:${String(Math.floor(rand() * 60)).padStart(2, '0')}`,
      durationMin: absent ? null : Math.max(12, Math.round(exam.duration * (0.35 + rand() * 0.6))),
      answers: absent ? [] : answers,
      total: absent ? null : total,
      fullScore: full,
      passed: absent ? null : total >= (exam.passScore || 60),
    });
  }
  return out;
};

const SUBMISSIONS = {}; // examId -> array
for (const ex of EXAMS) SUBMISSIONS[ex.id] = buildSubmissions(ex);

/** 只读口：概览页（T1）聚合待复核 / 缺考数用，避免把可变数组直接导出去 */
export const submissionsOf = (examId) => SUBMISSIONS[Number(examId)] || [];

/** 只读口：同上，概览页要遍历全部考试做聚合 */
export const allExams = () => EXAMS;

// 列表页展示的 submittedCount / avgScore / passRate 一律从提交记录**反推**，
// 保证列表、批改、统计三处数字同源（写死三份必然互相打架）。
for (const ex of EXAMS) {
  const graded = (SUBMISSIONS[ex.id] || []).filter((s) => s.total !== null);
  ex.submittedCount = graded.length;
  ex.avgScore = graded.length ? Math.round((graded.reduce((s, x) => s + x.total, 0) / graded.length) * 10) / 10 : null;
  ex.passRate = graded.length ? graded.filter((s) => s.passed).length / graded.length : null;
}

const findSubmissions = (examId) => {
  const ex = EXAMS.find((x) => x.id === Number(examId));
  if (!ex) throw new Error('考试不存在');
  return { exam: ex, list: SUBMISSIONS[ex.id] || [] };
};

/**
 * 待批改总览 —— **从 EXAMS / SUBMISSIONS 推导**，不另写一份数字
 * （否则 mock 里的列表与总数迟早对不上，页面看起来就像坏了）。
 */
export const getPendingReview = async () => {
  const items = EXAMS
    .filter((x) => x.status !== 'draft')
    .map((x) => {
      const subs = submissionsOf(x.id).filter((s) => s.status !== 'doing');
      const reviewed = subs.filter((s) => s.confirmed).length;
      const last = subs.map((s) => s.submittedAt).sort().pop() || '';
      return {
        examId: x.id,
        examName: x.name,
        courseId: x.courseId,
        courseName: x.courseName || '',
        examType: x.examType,
        questionCount: (x.items || []).length,
        totalScore: (x.items || []).reduce((s2, i) => s2 + (i.score || 0), 0),
        submittedCount: subs.length,
        reviewedCount: reviewed,
        pendingCount: subs.length - reviewed,
        lastSubmitAt: last,
      };
    })
    .filter((x) => x.submittedCount > 0)
    .sort((a, b) => b.pendingCount - a.pendingCount || String(b.lastSubmitAt).localeCompare(a.lastSubmitAt));
  return {
    examCount: items.length,
    pendingCount: items.reduce((s2, x) => s2 + x.pendingCount, 0),
    submittedCount: items.reduce((s2, x) => s2 + x.submittedCount, 0),
    items,
  };
};

/** 交卷列表 + 汇总（批改页左栏与页头用） */
export const listSubmissions = async (examId, params = {}) => {
  await delay();
  const { exam, list } = findSubmissions(examId);
  const { status = '', keyword = '' } = params;
  const filtered = list.filter((s) => {
    if (status && s.status !== status) return false;
    if (keyword) {
      const kw = String(keyword).trim().toLowerCase();
      if (!kw) return true;
      return s.studentName.toLowerCase().includes(kw) || s.className.toLowerCase().includes(kw);
    }
    return true;
  });
  const graded = list.filter((s) => s.total !== null);
  const summary = {
    enrolled: list.length,
    submitted: graded.length,
    absent: list.length - graded.length,
    confirmed: graded.filter((s) => s.confirmed).length,
    avgScore: graded.length ? Math.round((graded.reduce((s, x) => s + x.total, 0) / graded.length) * 10) / 10 : null,
    passRate: graded.length ? graded.filter((s) => s.passed).length / graded.length : null,
    highest: graded.length ? Math.max(...graded.map((s) => s.total)) : null,
    lowest: graded.length ? Math.min(...graded.map((s) => s.total)) : null,
  };
  return { exam: clone(exam), summary, list: clone(filtered) };
};

/** 单份答卷（批改页右栏）—— 答案解析基于快照展开 */
export const getSubmission = async (examId, subId) => {
  await delay(140);
  const { exam, list } = findSubmissions(examId);
  const sub = list.find((s) => s.id === Number(subId));
  if (!sub) throw new Error('交卷记录不存在');
  const snapshot = exam.snapshotItems || exam.items;
  const items = snapshot.map((item, i) => {
    const a = (sub.answers || []).find((x) => x.questionId === item.questionId);
    return {
      order: i + 1,
      questionId: item.questionId,
      score: item.score,
      type: item.type,
      stem: item.stem,
      options: item.options || [],
      answer: item.answer || [],
      analysis: item.analysis || '',
      knowledgePoints: item.knowledgePoints || [],
      studentAnswer: a ? a.answer : [],
      correct: a ? a.correct : false,
      gotScore: a ? a.score : 0,
      answered: Boolean(a && a.answer && a.answer.length),
    };
  });
  return { submission: clone(sub), items, paperVersion: exam.paperVersion, snapshotAt: exam.snapshotAt };
};

/** 批改确认（客观题自动判分后的「复核确认」动作） */
export const confirmSubmission = async (examId, subId) => {
  await delay(200);
  const { list } = findSubmissions(examId);
  const sub = list.find((s) => s.id === Number(subId));
  if (!sub) throw new Error('交卷记录不存在');
  if (sub.status === 'absent') throw new Error('缺考记录没有可确认的成绩');
  sub.confirmed = true;
  return { ok: true, confirmed: list.filter((s) => s.confirmed).length };
};

/** 统计（T8）—— 逐题 / 逐知识点正确率全部基于快照 */
export const getStats = async (examId) => {
  await delay(220);
  const { exam, list } = findSubmissions(examId);
  const graded = list.filter((s) => s.total !== null);
  const snapshot = exam.snapshotItems || exam.items;
  const full = snapshot.reduce((s, x) => s + x.score, 0);

  const perQuestion = snapshot.map((item, i) => {
    const attempts = graded.filter((s) => (s.answers || []).some((a) => a.questionId === item.questionId));
    const correct = attempts.filter((s) => (s.answers || []).find((a) => a.questionId === item.questionId)?.correct).length;
    return {
      order: i + 1,
      questionId: item.questionId,
      stem: item.stem,
      type: item.type,
      difficulty: item.difficulty,
      score: item.score,
      knowledgePoints: item.knowledgePoints || [],
      attempts: attempts.length,
      correctCount: correct,
      correctRate: attempts.length ? correct / attempts.length : null,
    };
  });

  const kpMap = {};
  for (const q of perQuestion) {
    for (const kp of q.knowledgePoints) {
      const cur = kpMap[kp] || { attempts: 0, correct: 0, score: 0, got: 0 };
      cur.attempts += q.attempts;
      cur.correct += q.correctCount;
      kpMap[kp] = cur;
    }
  }
  const perKnowledge = Object.entries(kpMap).map(([name, v]) => ({
    name,
    attempts: v.attempts,
    correctCount: v.correct,
    correctRate: v.attempts ? v.correct / v.attempts : null,
  })).sort((a, b) => (a.correctRate ?? 1) - (b.correctRate ?? 1));

  // 分数分布：60 以下 / 60–69 / 70–79 / 80–89 / 90–100
  const bins = [
    { label: '< 60', min: -1, max: 60, count: 0 },
    { label: '60–69', min: 59, max: 70, count: 0 },
    { label: '70–79', min: 69, max: 80, count: 0 },
    { label: '80–89', min: 79, max: 90, count: 0 },
    { label: '90–100', min: 89, max: 1000, count: 0 },
  ];
  for (const s of graded) {
    const bin = bins.find((b) => s.total > b.min && s.total <= b.max);
    if (bin) bin.count++;
  }

  return {
    exam: clone(exam),
    fullScore: full,
    summary: {
      enrolled: list.length,
      submitted: graded.length,
      absent: list.length - graded.length,
      avgScore: graded.length ? Math.round((graded.reduce((s, x) => s + x.total, 0) / graded.length) * 10) / 10 : null,
      passRate: graded.length ? graded.filter((s) => s.passed).length / graded.length : null,
      highest: graded.length ? Math.max(...graded.map((s) => s.total)) : null,
      lowest: graded.length ? Math.min(...graded.map((s) => s.total)) : null,
    },
    distribution: bins,
    perQuestion,
    perKnowledge,
  };
};
