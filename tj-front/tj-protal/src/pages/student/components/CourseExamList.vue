<!--
 * 课程试卷列表（学生视角）— P17
 * -----------------------------------------------------------------------------
 * 学生端**两处共用**这一个组件（用户拍板：同一份数据、同一套卡片，少维护一份）：
 *   ① 课程学习页右侧「考试」页签    ② 课程详情页的「课程试卷」
 *
 * 数据源：GET /es/exams/published?courseId= —— 只返回**已发布**的试卷，
 * 并带上「我的作答状态」，所以列表上就能看出哪场没考、哪场考了多少分。
 *
 * 点一场 → 答题页（/student/exams/answer?examId=）；已交卷的 → 答卷回看。
 -->
<template>
  <div class="cel">
    <p v-if="loading" class="cel__state">正在读取课程试卷…</p>

    <p v-else-if="error" class="cel__state cel__state--err">{{ error }}</p>

    <div v-else-if="!list.length" class="cel__empty">
      <p class="cel__emptyTitle">这门课还没有试卷</p>
      <p class="cel__emptyText">等讲师发布「第一章检测」这类试卷后，这里就会出现。</p>
    </div>

    <ul v-else class="cel__list">
      <li v-for="e in list" :key="e.examId" class="cel__item">
        <button class="cel__card" type="button" :disabled="locked(e)" @click="open(e)">
          <span class="cel__top">
            <span class="cel__name">{{ e.name }}</span>
            <span class="cel__badge" :class="badgeClass(e)">{{ badgeText(e) }}</span>
          </span>
          <span class="cel__meta">
            共 {{ e.questionCount }} 题
            <template v-if="e.totalScore"> · 满分 {{ e.totalScore }}</template>
            <template v-if="e.duration"> · 限时 {{ e.duration }} 分钟</template>
            <template v-else> · 不限时</template>
            <template v-if="e.passScore"> · 及格 {{ e.passScore }} 分</template>
          </span>
          <span class="cel__action">{{ actionText(e) }}</span>
        </button>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { pageCourseExams } from '@/api/subject.js';

const props = defineProps({
  courseId: { type: [String, Number], default: null },
});

const router = useRouter();
const loading = ref(true);
const error = ref('');
const list = ref([]);

/** 已交卷 / 已复核 = 有成绩可看；进行中（0）= 还没交，继续作答 */
const done = (e) => e.myStatus != null && Number(e.myStatus) >= 1;

// =============================================================================
// 时间窗口（P38）
// -----------------------------------------------------------------------------
// 服务端**已经**会驳回窗口外的进入（那是权威口径）；这里只做一件事：
// 提前把状态说清楚，别让卡片写着「开始答题」、点进去才吃一条报错。
// ⚠️ 已交卷的仍然可点（要能看答卷）→「锁定」= 没交过 且 不在窗口内。
// =============================================================================
const now = ref(Date.now());
let ticker = null;
onMounted(() => {
  // 半分钟刷一次就够：只为了让一直开着的页面能把「已结束」标出来
  ticker = setInterval(() => {
    now.value = Date.now();
  }, 30000);
});
onUnmounted(() => {
  if (ticker) clearInterval(ticker);
});

/** 后端给的是 "yyyy-MM-dd HH:mm:ss"；空 = 不限 */
const toTs = (v) => (v ? new Date(String(v).replace(' ', 'T')).getTime() : null);

const phase = (e) => {
  const t = now.value;
  const s = toTs(e.startAt);
  const en = toTs(e.endAt);
  if (s != null && t < s) return 'before';
  if (en != null && t > en) return 'after';
  return 'open';
};

const locked = (e) => !done(e) && phase(e) !== 'open';

const actionText = (e) => {
  if (done(e)) return '查看答卷';
  const p = phase(e);
  if (p === 'before') return `未开考（${String(e.startAt || '').slice(5, 16)}）`;
  if (p === 'after') return '已结束';
  return '开始答题';
};

const badgeText = (e) => {
  if (!done(e)) {
    const p = phase(e);
    if (p === 'before') return '未开考';
    if (p === 'after') return '已结束';
    return e.myStatus === 0 ? '进行中' : '未作答';
  }
  const score = e.myScore == null ? '—' : e.myScore;
  const total = e.myTotalScore == null ? '' : ` / ${e.myTotalScore}`;
  return `${score}${total} 分 · ${e.myPassed === 1 ? '及格' : '未及格'}`;
};
const badgeClass = (e) => {
  if (!done(e)) return phase(e) === 'open' ? 'is-todo' : 'is-closed';
  return e.myPassed === 1 ? 'is-pass' : 'is-fail';
};

const open = (e) => {
  if (locked(e)) return;   // 窗口外不让点（服务端也会驳回，这里只是别白跑一趟）
  if (done(e)) {
    router.push({ path: '/student/exams/review', query: { id: e.examId } });
    return;
  }
  router.push({ path: '/student/exams/answer', query: { examId: e.examId } });
};

const load = async () => {
  if (!props.courseId) {
    loading.value = false;
    list.value = [];
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const res = await pageCourseExams(props.courseId);
    list.value = Array.isArray(res) ? res : [];
  } catch (e) {
    error.value = e?.message || '课程试卷读取失败';
    list.value = [];
  } finally {
    loading.value = false;
  }
};

onMounted(load);
// 课程切换要重载（学习页与详情页都是靠 query.id 表达"哪门课"）
watch(() => props.courseId, load);
</script>

<style lang="scss" scoped>
.cel {
  &__state {
    padding: 20px 0;
    font-size: 13px;
    color: #6e6e73;
    text-align: center;

    &--err {
      color: #c0392b;
    }
  }

  &__empty {
    padding: 24px 12px;
    text-align: center;
  }
  &__emptyTitle {
    margin: 0 0 6px;
    font-size: 13px;
    font-weight: 600;
    color: #1d1d1f;
  }
  &__emptyText {
    margin: 0;
    font-size: 12px;
    line-height: 20px;
    color: #86868b;
  }

  &__list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  &__card {
    display: flex;
    flex-direction: column;
    gap: 6px;
    width: 100%;
    padding: 14px 16px;
    border: 0;
    border-radius: 14px;
    background: #fafafc;
    box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.06);
    font-family: inherit;
    text-align: left;
    cursor: pointer;
    transition: background-color 0.14s ease, box-shadow 0.14s ease;

    &:hover {
      background: #f4f4f7;
      box-shadow: inset 0 0 0 1px rgba(0, 102, 204, 0.35);
    }
    // 窗口外（未开考 / 已结束）：不给点，也别让它看起来能点
    &:disabled {
      cursor: not-allowed;
      opacity: 0.66;
    }
    &:disabled:hover {
      background: #fafafc;
      box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.06);
    }
  }

  &__top {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  &__name {
    flex: 1 1 auto;
    min-width: 0;
    font-size: 14px;
    font-weight: 600;
    color: #1d1d1f;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  &__badge {
    flex: 0 0 auto;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 11px;
    line-height: 18px;

    &.is-todo {
      background: var(--s-soft, #f5f5f7);
      color: #6e6e73;
    }
    &.is-pass {
      background: rgba(29, 138, 67, 0.1);
      color: #1d8a43;
    }
    &.is-fail {
      background: rgba(178, 106, 0, 0.12);
      color: #b26a00;
    }
    &.is-closed {
      background: rgba(134, 134, 139, 0.14);
      color: #86868b;
    }
  }
  &__meta {
    font-size: 12px;
    color: #6e6e73;
  }
  &__action {
    font-size: 12px;
    color: var(--sa, #0066cc);
  }
}
</style>
