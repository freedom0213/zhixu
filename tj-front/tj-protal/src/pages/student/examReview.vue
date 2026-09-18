<!--
 * 考试批阅（/student/exams/review?id=...）— 对应设计稿 12
 * 数据契约（只用真实返回字段）：
 *   - getExamDetails(id) → [{correct, answer, score, comment, question:{name,type,options,answer,difficulty,score,analysis}}]
 *     question.type: 1单选 2多选 3不定项 4判断 5主观；difficulty: 1简单 2中等 3困难
 *   - 列表页通过 query 传入 courseName/sectionName/finishTime/duration/score
 *   - 及格线后端未返回，用「正确率」替代设计稿中的「通过/未通过」，不编造判定
 -->
<template>
  <div class="rev">
    <!-- ============ 成绩概览 ============ -->
    <section class="s-card head" v-loading="loading">
      <template v-if="detail">
        <div class="head__score">
          <div class="head__num">{{ head.score ?? '—' }}</div>
          <div class="head__numLabel">得分 / 总分 {{ head.totalScore ?? '—' }}</div>
        </div>
        <div class="head__divider"></div>
        <div class="head__stats">
          <div class="stat">
            <div class="stat__value">{{ head.rightCount }}<em>/ {{ detail.length }}</em></div>
            <div class="stat__label">答对题数</div>
          </div>
          <div class="stat">
            <div class="stat__value">{{ head.accuracy }}<em>%</em></div>
            <div class="stat__label">正确率</div>
          </div>
          <div class="stat" v-if="head.durationText">
            <div class="stat__value">{{ head.durationText }}</div>
            <div class="stat__label">所用时长</div>
          </div>
          <div class="stat" v-if="head.finishTime">
            <div class="stat__value head__time">{{ head.finishTime }}</div>
            <div class="stat__label">提交时间</div>
          </div>
        </div>
        <div class="head__meta">
          <div class="head__title" :title="head.examName">{{ head.examName || '答卷回看' }}</div>
          <div class="head__course" v-if="head.courseName">{{ head.courseName }}</div>
        </div>
        <div class="head__actions">
          <button class="btn btn--ghost" type="button" @click="router.push('/student/exams')">返回列表</button>
        </div>
      </template>
    </section>

    <!-- 读不到答卷时如实说明（例如这场还没交卷） -->
    <p v-if="loadError" class="emptyLine">{{ loadError }}</p>

    <!-- ============ 结果筛选 ============ -->
    <div class="filter" role="tablist" aria-label="结果筛选" v-if="detail && detail.length">
      <button
        v-for="f in filters"
        :key="f.value"
        type="button"
        role="tab"
        :aria-selected="filter === f.value"
        class="filter__item"
        :class="{ 'is-active': filter === f.value }"
        @click="filter = f.value"
      >
        {{ f.label }}
        <span class="filter__count">{{ f.count }}</span>
      </button>
    </div>

    <!-- ============ 逐题卡 ============ -->
    <div class="qs" v-if="detail">
      <article class="s-card q" v-for="(item, index) in shownList" :key="index">
        <div class="q__head">
          <span class="q__badge">{{ item.qIndex + 1 }}</span>
          <span class="q__tag">{{ typeText(item.question.type) }}</span>
          <span class="chip" :class="chipClass(item)">{{ chipText(item) }}</span>
          <span class="q__score" v-if="item.question.score != null">本题 {{ item.question.score }} 分</span>
        </div>

        <h3 class="q__question" v-html="item.question.name"></h3>

        <ul class="q__options">
          <li
            v-for="(opt, oi) in item.question.options || []"
            :key="oi"
            class="opt"
            :class="optClass(item, oi)"
          >
            <span class="opt__mark" aria-hidden="true">
              <!-- 答案标记：✓ / ✕ -->
              <svg v-if="markKind(item, oi) === 'right'" viewBox="0 0 18 18"><circle cx="9" cy="9" r="8.5" fill="currentColor"/><path d="M5.5 9.2 8 11.6l4.5-5" stroke="#fff" stroke-width="1.8" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>
              <svg v-else-if="markKind(item, oi) === 'wrong'" viewBox="0 0 18 18"><circle cx="9" cy="9" r="8.5" fill="currentColor"/><path d="M6 6l6 6M12 6l-6 6" stroke="#fff" stroke-width="1.8" stroke-linecap="round"/></svg>
              <svg v-else viewBox="0 0 18 18"><circle cx="9" cy="9" r="8" fill="none" stroke="currentColor" stroke-width="1.5"/></svg>
            </span>
            <span class="opt__label">{{ upperAlpha(oi) }}. <span v-html="opt"></span></span>
          </li>
        </ul>

        <div class="q__answers">
          <div class="ans"><span class="ans__label">你的答案</span><span class="ans__value" :class="{ 'ans__value--none': !item.answer }">{{ item.answer ? answerText(item.question.type, item.answer) : '未作答' }}</span></div>
          <div class="ans"><span class="ans__label">正确答案</span><span class="ans__value ans__value--right">{{ answerText(item.question.type, item.question.answer) }}</span></div>
          <div class="ans"><span class="ans__label">难易程度</span><span class="ans__value">{{ diffText(item.question.difficulty) }}</span></div>
          <div class="ans" v-if="item.score !== undefined"><span class="ans__label">得分</span><span class="ans__value">{{ item.score }}</span></div>
        </div>

        <div class="analysis" v-if="item.question.analysis">
          <div class="analysis__label">解析</div>
          <div class="analysis__text" v-html="item.question.analysis"></div>
        </div>
        <div class="analysis analysis--comment" v-if="item.comment">
          <div class="analysis__label">教师评语</div>
          <div class="analysis__text" v-html="item.comment"></div>
        </div>
      </article>

      <p class="emptyLine" v-if="shownList.length === 0">该筛选条件下没有题目</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getExamResult } from '@/api/subject.js';
import { upperAlpha, timeFormat } from '@/utils/tool.js';

const route = useRoute();
const router = useRouter();

const loading = ref(true);
const detail = ref([]);
const result = ref(null);
const loadError = ref('');

// 概览：**以服务端返回为准**（route.query 只是跳转时带过来的便利值，缺了也能显示）
const head = computed(() => {
  const r = result.value;
  const list = detail.value || [];
  const total = r?.totalCount ?? list.length;
  const rightCount = r?.correctCount ?? list.filter((it) => it.correct).length;
  const accuracy = total ? Math.round((rightCount * 100) / total) : 0;
  return {
    score: r?.score ?? route.query.score ?? 0,
    totalScore: r?.totalScore ?? list.reduce((sum, it) => sum + Number(it.question?.score || 0), 0),
    rightCount,
    accuracy,
    durationText: r?.duration ? timeFormat(r.duration) : route.query.duration ? timeFormat(route.query.duration) : '',
    finishTime: r?.finishTime || route.query.finishTime || '',
    examName: r?.examName || route.query.examName || '',
    courseName: r?.courseName || route.query.courseName || '',
  };
});

// 筛选：全部 / 答对 / 答错 / 未答（对应设计稿 12 的 4 个 Result Tabs）
const filter = ref('all');
const filters = computed(() => {
  const list = detail.value || [];
  const right = list.filter((it) => it.correct).length;
  const unanswered = list.filter((it) => !it.correct && !it.answer).length;
  const wrong = list.length - right - unanswered;
  return [
    { label: '全部', value: 'all', count: list.length },
    { label: '答对', value: 'right', count: right },
    { label: '答错', value: 'wrong', count: wrong },
    { label: '未答', value: 'none', count: unanswered },
  ];
});

const shownList = computed(() => {
  const list = (detail.value || []).map((it, i) => ({ ...it, qIndex: i }));
  if (filter.value === 'right') return list.filter((it) => it.correct);
  if (filter.value === 'wrong') return list.filter((it) => !it.correct && it.answer);
  if (filter.value === 'none') return list.filter((it) => !it.correct && !it.answer);
  return list;
});

// ---------- 展示辅助 ----------
const typeText = (t) => ({ 1: '单选题', 2: '多选题', 3: '不定项', 4: '判断题', 5: '主观题' }[parseInt(t)] || '题目');
// 难度：后端当前没有这道题的难度快照 → 显示「—」，不猜一个「困难」出来
const diffText = (d) => (d == null || d === '' ? '—' : d == 1 ? '简单' : d == 2 ? '中等' : '困难');

const chipText = (item) => (item.correct ? '答对' : item.answer ? '答错' : '未作答');
const chipClass = (item) => (item.correct ? 'chip--right' : item.answer ? 'chip--wrong' : 'chip--none');

// 选项标记：正确答案=绿、用户答错的选项=红、其余=空心
function markKind(item, oi) {
  const idx = String(oi);
  const isAnswer = selHas(item.question.answer, idx);
  const isMine = selHas(item.answer, idx);
  if (isAnswer && (!item.answer || item.correct)) return 'right';
  if (isMine && !item.correct) return 'wrong';
  if (isAnswer && item.answer && !item.correct) return 'right';
  return 'none';
}
function selHas(answer, idx) {
  if (answer === null || answer === undefined || answer === '') return false;
  const parts = String(answer).split(',');
  return parts.some((p) => p.trim() === idx.trim());
}
function optClass(item, oi) {
  const kind = markKind(item, oi);
  return kind === 'right' ? 'opt--right' : kind === 'wrong' ? 'opt--wrong' : '';
}

const answerText = (type, val) => {
  if (val === null || val === undefined || val === '') return '未作答';
  const t = parseInt(type);
  if (t === 1) return isNaN(Number(val)) ? val : upperAlpha(Number(val));
  if (t === 2 || t === 3) {
    const arr = typeof val === 'string' ? val.split(',') : val;
    return arr.map((n) => (isNaN(Number(n)) ? n : upperAlpha(Number(n)))).join('、');
  }
  if (t === 4) return val === true || val === 'true' || val == 1 ? '正确' : '错误';
  return String(val);
};

/**
 * 读这场考试的成绩。
 * 服务端返回的是**作答时冻结的快照**口径：题目内容 / 标准答案 / 解析都取那一版 ——
 * 所以讲师之后改题库、重新发布，这里看到的仍然是我当时做的那份。
 */
const mapDetails = (res) =>
  (res?.details || []).map((d) => ({
    correct: d.correct === 1,
    answer: d.myAnswer || '',
    score: d.score,
    comment: '',
    question: {
      name: d.name,
      type: d.type,
      options: d.options || [],
      answer: d.correctAnswer || '',
      difficulty: null, // 快照里没有难度字段，如实留空 → 页面显示「—」
      score: d.maxScore,
      analysis: d.analysis || '',
    },
  }));

async function load() {
  loading.value = true;
  loadError.value = '';
  try {
    const res = await getExamResult(route.query.id);
    result.value = res;
    detail.value = mapDetails(res);
  } catch (e) {
    detail.value = [];
    loadError.value = e?.message || '读取答卷失败';
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style lang="scss" scoped>
.rev {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

// ---------- 成绩概览 ----------
.head {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 28px;
  min-height: 120px;

  &__score { flex: 0 0 auto; }
  &__num {
    font-size: 44px;
    line-height: 52px;
    font-weight: 600;
    color: var(--s-ink);
    font-variant-numeric: tabular-nums;
  }
  &__numLabel { margin-top: 4px; font-size: 12px; color: var(--s-ink-2); }
  &__divider { width: 1px; height: 64px; background: var(--s-divider); flex: 0 0 1px; }
  &__stats { display: flex; gap: 40px; flex: 1 1 auto; min-width: 0; flex-wrap: wrap; }
  &__time { font-size: 16px; }
  &__meta {
    min-width: 0;
    max-width: 220px;

    .head__title {
      font-size: 14px;
      font-weight: 600;
      color: var(--s-ink);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .head__course {
      margin-top: 2px;
      font-size: 12px;
      color: var(--s-ink-3);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
  &__actions { margin-left: auto; flex: 0 0 auto; }
}

.stat {
  &__value {
    font-size: 22px;
    font-weight: 600;
    color: var(--s-ink);
    font-variant-numeric: tabular-nums;

    em { font-style: normal; font-size: 13px; font-weight: 400; color: var(--s-ink-3); margin-left: 2px; }
  }
  &__label { margin-top: 4px; font-size: 12px; color: var(--s-ink-3); }
}

.btn {
  height: 40px;
  padding: 0 22px;
  border: 0;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.15s ease;

  &--primary { background: var(--sa); color: #fff; font-weight: 500; }
  &--primary:hover { background: var(--sa-hover); }
  &--ghost { background: var(--s-card); color: var(--s-ink); box-shadow: inset 0 0 0 1px var(--s-divider); }
  &--ghost:hover { background: var(--s-canvas); }
}

// ---------- 筛选 ----------
.filter {
  display: flex;
  gap: 8px;

  &__item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 36px;
    padding: 0 16px;
    border: 0;
    border-radius: 18px;
    background: var(--s-canvas);
    color: var(--s-ink-2);
    font-size: 13px;
    cursor: pointer;
    transition: background-color 0.15s ease, color 0.15s ease;

    &:hover { color: var(--s-ink); }
    &.is-active { background: var(--sa); color: #fff; }
  }
  &__count { font-size: 12px; opacity: 0.75; font-variant-numeric: tabular-nums; }
}

// ---------- 逐题卡 ----------
.qs { display: flex; flex-direction: column; gap: 16px; }

.q {
  padding: 24px;

  &__head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
  &__badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    border-radius: 14px;
    background: var(--sa-soft);
    color: var(--sa);
    font-size: 13px;
    font-weight: 600;
  }
  &__tag {
    height: 24px;
    display: inline-flex;
    align-items: center;
    padding: 0 12px;
    border-radius: 12px;
    background: var(--s-canvas);
    color: var(--s-ink-2);
    font-size: 12px;
  }
  &__score { font-size: 12px; color: var(--s-ink-3); margin-left: auto; }
  &__question {
    margin: 14px 0 0;
    font-size: 16px;
    line-height: 26px;
    font-weight: 400;
    color: var(--s-ink);
  }
  &__answers {
    display: flex;
    gap: 32px;
    margin-top: 16px;
    flex-wrap: wrap;
  }
}

.opt {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-top: 12px;
  padding: 17px 18px;
  border-radius: 12px;
  background: var(--s-canvas);
  box-shadow: inset 0 0 0 1px var(--s-divider);
  list-style: none;
  transition: background-color 0.15s ease, box-shadow 0.15s ease;

  &--right {
    background: #e9f8ef;
    box-shadow: inset 0 0 0 1px #34a853;
    .opt__label { color: #2e9e52; font-weight: 500; }
  }
  &--wrong {
    background: #fdeeed;
    box-shadow: inset 0 0 0 1px #ea4335;
    .opt__label { color: #d93025; font-weight: 500; }
  }

  &__mark {
    flex: 0 0 18px;
    width: 18px;
    height: 18px;
    margin-top: 2px;
    color: var(--s-ink-3);

    svg { width: 18px; height: 18px; display: block; }
  }
  &--right .opt__mark { color: #34a853; }
  &--wrong .opt__mark { color: #ea4335; }

  &__label { font-size: 15px; line-height: 22px; color: var(--s-ink); word-break: break-all; }
}

.ans {
  &__label { margin-right: 8px; font-size: 13px; color: var(--s-ink-3); }
  &__value { font-size: 13px; font-weight: 600; color: var(--s-ink); word-break: break-all; }
  &__value--right { color: #2e9e52; }
  &__value--none { color: var(--s-ink-3); font-weight: 400; }
}

.chip {
  height: 26px;
  display: inline-flex;
  align-items: center;
  padding: 0 12px;
  border-radius: 13px;
  font-size: 12px;
  font-weight: 500;

  &--right { background: #e9f8ef; color: #34a853; }
  &--wrong { background: #fdeeed; color: #ea4335; }
  &--none { background: var(--s-canvas); color: var(--s-ink-3); }
}

.analysis {
  margin-top: 16px;
  padding: 16px;
  border-radius: 12px;
  background: #f2f7ff;

  &__label { font-size: 12px; font-weight: 600; color: #1d4e8c; margin-bottom: 6px; }
  &__text { font-size: 14px; line-height: 24px; color: #1d4e8c; word-break: break-all; }

  &--comment { background: #f6f7f9; }
  &--comment .analysis__label { color: var(--s-ink-2); }
  &--comment .analysis__text { color: var(--s-ink-2); }
}

.emptyLine {
  margin: 0;
  padding: 32px 0;
  text-align: center;
  font-size: 13px;
  color: var(--s-ink-3);
}

@media (max-width: 900px) {
  .head { flex-wrap: wrap; }
  .head__divider { display: none; }
  .head__actions { margin-left: 0; }
}
</style>
