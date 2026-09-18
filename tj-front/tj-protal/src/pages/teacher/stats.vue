<!--
 * 教师端 · 考试统计（设计稿 T8）
 * -----------------------------------------------------------------------------
 * 阶段 3 的原则在这页最显眼：**回看类页面禁止直接查题库**。
 * 所有逐题 / 逐知识点的正确率都基于试卷快照（snapshotItems）——
 * 页头的「快照口径条」就是在向老师声明这一点：之后题库怎么改，这份统计不变。
 *
 * 数字只算「交了卷的学生」，缺考不进平均分 —— 这是统计的常识口径，别把分母做错。
-->
<template>
  <div class="st">
    <!-- 页头 -->
    <div class="st-head">
      <div class="st-head__text">
        <button class="q-btn" type="button" @click="goBack">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M9.6 3.4 5.2 8l4.4 4.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          返回考试管理
        </button>
        <h2 class="s-h2">{{ exam ? exam.name : '考试统计' }}</h2>
      </div>
      <button class="q-btn" type="button" @click="goMarking">去批改 / 查答卷</button>
    </div>

    <p v-if="loading" class="st-state">加载中…</p>

    <!-- 未发布：没有快照，统计无从谈起 -->
    <section v-else-if="!exam?.paperVersion" class="s-card st-card">
      <p class="st-state">这场考试还没有发布，没有可统计的成绩。发布后这里会出现分数分布与逐题正确率。</p>
    </section>

    <template v-else>
      <!-- 快照口径条 -->
      <div class="st-snapshot" role="note">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
          <path d="M7 1.3 12.7 12H1.3L7 1.3Z" stroke="#B26A00" stroke-width="1.4" stroke-linejoin="round" />
          <path d="M7 5.4v3" stroke="#B26A00" stroke-width="1.4" stroke-linecap="round" />
          <circle cx="7" cy="10.4" r="0.8" fill="#B26A00" />
        </svg>
        <span>
          本页成绩基于试卷快照 <strong>v{{ exam.paperVersion.replace('v', '') }}</strong>（冻结于 {{ exam.snapshotAt }}）——
          之后题库里的改动不会影响这场考试的统计。
        </span>
      </div>

      <!-- 概览 -->
      <div class="st-cards">
        <div class="s-card st-card">
          <p class="st-card__label">提交 / 应考</p>
          <p class="st-card__num">{{ summary.submitted }}<em>/ {{ summary.enrolled ?? '—' }}</em></p>
          <p class="st-card__note">应考人数 / 缺考需要「选课名单」，平台暂无此数据</p>
        </div>
        <div class="s-card st-card">
          <p class="st-card__label">平均分</p>
          <p class="st-card__num">{{ summary.avgScore ?? '—' }}<em>/ {{ fullScore }}</em></p>
          <p class="st-card__note">只计已交卷学生</p>
        </div>
        <div class="s-card st-card">
          <p class="st-card__label">通过率</p>
          <p class="st-card__num">{{ pct(summary.passRate) }}</p>
          <p class="st-card__note">及格线 {{ exam.passScore }} 分</p>
        </div>
        <div class="s-card st-card">
          <p class="st-card__label">最高 / 最低</p>
          <p class="st-card__num">{{ summary.highest ?? '—' }}<em>/ {{ summary.lowest ?? '—' }}</em></p>
          <p class="st-card__note">满分 {{ fullScore }} 分</p>
        </div>
      </div>

      <div v-if="!summary.submitted" class="s-card st-card">
        <p class="st-state">还没有学生提交答卷，统计会在交卷后自动出现。</p>
      </div>

      <template v-else>
        <div class="st-cols">
          <!-- 分数分布 -->
          <section class="s-card st-card">
            <h3 class="st-card__title">分数分布</h3>
            <div class="st-dist">
              <div v-for="bin in distribution" :key="bin.label" class="st-dist__col">
                <span class="st-dist__count">{{ bin.count }}</span>
                <div class="st-dist__barWrap">
                  <div class="st-dist__bar" :style="{ height: distHeight(bin.count) }" :class="{ 'is-fail': bin.label === distribution[0]?.label }"></div>
                </div>
                <span class="st-dist__label">{{ bin.label }}</span>
              </div>
            </div>
          </section>

          <!-- 逐知识点正确率（薄弱在前，接学情分析） -->
          <section class="s-card st-card">
            <h3 class="st-card__title">知识点掌握（薄弱在前）</h3>
            <div v-for="kp in perKnowledge" :key="kp.name" class="st-kp">
              <span class="st-kp__name" :title="kp.name">{{ kp.name }}</span>
              <div class="st-kp__barWrap">
                <div class="st-kp__bar" :class="rateClass(kp.correctRate)" :style="{ width: kpW(kp) }"></div>
              </div>
              <span class="st-kp__rate" :class="rateClass(kp.correctRate)">{{ pct(kp.correctRate) }}</span>
            </div>
            <p v-if="!perKnowledge.length" class="st-state">题目没有挂知识点，无法按知识点统计。</p>
          </section>
        </div>

        <!-- 逐题正确率 -->
        <section class="s-card st-card">
          <h3 class="st-card__title">逐题正确率</h3>
          <div class="st-table">
            <div class="st-row st-row--head">
              <span class="st-c st-c--no">题号</span>
              <span class="st-c">题目</span>
              <span class="st-c st-c--kp">知识点</span>
              <span class="st-c st-c--rate">正确率</span>
              <span class="st-c st-c--cnt">答对 / 答题</span>
            </div>
            <div v-for="q in perQuestion" :key="q.questionId" class="st-row">
              <span class="st-c st-c--no">{{ q.order }}</span>
              <span class="st-c st-c--stem" :title="q.stem">{{ q.stem }}</span>
              <span class="st-c st-c--kp">
                <span v-for="k in q.knowledgePoints" :key="k" class="st-kpTag">{{ k }}</span>
                <span v-if="!q.knowledgePoints.length" class="st-c--muted">—</span>
              </span>
              <span class="st-c st-c--rate">
                <span class="st-miniBar"><span class="st-miniBar__fill" :class="rateClass(q.correctRate)" :style="{ width: qW(q) }"></span></span>
                <strong :class="rateClass(q.correctRate)">{{ pct(q.correctRate) }}</strong>
              </span>
              <span class="st-c st-c--cnt st-c--muted">{{ q.correctCount }} / {{ q.attempts }}</span>
            </div>
          </div>
        </section>
      </template>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getStats } from '@/api/teacher/exams';
import { TYPE_LABEL } from '@/config/teacherDict';

const route = useRoute();
const router = useRouter();

const loading = ref(true);
const exam = ref(null);
const summary = ref({});
const distribution = ref([]);
const perQuestion = ref([]);
const perKnowledge = ref([]);
const fullScore = ref(0);

const pct = (v) => (v === null || v === undefined ? '—' : `${Math.round(v * 100)}%`);

// 正确率配色：<50% 困难（红）/ 50–75% 中等（橙）/ >75% 健康（绿）—— 只染文字与细条，不进背景
const rateClass = (v) => {
  if (v === null || v === undefined) return '';
  return v < 0.5 ? 'is-bad' : v < 0.75 ? 'is-mid' : 'is-good';
};

const distHeight = (count) => {
  const max = Math.max(...distribution.value.map((b) => b.count), 1);
  return `${Math.round((count / max) * 100)}%`;
};
const distWidth = (count) => {
  const max = Math.max(...distribution.value.map((b) => b.count), 1);
  return `${Math.round((count / max) * 100)}%`;
};
const kpW = (kp) => (kp.correctRate === null ? '0%' : `${Math.round(kp.correctRate * 100)}%`);
const qW = (q) => (q.correctRate === null ? '0%' : `${Math.round(q.correctRate * 100)}%`);

const goBack = () => router.push('/teacher/exams');
const goMarking = () => router.push(`/teacher/exams/${route.params.id}/marking`);

// 统计数据随考试变化；注意 :id 变化时 vue-router 会复用本组件实例，必须 watch 重载
const load = async () => {
  loading.value = true;
  try {
    const res = await getStats(route.params.id);
    exam.value = res.exam;
    summary.value = res.summary;
    distribution.value = res.distribution;
    perQuestion.value = res.perQuestion;
    perKnowledge.value = res.perKnowledge;
    fullScore.value = res.fullScore;
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

// distribution 列只是视觉占位，避免未用变量告警
void distWidth;
void TYPE_LABEL;
</script>

<style lang="scss" scoped>
.st {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1096px;
}

// ---- 页头 ----
.st-head {
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
.st-snapshot {
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

// ---- 概览卡 ----
.st-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.st-card {
  padding: 20px;

  &__label {
    margin: 0 0 8px;
    font-size: 12px;
    color: var(--s-ink-3);
  }
  &__num {
    margin: 0;
    font-size: 26px;
    line-height: 32px;
    font-weight: 600;
    color: var(--s-ink);

    em {
      margin-left: 4px;
      font-size: 13px;
      font-style: normal;
      font-weight: 400;
      color: var(--s-ink-3);
    }
  }
  &__note {
    margin: 6px 0 0;
    font-size: 12px;
    color: var(--s-ink-3);
  }
  &__title {
    margin: 0 0 16px;
    font-size: 14px;
    line-height: 20px;
    font-weight: 600;
    color: var(--s-ink);
  }
}

// ---- 两栏 ----
.st-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}

// ---- 分数分布 ----
.st-dist {
  display: flex;
  align-items: flex-end;
  gap: 18px;
  height: 180px;

  &__col {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    height: 100%;
  }
  &__count {
    font-size: 12px;
    font-weight: 600;
    color: var(--s-ink);
  }
  &__barWrap {
    flex: 1;
    width: 100%;
    display: flex;
    align-items: flex-end;
  }
  &__bar {
    width: 100%;
    min-height: 4px;
    border-radius: 6px 6px 2px 2px;
    background: var(--sa, #0066cc);
    opacity: 0.85;

    &.is-fail {
      background: #c2543a;
    }
  }
  &__label {
    font-size: 11px;
    color: var(--s-ink-3);
  }
}

// ---- 知识点正确率 ----
.st-kp {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 0;

  &__name {
    flex: 0 0 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13px;
    color: var(--s-ink);
  }
  &__barWrap {
    flex: 1 1 auto;
    height: 8px;
    border-radius: 4px;
    background: #ededf0;
    overflow: hidden;
  }
  &__bar {
    height: 100%;
    border-radius: 4px;

    &.is-good { background: #1d8a43; }
    &.is-mid { background: #c98a2b; }
    &.is-bad { background: #c2543a; }
  }
  &__rate {
    flex: 0 0 40px;
    text-align: right;
    font-size: 12px;
    font-weight: 600;

    &.is-good { color: #1d8a43; }
    &.is-mid { color: #c98a2b; }
    &.is-bad { color: #c2543a; }
  }
}

// ---- 逐题表 ----
.st-table {
  display: flex;
  flex-direction: column;
}

.st-row {
  display: grid;
  grid-template-columns: 48px 1fr 180px 150px 88px;
  gap: 12px;
  align-items: center;
  padding: 10px 0;

  & + & {
    border-top: 1px solid #f0f0f2;
  }
  &--head {
    padding: 0 0 10px;
    font-size: 12px;
    color: var(--s-ink-3);
    border-bottom: 1px solid #f0f0f2;
  }
}

.st-c {
  min-width: 0;
  font-size: 13px;
  color: var(--s-ink);

  &--muted {
    color: var(--s-ink-3);
  }
  &--no {
    color: var(--s-ink-3);
  }
  &--stem {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  &--kp {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }
  &--rate {
    display: flex;
    align-items: center;
    gap: 8px;

    strong {
      flex: 0 0 36px;
      font-size: 12px;
      font-weight: 600;

      &.is-good { color: #1d8a43; }
      &.is-mid { color: #c98a2b; }
      &.is-bad { color: #c2543a; }
    }
  }
  &--cnt {
    text-align: right;
  }
}

.st-kpTag {
  padding: 2px 8px;
  border-radius: 999px;
  background: #f5f5f7;
  font-size: 11px;
  color: #6e6e73;
}

.st-miniBar {
  flex: 1 1 auto;
  height: 6px;
  border-radius: 3px;
  background: #ededf0;
  overflow: hidden;

  &__fill {
    display: block;
    height: 100%;
    border-radius: 3px;

    &.is-good { background: #1d8a43; }
    &.is-mid { background: #c98a2b; }
    &.is-bad { background: #c2543a; }
  }
}

.st-state {
  margin: 0;
  padding: 28px 0;
  text-align: center;
  font-size: 13px;
  color: var(--s-ink-3);
}
</style>
