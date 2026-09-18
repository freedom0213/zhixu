<!-- 知识检测：基于知识库出题 → 作答 → 判分 → 薄弱知识点反馈
     默认用于课程助教（COURSE）；私人助手传入 assistantType="PRIVATE" 即可复用同一套答题卡。 -->
<template>
  <div class="quiz">
    <div v-if="!kbReady" class="quizNotice">
      {{ assistantType === 'PRIVATE' ? '你的个人知识库还没有资料，请先在「我的知识库」上传 Markdown / TXT 后再检测。' : '本课程知识库资料尚在建设中，暂不支持知识检测。' }}
    </div>

    <div v-else-if="questions.length === 0" class="quizStart">
      <p class="quizDesc">基于{{ assistantType === 'PRIVATE' ? '你的个人知识库' : '当前课程知识库' }}自动出题，提交后立即判分，并指出你的薄弱知识点。</p>
      <button type="button" class="quizBtn primary" :disabled="loading" @click="startQuiz">
        {{ loading ? '生成中…' : '开始检测' }}
      </button>
      <p v-if="loading" class="quizHint">正在根据课程资料出题，请稍候…</p>
      <p v-if="error" class="quizError">{{ error }}</p>
    </div>

    <div v-else class="quizList">
      <div v-for="(q, qi) in questions" :key="q.id" class="quizItem"
           :class="{ isCorrect: submitted && answers[qi] === q.answerIndex, isWrong: submitted && answers[qi] !== q.answerIndex }">
        <div class="quizStemLine">
          <span class="quizNo">{{ qi + 1 }}</span>
          <span class="quizType">{{ q.typeText }}</span>
        </div>
        <p class="quizStem">{{ q.stem }}</p>
        <div class="quizOptions">
          <label v-for="(opt, oi) in q.options" :key="oi" class="quizOption" :class="{ picked: answers[qi] === oi }">
            <input type="radio" :name="'quiz-' + qi" :value="oi" v-model="answers[qi]" :disabled="submitted" />
            <span class="optLetter">{{ letter(oi) }}</span>
            <span class="optText">{{ opt }}</span>
          </label>
        </div>
        <div v-if="submitted" class="quizExplain">
          <p class="verdict" :class="answers[qi] === q.answerIndex ? 'ok' : 'bad'">
            {{ answers[qi] === q.answerIndex ? '回答正确' : '回答错误' }}
          </p>
          <p>正确答案：{{ letter(q.answerIndex) }}. {{ q.options[q.answerIndex] }}</p>
          <p v-if="q.explanation">解析：{{ q.explanation }}</p>
          <p v-if="q.point" class="point">对应知识点：{{ q.point }}</p>
        </div>
      </div>

      <div v-if="!submitted" class="quizActions">
        <button type="button" class="quizBtn primary" :disabled="!allAnswered" @click="submitQuiz">提交答案</button>
        <button type="button" class="quizBtn ghost" @click="reset">重新出题</button>
      </div>

      <div v-else class="quizResult">
        <p class="scoreNum">{{ correctCount }} / {{ questions.length }}</p>
        <p class="scoreText">本次检测得分</p>
        <p v-if="weakPoints.length" class="weak">薄弱知识点：{{ weakPoints.join('、') }}</p>
        <p v-else class="strong">全部答对，本知识块掌握良好。</p>
        <button type="button" class="quizBtn primary" @click="reset">再测一次</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { memoryChatRedis, chatTask } from '@/api/ai.js';

const props = defineProps({
  courseId: { type: [String, Number], default: null },
  courseName: { type: String, default: '' },
  sectionName: { type: String, default: '' },
  kbReady: { type: Boolean, default: false },
  // COURSE=课程助教（默认）；PRIVATE=私人助手（走 /ct/chat/task 个人知识库）
  assistantType: { type: String, default: 'COURSE' },
  // 私人助手的资料范围：某份文件的 docId（''=全部资料），决定按哪份笔记出题
  docId: { type: String, default: '' }
});

const questions = ref([]);
const answers = ref([]);
const submitted = ref(false);
const loading = ref(false);
const error = ref('');

const letter = (i) => String.fromCharCode(65 + i);

const reset = () => {
  questions.value = [];
  answers.value = [];
  submitted.value = false;
  error.value = '';
  loading.value = false;
};

// 切换课程、小节或资料范围时重置检测
watch(() => [props.courseId, props.sectionName, props.docId], reset);

// 出题提示词：要求严格返回 JSON，便于前端渲染成答题卡
const buildPrompt = () => {
  const isPrivate = props.assistantType === 'PRIVATE';
  const source = isPrivate ? '我的个人知识库资料' : '本课程知识库资料';
  const where = props.sectionName ? `当前小节「${props.sectionName}」` : (isPrivate ? '我的知识库覆盖的知识点' : '本课程');
  return [
    `请根据${source}，围绕${where}出 3 道知识检测题，题型为单选题或判断题。`,
    '严格只输出一个 JSON 对象，不要输出任何解释文字，不要使用 Markdown 代码块围栏。',
    '格式：{"questions":[{"type":"single","stem":"题干","options":["选项A","选项B","选项C","选项D"],"answerIndex":0,"explanation":"解析","point":"知识点"}]}',
    '要求：type 为 "single"（单选，4 个选项）或 "bool"（判断题，options 固定为 ["正确","错误"]）；',
    `answerIndex 为正确选项下标，从 0 开始；题干、选项、解析必须来自${source}，不要编造。`,
    '正确答案的位置请随机分布，不要每道题的 answerIndex 都相同（尤其不要都放在第一个）。'
  ].join('');
};

// 从模型输出里提取 JSON（兼容被 Markdown 代码块包裹的情况）
const extractJson = (text) => {
  if (!text) return '';
  let s = String(text).trim();
  const fence = s.match(/```(?:json)?([\s\S]*?)```/i);
  if (fence) s = fence[1];
  const start = s.indexOf('{');
  const end = s.lastIndexOf('}');
  if (start === -1 || end === -1 || end <= start) return '';
  return s.slice(start, end + 1);
};

// 规范化题目结构，剔除无效题
const normalize = (raw) => {
  const list = Array.isArray(raw && raw.questions) ? raw.questions : [];
  return list.map((q, i) => {
    const isBool = q && q.type === 'bool';
    let options = Array.isArray(q && q.options) ? q.options.map((o) => String(o)) : [];
    if (isBool && options.length < 2) options = ['正确', '错误'];
    let ai = Number(q && q.answerIndex);
    if (!Number.isInteger(ai) || ai < 0 || ai >= options.length) ai = 0;
    return {
      id: i + 1,
      typeText: isBool ? '判断题' : '单选题',
      stem: String((q && q.stem) || '').trim(),
      options,
      answerIndex: ai,
      explanation: String((q && q.explanation) || '').trim(),
      point: String((q && q.point) || '').trim()
    };
  }).filter((q) => q.stem && q.options.length >= 2);
};

// 调用后端 RAG 出题：课程助教走 /ct/chat/simple（COURSE）；私人助手走 /ct/chat/task（PRIVATE）
const requestQuestions = async () => {
  let content = '';
  if (props.assistantType === 'PRIVATE') {
    // docId 为空表示「全部资料」；否则只按该份笔记出题
    const res = await chatTask({ task: 'quiz', message: buildPrompt(), docId: props.docId || '' });
    content = (res && res.data && res.data.content) || '';
  } else {
    const res = await memoryChatRedis({
      question: buildPrompt(),
      assistantType: 'COURSE',
      courseId: props.courseId
    });
    content = (res && res.data && res.data.content) || '';
  }
  const jsonText = extractJson(content);
  if (!jsonText) return [];
  try {
    return normalize(JSON.parse(jsonText));
  } catch (e) {
    return [];
  }
};

const startQuiz = async () => {
  if (loading.value) return;
  loading.value = true;
  error.value = '';
  try {
    let list = await requestQuestions();
    if (!list.length) list = await requestQuestions(); // 解析失败重试一次
    if (!list.length) {
      error.value = '题目生成失败，请点击重试。';
      return;
    }
    questions.value = list;
    answers.value = new Array(list.length).fill(null);
    submitted.value = false;
  } catch (e) {
    error.value = '题目生成失败：' + ((e && e.message) || '未知错误');
  } finally {
    loading.value = false;
  }
};

const allAnswered = computed(() => questions.value.length > 0 && answers.value.every((a) => a !== null && a !== undefined));
const correctCount = computed(() => questions.value.reduce((n, q, i) => n + (answers.value[i] === q.answerIndex ? 1 : 0), 0));
const weakPoints = computed(() => {
  const out = [];
  questions.value.forEach((q, i) => {
    if (answers.value[i] !== q.answerIndex && q.point && !out.includes(q.point)) out.push(q.point);
  });
  return out;
});

const submitQuiz = () => {
  if (!allAnswered.value) return;
  submitted.value = true;
};
</script>

<style lang="scss" scoped>
.quiz {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px 4px;
  scrollbar-width: thin;
  scrollbar-color: #66717d #252c33;
}
.quiz::-webkit-scrollbar { width: 8px; }
.quiz::-webkit-scrollbar-track { background: #252c33; border-radius: 4px; }
.quiz::-webkit-scrollbar-thumb { background: #66717d; border-radius: 4px; }

.quizNotice {
  background: #3a2f14; color: #e8c87a; border: 1px solid #5a4a1e;
  border-radius: 6px; padding: 8px 10px; font-size: 12px; line-height: 1.6;
}

.quizStart { background: #f5f6f8; border-radius: 10px; padding: 16px 14px; text-align: center; }
.quizDesc { color: #555; font-size: 12px; line-height: 1.7; margin: 0 0 12px; }
.quizBtn {
  border: none; border-radius: 8px; padding: 8px 14px; font-size: 13px; cursor: pointer; transition: background .2s;
  &.primary { background: #007BFF; color: #fff; &:hover { background: #0069d9; } &:disabled { background: #a0c4ff; cursor: not-allowed; } }
  &.ghost { background: transparent; border: 1px solid #c9d8ff; color: #3562c8; &:hover { background: #eef5ff; } }
}
.quizHint { color: #888; font-size: 12px; margin: 10px 0 0; }
.quizError { color: #d33; font-size: 12px; margin: 10px 0 0; }

.quizList { display: flex; flex-direction: column; gap: 10px; }
.quizItem {
  background: #f5f6f8; border-radius: 10px; padding: 12px;
  border-left: 3px solid transparent;
  &.isCorrect { border-left-color: #2ea043; }
  &.isWrong { border-left-color: #e5534b; }
}
.quizStemLine { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.quizNo {
  width: 18px; height: 18px; border-radius: 50%; background: #007BFF; color: #fff;
  font-size: 11px; display: inline-flex; align-items: center; justify-content: center;
}
.quizType { font-size: 11px; color: #666; background: #e6e9ee; border-radius: 8px; padding: 2px 6px; }
.quizStem { color: #222; font-size: 13px; line-height: 1.65; margin: 0 0 10px; }
.quizOptions { display: flex; flex-direction: column; gap: 6px; }
.quizOption {
  display: flex; align-items: flex-start; gap: 8px; padding: 7px 9px;
  border: 1px solid #e2e6ec; border-radius: 8px; background: #fff; cursor: pointer;
  font-size: 13px; color: #333;
  &:hover { border-color: #b9cdf5; }
  &.picked { border-color: #007BFF; background: #eef5ff; }
  input { margin-top: 3px; }
}
.optLetter { font-weight: 500; color: #007BFF; }
.optText { flex: 1; line-height: 1.55; }

.quizExplain {
  margin-top: 10px; padding-top: 10px; border-top: 1px dashed #d9dee6;
  font-size: 12px; color: #555; line-height: 1.7;
  p { margin: 0 0 4px; }
}
.verdict { font-weight: 500; &.ok { color: #2ea043; } &.bad { color: #e5534b; } }
.point { color: #8a5a00; }

.quizActions { display: flex; align-items: center; gap: 8px; padding: 2px 0; }
.quizResult { background: #f5f6f8; border-radius: 10px; padding: 16px 14px; text-align: center; }
.scoreNum { font-size: 24px; font-weight: 500; color: #007BFF; margin: 0; }
.scoreText { font-size: 12px; color: #888; margin: 2px 0 12px; }
.weak { font-size: 12px; color: #8a5a00; background: #fff3cd; border-radius: 6px; padding: 6px 8px; margin: 0 0 12px; line-height: 1.6; }
.strong { font-size: 12px; color: #0f6b3b; background: #e6f7ee; border-radius: 6px; padding: 6px 8px; margin: 0 0 12px; }
</style>
