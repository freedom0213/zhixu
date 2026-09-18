<!--
 * 教师端 · 试卷批改（设计稿 T5）
 * -----------------------------------------------------------------------------
 * 这个页面回答一个问题：**我现在有哪些卷子要处理**。
 *
 * 三件事刻意这么做：
 *   1) 只列**有人交过卷**的场次。没交卷的场次放进来只会稀释注意力（想要总览去考试管理）。
 *   2) 数字**从明细数**（后端已按 exam_record 明细算好），页面不再自己算一遍 ——
 *      顶部「待复核 3」和下面每行加起来必须永远相等。
 *   3) 行里的按钮按「有没有活」分叉：待复核 > 0 才把「去批改」做成主按钮，
 *      全复核完的只给「查看成绩」——不摆当下没意义的动作。
 *
 * ⚠️ 题库只有选择题、判分在交卷时自动完成，所以这里的"批改"实际是**复核确认**：
 *    讲师核对成绩无误 → 学生端该条记录标记为已复核。页面底部写明了这一点，
 *    免得老师以为要自己判分。
-->
<template>
  <div class="rv">
    <div class="rv-head">
      <div class="rv-head__text">
        <h2 class="s-h2">试卷批改</h2>
        <p class="rv-head__sub">
          <template v-if="summary.pendingCount > 0">
            有 <strong>{{ summary.pendingCount }}</strong> 份答卷待复核，分布在 {{ summary.examCount }} 场考试里。
          </template>
          <template v-else-if="summary.submittedCount > 0">
            {{ summary.submittedCount }} 份答卷全部复核完毕，没有积压。
          </template>
          <template v-else>现在没有要批改的卷子。</template>
        </p>
      </div>
      <button class="q-btn" type="button" @click="router.push('/teacher/exams')">去考试管理</button>
    </div>

    <div class="rv-stats">
      <div class="s-card rv-stat">
        <p class="rv-stat__cap">待复核</p>
        <p class="rv-stat__num" :class="{ 'is-warn': summary.pendingCount > 0 }">{{ summary.pendingCount }}</p>
        <p class="rv-stat__note">份答卷</p>
      </div>
      <div class="s-card rv-stat">
        <p class="rv-stat__cap">已交卷</p>
        <p class="rv-stat__num">{{ summary.submittedCount }}</p>
        <p class="rv-stat__note">份（含已复核）</p>
      </div>
      <div class="s-card rv-stat">
        <p class="rv-stat__cap">涉及考试</p>
        <p class="rv-stat__num">{{ summary.examCount }}</p>
        <p class="rv-stat__note">场</p>
      </div>
    </div>

    <section class="s-card rv-card">
      <div class="rv-table">
        <div class="rv-row rv-row--head">
          <span class="rv-c">考试</span>
          <span class="rv-c">题量 / 总分</span>
          <span class="rv-c">已交卷</span>
          <span class="rv-c">待复核</span>
          <span class="rv-c">最近交卷</span>
          <span class="rv-c rv-c--act">操作</span>
        </div>

        <p v-if="loading" class="rv-state">加载中…</p>
        <p v-else-if="error" class="rv-state rv-state--err">{{ error }}</p>
        <p v-else-if="!list.length" class="rv-state">
          还没有学生交卷，所以没有待批改的卷子。<br />
          学生在「课程学习页 → 考试」或课程详情页交卷后，会立刻出现在这里。
        </p>

        <div v-for="item in list" v-else :key="item.examId" class="rv-row">
          <span class="rv-c rv-c--name">
            <strong>{{ item.examName }}</strong>
            <em>{{ item.courseName || '未关联课程' }}</em>
          </span>
          <span class="rv-c rv-c--muted">{{ item.questionCount }} 题 / {{ item.totalScore }} 分</span>
          <span class="rv-c rv-c--muted">{{ item.submittedCount }} 份</span>
          <span class="rv-c" :class="item.pendingCount > 0 ? 'is-warn' : 'is-ok'">
            {{ item.pendingCount > 0 ? `${item.pendingCount} 份待复核` : '已全部复核' }}
          </span>
          <span class="rv-c rv-c--muted">{{ item.lastSubmitAt || '—' }}</span>
          <span class="rv-c rv-c--act">
            <button
              class="q-act"
              :class="{ 'is-primary': item.pendingCount > 0 }"
              type="button"
              @click="goMarking(item)"
            >
              去批改
            </button>
            <button class="q-act q-act--muted" type="button" @click="goStats(item)">查看成绩</button>
          </span>
        </div>
      </div>
    </section>

    <p class="rv-note">
      题目都是选择题，交卷时已自动判分；这里的「批改」是**复核**——你核对成绩无误后，学生端该份答卷会标记为已复核。
    </p>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getPendingReview } from '@/api/teacher/exams';

const router = useRouter();
const loading = ref(true);
const error = ref('');
const list = ref([]);
const summary = reactive({ examCount: 0, pendingCount: 0, submittedCount: 0 });

const load = async () => {
  loading.value = true;
  error.value = '';
  try {
    const res = await getPendingReview();
    list.value = res.items || [];
    summary.examCount = res.examCount ?? list.value.length;
    summary.pendingCount = res.pendingCount ?? 0;
    summary.submittedCount = res.submittedCount ?? 0;
  } catch (e) {
    // 服务端原话直接给用户（统一 call() 抛出的中文原因）
    error.value = e?.message || '读取待批改列表失败';
    list.value = [];
    summary.examCount = 0;
    summary.pendingCount = 0;
    summary.submittedCount = 0;
  } finally {
    loading.value = false;
  }
};

const goMarking = (item) => router.push(`/teacher/exams/${item.examId}/marking`);
const goStats = (item) => router.push({ path: `/teacher/exams/${item.examId}/stats` });

onMounted(load);
</script>

<style lang="scss" scoped>
// ⚠️ 页面内不用与外壳同名的类（.main/.side/.body/.card），一律 rv- 前缀
.rv {
  --rv-cols: minmax(200px, 1.6fr) 110px 90px 120px 150px 180px;
}

.rv-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;

  &__text {
    min-width: 0;
  }
  &__sub {
    margin: 6px 0 0;
    font-size: 13px;
    line-height: 20px;
    color: var(--s-ink-2);

    strong {
      font-weight: 600;
      color: var(--s-ink);
    }
  }
}

.rv-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.rv-stat {
  padding: 16px 20px;

  &__cap {
    margin: 0;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink-3);
  }
  &__num {
    margin: 6px 0 0;
    font-size: 26px;
    font-weight: 600;
    line-height: 32px;
    color: var(--s-ink);

    // 有待办时才用强调色：0 也高亮会让人以为出错了
    &.is-warn {
      color: #b26a00;
    }
  }
  &__note {
    margin: 4px 0 0;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink-3);
  }
}

.rv-card {
  padding: 0;
  overflow: hidden;
}

.rv-table {
  padding: 8px 24px 4px;
}

.rv-row {
  display: grid;
  grid-template-columns: var(--rv-cols);
  align-items: center;
  min-height: 56px;
  border-bottom: 1px solid var(--s-divider);
  font-size: 12px;
  line-height: 18px;
  color: #333;

  &:last-child {
    border-bottom: 0;
  }

  &--head {
    min-height: 40px;
    border-bottom-color: var(--s-hairline);
    color: var(--s-ink-3);
  }
}

.rv-c {
  padding-right: 8px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;

  &--name {
    display: flex;
    flex-direction: column;
    gap: 2px;

    strong {
      font-size: 13px;
      font-weight: 500;
      color: var(--s-ink);
      overflow: hidden;
      text-overflow: ellipsis;
    }
    em {
      font-size: 12px;
      font-style: normal;
      color: var(--s-ink-3);
      overflow: hidden;
      text-overflow: ellipsis;
    }
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
  &.is-ok {
    color: #1d8a43;
  }
  &.is-warn {
    color: #b26a00;
  }
}

// 有待复核时「去批改」是这一行的主行动
.q-act.is-primary {
  color: var(--sa, #0066cc);
  font-weight: 500;
}

.rv-state {
  margin: 0;
  padding: 28px 0;
  text-align: center;
  font-size: 13px;
  line-height: 22px;
  color: var(--s-ink-3);

  &--err {
    color: #c0392b;
  }
}

.rv-note {
  margin: 12px 2px 0;
  font-size: 12px;
  line-height: 20px;
  color: var(--s-ink-3);
}
</style>
