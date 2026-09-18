<!--
 * 教师端 · 学生分析（设计稿 T8）
 * -----------------------------------------------------------------------------
 * 这个页面回答一个问题：**我的课，谁在学、学到哪、谁掉队了**。
 *
 * 三件事刻意这么做：
 *   1) 先选课再看人。数据是「某门课的报名学生」—— 跨课程的平均值没有行动价值，
 *      老师真正要处理的是「这门课的这几个人」。
 *   2) 顶部三个数**全部从下面的明细推**（人数 = 行数，平均进度 = 各行进度均值，
 *      沉默数 = 逐行判断）—— 上面和下面加起来必须永远相等，不允许各算一套。
 *   3) 没有出处的字段一律「—」：学习时长在 learning_lesson 里没有对应列，
 *      所以表里不出现它，**不拿 0 或估算值充数**。
 *
 * 口径：沉默 = 报名后从未学习，或最近一次学习距今超过 7 天。
-->
<template>
  <div class="st">
    <div class="st-head">
      <div class="st-head__text">
        <h2 class="s-h2">学生分析</h2>
        <p class="st-head__sub">
          <template v-if="rows.length">
            这门课有 <strong>{{ rows.length }}</strong> 人报名，平均进度 {{ avgProgressText }}。
          </template>
          <template v-else>选一门课，看看谁在学、谁掉队了。</template>
        </p>
      </div>
      <select
        v-if="courses.length"
        v-model="courseId"
        class="q-select st-pick"
        aria-label="选择课程"
        @change="loadStudents"
      >
        <option v-for="c in courses" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
    </div>

    <div class="st-stats">
      <div class="s-card st-stat">
        <p class="st-stat__cap">报名学生</p>
        <p class="st-stat__num">{{ rows.length }}</p>
        <p class="st-stat__note">人</p>
      </div>
      <div class="s-card st-stat">
        <p class="st-stat__cap">平均进度</p>
        <p class="st-stat__num">{{ avgProgressText }}</p>
        <p class="st-stat__note">
          已学小节 ÷ 全课 {{ totalSections ?? '—' }} 节
        </p>
      </div>
      <div class="s-card st-stat">
        <p class="st-stat__cap">沉默学生</p>
        <p class="st-stat__num" :class="{ 'is-warn': silentCount > 0 }">{{ silentCount }}</p>
        <p class="st-stat__note">超过 7 天没学习</p>
      </div>
    </div>

    <section class="s-card st-table">
      <div class="st-tr st-tr--head">
        <span class="st-c">学生</span>
        <span class="st-c">报名时间</span>
        <span class="st-c">已学小节</span>
        <span class="st-c">进度</span>
        <span class="st-c">最近学习</span>
        <span class="st-c">状态</span>
      </div>

      <p v-if="loading" class="st-state">加载中…</p>
      <p v-else-if="error" class="st-state st-state--err">{{ error }}</p>
      <p v-else-if="!courses.length" class="st-state">
        你还没有课程，所以暂时没有学生数据。<br />
        上架一门课后，有学员报名就会出现在这里。
      </p>
      <p v-else-if="!rows.length" class="st-state">
        这门课还没有学生报名。<br />
        学员在课程详情页报名后，会立刻出现在这里。
      </p>

      <div v-for="s in rows" v-else :key="s.userId" class="st-tr">
        <span class="st-c st-c--stu">
          <img v-if="s.icon" class="st-ava" :src="s.icon" :alt="s.name || '学员'" />
          <i v-else class="st-ava st-ava--ph">{{ initialOf(s) }}</i>
          <span class="st-stu__text">
            <strong>{{ s.name || '未设昵称' }}</strong>
            <em>{{ s.cellPhone || '—' }}</em>
          </span>
        </span>
        <span class="st-c st-c--muted">{{ fmt(s.joinTime) }}</span>
        <span class="st-c st-c--muted">
          {{ s.learnedSections ?? 0 }} / {{ totalSections ?? '—' }}
        </span>
        <span class="st-c st-c--bar">
          <template v-if="progressOf(s) !== null">
            <span class="st-bar"><i :style="{ width: `${progressOf(s)}%` }"></i></span>
            <em class="st-bar__num">{{ progressOf(s) }}%</em>
          </template>
          <em v-else class="st-bar__num st-bar__num--muted">—</em>
        </span>
        <span class="st-c" :class="{ 'st-c--muted': !s.latestLearnTime }">
          {{ s.latestLearnTime ? fmt(s.latestLearnTime) : '从未学习' }}
        </span>
        <span class="st-c">
          <b class="st-tag" :class="tagClassOf(s)">{{ statusTextOf(s) }}</b>
        </span>
      </div>
    </section>

    <p class="st-note">
      说明：进度 = 已学小节 ÷ 全课小节数；「沉默」按最近一次学习时间算。
      学习时长暂未统计（学习记录表里没有这个字段），所以本页不展示时长列 —— 有数据源后再补。
    </p>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { listMyCourses, listCourseStudents, LESSON_STATUS_TEXT } from '@/api/teacher/students';

const SILENT_DAYS = 7;
const DAY_MS = 24 * 60 * 60 * 1000;

const courses = ref([]);
const courseId = ref(null);
const rows = ref([]);
const loading = ref(true);
const error = ref('');

const currentCourse = computed(
  () => courses.value.find((c) => String(c.id) === String(courseId.value)) || null
);
/** 全课小节数：取「我的课程」里的 sectionNum；拿不到就不算百分比（显示 —） */
const totalSections = computed(() => {
  const n = currentCourse.value?.sectionNum;
  return Number.isFinite(Number(n)) && Number(n) > 0 ? Number(n) : null;
});

const fmt = (dt) => (dt ? String(dt).replace('T', ' ').slice(0, 16) : '—');
const initialOf = (s) => (s.name || '学').trim().slice(0, 1);

const progressOf = (s) => {
  if (!totalSections.value) return null;
  const learned = Number(s.learnedSections) || 0;
  return Math.max(0, Math.min(100, Math.round((learned / totalSections.value) * 100)));
};

/** 顶部「平均进度」= 各行进度取均值（与明细同一口径，不允许各算一套） */
const avgProgressText = computed(() => {
  if (!rows.value.length) return '—';
  const vals = rows.value.map(progressOf).filter((v) => v !== null);
  if (!vals.length) return '—';
  return `${Math.round(vals.reduce((a, b) => a + b, 0) / vals.length)}%`;
});

const isSilent = (s) => {
  if (!s.latestLearnTime) return true; // 报名后从未学习
  const t = new Date(String(s.latestLearnTime).replace(/-/g, '/')).getTime();
  if (Number.isNaN(t)) return false; // 时间解析不了就不臆断，不算沉默
  return Date.now() - t > SILENT_DAYS * DAY_MS;
};
const silentCount = computed(() => rows.value.filter(isSilent).length);

const statusTextOf = (s) => {
  if (s.status === null || s.status === undefined) return '—';
  return LESSON_STATUS_TEXT[s.status] || '—';
};
const tagClassOf = (s) => {
  if (Number(s.status) === 2) return 'is-ok';
  if (Number(s.status) === 3) return 'is-off';
  if (isSilent(s)) return 'is-warn';
  return 'is-on';
};

const loadStudents = async () => {
  if (courseId.value === null || courseId.value === undefined) {
    rows.value = [];
    loading.value = false;
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const list = await listCourseStudents(courseId.value);
    rows.value = Array.isArray(list) ? list : [];
  } catch (e) {
    // 服务端原话直接给用户（统一 call() 抛出的中文原因）
    error.value = e?.message || '读取学生数据失败';
    rows.value = [];
  } finally {
    loading.value = false;
  }
};

const load = async () => {
  loading.value = true;
  error.value = '';
  try {
    const list = await listMyCourses();
    courses.value = Array.isArray(list) ? list : [];
    // 默认选第一门（有课才请求学生，没课直接是空态）
    if (courses.value.length) {
      courseId.value = courses.value[0].id;
      await loadStudents();
      return;
    }
    rows.value = [];
  } catch (e) {
    error.value = e?.message || '读取课程列表失败';
    courses.value = [];
    rows.value = [];
  } finally {
    loading.value = false;
  }
};

onMounted(load);
</script>

<style lang="scss" scoped>
// ⚠️ 页面内不用与外壳同名的类（.main/.body/.card），一律 st- 前缀
.st {
  --st-cols: minmax(200px, 1.5fr) 150px 110px 170px 150px 90px;
  max-width: 1096px;
}

.st-head {
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

.st-pick {
  max-width: 260px;
  flex: 0 0 auto;
}

// ---------- 三个概览 ----------
.st-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.st-stat {
  padding: 18px 20px;

  &__cap {
    margin: 0;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink-3);
  }
  &__num {
    margin: 6px 0 0;
    font-size: 28px;
    line-height: 34px;
    font-weight: 600;
    color: var(--s-ink);

    &.is-warn {
      color: var(--s-warn);
    }
  }
  &__note {
    margin: 4px 0 0;
    font-size: 11px;
    line-height: 16px;
    color: var(--s-ink-4);
  }
}

// ---------- 表格 ----------
.st-table {
  padding: 8px 8px 4px;
}

.st-tr {
  display: grid;
  grid-template-columns: var(--st-cols);
  align-items: center;
  gap: 12px;
  padding: 12px 12px;

  &--head {
    padding-top: 10px;
    padding-bottom: 10px;
    border-bottom: 1px solid var(--s-hairline);

    .st-c {
      font-size: 12px;
      line-height: 18px;
      color: var(--s-ink-3);
    }
  }

  &:not(&--head) + .st-tr:not(&--head) {
    border-top: 1px solid var(--s-divider);
  }
}

.st-c {
  min-width: 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--s-ink);

  &--muted {
    color: var(--s-ink-2);
  }
}

.st-c--stu {
  display: flex;
  align-items: center;
  gap: 10px;
}

.st-stu__text {
  min-width: 0;

  strong {
    display: block;
    font-size: 13px;
    line-height: 20px;
    font-weight: 500;
    color: var(--s-ink);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  em {
    display: block;
    font-style: normal;
    font-size: 11px;
    line-height: 16px;
    color: var(--s-ink-3);
  }
}

.st-ava {
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
  background: var(--sa-soft);

  &--ph {
    display: flex;
    align-items: center;
    justify-content: center;
    font-style: normal;
    font-size: 13px;
    font-weight: 600;
    color: var(--sa);
  }
}

.st-c--bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.st-bar {
  flex: 1 1 auto;
  height: 6px;
  border-radius: 3px;
  background: var(--s-soft);
  overflow: hidden;

  i {
    display: block;
    height: 100%;
    border-radius: 3px;
    background: var(--sa);
    transition: width 0.2s ease;
  }

  &__num {
    flex: 0 0 auto;
    font-style: normal;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink);

    &--muted {
      color: var(--s-ink-4);
    }
  }
}

.st-tag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  border-radius: 11px;
  font-size: 11px;
  line-height: 16px;
  font-weight: 500;
  background: var(--s-soft);
  color: var(--s-ink-2);

  &.is-on {
    background: var(--sa-soft);
    color: var(--sa);
  }
  &.is-ok {
    background: rgba(52, 199, 89, 0.12);
    color: var(--s-ok);
  }
  &.is-warn {
    background: rgba(255, 159, 10, 0.14);
    color: #b26a00;
  }
  &.is-off {
    background: var(--s-soft);
    color: var(--s-ink-3);
  }
}

.st-state {
  margin: 0;
  padding: 32px 16px;
  font-size: 13px;
  line-height: 22px;
  color: var(--s-ink-2);
  text-align: center;

  &--err {
    color: var(--s-danger);
  }
}

.st-note {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 20px;
  color: var(--s-ink-3);
}

@media (max-width: 1024px) {
  .st-stats {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
