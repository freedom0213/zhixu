<!--
 * 教师端 · 我的题库（列表页 · 设计稿 Q2）
 * -----------------------------------------------------------------------------
 * 这一页的定位不是「列表」，而是**题库的唯一维护入口**：
 *   · 两个入口：手动新建题目 / Excel 导入 —— 都落到同一个池子
 *   · 题库默认只有老师自己的题；归属信息完善后才多出「全部题目」（平台范围）的可见范围
 *   · 权限边界：**只能编辑自己出的题**，别人的题只能预览与引用
 *     → 所以列表必须有「出题人」列（引用到错题时得找得到人）
 *   · 停用而非删除：历史试卷存的是快照，停用只影响「今后选不到」
 *   · 知识点是课程级数据，筛选项取自课程维护的知识点树
 *
 * 数据来源：@/api/teacher/questions（mock 与真实后端由 VITE_TEACHER_MOCK 切换）
-->
<template>
  <div class="q">
    <!-- 页头 -->
    <div class="q-head">
      <div class="q-head__text">
        <h2 class="s-h2">我的题库</h2>
        <p class="q-head__sub">{{ headSub }}</p>
      </div>
      <div class="q-head__actions">
        <button class="q-btn" type="button" @click="router.push('/teacher/questions/import')">Excel 导入</button>
        <button class="q-btn q-btn--primary" type="button" @click="router.push('/teacher/questions/new')">
          ＋ 新建题目
        </button>
      </div>
    </div>

    <!-- 分段控件：归属信息未完善时只有「我的题目」，完善后才出现平台范围的「全部题目」 -->
    <div v-if="identity.boundSchool" class="q-seg" role="tablist" aria-label="题库范围">
      <button
        class="q-seg__item"
        :class="{ 'is-on': filters.scope === 'all' }"
        type="button"
        role="tab"
        :aria-selected="filters.scope === 'all'"
        @click="setScope('all')"
      >
        全部题目 {{ counts.all }}
      </button>
      <button
        class="q-seg__item"
        :class="{ 'is-on': filters.scope === 'mine' }"
        type="button"
        role="tab"
        :aria-selected="filters.scope === 'mine'"
        @click="setScope('mine')"
      >
        我的题目 {{ counts.mine }}
      </button>
    </div>

    <!-- 筛选 + 列表 -->
    <section class="s-card q-card">
      <div class="q-filters">
        <label class="q-search">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <circle cx="7.1" cy="7.1" r="4.4" stroke="currentColor" stroke-width="1.6" />
            <path d="m10.6 10.6 2.6 2.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
          </svg>
          <input v-model.trim="filters.keyword" type="search" placeholder="搜索题干关键词" aria-label="搜索题干关键词" @keyup.enter="reload(1)" />
        </label>

        <div class="q-chips" role="group" aria-label="按题型筛选">
          <button
            v-for="chip in typeChips"
            :key="chip.value || 'all'"
            class="q-chip"
            :class="{ 'is-on': filters.type === chip.value }"
            type="button"
            @click="setType(chip.value)"
          >
            {{ chip.label }}
          </button>
        </div>

        <select v-model="filters.difficulty" class="q-select" aria-label="按难度筛选" @change="reload(1)">
          <option value="">全部难度</option>
          <option v-for="d in DIFFICULTIES" :key="d" :value="d">{{ DIFF_LABEL[d] }}</option>
        </select>

        <select v-model="filters.knowledgePoint" class="q-select" aria-label="按知识点筛选" @change="reload(1)">
          <option value="">全部知识点</option>
          <option v-for="k in knowledgeOptions" :key="k.id" :value="k.name">{{ k.name }}</option>
        </select>

        <select v-model="filters.courseId" class="q-select" aria-label="按所属课程筛选" @change="onCourseChange">
          <option value="">全部课程</option>
          <option v-for="c in courses" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>

        <select v-model="filters.status" class="q-select" aria-label="按状态筛选" @change="reload(1)">
          <option value="">全部状态</option>
          <option value="enabled">可用</option>
          <option value="disabled">已停用</option>
        </select>
      </div>

      <!-- 表格 -->
      <div class="q-table" :class="{ 'is-bound': identity.boundSchool }">
        <div class="q-row q-row--head">
          <span class="q-c q-c--check">
            <input
              type="checkbox"
              aria-label="全选本页"
              :checked="allChecked"
              :indeterminate.prop="someChecked && !allChecked"
              @change="toggleAll"
            />
          </span>
          <span class="q-c">题干</span>
          <span class="q-c">题型</span>
          <span class="q-c">难度</span>
          <span class="q-c">知识点</span>
          <span class="q-c">所属课程</span>
          <span v-if="identity.boundSchool" class="q-c">出题人</span>
          <span class="q-c">状态</span>
          <span class="q-c q-c--act">操作</span>
        </div>

        <p v-if="loading" class="q-state">加载中…</p>
        <p v-else-if="!list.length" class="q-state">没有符合条件的题目。换个筛选条件，或点右上角「＋ 新建题目」。</p>

        <div v-for="item in list" v-else :key="item.id" class="q-row" :class="{ 'is-off': item.status === 'disabled' }">
          <span class="q-c q-c--check">
            <input type="checkbox" :value="item.id" :checked="selected.includes(item.id)" :aria-label="`选择题目：${item.stem}`" @change="toggleOne(item.id)" />
          </span>
          <span class="q-c q-c--stem" :title="item.stem">
            <em
              class="q-scope"
              :class="{ 'is-public': item.visibility === 1 }"
              :title="item.visibility === 1 ? '全平台讲师都能搜到并引用这道题' : '只有你自己能看到这道题'"
            >{{ item.visibility === 1 ? '公开' : '仅我' }}</em>
            {{ item.stem }}
          </span>
          <span class="q-c">{{ TYPE_LABEL[item.type] }}</span>
          <span class="q-c" :class="DIFF_CLASS[item.difficulty]">{{ DIFF_LABEL[item.difficulty] }}</span>
          <span class="q-c q-c--muted">{{ item.knowledgePoints.join(' / ') || '—' }}</span>
          <span class="q-c q-c--muted" :title="item.courseName">{{ item.courseName }}</span>
          <span v-if="identity.boundSchool" class="q-c q-c--muted">{{ item.creatorName }}</span>
          <span class="q-c">
            <span class="q-tag" :class="item.status === 'enabled' ? 'is-on' : 'is-off'">{{ STATUS_LABEL[item.status] }}</span>
          </span>
          <span class="q-c q-c--act">
            <template v-if="isOwn(item)">
              <button class="q-act" type="button" @click="openPreview(item)">编辑</button>
              <!-- 可见范围（P17）：发布 = 改范围字段（引用式共享，不复制内容）；撤回被引用时后端会拒绝 -->
              <button
                v-if="item.visibility !== 1"
                class="q-act"
                type="button"
                title="发布到平台：全平台讲师都能搜到并引用"
                @click="publishQ(item)"
              >发布</button>
              <button
                v-else
                class="q-act q-act--muted"
                type="button"
                title="撤回为「仅我可见」；已被试卷引用时会被拒绝"
                @click="withdrawQ(item)"
              >撤回</button>
              <button class="q-act q-act--muted" type="button" @click="askToggleStatus(item)">
                {{ item.status === 'enabled' ? '停用' : '启用' }}
              </button>
            </template>
            <template v-else>
              <button class="q-act" type="button" @click="openPreview(item)">预览</button>
              <button class="q-act" type="button" @click="cite(item)">引用</button>
            </template>
          </span>
        </div>
      </div>

      <!-- 分页 -->
      <div class="q-foot">
        <span class="q-foot__info">共 {{ total }} 题 · 每页 {{ size }} 条</span>
        <div class="q-pager">
          <button class="q-pager__btn" type="button" :disabled="page <= 1" @click="reload(page - 1)">‹</button>
          <button
            v-for="p in pageList"
            :key="p"
            class="q-pager__btn"
            :class="{ 'is-on': p === page }"
            type="button"
            @click="reload(p)"
          >
            {{ p }}
          </button>
          <button class="q-pager__btn" type="button" :disabled="page >= pageCount" @click="reload(page + 1)">›</button>
        </div>
      </div>
    </section>

    <!-- 批量操作条 -->
    <div v-if="selected.length" class="s-card q-batch">
      <strong class="q-batch__count">已选 {{ selected.length }} 题</strong>
      <label class="q-batch__field">
        <span>批量改难度</span>
        <select class="q-select q-select--sm" aria-label="批量修改难度" @change="batchSetDifficulty($event.target.value)">
          <option value="">请选择</option>
          <option v-for="d in DIFFICULTIES" :key="d" :value="d">{{ DIFF_LABEL[d] }}</option>
        </select>
      </label>
      <label class="q-batch__field">
        <span>批量改知识点</span>
        <select class="q-select q-select--sm" aria-label="批量修改知识点" @change="batchSetKnowledge($event.target.value)">
          <option value="">请选择</option>
          <option v-for="k in knowledgeOptions" :key="k.id" :value="k.name">{{ k.name }}</option>
        </select>
      </label>
      <button class="q-btn q-btn--sm" type="button" @click="batchDisable">批量停用</button>
      <button class="q-act q-act--muted q-batch__clear" type="button" @click="selected = []">取消选择</button>
      <span class="q-batch__note">改难度 / 停用仅对自己的题生效；别人的题只能引用</span>
    </div>

    <!-- 题目预览抽屉（Q6 的左半） -->
    <QuestionPreview
      v-if="preview"
      :question="preview"
      :own="isOwn(preview)"
      @close="preview = null"
      @edit="goEdit"
      @toggle-status="onPreviewToggleStatus"
      @cite="cite"
    />

    <!-- 停用确认（Q6 的右半）：已被引用的题提示但不阻止；没被用过的直接生效 -->
    <div v-if="confirmTarget" class="q-dialog" role="dialog" aria-modal="true" aria-label="停用确认">
      <div class="q-dialog__panel">
        <h3 class="q-dialog__title">停用这道题？</h3>
        <p class="q-dialog__desc">
          有 <strong>{{ confirmTarget.usageCount }}</strong> 份试卷正在使用它。已发布的试卷存的是快照，不受影响；
          但今后新建试卷时选不到这道题。
        </p>
        <label class="q-dialog__check">
          <input v-model="noPromptAgain" type="checkbox" />
          <span>不再提示</span>
        </label>
        <div class="q-dialog__foot">
          <button class="q-btn" type="button" @click="confirmTarget = null">取消</button>
          <button class="q-btn q-btn--primary" type="button" @click="doToggleStatus">确认停用</button>
        </div>
      </div>
    </div>

    <!-- 轻提示 -->
    <transition name="q-fade">
      <p v-if="notice" class="q-toast" role="status">{{ notice }}</p>
    </transition>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  pageQuestions,
  countQuestions,
  setQuestionVisibility,
  listCourses,
  getKnowledgePoints,
  getMyIdentity,
  setQuestionStatus,
  batchPatchQuestions,
  getQuestionUsage,
} from '@/api/teacher/questions';
import { TYPE_LABEL, DIFF_LABEL, DIFFICULTIES, DIFF_CLASS, STATUS_LABEL } from '@/config/teacherDict';
import QuestionPreview from './components/QuestionPreview.vue';

const route = useRoute();
const router = useRouter();

// ---- 身份与字典 ----
const identity = ref({ id: 0, name: '', boundSchool: false });
const counts = reactive({ all: 0, mine: 0, disabled: 0 });
const courses = ref([]);
const knowledgeOptions = ref([]);

// ---- 列表状态 ----
const filters = reactive({
  keyword: '',
  type: '',
  difficulty: '',
  knowledgePoint: '',
  courseId: '',
  status: '',
  scope: 'all',
});
const list = ref([]);
const total = ref(0);
const page = ref(1);
const size = 20;
const loading = ref(false);
const selected = ref([]);

const preview = ref(null);
const confirmTarget = ref(null);
const noPromptAgain = ref(false);
const notice = ref('');
let noticeTimer = null;

const typeChips = [
  { value: '', label: '全部' },
  { value: 'single', label: '单选' },
  { value: 'multi', label: '多选' },
];

const headSub = computed(() =>
  identity.value.boundSchool
    ? '题库是平台共用的池子：可查看与引用其他老师的题，但只能编辑自己出的题。'
    : '题库默认只显示你自己的题；完善归属信息后可查看并引用平台上其他老师的题（仍只能改自己的）。'
);

const isOwn = (item) => Number(item.creatorId) === Number(identity.value.id);

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size)));
const pageList = computed(() => {
  const count = pageCount.value;
  const cur = page.value;
  const start = Math.max(1, Math.min(cur - 2, count - 4));
  const end = Math.min(count, start + 4);
  const arr = [];
  for (let i = start; i <= end; i += 1) arr.push(i);
  return arr;
});
const allChecked = computed(() => list.value.length > 0 && list.value.every((x) => selected.value.includes(x.id)));
const someChecked = computed(() => selected.value.length > 0);

// ---- 数据加载 ----
const toast = (text) => {
  notice.value = text;
  clearTimeout(noticeTimer);
  noticeTimer = setTimeout(() => {
    notice.value = '';
  }, 2800);
};

const loadKnowledge = async (courseId) => {
  knowledgeOptions.value = await getKnowledgePoints(courseId || undefined);
};

/**
 * 发布到平台 / 撤回（P17）。
 * 撤回被试卷引用的题时后端会拒绝，并把引用它的试卷名一并返回 —— **原话直接给用户**，
 * 不要改写成"操作失败"（那样老师不知道到底卡在哪）。
 */
const publishQ = async (item) => {
  try {
    await setQuestionVisibility(item.id, 1);
    toast('已发布到平台：其他讲师能搜到并引用它');
    await reload();
    Object.assign(counts, await countQuestions());
  } catch (e) {
    toast(e?.message || '发布失败');
  }
};
const withdrawQ = async (item) => {
  try {
    await setQuestionVisibility(item.id, 0);
    toast('已撤回为「仅我可见」');
    await reload();
    Object.assign(counts, await countQuestions());
  } catch (e) {
    toast(e?.message || '撤回失败');
  }
};

const reload = async (targetPage = page.value) => {
  loading.value = true;
  try {
    const res = await pageQuestions({ ...filters, page: targetPage, size });
    list.value = res.list;
    total.value = res.total;
    page.value = res.page;
    // 翻页 / 换筛选后清空选择：跨页保留选择会让「批量停用」误伤看不见的题
    selected.value = [];
  } catch (e) {
    toast('题目加载失败，请稍后重试');
  } finally {
    loading.value = false;
  }
};

const refreshCounts = async () => {
  Object.assign(counts, await countQuestions());
};

const setScope = (scope) => {
  if (filters.scope === scope) return;
  filters.scope = scope;
  reload(1);
};

const setType = (type) => {
  if (filters.type === type) return;
  filters.type = type;
  reload(1);
};

const onCourseChange = async () => {
  // 知识点是课程级数据：换了课程，知识点候选也要跟着换（否则会筛出空结果）
  filters.knowledgePoint = '';
  await loadKnowledge(filters.courseId);
  reload(1);
};

// ---- 行内操作 ----
const openPreview = (item) => {
  preview.value = item;
};

const askToggleStatus = async (item) => {
  if (item.status === 'disabled') {
    await applyStatus(item, 'enabled');
    return;
  }
  // 停用：先看有没有被试卷引用。没被引用的直接生效，不打扰
  const usage = await getQuestionUsage(item.id);
  if (!usage.usageCount || noPromptAgain.value) {
    await applyStatus(item, 'disabled');
    return;
  }
  confirmTarget.value = { ...item, usageCount: usage.usageCount };
};

const onPreviewToggleStatus = async (item) => {
  preview.value = null;
  await askToggleStatus(item);
};

const applyStatus = async (item, status) => {
  try {
    await setQuestionStatus(item.id, status);
    toast(status === 'disabled' ? '已停用，今后新建试卷时选不到这道题' : '已重新启用');
    await Promise.all([reload(), refreshCounts()]);
  } catch (e) {
    toast(e?.message || '操作失败');
  }
};

const doToggleStatus = async () => {
  const target = confirmTarget.value;
  confirmTarget.value = null;
  if (target) await applyStatus(target, 'disabled');
};

const goEdit = (item) => {
  preview.value = null;
  router.push(`/teacher/questions/new?id=${item.id}`);
};

const cite = (item) => {
  preview.value = null;
  // 引用需要「当前正在编辑的试卷」这个上下文，属于阶段 2 的组卷页
  toast(`「${item.stem.slice(0, 12)}…」的引用将在组卷页里完成（阶段 2 落地）`);
};

// ---- 选择与批量 ----
const toggleOne = (id) => {
  const idx = selected.value.indexOf(id);
  if (idx >= 0) selected.value.splice(idx, 1);
  else selected.value.push(id);
};

const toggleAll = () => {
  selected.value = allChecked.value ? [] : list.value.map((x) => x.id);
};

const runBatch = async (patch, doneText) => {
  if (!selected.value.length) return;
  const res = await batchPatchQuestions(selected.value, patch);
  const extra = res.skipped ? `，跳过 ${res.skipped} 道别人的题` : '';
  toast(`${doneText}（已更新 ${res.changed} 道${extra}）`);
  await Promise.all([reload(), refreshCounts()]);
};

const batchSetDifficulty = (value) => {
  if (!value) return;
  runBatch({ difficulty: value }, `已改为「${DIFF_LABEL[value]}」`);
};

const batchSetKnowledge = (value) => {
  if (!value) return;
  runBatch({ knowledgePoints: [value] }, `已改为知识点「${value}」`);
};

const batchDisable = () => {
  runBatch({ status: 'disabled' }, '已停用');
};

// ---- 初始化 ----
onMounted(async () => {
  identity.value = await getMyIdentity();
  // 归属信息未完善时不存在「全部题目」，范围直接锁在「我的题目」
  // ⚠️ 原来这里是 `boundSchool ? 'all' : 'mine'` —— 而 boundSchool 后端**写死为 true**，
  //    所以这道「完善归属信息才能看全部题目」的门槛**从未生效**（P13 §0 读代码纠正）。
  //    现在默认就落「全部题目」，而「全部」的口径是**公开的 ∪ 我的**（P17），隐私由口径保证，不靠门槛。
  filters.scope = 'all';
  courses.value = await listCourses();
  await loadKnowledge();
  await Promise.all([reload(1), refreshCounts()]);
});

// 顶栏搜索会把关键词带到本页（?keyword=）
watch(
  () => route.query.keyword,
  (kw) => {
    if (kw === undefined) return;
    filters.keyword = String(kw || '');
    reload(1);
  }
);
</script>

<style lang="scss" scoped>
.q {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1096px;
}

// ---- 页头 ----
.q-head {
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

// ---- 分段控件 ----
.q-seg {
  align-self: flex-start;
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border-radius: 20px;
  background: #f0f0f2;

  &__item {
    height: 32px;
    padding: 0 16px;
    border: 0;
    border-radius: 16px;
    background: transparent;
    color: var(--s-ink-2);
    font-size: 13px;
    font-family: inherit;
    cursor: pointer;

    &.is-on {
      background: #fff;
      color: var(--s-ink);
      font-weight: 600;
    }
  }
}

// ---- 卡片与筛选 ----
.q-card {
  padding: 24px;
}

.q-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--s-hairline);
}

.q-search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 240px;
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

.q-scope {
  display: inline-block;
  margin-right: 6px;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(16, 24, 40, 0.06);
  color: var(--s-ink-3);
  font-size: 11px;
  font-style: normal;
  line-height: 16px;
  vertical-align: 1px;

  &.is-public {
    background: rgba(0, 102, 204, 0.1);
    color: var(--sa);
  }
}

// ---- 胶囊多选与胶囊下拉 ----
// .q-chips / .q-chip / .q-select 已**上移到全局 style/teacher.scss**：
// 它们原先只在这里的 <scoped> 里定义，导致录题页（questionEdit.vue）用到时完全没有样式
// —— 题型/难度按钮看起来是裸文字、点击无反馈（2026-09-16 用户报「点不动」的真因）。
// 跨页面复用的基础控件只能放全局，别再放回本页 scoped。

// ---- 表格 ----
.q-table {
  --q-cols: 36px minmax(180px, 1fr) 64px 56px 112px 144px 64px 128px;

  &.is-bound {
    --q-cols: 36px minmax(180px, 1fr) 64px 56px 112px 144px 76px 64px 128px;
  }
}

.q-row {
  display: grid;
  grid-template-columns: var(--q-cols);
  align-items: center;
  min-height: 48px;
  border-bottom: 1px solid var(--s-divider);
  font-size: 12px;
  line-height: 18px;
  color: #333;

  &--head {
    min-height: 40px;
    border-bottom-color: var(--s-hairline);
    color: var(--s-ink-3);
  }

  &.is-off {
    background: #fcfcfd;
  }

  input[type='checkbox'] {
    width: 16px;
    height: 16px;
    accent-color: var(--sa);
    cursor: pointer;
  }
}

.q-c {
  padding-right: 8px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;

  &--check {
    display: flex;
    align-items: center;
  }
  &--stem {
    font-size: 13px;
  }
  &--muted {
    color: var(--s-ink-2);
  }
  &--act {
    display: flex;
    align-items: center;
    gap: 10px;
    padding-right: 0;
  }
}

.q-tag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  border-radius: 11px;
  font-size: 11px;
  font-weight: 500;

  &.is-on {
    background: #e6f6ec;
    color: #1d8a43;
  }
  &.is-off {
    background: #f2f2f4;
    color: var(--s-ink-2);
  }
}

.is-easy {
  color: #1d8a43;
}
.is-medium {
  color: #b26a00;
}
.is-hard {
  color: #c0392b;
}

.q-state {
  margin: 0;
  padding: 56px 0;
  text-align: center;
  font-size: 13px;
  color: var(--s-ink-3);
}

// ---- 分页 ----
.q-foot {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 16px;

  &__info {
    font-size: 12px;
    color: var(--s-ink-3);
  }
}

.q-pager {
  margin-left: auto;
  display: flex;
  gap: 6px;

  &__btn {
    min-width: 30px;
    height: 30px;
    padding: 0 8px;
    border: 0;
    border-radius: 8px;
    background: transparent;
    color: var(--s-ink-2);
    font-size: 12px;
    font-family: inherit;
    cursor: pointer;

    &:hover:not(:disabled) {
      background: var(--s-soft);
    }
    &.is-on {
      background: var(--sa);
      color: #fff;
      font-weight: 600;
    }
    &:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
  }
}

// ---- 批量操作条 ----
// 吸底浮起：列表满页时批量条若留在文档末尾会落到首屏之外，
// 用户勾了复选框却看不到任何反馈（实测落在 y=1405 / 视口 900）。
.q-batch {
  position: sticky;
  bottom: 20px;
  z-index: 12;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  padding: 16px 24px;
  box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.055), 0 14px 32px -6px rgba(0, 0, 0, 0.14);

  &__count {
    font-size: 13px;
    color: var(--s-ink);
  }
  &__field {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    color: var(--s-ink-2);
  }
  &__clear {
    margin-left: 4px;
  }
  &__note {
    margin-left: auto;
    font-size: 12px;
    color: var(--s-ink-3);
  }
}

// ---- 停用确认 ----
.q-dialog {
  position: fixed;
  inset: 0;
  z-index: 70;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.28);

  &__panel {
    width: 420px;
    padding: 24px;
    border-radius: 18px;
    background: #fff;
    box-shadow: var(--s-sh-pop);
  }
  &__title {
    margin: 0 0 10px;
    font-size: 15px;
    font-weight: 600;
    color: var(--s-ink);
  }
  &__desc {
    margin: 0 0 16px;
    font-size: 13px;
    line-height: 20px;
    color: var(--s-ink-2);
  }
  &__check {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    color: var(--s-ink-2);

    input {
      width: 16px;
      height: 16px;
      accent-color: var(--sa);
      cursor: pointer;
    }
  }
  &__foot {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    margin-top: 20px;
  }
}

// ---- 轻提示 ----
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
