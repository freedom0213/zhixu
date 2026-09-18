<!--
 * 答题页（/student/exams/answer?examId=xxx）— P17 重写
 * -----------------------------------------------------------------------------
 * 以前它是「考试作答引导页」：一句文案 + 一个跳转，因为后端当时没有作答接口。
 * P14 起作答链路已经建好（开始 / 交卷判分 / 看成绩），这里就把它变成**真的答题页**：
 *
 *   进入 → startExam（拿题目，**不含答案**）→ 作答 → 交卷（自动判分）→ 看答卷
 *
 * 学生从两个地方进来（P17）：课程学习页右侧「考试」页签、课程详情页的「课程试卷」。
 *
 * ⚠️ 两个后端口径（踩过）：
 *   ① 答案编号是 **1 基**（选项 A = 1），与题库 question_detail.answer 同口径；
 *   ② 「考试只能考一次」是业务规则 → 已交卷再进会被 400 拒绝，页面要如实引导去「查看答卷」，
 *      而不是显示一个空白答题区。
-->
<template>
  <div class="exa">
    <p v-if="loading" class="exa__state">正在加载试卷…</p>

    <section v-else-if="error" class="s-card exa__state">
      <p class="exa__errTitle">{{ error }}</p>
      <p v-if="alreadyDone" class="exa__errHint">这份卷子只能考一次，可以去看自己的答卷与解析。</p>
      <div class="exa__stateOps">
        <button v-if="alreadyDone" class="btn btn--primary" type="button" @click="goReview">查看答卷</button>
        <router-link class="btn" to="/student/exams">返回考试列表</router-link>
      </div>
    </section>

    <template v-else-if="paper">
      <!-- 卷头 -->
      <section class="s-card exa__head">
        <div class="exa__headMain">
          <h2 class="exa__title">{{ paper.name }}</h2>
          <p class="exa__meta">
            共 {{ paper.questions.length }} 题
            <template v-if="paper.totalScore"> · 总分 {{ paper.totalScore }}</template>
            <template v-if="paper.duration"> · 限时 {{ paper.duration }} 分钟</template>
            <template v-else> · 不限时</template>
            <template v-if="paper.passScore"> · 及格 {{ paper.passScore }} 分</template>
          </p>
          <p v-if="paper.notice" class="exa__notice">{{ paper.notice }}</p>
        </div>
        <div class="exa__progress">
          <strong>{{ answeredCount }}</strong>
          <span>/ {{ paper.questions.length }} 已作答</span>
        </div>
      </section>

      <!-- 题目 -->
      <section v-for="(q, qi) in paper.questions" :key="q.id" class="s-card exa__q">
        <p class="exa__qTitle">
          <span class="exa__qNo">{{ qi + 1 }}</span>
          <span class="exa__qType">{{ q.type === 2 ? '多选题' : '单选题' }}</span>
          <span v-if="q.score" class="exa__qScore">{{ q.score }} 分</span>
        </p>
        <p class="exa__stem">{{ q.name }}</p>
        <ul class="exa__opts">
          <li v-for="(opt, oi) in q.options || []" :key="oi">
            <button
              class="exa__opt"
              :class="{ 'is-on': isPicked(q.id, oi + 1) }"
              type="button"
              :aria-pressed="isPicked(q.id, oi + 1)"
              @click="pick(q, oi + 1)"
            >
              <span class="exa__optKey">{{ letter(oi) }}</span>
              <span class="exa__optText">{{ opt }}</span>
            </button>
          </li>
        </ul>
      </section>

      <!-- 底部提交条 -->
      <div class="exa__bar">
        <p class="exa__barText">
          <template v-if="unanswered">还有 {{ unanswered }} 题没作答</template>
          <template v-else>全部作答完成</template>
        </p>
        <button v-if="!confirming" class="btn btn--primary" type="button" :disabled="submitting" @click="askSubmit">
          交卷
        </button>
        <template v-else>
          <span class="exa__barAsk">交卷后不能改，确认提交？</span>
          <button class="btn" type="button" :disabled="submitting" @click="confirming = false">再检查一下</button>
          <button class="btn btn--primary" type="button" :disabled="submitting" @click="doSubmit">
            {{ submitting ? '提交中…' : '确认交卷' }}
          </button>
        </template>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { startExam, submitExam } from '@/api/subject.js';

const route = useRoute();
const router = useRouter();

const loading = ref(true);
const error = ref('');
const alreadyDone = ref(false);
const paper = ref(null);
const submitting = ref(false);
const confirming = ref(false);

/** questionId → 已选编号数组（1 基，与题库答案同口径） */
const picked = reactive({});

const examId = computed(() => route.query.examId || '');

const letter = (i) => String.fromCharCode(65 + i);
const isPicked = (qid, n) => (picked[qid] || []).includes(n);

/** 单选：选一个（再点取消）；多选：切换 */
const pick = (q, n) => {
  const cur = picked[q.id] || [];
  if (q.type === 2) {
    picked[q.id] = cur.includes(n) ? cur.filter((x) => x !== n) : [...cur, n].sort((a, b) => a - b);
  } else {
    picked[q.id] = cur.includes(n) ? [] : [n];
  }
};

const answeredCount = computed(() =>
  (paper.value?.questions || []).filter((q) => (picked[q.id] || []).length > 0).length,
);
const unanswered = computed(() => (paper.value?.questions || []).length - answeredCount.value);

const load = async () => {
  const id = examId.value;
  if (!id) {
    loading.value = false;
    error.value = '没有指定要作答的试卷。';
    return;
  }
  loading.value = true;
  error.value = '';
  alreadyDone.value = false;
  paper.value = null;
  Object.keys(picked).forEach((k) => delete picked[k]);
  try {
    const d = await startExam(id);
    // 卷面总分由题目分值累加（后端 start 已算好，这里兜底）
    if (d && d.totalScore == null) {
      d.totalScore = (d.questions || []).reduce((sum, q) => sum + Number(q.score || 0), 0);
    }
    paper.value = d;
  } catch (e) {
    error.value = e?.message || '试卷加载失败';
    // 「考试只能考一次」是业务规则，不是系统故障 —— 引导去看答卷
    if (String(error.value).includes('已经交过') || String(error.value).includes('不能作答')) {
      alreadyDone.value = true;
    }
  } finally {
    loading.value = false;
  }
};

const askSubmit = () => {
  confirming.value = true;
};

const doSubmit = async () => {
  if (submitting.value) return;
  submitting.value = true;
  try {
    const details = (paper.value.questions || []).map((q) => ({
      questionId: q.id,
      // 未作答传空串（后端按错处理，不会漏题）
      answer: (picked[q.id] || []).join(','),
      questionType: q.type,
    }));
    await submitExam(examId.value, details);
    // 交卷成功 → 去看答卷（那里有逐题对错与解析）
    router.replace({ path: '/student/exams/review', query: { id: examId.value } });
  } catch (e) {
    error.value = e?.message || '交卷失败，请重试';
    confirming.value = false;
  } finally {
    submitting.value = false;
  }
};

const goReview = () => router.replace({ path: '/student/exams/review', query: { id: examId.value } });

onMounted(load);
// ⚠️ vue-router 的 query 变化**不触发 onMounted** → 从别处再点一场必须靠 watch 重载
watch(
  () => route.query.examId,
  () => {
    if (route.name === 'studentExamAnswer') load();
  },
);
</script>

<style lang="scss" scoped>
.exa {
  display: flex;
  flex-direction: column;
  gap: 12px;

  &__state {
    padding: 28px;
    text-align: center;
  }
  &__errTitle {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: #1d1d1f;
  }
  &__errHint {
    margin: 8px 0 0;
    font-size: 13px;
    color: #6e6e73;
  }
  &__stateOps {
    display: flex;
    justify-content: center;
    gap: 10px;
    margin-top: 16px;
  }

  &__head {
    display: flex;
    align-items: flex-start;
    gap: 16px;
    padding: 20px;
  }
  &__headMain {
    flex: 1 1 auto;
    min-width: 0;
  }
  &__title {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
    color: #1d1d1f;
  }
  &__meta {
    margin: 6px 0 0;
    font-size: 13px;
    color: #6e6e73;
  }
  &__notice {
    margin: 8px 0 0;
    font-size: 12px;
    line-height: 18px;
    color: #86868b;
  }
  &__progress {
    flex: 0 0 auto;
    text-align: right;

    strong {
      display: block;
      font-size: 22px;
      line-height: 26px;
      font-weight: 600;
      color: var(--sa, #0066cc);
    }
    span {
      font-size: 12px;
      color: #6e6e73;
    }
  }

  &__q {
    padding: 18px 20px;
  }
  &__qTitle {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0;
    font-size: 12px;
    color: #6e6e73;
  }
  &__qNo {
    width: 22px;
    height: 22px;
    border-radius: 7px;
    background: rgba(0, 102, 204, 0.1);
    color: var(--sa, #0066cc);
    font-size: 12px;
    line-height: 22px;
    text-align: center;
  }
  &__qType {
    padding: 2px 8px;
    border-radius: 999px;
    background: #f5f5f7;
  }
  &__qScore {
    margin-left: auto;
  }
  &__stem {
    margin: 10px 0 14px;
    font-size: 15px;
    line-height: 24px;
    color: #1d1d1f;
  }

  &__opts {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  &__opt {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    width: 100%;
    padding: 10px 12px;
    border: 0;
    border-radius: 12px;
    background: #fafafc;
    box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.06);
    font-family: inherit;
    font-size: 14px;
    line-height: 22px;
    color: #1d1d1f;
    text-align: left;
    cursor: pointer;
    transition: background-color 0.14s ease, box-shadow 0.14s ease;

    &:hover {
      background: #f2f2f5;
    }
    &.is-on {
      background: rgba(0, 102, 204, 0.08);
      box-shadow: inset 0 0 0 1px var(--sa, #0066cc);
    }
  }
  &__optKey {
    flex: 0 0 auto;
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background: #fff;
    box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.12);
    font-size: 12px;
    line-height: 22px;
    text-align: center;
    color: #6e6e73;

    .exa__opt.is-on & {
      background: var(--sa, #0066cc);
      color: #fff;
      box-shadow: none;
    }
  }
  &__optText {
    flex: 1 1 auto;
    min-width: 0;
  }

  &__bar {
    position: sticky;
    bottom: 0;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 20px;
    border-radius: 16px;
    background: rgba(255, 255, 255, 0.92);
    backdrop-filter: blur(12px);
    box-shadow: 0 -2px 16px rgba(16, 24, 40, 0.06), inset 0 0 0 1px rgba(16, 24, 40, 0.06);
  }
  &__barText {
    flex: 1 1 auto;
    margin: 0;
    font-size: 13px;
    color: #6e6e73;
  }
  &__barAsk {
    flex: 1 1 auto;
    font-size: 13px;
    color: #1d1d1f;
  }
}
</style>
