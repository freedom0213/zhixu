<!--
 * 教师端 · 工作概览（设计稿 T1）
 * -----------------------------------------------------------------------------
 * 聚合页的铁律：**只做搬运，不做计算的第二份**。
 * 待复核 / 缺考 / 草稿数从提交记录反推（和批改页同一个数字），
 * 近期考试的通过率和统计页同一个算法 —— 老师从概览点进去，
 * 看到的数字必须和落地页一致，否则概览就失去了「可信入口」的价值。
-->
<template>
  <div class="db">
    <!-- 问候 -->
    <div class="db-head">
      <div>
        <h2 class="s-h2">{{ greeting }}，{{ overview.teacher?.name || '老师' }}</h2>
        <!-- 平台不是学校系统：不再显示「学校名」 -->
        <p class="db-head__sub">{{ todayText }}</p>
      </div>
      <div class="db-head__actions">
        <button class="q-btn" type="button" @click="$router.push('/teacher/questions/new')">＋ 录一道题</button>
        <button class="q-btn q-btn--primary" type="button" @click="$router.push('/teacher/exams/new')">＋ 新建考试</button>
      </div>
    </div>

    <p v-if="loading" class="db-state">加载中…</p>

    <template v-else>
      <!-- 教学资产 -->
      <div class="db-cards">
        <div class="s-card db-card">
          <p class="db-card__label">我的课程</p>
          <p class="db-card__num">{{ nz(overview.teaching.courses) }}</p>
          <!-- 学生数没有聚合来源（要「选课名单」）→ 显示「—」 -->
          <p class="db-card__note">学生 {{ nz(overview.teaching.students) }} 人</p>
        </div>
        <div class="s-card db-card">
          <p class="db-card__label">我的题目</p>
          <p class="db-card__num">{{ nz(overview.teaching.questions) }}</p>
          <p class="db-card__note">平台题库另有共享题可引用</p>
        </div>
        <div class="s-card db-card">
          <p class="db-card__label">考试</p>
          <p class="db-card__num">{{ nz(overview.teaching.exams) }}</p>
          <p class="db-card__note">草稿 {{ nz(overview.todo.drafts) }} 场待发布</p>
        </div>
        <div class="s-card db-card db-card--accent">
          <p class="db-card__label">待复核成绩</p>
          <p class="db-card__num">{{ nz(overview.todo.toReview) }}</p>
          <p class="db-card__note">已交卷 {{ nz(overview.todo.submitted) }} 份</p>
        </div>
      </div>

      <div class="db-cols">
        <!-- 待办 -->
        <section class="s-card db-card">
          <h3 class="db-card__title">需要处理</h3>
          <!-- 只列**当下真有数**的待办：数字为 0 / 拿不到就不摆（摆了只是噪音）。
               「缺考登记」这条已删：后端没有这个概念，学生数/应考数都无来源。 -->
          <button v-if="overview.todo.toReview" class="db-todo" type="button" @click="$router.push('/teacher/review')">
            <span class="db-todo__text">{{ overview.todo.toReview }} 份答卷待复核</span>
            <span class="db-todo__go">去批改</span>
          </button>
          <button v-if="overview.todo.drafts" class="db-todo" type="button" @click="$router.push('/teacher/exams')">
            <span class="db-todo__text">{{ overview.todo.drafts }} 份试卷还是草稿</span>
            <span class="db-todo__go">继续编辑</span>
          </button>
          <p v-if="!overview.todo.toReview && !overview.todo.drafts" class="db-todoEmpty">
            现在没有要处理的事 —— 录一道题、或新建一场考试都会出现在这里。
          </p>
        </section>

        <!-- 近期考试 -->
        <section class="s-card db-card">
          <div class="db-secHead">
            <h3 class="db-card__title">近期考试</h3>
            <button class="q-act" type="button" @click="$router.push('/teacher/exams')">全部</button>
          </div>
          <div v-for="ex in overview.recentExams" :key="ex.id" class="db-exam">
            <div class="db-exam__text">
              <p class="db-exam__name">{{ ex.name }}</p>
              <p class="db-exam__meta">
                {{ ex.courseName }} ·
                {{ ex.status === 'draft' ? '草稿' : `已交 ${ex.submittedCount} 人` }}
                <template v-if="ex.passRate !== null"> · 通过 {{ Math.round(ex.passRate * 100) }}%</template>
              </p>
            </div>
            <span class="x-tag" :class="`is-${ex.status}`">{{ statusLabel(ex.status) }}</span>
            <!-- 草稿没有快照、也就没有成绩可统计 → 回向导继续编辑；已发布的进批改页 -->
            <button
              class="q-act"
              type="button"
              @click="$router.push(ex.status === 'draft' ? `/teacher/exams/new?id=${ex.id}` : `/teacher/exams/${ex.id}/marking`)"
            >
              {{ ex.status === 'draft' ? '继续编辑' : '去批改' }}
            </button>
          </div>
        </section>
      </div>

      <!-- 课程速览 -->
      <section class="s-card db-card">
        <div class="db-secHead">
          <h3 class="db-card__title">我的课程</h3>
          <button class="q-act" type="button" @click="$router.push('/teacher/courses')">管理课程</button>
        </div>
        <div class="db-courseGrid">
          <button v-for="c in overview.courses" :key="c.id" class="db-course" type="button" @click="$router.push('/teacher/courses')">
            <p class="db-course__name">{{ c.name }}</p>
            <p class="db-course__meta">
              {{ nz(c.chapters) }} 章 · {{ nz(c.students) }} 人 · {{ kpText(c) }}
            </p>
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { getOverview } from '@/api/teacher/dashboard';

const loading = ref(true);
const overview = ref({ teacher: {}, teaching: {}, todo: {}, recentExams: [], courses: [] });

const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 6) return '夜深了';
  if (h < 12) return '早上好';
  if (h < 18) return '下午好';
  return '晚上好';
});

const todayText = computed(() => {
  const d = new Date();
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()];
  return `${d.getMonth() + 1} 月 ${d.getDate()} 日 · 星期${week}`;
});

const statusLabel = (s) => ({ draft: '草稿', published: '已发布', marking: '批改中', closed: '已结束' }[s] || s);

/** 没有数据源的格子一律「—」：0 和「拿不到」是两件事（项目铁律） */
const nz = (v) => (v === null || v === undefined ? '—' : v);
/** 知识点关联表还没建，空数组不代表 0 个，而是「没有这个数据」 */
const kpText = (c) => {
  const n = c?.knowledgePoints?.length;
  return n ? `${n} 个知识点` : '— 个知识点';
};

onMounted(async () => {
  overview.value = await getOverview();
  loading.value = false;
});
</script>

<style lang="scss" scoped>
.db {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1096px;
}

// ---- 问候 ----
.db-head {
  display: flex;
  align-items: flex-start;
  gap: 16px;

  &__sub {
    margin: 6px 0 0;
    font-size: 13px;
    color: var(--s-ink-3);
  }
  &__actions {
    flex: 0 0 auto;
    margin-left: auto;
    display: flex;
    gap: 10px;
  }
}

// ---- 概览卡 ----
.db-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.db-card {
  padding: 20px;

  &--accent .db-card__num {
    color: var(--sa, #0066cc);
  }
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
  }
  &__note {
    margin: 6px 0 0;
    font-size: 12px;
    color: var(--s-ink-3);
  }
  &__title {
    margin: 0;
    font-size: 14px;
    line-height: 20px;
    font-weight: 600;
    color: var(--s-ink);
  }
}

.db-cols {
  display: grid;
  grid-template-columns: 5fr 7fr;
  gap: 16px;
  align-items: start;
}

.db-secHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;

  .db-card__title {
    margin: 0;
  }
}

// ---- 待办 ----
.db-todoEmpty {
  margin: 0;
  padding: 14px 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--s-ink-3);
}

.db-todo {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border: 0;
  border-radius: 12px;
  background: #f5f5f7;
  cursor: pointer;
  text-align: left;

  & + & {
    margin-top: 8px;
  }
  &:hover {
    background: #ececf1;
  }

  &__text {
    font-size: 13px;
    color: var(--s-ink);
  }
  &__go {
    flex: 0 0 auto;
    font-size: 12px;
    font-weight: 500;
    color: var(--sa, #0066cc);
  }
}

// ---- 近期考试 ----
.db-exam {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;

  & + & {
    border-top: 1px solid #f0f0f2;
  }

  &__text {
    flex: 1 1 auto;
    min-width: 0;
  }
  &__name {
    margin: 0 0 2px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13px;
    font-weight: 500;
    color: var(--s-ink);
  }
  &__meta {
    margin: 0;
    font-size: 12px;
    color: var(--s-ink-3);
  }
}

// 复用考试列表的状态标签配色（青出于蓝但不同源 —— 这里保持一致观感）
.x-tag {
  flex: 0 0 auto;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;

  &.is-draft {
    background: #f0f0f2;
    color: #6e6e73;
  }
  &.is-published {
    background: rgba(0, 102, 204, 0.1);
    color: #0066cc;
  }
  &.is-marking {
    background: #fff3e0;
    color: #b26a00;
  }
  &.is-closed {
    background: #eaf6ee;
    color: #1d8a43;
  }
}

// ---- 课程速览 ----
.db-courseGrid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.db-course {
  padding: 14px;
  border: 0;
  border-radius: 12px;
  background: #f5f5f7;
  cursor: pointer;
  text-align: left;

  &:hover {
    background: #ececf1;
  }

  &__name {
    margin: 0 0 4px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13px;
    font-weight: 600;
    color: var(--s-ink);
  }
  &__meta {
    margin: 0;
    font-size: 11px;
    color: var(--s-ink-3);
  }
}

.db-state {
  margin: 0;
  padding: 28px 0;
  text-align: center;
  font-size: 13px;
  color: var(--s-ink-3);
}
</style>
