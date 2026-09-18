<!--
 * 教师端 · 新建 / 编辑考试（三步向导 · 设计稿 E1 + E2/E3 + E4）
 * -----------------------------------------------------------------------------
 * 这一页承载整套设计的**主线**：
 *
 *   **题库是活的，试卷是死的。**
 *     ① 组卷时（草稿）—— 存的是**引用**（只记 questionId + 分值）
 *        所以出卷过程中发现某道题写错了，去题库改一下，卷子里就跟着对了
 *     ② 发布时 —— 服务端把题目内容**冻结成快照**（paperVersion / snapshotItems）
 *        之后题库怎么改，这场考试的卷子 / 学生答卷 / 批改 / 统计全部不变
 *
 * 为什么必须在「发布」这一刻切，而不是一开始就复制：
 *   一开始就复制 → 出卷过程中改题得重新选一遍，很烦；
 *   一直用引用   → 事后改题库会静默改写历史考卷，成绩单上的题和学生对不上的题永远对不上。
 *
 * 两条与题库共用的规则：
 *   · 分值**只在试卷侧**出现（题库里没有分值）——同一道题在不同卷里可以不同分
 *   · 现场新建题目**复用题库的录题表单**，不做第二套（避免两处表单逐渐长歪）
-->
<template>
  <div class="w">
    <!-- 页头 -->
    <div class="w-head">
      <div class="w-head__text">
        <button class="w-back" type="button" @click="goBack">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M9.6 3.4 5.2 8l4.4 4.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          返回考试管理
        </button>
        <h2 class="s-h2">{{ isEdit ? '编辑考试' : '新建考试' }}</h2>
        <p class="w-head__sub">{{ stepSub }}</p>
      </div>
      <div class="w-head__actions">
        <button class="q-btn" type="button" :disabled="busy" @click="saveDraft">存草稿</button>
        <button v-if="step < 3" class="q-btn q-btn--primary" type="button" @click="goStep(step + 1)">下一步</button>
        <button v-else class="q-btn q-btn--primary" type="button" :disabled="busy || !canPublish" @click="publish">
          {{ busy ? '发布中…' : '发布并通知学生' }}
        </button>
      </div>
    </div>

    <!-- 步骤条 -->
    <ol class="w-steps">
      <li v-for="(s, i) in steps" :key="s.no" class="w-steps__item">
        <button class="w-steps__btn" type="button" @click="goStep(i + 1)">
          <span class="w-steps__dot" :class="{ 'is-done': step > i + 1, 'is-on': step === i + 1 }">
            {{ step > i + 1 ? '✓' : s.no }}
          </span>
          <span class="w-steps__label" :class="{ 'is-on': step === i + 1 }">{{ s.label }}</span>
        </button>
        <span v-if="i < steps.length - 1" class="w-steps__line"></span>
      </li>
    </ol>

    <!-- ============ 步骤 ① 考试信息 ============ -->
    <section v-if="step === 1" class="s-card w-card">
      <h3 class="w-card__title">考试信息</h3>
      <p class="w-card__desc">先定这份卷子是什么：考哪门课、考多久、多少分算通过。</p>
      <div class="w-hr"></div>

      <div class="w-grid">
        <div class="w-field">
          <p class="w-label">考试名称 <span class="w-req">*</span></p>
          <input v-model.trim="form.name" class="w-input" type="text" placeholder="例如：Java 集合与并发编程 · 期中测试" aria-label="考试名称" />
        </div>
        <div class="w-field">
          <p class="w-label">关联课程 <span class="w-req">*</span></p>
          <select v-model="form.courseId" class="w-select" aria-label="关联课程" @change="onCourseChange">
            <option value="">请选择课程</option>
            <option v-for="c in courses" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </div>
        <div class="w-field">
          <p class="w-label">考试类型</p>
          <select v-model="form.examType" class="w-select" aria-label="考试类型">
            <option value="formal">正式考试（计入成绩）</option>
            <option value="practice">随堂练习（不计入成绩）</option>
          </select>
        </div>
        <div class="w-field">
          <p class="w-label">及格线 <span class="w-req">*</span></p>
          <input v-model.number="form.passScore" class="w-input" type="number" min="1" max="100" aria-label="及格线" />
        </div>
        <div class="w-field">
          <p class="w-label">考试时长（分钟） <span class="w-req">*</span></p>
          <input v-model.number="form.duration" class="w-input" type="number" min="5" max="300" aria-label="考试时长" />
        </div>
      </div>

      <div class="w-field">
        <p class="w-label">考试须知 <span class="w-label__note">选填 · 学生进考试前会看到</span></p>
        <textarea v-model.trim="form.notice" class="w-textarea" rows="3" placeholder="例如：开考 30 分钟后不得入场；交卷后客观题即时出分。" aria-label="考试须知"></textarea>
      </div>

      <div class="w-hr"></div>
      <div class="w-foot">
        <span class="w-foot__note">下一步：组卷选题。两种方式可以混用，已选的题都在同一份试卷里排序。</span>
        <button class="q-btn q-btn--primary" type="button" @click="goStep(2)">下一步</button>
      </div>
    </section>

    <!-- ============ 步骤 ② 组卷 ============ -->
    <section v-else-if="step === 2" class="w-paper">
      <div class="w-tabs">
        <button
          v-for="s in PAPER_SOURCES"
          :key="s.value"
          class="w-tab"
          :class="{ 'is-on': source === s.value }"
          type="button"
          @click="source = s.value"
        >
          {{ s.label }}
        </button>
        <span class="w-tabs__sum">已选 {{ selected.length }} 题 · 合计 {{ totalScore }} 分</span>
      </div>

      <!-- 方式 A：从题库选题（主力） -->
      <div v-if="source === 'library'" class="s-card w-workspace">
        <!-- 左：筛选 -->
        <aside class="w-filters">
          <div class="w-filter-group">
            <p class="w-filter-title">题型</p>
            <button
              v-for="opt in typeOptions"
              :key="opt.value || 'all'"
              class="w-filter"
              :class="{ 'is-on': pool.type === opt.value }"
              type="button"
              @click="setPool('type', opt.value)"
            >
              {{ opt.label }}
            </button>
          </div>
          <div class="w-filter-group">
            <p class="w-filter-title">难度</p>
            <button
              v-for="opt in diffOptions"
              :key="opt.value || 'all'"
              class="w-filter"
              :class="{ 'is-on': pool.difficulty === opt.value }"
              type="button"
              @click="setPool('difficulty', opt.value)"
            >
              {{ opt.label }}
            </button>
          </div>
          <div class="w-filter-group">
            <p class="w-filter-title">所属课程</p>
            <select
              v-model="pool.courseId"
              class="w-select w-select--sm"
              aria-label="按课程筛选"
              :disabled="pool.mineAll || pool.platform"
              @change="onPoolCourseChange"
            >
              <option value="">全部课程</option>
              <option v-for="c in courses" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </div>
          <!-- P17：默认只看这门课的题。要跨课程复用就显式打开下面两个开关（范围越大越容易选错，所以默认关） -->
          <div class="w-filter-group">
            <p class="w-filter-title">扩大搜索范围</p>
            <label class="w-switch">
              <input v-model="pool.mineAll" type="checkbox" @change="onScopeChange" />
              <span>从我的题库搜索</span>
            </label>
            <label class="w-switch">
              <input v-model="pool.platform" type="checkbox" @change="onScopeChange" />
              <span>从平台题库搜索</span>
            </label>
            <p v-if="pool.mineAll || pool.platform" class="w-filter-hint">{{ scopeHint }}</p>
          </div>
          <div class="w-filter-group">
            <p class="w-filter-title">知识点</p>
            <select v-model="pool.knowledgePoint" class="w-select w-select--sm" aria-label="按知识点筛选" @change="loadPool(1)">
              <option value="">全部知识点</option>
              <option v-for="k in poolKnowledge" :key="k.id" :value="k.name">{{ k.name }}</option>
            </select>
          </div>
        </aside>

        <!-- 中：题目列表 -->
        <div class="w-list">
          <div class="w-list__head">
            <label class="w-search">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                <circle cx="7.1" cy="7.1" r="4.4" stroke="currentColor" stroke-width="1.6" />
                <path d="m10.6 10.6 2.6 2.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
              </svg>
              <input v-model.trim="pool.keyword" type="search" placeholder="搜索题干关键词" aria-label="搜索题干" @keyup.enter="loadPool(1)" />
            </label>
            <button class="q-act" type="button" @click="selectAllOnPage">全选本页</button>
          </div>

          <p v-if="poolLoading" class="w-state">加载中…</p>
          <p v-else-if="!poolList.length" class="w-state">没有符合条件的题目。换个筛选条件，或用「现场新建题目」。</p>

          <label v-for="q in poolList" v-else :key="q.id" class="w-q">
            <input type="checkbox" :checked="isPicked(q.id)" aria-label="选择题目" @change="togglePick(q)" />
            <span class="w-q__body">
              <span class="w-q__stem">{{ q.stem }}</span>
              <span class="w-q__meta">{{ TYPE_LABEL[q.type] }} · {{ DIFF_LABEL[q.difficulty] }} · {{ q.courseName }}</span>
            </span>
          </label>

          <p class="w-list__foot">已加载 {{ poolList.length }} / {{ poolTotal }} 题</p>
        </div>

        <!-- 右：已选清单 -->
        <aside class="w-picked">
          <p class="w-picked__title">已选清单</p>
          <p class="w-picked__sub">{{ selected.length }} 题 · 合计 {{ totalScore }} 分 · 满分 {{ totalScore }}</p>

          <p v-if="!selected.length" class="w-picked__empty">左侧勾选题目即加入试卷。</p>
          <ul v-else class="w-picked__list">
            <li v-for="(item, i) in selected" :key="item.questionId" class="w-picked__item">
              <span class="w-picked__no">{{ i + 1 }}</span>
              <span class="w-picked__text" :title="item.stem">{{ item.stem }}</span>
              <span class="w-picked__scoreWrap">
                <input
                  v-model.number="item.score"
                  class="w-picked__score"
                  type="number"
                  min="1"
                  max="100"
                  :aria-label="`第 ${i + 1} 题的分值`"
                />
                <em>分</em>
              </span>
              <span class="w-picked__ops">
                <button class="w-mini" type="button" :disabled="i === 0" aria-label="上移" @click="move(i, -1)">↑</button>
                <button class="w-mini" type="button" :disabled="i === selected.length - 1" aria-label="下移" @click="move(i, 1)">↓</button>
                <button class="q-act q-act--muted" type="button" @click="unpick(item.questionId)">移除</button>
              </span>
            </li>
          </ul>

          <template v-if="selected.length">
            <div class="w-picked__hr"></div>
            <p class="w-picked__title">试卷结构</p>
            <p v-for="row in structure" :key="row.label" class="w-picked__stat">{{ row.label }}　{{ row.count }} 题　{{ row.score }} 分</p>
          </template>
        </aside>
      </div>

      <!-- 方式 B：现场新建题目 -->
      <div v-else class="s-card w-manual">
        <h3 class="w-card__title">现场新建题目</h3>
        <p class="w-card__desc">
          题库里没有的题，就地录一道。用的是**题库的同一个录题表单**，不做第二套 ——
          否则两处表单会逐渐长歪。保存后自动回到这里并加入试卷（默认 5 分，可在清单里改）。
        </p>
        <p class="w-manual__note">当前已选 {{ selected.length }} 题。跳过去录题时，这里已填的内容会先存成草稿，不用担心丢。</p>
        <button class="q-btn q-btn--primary" type="button" @click="goCreateQuestion">打开录题表单</button>
      </div>

      <div class="w-foot w-foot--card">
        <span class="w-foot__note">
          草稿阶段这里存的是**引用**：之后去题库改题，这份还没发布的卷子会跟着变。
        </span>
        <button class="q-btn" type="button" @click="goStep(1)">上一步</button>
        <button class="q-btn q-btn--primary" type="button" :disabled="!selected.length" @click="goStep(3)">下一步</button>
      </div>
    </section>

    <!-- ============ 步骤 ③ 发布设置 ============ -->
    <section v-else class="s-card w-card">
      <h3 class="w-card__title">发布设置</h3>
      <p class="w-card__desc">定什么时候考、能考几次、题目顺序怎么排，然后发布。发布时会生成试卷快照。</p>
      <div class="w-hr"></div>

      <div class="w-grid">
        <div class="w-field">
          <p class="w-label">开始时间 <span class="w-req">*</span></p>
          <input class="w-input" type="datetime-local" aria-label="开始时间"
                 :value="toPickerValue(form.startAt)"
                 @input="form.startAt = fromPickerValue($event.target.value)" />
        </div>
        <div class="w-field">
          <p class="w-label">结束时间 <span class="w-req">*</span></p>
          <input class="w-input" type="datetime-local" aria-label="结束时间"
                 :value="toPickerValue(form.endAt)"
                 @input="form.endAt = fromPickerValue($event.target.value)" />
        </div>
        <div class="w-field">
          <p class="w-label">允许作答次数</p>
          <select v-model.number="form.attempts" class="w-select" aria-label="作答次数">
            <option :value="1">1 次（交卷后不可重考）</option>
            <option :value="2">2 次</option>
            <option :value="3">3 次</option>
          </select>
        </div>
        <div class="w-field">
          <p class="w-label">题目顺序</p>
          <select v-model="form.shuffleQuestions" class="w-select" aria-label="题目顺序">
            <option :value="true">随机打乱</option>
            <option :value="false">保持原顺序</option>
          </select>
        </div>
        <div class="w-field">
          <p class="w-label">选项顺序</p>
          <select v-model="form.shuffleOptions" class="w-select" aria-label="选项顺序">
            <option :value="false">保持原顺序</option>
            <option :value="true">随机打乱</option>
          </select>
        </div>
      </div>

      <div class="w-field">
        <p class="w-label">发布方式</p>
        <div class="w-modes">
          <button
            v-for="m in PUBLISH_MODES"
            :key="m.value"
            class="w-mode"
            :class="{ 'is-on': form.publishMode === m.value }"
            type="button"
            @click="form.publishMode = m.value"
          >
            <strong>{{ m.label }}</strong>
            <em>{{ m.desc }}</em>
          </button>
        </div>
      </div>

      <div class="w-hr"></div>
      <p class="w-label">发布前校验</p>
      <ul class="w-checks">
        <li v-for="c in checks" :key="c.text" class="w-checks__item" :class="`is-${c.level}`">
          <span class="w-checks__ic" v-html="c.level === 'ok' ? okIcon : warnIcon"></span>
          <span class="w-checks__text">{{ c.text }}</span>
          <span class="w-checks__tag">{{ c.level === 'ok' ? '通过' : '注意' }}</span>
        </li>
      </ul>

      <div class="w-hr"></div>
      <div class="w-foot">
        <span class="w-foot__note">
          发布后服务端会生成快照：之后在题库里改题、停用题目，都不会影响这一场考试。
        </span>
        <button class="q-btn" type="button" @click="goStep(2)">上一步</button>
        <button class="q-btn q-btn--primary" type="button" :disabled="busy || !canPublish" @click="publish">
          {{ busy ? '发布中…' : '发布并通知学生' }}
        </button>
      </div>
    </section>

    <!-- 发布结果 -->
    <div v-if="published" class="q-dialog" role="dialog" aria-modal="true" aria-label="发布结果">
      <div class="q-dialog__panel">
        <h3 class="q-dialog__title">已发布，试卷快照已生成</h3>
        <p class="q-dialog__desc">
          「{{ published.name }}」· 版本 <strong>{{ published.paperVersion }}</strong> · 冻结于 {{ published.snapshotAt }}。<br />
          共 {{ published.items.length }} 题、{{ published.items.reduce((s, x) => s + Number(x.score || 0), 0) }} 分。
          之后在题库里修改这道题，**不会**影响这一场考试。
        </p>
        <div class="q-dialog__foot">
          <button class="q-btn" type="button" @click="published = null">留在本页</button>
          <button class="q-btn q-btn--primary" type="button" @click="goBack">返回考试管理</button>
        </div>
      </div>
    </div>

    <transition name="q-fade">
      <p v-if="notice" class="q-toast" role="status">{{ notice }}</p>
    </transition>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { listCourses, getKnowledgePoints, pageQuestions } from '@/api/teacher/questions';
import { saveExamDraft, publishExam, getExam } from '@/api/teacher/exams';
import { TYPE_LABEL, DIFF_LABEL, DIFFICULTIES, QUESTIONS_TYPES, PUBLISH_MODES, PAPER_SOURCES } from '@/config/teacherDict';

const route = useRoute();
const router = useRouter();

const steps = [
  { no: 1, label: '考试信息' },
  { no: 2, label: '组卷选题' },
  { no: 3, label: '发布设置' },
];

// 跳到录题表单前把向导状态存进 sessionStorage —— 否则来回一趟已填内容全丢
const DRAFT_KEY = 'tianji:exam-wizard-draft';
const PENDING_KEY = 'tianji:exam-pending-question';

const examId = computed(() => (route.query.id ? Number(route.query.id) : null));
const isEdit = computed(() => !!examId.value);

const step = ref(Number(route.query.step) || 1);
const source = ref('library');
const courses = ref([]);
const poolKnowledge = ref([]);
const poolList = ref([]);
const poolTotal = ref(0);
const poolLoading = ref(false);
const selected = ref([]);
const busy = ref(false);
const published = ref(null);
const notice = ref('');
let noticeTimer = null;

const form = reactive({
  name: '',
  courseId: '',
  examType: 'formal',
  passScore: 60,
  duration: 90,
  notice: '',
  startAt: '',
  endAt: '',
  attempts: 1,
  shuffleQuestions: true,
  shuffleOptions: false,
  publishMode: 'now',
});

// -----------------------------------------------------------------------------
// 时间选择（P37）
// -----------------------------------------------------------------------------
// 开始/结束时间原来是**纯文本输入框**，必须手打 "2026-09-20 09:00"，格式写错后端直接报错。
// 改用浏览器原生 datetime-local：点右侧日历图标即可选日期与时分，零新依赖、风格与 .w-input 一致。
// ⚠️ 原生控件要求的值格式是 "YYYY-MM-DDTHH:mm"（带 T），而表单与接口用的是 "YYYY-MM-DD HH:mm"，
//    因此只在「进出控件」这一层做格式搬运，form.startAt / form.endAt 的数据形状完全不变。
const toPickerValue = (v) => (v ? String(v).replace(' ', 'T').slice(0, 16) : '');
const fromPickerValue = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '');

// P17：默认只看「关联课程」的题；两个开关用来**主动扩大范围**（关着的时候范围最小、最不容易选错）
const pool = reactive({
  type: '',
  difficulty: '',
  knowledgePoint: '',
  courseId: '',
  keyword: '',
  mineAll: false,   // 从我的题库搜索（跨课程，我出的全部题）
  platform: false,  // 从平台题库搜索（其他讲师公开到平台的题）
});

const toast = (text) => {
  notice.value = text;
  clearTimeout(noticeTimer);
  noticeTimer = setTimeout(() => {
    notice.value = '';
  }, 2800);
};

const okIcon = `<svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><circle cx="8" cy="8" r="7" fill="#E6F6EC"/><path d="M5.2 8.2 7 10l3.8-3.8" stroke="#1D8A43" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
const warnIcon = `<svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><circle cx="8" cy="8" r="7" fill="#FFF4E0"/><path d="M8 4.6v4.2" stroke="#B26A00" stroke-width="1.6" stroke-linecap="round"/><path d="M8 11.4h.01" stroke="#B26A00" stroke-width="1.8" stroke-linecap="round"/></svg>`;

// ---- 步骤文案 ----
const stepSub = computed(() => {
  if (step.value === 1) return `共 3 步 · 当前第 1 步「考试信息」`;
  if (step.value === 2) return `共 3 步 · 当前第 2 步「组卷选题」—— 草稿存引用，改题库会同步到这份卷子`;
  return `共 3 步 · 当前第 3 步「发布设置」—— 发布时才冻结快照`;
});

const totalScore = computed(() => selected.value.reduce((sum, x) => sum + Number(x.score || 0), 0));

const structure = computed(() => {
  const map = {};
  selected.value.forEach((x) => {
    const label = TYPE_LABEL[x.type] || '其他';
    if (!map[label]) map[label] = { label, count: 0, score: 0 };
    map[label].count += 1;
    map[label].score += Number(x.score || 0);
  });
  return Object.values(map);
});

const checks = computed(() => {
  const list = [];
  const basicOk = !!(form.name && form.courseId && form.duration && form.passScore);
  list.push({ level: basicOk ? 'ok' : 'warn', text: basicOk ? '基本信息完整，及格线与时长已设置' : '基本信息还有未填项（名称 / 课程 / 及格线 / 时长）' });

  // 时间窗口：**一条提示只说一件事** —— 未设置 / 顺序不对 / 已就绪。
  // （别拆成「已设置 ✓」+「顺序不对 ⚠」两行，那会出现"通过"和"注意"互相打架的观感）
  const timeOk = !!(form.startAt && form.endAt);
  // 格式固定为 "YYYY-MM-DD HH:mm"（零填充），字符串比较等价于时间比较，不必建 Date 对象
  const timeOrderOk = timeOk && form.endAt > form.startAt;
  list.push({
    level: timeOk && timeOrderOk ? 'ok' : 'warn',
    text: !timeOk
      ? '还没设置考试时间窗口'
      : timeOrderOk
        ? `考试时间已设置（${form.startAt} 至 ${form.endAt}）`
        : `结束时间早于开始时间（${form.startAt} → ${form.endAt}），学生进不了考场`,
  });

  list.push({
    level: selected.value.length ? 'ok' : 'warn',
    text: selected.value.length ? `已选 ${selected.value.length} 题，合计 ${totalScore.value} 分` : '还没选任何题目',
  });

  if (selected.value.length && totalScore.value !== 100) {
    list.push({ level: 'warn', text: `满分是 ${totalScore.value} 分，不是 100 —— 确认无误再发布` });
  }

  const noAnalysis = selected.value.filter((x) => !x.analysis).length;
  if (noAnalysis) {
    list.push({ level: 'warn', text: `${noAnalysis} 道题没有解析 —— 不影响发布，但学生交卷后看不到讲解` });
  }
  return list;
});

const canPublish = computed(
  () =>
    !!(form.name && form.courseId && form.startAt && form.endAt && selected.value.length) &&
    // 时间顺序不对就挡住发布：放出去也只是一场谁也进不去的考试
    form.endAt > form.startAt
);

// ---- 题库池 ----
const typeOptions = [{ value: '', label: '全部' }, ...QUESTIONS_TYPES.map((t) => ({ value: t, label: TYPE_LABEL[t] }))];
const diffOptions = [{ value: '', label: '全部' }, ...DIFFICULTIES.map((d) => ({ value: d, label: DIFF_LABEL[d] }))];

/**
 * 搜索范围（P17）：
 *   默认            → 本课程范围内（`courseId` 限定），口径是「公开的 ∪ 我的」
 *   我的题库        → 去掉课程限定，只要我出的（跨课程复用）
 *   平台题库        → 去掉课程限定，只看其他讲师**公开到平台**的题
 *   两个都开        → 公开的 ∪ 我的（并集）
 */
const poolScope = computed(() => {
  if (pool.mineAll && pool.platform) return 'visible';
  if (pool.mineAll) return 'mine';
  if (pool.platform) return 'public';
  return 'all';
});
const scopeHint = computed(() => {
  if (pool.mineAll && pool.platform) return '已跨课程：平台公开题 + 我出的全部题';
  if (pool.mineAll) return '已跨课程：我出的全部题（可用于别的课程）';
  return '已跨课程：平台公开题（其他讲师发布到平台的题）';
});
const onScopeChange = () => loadPool(1);

const loadPool = async (page = 1) => {
  poolLoading.value = true;
  try {
    const res = await pageQuestions({
      scope: poolScope.value,
      type: pool.type,
      difficulty: pool.difficulty,
      knowledgePoint: pool.knowledgePoint,
      // 扩大范围时课程筛选不参与查询（它只对「本课程」这一档有意义）
      courseId: pool.mineAll || pool.platform ? undefined : pool.courseId,
      keyword: pool.keyword,
      page,
      size: 50,
    });
    poolList.value = res.list;
    poolTotal.value = res.total;
  } catch (e) {
    toast('题库加载失败');
  } finally {
    poolLoading.value = false;
  }
};

const setPool = (key, value) => {
  pool[key] = value;
  loadPool(1);
};

const loadPoolKnowledge = async () => {
  poolKnowledge.value = await getKnowledgePoints(pool.courseId || undefined);
};

const onPoolCourseChange = async () => {
  pool.knowledgePoint = '';
  await loadPoolKnowledge();
  loadPool(1);
};

// 组卷时默认只看「关联课程」的题（跨课程复用靠知识点，但组一份课的卷子时不该被别的课干扰）
const onCourseChange = async () => {
  pool.courseId = form.courseId || '';
  pool.knowledgePoint = '';
  await loadPoolKnowledge();
  if (step.value === 2) loadPool(1);
};

const isPicked = (id) => selected.value.some((x) => x.questionId === id);

const togglePick = (q) => {
  if (isPicked(q.id)) {
    unpick(q.id);
    return;
  }
  // 分值只在试卷侧存在：入卷时给一个默认分，之后在右侧清单里改
  selected.value.push({
    questionId: q.id,
    score: 5,
    type: q.type,
    stem: q.stem,
    difficulty: q.difficulty,
    analysis: q.analysis,
  });
};

const unpick = (id) => {
  selected.value = selected.value.filter((x) => x.questionId !== id);
};

const selectAllOnPage = () => {
  poolList.value.forEach((q) => {
    if (!isPicked(q.id)) togglePick(q);
  });
};

const move = (i, delta) => {
  const target = i + delta;
  if (target < 0 || target >= selected.value.length) return;
  const arr = selected.value.slice();
  [arr[i], arr[target]] = [arr[target], arr[i]];
  selected.value = arr;
};

// ---- 跳去录题表单（同一表单，不做第二套）----
const goCreateQuestion = () => {
  sessionStorage.setItem(DRAFT_KEY, JSON.stringify({ form: { ...form }, selected: selected.value, step: 2 }));
  const back = `/teacher/exams/new?resume=1${examId.value ? `&id=${examId.value}` : ''}`;
  router.push({ path: '/teacher/questions/new', query: { from: 'exam', back } });
};

// ---- 步骤切换 ----
const goStep = (n) => {
  if (n === 2 && !form.name) {
    toast('先填考试名称');
    return;
  }
  if (n >= 2 && !form.courseId) {
    toast('先选关联课程');
    return;
  }
  if (n === 3 && !selected.value.length) {
    toast('还没选任何题目');
    return;
  }
  step.value = n;
  if (n === 2) {
    if (!pool.courseId) pool.courseId = form.courseId;
    loadPool(1);
  }
};

// ---- 保存草稿 / 发布 ----
const payload = () => ({
  name: form.name,
  courseId: Number(form.courseId),
  examType: form.examType,
  passScore: Number(form.passScore),
  duration: Number(form.duration),
  notice: form.notice,
  startAt: form.startAt,
  endAt: form.endAt,
  attempts: Number(form.attempts),
  shuffleQuestions: form.shuffleQuestions,
  shuffleOptions: form.shuffleOptions,
  publishMode: form.publishMode,
  items: selected.value.map((x) => ({ questionId: x.questionId, score: Number(x.score || 0) })),
});

const saveDraft = async () => {
  if (!form.name || !form.courseId) {
    toast('先填考试名称与关联课程');
    return;
  }
  busy.value = true;
  try {
    await saveExamDraft(payload(), examId.value);
    sessionStorage.removeItem(DRAFT_KEY);
    toast('草稿已保存');
  } catch (e) {
    toast(e?.message || '保存失败');
  } finally {
    busy.value = false;
  }
};

const publish = async () => {
  if (!canPublish.value) {
    toast('还有必填项没完成，看下面的校验清单');
    return;
  }
  busy.value = true;
  try {
    // 先落一次草稿（拿到 id），再发布 —— 发布是服务端的动作，需要试卷已存在
    const draft = await saveExamDraft(payload(), examId.value);
    const res = await publishExam(draft.id, { ...payload(), publishMode: form.publishMode });
    published.value = res;
    sessionStorage.removeItem(DRAFT_KEY);
    toast('已发布，快照已生成');
  } catch (e) {
    toast(e?.message || '发布失败');
  } finally {
    busy.value = false;
  }
};

const goBack = () => router.push('/teacher/exams');

// ---- 初始化 ----
// 注意：从「新建」跳「编辑」只是 query 变化（?id=9004），vue-router 会**复用组件实例**，
// onMounted 不会再跑 —— 所以抽成 init()，并在 examId 变化时重新初始化。
const init = async () => {
  courses.value = await listCourses();

  // 从录题表单回来：先把草稿恢复，再把刚录的题追加进已选清单
  if (route.query.resume === '1') {
    try {
      const raw = sessionStorage.getItem(DRAFT_KEY);
      if (raw) {
        const saved = JSON.parse(raw);
        Object.assign(form, saved.form || {});
        selected.value = saved.selected || [];
      }
    } catch (e) {
      /* 草稿损坏就按新建处理，不阻断 */
    }
  }

  if (examId.value) {
    try {
      const exam = await getExam(examId.value);
      Object.assign(form, {
        name: exam.name,
        courseId: exam.courseId,
        examType: exam.examType,
        passScore: exam.passScore,
        duration: exam.duration,
        notice: exam.notice,
        startAt: exam.startAt,
        endAt: exam.endAt,
        attempts: exam.attempts,
        shuffleQuestions: exam.shuffleQuestions,
        shuffleOptions: exam.shuffleOptions,
      });
      selected.value = exam.expandedItems.map((x) => ({
        questionId: x.questionId,
        score: x.score,
        type: x.type,
        stem: x.stem,
        difficulty: x.difficulty,
        analysis: x.analysis,
      }));
    } catch (e) {
      toast('考试加载失败');
    }
  }

  pool.courseId = form.courseId || '';
  await loadPoolKnowledge();

  const pending = sessionStorage.getItem(PENDING_KEY);
  if (pending) {
    sessionStorage.removeItem(PENDING_KEY);
    try {
      const q = JSON.parse(pending);
      if (!isPicked(q.id)) {
        selected.value.push({
          questionId: q.id,
          score: 5,
          type: q.type,
          stem: q.stem,
          difficulty: q.difficulty,
          analysis: q.analysis,
        });
        toast('新录的题已加入试卷（默认 5 分，可在清单里改）');
      }
    } catch (e) {
      /* ignore */
    }
  }

  if (step.value === 2) loadPool(1);
};

onMounted(init);

// 同一组件实例内的 query 跳转（列表「继续编辑」→ 向导、新建 → 编辑）都走这里
watch(examId, () => {
  published.value = null; // 关掉可能还开着的发布结果弹窗
  step.value = Number(route.query.step) || 1;
  init();
});
</script>

<style lang="scss" scoped>
.w {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1096px;
}

// ---- 页头 ----
.w-head {
  display: flex;
  align-items: flex-start;
  gap: 16px;

  &__text {
    flex: 1 1 auto;
    min-width: 0;
  }
  &__sub {
    margin: 6px 0 0;
    font-size: 13px;
    line-height: 18px;
    color: var(--s-ink-3);
  }
  &__actions {
    flex: 0 0 auto;
    display: flex;
    gap: 10px;
  }
}

.w-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 10px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--sa);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}

// ---- 步骤条 ----
.w-steps {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;

  &__item {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  &__btn {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 0;
    border: 0;
    background: none;
    font-family: inherit;
    cursor: pointer;
  }
  &__dot {
    width: 24px;
    height: 24px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: #f0f0f2;
    color: var(--s-ink-2);
    font-size: 12px;
    font-weight: 500;

    &.is-on {
      background: var(--sa);
      color: #fff;
    }
    &.is-done {
      background: #e8f1fc;
      color: var(--sa);
    }
  }
  &__label {
    font-size: 13px;
    line-height: 20px;
    color: #333;

    &.is-on {
      color: var(--sa);
      font-weight: 600;
    }
  }
  &__line {
    width: 32px;
    height: 1px;
    background: #d2d2d7;
  }
}

// ---- 卡片 / 表单 ----
.w-card {
  padding: 28px;

  &__title {
    margin: 0 0 6px;
    font-size: 15px;
    font-weight: 600;
    line-height: 22px;
    color: var(--s-ink);
  }
  &__desc {
    margin: 0;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink-2);
  }
}

.w-hr {
  height: 1px;
  margin: 22px 0;
  background: var(--s-hairline);
}

.w-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 18px;
  margin-bottom: 18px;
}

.w-field {
  min-width: 0;
}

.w-label {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 18px;
  color: var(--s-ink-2);

  &__note {
    font-size: 12px;
    color: var(--s-ink-3);
  }
}

.w-req {
  color: var(--s-danger);
}

.w-input,
.w-select {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 0;
  border-radius: 10px;
  background: var(--s-soft);
  color: var(--s-ink);
  font-size: 13px;
  font-family: inherit;

  &:focus {
    outline: 0;
    background: #fff;
    box-shadow: inset 0 0 0 1.5px var(--sa);
  }
}

.w-select,
.w-input[type='datetime-local'] {
  cursor: pointer;
}

.w-textarea {
  width: 100%;
  padding: 12px 14px;
  border: 0;
  border-radius: 12px;
  background: var(--s-soft);
  color: var(--s-ink);
  font-size: 13px;
  font-family: inherit;
  line-height: 20px;
  resize: vertical;

  &:focus {
    outline: 0;
    background: #fff;
    box-shadow: inset 0 0 0 1.5px var(--sa);
  }
}

.w-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;

  &__note {
    margin-right: auto;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink-3);
  }
  &--card {
    padding: 0 4px;
  }
}

// ---- 步骤 ② 组卷 ----
.w-tabs {
  display: flex;
  align-items: center;
  gap: 10px;

  &__sum {
    margin-left: auto;
    font-size: 13px;
    font-weight: 500;
    color: var(--s-ink);
  }
}

.w-tab {
  height: 36px;
  padding: 0 18px;
  border: 0;
  border-radius: 18px;
  background: var(--s-soft);
  color: #333;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;

  &.is-on {
    background: var(--sa);
    color: #fff;
    font-weight: 500;
  }
}

.w-workspace {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr) 300px;
  gap: 0;
  padding: 0;
  overflow: hidden;
}

.w-filters {
  padding: 20px 16px;
  border-right: 1px solid var(--s-hairline);
}

.w-filter-group + .w-switch {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  font-size: 12px;
  color: var(--s-ink-2);
  cursor: pointer;

  input {
    width: 14px;
    height: 14px;
    accent-color: var(--sa);
    cursor: pointer;
  }
}

.w-filter-hint {
  margin: 4px 0 0;
  font-size: 11px;
  line-height: 16px;
  color: var(--sa);
}

.w-filter-group {
  margin-top: 18px;
}

.w-filter-title {
  margin: 0 0 8px;
  font-size: 12px;
  line-height: 16px;
  color: var(--s-ink-3);
}

.w-filter {
  display: block;
  width: 100%;
  height: 32px;
  margin-bottom: 4px;
  padding: 0 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #333;
  font-size: 13px;
  font-family: inherit;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: var(--s-soft);
  }
  &.is-on {
    background: var(--sa-soft);
    color: var(--sa);
    font-weight: 600;
  }
}

.w-select--sm {
  height: 32px;
  font-size: 12px;
  border-radius: 8px;
}

.w-list {
  padding: 20px;
  min-width: 0;

  &__head {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 8px;
  }
  &__foot {
    margin: 14px 0 0;
    font-size: 12px;
    color: var(--s-ink-3);
  }
}

.w-search {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1 1 auto;
  min-width: 0;
  height: 36px;
  padding: 0 14px;
  border-radius: 18px;
  background: var(--s-soft);
  color: var(--s-ink-3);

  &:focus-within {
    background: #fff;
    box-shadow: inset 0 0 0 1.5px var(--sa);
  }

  input {
    flex: 1 1 auto;
    min-width: 0;
    border: 0;
    outline: 0;
    background: transparent;
    font-size: 13px;
    font-family: inherit;
    color: var(--s-ink);

    &::placeholder {
      color: var(--s-ink-3);
    }
  }
}

.w-q {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 8px;
  border-radius: 8px;
  cursor: pointer;

  &:hover {
    background: var(--s-soft);
  }

  input {
    margin-top: 2px;
    width: 16px;
    height: 16px;
    flex: 0 0 16px;
    accent-color: var(--sa);
    cursor: pointer;
  }

  &__body {
    flex: 1 1 auto;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 3px;
  }
  &__stem {
    font-size: 13px;
    line-height: 20px;
    color: var(--s-ink);
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
  &__meta {
    font-size: 12px;
    line-height: 16px;
    color: var(--s-ink-3);
  }
}

.w-picked {
  padding: 20px;
  background: #fafafc;
  border-left: 1px solid var(--s-hairline);

  &__title {
    margin: 0;
    font-size: 13px;
    font-weight: 600;
    line-height: 20px;
    color: var(--s-ink);
  }
  &__sub {
    margin: 4px 0 12px;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink-3);
  }
  &__empty {
    margin: 0;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink-3);
  }
  &__list {
    margin: 0;
    padding: 0;
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 2px;
    max-height: 300px;
    overflow-y: auto;
  }
  &__item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 4px;
    border-bottom: 1px solid var(--s-divider);
    font-size: 12px;
  }
  &__no {
    flex: 0 0 16px;
    color: var(--s-ink-3);
  }
  &__text {
    flex: 1 1 auto;
    min-width: 0;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    color: #333;
  }
  &__scoreWrap {
    display: flex;
    align-items: center;
    gap: 2px;
    flex: 0 0 auto;

    em {
      font-size: 11px;
      font-style: normal;
      color: var(--s-ink-3);
    }
  }
  &__score {
    width: 40px;
    height: 26px;
    padding: 0 6px;
    border: 0;
    border-radius: 8px;
    background: #fff;
    box-shadow: inset 0 0 0 1px var(--s-hairline);
    font-size: 12px;
    font-family: inherit;
    text-align: center;

    &:focus {
      outline: 0;
      box-shadow: inset 0 0 0 1.5px var(--sa);
    }
  }
  &__ops {
    display: flex;
    align-items: center;
    gap: 4px;
    flex: 0 0 auto;
  }
  &__hr {
    height: 1px;
    margin: 14px 0;
    background: var(--s-hairline);
  }
  &__stat {
    margin: 0 0 4px;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink-2);
  }
}

.w-mini {
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: #f0f0f2;
  color: var(--s-ink-2);
  font-size: 11px;
  font-family: inherit;
  line-height: 1;
  cursor: pointer;

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
  &:hover:not(:disabled) {
    background: #e8e8ed;
  }
}

.w-manual {
  padding: 28px;

  &__note {
    margin: 14px 0 20px;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink-2);
  }
}

.w-state {
  margin: 0;
  padding: 40px 0;
  text-align: center;
  font-size: 13px;
  color: var(--s-ink-3);
}

// ---- 步骤 ③ 发布 ----
.w-modes {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 12px;
}

.w-mode {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  border: 0;
  border-radius: 12px;
  background: var(--s-soft);
  font-family: inherit;
  text-align: left;
  cursor: pointer;

  strong {
    font-size: 13px;
    font-weight: 500;
    color: #333;
  }
  em {
    font-size: 11px;
    font-style: normal;
    line-height: 16px;
    color: var(--s-ink-3);
  }

  &.is-on {
    background: var(--sa-soft);
    box-shadow: inset 0 0 0 1.5px var(--sa);

    strong {
      color: var(--sa);
    }
    em {
      color: #3a3a3c;
    }
  }
}

.w-checks {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 12px;

  &__item {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  &__ic {
    display: flex;
    flex: 0 0 16px;
  }
  &__text {
    flex: 1 1 auto;
    min-width: 0;
    font-size: 13px;
    line-height: 20px;
    color: #333;
  }
  &__tag {
    flex: 0 0 auto;
    font-size: 12px;
    color: var(--s-ink-3);
  }

  &__item.is-ok &__tag {
    color: #1d8a43;
  }
  &__item.is-warn &__tag {
    color: #b26a00;
  }
}

.q-dialog__panel {
  max-width: 480px;
}

.q-toast {
  position: fixed;
  left: 50%;
  bottom: 40px;
  z-index: 80;
  transform: translateX(-50%);
  margin: 0;
  padding: 10px 20px;
  border-radius: 20px;
  background: rgba(29, 29, 31, 0.92);
  color: #fff;
  font-size: 13px;
  line-height: 20px;
}

.q-fade-enter-active,
.q-fade-leave-active {
  transition: opacity 0.2s ease;
}
.q-fade-enter-from,
.q-fade-leave-to {
  opacity: 0;
}
</style>
