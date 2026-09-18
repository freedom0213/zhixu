<!--
 * 教师端 · 试卷批改 / 答卷复核（设计稿 T4）
 * -----------------------------------------------------------------------------
 * 题型只有单选/多选（全部客观题），判分在提交时就按快照自动完成 ——
 * 所以这页不是「逐题打分」，而是**复核**：
 *   · 每个学生：学生答案 vs 快照里的正确答案，逐题对错一目了然
 *   · 教师的动作只有一个：复核无误后「确认成绩」（有作答记录的状态闭环）
 * 数据全部读快照（禁止查题库）—— 学生答的是考场上那份卷子，不是题库的现在时。
-->
<template>
  <div class="mk">
    <!-- 页头 -->
    <div class="mk-head">
      <div class="mk-head__text">
        <button class="q-btn" type="button" @click="goBack">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M9.6 3.4 5.2 8l4.4 4.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          返回考试管理
        </button>
        <h2 class="s-h2">{{ exam ? exam.name : '试卷批改' }}</h2>
      </div>
      <button class="q-btn" type="button" @click="goStats">看统计</button>
    </div>

    <p v-if="loading" class="mk-state">加载中…</p>

    <!-- 未发布 -->
    <section v-else-if="!exam?.paperVersion" class="s-card mk-card">
      <p class="mk-state">这场考试还没有发布，没有答卷可批改。</p>
    </section>

    <template v-else>
      <!-- 快照口径条 -->
      <div class="mk-snapshot" role="note">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
          <path d="M7 1.3 12.7 12H1.3L7 1.3Z" stroke="#B26A00" stroke-width="1.4" stroke-linejoin="round" />
          <path d="M7 5.4v3" stroke="#B26A00" stroke-width="1.4" stroke-linecap="round" />
          <circle cx="7" cy="10.4" r="0.8" fill="#B26A00" />
        </svg>
        <span>
          答卷与判分基于试卷快照 <strong>v{{ exam.paperVersion.replace('v', '') }}</strong>——学生答的是考场上那份卷子，
          之后题库的改动不影响这里的对错与分数。
        </span>
      </div>

      <div class="mk-body">
        <!-- 左：学生列表 -->
        <aside class="s-card mk-list">
          <label class="mk-search">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <circle cx="7.1" cy="7.1" r="4.4" stroke="currentColor" stroke-width="1.6" />
              <path d="m10.6 10.6 2.6 2.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
            <input v-model.trim="keyword" type="search" placeholder="搜索学生" aria-label="搜索学生" />
          </label>
          <div class="mk-chips" role="group" aria-label="按状态筛选">
            <button
              v-for="c in statusChips"
              :key="c.value"
              class="q-chip"
              :class="{ 'is-on': status === c.value }"
              type="button"
              @click="status = c.value"
            >
              {{ c.label }} {{ c.count }}
            </button>
          </div>

          <p v-if="!filteredList.length" class="mk-state">没有匹配的学生。</p>
          <button
            v-for="s in filteredList"
            v-else
            :key="s.id"
            class="mk-item"
            :class="{ 'is-on': currentId === s.id }"
            type="button"
            @click="pick(s)"
          >
            <span class="mk-item__avatar">{{ s.studentName.slice(0, 1) }}</span>
            <span class="mk-item__text">
              <span class="mk-item__name">{{ s.studentName }}</span>
              <span class="mk-item__meta">{{ classLabel(s) }} · {{ statusLabel(s) }}</span>
            </span>
            <span class="mk-item__score" :class="{ 'is-absent': s.total === null }">
              {{ s.total === null ? '—' : s.total }}
              <em v-if="s.confirmed">已复核</em>
            </span>
          </button>
        </aside>

        <!-- 右：答卷详情 -->
        <section class="s-card mk-sheet">
          <p v-if="!current" class="mk-state">从左侧选择一名学生查看答卷。</p>
          <template v-else>
            <!-- 答卷头 -->
            <div class="mk-sheet__head">
              <div>
                <p class="mk-sheet__name">{{ current.submission.studentName }}</p>
                <p class="mk-sheet__meta">
                  <template v-if="current.submission.className">{{ current.submission.className }} · </template>
                  学号 {{ current.submission.studentId ?? '—' }}
                  <template v-if="current.submission.submittedAt"> · 交于 {{ current.submission.submittedAt }}</template>
                  <template v-if="current.submission.durationMin"> · 用时 {{ current.submission.durationMin }} 分钟</template>
                </p>
              </div>
              <div class="mk-sheet__total">
                <strong :class="current.submission.passed ? 'is-pass' : 'is-fail'">{{ current.submission.total }}</strong>
                <span>/ {{ current.submission.fullScore }} · {{ current.submission.passed ? '及格' : '不及格' }}</span>
              </div>
            </div>

            <!-- 逐题 -->
            <div class="mk-q" v-for="q in current.items" :key="q.questionId">
              <div class="mk-q__head">
                <span class="mk-q__no">第 {{ q.order }} 题</span>
                <span class="mk-q__type">{{ TYPE_LABEL[q.type] }} · {{ q.score }} 分</span>
                <span v-for="k in q.knowledgePoints" :key="k" class="mk-q__kp">{{ k }}</span>
                <span class="mk-q__verdict" :class="q.correct ? 'is-right' : 'is-wrong'">
                  {{ q.correct ? '✓ 答对' : q.answered ? '✕ 答错' : '— 未作答' }}
                </span>
              </div>
              <p class="mk-q__stem">{{ q.stem }}</p>
              <div class="mk-q__opts">
                <p
                  v-for="o in q.options"
                  :key="o.key"
                  class="mk-q__opt"
                  :class="{
                    'is-true': q.answer.includes(o.key),
                    'is-pick': q.studentAnswer.includes(o.key),
                    'is-wrong-pick': q.studentAnswer.includes(o.key) && !q.answer.includes(o.key),
                  }"
                >
                  <span class="mk-q__key">{{ o.key }}</span>
                  <span class="mk-q__optText">{{ o.text }}</span>
                  <em v-if="q.answer.includes(o.key)" class="mk-q__flag">正确答案</em>
                  <em v-else-if="q.studentAnswer.includes(o.key)" class="mk-q__flag mk-q__flag--wrong">学生选择</em>
                </p>
              </div>
              <p v-if="!q.correct && q.analysis" class="mk-q__analysis">解析：{{ q.analysis }}</p>
            </div>

            <!-- 复核动作 -->
            <div class="mk-sheet__foot">
              <p class="mk-foot__note">
                客观题已按快照自动判分。确认后这名学生的成绩进入「已复核」，统计页以确认后的成绩为准。
              </p>
              <button
                v-if="current.submission.total !== null && !current.submission.confirmed"
                class="q-btn q-btn--primary"
                type="button"
                :disabled="busy"
                @click="confirm"
              >
                确认成绩
              </button>
              <span v-else-if="current.submission.confirmed" class="mk-confirmed">✓ 已复核</span>
            </div>
          </template>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { listSubmissions, getSubmission, confirmSubmission } from '@/api/teacher/exams';
import { TYPE_LABEL } from '@/config/teacherDict';

const route = useRoute();
const router = useRouter();

const loading = ref(true);
const exam = ref(null);
const summary = ref({});
const list = ref([]);
const keyword = ref('');
const status = ref('');
const current = ref(null);
const busy = ref(false);

// 筛选条：后端没有「缺考」这个数据源（缺考要靠选课名单推算，考试服务里没有这张表），
// 所以这里只列真实存在的两种状态：已交 / 进行中（开始作答但还没交卷）。
const statusChips = computed(() => [
  { value: '', label: '全部', count: list.value.length },
  { value: 'graded', label: '已交', count: list.value.filter((s) => s.status === 'graded').length },
  { value: 'doing', label: '进行中', count: list.value.filter((s) => s.status === 'doing').length },
]);

const filteredList = computed(() =>
  list.value.filter((s) => {
    if (status.value && s.status !== status.value) return false;
    if (keyword.value) {
      const kw = keyword.value.toLowerCase();
      return s.studentName.toLowerCase().includes(kw) || s.className.toLowerCase().includes(kw);
    }
    return true;
  }),
);

const statusLabel = (s) => {
  if (s.status === 'doing') return '进行中（未交卷）';
  return s.total === null ? '—' : s.confirmed ? '已复核' : '待复核';
};

/** 班级是学校系统的概念，平台里没有 → 空值显示 — */
const classLabel = (s) => s.className || '—';

const pick = async (s) => {
  currentId.value = s.id;
  current.value = null;
  try {
    current.value = await getSubmission(route.params.id, s.id);
  } catch (e) {
    current.value = null;
  }
};

const currentId = ref(null);

const confirm = async () => {
  if (!current.value) return;
  busy.value = true;
  try {
    await confirmSubmission(route.params.id, current.value.submission.id);
    current.value.submission.confirmed = true;
    const inList = list.value.find((x) => x.id === current.value.submission.id);
    if (inList) inList.confirmed = true;
    summary.value.confirmed = (summary.value.confirmed || 0) + 1;
  } finally {
    busy.value = false;
  }
};

const goBack = () => router.push('/teacher/exams');
const goStats = () => router.push(`/teacher/exams/${route.params.id}/stats`);

// 数据随考试变化；:id 变化时组件实例被复用，必须 watch 重载（否则上一场考试的数据会残留）
const load = async () => {
  loading.value = true;
  current.value = null;
  currentId.value = null;
  try {
    const res = await listSubmissions(route.params.id, {});
    exam.value = res.exam;
    summary.value = res.summary;
    list.value = res.list;
    // 默认选第一个「待复核」的学生，没有就选第一个
    const first = res.list.find((x) => x.total !== null && !x.confirmed) || res.list[0];
    if (first) await pick(first);
  } catch (e) {
    router.replace('/teacher/exams');
    return;
  }
  loading.value = false;
};

onMounted(load);
watch(() => route.params.id, (nv, ov) => {
  if (nv && nv !== ov) load();
});
</script>

<style lang="scss" scoped>
.mk {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1096px;
}

// ---- 页头 ----
.mk-head {
  display: flex;
  align-items: flex-start;
  gap: 16px;

  &__text {
    flex: 1 1 auto;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 10px;
    align-items: flex-start;
  }
}

// ---- 快照口径条 ----
.mk-snapshot {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 10px;
  background: #fff7ec;
  font-size: 12px;
  line-height: 18px;
  color: #8a5a10;

  strong {
    font-weight: 600;
  }
}

.mk-body {
  display: grid;
  grid-template-columns: 328px 1fr;
  gap: 16px;
  align-items: start;
}

// ---- 左栏：学生列表 ----
.mk-list {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 720px;
  overflow-y: auto;
}

.mk-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  height: 36px;
  border-radius: 10px;
  background: #f5f5f7;
  color: #86868b;

  input {
    flex: 1 1 auto;
    min-width: 0;
    border: 0;
    outline: none;
    background: transparent;
    font-size: 13px;
    color: var(--s-ink);

    &::placeholder {
      color: #86868b;
    }
  }
}

.mk-chips {
  display: flex;
  gap: 8px;
}

.mk-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: #f5f5f7;
  }
  &.is-on {
    background: rgba(0, 102, 204, 0.08);
  }

  &__avatar {
    flex: 0 0 32px;
    height: 32px;
    border-radius: 50%;
    background: #e8e8ed;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 600;
    color: #6e6e73;
  }
  &__text {
    flex: 1 1 auto;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  &__name {
    font-size: 13px;
    font-weight: 500;
    color: var(--s-ink);
  }
  &__meta {
    font-size: 11px;
    color: var(--s-ink-3);
  }
  &__score {
    flex: 0 0 auto;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 2px;
    font-size: 15px;
    font-weight: 600;
    color: var(--s-ink);

    em {
      font-size: 10px;
      font-style: normal;
      font-weight: 500;
      color: #1d8a43;
    }
    &.is-absent {
      font-size: 12px;
      color: var(--s-ink-3);
    }
  }
}

// ---- 右栏：答卷 ----
.mk-sheet {
  padding: 24px;
  min-height: 320px;

  &__head {
    display: flex;
    align-items: flex-start;
    gap: 16px;
    padding-bottom: 16px;
    border-bottom: 1px solid #f0f0f2;

    > div:first-child {
      flex: 1 1 auto;
      min-width: 0;
    }
  }
  &__name {
    margin: 0 0 4px;
    font-size: 16px;
    font-weight: 600;
    color: var(--s-ink);
  }
  &__meta {
    margin: 0;
    font-size: 12px;
    color: var(--s-ink-3);
  }
  &__total {
    flex: 0 0 auto;
    display: flex;
    align-items: baseline;
    gap: 6px;

    strong {
      font-size: 28px;
      line-height: 32px;
      font-weight: 600;

      &.is-pass { color: #1d8a43; }
      &.is-fail { color: #c2543a; }
    }
    span {
      font-size: 12px;
      color: var(--s-ink-3);
    }
  }
  &__foot {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-top: 20px;
    padding-top: 16px;
    border-top: 1px solid #f0f0f2;
  }
}

.mk-confirmed {
  font-size: 13px;
  font-weight: 600;
  color: #1d8a43;
}

// ---- 逐题 ----
.mk-q {
  padding: 16px 0;

  & + & {
    border-top: 1px solid #f0f0f2;
  }

  &__head {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 8px;
  }
  &__no {
    font-size: 12px;
    font-weight: 600;
    color: var(--s-ink);
  }
  &__type {
    font-size: 12px;
    color: var(--s-ink-3);
  }
  &__kp {
    padding: 2px 8px;
    border-radius: 999px;
    background: #f5f5f7;
    font-size: 11px;
    color: #6e6e73;
  }
  &__verdict {
    margin-left: auto;
    font-size: 12px;
    font-weight: 600;

    &.is-right { color: #1d8a43; }
    &.is-wrong { color: #c2543a; }
  }
  &__stem {
    margin: 0 0 10px;
    font-size: 14px;
    line-height: 20px;
    color: var(--s-ink);
  }
  &__opts {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  &__opt {
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 0;
    padding: 8px 12px;
    border-radius: 10px;
    background: #fafafc;
    font-size: 13px;
    color: var(--s-ink);

    &.is-true {
      background: #eaf6ee;
    }
    &.is-wrong-pick {
      background: #fbeeea;
    }
  }
  &__key {
    flex: 0 0 22px;
    font-weight: 600;
    color: var(--s-ink-3);
  }
  &__optText {
    flex: 1 1 auto;
    min-width: 0;
  }
  &__flag {
    flex: 0 0 auto;
    font-size: 11px;
    font-style: normal;
    font-weight: 500;
    color: #1d8a43;

    &--wrong {
      color: #c2543a;
    }
  }
  &__analysis {
    margin: 8px 0 0;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink-3);
  }
}

.mk-foot__note {
  flex: 1 1 auto;
  min-width: 0;
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--s-ink-3);
}

.mk-state {
  margin: 0;
  padding: 28px 0;
  text-align: center;
  font-size: 13px;
  color: var(--s-ink-3);
}

.mk-card {
  padding: 20px;
}
</style>
