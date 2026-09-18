// =============================================================================
// 教师端 · 题库接口 + **适配层**
// -----------------------------------------------------------------------------
// 这一层的职责：拼 URL、mock/真实切换，以及——在真实分支里把后端字段
// **适配成契约形状**再交给页面。页面只 import 这里的函数，返回值形状
// 与 mock 完全一致，所以切换后端时页面代码一行不改。
//
// 适配速查（后端 ↔ 契约）：
//   question.type       1单选/2多选        ↔ 'single' / 'multi'
//   question.difficulty 1/2/3              ↔ 'easy' / 'medium' / 'hard'
//   question.status     1可用/0停用         ↔ 'enabled' / 'disabled'
//   question_detail.answer  "1" 或 "1,3"   ↔ ["A"] / ["A","C"]
//   question_detail.options ["文本",...]    ↔ [{key:'A', text:'文本'}]
//   题干 = question.name（后端口径）        ↔ stem
//   分页参数 pageNo/pageSize               ↔ page / size
//   响应统一包装 R{code, data}             → 取 res.data
//
// ⚠️ 两个已知口径差（联调时对齐，适配层先做最接近的映射）：
//   1. 「所属课程」：后端题目挂 cateId1-3 三级分类，前端 mock 是 301-304 课程体系。
//      现映射 courseId ↔ cateId3，录题的 cateId1/2 用同值占位（后端只校验长度）。
//   2. 「按知识点筛选」：题目↔知识点关联表未建，该筛选暂不生效（契约 §14.3）。
// =============================================================================
import request from '@/utils/request.js';
import { USE_MOCK, EXAM_API_PREFIX as P } from '@/config/teacherApi';
import * as mock from '@/mock/teacher/questions';

// ---- 枚举映射工具 ----
const toType = (t) => (t === 2 ? 'multi' : 'single');
const fromType = (t) => (t === 'multi' ? 2 : 1);
const toDiff = (d) => ({ 1: 'easy', 2: 'medium', 3: 'hard' }[d] || 'medium');
const fromDiff = (d) => ({ easy: 1, medium: 2, hard: 3 }[d] ?? 2);
const toStatus = (s) => (s === 0 ? 'disabled' : 'enabled');
const fromStatus = (s) => (s === 'disabled' ? 0 : 1);
const toLetters = (answer) =>
  String(answer || '')
    .split(',')
    .filter(Boolean)
    .map((n) => {
      const i = Number(n);
      return Number.isInteger(i) && i >= 1 && i <= 26 ? String.fromCharCode(64 + i) : n;
    });
const fromLetters = (letters) => (letters || []).map((l) => String(l.charCodeAt(0) - 64)).join(',');
const unwrap = (res) => res?.data ?? res;

/**
 * 统一请求：成功返回裸 JSON；失败抛出**服务端的中文原因**。
 * exam-service 出错是 HTTP 400/500 + text/plain（如「答案解析长度为5-300」「服务器内部异常」），
 * 默认 axios 只会给「Request failed with status code 400」—— 用户看到的就是这句看不懂的话，
 * 于是以为「保存了但没进库」。所有写操作与详情读取都走这里。
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

/** 后端题目（分页行 / 详情）→ 契约形状 */
function toQuestionContract(raw, detail = null) {
  if (!raw) return raw;
  return {
    id: raw.id,
    type: toType(raw.type),
    stem: raw.name ?? raw.stem ?? '',
    difficulty: toDiff(raw.difficulty),
    // 分页行没有详情，这两个字段预览抽屉走 getQuestion 拿
    options: detail ? (detail.options || []).map((text, i) => ({ key: String.fromCharCode(65 + i), text })) : undefined,
    answer: detail ? toLetters(detail.answer) : undefined,
    analysis: detail?.analysis ?? '',
    // 题目归属课程（question.course_id，联调轮已建列；老数据回退 cateId3 近似）
    courseId: raw.courseId ?? raw.cateId3,
    courseName: raw.courseName || (raw.categories ? raw.categories[raw.categories.length - 1] : ''),
    creatorId: raw.creater,
    creatorName: raw.creatorName ?? raw.updater ?? '',
    status: toStatus(raw.status),
    // 可见范围（P21）：列表行与详情都带（详情靠 QuestionDetailVO.visibility，BeanUtils 自动拷）
    visibility: Number(raw.visibility) === 1 ? 1 : 0,
    knowledgePoints: [], // 题目↔知识点关联表未建，暂恒空（契约 §14.3）
    refCount: raw.useTimes ?? 0,
    answerTimes: raw.answerTimes ?? 0,
    updateTime: raw.updateTime,
  };
}

/** 契约形状 → 后端 QuestionFormDTO（录题 / 改题） */
function toQuestionForm(data) {
  return {
    name: data.stem,
    type: fromType(data.type),
    difficulty: fromDiff(data.difficulty),
    // 课程归属走 question.course_id（教师端录题选的就是课程）
    courseId: data.courseId,
    // ⚠️ 不要用 courseId 占位凑 cateIds！cate_id1/2/3 要的是**分类** id，
    //    塞课程 id 会写出一个不存在的分类 → 列表接口取分类名时 NPE → 题库整页 500
    //    （2026-09-16 踩过，录一道题就把列表打挂）。录题表单没有分类选择，就不传。
    cateIds: [],
    options: (data.options || []).map((o) => o.text),
    answer: fromLetters(data.answer),
    // ⚠️ 空解析必须传 null：后端 @Size(min=5,max=300) 拒空串（长度 0）但放行 null。
    //    传 '' 会让「不填解析」也存不进去（2026-09-16 实测 400「答案解析长度为5-300」）。
    analysis: (data.analysis || '').trim() || null,
    // 可见范围（P21）：0 仅我 / 1 公开到平台。
    // undefined = 不改（新增时后端按「仅我」处理，编辑时保持原值）
    visibility: data.visibility === undefined ? undefined : Number(data.visibility) === 1 ? 1 : 0,
  };
}

/** 缓存身份，scope=mine 翻译成 creater 过滤要用 */
let identityCache = null;
async function me() {
  if (!identityCache) {
    const res = await call({ url: `${P}/questions/identity`, method: 'get' }, '读取身份失败');
    identityCache = res || { id: null };
  }
  return identityCache;
}

/** 当前登录教师身份（含是否绑定学校，决定「全部题目」分段与「出题人」列） */
export const getMyIdentity = async () => {
  if (USE_MOCK) return mock.getMyIdentity();
  const res = await me();
  return { schoolName: '', ...res };
};

/** 课程列表（教师自己的课程）—— 既有接口，直接复用 */
export const listCourses = async () => {
  if (USE_MOCK) return mock.listCourses();
  const res = await call({ url: `/cs/courses/simpleInfo/list`, method: 'get' }, '读取课程列表失败');
  const list = res?.list || res;
  return Array.isArray(list) ? list : [];
};

/** 课程知识点树 —— 录题时只能从这里选（路径已按后端调整为 /es） */
export const getKnowledgePoints = async (courseId) => {
  if (USE_MOCK) return mock.getKnowledgePoints(courseId);
  const res = await call({ url: `${P}/knowledge-points`, method: 'get', params: { courseId } }, '读取知识点失败');
  // 容错：后端异常时全局处理器返回非数组 R，不能让 onMounted 断链
  return Array.isArray(res) ? res.map((k) => ({ id: k.id, name: k.name })) : [];
};

/** 题目分页查询：{ keyword, type, difficulty, knowledgePoint, courseId, status, scope, page, size } */
export const pageQuestions = async (params = {}) => {
  if (USE_MOCK) return mock.pageQuestions(params);
  const { keyword, type, difficulty, knowledgePoint, courseId, status, scope, page, size } = params;
  // 可见范围口径（P17，与后端 QuestionPageQuery.visibilityScope 对齐）：
  //   all（默认）→ visible = 公开的 ∪ 我自己出的   ← 题库「全部题目」：看不到别人的私有题，也不会漏自己的
  //   mine       → 只看我出的                      ← 「我的题目」
  //   public     → 只看公开的                      ← 组卷时「从平台题库搜索」
  //   undefined  → 不过滤
  // ⚠️ 别再用 creater=我去表达「我的题目」：那样拿不到别人的公开题（P17 前的旧口径）。
  const visibilityScope =
    scope === 'mine' ? 'mine' : scope === 'public' ? 'public' : scope === 'all' ? 'visible' : undefined;
  const res = await call(
    {
      url: `${P}/questions/page`,
      method: 'get',
      params: {
        keyword: keyword || undefined,
        types: type ? [fromType(type)] : undefined,
        difficulty: difficulty ? fromDiff(difficulty) : undefined,
        status: status ? fromStatus(status) : undefined,
        courseId: courseId || undefined,
        visibilityScope,
        pageNo: page || 1,
        pageSize: size || 20,
      },
    },
    '读取题库失败',
  );
  return {
    list: (res?.list || []).map((q) => toQuestionContract(q)),
    total: res?.total ?? 0,
    page: page || 1,
    size: size || 20,
  };
};

/** 分段计数：{ all, mine, disabled } —— 后端无 count 接口，由分页总量推导 */
export const countQuestions = async () => {
  if (USE_MOCK) return mock.countQuestions();
  const fetchTotal = async (params) => {
    const res = await call({ url: `${P}/questions/page`, method: 'get', params }, '读取题库失败');
    return res?.total ?? 0;
  };
  // 三个数必须与列表用**同一口径**，否则「全部题目 12」点进去只有 5 条
  const [all, mine, disabled] = await Promise.all([
    fetchTotal({ pageNo: 1, pageSize: 1, visibilityScope: 'visible' }),
    fetchTotal({ pageNo: 1, pageSize: 1, visibilityScope: 'mine' }),
    fetchTotal({ pageNo: 1, pageSize: 1, visibilityScope: 'visible', status: 0 }),
  ]);
  return { all, mine, disabled };
};

/** 题目详情（预览抽屉） */
export const getQuestion = async (id) => {
  if (USE_MOCK) return mock.getQuestion(id);
  const res = await call({ url: `${P}/questions/${id}`, method: 'get' }, '读取题目失败');
  return toQuestionContract(res, res);
};

/** 被多少份试卷引用（停用前的提示） */
export const getQuestionUsage = async (id) => {
  if (USE_MOCK) return mock.getQuestionUsage(id);
  const res = await call({ url: `${P}/questions/${id}/usage`, method: 'get' }, '读取题目引用失败');
  return { usageCount: res?.usageCount ?? 0, papers: res?.papers || [] };
};

/** 新建题目（保存即入库） */
export const createQuestion = async (data) => {
  if (USE_MOCK) return mock.createQuestion(data);
  const res = await call({ url: `${P}/questions`, method: 'post', data: toQuestionForm(data) }, '保存题目失败');
  return { id: res ?? null, ...data };
};

/** 更新题目（只能改自己的题） */
export const updateQuestion = async (id, data) => {
  if (USE_MOCK) return mock.updateQuestion(id, data);
  await call({ url: `${P}/questions/${id}`, method: 'put', data: toQuestionForm(data) }, '保存题目失败');
  return { id, ...data };
};

/**
 * 发布到平台 / 撤回为私有（P17）。
 * - 发布：只改 `visibility` 字段（**引用式共享，不复制内容**），全平台的讲师都能搜到并引用；
 * - 撤回：**已被试卷引用时后端会拒绝**，并把引用它的试卷名一并返回（别人的卷子会少一道题）。
 */
export const setQuestionVisibility = async (id, visibility) =>
  call(
    { url: `${P}/questions/${id}/visibility`, method: 'put', params: { visibility } },
    visibility === 1 ? '发布到平台失败' : '撤回失败',
  );

/** 停用 / 启用（不硬删：历史试卷存的是快照） */
export const setQuestionStatus = async (id, status) => {
  if (USE_MOCK) return mock.setQuestionStatus(id, status);
  await call({ url: `${P}/questions/${id}/status`, method: 'put', params: { status: fromStatus(status) } }, '操作失败');
  return { ok: true };
};

/** 批量操作（只对自己出的题生效） */
export const batchPatchQuestions = async (ids, patch = {}) => {
  if (USE_MOCK) return mock.batchPatchQuestions(ids, patch);
  const body = { ids };
  if (patch.difficulty) body.difficulty = fromDiff(patch.difficulty);
  if (patch.status) body.status = fromStatus(patch.status);
  const res = await call({ url: `${P}/questions/batch`, method: 'put', data: body }, '批量操作失败');
  return { changed: res?.changed ?? 0, skipped: res?.skipped ?? 0 };
};

/** Excel 导入：后端未实现（契约 §4.4 待做）—— 真实分支显式报错，不做假数据 */
export const importQuestions = (file) => {
  if (USE_MOCK) return mock.importQuestions(file);
  return Promise.reject(new Error('Excel 导入接口后端尚未实现（契约 §4.4），敬请期待'));
};
