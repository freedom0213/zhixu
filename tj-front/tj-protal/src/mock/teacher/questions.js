// =============================================================================
// 教师端 · 题库 mock 数据（内存实现）
// -----------------------------------------------------------------------------
// 这份文件同时承担两个角色：
//   1) 前端演示的数据源（后端未就绪时页面能跑）
//   2) **数据契约的可执行版本** —— 字段名与取值就是后端要实现的形状
//
// 契约要点（都是与用户逐条确认过的规则，实现时别走偏）：
//   · 题目 9 个字段：题型 / 题干 / 选项 / 正确答案 / 解析 / 难度 / 知识点 / 所属课程 / 出题人
//   · **没有 score（分值）** —— 分值归试卷，同一道题在不同卷里可以不同分
//   · 只做选择题（single / multi），不做判断题、填空、主观题
//   · 知识点是**课程级数据**，录题时只能从课程的知识点树里选
//   · 题库平台共用一个池子；老师只能编辑自己的题，别人的题只能查看 / 引用
//   · 已被试卷引用的题**只能停用，不能硬删**（历史试卷存的是快照）
//   · Excel 导入碰到已存在的题干 → **跳过**，不覆盖、不新增副本
// =============================================================================

// ---- 当前登录身份（含是否绑定学校：决定「全部题目」分段与「出题人」列是否出现）----
const CURRENT = { id: 42, name: '王老师', boundSchool: true, schoolName: '示例大学' };

// ---- 课程（老师自己的资产，不绑任何机构）----
export const COURSES = [
  { id: 301, name: 'Java 集合与并发编程' },
  { id: 302, name: 'Spring Boot 快速上手' },
  { id: 303, name: 'Vue3 前端工程化' },
  { id: 304, name: 'MySQL 性能优化' },
];

// ---- 知识点树（课程级；录题时只能选，不能现场新建）----
export const KNOWLEDGE = {
  301: [
    { id: 'k3011', name: '集合框架' },
    { id: 'k3012', name: '并发基础' },
    { id: 'k3013', name: 'JVM 内存' },
  ],
  302: [
    { id: 'k3021', name: '自动配置' },
    { id: 'k3022', name: '条件注解' },
    { id: 'k3023', name: '起步依赖' },
  ],
  303: [
    { id: 'k3031', name: '响应式原理' },
    { id: 'k3032', name: '组件通信' },
    { id: 'k3033', name: 'Composition API' },
  ],
  304: [
    { id: 'k3041', name: '索引优化' },
    { id: 'k3042', name: '执行计划' },
    { id: 'k3043', name: '事务与锁' },
  ],
};

const TYPES = ['single', 'multi'];
const DIFFS = ['easy', 'medium', 'hard'];

const q = (
  id,
  type,
  stem,
  options,
  answer,
  analysis,
  difficulty,
  courseId,
  knowledgePoints,
  creatorId,
  usageCount,
  status = 'enabled'
) => ({
  id,
  type,
  stem,
  options: options.map((text, i) => ({ key: String.fromCharCode(65 + i), text })),
  answer,
  analysis,
  difficulty,
  courseId,
  courseName: COURSES.find((c) => c.id === courseId)?.name || '',
  knowledgePoints,
  creatorId,
  creatorName: creatorId === CURRENT.id ? `${CURRENT.name}（我）` : `${['李', '张', '陈'][creatorId % 3]}老师`,
  status,
  usageCount,
  createdAt: `2026-09-${String((id % 27) + 1).padStart(2, '0')} 1${id % 9}:20`,
});

// prettier-ignore
let QUESTIONS = [
  q(1001, 'single', 'JVM 中哪个内存区域通常不会发生垃圾回收？',
    ['程序计数器', '堆区', '方法区', '新生代'], ['A'],
    '程序计数器只记录当前线程执行的字节码行号，占用的内存固定且随线程生命周期，是 JVM 规范中唯一不要求 OOM 的区域。',
    'easy', 301, ['JVM 内存'], 42, 12),
  q(1002, 'multi', '关于 Java 并发包的 Atomic 类，下列说法正确的是？',
    ['基于 CAS 实现无锁更新', '底层依赖 Unsafe 类', '适用于高并发计数场景', '能保证复合操作的原子性'], ['A', 'B', 'C'],
    'Atomic 系列只保证单个变量的读写原子性；i++ 这类复合操作需要用 AtomicInteger#incrementAndGet 才能保证原子。',
    'medium', 301, ['并发基础'], 42, 9),
  q(1003, 'single', 'HashMap 在 JDK 8 中扩容后的链表处理方式是什么？',
    ['头插法', '尾插法', '重新排序', '不做处理'], ['B'],
    'JDK 8 改为尾插法，避免了多线程扩容时形成环形链表导致死循环的问题。',
    'medium', 301, ['集合框架'], 42, 7),
  q(1004, 'single', 'ConcurrentHashMap 在 JDK 8 中如何保证线程安全？',
    ['整表加锁', '分段锁 Segment', 'CAS + synchronized 锁桶', '读写锁'], ['C'],
    'JDK 8 放弃分段锁，改为 CAS 无锁插入 + synchronized 锁单个桶头，锁粒度更细。',
    'hard', 301, ['并发基础', '集合框架'], 42, 4),
  q(1005, 'multi', '下列哪些属于线程池拒绝策略？',
    ['AbortPolicy', 'CallerRunsPolicy', 'DiscardPolicy', 'BlockPolicy'], ['A', 'B', 'C'],
    'JDK 内置四种拒绝策略：Abort、CallerRuns、Discard、DiscardOldest；BlockPolicy 不是 JDK 提供的。',
    'medium', 301, ['并发基础'], 43, 6),
  q(1006, 'single', 'Spring Boot 自动配置的核心注解是？',
    ['@EnableAutoConfiguration', '@SpringBootApplication', '@ComponentScan', '@Configuration'], ['A'],
    '自动配置由 @EnableAutoConfiguration 导入候选配置类，再由 @Conditional 系列条件注解按需装配。',
    'easy', 302, ['自动配置'], 42, 18),
  q(1007, 'multi', '关于 @ConditionalOnMissingBean，下列说法正确的是？',
    ['容器里不存在该类型 Bean 时才生效', '常用于提供兜底默认实现', '已有自定义实现时不覆盖', '与 @Bean 互斥'], ['A', 'B', 'C'],
    '它只判断「不存在才注册」，正好用来做可被用户覆盖的默认实现，与 @Bean 并不互斥。',
    'medium', 302, ['条件注解'], 42, 6),
  q(1008, 'single', 'Spring Boot 默认的配置文件名称是？',
    ['application.yml', 'boot.yml', 'spring.yml', 'config.yml'], ['A'],
    '默认读取 application.properties / application.yml，可用 spring.config.name 修改。',
    'easy', 302, ['自动配置'], 42, 0),
  q(1009, 'single', '起步依赖（starter）本质上是什么？',
    ['一个注解', '一组依赖描述符的聚合', '一个自动配置类', '一个插件'], ['B'],
    'starter 本身几乎不含代码，它把某个场景需要的依赖坐标聚合在一起，由自动配置类完成装配。',
    'easy', 302, ['起步依赖'], 44, 11),
  q(1010, 'multi', 'Spring Boot 外部化配置的优先级，下列高于 application.yml 的有？',
    ['命令行参数', '操作系统环境变量', 'jar 包内的 application.yml', '同目录下的 config/application.yml'], ['A', 'B', 'D'],
    'jar 包内的配置优先级最低；命令行 > 环境变量 > 外部 config 目录 > jar 内。',
    'hard', 302, ['自动配置'], 43, 3),
  q(1011, 'single', 'Vue3 的响应式系统底层依赖什么？',
    ['Object.defineProperty', 'Proxy', 'Object.observe', 'MutationObserver'], ['B'],
    'Vue3 用 Proxy 代理整个对象，解决了 Vue2 无法监听新增属性与数组下标赋值的限制。',
    'easy', 303, ['响应式原理'], 42, 15),
  q(1012, 'multi', '下列哪些是 Vue3 Composition API 的钩子？',
    ['ref', 'reactive', 'computed', 'watch'], ['A', 'B', 'C', 'D'],
    '四者都是 @vue/reactivity 或 vue 包导出的组合式 API；computed 与 watch 同属响应式副作用范畴。',
    'medium', 303, ['Composition API'], 42, 8),
  q(1013, 'single', '父组件向子组件传递数据应该怎么做？',
    ['子组件直接修改父数据', '通过 props 传递', '用 localStorage', '用事件总线传值'], ['B'],
    'props 向下、事件向上是 Vue 的单向数据流约定；子组件不应直接修改 props。',
    'easy', 303, ['组件通信'], 42, 2),
  q(1014, 'single', '为什么 v-for 要绑定唯一的 key？',
    ['为了通过编译校验', '帮助 diff 算法复用节点', '提升网络请求速度', '为了样式作用域'], ['B'],
    'key 是虚拟 DOM diff 时判断节点是否可复用的依据；用索引当 key 在列表顺序变化时会退化成全量重建。',
    'medium', 303, ['组件通信'], 43, 5),
  q(1015, 'multi', '关于 ref 与 reactive 的区别，下列说法正确的是？',
    ['ref 可以包装基本类型', 'reactive 只接受对象', 'ref 需要通过 .value 访问', '两者都可以直接解构而不丢响应性'], ['A', 'B', 'C'],
    'reactive 返回的对象直接解构会丢失响应性，需要配合 toRefs。',
    'medium', 303, ['响应式原理', 'Composition API'], 42, 4),
  q(1016, 'single', 'InnoDB 中聚簇索引的叶子节点存储的是什么？',
    ['主键值', '完整行数据', '行指针', '索引列值'], ['B'],
    'InnoDB 的聚簇索引即主键索引，叶子节点存整行数据，因此二级索引查到主键后还要回表。',
    'medium', 304, ['索引优化'], 42, 9),
  q(1017, 'single', 'EXPLAIN 输出中的 type 字段，下列哪种访问效率最高？',
    ['ALL', 'index', 'range', 'const'], ['D'],
    'type 由差到好：ALL < index < range < ref < eq_ref < const < system；出现 ALL 说明全表扫描。',
    'medium', 304, ['执行计划'], 42, 7),
  q(1018, 'multi', '下列哪些情况会导致索引失效？',
    ['在索引列上使用函数', '以 % 开头的模糊查询', '联合索引未遵循最左前缀', '使用覆盖索引'], ['A', 'B', 'C'],
    '覆盖索引反而会提升性能，且无需回表，不属于失效场景。',
    'hard', 304, ['索引优化'], 44, 5),
  q(1019, 'single', 'MySQL 默认的隔离级别是？',
    ['读未提交', '读已提交', '可重复读', '串行化'], ['C'],
    'InnoDB 默认 RR，并通过 MVCC + 间隙锁在很大程度上避免了幻读。',
    'easy', 304, ['事务与锁'], 42, 1),
  q(1020, 'single', '行锁在什么情况下会升级为表锁？',
    ['查询条件命中索引时', '查询条件未命中索引时', '使用主键更新时', '开启事务时'], ['B'],
    'InnoDB 的行锁是加在索引上的；条件没走索引时无法定位行，会退化为锁全表。',
    'hard', 304, ['事务与锁', '索引优化'], 43, 2),
  q(1021, 'single', 'Composition API 中 watchEffect 与 watch 的主要区别是？',
    ['没有区别', 'watchEffect 自动收集依赖', 'watch 只能监听 ref', 'watchEffect 是异步的'], ['B'],
    'watchEffect 立即执行并自动收集回调内用到的响应式依赖；watch 需要显式指定监听源。',
    'medium', 303, ['Composition API'], 42, 3),
  q(1022, 'multi', 'ThreadLocal 可能导致内存泄漏的原因包括？',
    ['ThreadLocal 被线程强引用', 'value 被 ThreadLocalMap 的 key 弱引用', '线程长期存活', '未调用 remove'], ['C', 'D'],
    'key 是弱引用会被回收，但 value 仍被 Entry 强引用；线程池场景下线程长期存活且不 remove 就会泄漏。',
    'hard', 301, ['并发基础', 'JVM 内存'], 42, 0),
  q(1023, 'single', 'Vite 在开发环境为什么启动很快？',
    ['预打包所有依赖', '基于 ESM 按需编译', '使用 Webpack 5 缓存', '压缩了源码'], ['B'],
    '开发时不打包，浏览器直接以原生 ESM 请求模块，服务端按需转译，所以启动时间与项目体积基本无关。',
    'easy', 303, ['组件通信'], 42, 0, 'disabled'),
  q(1024, 'single', 'Spring Boot 中 @Configuration 与 @Component 的区别是？',
    ['完全相同', '@Configuration 默认使用 CGLIB 代理保证单例', '@Component 性能更高', '@Configuration 不能标注类'], ['B'],
    '@Configuration 默认 proxyBeanMethods=true，类被 CGLIB 增强，方法间调用会返回容器里的同一个 Bean。',
    'medium', 302, ['自动配置'], 42, 4),
];

// ---- 工具 ----
const delay = (ms = 260) => new Promise((resolve) => setTimeout(resolve, ms));

const clone = (v) => JSON.parse(JSON.stringify(v));

const matchKeyword = (item, keyword) => {
  if (!keyword) return true;
  const kw = String(keyword).trim().toLowerCase();
  if (!kw) return true;
  return (
    item.stem.toLowerCase().includes(kw) ||
    item.analysis?.toLowerCase().includes(kw) ||
    item.knowledgePoints.some((k) => k.toLowerCase().includes(kw))
  );
};

const applyFilters = (list, params = {}) => {
  const { keyword, type, difficulty, knowledgePoint, courseId, status, scope } = params;
  return list.filter((item) => {
    if (scope === 'mine' && item.creatorId !== CURRENT.id) return false;
    if (type && item.type !== type) return false;
    if (difficulty && item.difficulty !== difficulty) return false;
    if (status && item.status !== status) return false;
    if (courseId && String(item.courseId) !== String(courseId)) return false;
    if (knowledgePoint && !item.knowledgePoints.includes(knowledgePoint)) return false;
    return matchKeyword(item, keyword);
  });
};

// =============================================================================
// 对外接口（与 @/api/teacher/questions.js 一一对应）
// =============================================================================

/** 当前登录教师身份（含是否绑定学校） */
export const getMyIdentity = async () => {
  await delay(120);
  return clone(CURRENT);
};

/** 课程列表（老师自己的课程） */
export const listCourses = async () => {
  await delay(120);
  return clone(COURSES);
};

/** 某门课程的知识点树（录题时只能从这里选）；不传 courseId 时返回全部课程的并集（筛选用） */
export const getKnowledgePoints = async (courseId) => {
  await delay(120);
  if (!courseId) return clone(Object.values(KNOWLEDGE).flat());
  return clone(KNOWLEDGE[courseId] || []);
};

/** 题目分页查询 */
export const pageQuestions = async (params = {}) => {
  await delay();
  const { page = 1, size = 20 } = params;
  const filtered = applyFilters(QUESTIONS, params).sort((a, b) => b.id - a.id);
  const start = (page - 1) * size;
  return {
    list: clone(filtered.slice(start, start + size)),
    total: filtered.length,
    page,
    size,
  };
};

/** 统计（分段控件的计数） */
export const countQuestions = async () => {
  await delay(120);
  return {
    all: QUESTIONS.length,
    mine: QUESTIONS.filter((x) => x.creatorId === CURRENT.id).length,
    disabled: QUESTIONS.filter((x) => x.status === 'disabled').length,
  };
};

/** 题目详情（预览用，含选项 / 答案 / 解析） */
export const getQuestion = async (id) => {
  await delay(150);
  const found = QUESTIONS.find((x) => x.id === Number(id));
  if (!found) throw new Error('题目不存在');
  return clone(found);
};

/** 该题被多少份试卷引用（停用前提示用） */
export const getQuestionUsage = async (id) => {
  await delay(120);
  const found = QUESTIONS.find((x) => x.id === Number(id));
  return { usageCount: found?.usageCount ?? 0, papers: [] };
};

/** 新建题目（保存即入库，没有「先建库再录题」两个阶段） */
export const createQuestion = async (data) => {
  await delay(320);
  const id = Math.max(...QUESTIONS.map((x) => x.id)) + 1;
  const course = COURSES.find((c) => c.id === Number(data.courseId));
  const item = {
    id,
    ...clone(data),
    courseId: Number(data.courseId),
    courseName: course?.name || '',
    creatorId: CURRENT.id,
    creatorName: `${CURRENT.name}（我）`,
    status: 'enabled',
    usageCount: 0,
    createdAt: new Date().toISOString().slice(0, 16).replace('T', ' '),
  };
  QUESTIONS = [item, ...QUESTIONS];
  return clone(item);
};

/** 更新题目（只能改自己的题） */
export const updateQuestion = async (id, data) => {
  await delay(300);
  const idx = QUESTIONS.findIndex((x) => x.id === Number(id));
  if (idx < 0) throw new Error('题目不存在');
  if (QUESTIONS[idx].creatorId !== CURRENT.id) throw new Error('只能修改自己出的题');
  const course = COURSES.find((c) => c.id === Number(data.courseId ?? QUESTIONS[idx].courseId));
  QUESTIONS[idx] = { ...QUESTIONS[idx], ...clone(data), courseId: course?.id, courseName: course?.name || '' };
  return clone(QUESTIONS[idx]);
};

/**
 * 停用 / 启用。
 * 注意：**不能硬删**——历史试卷存的是快照，但「今后选不到」这件事由 status 控制。
 * 已被引用的题停用前应先调 getQuestionUsage 提示「有 N 份试卷在用」。
 */
export const setQuestionStatus = async (id, status) => {
  await delay(260);
  const idx = QUESTIONS.findIndex((x) => x.id === Number(id));
  if (idx < 0) throw new Error('题目不存在');
  if (QUESTIONS[idx].creatorId !== CURRENT.id) throw new Error('只能修改自己出的题');
  QUESTIONS[idx].status = status;
  return clone(QUESTIONS[idx]);
};

/** 批量操作（改难度 / 改知识点 / 停用）—— 只对自己出的题生效 */
export const batchPatchQuestions = async (ids, patch) => {
  await delay(320);
  const idSet = new Set(ids.map(Number));
  let changed = 0;
  let skipped = 0;
  QUESTIONS = QUESTIONS.map((item) => {
    if (!idSet.has(item.id)) return item;
    if (item.creatorId !== CURRENT.id) {
      skipped += 1;
      return item;
    }
    changed += 1;
    return { ...item, ...clone(patch) };
  });
  return { changed, skipped };
};

/**
 * Excel 导入：逐行校验，不是整份退回。
 * 返回 { success: [...], failed: [...], skipped: [...] }
 *   · failed  —— 该行有错，需改后重传（携带「第几行 / 哪个字段 / 什么问题」）
 *   · skipped —— 题库里已存在相同题干，跳过（不覆盖、不新增副本）
 */
export const importQuestions = async (file) => {
  await delay(600);
  return {
    fileName: file?.name || 'questions.xlsx',
    total: 21,
    success: Array.from({ length: 18 }, (_, i) => ({ row: i + 1, id: 2000 + i })),
    failed: [
      { row: 5, type: '单选题', stem: 'Spring Boot 的核心注解是？', reason: '「正确答案」为空' },
      { row: 8, type: '单选题', stem: '关于配置文件优先级，下列说法错误的是？', reason: '「知识点」不在该课程的知识点里' },
    ],
    skipped: [
      { row: 7, type: '单选题', stem: 'JVM 中哪个内存区域通常不会发生垃圾回收？', reason: '题库里已存在相同题干，已跳过（不覆盖、不新增副本）' },
    ],
  };
};

export { CURRENT, QUESTIONS };

/**
 * 同步取题（**仅供 mock 内部使用**）。
 * 用途：试卷发布时要按引用关系把题目内容冻结成快照 —— 这个动作发生在「服务端」，
 * 所以 mock 里也需要一个不走 Promise 的取题入口，好让 exams mock 能在一次调用里完成。
 * 真实后端不存在这个函数，它是 mock 的实现细节。
 */
export const findQuestionSync = (id) => QUESTIONS.find((x) => x.id === Number(id));
