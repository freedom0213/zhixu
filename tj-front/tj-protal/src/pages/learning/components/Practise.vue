<!-- 学习练习 -->
<template>
  <div class="practiseWrapper">
    <div class="preview soft-card">
      <div class="previewTit">答题卡</div>
      <div class="previewList">
        <span v-for="(item, index) in params"
          :class="{act: item && item.answers != undefined && String(item.answers) != ''}" :key="index">{{index +
          1}}</span>
      </div>
      <div class="previewSub" @click="submit"><span class="bt">提交试卷</span></div>

    </div>
    <div class="ExQuestions soft-card" v-infinite-scroll="load" style="overflow: auto">
      <!-- 卷头：让学生知道自己正在做哪份卷、多少题、总分多少（以前什么都没有） -->
      <div v-if="paperInfo.name" class="paperBar">
        <span class="paperBar__name">{{ paperInfo.name }}</span>
        <span class="paperBar__meta">
          共 {{ subjectList.length }} 题
          <template v-if="paperInfo.totalScore != null"> · 总分 {{ paperInfo.totalScore }}</template>
          <template v-if="paperInfo.duration"> · 限时 {{ paperInfo.duration }} 分钟</template>
          · 已作答 {{ answeredCount }} / {{ subjectList.length }}
        </span>
      </div>

      <!-- 交卷后的成绩条：这是"我的成绩"，不是"批阅" -->
      <div v-if="isSubmit && result" class="scoreBar">
        已交卷：<strong>{{ result.score ?? '—' }}</strong> / {{ result.totalScore ?? '—' }} 分
        （答对 {{ result.correctCount ?? '—' }} / {{ result.totalCount ?? '—' }} 题）
        · 到「在线考试」里可以查看答卷与解析
      </div>
      <div v-else-if="loadError" class="scoreBar scoreBar--bad">{{ loadError }}</div>

      <div class="questions" v-for="(item, index) in subjectList" :key="index">
        <div class="title fx" v-html="` <p>${index+1}. ${subjectTypeWt(item.type)}</p>${item.name}`"></div>
        <!-- 不同的题型展示不同的题 1：单选题，2：多选题，3：不定向选择题，4：判断题，5：主观题 -->
        <div v-if="item.type == 1">
          <el-radio-group v-model="item.answers" class="ml-4" :disabled="isSubmit">
            <el-radio :label="ind+1" v-for="(it, ind) in item.options" :key="it" size="large">
              <div class="fx"><span>{{upperAlpha(ind)}}</span><span v-html="it"></span></div>
            </el-radio>
          </el-radio-group>
        </div>
        <div v-if="item.type == 2">
          <el-checkbox-group v-model="item.answers" :disabled="isSubmit">
            <el-checkbox v-for="(it, ind) in item.options" :label="ind+1" :key="it">
              <div class="fx"><span>{{upperAlpha(ind)}}</span><span v-html="it"></span></div>
            </el-checkbox>
          </el-checkbox-group>
        </div>
        <div v-if="item.type == 3">
          <el-checkbox-group v-model="item.answers" :disabled="isSubmit">
            <el-checkbox v-for="(it, ind) in item.options" :label="ind+1" :key="it">
              <div class="fx"> <span>{{upperAlpha(ind)}}</span><span v-html="it"></span></div>
            </el-checkbox>
          </el-checkbox-group>
        </div>
        <div v-if="item.type == 4">
          <el-radio-group v-model="item.answers" class="ml-4" :disabled="isSubmit">
            <el-radio :label="true" size="large">A. 正确</el-radio>
            <el-radio :label="false" size="large">B. 错误</el-radio>
          </el-radio-group>
        </div>
        <div v-if="item.type == 5">
          <el-input type="textarea" class="textArea" rows="5" maxlength="200" v-model="item.answers"
            placeholder="请输入正确答案" show-word-limit :disabled="isSubmit"></el-input>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup>
import { computed, onMounted, ref, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { startExamBySection, submitExam } from '@/api/subject.js';
import { dataCacheStore } from "@/store"

const store = dataCacheStore()
const currentPlayData = ref({})

// 接收传过来的参数
const props = defineProps({
  id: {
    type: String,
    default: '',
  },
  examInfo: {
    type: Object
  },
})

const emit = defineEmits(['goHandle', 'playHadle'])
// 生命周期
onMounted(() => {
  currentPlayData.value = JSON.parse(JSON.stringify(store.getCurrentPlayData))
  currentPlayData.value.name = currentPlayData.value.sectionName
  // 根据小节或测试id获取练习题
  getSubjectList()
})
// 添加题型
const subjectTypeWt = (type) => {
  let str = ''
  switch (type) {
    case 1:
      str = ' (选择题) ';
      break
    case 2:
      str = ' (多选题) ';
      break
    case 3:
      str = ' (不定项选择题) ';
      break
    case 4:
      str = ' (判断题) ';
      break
    case 5:
      str = ' (主观题) ';
      break
  }
  return str
}

// 添加 A、B、C、D
const upperAlpha = (num) => {
  let str = ''
  switch (num) {
    case 0:
      str = 'A.';
      break
    case 1:
      str = 'B.';
      break
    case 2:
      str = 'C.';
      break
    case 3:
      str = 'D.';
      break
    case 4:
      str = 'E.';
      break
    case 5:
      str = 'F.';
      break
    case 6:
      str = 'G.';
      break
  }
  return str
}

// 根据小节拉取试卷（p14：走真实接口，返回的题目**不含答案**）
const subjectList = ref([])
const examId = ref('')
const paperInfo = ref({ name: '', totalScore: null, duration: null })
const loadError = ref('')
const submitting = ref(false)
const result = ref(null)

const getSubjectList = async () => {
  loadError.value = ''
  try {
    const sectionId = props.examInfo?.sectionId
    if (!sectionId) throw new Error('缺少小节信息，无法拉取试卷')
    const data = await startExamBySection(sectionId)
    examId.value = data.examId
    paperInfo.value = { name: data.name, totalScore: data.totalScore, duration: data.duration }
    // 每题补一个 answers：多选给数组、单选/判断给标量位（点选后才有效值）
    subjectList.value = (data.questions || []).map((q) => ({
      ...q,
      answers: q.type === 2 || q.type === 3 ? [] : undefined,
    }))
  } catch (e) {
    loadError.value = e?.message || '获取试卷失败'
    subjectList.value = []
    ElMessage({ message: loadError.value, type: 'error' })
  }
}

// 是否已经提交试卷
const isSubmit = ref(false)

/** 一题是否作答过（单选是标量、多选是数组，两种都要认） */
const hasAnswer = (q) => {
  const a = q?.answers
  if (a === null || a === undefined || a === '') return false
  if (Array.isArray(a)) return a.length > 0
  return true
}
const answeredCount = computed(() => subjectList.value.filter(hasAnswer).length)

// 确认提交试卷
const params = subjectList
const submit = () => {
  if (isSubmit.value || submitting.value) return
  if (!subjectList.value.length) {
    ElMessage({ message: '还没有题目可以提交', type: 'warning' })
    return
  }
  if (answeredCount.value < subjectList.value.length) {
    ElMessageBox.confirm(
      `还有 ${subjectList.value.length - answeredCount.value} 道题没作答，是否要提交答卷。`,
      '确认交卷',
      {
        confirmButtonText: '确认提交',
        cancelButtonText: '我再看看',
        type: 'warning',
      }
    )
      .then(() => {
        postSubjectHandle()
      })
      .catch(() => {
      })
  } else {
    postSubjectHandle()
  }
}

const postSubjectHandle = async () => {
  if (isSubmit.value || submitting.value) return
  const param = params.value.map(el => {
    // ⚠️ 单选 / 判断的 v-model 是**标量**（数字或布尔），只有多选才是数组。
    //    老代码只处理 Array，单选会被过滤成空串 → 单选永远判错。这里两种都收。
    const raw = el.answers
    const answersArray = Array.isArray(raw) ? raw : (raw === null || raw === undefined || raw === '' ? [] : [raw])

    const validAnswers = answersArray
      .filter(answer => answer !== null && answer !== undefined && answer !== '')
      .map(answer => parseInt(answer, 10))
      .filter(n => !Number.isNaN(n))

    // 去重 + 升序：与题库标准答案（"1,3"）同口径
    const sortedAnswers = Array.from(new Set(validAnswers)).sort((a, b) => a - b).join(',')

    return {
      questionId: el.id,
      answer: sortedAnswers,
      questionType: el.type
    };
  });

  submitting.value = true
  try {
    const res = await submitExam(examId.value, param)
    isSubmit.value = true
    result.value = res
    ElMessage({
      message: `交卷成功：${res.score ?? '—'} / ${res.totalScore ?? '—'} 分（答对 ${res.correctCount ?? '—'} / ${res.totalCount ?? '—'} 题）`,
      type: 'success'
    });
    emit('playHadle', { item: currentPlayData.value, tp: '9' });
  } catch (e) {
    // 失败时**保留学生已填的答案**，只提示原因（不清空作答）
    ElMessage({ message: e?.message || '交卷失败', type: 'error' })
  } finally {
    submitting.value = false
  }
};

// 组件卸载前提醒交卷
onBeforeUnmount(() => {
  leaveConfirm()
})
// 已经答题但没提交前的效验
function leaveConfirm() {
  if (isSubmit.value || !subjectList.value.length) {
    return false;
  }
  if (answeredCount.value > 0) {
    ElMessageBox.confirm(
      `已作答 ${answeredCount.value} 道题但还没交卷，是否现在提交？`,
      '确认交卷',
      {
        confirmButtonText: '确认提交',
        cancelButtonText: '我再看看',
        type: 'warning',
      }
    )
      .then(() => {
        postSubjectHandle()
      })
      .catch(() => {
      })
  }
}
const load = () => { }
</script>
<style lang="scss" scoped>
// 新学员端是浅色画布。这段原来是照老深色学习页写的（黑底 + height:100vh-60 + 内边距 30），
// 放进新壳里会变成「黑框里贴两张白纸」，而且比壳内可视区多出 60px、把整页顶出滚动条。
// 现在改成：容器透明、两栏用设计系统的 soft-card 呈现（与右侧目录卡同一套卡片语言）。
.practiseWrapper {
  background: transparent;
  color: #1d1d1f;
  padding: 0;
  display: flex;
  gap: 16px;
  align-items: flex-start;

  // 卷头：卷名 + 题量/总分/限时/已作答
  .paperBar {
    background: #fff;
    border-radius: 8px;
    padding: 14px 18px;
    margin-bottom: 14px;
    display: flex;
    align-items: baseline;
    gap: 14px;
    flex-wrap: wrap;

    &__name {
      font-size: 16px;
      font-weight: 600;
    }
    &__meta {
      font-size: 13px;
      color: #6e6e73;
    }
  }

  // 交卷后的成绩条（学生的成绩，不是"批阅"）
  .scoreBar {
    background: #eef7ee;
    border: 1px solid #cfe8cf;
    color: #1e5b1e;
    border-radius: 8px;
    padding: 14px 18px;
    margin-bottom: 14px;
    font-size: 14px;

    strong {
      font-size: 18px;
    }

    &--bad {
      background: #fdecec;
      border-color: #f3c6c6;
      color: #8a1f1f;
    }
  }

  .preview {
    flex: 0 0 188px;
    width: 188px;
    height: max-content;
    padding: 15px;

    .previewTit {
      font-size: 14px;
      color: var(--color-font1);
      margin-bottom: 20px;
    }

    .previewList {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-start;
      margin-bottom: 30px;

      span {
        display: inline-block;
        width: 22px;
        line-height: 22px;
        border: 1px solid #80878C;
        color: #80878C;
        border-radius: 2px;
        margin-right: 10px;
        margin-bottom: 10px;
        text-align: center;
      }

      span:nth-child(5n) {
        margin-right: 0px;
      }

      .act {
        color: #fff;
        background: #27BA9B;
        border: 1px solid #27BA9B;
      }
    }

    .previewSub {
      width: 80px;
      height: 28px;
      border-radius: 3px;
      margin: 0 auto;

      .bt {
        font-size: 14px;
        line-height: 28px;
      }
    }
  }

  .ExQuestions {
    flex: 1;
    min-width: 0;
    padding: 24px 30px;
    // 题目多的时候在卡片内部滚（容器不再写死 100vh，这里给它一个可视高度上限）
    max-height: calc(100vh - 210px);

    .textArea {
      width: 100%;
    }

    // 选项统一「一行一个」。
    // Element 的 .el-radio/.el-checkbox 默认是 inline-flex 且带 margin-right:32px，
    // 最后一项的右外边距会多算进 scrollWidth（实测 464 > 432），于是卡片下沿多出一条
    // 横向滚动条；长题干下它们本来也会互相挤。这里改成块级、去掉右外边距。
    :deep(.el-radio-group),
    :deep(.el-checkbox-group) {
      display: block;
      margin-left: 0;
    }
    :deep(.el-radio),
    :deep(.el-checkbox) {
      display: flex;
      margin-right: 0;
      white-space: normal;
    }

    .title {
      margin: 16px 0;
    }
  }
}
</style>
