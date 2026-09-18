<!--
 * 在线考试（/student/exams）— 对应设计稿 04
 * 数据契约（只用真实返回字段，缺失即降级，不编造）：
 *   - getExamList({pageNo,pageSize}) → {list:[{id,courseName,sectionName,finishTime,duration,score,type}],total}
 *     type: 1=考试 2=练习；考试作答流程在课程章节内发起（Practise.vue），本页只展示已交卷记录
 * 概览统计从记录计算：总场次 / 考试场次 / 练习场次 / 平均分
 -->
<template>
  <div class="exm">
    <!-- ============ 左：考试概览 ============ -->
    <aside class="exm__side">
      <section class="s-card ov">
        <h2 class="ov__title">考试概览</h2>
        <div class="ov__stat" v-for="s in overview" :key="s.label">
          <span class="ov__label">{{ s.label }}</span>
          <span class="ov__value">{{ s.value }}</span>
        </div>
        <div class="ov__divider"></div>
        <h3 class="ov__sub">快速筛选</h3>
        <div class="ov__chips">
          <button
            v-for="t in tabs"
            :key="t.value"
            type="button"
            class="ov__chip"
            :class="{ 'is-active': activeTab === t.value }"
            @click="switchTab(t.value)"
          >{{ t.label }}</button>
        </div>
      </section>
    </aside>

    <!-- ============ 右：考试列表 ============ -->
    <div class="exm__main">
      <section class="s-card listCard">
        <div class="listCard__head">
          <div class="seg" role="tablist" aria-label="考试筛选">
            <button
              v-for="t in tabs"
              :key="t.value"
              type="button"
              role="tab"
              :aria-selected="activeTab === t.value"
              class="seg__item"
              :class="{ 'is-active': activeTab === t.value }"
              @click="switchTab(t.value)"
            >{{ t.label }}</button>
          </div>
          <div class="search">
            <svg class="search__icon" viewBox="0 0 20 20" fill="none" aria-hidden="true">
              <circle cx="9" cy="9" r="5.5" stroke="currentColor" stroke-width="1.5"/>
              <path d="M13.2 13.2 17 17" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
            <input
              v-model.trim="keyword"
              class="search__input"
              type="search"
              placeholder="搜索课程或测试名称"
              aria-label="搜索课程或测试名称"
            />
          </div>
        </div>

        <div v-loading="loading" class="grid">
          <article
            class="card"
            v-for="item in filteredList"
            :key="item.id"
          >
            <div class="card__top">
              <span class="chip" :class="item.examType == 1 ? 'chip--exam' : 'chip--drill'">
                {{ item.examType == 1 ? '考试' : '练习' }}
              </span>
              <span v-if="item.status === 0" class="chip chip--drill">未交卷</span>
              <span v-else-if="item.passed == 1" class="chip chip--exam">已及格</span>
              <span v-else class="chip chip--drill">未及格</span>
              <span class="card__duration" v-if="item.duration">{{ timeFormat(item.duration) }}</span>
            </div>
            <h3 class="card__title" :title="item.examName">{{ item.examName || '未命名试卷' }}</h3>
            <p class="card__desc" :title="item.courseName">{{ item.courseName }}</p>
            <p class="card__meta">
              <template v-if="item.status === 0">
                开始于 {{ item.startTime || '--' }} · 还没交卷
              </template>
              <template v-else>
                交卷于 {{ item.finishTime || '--' }} · 得分
                <strong>{{ item.score ?? '—' }}</strong> / {{ item.totalScore ?? '—' }}
                （答对 {{ item.correctCount ?? '—' }} / {{ item.totalCount ?? '—' }} 题）
              </template>
            </p>
            <div class="card__btns">
              <!-- 学员看的是**自己的答卷**（题目 + 我的答案 + 正确答案 + 解析），不是"批阅"——
                   批阅是讲师的动作。按钮文案以前写错了，这里改正。 -->
              <button
                v-if="item.status !== 0"
                class="btn btn--primary"
                type="button"
                @click="goReview(item)"
              >查看答卷</button>
              <span v-else class="card__hint">未交卷的场次不能回看</span>
            </div>
          </article>
        </div>

        <p class="emptyLine" v-if="!loading && filteredList.length === 0">
          {{ keyword ? '没有匹配的考试记录' : (activeTab === 'all' ? '还没有考试记录，去课程章节开始一场测试吧' : '暂无对应类型的记录') }}
        </p>

        <div class="listCard__foot" v-if="total > params.pageSize">
          <SPagination v-model="params.pageNo" :total="total" :page-size="params.pageSize" @change="loadList" />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { pageMyExamRecords } from '@/api/subject.js';
import { timeFormat } from '@/utils/tool.js';
import SPagination from '@/components/shell/SPagination.vue';

const router = useRouter();

const tabs = [
  { label: '全部', value: 'all' },
  { label: '考试', value: 'exam' },
  { label: '练习', value: 'drill' },
];
const activeTab = ref('all');
const keyword = ref('');

const loading = ref(true);
const records = ref([]);   // 全量记录（用于概览 + 本地筛选）
const params = reactive({ pageNo: 1, pageSize: 10 });
const total = ref(0);

// 分页列表（当前页）
const pageList = ref([]);

const filteredList = computed(() => {
  let list = pageList.value;
  if (activeTab.value === 'exam') list = list.filter((r) => r.examType == 1);
  else if (activeTab.value === 'drill') list = list.filter((r) => r.examType != 1);
  const kw = keyword.value.toLowerCase();
  if (kw) {
    list = list.filter(
      (r) => (r.examName || '').toLowerCase().includes(kw) || (r.courseName || '').toLowerCase().includes(kw),
    );
  }
  return list;
});

const overview = computed(() => {
  const rs = records.value;
  const exams = rs.filter((r) => r.examType == 1);
  const drills = rs.filter((r) => r.examType != 1);
  // 只统计已交卷且有分的学生；没有数据时显示「—」，不写 0 冒充
  const scored = rs.filter((r) => r.status !== 0 && r.score !== null && r.score !== undefined);
  const avg = scored.length
    ? Math.round(scored.reduce((sum, r) => sum + Number(r.score || 0), 0) / scored.length)
    : null;
  return [
    { label: '总场次', value: `${total.value} 场` },
    { label: '考试', value: `${exams.length} 场` },
    { label: '练习', value: `${drills.length} 场` },
    { label: '平均分', value: avg === null ? '—' : `${avg} 分` },
  ];
});

/** exam-service 返回裸 JSON：PageDTO{total,pages,list}，出错抛中文原因 */
async function loadList() {
  loading.value = true;
  try {
    const res = await pageMyExamRecords({ pageNo: params.pageNo, pageSize: params.pageSize });
    pageList.value = res?.list || [];
    total.value = Number(res?.total) || pageList.value.length;
  } catch (e) {
    pageList.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

// 概览基于全量记录（一次性拉较大页）；失败静默，统计保持 0
async function loadAllForStats() {
  try {
    const res = await pageMyExamRecords({ pageNo: 1, pageSize: 200 });
    records.value = res?.list || [];
  } catch (e) { /* 静默 */ }
}

function switchTab(v) {
  if (activeTab.value === v) return;
  activeTab.value = v;
}

function goReview(item) {
  // 回看按**试卷id**取成绩（/es/exams/{examId}/result），不是按记录id
  router.push({
    path: '/student/exams/review',
    query: {
      id: item.examId,
      recordId: item.id,
      courseName: item.courseName || '',
      examName: item.examName || '',
      duration: item.duration || '',
      finishTime: item.finishTime || '',
      score: item.score ?? '',
    },
  });
}

onMounted(() => {
  loadList();
  loadAllForStats();
});
</script>

<style lang="scss" scoped>
.exm {
  display: flex;
  gap: 24px;
  align-items: flex-start;

  &__side { flex: 0 0 328px; width: 328px; }
  &__main { flex: 1 1 auto; min-width: 0; }
}

// ---------- 概览卡 ----------
.ov {
  padding: 20px;

  &__title {
    margin: 0 0 16px;
    font-size: 15px;
    font-weight: 600;
    color: var(--s-ink);
  }
  &__stat {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 7px 0;
  }
  &__label { font-size: 13px; color: var(--s-ink-2); }
  &__value {
    font-size: 15px;
    font-weight: 600;
    color: var(--s-ink);
    font-variant-numeric: tabular-nums;
  }
  &__divider {
    height: 1px;
    margin: 14px 0;
    background: var(--s-divider);
  }
  &__sub {
    margin: 0 0 10px;
    font-size: 13px;
    font-weight: 600;
    color: var(--s-ink);
  }
  &__chips { display: flex; flex-wrap: wrap; gap: 8px; }
  &__chip {
    height: 28px;
    padding: 0 14px;
    border: 0;
    border-radius: 14px;
    background: var(--s-canvas);
    color: var(--s-ink);
    font-size: 13px;
    cursor: pointer;
    transition: background-color 0.15s ease, color 0.15s ease;

    &:hover { background: var(--s-soft); }
    &.is-active { background: var(--sa); color: #fff; }
  }
}

// ---------- 列表卡 ----------
.listCard {
  padding: 24px;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 20px;
    flex-wrap: wrap;
  }
  &__foot {
    display: flex;
    justify-content: flex-end;
    padding-top: 16px;
    border-top: 1px solid var(--s-divider);
  }
}

.seg {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border-radius: 12px;
  background: var(--s-canvas);

  &__item {
    height: 32px;
    padding: 0 16px;
    border: 0;
    border-radius: 9px;
    background: transparent;
    color: var(--s-ink-2);
    font-size: 13px;
    cursor: pointer;
    transition: background-color 0.15s ease, color 0.15s ease;

    &:hover { color: var(--s-ink); }
    &.is-active {
      background: var(--s-card);
      color: var(--s-ink);
      font-weight: 600;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
    }
  }
}

.search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 220px;
  height: 36px;
  padding: 0 14px;
  border-radius: 18px;
  background: var(--s-canvas);

  &:focus-within { box-shadow: inset 0 0 0 1.5px var(--sa); }
  &__icon { width: 16px; height: 16px; color: var(--s-ink-3); flex: 0 0 16px; }
  &__input {
    flex: 1 1 auto;
    min-width: 0;
    border: 0;
    background: transparent;
    outline: none;
    font-size: 13px;
    color: var(--s-ink);

    &::placeholder { color: var(--s-ink-3); }
  }
}

// ---------- 考试卡片 ----------
.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  min-height: 120px;
}

.card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
  border-radius: var(--s-r-md);
  background: var(--s-canvas);
  box-shadow: inset 0 0 0 1px var(--s-divider);
  transition: box-shadow 0.15s ease, transform 0.15s ease;

  &:hover {
    box-shadow: inset 0 0 0 1px var(--card-line-hover, #d2d2d7), 0 4px 14px rgba(0, 0, 0, 0.05);
    transform: translateY(-1px);
  }

  &__top {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  &__duration { font-size: 12px; color: var(--s-ink-2); font-variant-numeric: tabular-nums; }
  &__title {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: var(--s-ink);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &__desc {
    margin: 0;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink-2);
    display: -webkit-box;
    -webkit-line-clamp: 1;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  &__meta {
    margin: 0;
    font-size: 12px;
    color: var(--s-ink-3);
    font-variant-numeric: tabular-nums;
  }
  &__btns { display: flex; gap: 10px; margin-top: auto; }
}

.chip {
  padding: 3px 11px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;

  &--exam { background: var(--sa-soft); color: var(--sa); }
  &--drill { background: #eefaf2; color: #34a853; }
}

.btn {
  flex: 1 1 auto;
  height: 38px;
  border: 0;
  border-radius: 19px;
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.15s ease;

  &--primary { background: var(--sa); color: #fff; font-weight: 500; }
  &--primary:hover { background: var(--sa-hover); }
  &--ghost { background: var(--s-card); color: var(--s-ink); box-shadow: inset 0 0 0 1px var(--s-divider); }
  &--ghost:hover { background: var(--s-canvas); }
}

.emptyLine {
  margin: 12px 0 0;
  padding: 28px 0;
  text-align: center;
  font-size: 13px;
  color: var(--s-ink-3);
}

@media (max-width: 1180px) {
  .exm { flex-direction: column; }
  .exm__side { flex: 1 1 auto; width: 100%; }
  .grid { grid-template-columns: 1fr; }
}
</style>
