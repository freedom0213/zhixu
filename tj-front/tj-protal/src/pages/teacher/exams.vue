<!--
 * 教师端 · 考试管理（设计稿 T3）
 * -----------------------------------------------------------------------------
 * 这是「出卷」的入口页：右上角「新建考试」进三步向导，草稿行点「继续编辑」回到向导。
 *
 * 关于状态的三个刻意决定：
 *   1) **草稿可删，已发布不可删** —— 已发布的有作答记录，删掉会让成绩凭空消失。
 *   2) **通过率为空时不显示 0%**，显示「—」。0% 和「还没考完」是两件完全不同的事，
 *      用 0 表示会让老师以为全班都挂了。
 *   3) 操作列按状态分叉，不把「去批改」这种当下无意义的动作也摆出来。
-->
<template>
  <div class="x">
    <div class="x-head">
      <div class="x-head__text">
        <h2 class="s-h2">考试管理</h2>
        <p class="x-head__sub">
          共 {{ counts.all }} 场：草稿 {{ counts.draft }} · 已发布 {{ counts.published }} · 已结束 {{ counts.closed }}。
          新建考试走「考试信息 → 组卷 → 发布设置」三步。
        </p>
      </div>
      <button class="q-btn q-btn--primary" type="button" @click="router.push('/teacher/exams/new')">＋ 新建考试</button>
    </div>

    <section class="s-card x-card">
      <div class="x-filters">
        <label class="x-search">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <circle cx="7.1" cy="7.1" r="4.4" stroke="currentColor" stroke-width="1.6" />
            <path d="m10.6 10.6 2.6 2.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
          </svg>
          <input v-model.trim="keyword" type="search" placeholder="搜索考试名称或课程" aria-label="搜索考试" @keyup.enter="reload(1)" />
        </label>
        <div class="x-chips" role="group" aria-label="按状态筛选">
          <button
            v-for="chip in statusChips"
            :key="chip.value || 'all'"
            class="q-chip"
            :class="{ 'is-on': status === chip.value }"
            type="button"
            @click="setStatus(chip.value)"
          >
            {{ chip.label }}
          </button>
        </div>
      </div>

      <div class="x-table">
        <div class="x-row x-row--head">
          <span class="x-c">考试</span>
          <span class="x-c">状态</span>
          <span class="x-c">题量 / 总分</span>
          <span class="x-c">截止时间</span>
          <span class="x-c">提交</span>
          <span class="x-c">通过率</span>
          <span class="x-c x-c--act">操作</span>
        </div>

        <p v-if="loading" class="x-state">加载中…</p>
        <p v-else-if="!list.length" class="x-state">还没有考试。点右上角「＋ 新建考试」开始。</p>

        <div v-for="item in list" v-else :key="item.id" class="x-row">
          <span class="x-c x-c--name">
            <strong>{{ item.name }}</strong>
            <em>{{ metaLine(item) }}</em>
          </span>
          <span class="x-c"><span class="x-tag" :class="`is-${item.status}`">{{ STATUS_LABEL[item.status] }}</span></span>
          <span class="x-c x-c--muted">{{ item.items.length }} 题 / {{ totalScore(item) }} 分</span>
          <span class="x-c x-c--muted">{{ item.endAt || '未设定' }}</span>
          <span class="x-c x-c--muted">{{ item.status === 'draft' ? '—' : `${item.submittedCount} 人` }}</span>
          <span class="x-c" :class="rateClass(item)">
            {{ item.passRate === null || item.passRate === undefined ? '—' : `${Math.round(item.passRate * 100)}%` }}
          </span>
          <span class="x-c x-c--act">
            <template v-if="item.status === 'draft'">
              <button class="q-act" type="button" @click="router.push(`/teacher/exams/new?id=${item.id}`)">继续编辑</button>
              <button class="q-act q-act--muted" type="button" @click="askRemove(item)">删除</button>
            </template>
            <!--
              有学生交卷才给「去批改」。
              ⚠️ 原来这里判的是 status === 'marking'，但**后端从来没有把卷子置成"批改中"**
                 （exam.status 只有 0 草稿 / 1 已发布 / 3 停用）→ 这个分支永远进不去，
                 604 行的批改页实际上一个入口都没有（P18 修）。
            -->
            <template v-else-if="item.status === 'marking' || item.submittedCount > 0">
              <button class="q-act" type="button" @click="router.push(`/teacher/exams/${item.id}/marking`)">去批改</button>
              <button class="q-act" type="button" @click="goStats(item)">统计</button>
            </template>
            <template v-else>
              <button class="q-act" type="button" @click="goStats(item)">查看成绩</button>
            </template>
          </span>
        </div>
      </div>

      <div class="x-foot">
        <span class="x-foot__info">共 {{ total }} 场 · 每页 {{ size }} 条</span>
        <div class="x-pager">
          <button class="x-pager__btn" type="button" :disabled="page <= 1" @click="reload(page - 1)">‹</button>
          <button
            v-for="p in pageList"
            :key="p"
            class="x-pager__btn"
            :class="{ 'is-on': p === page }"
            type="button"
            @click="reload(p)"
          >
            {{ p }}
          </button>
          <button class="x-pager__btn" type="button" :disabled="page >= pageCount" @click="reload(page + 1)">›</button>
        </div>
      </div>
    </section>

    <!-- 删除确认：草稿才可删 -->
    <div v-if="removeTarget" class="q-dialog" role="dialog" aria-modal="true" aria-label="删除确认">
      <div class="q-dialog__panel">
        <h3 class="q-dialog__title">删除这份草稿？</h3>
        <p class="q-dialog__desc">「{{ removeTarget.name }}」还没发布过，删除不会影响任何人。删除后不可恢复。</p>
        <div class="q-dialog__foot">
          <button class="q-btn" type="button" @click="removeTarget = null">取消</button>
          <button class="q-btn q-btn--primary" type="button" @click="doRemove">确认删除</button>
        </div>
      </div>
    </div>

    <transition name="q-fade">
      <p v-if="notice" class="q-toast" role="status">{{ notice }}</p>
    </transition>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { listExams, countExams, removeExam } from '@/api/teacher/exams';
import { EXAM_STATUS_LABEL as STATUS_LABEL } from '@/config/teacherDict';

const router = useRouter();

const counts = reactive({ all: 0, draft: 0, published: 0, closed: 0 });
/**
 * 列表副标题。随堂练习（exam_type=2）**没有时长**——它是老师在建课向导里配出来的，
 * 不能写死"X 分钟"，也不能显示 "null 分钟"；这里顺带把类型标出来，
 * 免得老师以为它和正式试卷一样能进向导改（它由配题驱动，改了也会被覆盖）。
 */
const metaLine = (item) => {
  const parts = [item.courseName || '—'];
  if (item.examType === 'practice') parts.push('随堂练习');
  if (item.duration) parts.push(`${item.duration} 分钟`);
  if (item.passScore) parts.push(`及格 ${item.passScore} 分`);
  return parts.join(' · ');
};

const status = ref('');
const keyword = ref('');
const list = ref([]);
const total = ref(0);
const page = ref(1);
const size = 20;
const loading = ref(false);
const notice = ref('');
const removeTarget = ref(null);
let noticeTimer = null;

const toast = (text) => {
  notice.value = text;
  clearTimeout(noticeTimer);
  noticeTimer = setTimeout(() => {
    notice.value = '';
  }, 2600);
};

const statusChips = computed(() => [
  { value: '', label: `全部 ${counts.all}` },
  { value: 'draft', label: `草稿 ${counts.draft}` },
  { value: 'published', label: `已发布 ${counts.published}` },
  { value: 'closed', label: `已结束 ${counts.closed}` },
]);

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

// 分值是试卷的属性，所以总分要在这一页算出来（题库里没有分值）
const totalScore = (exam) => exam.items.reduce((sum, x) => sum + Number(x.score || 0), 0);

// 通过率为空 ≠ 0%
const rateClass = (exam) => {
  if (exam.passRate === null || exam.passRate === undefined) return 'x-c--muted';
  return exam.passRate < 0.6 ? 'is-warn' : 'is-ok';
};

const reload = async (targetPage = page.value) => {
  loading.value = true;
  try {
    const res = await listExams({ status: status.value, keyword: keyword.value, page: targetPage, size });
    list.value = res.list;
    total.value = res.total;
    page.value = res.page;
  } catch (e) {
    toast('加载失败，请稍后重试');
  } finally {
    loading.value = false;
  }
};

const refreshCounts = async () => {
  Object.assign(counts, await countExams());
};

const setStatus = (v) => {
  if (status.value === v) return;
  status.value = v;
  reload(1);
};

const goStats = (exam) => {
  router.push({ path: `/teacher/exams/${exam.id}/stats` });
};

const askRemove = (exam) => {
  removeTarget.value = exam;
};

const doRemove = async () => {
  const target = removeTarget.value;
  removeTarget.value = null;
  try {
    await removeExam(target.id);
    toast('草稿已删除');
    await Promise.all([reload(), refreshCounts()]);
  } catch (e) {
    toast(e?.message || '删除失败');
  }
};

onMounted(async () => {
  await Promise.all([reload(1), refreshCounts()]);
});
</script>

<style lang="scss" scoped>
.x {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1096px;
}

.x-head {
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
}

.x-card {
  padding: 24px;
}

.x-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--s-hairline);
}

.x-search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 260px;
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

.x-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.x-table {
  --x-cols: minmax(220px, 1fr) 72px 96px 132px 72px 80px 156px;
}

.x-row {
  display: grid;
  grid-template-columns: var(--x-cols);
  align-items: center;
  min-height: 56px;
  border-bottom: 1px solid var(--s-divider);
  font-size: 12px;
  line-height: 18px;
  color: #333;

  &--head {
    min-height: 40px;
    border-bottom-color: var(--s-hairline);
    color: var(--s-ink-3);
  }
}

.x-c {
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
    }
    em {
      font-size: 12px;
      font-style: normal;
      color: var(--s-ink-3);
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

.x-tag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  border-radius: 11px;
  font-size: 11px;
  font-weight: 500;

  &.is-draft {
    background: #f2f2f4;
    color: var(--s-ink-2);
  }
  &.is-published {
    background: #e8f1fc;
    color: #0066cc;
  }
  &.is-marking {
    background: #fff4e0;
    color: #b26a00;
  }
  &.is-closed {
    background: #e6f6ec;
    color: #1d8a43;
  }
}

.x-state {
  margin: 0;
  padding: 56px 0;
  text-align: center;
  font-size: 13px;
  color: var(--s-ink-3);
}

.x-foot {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 16px;

  &__info {
    font-size: 12px;
    color: var(--s-ink-3);
  }
}

.x-pager {
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
