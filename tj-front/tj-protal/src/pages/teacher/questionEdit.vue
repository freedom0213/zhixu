<!--
 * 教师端 · 新建 / 编辑题目（设计稿 Q3）
 * -----------------------------------------------------------------------------
 * 7 个填充分组对应 8 个字段（「正确答案」不单独设字段，在选项里点圆点标记
 * —— 否则必然出现「选项改了、答案没改」的不一致）。
 *
 * 三条刻意的设计约束：
 *   1) 分值不在这里 —— 分值属于试卷，同一道题在不同卷里可以不同分。
 *   2) 知识点只能从**所属课程的知识点树**里选，不能现场新建
 *      （允许自由新建的话，同一门课很快会出现「自动配置」「自动配置 」「AutoConfig」
 *        三种写法，按知识点筛选与学情分析就全废了）。
 *   3) 只做选择题（单选 / 多选）—— 判断题、填空、主观题不进表单，
 *      避免出现「选了题型却填不完」的半成品。
 *
 * 选项与答案用**索引**在内部维护，保存时才映射成 A~H 字母：
 * 否则删掉中间一个选项，后面的答案字母会集体错位。
-->
<template>
  <div class="qe">
    <!-- 页头 -->
    <div class="qe-head">
      <div class="qe-head__text">
        <button class="qe-back" type="button" @click="goBack">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M9.6 3.4 5.2 8l4.4 4.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          返回我的题库
        </button>
        <h2 class="s-h2">{{ isEdit ? '编辑题目' : '新建题目' }}</h2>
        <p class="qe-head__sub">
          {{ isEdit
            ? '保存后立刻生效；已被试卷引用过的地方不受影响——历史试卷存的是快照。'
            : '9 个字段，保存后立即进入题库，不需要单独提交入库。' }}
        </p>
      </div>
      <div class="qe-head__actions">
        <template v-if="!isEdit">
          <button class="q-btn" type="button" :disabled="saving" @click="submit(true)">保存并继续新建</button>
        </template>
        <button class="q-btn q-btn--primary" type="button" :disabled="saving" @click="submit(false)">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
    </div>

    <p v-if="loadError" class="qe-error">{{ loadError }}</p>

    <section class="s-card qe-card">
      <!-- ① 题型 -->
      <div class="qe-field">
        <p class="qe-label">① 题型 <span class="qe-req">*</span></p>
        <div class="qe-chips">
          <button
            v-for="t in QUESTIONS_TYPES"
            :key="t"
            class="q-chip"
            :class="{ 'is-on': form.type === t }"
            type="button"
            @click="setType(t)"
          >
            {{ TYPE_LABEL[t] }}
          </button>
        </div>
        <p class="qe-hint">本期只做选择题。判断题与主观题不在此表单内。</p>
      </div>

      <!-- ② 题干 -->
      <div class="qe-field">
        <p class="qe-label">② 题干 <span class="qe-req">*</span></p>
        <textarea
          v-model.trim="form.stem"
          class="qe-textarea"
          rows="3"
          placeholder="把问题写清楚。学生看到的就是这一句。"
          aria-label="题干"
        ></textarea>
        <p v-if="errors.stem" class="qe-err">{{ errors.stem }}</p>
      </div>

      <!-- ③ 选项 -->
      <div class="qe-field">
        <p class="qe-label">
          ③ 选项 <span class="qe-req">*</span>
          <span class="qe-label__note">点左侧圆点标记正确答案（{{ TYPE_LABEL[form.type] }}）</span>
        </p>

        <div class="qe-opts">
          <div v-for="(opt, i) in form.options" :key="i" class="qe-opt">
            <button
              class="qe-opt__mark"
              :class="{ 'is-on': opt.correct }"
              type="button"
              :aria-pressed="opt.correct"
              :aria-label="`把选项 ${letter(i)} 标记为正确答案`"
              @click="toggleCorrect(i)"
            >
              {{ letter(i) }}
            </button>
            <input
              v-model.trim="opt.text"
              class="qe-opt__input"
              type="text"
              :placeholder="`选项 ${letter(i)} 的内容`"
              :aria-label="`选项 ${letter(i)} 内容`"
            />
            <button
              v-if="form.options.length > 2"
              class="q-act q-act--muted"
              type="button"
              @click="removeOption(i)"
            >
              删除
            </button>
          </div>
        </div>

        <div class="qe-opt-foot">
          <button
            v-if="form.options.length < 8"
            class="q-act"
            type="button"
            @click="addOption"
          >
            ＋ 添加选项
          </button>
          <span class="qe-hint">2–8 个选项，字母自动编号；末尾没用到的空行保存时会自动忽略</span>
        </div>
        <p v-if="errors.options" class="qe-err">{{ errors.options }}</p>
        <p v-if="errors.answer" class="qe-err">{{ errors.answer }}</p>
      </div>

      <!-- ④ 解析 -->
      <div class="qe-field">
        <p class="qe-label">
          ④ 解析 <span class="qe-label__note">选填 · 学生交卷后可见 · 写了就是 5–300 字</span>
        </p>
        <textarea
          v-model.trim="form.analysis"
          class="qe-textarea qe-textarea--sm"
          rows="3"
          placeholder="讲清为什么这个答案对。这是这道题唯一的教学价值载体。"
          aria-label="解析"
        ></textarea>
        <p v-if="errors.analysis" class="qe-err">{{ errors.analysis }}</p>
        <p class="qe-hint" v-else>
          留空表示暂时不写解析（能存，不是必填）；一旦写了，后端要求 5–300 字。
        </p>
      </div>

      <div class="qe-grid">
        <!-- ⑤ 难度 -->
        <div class="qe-field">
          <p class="qe-label">⑤ 难度 <span class="qe-req">*</span></p>
          <div class="qe-chips">
            <button
              v-for="d in DIFFICULTIES"
              :key="d"
              class="q-chip"
              :class="{ 'is-on': form.difficulty === d }"
              type="button"
              @click="form.difficulty = d"
            >
              {{ DIFF_LABEL[d] }}
            </button>
          </div>
          <p class="qe-hint">固定三档，不给自定义——否则按难度筛选与统计会失效。</p>
        </div>

        <!-- ⑥ 所属课程 -->
        <div class="qe-field">
          <p class="qe-label">⑦ 所属课程 <span class="qe-req">*</span></p>
          <select v-model="form.courseId" class="qe-select" aria-label="所属课程" @change="onCourseChange">
            <option value="">请选择课程</option>
            <option v-for="c in courses" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
          <p class="qe-hint">单选。课程是老师自己的资产。</p>
        </div>
      </div>

      <!-- ⑦ 知识点 -->
      <div class="qe-field">
        <p class="qe-label">⑥ 知识点 <span class="qe-label__note">可多个 · 只能从该课程的知识点里选</span></p>
        <template v-if="form.courseId">
          <div class="qe-chips qe-chips--wrap">
            <button
              v-for="k in knowledgeOptions"
              :key="k.id"
              class="q-chip"
              :class="{ 'is-on': form.knowledgePoints.includes(k.name) }"
              type="button"
              @click="toggleKnowledge(k.name)"
            >
              {{ k.name }}
            </button>
          </div>
          <p class="qe-hint">
            找不到需要的知识点？去「课程设置 · 知识点」里补充后回来 —— 保持只能选，筛选才不会失效。
          </p>
        </template>
        <p v-else class="qe-hint">先选择「所属课程」，这里才会列出它维护的知识点。</p>

      <!-- ⑧ 可见范围（P21） -->
      <div class="qe-field">
        <p class="qe-label">⑧ 可见范围</p>
        <div class="qe-chips">
          <button
            class="q-chip"
            :class="{ 'is-on': form.visibility === 0 }"
            type="button"
            @click="form.visibility = 0"
          >
            仅我
          </button>
          <button
            class="q-chip"
            :class="{ 'is-on': form.visibility === 1 }"
            type="button"
            @click="form.visibility = 1"
          >
            公开到平台
          </button>
        </div>
        <p class="qe-hint">
          <strong>仅我</strong>：只有你自己看得到，组卷时照样能选到。新题默认就是它。
          <strong>公开到平台</strong>：全平台讲师都能搜到并引用。随时可以撤回，但**已被试卷引用的题不能撤回**（只能停用）。
        </p>
      </div>

      </div>

      <div class="qe-divider"></div>
      <div class="qe-foot">
        <span class="qe-foot__note">
          正确答案在 ③ 里标记，不单独设字段。分值也不在这里 —— 它属于试卷。
        </span>
        <button class="q-btn" type="button" @click="goBack">取消</button>
        <button v-if="!isEdit" class="q-btn" type="button" :disabled="saving" @click="submit(true)">
          保存并继续新建
        </button>
        <button class="q-btn q-btn--primary" type="button" :disabled="saving" @click="submit(false)">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
    </section>

    <transition name="q-fade">
      <p v-if="notice" class="q-toast" role="status">{{ notice }}</p>
    </transition>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  listCourses,
  getKnowledgePoints,
  getQuestion,
  createQuestion,
  updateQuestion,
  getMyIdentity,
} from '@/api/teacher/questions';
import { QUESTIONS_TYPES, TYPE_LABEL, DIFFICULTIES, DIFF_LABEL } from '@/config/teacherDict';

const route = useRoute();
const router = useRouter();

const isEdit = computed(() => !!route.query.id);
const editId = computed(() => (route.query.id ? Number(route.query.id) : null));

const courses = ref([]);
const knowledgeOptions = ref([]);
const saving = ref(false);
const loadError = ref('');
const notice = ref('');
const errors = reactive({});

let noticeTimer = null;
const toast = (text) => {
  notice.value = text;
  clearTimeout(noticeTimer);
  noticeTimer = setTimeout(() => {
    notice.value = '';
  }, 2600);
};

const letter = (i) => String.fromCharCode(65 + i);

const form = reactive({
  type: 'single',
  stem: '',
  // correct 只存在表单内部；对外映射成 answer 字母
  options: [
    { text: '', correct: false },
    { text: '', correct: false },
    { text: '', correct: false },
    { text: '', correct: false },
  ],
  analysis: '',
  difficulty: '',
  courseId: '',
  knowledgePoints: [],
  // 可见范围（P21）：0 仅我 / 1 公开到平台。默认「仅我」= 库列默认，不替老师做公开的决定
  visibility: 0,
});

const setType = (t) => {
  form.type = t;
  // 单选：只留第一个被标记的答案，避免切回来出现多个正确答案
  if (t === 'single') {
    const first = form.options.findIndex((o) => o.correct);
    form.options.forEach((o, i) => {
      o.correct = i === first;
    });
  }
};

const toggleCorrect = (i) => {
  if (form.type === 'single') {
    form.options.forEach((o, idx) => {
      o.correct = idx === i ? !o.correct : false;
    });
    return;
  }
  form.options[i].correct = !form.options[i].correct;
};

const addOption = () => {
  if (form.options.length >= 8) return;
  form.options.push({ text: '', correct: false });
};

const removeOption = (i) => {
  if (form.options.length <= 2) return;
  // 索引式维护，删除后答案自动跟着新顺序走，不会错位
  form.options.splice(i, 1);
};

const toggleKnowledge = (name) => {
  const idx = form.knowledgePoints.indexOf(name);
  if (idx >= 0) form.knowledgePoints.splice(idx, 1);
  else form.knowledgePoints.push(name);
};

const loadKnowledge = async (courseId) => {
  knowledgeOptions.value = courseId ? await getKnowledgePoints(courseId) : [];
};

const onCourseChange = async () => {
  // 知识点是课程级数据：换课程后原来的选择可能已不属于该课程，必须清掉
  const names = new Set(knowledgeOptions.value.map((k) => k.name));
  form.knowledgePoints = form.knowledgePoints.filter((n) => names.has(n));
  await loadKnowledge(form.courseId);
  form.knowledgePoints = form.knowledgePoints.filter((n) =>
    knowledgeOptions.value.some((k) => k.name === n)
  );
};

const clearErrors = () => {
  Object.keys(errors).forEach((k) => delete errors[k]);
};

// 末尾多余的空选项行自动忽略：默认给 4 行是为了少点两次「添加选项」，
// 但只出 2 个选项的题也很常见，不该逼老师先去删两行。
// 只忽略**尾部**空白；中间出现空行属于漏填，仍然拦下来（否则答案字母会错位得不明显）。
const trimmedOptions = () => {
  const opts = form.options.slice();
  while (opts.length > 2 && !opts[opts.length - 1].text) opts.pop();
  return opts;
};

const validate = () => {
  clearErrors();
  if (!form.stem) errors.stem = '题干不能为空。';

  const opts = trimmedOptions();
  const firstEmpty = opts.findIndex((o) => !o.text);
  if (opts.filter((o) => o.text).length < 2) {
    errors.options = '至少填 2 个选项。';
  } else if (firstEmpty >= 0) {
    errors.options = `选项 ${letter(firstEmpty)} 是空的，请补齐或删掉（只能忽略末尾的空行）。`;
  }

  const correctCount = opts.filter((o) => o.correct).length;
  if (!correctCount) errors.answer = '还没有标记正确答案 —— 点选项左边的圆点。';
  else if (form.type === 'multi' && correctCount < 2) errors.answer = '多选题至少要有 2 个正确答案。';

  if (!form.difficulty) errors.difficulty = '请选难度。';
  if (!form.courseId) errors.courseId = '请选所属课程。';

  // 解析：选填，但写了就必须 5–300 字。
  // ⚠️ 后端 @Size(min=5,max=300) 对**空串**是拒的（长度 0），对 null 才放行
  //    → 所以留空时必须在适配层传 null，不能传空串（否则「不填解析」反而存不进去）。
  const analysis = (form.analysis || '').trim();
  if (analysis.length > 0 && analysis.length < 5) {
    errors.analysis = '解析至少 5 个字（后端要求 5–300 字）。';
  } else if (analysis.length > 300) {
    errors.analysis = '解析不能超过 300 字。';
  }
  return Object.keys(errors).length === 0;
};

const buildPayload = () => {
  const opts = trimmedOptions();
  return {
    type: form.type,
    stem: form.stem,
    options: opts.map((o, i) => ({ key: letter(i), text: o.text })),
    answer: opts.map((o, i) => (o.correct ? letter(i) : null)).filter(Boolean),
    analysis: form.analysis,
    difficulty: form.difficulty,
    courseId: Number(form.courseId),
    knowledgePoints: [...form.knowledgePoints],
    visibility: form.visibility,
  };
};

const resetForm = () => {
  form.type = 'single';
  form.stem = '';
  form.options = [
    { text: '', correct: false },
    { text: '', correct: false },
    { text: '', correct: false },
    { text: '', correct: false },
  ];
  form.analysis = '';
  form.difficulty = '';
  form.knowledgePoints = [];
  // ⚠️ 可见范围也复位：上一题选了「公开」不该让下一题默认跟着公开（默认始终是「仅我」，公开要每次主动选）
  form.visibility = 0;
  clearErrors();
};

const submit = async (continueNew) => {
  if (saving.value) return;
  if (!validate()) {
    toast(Object.values(errors)[0]);
    return;
  }
  saving.value = true;
  try {
    if (isEdit.value) {
      await updateQuestion(editId.value, buildPayload());
      toast('已保存');
      router.push('/teacher/questions');
      return;
    }
    await createQuestion(buildPayload());
    if (continueNew) {
      toast('已入库，可以接着录下一道');
      // 保留「所属课程」与「知识点」：同一批题通常属于同一课程，重复选很烦
      const keepCourse = form.courseId;
      const keepKnowledge = [...form.knowledgePoints];
      resetForm();
      form.courseId = keepCourse;
      form.knowledgePoints = keepKnowledge;
    } else {
      toast('已入库');
      router.push('/teacher/questions');
    }
  } catch (e) {
    toast(e?.message || '保存失败，请重试');
  } finally {
    saving.value = false;
  }
};

const goBack = () => router.push('/teacher/questions');

onMounted(async () => {
  courses.value = await listCourses();

  if (!isEdit.value) return;

  // 编辑态：只有自己的题能改（别人的题在列表里只有「预览 · 引用」）
  try {
    const identity = await getMyIdentity();
    const item = await getQuestion(editId.value);
    if (Number(item.creatorId) !== Number(identity.id)) {
      loadError.value = '这道题不是你出的，只能预览与引用，不能编辑。';
      return;
    }
    form.type = item.type;
    form.stem = item.stem;
    form.options = item.options.map((o) => ({
      text: o.text,
      correct: item.answer.includes(o.key),
    }));
    form.analysis = item.analysis || '';
    form.difficulty = item.difficulty;
    form.courseId = item.courseId;
    form.knowledgePoints = [...item.knowledgePoints];
    form.visibility = Number(item.visibility) === 1 ? 1 : 0;
    await loadKnowledge(item.courseId);
  } catch (e) {
    loadError.value = e?.message || '题目加载失败。';
  }
});
</script>

<style lang="scss" scoped>
.qe {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1096px;
}

// ---- 页头 ----
.qe-head {
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

.qe-back {
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

.qe-error {
  margin: 0;
  padding: 12px 16px;
  border-radius: 12px;
  background: #fff4e0;
  color: #b26a00;
  font-size: 13px;
  line-height: 20px;
}

// ---- 卡片与字段 ----
.qe-card {
  padding: 28px;
}

.qe-field {
  & + & {
    margin-top: 22px;
  }
}

.qe-label {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 18px;
  color: var(--s-ink-2);

  &__note {
    font-size: 12px;
    color: var(--s-ink-3);
  }
}

.qe-req {
  color: var(--s-danger);
}

.qe-hint {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--s-ink-3);
}

.qe-err {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--s-danger);
}

.qe-chips {
  display: flex;
  gap: 8px;

  &--wrap {
    flex-wrap: wrap;
  }
}

.qe-textarea {
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

  &--sm {
    min-height: 76px;
  }
}

.qe-select {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 0;
  border-radius: 10px;
  background: var(--s-soft);
  color: #333;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
}

.qe-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-top: 22px;

  .qe-field + .qe-field {
    margin-top: 0;
  }
}

// ---- 选项行 ----
.qe-opts {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.qe-opt {
  display: flex;
  align-items: center;
  gap: 10px;

  &__mark {
    width: 28px;
    height: 28px;
    flex: 0 0 28px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 0;
    border-radius: 50%;
    background: #f0f0f2;
    color: var(--s-ink-2);
    font-size: 13px;
    font-weight: 600;
    font-family: inherit;
    cursor: pointer;
    transition: background-color 0.16s ease, color 0.16s ease;

    &.is-on {
      background: var(--sa);
      color: #fff;
    }
  }

  &__input {
    flex: 1 1 auto;
    min-width: 0;
    height: 40px;
    padding: 0 14px;
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
}

.qe-opt-foot {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 12px;

  .qe-hint {
    margin: 0;
  }
}

// ---- 页脚 ----
.qe-divider {
  height: 1px;
  margin: 26px 0 20px;
  background: var(--s-hairline);
}

.qe-foot {
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
