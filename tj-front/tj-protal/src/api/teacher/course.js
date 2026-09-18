// =============================================================================
// 教师端 · 建课向导接口（契约 §13 / §18）
// -----------------------------------------------------------------------------
// 真实分支（VITE_TEACHER_MOCK=0）已接入后端全部接口。这一层只做两件事：
//   ① 拼 URL + mock/真实切换 + 把服务端错误翻译成人话
//   ② 把后端返回值适配成**契约形状**再交给页面（字段名/单位差异全在这里抹平），
//      所以页面代码在 mock ↔ 真实之间不需要分支。
//
// ⚠️ 本模块后端（course-service / user-service）的响应是**裸 JSON**（不包 R{code,data}），
//    出错时是 HTTP 400 + text/plain 的中文原因（如「上架前校验未通过：基本信息 课程目录」）。
//    默认 axios 只会给「Request failed with status code 400」，页面 toast 出来等于没说 ——
//    所以统一走 call() 把服务端原话抛出来。
//
// 适配速查（后端 ↔ 页面）：
//   basic.thirdCateId + categoryName  ↔  页面 thirdCateId / categoryName（三级分类来自 /cs/categorys/all）
//   basic.introduce / coverUrl        ↔  页面 intro / cover{dataUrl}
//   basic.price（**分**）              ↔  页面价格（元）—— 库里存分，页面填元
//   section.quiz{count}               ↔  页面 quiz{count,totalScore,dist}
//       分值归属试卷（题库不存分值，见契约 §14），重进页面只回显真实题量，
//       总分 / 题型分布显示「—」，不拿 0 / 假分布充数。
//   讲师                               ↔  后端要 teacherId（用户id），页面从 /us/teachers/simple 选真实教师
//   章 / 小节顺序                       ↔  一律按服务端 c_index（保存后必须采纳返回的真实 id 与顺序）
//
// ⚠️ 视频上传仍是「UI + 本地模拟进度」：真上传要腾讯云 VOD 凭证（契约 §13.2，后端待补）。
//    落库的是真实已知的「视频名 + 时长 + 试看」，不是假媒资。
// =============================================================================
import request from '@/utils/request.js';
import { USE_MOCK } from '@/config/teacherApi';
import * as mock from '@/mock/teacher/course';

const C = '/cs'; // course-service
const U = '/us'; // user-service

/**
 * 统一请求：成功返回 body（裸 JSON / 字符串 / 数组都直通）；
 * 失败抛 Error，message 优先取服务端返回的中文原因。
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
  // 兼容个别包了 R{code,data,msg} 的接口（账号 / 题库服务是那种形态）
  if (res && typeof res === 'object' && !Array.isArray(res) && 'code' in res) {
    if (Number(res.code) !== 200) throw new Error(res.msg || fallback);
    return res.data;
  }
  return res;
};

// 当前草稿绑定的课程 id：新建流程里由「保存基本信息」的返回值确定，
// 之后的目录 / 视频 / 配题 / 讲师 / 上架都必须带它 —— 放模块级，页面无需感知。
let currentCourseId = null;

/** 页面偶尔需要知道「当前在编哪门课」（题库选题按课程过滤、上架前刷新校验） */
export const getCurrentCourseId = () => currentCourseId;

const toYuan = (v) => (v == null ? 0 : Math.round(Number(v) / 100));
const toFen = (v) => (v == null ? 0 : Math.round(Number(v) * 100));

// ---- 后端 → 页面 ----

function toSection(raw) {
  const s = raw || {};
  const v = s.video || {};
  const done = v.status === 'done';
  return {
    id: s.id,
    title: s.title || '',
    duration: s.duration ?? null,
    preview: !!s.preview,
    video: {
      status: done ? 'done' : 'none',
      name: v.name || '',
      sizeMB: v.sizeMB ?? 0,
      progress: done ? 100 : 0,
      durationMin: v.durationMin ?? null,
    },
    quiz: s.quiz
      ? {
          count: s.quiz.count ?? 0,
          totalScore: s.quiz.totalScore ?? null,
          dist: Array.isArray(s.quiz.dist) ? s.quiz.dist : [],
        }
      : null,
  };
}

function toDraft(raw) {
  const d = raw || {};
  const b = d.basic || {};
  const coverUrl = b.coverUrl || '';
  const courseId = d.courseId ?? null;
  return {
    courseId,
    // 课程归属人 = 主讲（能编辑这门课的老师就是它的主讲）。前端靠它把「自己」那条标记成不可移除
    ownerId: d.ownerId ?? null,
    status: d.status || 'draft',
    step: d.step || 1,
    publishMode: d.publishMode || 'now',
    basic: {
      courseId,
      name: b.name || '',
      thirdCateId: b.thirdCateId ?? null,
      categoryName: b.categoryName || '',
      category: b.categoryName || '', // 页面模板的兜底展示字段
      price: toYuan(b.price),
      validDays: b.validDays == null ? 365 : b.validDays,
      // 本地预览统一用 {name,dataUrl}；后端只有一串地址，name 用文件名兜底
      cover: coverUrl ? { name: coverUrl.split('/').pop(), dataUrl: coverUrl } : null,
      coverUrl,
      intro: b.introduce || '',
      introduce: b.introduce || '',
      usePeople: b.usePeople || '',
      detail: b.detail || '',
    },
    chapters: (d.chapters || []).map((ch) => ({
      id: ch.id,
      title: ch.title || '',
      sections: (ch.sections || []).map(toSection),
    })),
    teachers: (d.teachers || []).map((t) => ({
      id: t.id,
      name: t.name || '',
      role: t.role || '',
      dept: t.dept || '',
      certified: !!t.certified,
      isShow: t.isShow !== false,
    })),
    checks: d.checks || {},
  };
}

// ---- 页面 → 后端 ----

/** 页面 basic → BasicSaveDTO。封面只接受「真地址」（cover_url 是 varchar(500)，dataURL 存不下） */
function toBasicDTO(data) {
  const coverUrl = data.coverUrl || data.cover?.dataUrl || '';
  const urlish = coverUrl && !coverUrl.startsWith('data:') ? coverUrl : null;
  return {
    courseId: currentCourseId,
    name: data.name,
    thirdCateId: data.thirdCateId,
    coverUrl: urlish,
    price: toFen(data.price),
    validDays: data.validDays,
    introduce: data.intro ?? data.introduce ?? '',
    usePeople: data.usePeople ?? '',
    detail: data.detail ?? '',
  };
}

const toCatalogDTO = (chapters) => ({
  courseId: currentCourseId,
  chapters: (chapters || []).map((ch) => ({
    // 本地占位 id（Date.now()）后端会当新增处理；只有服务端真实 id 才会被复用
    id: ch.id,
    title: ch.title,
    sections: (ch.sections || []).map((s) => ({ id: s.id, title: s.title })),
  })),
});

const requireCourseId = () => {
  if (!currentCourseId) {
    throw new Error('还没有保存基本信息，课程还没创建 —— 请先在第 ① 步填名称与分类');
  }
  return currentCourseId;
};

// ---------------------------------------------------------------------------
// 页面用的接口
// ---------------------------------------------------------------------------

/** 课程草稿（含每步保存的内容 + 上架前校验清单） */
export const getCourseDraft = async (courseId) => {
  if (USE_MOCK) return mock.getCourseDraft(courseId);
  const res = await call(
    {
      url: `${C}/teacher/course-draft`,
      method: 'get',
      params: { courseId: courseId ? Number(courseId) : undefined },
      timeout: 8000,
    },
    '读取课程草稿失败',
  );
  const vo = toDraft(res);
  currentCourseId = vo.courseId;
  return vo;
};

/** 步骤① 基本信息；新建时返回服务端分配的 courseId（后续所有步骤都靠它寻址） */
export const saveBasic = async (data) => {
  if (USE_MOCK) return mock.saveBasic(data);
  const id = await call(
    {
      url: `${C}/teacher/course-draft/basic`,
      method: 'put',
      data: toBasicDTO(data),
      timeout: 10000,
    },
    '保存课程基本信息失败',
  );
  if (id) currentCourseId = id;
  return {
    ok: true,
    courseId: currentCourseId,
    nameOk: Boolean(data.name),
    // 封面是 dataURL（本地预览）时没有真上传通道 —— 如实告诉页面，不假装存上了
    coverStored: !data.cover?.dataUrl || !String(data.cover.dataUrl).startsWith('data:'),
  };
};

/**
 * 步骤② 目录整体保存。
 * 返回服务端**真实 id + 真实顺序**的目录，页面必须采纳（否则后面传视频 / 配题会寻址失配）。
 */
export const saveCatalog = async (chapters) => {
  if (USE_MOCK) return mock.saveCatalog(chapters);
  requireCourseId();
  const res = await call(
    {
      url: `${C}/teacher/course-draft/catalog`,
      method: 'put',
      data: toCatalogDTO(chapters),
      timeout: 10000,
    },
    '保存课程目录失败',
  );
  const vo = toDraft(res);
  return { ok: true, chapters: vo.chapters, checks: vo.checks };
};

/** 步骤③ 小节视频登记（上传完成 / 替换 / 删除后调用）；返回服务端登记的时长文本 */
export const setSectionVideo = async (sid, video) => {
  if (USE_MOCK) return mock.setSectionVideo(sid, video);
  requireCourseId();
  const done = video?.status === 'done';
  const duration = await call(
    {
      url: `${C}/teacher/sections/${sid}/video`,
      method: 'put',
      data: {
        courseId: currentCourseId,
        videoName: done ? video.name : null,
        durationMin: done ? video.durationMin ?? null : 0,
        // P25：真实秒数（优先于 durationMin）—— 5 秒的视频不该被记成 1 分钟
        durationSec: done ? video.durationSec ?? null : null,
        sizeMB: done ? video.sizeMB ?? null : null,
        // P23 · 本地媒资：上传成功后由 media-service 返回的 mediaId（删除视频时为 null → 清空）
        mediaId: done ? video.mediaId ?? null : null,
      },
      timeout: 10000,
    },
    '登记视频信息失败',
  );
  return { ok: true, duration: typeof duration === 'string' ? duration : null };
};

export const toggleSectionPreview = async (sid) => {
  if (USE_MOCK) return mock.toggleSectionPreview(sid);
  requireCourseId();
  const preview = await call(
    {
      url: `${C}/teacher/sections/${sid}/preview`,
      method: 'put',
      params: { courseId: currentCourseId },
      timeout: 8000,
    },
    '切换试看状态失败',
  );
  return { ok: true, preview: !!preview };
};

/**
 * 步骤④ 小节配题。
 * quiz 是页面算出来的展示用汇总（题量 / 总分 / 分布），subjectIds 才是真正要落库的引用 ——
 * 后端只存引用（题目本体在题库），所以这里必须把两道信息一起收。
 */
export const setSectionQuiz = async (sid, quiz, subjectIds = []) => {
  if (USE_MOCK) return mock.setSectionQuiz(sid, quiz);
  requireCourseId();
  await call(
    {
      url: `${C}/teacher/sections/${sid}/quiz`,
      method: 'put',
      data: { courseId: currentCourseId, subjectIds: subjectIds || [] },
      timeout: 10000,
    },
    '保存小节配题失败',
  );
  return { ok: true };
};

/** 步骤⑤ 添加讲师（后端按用户 id 关联，不按姓名） */
export const addTeacher = async (t) => {
  if (USE_MOCK) return mock.addTeacher(t);
  requireCourseId();
  const item = await call(
    {
      url: `${C}/teacher/course-draft/teachers`,
      method: 'post',
      data: { courseId: currentCourseId, teacherId: t.id },
      timeout: 10000,
    },
    '添加讲师失败',
  );
  return {
    ok: true,
    teacher: {
      id: item?.id ?? t.id,
      name: item?.name || t.name || '',
      role: item?.role || t.role || '',
      dept: item?.dept || t.dept || '',
      certified: !!item?.certified,
      isShow: true,
    },
  };
};

export const removeTeacher = async (tid) => {
  if (USE_MOCK) return mock.removeTeacher(tid);
  requireCourseId();
  await call(
    {
      url: `${C}/teacher/course-draft/teachers/${tid}`,
      method: 'delete',
      params: { courseId: currentCourseId },
      timeout: 8000,
    },
    '移除讲师失败',
  );
  return { ok: true };
};

/**
 * 步骤⑤ 提交。
 *   立即上架 → 真调上架接口（草稿搬运到正式表并锁定）
 *   存为草稿 → 什么都不用做（每一步都已增量落库），如实返回草稿状态
 *   定时发布 → 后端没有定时字段（契约 §13）—— 显式拒绝，不让它假装成功
 */
export const publishCourse = async (mode) => {
  if (USE_MOCK) return mock.publishCourse(mode);
  requireCourseId();
  if (mode === 'scheduled') {
    throw new Error('定时发布后端尚未实现（课程没有定时公开字段），请选「立即上架」或「存为草稿」');
  }
  if (mode === 'draft') {
    const vo = await getCourseDraft(currentCourseId);
    return { ok: true, status: 'draft', publishMode: 'draft', checks: vo.checks };
  }
  const checks = await call(
    {
      url: `${C}/teacher/course-draft/publish`,
      method: 'post',
      params: { courseId: currentCourseId },
      timeout: 30000,
    },
    '上架失败',
  );
  return { ok: true, status: 'published', publishMode: 'now', checks };
};

/** 发布方式只是页面的选择，真实语义体现在提交那一刻（后端无独立字段） */
export const setPublishMode = (mode) => (USE_MOCK ? mock.setPublishMode(mode) : Promise.resolve({ ok: true, mode }));

// ---------------------------------------------------------------------------
// 向导需要的两份「真实来源」
// ---------------------------------------------------------------------------

/** 三级分类树（/cs/categorys/all）—— 课程分类必须来自这里，不能写死字符串 */
export const getCategoryTree = async () => {
  if (USE_MOCK) return mock.getCategoryTree();
  const res = await call({ url: `${C}/categorys/all`, method: 'get', timeout: 8000 }, '读取课程分类失败');
  return Array.isArray(res) ? res : [];
};

/**
 * 按账号查一位可授课教师（`/us/users/lookup`）。
 * -----------------------------------------------------------------------------
 * 知序学堂是**面向所有有授课能力的老师**的开放平台，没有「校内教师名册」这种东西 ——
 * 所以这里不是「拉一份教师名录做下拉」，而是把老师**自己填**的账号（用户名 / 手机号）
 * 解析成一个真实用户 id：`course_teacher.teacher_id` 必须是真的，存不进一个手输的名字。
 * 查不到 / 对方不是教师身份 / 账号被禁用 → 抛出后端的中文原话。
 */
export const lookupTeacher = async (keyword) => {
  if (USE_MOCK) return mock.lookupTeacher(keyword);
  const res = await call(
    { url: `${U}/users/lookup`, method: 'get', params: { keyword }, timeout: 8000 },
    '查询账号失败',
  );
  return res || null;
};

/**
 * 视频上传（本地模拟器）。
 * ⚠️ 后端待补：真上传 = ① 向后端要 VOD 临时凭证 ② 直传腾讯云点播 ③ 回调登记。
 * 模拟器只做进度动画与本地登记，接口形状与未来真实现一致。
 */
/**
 * 真上传视频到本地媒资库（P23）：multipart → media-service 存盘 + 登记。
 * 进度来自 axios 的 onUploadProgress（真进度，不再是模拟）。
 * 时长由调用方先用 probeVideoDuration 读出来传入（秒）。
 *
 * @returns {{ promise: Promise<{mediaId: String, durationSec: Float|null}>, cancel: Function }}
 */
export const uploadSectionVideo = (file, durationSec, onProgress) => {
  const form = new FormData();
  form.append('file', file);
  if (durationSec) form.append('durationSec', String(durationSec));
  const controller = new AbortController();
  const promise = request({
    url: '/ms/medias/upload',
    method: 'post',
    data: form,
    timeout: 0, // 视频上传可能较久，不设超时
    signal: controller.signal,
    onUploadProgress: (e) => {
      if (e.total) onProgress?.(Math.min(100, Math.round((e.loaded / e.total) * 100)));
    },
  }).then((res) => {
    const data = res?.data ?? res; // 网关会把裸返回包成 R → 兼容两种
    if (!data?.id) throw new Error(res?.msg || '上传失败，请稍后重试');
    return { mediaId: data.id, durationSec: data.duration ?? durationSec ?? null };
  });
  return { promise, cancel: () => controller.abort() };
};

/** 用 <video> 预读视频元数据拿时长（秒）；读不出就返回 null（登记时按 0 处理） */
export const probeVideoDuration = (file) =>
  new Promise((resolve) => {
    try {
      const url = URL.createObjectURL(file);
      const v = document.createElement('video');
      v.preload = 'metadata';
      v.onloadedmetadata = () => {
        URL.revokeObjectURL(url);
        resolve(Number.isFinite(v.duration) ? v.duration : null);
      };
      v.onerror = () => {
        URL.revokeObjectURL(url);
        resolve(null);
      };
      v.src = url;
    } catch (e) {
      resolve(null);
    }
  });

export const uploadVideoSim = (file, { onProgress } = {}) => {
  const sizeMB = Math.max(1, Math.round((file?.size || 0) / 1024 / 1024) || Math.round(80 + Math.random() * 160));
  const durationMin = 6 + Math.floor(Math.random() * 14);
  let progress = 0;
  const timer = setInterval(() => {
    progress = Math.min(100, progress + 7 + Math.round(Math.random() * 9));
    onProgress?.(progress);
    if (progress >= 100) {
      clearInterval(timer);
      onProgress?.('done');
    }
  }, 180);
  return {
    cancel: () => clearInterval(timer),
    result: {
      status: 'done',
      name: file?.name || 'demo-video.mp4',
      sizeMB,
      progress: 100,
      durationMin,
    },
  };
};
