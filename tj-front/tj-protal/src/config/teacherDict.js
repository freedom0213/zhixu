// =============================================================================
// 教师端字典（题型 / 难度 / 状态）
// -----------------------------------------------------------------------------
// 页面与组件共用，避免同一套文案在两处各写一份（改一处漏一处是本项目踩过的坑）。
// 取值即数据契约里的英文枚举，显示文案只在这里定义。
// =============================================================================

export const QUESTIONS_TYPES = ['single', 'multi'];

export const TYPE_LABEL = {
  single: '单选题',
  multi: '多选题',
};

export const DIFFICULTIES = ['easy', 'medium', 'hard'];

export const DIFF_LABEL = {
  easy: '容易',
  medium: '中等',
  hard: '困难',
};

export const STATUS_LABEL = {
  enabled: '可用',
  disabled: '已停用',
};

// 难度标签配色（只作用于标签文字，不进入按钮/背景——沿用 Apple 式「状态色只出现在状态上」）
export const DIFF_CLASS = {
  easy: 'is-easy',
  medium: 'is-medium',
  hard: 'is-hard',
};

// ---- 考试状态 ----
// 关键区分：**草稿可删、已发布不可删**（已发布的有作答记录）；「批改中」是已发布的下游状态
export const EXAM_STATUS_LABEL = {
  draft: '草稿',
  published: '已发布',
  marking: '批改中',
  closed: '已结束',
};

export const EXAM_STATUSES = ['draft', 'published', 'marking', 'closed'];

// ---- 发布方式 ----
export const PUBLISH_MODES = [
  { value: 'now', label: '立即发布', desc: '学生立刻能在考试列表看到' },
  { value: 'draft', label: '存为草稿', desc: '暂不公开，之后继续编辑' },
  { value: 'scheduled', label: '定时发布', desc: '到开始时间自动公开' },
];

// ---- 组卷方式（本期只保留两种：题库选题 / 现场新建）----
export const PAPER_SOURCES = [
  { value: 'library', label: '从题库选题', desc: '主力方式：筛选 → 勾选 → 进卷' },
  { value: 'manual', label: '现场新建题目', desc: '题库里没有的就地录一道，用的是同一个表单' },
];
