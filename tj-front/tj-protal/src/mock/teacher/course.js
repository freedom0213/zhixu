// =============================================================================
// 教师端 · 建课向导 mock（A1→A2→A3→A5→A6 五步）
// -----------------------------------------------------------------------------
// 与考试 mock 同一套纪律：
//   · 草稿整体可存可恢复（每一步单独保存，随时退出再回来）
//   · 上架前校验清单**从数据推导**，不写死 —— 「视频缺 2 节」这类数字
//     必须和 A3 页面看到的实际状态一致
//   · 视频上传是「UI + 本地模拟进度」，真上传依赖腾讯云 VOD 凭证签发，
//     后端就绪后换实现，页面不动（契约 §13 已标注）
// =============================================================================
import { CURRENT, COURSES } from './questions';

const delay = (ms = 240) => new Promise((resolve) => setTimeout(resolve, ms));
const clone = (v) => JSON.parse(JSON.stringify(v));

let nextId = 100;
const id = () => ++nextId;

/** 演示草稿：按设计稿 A2/A3 的示例内容初始化（Spring Boot 课，3 章 9 节） */
const sec = (title, duration = null, video = null, quiz = null, preview = false) => ({
  id: id(),
  title,
  duration,
  preview,
  video: video || { status: 'none', name: '', sizeMB: 0, progress: 0 },
  quiz,
});

// 初始演示底稿（Spring Boot 课的完整目录，用于展示上传/配题等状态）。
// 常量保底：新建模式永远从这里出发，「新建」与「编辑」互不污染。
const DEFAULT_DRAFT = {
  id: 8001,
  status: 'draft', // draft | published
  step: 1,
  basic: {
    name: 'Spring Boot 快速上手',
    category: 'IT 互联网',
    price: 199,
    validDays: 365,
    cover: null,
    intro: '从零开始搭一个能跑的 Spring Boot 工程，理解自动配置与起步依赖背后的机制。',
  },
  chapters: [
    {
      id: 11,
      title: '第 1 章　初识 Spring Boot',
      sections: [
        sec('1.1　Spring Boot 是什么', '12:35', { status: 'done', name: 'springboot-intro.mp4', sizeMB: 206, progress: 100 },
          { count: 2, totalScore: 5, dist: ['单选 1', '多选 1'] }, true),
        sec('1.2　Spring Boot 自动配置', null, { status: 'none', name: '', sizeMB: 0, progress: 0 }),
        sec('1.3　起步依赖与自动装配', '08:20', { status: 'done', name: 'starter-deps.mp4', sizeMB: 128, progress: 100 },
          { count: 3, totalScore: 8, dist: ['单选 2', '多选 1'] }),
        sec('1.4　配置文件详解', '09:48', { status: 'done', name: 'config-explained.mp4', sizeMB: 128, progress: 100 }),
      ],
    },
    {
      id: 12,
      title: '第 2 章　Web 开发',
      sections: [
        sec('2.1　@RestController 与请求映射', '15:02', { status: 'done', name: 'rest-controller.mp4', sizeMB: 240, progress: 100 }),
        sec('2.2　参数校验与统一返回', null, { status: 'none', name: '', sizeMB: 0, progress: 0 }),
      ],
    },
    {
      id: 13,
      title: '第 3 章　数据访问',
      sections: [
        sec('3.1　Spring Data JPA 入门', null, { status: 'none', name: '', sizeMB: 0, progress: 0 }),
        sec('3.2　事务边界与回滚', null, { status: 'none', name: '', sizeMB: 0, progress: 0 }),
        sec('3.3　课程实战：COMPLETE 整合', null, { status: 'none', name: '', sizeMB: 0, progress: 0 }),
      ],
    },
  ],
  teachers: [
    { id: CURRENT.id, name: CURRENT.name, role: '主讲', dept: '计算机学院', certified: true },
    { id: 7, name: '李明', role: '助教', dept: '软件工程系', certified: false },
  ],
  publishMode: 'now',
};

// 可变工作副本：新建模式绑 null（=初始底稿），编辑模式绑课程 id
let DRAFT = clone(DEFAULT_DRAFT);
let DRAFT_COURSE_ID = null;
let loadedSlot = null; // 已加载的槽位，避免重复覆盖内存中的最新编辑

// ---------------------------------------------------------------------------
// 本地持久化（sessionStorage）
// ---------------------------------------------------------------------------
// 为什么需要：mock 全部活在内存里 —— 页面一刷新，编辑结果就回到初始值，
// 使用者会以为「功能没生效」。这里按槽位（课程 id / new）落盘，刷新后能接着改。
// 封面 dataUrl 超过 200KB 时只在内存里预览、不落盘（避免撑爆 sessionStorage 配额）。
const PKEY = 'tianji:mock:course-drafts';
const readStore = () => {
  try { return JSON.parse(sessionStorage.getItem(PKEY) || '{}'); } catch (e) { return {}; }
};
const writeStore = (m) => {
  try { sessionStorage.setItem(PKEY, JSON.stringify(m)); } catch (e) { /* 配额超限：放弃本次落盘，不影响内存状态 */ }
};
const slotOf = (cid) => (cid ? String(cid) : 'new');
const slimForStore = (d) => {
  const c = clone(d);
  const cover = c.basic && c.basic.cover;
  if (cover && cover.dataUrl && cover.dataUrl.length > 200000) delete cover.dataUrl;
  return c;
};
const persist = () => {
  const m = readStore();
  m[slotOf(DRAFT_COURSE_ID)] = slimForStore(DRAFT);
  writeStore(m);
};

/** 切到某个槽位：绑定一致才复用内存副本；切走前先把当前槽位落盘，避免丢改动 */
const loadSlot = (cid) => {
  const key = slotOf(cid);
  if (loadedSlot === key && DRAFT_COURSE_ID === cid) return;
  if (loadedSlot !== null && loadedSlot !== key) persist(); // 切槽位前先落盘当前进度
  const stored = readStore()[key];
  DRAFT_COURSE_ID = cid;
  loadedSlot = key;
  if (stored) {
    DRAFT = clone(stored);
  } else if (cid === null || cid === 302) {
    DRAFT = clone(DEFAULT_DRAFT); // 新建模式与 302 都用完整演示底稿
  } else {
    DRAFT = draftForCourse(COURSES.find((c) => c.id === cid));
  }
};

/** 只读：某课程在本地是否已有编辑结果（供「我的课程」列表与预览抽屉用） */
export const draftOf = (courseId) => readStore()[String(courseId)] || null;

const findSection = (sid) => {
  for (const ch of DRAFT.chapters) {
    const s = ch.sections.find((x) => x.id === Number(sid));
    if (s) return s;
  }
  return null;
};

/** 上架前校验：全部从数据推导 */
const buildChecks = () => {
  const b = DRAFT.basic;
  const basicOk = Boolean(b.name && b.category);
  const sections = DRAFT.chapters.flatMap((c) => c.sections);
  const withVideo = sections.filter((s) => s.video.status === 'done').length;
  const withQuiz = sections.filter((s) => s.quiz).length;
  return {
    basic: { ok: basicOk, text: basicOk ? '基本信息已填写完整' : '基本信息未填写完整（名称 / 分类必填）' },
    catalog: {
      ok: sections.length > 0,
      text: sections.length ? `课程目录已建立（${DRAFT.chapters.length} 章 ${sections.length} 节）` : '还没有建立课程目录',
    },
    video: {
      ok: sections.length > 0 && withVideo === sections.length,
      warn: withVideo < sections.length,
      text: sections.length ? `视频已绑定（${withVideo} / ${sections.length} 节）` : '暂无小节',
      missing: sections.length - withVideo,
    },
    quiz: {
      ok: true,
      warn: withQuiz < sections.length,
      text: withQuiz ? `${withQuiz} 个小节已配练习题（${sections.length - withQuiz} 节未配，可上架后补）` : '还没有小节配练习题（可上架后补）',
    },
  };
};

// 当前草稿绑定的课程：初始 DRAFT 即「Spring Boot 快速上手」(302) 的编辑底稿
// （DRAFT_COURSE_ID 已上移到 DEFAULT_DRAFT 之后统一定义）

const draftForCourse = (meta) => ({
  id: 8001,
  status: 'draft',
  step: 1,
  basic: {
    name: meta.name,
    category: 'IT 互联网',
    price: 199,
    validDays: 365,
    cover: null,
    intro: '',
  },
  chapters: [
    { id: id(), title: '第 1 章　', sections: [sec('1.1　'), sec('1.2　')] },
  ],
  teachers: [{ id: CURRENT.id, name: CURRENT.name, role: '主讲', dept: '计算机学院', certified: true }],
  publishMode: 'now',
});

/** 换一门课程编辑时：以该课程为底稿出一份编辑草稿。
 *  后端 §13 就绪前，mock 无法还原已上架课程的真实目录 —— 302 用初始演示底稿，
 *  其它课程只带出课程名、目录给空骨架（不假装有内容）；课程后端落地后由这里返回真实数据。 */
export const getCourseDraft = async (courseId) => {
  await delay(200);
  loadSlot(courseId ? Number(courseId) : null);
  return { ...clone(DRAFT), courseId: DRAFT_COURSE_ID, checks: buildChecks() };
};

export const saveBasic = async (data) => {
  await delay(260);
  DRAFT.basic = { ...DRAFT.basic, ...clone(data) };
  persist();
  return { ok: true, nameOk: Boolean(DRAFT.basic.name) };
};

/** 目录整体保存（章 / 小节的增删改排序都收拢成一个数组，简单且不易出错） */
export const saveCatalog = async (chapters) => {
  await delay(280);
  DRAFT.chapters = clone(chapters);
  persist();
  return { ok: true };
};

export const setSectionVideo = async (sid, video) => {
  await delay(160);
  const s = findSection(sid);
  if (!s) throw new Error('小节不存在');
  s.video = clone(video);
  if (video.status === 'done' && video.durationMin) {
    const m = Math.floor(video.durationMin / 60);
    s.duration = `${String(m).padStart(2, '0')}:${String(video.durationMin % 60).padStart(2, '0')}`;
  }
  // 把登记后的时长带回给页面（页面本地副本与 mock 副本不同步，必须显式返回）
  persist();
  return { ok: true, duration: s.duration };
};

export const toggleSectionPreview = async (sid) => {
  await delay(120);
  const s = findSection(sid);
  if (!s) throw new Error('小节不存在');
  s.preview = !s.preview;
  persist();
  return { ok: true, preview: s.preview };
};

export const setSectionQuiz = async (sid, quiz) => {
  await delay(200);
  const s = findSection(sid);
  if (!s) throw new Error('小节不存在');
  s.quiz = clone(quiz);
  persist();
  return { ok: true };
};

export const addTeacher = async (t) => {
  await delay(180);
  if (DRAFT.teachers.some((x) => (t.id ? x.id === t.id : x.name === t.name))) throw new Error('这位讲师已在列表里');
  const teacher = { ...clone(t), id: t.id ?? id(), role: t.role || '主讲', dept: t.dept || '', certified: false };
  DRAFT.teachers.push(teacher);
  persist();
  // 与真实分支同形状：页面靠返回的 teacher 更新本地列表（不依赖 mock 内部状态）
  return { ok: true, teacher };
};

export const removeTeacher = async (tid) => {
  await delay(160);
  const idx = DRAFT.teachers.findIndex((x) => x.id === Number(tid));
  if (idx >= 0) DRAFT.teachers.splice(idx, 1);
  persist();
  return { ok: true };
};

export const setPublishMode = async (mode) => {
  await delay(120);
  DRAFT.publishMode = mode;
  persist();
  return { ok: true };
};

/** 提交上架 —— 校验不通过（基本信息 / 目录）时拒绝；视频 / 配题只警告 */
export const publishCourse = async (mode) => {
  await delay(420);
  DRAFT.publishMode = mode || DRAFT.publishMode;
  const checks = buildChecks();
  if (!checks.basic.ok || !checks.catalog.ok) {
    throw new Error('上架前校验未通过：' + [!checks.basic.ok && '基本信息', !checks.catalog.ok && '课程目录'].filter(Boolean).join('、'));
  }
  DRAFT.status = 'published';
  persist();
  return { ok: true, checks, status: DRAFT.status, publishMode: DRAFT.publishMode };
};

// ---------------------------------------------------------------------------
// 向导的两个「真实来源」的 mock 版
// ---------------------------------------------------------------------------
// 真实分支分别打 /cs/categorys/all 与 /us/teachers/simple。
// 这里给出同形状的演示数据，让 mock 模式下三级联动与讲师选择也能走通
// （否则 dev 默认 USE_MOCK=1 时这两个下拉会是空的）。

/** 课程分类树（结构与 SimpleCategoryVO 一致：id / name / children / level / parentId） */
export const getCategoryTree = async () => {
  await delay(120);
  const l3 = (id, name, parentId) => ({ id, name, children: [], level: 3, parentId });
  const l2 = (id, name, parentId, children) => ({ id, name, children, level: 2, parentId });
  return [
    {
      id: 101,
      name: 'IT互联网',
      level: 1,
      parentId: null,
      children: [
        l2(111, '计算机基础', 101, [l3(211, '操作系统', 111), l3(212, '计算机网络', 111), l3(213, '数据结构', 111)]),
        l2(112, '编程语言', 101, [l3(221, 'Java', 112), l3(222, 'Python', 112), l3(223, 'Go', 112)]),
      ],
    },
    {
      id: 102,
      name: '设计创意',
      level: 1,
      parentId: null,
      children: [l2(141, 'UI设计', 102, [l3(241, 'UI基础', 141), l3(242, 'Figma', 141), l3(243, '界面设计', 141)])],
    },
  ];
};

/** 按账号查教师（mock）。真实分支打 `/us/users/lookup`。
 *  刻意只认几个账号、其余按「查不到」报错 —— 与真实分支同形状：
 *  返回真实用户 id（讲师关系靠 id 关联），查不到就抛错，不返回一个手输的名字。 */
const MOCK_TEACHER_ACCOUNTS = {
  liming: { id: 9001, name: '李明', job: '助教', intro: '软件工程系' },
  chenjing: { id: 9002, name: '陈静', job: '讲师', intro: '设计学院' },
};

export const lookupTeacher = async (keyword) => {
  await delay(200);
  const kw = String(keyword || '').trim();
  if (!kw) throw new Error('请输入对方在平台上的账号（用户名或手机号）');
  const hit = MOCK_TEACHER_ACCOUNTS[kw] || MOCK_TEACHER_ACCOUNTS[kw.toLowerCase()];
  if (!hit) throw new Error(`平台里没有这个账号：${kw}`);
  return { username: kw, ...hit };
};
