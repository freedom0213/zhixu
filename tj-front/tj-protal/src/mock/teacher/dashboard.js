// =============================================================================
// 教师端 · 工作概览 / 我的课程 mock（T1 / T2）
// -----------------------------------------------------------------------------
// 聚合页最容易犯的错：为了好看把数字**再写一份**。这里全部从
// questions / exams 两个既有 mock **推导**，与列表、批改、统计天然同源 ——
// 哪个页面的数字变了，概览跟着变，不需要人工同步。
//
// 展示型字段（章节数 / 学生数）是题库与考试之外的东西，用种子伪随机补齐，
// 同样保证刷新不变。
// =============================================================================
import { COURSES, KNOWLEDGE, QUESTIONS, CURRENT } from './questions';
import { draftOf } from './course';
import { allExams, submissionsOf } from './exams';

const delay = (ms = 240) => new Promise((resolve) => setTimeout(resolve, ms));
const clone = (v) => JSON.parse(JSON.stringify(v));

const rng = (seed) => () => {
  seed = (seed * 1103515245 + 12345) % 2147483648;
  return seed / 2147483648;
};

/** 每门课的展示信息（章节 / 学生数确定性生成） */
const courseMeta = () => {
  const meta = {};
  for (const c of COURSES) {
    const rand = rng(c.id * 104729);
    meta[c.id] = {
      chapters: 4 + Math.floor(rand() * 5), // 4–8 章
      students: 60 + Math.floor(rand() * 90), // 60–150 人
    };
  }
  return meta;
};
const META = courseMeta();

const myQuestions = () => QUESTIONS.filter((x) => x.creatorId === CURRENT.id);
const myExams = () => allExams();

/** T1 · 工作概览 */
export const getOverview = async () => {
  await delay();
  const mine = myQuestions();
  const exams = myExams();

  // 待办从提交记录反推（同源，不写死）
  let toReview = 0;
  let absentTotal = 0;
  for (const ex of exams) {
    const subs = submissionsOf(ex.id);
    toReview += subs.filter((s) => s.total !== null && !s.confirmed).length;
    absentTotal += subs.filter((s) => s.total === null).length;
  }
  const drafts = exams.filter((x) => x.status === 'draft').length;

  const recentExams = [...exams]
    .sort((a, b) => b.id - a.id)
    .slice(0, 4)
    .map((ex) => {
      const subs = submissionsOf(ex.id);
      const graded = subs.filter((s) => s.total !== null);
      return {
        id: ex.id,
        name: ex.name,
        courseName: ex.courseName,
        status: ex.status,
        submittedCount: graded.length,
        enrolled: subs.length,
        passRate: graded.length ? graded.filter((s) => s.passed).length / graded.length : null,
        snapshotAt: ex.snapshotAt,
        paperVersion: ex.paperVersion,
      };
    });

  return {
    teacher: clone(CURRENT),
    teaching: {
      courses: COURSES.length,
      students: COURSES.reduce((s, c) => s + META[c.id].students, 0),
      questions: mine.length,
      exams: exams.length,
    },
    todo: { toReview, absentTotal, drafts },
    recentExams,
    courses: COURSES.map((c) => ({ ...clone(c), ...META[c.id], knowledgePoints: (KNOWLEDGE[c.id] || []).length })),
  };
};

/** T2 · 我的课程 */
export const listMyCourses = async () => {
  await delay();
  return COURSES.map((c) => {
    const exams = allExams().filter((x) => x.courseId === c.id);
    const questionCount = QUESTIONS.filter((x) => x.courseId === c.id).length;
    const lastExam = [...exams].sort((a, b) => b.id - a.id)[0] || null;
    // 本地编辑结果覆盖静态数据：编辑过就显示编辑后的名称 / 章数 / 状态 / 目录
    const local = draftOf(c.id);
    const sections = local ? local.chapters.reduce((n, ch) => n + ch.sections.length, 0) : null;
    return {
      id: c.id,
      name: local?.basic?.name || c.name,
      ...clone(META[c.id]),
      chapters: local ? local.chapters.length : META[c.id].chapters,
      sections,
      status: local?.status || null, // draft / published；null = 未在本地编辑过
      edited: Boolean(local),
      outline: local ? local.chapters : null, // 预览抽屉用；未编辑过则为 null（不编造目录）
      knowledgePoints: (KNOWLEDGE[c.id] || []).map((k) => k.name),
      questionCount,
      examCount: exams.length,
      lastExam: lastExam ? { name: lastExam.name, status: lastExam.status } : null,
    };
  });
};
